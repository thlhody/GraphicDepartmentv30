package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
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
     * @param userId The user ID
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
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService()
                    .getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues =
                    ctx.getValidationService().execute(timeCommand);
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

            // Start monitoring
            ctx.getSessionMonitorService().startMonitoring(username);

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
            session.setTotalTemporaryStopMinutes(totalStopMinutes);
            debug(String.format("Updated total temporary stop minutes: %d", totalStopMinutes));
        }

        // Update session state using builder
        SessionEntityBuilder.updateSession(session, builder ->
            builder.status(WorkCode.WORK_ONLINE)
                    .currentStartTime(resumeTime)
                    .dayEndTime(null)
                    .workdayCompleted(false)
        );

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
            List<WorkTimeTable> entries = context.getWorkTimeService().loadUserEntries(
                    username,
                    workDate.getYear(),
                    workDate.getMonthValue(),
                    username
            );

            // Find the entry for this specific day
            WorkTimeTable entry = entries.stream()
                    .filter(e -> e.getWorkDate().equals(workDate))
                    .findFirst()
                    .orElse(null);

            if (entry != null) {
                // Update existing entry with session values
                debug("Updating existing worktime entry");
                entry.setDayEndTime(null); // Reset end time since we're resuming
                entry.setTemporaryStopCount(session.getTemporaryStopCount());
                entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
                entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
                entry.setAdminSync(SyncStatusWorktime.USER_IN_PROCESS); // Mark as in-process

                // Save the updated entry
                context.getWorkTimeService().saveWorkTimeEntry(
                        username,
                        entry,
                        workDate.getYear(),
                        workDate.getMonthValue(),
                        username
                );

                info("Updated worktime entry for resumed session");
            } else {
                warn(String.format("No worktime entry found for user %s on %s, cannot update", username, workDate));
            }
        } catch (Exception e) {
            error(String.format("Failed to update worktime entry: %s", e.getMessage()), e);
        }
    }
}