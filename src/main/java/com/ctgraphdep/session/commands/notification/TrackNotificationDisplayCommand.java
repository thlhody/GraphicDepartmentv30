package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.session.SessionContext;

// Command to track when a notification is displayed
public class TrackNotificationDisplayCommand extends BaseNotificationCommand<Void> {

    private final boolean isTempStop;

    public TrackNotificationDisplayCommand(String username, Integer userId, boolean isTempStop) {
        super(username, userId);
        this.isTempStop = isTempStop;
    }

    @Override
    public Void execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            // Log start of the operation
            info(String.format("Tracking notification display for user %s", username));

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