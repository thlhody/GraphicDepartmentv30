package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;

import javax.swing.*;
import java.awt.*;


public class ShowSessionWarningCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final Integer finalMinutes;

    public ShowSessionWarningCommand(String username, Integer userId, Integer finalMinutes) {
        this.username = username;
        this.userId = userId;
        this.finalMinutes = finalMinutes;
    }
    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), "Attempting to show schedule completion notification for user: " + username);

            // Check if notification can be shown (rate limiting logic)
            if (!context.getNotificationService().canShowNotification(username, WorkCode.SCHEDULE_END_TYPE, 24 * 60)) {
                return false;
            }

            // Register backup notification
            context.getBackupService().registerScheduleEndNotification(username, userId);

            // Show actual notification using UI service with explicit invokeLater
            final boolean[] result = {false};
            SwingUtilities.invokeLater(() -> {
                try {
                    LoggerUtil.info(this.getClass(), "Showing schedule completion dialog on EDT thread: " + SwingUtilities.isEventDispatchThread());

                    result[0] = context.getNotificationService().showNotificationWithFallback(
                            username, userId,
                            WorkCode.NOTICE_TITLE,
                            WorkCode.SESSION_WARNING_MESSAGE,
                            WorkCode.SESSION_WARNING_TRAY,
                            WorkCode.ON_FOR_TEN_MINUTES,
                            false, false, finalMinutes,
                            (components, u, id, minutes) -> context.getNotificationService().addStandardButtons(components, u, id, minutes, false),
                            TrayIcon.MessageType.INFO
                    );

                    LoggerUtil.info(this.getClass(), "Schedule completion dialog display result: " + result[0]);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error showing notification on EDT: " + e.getMessage());
                }
            });

            // Return true to indicate we've dispatched the request
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in ShowSessionWarningCommand: " + e.getMessage());
            return false;
        }
    }
}