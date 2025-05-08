package com.ctgraphdep.notification.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.monitoring.MonitoringStateService;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationEventPublisher;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.notification.events.*;
import com.ctgraphdep.notification.model.NotificationRequest;
import com.ctgraphdep.notification.model.NotificationResponse;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of NotificationService.
 * This service serves as the main entry point for notification operations,
 * using the event-based architecture to publish notification events.
 */
@Service
public class DefaultNotificationService implements NotificationService {

    private final NotificationEventPublisher eventPublisher;
    private final NotificationDisplayService displayService;
    private final TimeValidationService timeValidationService;
    private final NotificationBackupService backupService;
    private final SchedulerHealthMonitor healthMonitor;
    private final MonitoringStateService monitoringStateService;
    private final NotificationConfigService configService;

    private final AtomicLong totalNotificationsShown = new AtomicLong(0);
    private final Map<String, LocalDateTime> pendingNotifications = new HashMap<>();

    public DefaultNotificationService(
            NotificationEventPublisher eventPublisher,
            NotificationDisplayService displayService,
            TimeValidationService timeValidationService,
            NotificationBackupService backupService,
            SchedulerHealthMonitor healthMonitor, MonitoringStateService monitoringStateService, NotificationConfigService configService) {

        this.eventPublisher = eventPublisher;
        this.displayService = displayService;
        this.timeValidationService = timeValidationService;
        this.backupService = backupService;
        this.healthMonitor = healthMonitor;
        this.monitoringStateService = monitoringStateService;
        this.configService = configService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        // Register the display service as a subscriber
        eventPublisher.registerSubscriber(displayService);

        // Register with health monitor
        healthMonitor.registerTask(
                "notification-service",
                5, // Expected to run every 5 minutes
                status -> {
                    // Recovery action - reset notification service if unhealthy
                    LoggerUtil.warn(this.getClass(), "Attempting to recover notification service");
                    resetService();
                }
        );

        // Record initial execution
        healthMonitor.recordTaskExecution("notification-service");
        LoggerUtil.info(this.getClass(), "Notification service initialized");
    }

    @Override
    public NotificationResponse showNotification(NotificationRequest request) {
        try {
            // Check if notifications are enabled
            if (!configService.isNotificationsEnabled()) {
                LoggerUtil.info(this.getClass(), String.format("Notifications disabled in config. Skipping notification for user %s", request.getUsername()));
                return NotificationResponse.success("notifications-disabled", false, false);
            }

            LoggerUtil.info(this.getClass(), String.format("Showing notification: Type=%s, User=%s", request.getType(), request.getUsername()));
            // Record notification attempt
            totalNotificationsShown.incrementAndGet();
            healthMonitor.recordTaskExecution("notification-service");
            return displayService.showNotification(request);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing notification for user %s: %s", request.getUsername(), e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            return NotificationResponse.failure(e.getMessage());
        }
    }

    @Override
    public boolean showScheduleEndNotification(String username, Integer userId, Integer finalMinutes) {
        try {
            // Create and publish schedule end event
            ScheduleEndEvent event = new ScheduleEndEvent(username, userId, finalMinutes);
            eventPublisher.publishEvent(event);
            // Record pending notification
            pendingNotifications.put(username + "_schedule", getStandardCurrentTime());
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing schedule end notification for user %s: %s", username, e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean showHourlyWarning(String username, Integer userId, Integer finalMinutes) {
        try {
            // Create and publish hourly warning event
            HourlyWarningEvent event = new HourlyWarningEvent(username, userId, finalMinutes);
            eventPublisher.publishEvent(event);
            // Record pending notification
            pendingNotifications.put(username + "_hourly", getStandardCurrentTime());
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing hourly warning for user %s: %s", username, e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean showTempStopWarning(String username, Integer userId, LocalDateTime tempStopStart) {
        try {
            // Create and publish temp stop warning event
            TempStopWarningEvent event = new TempStopWarningEvent(username, userId, tempStopStart);
            eventPublisher.publishEvent(event);
            // Record pending notification
            pendingNotifications.put(username + "_tempstop",getStandardCurrentTime());
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing temp stop warning for user %s: %s", username, e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean showStartDayReminder(String username, Integer userId) {
        try {
            // Get standardized time values
            LocalDateTime currentTime = getStandardCurrentTime();

            // Check if current time is within business hours (5 AM to 5 PM)
            int hour = currentTime.getHour();
            if (hour < WorkCode.WORK_START_HOUR || hour >= WorkCode.WORK_END_HOUR) {
                LoggerUtil.info(this.getClass(),
                        String.format("Outside display hours (5-17) for start day reminder, current hour: %d", hour));
                return false;
            }

            // Create and publish start day reminder event
            StartDayReminderEvent event = new StartDayReminderEvent(username, userId);
            eventPublisher.publishEvent(event);
            // Record pending notification
            pendingNotifications.put(username + "_startday", getStandardCurrentTime());
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing start day reminder for user %s: %s", username, e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean showResolutionReminder(String username, Integer userId, String title, String message, String trayMessage, Integer timeoutPeriod) {
        try {
            // Create and publish resolution reminder event
            ResolutionReminderEvent event = new ResolutionReminderEvent(username, userId, title, message, trayMessage, timeoutPeriod);
            eventPublisher.publishEvent(event);
            // Record pending notification
            pendingNotifications.put(username + "_resolution", getStandardCurrentTime());
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing resolution reminder for user %s: %s", username, e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            return false;
        }
    }

    @Override
    public void recordNotificationTime(String username, String notificationType) {
        monitoringStateService.recordNotificationTime(username, notificationType);
    }

    @Override
    public boolean canShowNotification(String username, String notificationType, Integer intervalMinutes) {
        return monitoringStateService.canShowNotification(username, notificationType, intervalMinutes);
    }

    @Override
    public void resetService() {
        try {
            LoggerUtil.info(this.getClass(), "Resetting notification service");

            // Clear pending notifications
            pendingNotifications.clear();

            // Reset display service
            displayService.resetService();

            // Reset backup service
            backupService.resetService();

            // Mark service as healthy
            healthMonitor.recordTaskExecution("notification-service");

            LoggerUtil.info(this.getClass(), "Notification service reset completed");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resetting notification service: " + e.getMessage());
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
        }
    }

    /**
     * Heartbeat to check service health
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void heartbeat() {
        try {
            // Record that the service is alive
            healthMonitor.recordTaskExecution("notification-service");
            // Clean up old pending notifications (older than 30 minutes)
            LocalDateTime cutoff = getStandardCurrentTime().minusMinutes(30);
            pendingNotifications.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            LoggerUtil.debug(this.getClass(), String.format("Notification service heartbeat. Total notifications: %d, Pending: %d", totalNotificationsShown.get(), pendingNotifications.size()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in notification service heartbeat: " + e.getMessage());
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
        }
    }

    /**
     * For testing - shows a test notification
     */
    public boolean showTestNotification(String username) {
        try {
            // Create and publish test notification event
            TestNotificationEvent event = new TestNotificationEvent(username);
            eventPublisher.publishEvent(event);
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing test notification for user %s: %s", username, e.getMessage()), e);
            healthMonitor.recordTaskFailure("notification-service", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean cancelNotificationBackup(String username) {
        try {
            // Use the injected backupService to cancel backup tasks
            backupService.cancelBackupTask(username);
            LoggerUtil.info(this.getClass(), String.format("Canceled backup tasks for user %s", username));
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error canceling backup tasks for user %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    private LocalDateTime getStandardCurrentTime() {
        // Get standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentTime();
    }
}