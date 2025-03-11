package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.DialogComponents;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.awt.*;

/**
 * Command to show start day reminder
 */
public class ShowStartDayReminderCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a new command to show start day reminder
     *
     * @param username The username
     * @param userId The user ID
     */
    public ShowStartDayReminderCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Attempting to show start day reminder for user %s", username));

            // Check if notification can be shown based on rate limiting (once per day)
            CanShowNotificationQuery canShowQuery = context.getCommandFactory().createCanShowNotificationQuery(username, WorkCode.START_DAY_TYPE,
                            WorkCode.ONCE_PER_DAY_TIMER, context.getNotificationService().getLastNotificationTimes());

            if (!context.executeQuery(canShowQuery)) {
                LoggerUtil.info(this.getClass(), String.format("Skipping start day reminder for user %s (already shown today)", username));
                return false;
            }

            // Show notification with fallback
            return context.getNotificationService().showNotificationWithFallback(
                    username, userId,
                    WorkCode.START_DAY_TITLE,
                    WorkCode.START_DAY_MESSAGE,
                    WorkCode.START_DAY_MESSAGE_TRAY,
                    WorkCode.ON_FOR_TWELVE_HOURS,
                    false, false, null,
                    (DialogComponents components, String u, Integer id, Integer minutes) -> context.getNotificationService().addStartDayButtons(components, u, id),
                    TrayIcon.MessageType.INFO
            );
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing start day reminder for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}