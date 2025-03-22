package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;

/**
 * Command for continuing work after a notification
 */
public class ContinueWorkingCommand extends BaseNotificationCommand<Void> {
    private final boolean isHourly;

    /**
     * Creates a new command for continuing work
     *
     * @param username The username
     * @param isHourly Whether this is from an hourly notification
     */
    public ContinueWorkingCommand(String username, boolean isHourly) {
        super(username, null);
        this.isHourly = isHourly;
    }

    @Override
    public Void execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            // Cancel any backup tasks
            ctx.getBackupService().cancelBackupTask(username);

            // Activate hourly monitoring if needed (this is a fresh schedule completion, not hourly warning)
            if (!isHourly) {
                // Use factory method to create and execute the command
                ActivateHourlyMonitoringCommand command = ctx.getCommandFactory().createActivateHourlyMonitoringCommand(username);

                boolean result = ctx.executeCommand(command);
                if (!result) {
                    warn(String.format("Failed to activate hourly monitoring for user %s", username));
                }
            }

            return null;
        });
    }
}