package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.DialogComponents;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.session.query.SessionStatusQuery;

import java.awt.*;

/**
 * Command to show hourly overtime warning
 */
public class ShowHourlyWarningCommand extends BaseNotificationCommand<Boolean> {
    private final Integer finalMinutes;

    /**
     * Creates a new command to show hourly warning
     *
     * @param username The username
     * @param userId The user ID
     * @param finalMinutes The final worked minutes
     */
    public ShowHourlyWarningCommand(String username, Integer userId, Integer finalMinutes) {
        super(username, userId);
        this.finalMinutes = finalMinutes;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Attempting to show hourly warning for user %s", username));

            // Check if notification can be shown based on rate limiting
            CanShowNotificationQuery canShowQuery = ctx.getCommandFactory().createCanShowNotificationQuery(username, WorkCode.OVERTIME_TYPE,
                    WorkCode.HOURLY_INTERVAL, ctx.getNotificationService().getLastNotificationTimes());

            if (!ctx.executeQuery(canShowQuery)) {
                info(String.format("Skipping hourly warning for user %s due to rate limiting", username));
                return false;
            }

            // Don't show during temporary stop
            SessionStatusQuery statusQuery = ctx.getCommandFactory().createSessionStatusQuery(username, userId);
            SessionStatusQuery.SessionStatus status = ctx.executeQuery(statusQuery);

            if (status.isInTemporaryStop()) {
                info(String.format("Skipping hourly warning for user %s (in temporary stop)", username));
                return false;
            }

            // Show notification with fallback
            return ctx.getNotificationService().showNotificationWithFallback(
                    username, userId,
                    WorkCode.OVERTIME_TITLE,
                    WorkCode.HOURLY_WARNING_MESSAGE,
                    WorkCode.HOURLY_WARNING_TRAY,
                    WorkCode.ON_FOR_FIVE_MINUTES,
                    true, false, finalMinutes,
                    (DialogComponents components, String u, Integer id, Integer minutes) -> ctx.getNotificationService().addStandardButtons(components, u, id, minutes, true),
                    TrayIcon.MessageType.WARNING
            );
        });
    }
}