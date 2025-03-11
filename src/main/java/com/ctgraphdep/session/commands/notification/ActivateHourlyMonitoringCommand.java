package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
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
            // Set the user's session to be monitored hourly
            context.getSessionMonitorService().activateHourlyMonitoring(username);
            // The updateMonitoringState method doesn't exist anymore, so we'll log the state change
            LoggerUtil.info(this.getClass(), String.format("Monitoring state set to HOURLY_MONITORING for user %s", username));

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error activating hourly monitoring for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}