package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery.SessionTimeValues;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.session.util.SessionValidator;
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
        GetSessionTimeValuesQuery timeQuery = new GetSessionTimeValuesQuery();
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
        // Use the builder to create worktime entry from session
        WorkTimeTable entry = SessionEntityBuilder.createWorktimeEntryFromSession(session);

        // Ensure the end time is set properly
        SessionEntityBuilder.updateWorktimeEntry(entry, builder -> {builder.dayEndTime(endTime).adminSync(SyncStatus.USER_INPUT);});

        // Get date from session entry
        LocalDate workDate = session.getDayStartTime().toLocalDate();

        // Save worktime entry using worktime service
        context.getWorkTimeService().saveWorkTimeEntry(username, entry, workDate.getYear(), workDate.getMonthValue(), session.getUsername());

        LoggerUtil.info(this.getClass(), String.format("Updated worktime entry for user %s - Total minutes: %d, Overtime: %d", username, entry.getTotalWorkedMinutes(), entry.getTotalOvertimeMinutes()));
    }
}