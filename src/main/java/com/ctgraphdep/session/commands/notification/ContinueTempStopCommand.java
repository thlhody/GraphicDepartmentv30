package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.UpdateSessionActivityCommand;

/**
 * Command to continue a temporary stop
 */
public class ContinueTempStopCommand extends BaseNotificationCommand<Boolean> {

    /**
     * Creates a new command to continue temporary stop
     *
     * @param username The username
     * @param userId The user ID
     */
    public ContinueTempStopCommand(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Continuing temporary stop for user %s", username));

            // Cancel any backup tasks
            ctx.getBackupService().cancelBackupTask(username);

            // Update the session if needed (e.g., refresh last activity timestamp)
            UpdateSessionActivityCommand updateCommand = ctx.getCommandFactory().createUpdateSessionActivityCommand(username, userId);
            ctx.executeCommand(updateCommand);

            info(String.format("Successfully continued temporary stop for user %s", username));

            return true;
        });
    }
}