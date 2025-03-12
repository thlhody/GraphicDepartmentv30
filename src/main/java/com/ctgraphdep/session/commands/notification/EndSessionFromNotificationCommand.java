package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.EndDayCommand;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Command to end a session from a notification.
 * This handles notification-specific logic and then delegates to the core EndDayCommand.
 */
public class EndSessionFromNotificationCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final Integer finalMinutes;

    /**
     * Creates a new command to end a session from a notification
     *
     * @param username The username
     * @param userId The user ID
     * @param finalMinutes The final minutes to record for the session
     */
    public EndSessionFromNotificationCommand(String username, Integer userId, Integer finalMinutes) {
        this.username = username;
        this.userId = userId;
        this.finalMinutes = finalMinutes;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Ending session from notification for user %s", username));
            // Get standardized time values
            GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
            GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

            // Get current session to validate it's still active
            WorkUsersSessionsStates session = context.getCurrentSession(username, userId);
            if (session == null || !WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                LoggerUtil.warn(this.getClass(), String.format("Cannot end session from notification - user %s is not online", username));
                return false;
            }

            // Cancel any pending notification backup tasks
            context.getBackupService().cancelBackupTask(username);

            LocalDateTime currentTime = timeValues.getCurrentTime();

            // Use the core EndDayCommand to perform the actual session end with explicit time
            EndDayCommand endDayCommand = context.getCommandFactory()
                    .createEndDayCommand(username, userId, finalMinutes, currentTime);
            context.executeCommand(endDayCommand);

            // Clear monitoring state
            context.getSessionMonitorService().clearMonitoring(username);

            // If we reach this point, the end was successful
            LoggerUtil.info(this.getClass(),
                    String.format("Successfully ended session from notification for user %s", username));

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error ending session from notification for user %s: %s",
                            username, e.getMessage()), e);
            return false;
        }
    }
}