package com.ctgraphdep.session.commands;

import com.ctgraphdep.calculations.commands.UpdateLastTemporaryStopCommand;
import com.ctgraphdep.calculations.queries.CalculateRawWorkMinutesQuery;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusWorktime;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Command to end a work day session
 */
public class EndDayCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;
    private final Integer finalMinutes;
    private final LocalDateTime explicitEndTime;

    /**
     * Creates a new command to end a work day with explicit end time
     *
     * @param username The username
     * @param userId The user ID
     * @param finalMinutes The final minutes to record for the day
     * @param endTime The explicit end time to use
     */
    public EndDayCommand(String username, Integer userId, Integer finalMinutes, LocalDateTime endTime) {
        validateUsername(username);
        validateUserId(userId);

        if (finalMinutes != null) {
            validateCondition(finalMinutes >= 0, "Final minutes cannot be negative");
        }

        this.username = username;
        this.userId = userId;
        this.finalMinutes = finalMinutes;
        this.explicitEndTime = endTime;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Executing EndDayCommand for user %s with %d minutes", username, finalMinutes != null ? finalMinutes : 0));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);

            // Get and validate session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);

            // Basic validation - session must exist
            if (session == null) {
                warn("Session is null, cannot end");
                return null;
            }

            // Use explicit end time if provided, otherwise use standardized current time
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : timeValues.getCurrentTime();
            info(String.format("Using end time: %s for user %s", endTime, username));

            // Handle temporary stop case if needed
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                info(String.format("Handling temporary stop before ending session for user %s", username));
                // Use the calculation command for updating last temporary stop
                try {
                    UpdateLastTemporaryStopCommand tempStopCommand = ctx.getCalculationFactory().createUpdateLastTemporaryStopCommand(session, endTime);
                    session = ctx.getCalculationService().executeCommand(tempStopCommand);

                    // Save the updated session
                    SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
                    ctx.executeCommand(saveCommand);

                    info(String.format("Successfully updated temporary stop info for user %s", username));
                } catch (Exception e) {
                    warn(String.format("Error updating temporary stop: %s. Continuing with session end.", e.getMessage()));
                    // Continue with ending session even if temp stop update fails
                }
            }

            // Handle the case where finalMinutes is null we're in an active session
            Integer effectiveFinalMinutes = finalMinutes;
            if (effectiveFinalMinutes == null && (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())
                    || WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()))) {
                // Calculate raw work minutes
                try {
                    CalculateRawWorkMinutesQuery workMinutesQuery = ctx.getCalculationFactory().createCalculateRawWorkMinutesQuery(session, endTime);
                    effectiveFinalMinutes = ctx.getCalculationService().executeQuery(workMinutesQuery);
                    info(String.format("Calculated final minutes for user %s: %d", username, effectiveFinalMinutes));
                } catch (Exception e) {
                    warn(String.format("Error calculating raw work minutes: %s. Using session value.", e.getMessage()));
                    // Fall back to session value if calculation fails
                    effectiveFinalMinutes = session.getTotalWorkedMinutes();
                }
            }

            // Process end session operation using calculation command
            session = processEndSession(session, endTime, ctx, effectiveFinalMinutes);

            // Save the updated session
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // Update session status
            ctx.getSessionStatusService().updateSessionStatus(username, userId, WorkCode.WORK_OFFLINE, endTime);

            // Then update worktime entry
            updateWorktimeEntry(session, ctx, endTime);

            // Clean up monitoring - use both methods to ensure complete cleanup
            ctx.getSessionMonitorService().stopMonitoring(username);
            ctx.getSessionMonitorService().deactivateHourlyMonitoring(username);
            try {
                ctx.getSessionMonitorService().clearMonitoring(username);
            } catch (Exception e) {
                // Just log and continue if clearMonitoring fails
                warn(String.format("Error clearing monitoring: %s", e.getMessage()));
            }

            info(String.format("Successfully ended session for user %s with %d minutes", username, effectiveFinalMinutes != null ? effectiveFinalMinutes : 0));

            return session;
        });
    }

    /**
     * Updates the session with end state values using calculation command
     */
    private WorkUsersSessionsStates processEndSession(WorkUsersSessionsStates session, LocalDateTime endTime, SessionContext context, Integer effectiveFinalMinutes) {
        final int currentWorkedMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;
        final int currentOvertimeMinutes = session.getTotalOvertimeMinutes() != null ? session.getTotalOvertimeMinutes() : 0;

        // Use the context method which delegates to the calculation command
        WorkUsersSessionsStates updatedSession = context.calculateEndDayValues(session, endTime, effectiveFinalMinutes);

        debug(String.format("Processing session end - Current Total: %d, Final: %d, Overtime: %d", currentWorkedMinutes, effectiveFinalMinutes != null ? effectiveFinalMinutes : 0, currentOvertimeMinutes));

        return updatedSession;
    }

    /**
     * Creates and saves a worktime entry based on session data
     */
    private void updateWorktimeEntry(WorkUsersSessionsStates session, SessionContext context, LocalDateTime endTime) {
        try {
            // Get date from session entry
            LocalDate workDate = session.getDayStartTime().toLocalDate();

            // Get user schedule from context
            Integer userSchedule = context.getUserService().getUserById(session.getUserId()).map(User::getSchedule).orElse(WorkCode.INTERVAL_HOURS_C); // Default to 8 hours if not found

            // Get schedule info using the query
            WorkScheduleQuery query = context.getCommandFactory().createWorkScheduleQuery(workDate, userSchedule);
            WorkScheduleQuery.ScheduleInfo scheduleInfo = context.executeQuery(query);

            // Create worktime entry using the command
            CreateWorktimeEntryCommand createCommand = context.getCommandFactory().createWorktimeEntryCommand(username, session, username);

            // Execute the command to get a properly created worktime entry
            WorkTimeTable entry = context.executeCommand(createCommand);

            // Set the end time on the entry
            entry.setDayEndTime(endTime);
            entry.setAdminSync(SyncStatusWorktime.USER_INPUT);

            // Calculate overtime using schedule info if needed
            if (scheduleInfo != null && session.getTotalWorkedMinutes() != null) {
                int overtimeMinutes = scheduleInfo.calculateOvertimeMinutes(session.getTotalWorkedMinutes());
                if (overtimeMinutes > 0 && (entry.getTotalOvertimeMinutes() == null || entry.getTotalOvertimeMinutes() == 0)) {
                    entry.setTotalOvertimeMinutes(overtimeMinutes);
                    info(String.format("Updated overtime minutes for user %s: %d minutes", username, overtimeMinutes));
                }
            }

            // Save the worktime entry
            context.getWorktimeManagementService().saveWorkTimeEntry(username, entry, workDate.getYear(), workDate.getMonthValue(), username);

            info(String.format("Updated worktime entry for user %s - Total minutes: %d, Overtime: %d", username, entry.getTotalWorkedMinutes(), entry.getTotalOvertimeMinutes()));
        } catch (Exception e) {
            error(String.format("Failed to update worktime entry for user %s: %s", username, e.getMessage()), e);
        }
    }
}