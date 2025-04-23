package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.session.query.SessionStatusQuery;

/**
 * Command to show worktime resolution reminder
 */
public class ShowResolutionReminderCommand extends BaseNotificationCommand<Boolean> {
    private final String title;
    private final String message;
    private final String trayMessage;
    private final Integer timeoutPeriod;

    /**
     * Creates a new command to show resolution reminder
     *
     * @param username The username
     * @param userId The user ID
     * @param title The notification title
     * @param message The notification message
     * @param trayMessage The tray notification message
     * @param timeoutPeriod The timeout period for the notification
     */
    public ShowResolutionReminderCommand(String username, Integer userId, String title, String message, String trayMessage, Integer timeoutPeriod) {
        super(username, userId);
        this.title = title;
        this.message = message;
        this.trayMessage = trayMessage;
        this.timeoutPeriod = timeoutPeriod;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            // Log start of the operation
            info(String.format("Attempting to show resolution reminder for user %s", username));

            // Check if notification can be shown (rate limiting)
            CanShowNotificationQuery canShowQuery = ctx.getCommandFactory().createCanShowNotificationQuery(username, WorkCode.RESOLUTION_REMINDER_TYPE, WorkCode.CHECK_INTERVAL);

            if (!ctx.executeQuery(canShowQuery)) {
                info(String.format("Skipping resolution reminder for user %s due to rate limiting", username));
                return false;
            }

            // Additional validation - don't show during certain session states
            SessionStatusQuery statusQuery = ctx.getCommandFactory().createSessionStatusQuery(username, userId);
            SessionStatusQuery.SessionStatus status = ctx.executeQuery(statusQuery);

            if (status.isInTemporaryStop()) {
                info(String.format("Skipping resolution reminder for user %s (in temporary stop)", username));
                return false;
            }

            // Show resolution reminder using the notification service
            boolean success = ctx.getNotificationService().showResolutionReminder(username, userId, title, message, trayMessage, timeoutPeriod);

            if (success) {
                // Record notification display
                recordNotificationDisplay(ctx, WorkCode.RESOLUTION_REMINDER_TYPE);
            }

            return success;
        });
    }
}