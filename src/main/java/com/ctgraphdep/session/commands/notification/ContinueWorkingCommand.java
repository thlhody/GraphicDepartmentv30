package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.utils.LoggerUtil;

public class ContinueWorkingCommand implements SessionCommand<Void> {
    private final String username;
    private final Integer userId;
    private final boolean isHourly;

    public ContinueWorkingCommand(String username, Integer userId, boolean isHourly) {
        this.username = username;
        this.userId = userId;
        this.isHourly = isHourly;
    }

    @Override
    public Void execute(SessionContext context) {
        try {
            // Get standardized time values
            GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
            GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

            // Cancel any backup tasks
            context.getBackupService().cancelBackupTask(username);

            // Activate hourly monitoring if needed (this is a fresh schedule completion, not hourly warning)
            if (!isHourly) {
                // Use factory method to create and execute the command
                ActivateHourlyMonitoringCommand command = context.getCommandFactory().createActivateHourlyMonitoringCommand(username);

                boolean result = context.executeCommand(command);
                if (!result) {
                    LoggerUtil.warn(this.getClass(), String.format("Failed to activate hourly monitoring for user %s", username));
                }
            }

            // Record continuation point
            context.getContinuationTrackingService().recordContinuationPoint(username, userId, timeValues.getCurrentTime(), isHourly);

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error executing continue working command: %s", e.getMessage()));
            return null;
        }
    }
}