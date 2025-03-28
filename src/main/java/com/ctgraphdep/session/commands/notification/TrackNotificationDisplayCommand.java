package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Command to track when a notification is displayed
 */
public class TrackNotificationDisplayCommand extends BaseNotificationCommand<Void> {
    private final int timeoutPeriod;
    private final boolean isTempStop;

    /**
     * Creates a new command to track notification display
     *
     * @param username The username
     * @param userId The user ID
     * @param timeoutPeriod The timeout period for the notification
     * @param isTempStop Whether this is a temporary stop notification
     */
    public TrackNotificationDisplayCommand(String username, Integer userId, int timeoutPeriod, boolean isTempStop) {
        super(username, userId);
        this.timeoutPeriod = timeoutPeriod;
        this.isTempStop = isTempStop;
    }

    @Override
    public Void execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            // Log start of the operation
            info(String.format("Tracking notification display for user %s", username));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService()
                    .getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues =
                    ctx.getValidationService().execute(timeCommand);

            // Determine notification type
            String notificationType = isTempStop ? "TEMP_STOP" : "SCHEDULE_END";

            // Record notification display time for rate limiting
            ctx.getNotificationService().recordNotificationTime(username, notificationType);

            // Create file-based tracking mechanism

            ctx.getDataAccessService().writeNotificationTrackingFile(username, notificationType, timeValues.getCurrentTime());
            // Register backup task if applicable
            if (timeoutPeriod > 0) {
                if (isTempStop) {
                    // Get the last temporary stop time from the session
                    LocalDateTime tempStopStartTime = null;
                    WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);
                    if (session != null) {
                        tempStopStartTime = session.getLastTemporaryStopTime();
                    }

                    // Use current time as fallback if temp stop start time not available
                    if (tempStopStartTime == null) {
                        tempStopStartTime = timeValues.getCurrentTime();
                    }

                    // Register temp stop notification
                    ctx.getBackupService().registerTempStopNotification(username, userId, tempStopStartTime);
                } else {
                    // Register schedule end notification
                    ctx.getBackupService().registerScheduleEndNotification(username, userId);
                }
            }

            info(String.format("Successfully tracked notification for user %s", username));

            return null;
        });
    }
}