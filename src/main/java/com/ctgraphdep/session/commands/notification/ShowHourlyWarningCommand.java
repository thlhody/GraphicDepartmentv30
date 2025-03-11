package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.DialogComponents;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.session.query.IsInTemporaryStopQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.awt.*;

/**
 * Command to show hourly overtime warning
 */
public class ShowHourlyWarningCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final Integer finalMinutes;

    /**
     * Creates a new command to show hourly warning
     *
     * @param username The username
     * @param userId The user ID
     * @param finalMinutes The final worked minutes
     */
    public ShowHourlyWarningCommand(String username, Integer userId, Integer finalMinutes) {
        this.username = username;
        this.userId = userId;
        this.finalMinutes = finalMinutes;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Attempting to show hourly warning for user %s", username));

            // Check if notification can be shown based on rate limiting
            CanShowNotificationQuery canShowQuery = context.getCommandFactory().createCanShowNotificationQuery(username, WorkCode.OVERTIME_TYPE,
                            WorkCode.CHECK_INTERVAL, context.getNotificationService().getLastNotificationTimes());

            if (!context.executeQuery(canShowQuery)) {
                LoggerUtil.info(this.getClass(), String.format("Skipping hourly warning for user %s due to rate limiting", username));
                return false;
            }

            // Don't show during temporary stop
            IsInTemporaryStopQuery tempStopQuery = context.getCommandFactory().createIsInTemporaryStopQuery(username, userId);

            if (context.executeQuery(tempStopQuery)) {
                LoggerUtil.info(this.getClass(), String.format("Skipping hourly warning for user %s (in temporary stop)", username));
                return false;
            }

            // Show notification with fallback
            return context.getNotificationService().showNotificationWithFallback(
                    username, userId,
                    WorkCode.NOTICE_TITLE,
                    WorkCode.HOURLY_WARNING_MESSAGE,
                    WorkCode.HOURLY_WARNING_TRAY,
                    WorkCode.ON_FOR_FIVE_MINUTES,
                    true, false, finalMinutes,
                    (DialogComponents components, String u, Integer id, Integer minutes) -> context.getNotificationService().addStandardButtons(components, u, id, minutes, true),
                    TrayIcon.MessageType.WARNING
            );
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing hourly warning for user %s: %s",
                    username, e.getMessage()));
            return false;
        }
    }
}