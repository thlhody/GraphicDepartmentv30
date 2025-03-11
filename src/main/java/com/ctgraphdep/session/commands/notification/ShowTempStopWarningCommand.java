package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.DialogComponents;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Command to show temporary stop warning
 */
public class ShowTempStopWarningCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final LocalDateTime tempStopStart;

    /**
     * Creates a new command to show temporary stop warning
     *
     * @param username The username
     * @param userId The user ID
     * @param tempStopStart The time when temporary stop started
     */
    public ShowTempStopWarningCommand(String username, Integer userId, LocalDateTime tempStopStart) {
        this.username = username;
        this.userId = userId;
        this.tempStopStart = tempStopStart;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Attempting to show temporary stop warning for user %s", username));
            // Get standardized time values
            GetSessionTimeValuesQuery timeQuery = new GetSessionTimeValuesQuery();
            GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

            // Check if notification can be shown based on rate limiting (hourly)
            CanShowNotificationQuery canShowQuery = context.getCommandFactory().createCanShowNotificationQuery(username, WorkCode.TEMP_STOP_TYPE,
                            WorkCode.HOURLY_INTERVAL, context.getNotificationService().getLastNotificationTimes());

            if (!context.executeQuery(canShowQuery)) {
                LoggerUtil.info(this.getClass(), String.format("Skipping temporary stop warning for user %s due to rate limiting", username));
                return false;
            }

            // Calculate temporary stop duration
            Duration stopDuration = Duration.between(tempStopStart, timeValues.getCurrentTime());
            int stopMinutes = (int) stopDuration.toMinutes();
            int hours = stopMinutes / 60;
            int minutes = stopMinutes % 60;

            // Format messages with the calculated duration
            String formattedMessage = String.format(WorkCode.LONG_TEMP_STOP_WARNING, hours, minutes);
            String trayMessage = String.format(WorkCode.LONG_TEMP_STOP_WARNING_TRAY, hours, minutes);

            // Show notification with fallback
            return context.getNotificationService().showNotificationWithFallback(
                    username, userId,
                    WorkCode.NOTICE_TITLE,
                    formattedMessage,
                    trayMessage,
                    WorkCode.ON_FOR_FIVE_MINUTES,
                    false, true, null,
                    (DialogComponents components, String u, Integer id, Integer mins) -> context.getNotificationService().addTempStopButtons(components, u, id),
                    TrayIcon.MessageType.WARNING
            );
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing temporary stop warning for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}