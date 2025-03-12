package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Command to activate hourly monitoring for a user
 */
public class ActivateHourlyMonitoringCommand implements SessionCommand<Boolean> {
    private final String username;

    /**
     * Creates a new command to activate hourly monitoring
     *
     * @param username The username
     */
    public ActivateHourlyMonitoringCommand(String username) {
        this.username = username;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Activating hourly monitoring for user %s", username));
            // Get standardized time values
            GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
            GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);
            // Direct manipulation of session monitoring state
            context.getSessionMonitorService().continuedAfterSchedule.put(username, true);
            context.getSessionMonitorService().lastHourlyWarning.put(username, timeValues.getCurrentTime());

            // Cancel any backup tasks
            context.getBackupService().cancelBackupTask(username);

            LoggerUtil.info(this.getClass(),
                    String.format("Successfully activated hourly monitoring for user %s", username));

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error activating hourly monitoring for user %s: %s",
                            username, e.getMessage()));
            return false;
        }
    }
}