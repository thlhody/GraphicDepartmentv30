package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

/**
 * Command to track when a notification is displayed
 */
public class TrackNotificationDisplayCommand extends BaseNotificationCommand<Void> {
    private final boolean isTempStop;

    /**
     * Creates a new command to track notification display
     *
     * @param username The username
     * @param userId The user ID

     * @param isTempStop Whether this is a temporary stop notification
     */
    public TrackNotificationDisplayCommand(String username, Integer userId, boolean isTempStop) {
        super(username, userId);
        this.isTempStop = isTempStop;
    }

    @Override
    public Void execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            // Log start of the operation
            info(String.format("Tracking notification display for user %s", username));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);

            // Determine notification type
            String notificationType = isTempStop ? WorkCode.TEMP_STOP_TYPE : WorkCode.SCHEDULE_END_TYPE;

            // Record notification display time for rate limiting
            ctx.getNotificationService().recordNotificationTime(username, notificationType);

            // Record notification display time for rate limiting
            ctx.getNotificationService().recordNotificationTime(username, notificationType);

            info(String.format("Successfully tracked notification for user %s", username));

            return null;
        });
    }
}