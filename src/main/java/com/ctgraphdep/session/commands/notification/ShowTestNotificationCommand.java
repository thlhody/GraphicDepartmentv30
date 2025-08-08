package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;

// Command to show a test notification dialog
public class ShowTestNotificationCommand extends BaseNotificationCommand<Boolean> {

    public ShowTestNotificationCommand() {
        super(null, null);  // userId is not needed for this command
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info("Executing test notification command for user. ");

            // Show test notification using the notification service
            boolean success = ctx.getNotificationService().showTestNotification();

            if (success) {
                info("Test notification successfully dispatched for user. ");
            } else {
                warn("Failed to dispatch test notification");
            }

            return success;
        });
    }
}