package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

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
                    WorkCode.HOURLY_INTERVAL
            );

            if (!ctx.executeQuery(canShowQuery)) {
                info(String.format("Skipping temporary stop warning for user %s due to rate limiting", username));
                return false;
            }

            // Show temporary stop warning using the notification service
            boolean success = ctx.getNotificationService().showTempStopWarning(
                    username,
                    userId,
                    tempStopStart
            );

            if (success) {
                recordNotificationDisplay(ctx, WorkCode.TEMP_STOP_TYPE);
            }

            return success;
        });
    }
}