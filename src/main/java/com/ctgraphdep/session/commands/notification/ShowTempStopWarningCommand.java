package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.ui.DialogComponents;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.awt.*;
import java.time.LocalDateTime;

/**
 * Command to show temporary stop warning
 */
public class ShowTempStopWarningCommand extends BaseNotificationCommand<Boolean> {
    private final LocalDateTime tempStopStart;

    /**
     * Creates a new command to show temporary stop warning
     *
     * @param username The username
     * @param userId The user ID
     * @param tempStopStart The time when temporary stop started
     */
    public ShowTempStopWarningCommand(String username, Integer userId, LocalDateTime tempStopStart) {
        super(username, userId);
        this.tempStopStart = tempStopStart;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            // Log start of the operation
            info(String.format("Attempting to show temporary stop warning for user %s", username));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService()
                    .getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues =
                    ctx.getValidationService().execute(timeCommand);

            // Check if notification can be shown (rate limiting)
            CanShowNotificationQuery canShowQuery = ctx.getCommandFactory().createCanShowNotificationQuery(
                    username,
                    WorkCode.TEMP_STOP_TYPE,
                    WorkCode.HOURLY_INTERVAL,
                    ctx.getNotificationService().getLastNotificationTimes()
            );

            if (!ctx.executeQuery(canShowQuery)) {
                info(String.format("Skipping temporary stop warning for user %s due to rate limiting", username));
                return false;
            }

            // Calculate temporary stop duration
            int stopMinutes = ctx.calculateWorkedMinutesBetween(tempStopStart, timeValues.getCurrentTime());
            int hours = stopMinutes / 60;
            int minutes = stopMinutes % 60;

            // Format messages with the calculated duration
            String formattedMessage = String.format(WorkCode.LONG_TEMP_STOP_WARNING, hours, minutes);
            String trayMessage = String.format(WorkCode.LONG_TEMP_STOP_WARNING_TRAY, hours, minutes);

            // Show notification with fallback
            return ctx.getNotificationService().showNotificationWithFallback(
                    username,
                    userId,
                    WorkCode.TEMPORARY_STOP_TITLE,
                    formattedMessage,
                    trayMessage,
                    WorkCode.ON_FOR_FIVE_MINUTES,
                    false,
                    true,
                    null,
                    (DialogComponents components, String u, Integer id, Integer min) ->
                            ctx.getNotificationService().addTempStopButtons(components, u, id),
                    TrayIcon.MessageType.WARNING
            );
        });
    }
}