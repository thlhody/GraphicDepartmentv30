package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.utils.LoggerUtil;

public class ContinueWorkingCommand implements SessionCommand<Void> {
    private final String username;

    private final boolean isHourly;

    public ContinueWorkingCommand(String username, boolean isHourly) {
        this.username = username;
        this.isHourly = isHourly;
    }

    @Override
    public Void execute(SessionContext context) {
        try {

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

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error executing continue working command: %s", e.getMessage()));
            return null;
        }
    }
}