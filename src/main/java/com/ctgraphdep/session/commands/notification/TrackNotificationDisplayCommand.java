package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Command to track when a notification is displayed
 */
public class TrackNotificationDisplayCommand implements SessionCommand<Void> {
    private final String username;
    private final Integer userId;
    private final int timeoutPeriod;
    private final boolean isTempStop;

    /**
     * Creates a new command to track notification display
     *
     * @param username The username
     * @param userId The user ID
     * @param timeoutPeriod The timeout period in milliseconds
     * @param isTempStop Whether this is a temporary stop notification
     */
    public TrackNotificationDisplayCommand(String username, Integer userId, int timeoutPeriod, boolean isTempStop) {
        this.username = username;
        this.userId = userId;
        this.timeoutPeriod = timeoutPeriod;
        this.isTempStop = isTempStop;
    }

    @Override
    public Void execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Tracking notification display for user %s", username));

            // Get standardized time values
            GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
            GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

            // Record notification display time for rate limiting
            String notificationType = isTempStop ? "TEMP_STOP" : "SCHEDULE_END";
            context.getNotificationService().recordNotificationTime(username, notificationType);

            // Create a file-based tracking mechanism for fallback
            Path notificationsDir = context.getPathConfig().getLocalPath().resolve("notifications");
            Files.createDirectories(notificationsDir);

            Path trackingFile = notificationsDir.resolve(String.format("%s_%s_notification.lock", username, notificationType.toLowerCase()));

            Files.write(trackingFile, timeValues.getCurrentTime().toString().getBytes());

            // Register backup task if applicable
            if (timeoutPeriod > 0) {
                if (isTempStop) {
                    // Get the last temporary stop time from the session
                    LocalDateTime tempStopStartTime = null;
                    WorkUsersSessionsStates session = context.getCurrentSession(username, userId);
                    if (session != null) {
                        tempStopStartTime = session.getLastTemporaryStopTime();
                    }

                    if (tempStopStartTime == null) {
                        tempStopStartTime = timeValues.getCurrentTime(); // Fallback if not available
                    }

                    // Register with the correct parameters
                    context.getBackupService().registerTempStopNotification(username, userId, tempStopStartTime);
                } else {
                    // For schedule end or hourly notifications
                    WorkUsersSessionsStates session = context.getCurrentSession(username, userId);
                    Integer finalMinutes = null;

                    if (session != null) {
                        finalMinutes = session.getFinalWorkedMinutes();
                    }

                    context.getBackupService().registerScheduleEndNotification(username, userId, finalMinutes);
                }
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully tracked notification for user %s", username));

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error tracking notification display for user %s: %s", username, e.getMessage()));
            return null;
        }
    }
}