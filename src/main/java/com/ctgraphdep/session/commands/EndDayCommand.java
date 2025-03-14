package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.User;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery.SessionTimeValues;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.session.util.SessionEntityBuilder;
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
    private final LocalDateTime explicitEndTime; // Explicit end time parameter

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

        // Get standardized time values
        GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

        // Get and validate session
        WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

        // Modified validation to handle midnight-closed sessions
        if (session == null) {
            LoggerUtil.warn(this.getClass(), "Session is null, cannot end");
            return null;
        }

        // Capture original status to restore it if needed (for midnight handler sessions)
        String originalStatus = session.getSessionStatus();
        boolean needsStatusRestore = false;

        // Handle sessions in "Offline" state from midnight handler
        if (WorkCode.WORK_OFFLINE.equals(originalStatus) && !session.getWorkdayCompleted()) {
            LoggerUtil.info(this.getClass(), "Session is in Offline state but not completed - handling midnight-closed session");
            // No need to change status as we'll respect the original state

        }
        else if (!WorkCode.WORK_ONLINE.equals(originalStatus) && !WorkCode.WORK_TEMPORARY_STOP.equals(originalStatus)) {
            LoggerUtil.warn(this.getClass(), String.format("Session in unexpected state: %s - cannot end", originalStatus));
            return session;
        }

        // Use explicit end time if provided, otherwise use standardized current time
        LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : timeValues.getCurrentTime();

        LoggerUtil.info(this.getClass(), String.format("Using end time: %s for user %s", endTime, username));

        // Process end session operation
        processEndSession(session, endTime, context);

        // Update worktime entry and persist session
        updateWorktimeAndPersist(session, context, endTime, timeValues);

        // Restore original status if needed (for consistency with midnight handler)
        if (needsStatusRestore) {
            session.setSessionStatus(originalStatus);
            // Save again to ensure the status is persisted
            SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(session);
            context.executeCommand(saveCommand);
        }

        // Clean up monitoring
        context.getSessionMonitorService().stopMonitoring(username);

        LoggerUtil.info(this.getClass(), String.format("Successfully ended session for user %s with %d minutes", username, finalMinutes));

        return session;
    }

    // Updates the session with end state values
    private void processEndSession(WorkUsersSessionsStates session, LocalDateTime endTime, SessionContext context) {
        final int currentWorkedMinutes = session.getTotalWorkedMinutes();
        final int currentOvertimeMinutes = session.getTotalOvertimeMinutes() != null ? session.getTotalOvertimeMinutes() : 0;

        context.getCalculationService().calculateEndDayValues(session, endTime, finalMinutes);

        LoggerUtil.debug(this.getClass(), String.format("Processing session end - Current Total: %d, Final: %d, Overtime: %d", currentWorkedMinutes, finalMinutes, currentOvertimeMinutes));
    }

    // Updates worktime entry and persists session
    private void updateWorktimeAndPersist(WorkUsersSessionsStates session, SessionContext context, LocalDateTime endTime, SessionTimeValues timeValues) {
        try {
            // Update worktime entry
            updateWorktimeEntry(session, context, endTime, timeValues);

            // Save session using SaveSessionCommand
            SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(session);
            context.executeCommand(saveCommand);

            // Update session status in database
            context.getSessionStatusService().updateSessionStatus(username, userId, WorkCode.WORK_OFFLINE, endTime);

            LoggerUtil.info(this.getClass(), String.format("Successfully persisted session for user %s with total minutes: %d", session.getUsername(), session.getTotalWorkedMinutes()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to update and persist session for user %s: %s", session.getUsername(), e.getMessage()));
        }
    }

    // Creates and saves a worktime entry based on session data
    private void updateWorktimeEntry(WorkUsersSessionsStates session, SessionContext context, LocalDateTime endTime, SessionTimeValues timeValues) {
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
                    LoggerUtil.info(this.getClass(), String.format(
                            "Updated overtime minutes for user %s: %d minutes",
                            username, overtimeMinutes));
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