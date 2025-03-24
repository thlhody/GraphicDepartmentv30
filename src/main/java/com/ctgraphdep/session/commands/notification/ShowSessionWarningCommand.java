package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.session.SessionContext;

import java.awt.*;

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
                return false;
            }

            // Register backup notification
            ctx.getBackupService().registerScheduleEndNotification(username, userId);

            // Record the notification display
            recordNotificationDisplay(ctx, WorkCode.SCHEDULE_END_TYPE);

            // Show notification with fallback
            boolean result = ctx.getNotificationService().showNotificationWithFallback(
                    username, userId,
                    WorkCode.END_SCHEDULE_TITLE,
                    WorkCode.SESSION_WARNING_MESSAGE,
                    WorkCode.SESSION_WARNING_TRAY,
                    WorkCode.ON_FOR_TEN_MINUTES,
                    false, false, finalMinutes,
                    (components, u, id, minutes) -> ctx.getNotificationService()
                            .addStandardButtons(components, u, id, minutes, false),
                    TrayIcon.MessageType.INFO
            );

            info("Schedule completion dialog display result: " + result);
            return result;
        });
    }
}