package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.User;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Command to end a work day session
public class EndDayCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;
    private final Integer finalMinutes;
    private final LocalDateTime explicitEndTime;// Explicit end time parameter
    /**
     * Creates a new command to end a work day with explicit end time
     *
     * @param username The username
     * @param userId The user ID
     * @param finalMinutes The final minutes to record for the day
     * @param endTime The explicit end time to use
     */
    public EndDayCommand(String username, Integer userId, Integer finalMinutes, LocalDateTime endTime) {
        this.username = username;
        this.userId = userId;
        this.finalMinutes = finalMinutes;
        this.explicitEndTime = endTime;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        LoggerUtil.info(this.getClass(), String.format("Executing EndDayCommand for user %s with %d minutes", username, finalMinutes));

        // Get standardized time values using the new validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);


        // Get and validate session
        WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

        // Basic validation - session must exist
        if (session == null) {
            LoggerUtil.warn(this.getClass(), "Session is null, cannot end");
            return null;
        }

        // Use explicit end time if provided, otherwise use standardized current time
        LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : timeValues.getCurrentTime();

        LoggerUtil.info(this.getClass(), String.format("Using end time: %s for user %s", endTime, username));

        // Process end session operation using calculation command
        session = processEndSession(session, endTime, context);

        // Save the updated session first
        SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(session);
        context.executeCommand(saveCommand);

        // Update session status in database
        context.getSessionStatusService().updateSessionStatus(username, userId, WorkCode.WORK_OFFLINE, endTime);

        // Then update worktime entry
        updateWorktimeEntry(session, context, endTime);

        // Clean up monitoring
        context.getSessionMonitorService().stopMonitoring(username);

        LoggerUtil.info(this.getClass(), String.format("Successfully ended session for user %s with %d minutes", username, finalMinutes));

        return session;
    }

    // Updates the session with end state values using calculation command
    private WorkUsersSessionsStates processEndSession(WorkUsersSessionsStates session, LocalDateTime endTime, SessionContext context) {
        final int currentWorkedMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;
        final int currentOvertimeMinutes = session.getTotalOvertimeMinutes() != null ? session.getTotalOvertimeMinutes() : 0;

        // Use the context method which delegates to the calculation command
        WorkUsersSessionsStates updatedSession = context.calculateEndDayValues(session, endTime, finalMinutes);

        LoggerUtil.debug(this.getClass(), String.format("Processing session end - Current Total: %d, Final: %d, Overtime: %d",
                currentWorkedMinutes, finalMinutes, currentOvertimeMinutes));

        return updatedSession;
    }

    // Creates and saves a worktime entry based on session data
    private void updateWorktimeEntry(WorkUsersSessionsStates session, SessionContext context, LocalDateTime endTime) {
        try {
            // Get date from session entry
            LocalDate workDate = session.getDayStartTime().toLocalDate();

            // Get user schedule from context
            Integer userSchedule = context.getUserService()
                    .getUserById(session.getUserId())
                    .map(User::getSchedule)
                    .orElse(WorkCode.INTERVAL_HOURS_C); // Default to 8 hours if not found

            // Get schedule info using the query
            WorkScheduleQuery query = context.getCommandFactory().createWorkScheduleQuery(workDate, userSchedule);
            WorkScheduleQuery.ScheduleInfo scheduleInfo = context.executeQuery(query);

            // Create worktime entry using the command
            CreateWorktimeEntryCommand createCommand = context.getCommandFactory()
                    .createWorktimeEntryCommand(username, session, username);

            // Execute the command to get a properly created worktime entry
            WorkTimeTable entry = context.executeCommand(createCommand);

            // Set the end time on the entry
            entry.setDayEndTime(endTime);
            entry.setAdminSync(SyncStatus.USER_INPUT);

            // Calculate overtime using schedule info if needed
            if (scheduleInfo != null && session.getTotalWorkedMinutes() != null) {
                int overtimeMinutes = scheduleInfo.calculateOvertimeMinutes(session.getTotalWorkedMinutes());
                if (overtimeMinutes > 0 && (entry.getTotalOvertimeMinutes() == null || entry.getTotalOvertimeMinutes() == 0)) {
                    entry.setTotalOvertimeMinutes(overtimeMinutes);
                    LoggerUtil.info(this.getClass(), String.format("Updated overtime minutes for user %s: %d minutes", username, overtimeMinutes));
                }
            }

            // Save the worktime entry
            context.getWorkTimeService().saveWorkTimeEntry(
                    username,
                    entry,
                    workDate.getYear(),
                    workDate.getMonthValue(),
                    username
            );

            LoggerUtil.info(this.getClass(), String.format(
                    "Updated worktime entry for user %s - Total minutes: %d, Overtime: %d",
                    username,
                    entry.getTotalWorkedMinutes(),
                    entry.getTotalOvertimeMinutes()
            ));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Failed to update worktime entry for user %s: %s",
                    username,
                    e.getMessage()
            ));
        }
    }
}