package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.model.DialogComponents;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.utils.LoggerUtil;

import java.awt.*;

/**
 * Command to show a resolution reminder notification
 */
public class ShowResolutionReminderCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final String title;
    private final String message;
    private final String trayMessage;
    private final int timeoutPeriod;

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
    public ShowResolutionReminderCommand(
            String username,
            Integer userId,
            String title,
            String message,
            String trayMessage,
            int timeoutPeriod) {
        this.username = username;
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.trayMessage = trayMessage;
        this.timeoutPeriod = timeoutPeriod;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Showing resolution reminder for user %s", username));

            return context.getNotificationService().showNotificationWithFallback(
                    username, userId,
                    title,
                    message,
                    trayMessage,
                    timeoutPeriod,
                    false, false, null,
                    (DialogComponents components, String u, Integer id, Integer minutes) ->
                            context.getNotificationService().addResolutionButtons(components),
                    TrayIcon.MessageType.WARNING
            );
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing resolution reminder for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}