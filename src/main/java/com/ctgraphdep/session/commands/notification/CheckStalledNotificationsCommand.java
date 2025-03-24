package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;
import java.util.Map;

public class CheckStalledNotificationsCommand implements SessionCommand<Void> {

    // Constants for stalled notification thresholds (in minutes)
    private static final int SCHEDULE_END_THRESHOLD = 15;
    private static final int HOURLY_WARNING_THRESHOLD = 12;
    private static final int TEMP_STOP_THRESHOLD = 10;

    @Override
    public Void execute(SessionContext context) {
        // Get standardized time values using the validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
        LocalDateTime currentTime = timeValues.getCurrentTime();

        // Handle schedule end notifications
        checkStalledNotifications(
                context,
                context.getBackupService().getStalledScheduleEndNotifications(),
                "SCHEDULE_END",
                SCHEDULE_END_THRESHOLD,
                currentTime
        );

        // Handle hourly warning notifications
        checkStalledNotifications(
                context,
                context.getBackupService().getStalledHourlyNotifications(),
                "HOURLY_WARNING",
                HOURLY_WARNING_THRESHOLD,
                currentTime
        );

        // Handle temporary stop notifications
        checkStalledNotifications(
                context,
                context.getBackupService().getStalledTempStopNotifications(),
                "TEMP_STOP",
                TEMP_STOP_THRESHOLD,
                currentTime
        );

        return null;
    }

    /**
     * Checks and handles stalled notifications of a specific type
     *
     * @param context The session context
     * @param notifications Map of username to notification time
     * @param notificationType The type of notification being checked
     * @param thresholdMinutes The number of minutes that must pass before considering notification stalled
     * @param currentTime The current time
     */
    private void checkStalledNotifications(
            SessionContext context,
            Map<String, LocalDateTime> notifications,
            String notificationType,
            int thresholdMinutes,
            LocalDateTime currentTime) {

        notifications.forEach((username, time) -> {
            // If notification is stalled
            if (isNotificationStalled(context, time, currentTime, thresholdMinutes)) {
                try {
                    // Get user
                    User user = context.getUserService().getUserByUsername(username).orElse(null);
                    if (user != null) {
                        // Get session
                        WorkUsersSessionsStates session = context.getCurrentSession(username, user.getUserId());

                        // If session is active, handle it based on notification type
                        if (isSessionActive(session, notificationType)) {
                            LoggerUtil.warn(this.getClass(),
                                    String.format("Detected stalled %s notification for user %s",
                                            notificationType, username));

                            // Remove stalled notification based on type
                            removeNotification(context, username, notificationType);
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            String.format("Error handling stalled %s notification for %s: %s",
                                    notificationType, username, e.getMessage()));
                }
            }
        });
    }

    /**
     * Removes a stalled notification based on its type
     *
     * @param context The session context
     * @param username The username associated with the notification
     * @param notificationType The type of notification to remove
     */
    private void removeNotification(SessionContext context, String username, String notificationType) {
        switch (notificationType) {
            case "SCHEDULE_END":
                context.getBackupService().removeScheduleEndNotification(username);
                break;
            case "HOURLY_WARNING":
                context.getBackupService().removeHourlyNotification(username);
                context.getBackupService().cancelBackupTask(username);
                break;
            case "TEMP_STOP":
                context.getBackupService().removeTempStopNotification(username);
                context.getBackupService().cancelBackupTask(username);
                break;
            default:
                LoggerUtil.warn(this.getClass(), "Unknown notification type: " + notificationType);
        }
    }

    /**
     * Determines if a notification is stalled based on the time threshold
     *
     * @param context The session context
     * @param notificationTime The notification timestamp
     * @param currentTime The current time
     * @param thresholdMinutes The number of minutes that must pass
     * @return true if the notification is stalled, false otherwise
     */
    private boolean isNotificationStalled(
            SessionContext context,
            LocalDateTime notificationTime,
            LocalDateTime currentTime,
            int thresholdMinutes) {
        int minutesSince = context.calculateMinutesBetween(notificationTime, currentTime);
        return minutesSince >= thresholdMinutes;
    }

    /**
     * Checks if a session is in active status appropriate for the notification type
     *
     * @param session The session to check
     * @param notificationType The type of notification
     * @return true if the session is in the appropriate active state, false otherwise
     */
    private boolean isSessionActive(WorkUsersSessionsStates session, String notificationType) {
        if (session == null) {
            return false;
        }

        return switch (notificationType) {
            case "SCHEDULE_END", "HOURLY_WARNING" -> WorkCode.WORK_ONLINE.equals(session.getSessionStatus());
            case "TEMP_STOP" -> WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());
            default -> false;
        };
    }
}