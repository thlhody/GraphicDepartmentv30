package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
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
    private final Map<String, Boolean> notificationShown = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastHourlyWarning = new ConcurrentHashMap<>();
    private final Map<String, Boolean> continuedAfterSchedule = new ConcurrentHashMap<>();

    public NotificationMonitorService() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Checks if a schedule notification has already been shown
     */
    public boolean isScheduleNotificationShown(String username) {
        // Check if we've shown schedule end notification
        return notificationCountMap.getOrDefault(username, new ConcurrentHashMap<>())
                .getOrDefault(WorkCode.SCHEDULE_END_TYPE, 0) > 0 ||
                notificationShown.getOrDefault(username, false);
    }

    /**
     * Checks if user has continued after schedule and should receive hourly notifications
     */
    public boolean shouldShowHourlyNotification(String username, LocalDateTime now) {
        // Only check if user has already continued after schedule
        if (!continuedAfterSchedule.getOrDefault(username, false)) {
            return false;
        }

        LocalDateTime lastWarning = lastHourlyWarning.get(username);
        if (lastWarning == null) {
            return false;
        }

        // Check if it's time for next hourly notification
        LocalDateTime nextHourlyTime = lastWarning.plusMinutes(WorkCode.HOURLY_INTERVAL);
        return now.isAfter(nextHourlyTime);
    }

    /**
     * Checks if temporary stop has exceeded notification threshold
     */
    public boolean shouldShowTempStopNotification(String username, LocalDateTime tempStopStart,
                                                  int minutesSinceTempStop, LocalDateTime lastNotification,
                                                  LocalDateTime now) {
        if (tempStopStart == null) {
            return false;
        }

        // If enough time has passed since last notification and total time is a multiple of hourly interval
        return (lastNotification == null ||
                now.isAfter(lastNotification.plusMinutes(WorkCode.HOURLY_INTERVAL - 2))) &&
                (minutesSinceTempStop >= WorkCode.HOURLY_INTERVAL) &&
                (minutesSinceTempStop % WorkCode.HOURLY_INTERVAL <= 5);
    }

    /**
     * Updates session continuation tracking when a user opts to continue working
     */
    public void activateHourlyMonitoring(String username, LocalDateTime timestamp) {
        continuedAfterSchedule.put(username, true);
        lastHourlyWarning.put(username, timestamp);
        LoggerUtil.info(this.getClass(), "Activated hourly monitoring for user: " + username);
    }

    /**
     * Record that a schedule notification was shown
     */
    public void recordScheduleNotificationShown(String username) {
        notificationShown.put(username, true);

        // Initialize notification count map if needed
        if (!notificationCountMap.containsKey(username)) {
            notificationCountMap.put(username, new ConcurrentHashMap<>());
        }

        // Update schedule notification count
        Map<String, Integer> userCounts = notificationCountMap.get(username);
        int currentCount = userCounts.getOrDefault(WorkCode.SCHEDULE_END_TYPE, 0);
        userCounts.put(WorkCode.SCHEDULE_END_TYPE, currentCount + 1);
    }

    /**
     * Update hourly notification timestamp
     */
    public void recordHourlyNotification(String username, LocalDateTime timestamp) {
        lastHourlyWarning.put(username, timestamp);
    }

    /**
     * Clears notification state for a user
     */
    public void clearNotificationState(String username) {
        notificationShown.remove(username);
        continuedAfterSchedule.remove(username);
        lastHourlyWarning.remove(username);
        notificationCountMap.remove(username);
        LoggerUtil.info(this.getClass(), "Cleared notification tracking for user: " + username);
    }
}