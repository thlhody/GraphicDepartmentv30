package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.session.SessionContext;

/**
 * Command to show schedule completion notification
 */
public class ShowSessionWarningCommand extends BaseNotificationCommand<Boolean> {
    private final Integer finalMinutes;

    /**
     * Creates a new command to show session warning
     *
     * @param username The username
     * @param userId The user ID
     * @param finalMinutes The final worked minutes
     */
    public ShowSessionWarningCommand(String username, Integer userId, Integer finalMinutes) {
        super(username, userId);
        this.finalMinutes = finalMinutes;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info("Attempting to show schedule completion notification for user: " + username);

            // Check if notification can be shown (rate limiting logic)
            if (!ctx.getNotificationService().canShowNotification(username, WorkCode.SCHEDULE_END_TYPE, 24 * 60)) {
                info(String.format("Skipping schedule completion notification for user %s due to rate limiting", username));
                return false;
            }

            // Show schedule end notification using the notification service
            boolean success = ctx.getNotificationService().showScheduleEndNotification(username, userId, finalMinutes);

            if (success) {
                // Record notification display
                recordNotificationDisplay(ctx, WorkCode.SCHEDULE_END_TYPE);
                info("Successfully showed schedule completion notification for user: " + username);
            } else {
                warn("Failed to show schedule completion notification for user: " + username);
            }

            return success;
        });
    }
}