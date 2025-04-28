package com.ctgraphdep.notification.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility component for monitoring notification state.
 * This component doesn't have service dependencies to avoid circular dependencies.
 */
@Component
public class NotificationMonitorService {

    // Maps to track notification state
    private final Map<String, Map<String, Integer>> notificationCountMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> scheduleNotificationShownMap = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastHourlyWarningMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> continuedAfterScheduleMap = new ConcurrentHashMap<>();
    /**
     * -- GETTER --
     *  Gets the last notification times map (for command queries)
     */
    @Getter
    private final Map<String, LocalDateTime> lastNotificationTimes = new ConcurrentHashMap<>();

    // Maps to track auto-dismiss state
    @Getter
    private final Map<String, LocalDateTime> tempStopNotificationTimes = new ConcurrentHashMap<>();

    public NotificationMonitorService() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Checks if a schedule notification has already been shown
     */
    public boolean wasScheduleNotificationShownToday(String username) {
        return scheduleNotificationShownMap.getOrDefault(username, false);
    }

    /**
     * Checks if an hourly notification is due
     */
    public boolean isHourlyNotificationDue(String username, LocalDateTime currentTime) {
        // Only check if user has already continued after schedule
        if (!continuedAfterScheduleMap.getOrDefault(username, false)) {
            return false;
        }

        LocalDateTime lastWarning = lastHourlyWarningMap.get(username);
        if (lastWarning == null) {
            return false;
        }

        // Check if it's time for next hourly notification
        LocalDateTime nextHourlyTime = lastWarning.plusMinutes(WorkCode.HOURLY_INTERVAL);
        return currentTime.isAfter(nextHourlyTime);
    }

    /**
     * Checks if a temporary stop notification is due
     */
    public boolean isTempStopNotificationDue(String username, LocalDateTime tempStopStart,
                                             int minutesSinceTempStop, LocalDateTime currentTime) {
        if (tempStopStart == null) {
            return false;
        }

        // Get last notification time for this type
        String key = getNotificationKey(username, WorkCode.TEMP_STOP_TYPE);
        LocalDateTime lastNotification = lastNotificationTimes.get(key);

        // If enough time has passed since last notification and total time is a multiple of hourly interval
        return (lastNotification == null ||
                currentTime.isAfter(lastNotification.plusMinutes(WorkCode.HOURLY_INTERVAL - 2))) &&
                (minutesSinceTempStop >= WorkCode.HOURLY_INTERVAL) &&
                (minutesSinceTempStop % WorkCode.HOURLY_INTERVAL <= 5);
    }

    /**
     * Marks that a schedule notification was shown
     */
    public void markScheduleNotificationShown(String username) {
        scheduleNotificationShownMap.put(username, true);

        // Initialize notification count map if needed
        if (!notificationCountMap.containsKey(username)) {
            notificationCountMap.put(username, new ConcurrentHashMap<>());
        }

        // Update schedule notification count
        Map<String, Integer> userCounts = notificationCountMap.get(username);
        int currentCount = userCounts.getOrDefault(WorkCode.SCHEDULE_END_TYPE, 0);
        userCounts.put(WorkCode.SCHEDULE_END_TYPE, currentCount + 1);

        LoggerUtil.info(this.getClass(), String.format("Marked schedule notification shown for user %s", username));
    }

    /**
     * Marks hourly monitoring as active for a user
     */
    public void markHourlyMonitoringActive(String username, LocalDateTime timestamp) {
        continuedAfterScheduleMap.put(username, true);
        lastHourlyWarningMap.put(username, timestamp);
        LoggerUtil.info(this.getClass(), String.format("Activated hourly monitoring for user %s", username));
    }

    /**
     * Records an hourly notification timestamp
     */
    public void recordHourlyNotification(String username, LocalDateTime timestamp) {
        lastHourlyWarningMap.put(username, timestamp);
        LoggerUtil.debug(this.getClass(), String.format("Recorded hourly notification for user %s at %s", username, timestamp));
    }

    /**
     * Records the time a notification was shown (for rate limiting)
     */
    public void recordNotificationTime(String username, String notificationType) {
        String key = getNotificationKey(username, notificationType);
        lastNotificationTimes.put(key, LocalDateTime.now());
        LoggerUtil.debug(this.getClass(), String.format("Recorded notification time for %s - %s", username, notificationType));
    }

    /**
     * Records a temporary stop notification time
     */
    public void recordTempStopNotification(String username, LocalDateTime timestamp) {
        tempStopNotificationTimes.put(username, timestamp);
        LoggerUtil.debug(this.getClass(), String.format("Recorded temp stop notification for user %s at %s", username, timestamp));
    }

    /**
     * Checks if a notification can be shown based on rate limiting
     */
    public boolean canShowNotification(String username, String notificationType, int intervalMinutes) {
        String key = getNotificationKey(username, notificationType);
        LocalDateTime lastTime = lastNotificationTimes.get(key);
        LocalDateTime now = LocalDateTime.now();

        // If no previous notification, allow
        if (lastTime == null) {
            return true;
        }

        // Calculate minutes since last notification
        long minutesSinceLastNotification = java.time.temporal.ChronoUnit.MINUTES.between(lastTime, now);

        // Check if enough time has passed
        return minutesSinceLastNotification >= intervalMinutes;
    }

    /**
     * Gets the notification count for a specific user and type
     */
    public int getNotificationCount(String username, String notificationType) {
        if (!notificationCountMap.containsKey(username)) {
            return 0;
        }

        return notificationCountMap.get(username).getOrDefault(notificationType, 0);
    }

    /**
     * Increments and returns the notification count for a specific user and type
     * @param username The username
     * @param notificationType The type of notification
     * @param maxCount Maximum count allowed
     * @return The updated count after increment
     */
    public int incrementNotificationCount(String username, String notificationType, int maxCount) {
        // Initialize the map structure if it doesn't exist
        if (!notificationCountMap.containsKey(username)) {
            notificationCountMap.put(username, new ConcurrentHashMap<>());
        }

        // Get the user's count map
        Map<String, Integer> userCounts = notificationCountMap.get(username);

        // Get current count
        int currentCount = userCounts.getOrDefault(notificationType, 0);

        // Increment count if below max
        if (currentCount < maxCount) {
            currentCount++;
            userCounts.put(notificationType, currentCount);
            LoggerUtil.debug(this.getClass(), String.format("Incremented notification count for %s-%s to %d",
                    username, notificationType, currentCount));
        }

        return currentCount;
    }
    /**
     * Clears notification state for a user
     */
    public void clearUserState(String username) {
        scheduleNotificationShownMap.remove(username);
        continuedAfterScheduleMap.remove(username);
        lastHourlyWarningMap.remove(username);
        tempStopNotificationTimes.remove(username);
        notificationCountMap.remove(username);

        // Remove all keys for this user from lastNotificationTimes
        lastNotificationTimes.entrySet().removeIf(entry -> entry.getKey().startsWith(username + "_"));

        LoggerUtil.info(this.getClass(), String.format("Cleared notification tracking for user %s", username));
    }

    /**
     * Gets a unique key for a notification based on username and type
     */
    private String getNotificationKey(String username, String notificationType) {
        return username + "_" + notificationType;
    }

}