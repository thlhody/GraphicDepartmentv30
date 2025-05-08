package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.notification.ActivateHourlyMonitoringCommand;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Command to resume a previously completed session
 */
public class ResumePreviousSessionCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a command to resume a previously completed session
     *
     * @param username The username
     * @param userId   The user ID
     */
    public ResumePreviousSessionCommand(String username, Integer userId) {
        validateUsername(username);
        validateUserId(userId);

        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Executing ResumePreviousSessionCommand for user %s", username));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);
            LocalDateTime resumeTime = timeValues.getCurrentTime();
            debug(String.format("Resume time: %s", resumeTime));

            // Get the current session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);

            // Validate session can be resumed - this requires special validation
            if (!SessionValidator.isCompletedSession(session, this.getClass())) {
                warn("Session is not in a completed state that can be resumed");
                return session;
            }

            // Process resume operation - update the session first
            processResumeSession(session, resumeTime, ctx);

            // Save the updated session
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // Then update the worktime entry using the session information
            updateWorktimeEntryFromSession(session, ctx);

            // NEW: Check if schedule is completed to determine proper monitoring
            boolean scheduleCompleted = isScheduleCompleted(session, ctx);

            if (scheduleCompleted) {
                debug(String.format("Schedule already completed for user %s, activating hourly monitoring", username));

                // Activate hourly monitoring since schedule is already completed
                ActivateHourlyMonitoringCommand activateCommand = ctx.getCommandFactory().createActivateHourlyMonitoringCommand(username);
                boolean result = ctx.executeCommand(activateCommand);

                if (result) {
                    info(String.format("Successfully activated hourly monitoring for user %s on resume", username));
                } else {
                    warn(String.format("Failed to activate hourly monitoring for user %s on resume", username));
                }
            } else {
                debug(String.format("Schedule not yet completed for user %s, starting enhanced monitoring", username));

                // Start enhanced monitoring for normal schedule tracking
                ctx.getSessionMonitorService().startEnhancedMonitoring(username);

                info(String.format("Started enhanced monitoring for user %s", username));
            }

            info(String.format("Resumed previous session for user %s", username));

            return session;
        });
    }

    /**
     * Handles the main resume process
     */
    private void processResumeSession(WorkUsersSessionsStates session, LocalDateTime resumeTime, SessionContext context) {
        debug("Processing resume for previously completed session");

        // Create a temporary stop for the break period
        final LocalDateTime previousEndTime = session.getDayEndTime();
        if (previousEndTime != null) {
            debug(String.format("Creating temporary stop from %s to %s", previousEndTime, resumeTime));

            // Use the calculation command through context to add a break as temporary stop
            context.addBreakAsTempStop(session, previousEndTime, resumeTime);

            // Update total temporary stop minutes using the dedicated method
            int totalStopMinutes = context.calculateTotalTempStopMinutes(session, resumeTime);

            // Update session state using builder - consolidate both updates into a single builder call
            SessionEntityBuilder.updateSession(session, builder -> builder
                    .status(WorkCode.WORK_ONLINE)
                    .currentStartTime(resumeTime)
                    .dayEndTime(null)
                    .workdayCompleted(false)
                    .totalTemporaryStopMinutes(totalStopMinutes));

            debug(String.format("Updated total temporary stop minutes: %d", totalStopMinutes));
        } else {
            // If no previous end time, just update the session state
            SessionEntityBuilder.updateSession(session, builder -> builder
                    .status(WorkCode.WORK_ONLINE)
                    .currentStartTime(resumeTime)
                    .dayEndTime(null)
                    .workdayCompleted(false));
        }

        debug("Session state updated to WORK_ONLINE");
    }
    /**
     * Updates the worktime entry based on the session information
     */
    private void updateWorktimeEntryFromSession(WorkUsersSessionsStates session, SessionContext context) {
        try {
            if (session.getDayStartTime() == null) {
                warn("Cannot update worktime entry: session has no start time");
                return;
            }

            LocalDate workDate = session.getDayStartTime().toLocalDate();
            debug(String.format("Updating worktime entry for date: %s", workDate));

            // Find existing worktime entries for the month
            List<WorkTimeTable> entries = context.getWorktimeManagementService().loadUserEntries(username, workDate.getYear(), workDate.getMonthValue(), username);

            // Find the entry for this specific day
            WorkTimeTable entry = entries.stream().filter(e -> e.getWorkDate().equals(workDate))
                    .findFirst().orElse(null);

            if (entry != null) {
                // Update existing entry with session values
                debug("Updating existing worktime entry");
                entry.setDayEndTime(null); // Reset end time since we're resuming
                entry.setTemporaryStopCount(session.getTemporaryStopCount());
                entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
                entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
                entry.setAdminSync(SyncStatusWorktime.USER_IN_PROCESS); // Mark as in-process

                // Save the updated entry
                context.getWorktimeManagementService().saveWorkTimeEntry(username, entry, workDate.getYear(), workDate.getMonthValue(), username);

                info("Updated worktime entry for resumed session");
            } else {
                warn(String.format("No worktime entry found for user %s on %s, cannot update", username, workDate));
            }
        } catch (Exception e) {
            error(String.format("Failed to update worktime entry: %s", e.getMessage()), e);
        }
    }

    /**
     * Checks if the user's schedule is completed based on worked minutes.
     * This is used to determine whether to start schedule monitoring or hourly monitoring.
     */
    private boolean isScheduleCompleted(WorkUsersSessionsStates session, SessionContext context) {
        try {
            if (session.getDayStartTime() == null) {
                return false;
            }

            LocalDate workDate = session.getDayStartTime().toLocalDate();

            // Get user schedule
            User user = context.getUserService().getUserById(userId).orElse(null);
            if (user == null) {
                warn(String.format("User not found for ID %d, cannot check schedule completion", userId));
                return false;
            }

            int userSchedule = user.getSchedule();

            // Get schedule info using the query
            WorkScheduleQuery query = context.getCommandFactory().createWorkScheduleQuery(workDate, userSchedule);
            WorkScheduleQuery.ScheduleInfo scheduleInfo = context.executeQuery(query);

            if (scheduleInfo == null) {
                warn("Could not retrieve schedule info, cannot check schedule completion");
                return false;
            }

            // Check if worked minutes exceed schedule
            int workedMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;
            return scheduleInfo.isScheduleCompleted(workedMinutes);

        } catch (Exception e) {
            error(String.format("Error checking schedule completion: %s", e.getMessage()), e);
            return false; // Default to false for safety
        }
    }
}