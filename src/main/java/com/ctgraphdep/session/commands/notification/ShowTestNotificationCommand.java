package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;

/**
 * Command to show a test notification dialog
 */
public class ShowTestNotificationCommand extends BaseNotificationCommand<Boolean> {

    public ShowTestNotificationCommand(String username) {
        super(username, null);  // userId is not needed for this command
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info("Executing test notification command for user: " + username);

            // Show test notification using the notification service
            boolean success = ctx.getNotificationService().showTestNotification(username);

            if (success) {
                info("Test notification successfully dispatched for user: " + username);
            } else {
                warn("Failed to dispatch test notification");
            }

            return success;
        });
    }
}