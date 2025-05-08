package com.ctgraphdep.notification.service;

import com.ctgraphdep.monitoring.MonitoringStateService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Utility component for monitoring notification state.
 * This component doesn't have service dependencies to avoid circular dependencies.
 */
@Component
public class NotificationMonitorService {

    private final MonitoringStateService monitoringStateService;

    public NotificationMonitorService(MonitoringStateService monitoringStateService) {
        this.monitoringStateService = monitoringStateService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Checks if a schedule notification has already been shown
     */
    public boolean wasScheduleNotificationShownToday(String username) {
        return monitoringStateService.wasScheduleNotificationShown(username);
    }
    /**
     * Checks if an hourly notification is due
     */
    public boolean isHourlyNotificationDue(String username, LocalDateTime currentTime) {
        return monitoringStateService.isHourlyNotificationDue(username, currentTime);
    }

    /**
     * Checks if a temporary stop notification is due
     */
    public boolean isTempStopNotificationDue(String username, int minutesSinceTempStop, LocalDateTime currentTime) {
        return monitoringStateService.isTempStopNotificationDue(username, minutesSinceTempStop, currentTime);
    }

    /**
     * Marks that a schedule notification was shown
     */
    public void markScheduleNotificationShown(String username) {
        monitoringStateService.markScheduleNotificationShown(username);
    }

    /**
     * Marks hourly monitoring as active for a user
     */
    public void markHourlyMonitoringActive(String username, LocalDateTime timestamp) {
        monitoringStateService.transitionToHourlyMonitoring(username, timestamp);
    }
    /**
     * Records an hourly notification timestamp
     */
    public void recordHourlyNotification(String username, LocalDateTime timestamp) {
        monitoringStateService.recordHourlyNotification(username, timestamp);
    }

    /**
     * Records the time a notification was shown (for rate limiting)
     */
    public void recordNotificationTime(String username, String notificationType) {
        monitoringStateService.recordNotificationTime(username, notificationType);
    }

    /**
     * Records a temporary stop notification time
     */
    public void recordTempStopNotification(String username, LocalDateTime timestamp) {
        monitoringStateService.recordTempStopNotification(username, timestamp);
    }

    /**
     * Gets the notification count for a specific user and type
     */
    public int getNotificationCount(String username, String notificationType) {
        return monitoringStateService.getNotificationCount(username, notificationType);
    }
}