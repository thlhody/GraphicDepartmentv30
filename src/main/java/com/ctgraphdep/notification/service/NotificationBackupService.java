package com.ctgraphdep.notification.service;


import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationEventPublisher;
import com.ctgraphdep.notification.events.*;
import com.ctgraphdep.service.DataAccessService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Service for handling notification backup and auto-dismiss functionality.
 * This service ensures notifications are properly handled even if the UI fails.
 */
@Service
public class NotificationBackupService {

    private final TaskScheduler taskScheduler;
    private final DataAccessService dataAccessService;
    private final TimeValidationService timeValidationService;
    private final NotificationEventPublisher eventPublisher;
    private final SchedulerHealthMonitor healthMonitor;

    // Track scheduled tasks for users
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private final Map<String, LocalDateTime> scheduleEndNotificationTimes = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> hourlyNotificationTimes = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> tempStopNotificationTimes = new ConcurrentHashMap<>();

    public NotificationBackupService(
            @Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler,
            DataAccessService dataAccessService, TimeValidationService timeValidationService,
            NotificationEventPublisher eventPublisher,
            SchedulerHealthMonitor healthMonitor) {
        this.taskScheduler = taskScheduler;
        this.dataAccessService = dataAccessService;
        this.timeValidationService = timeValidationService;
        this.eventPublisher = eventPublisher;
        this.healthMonitor = healthMonitor;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        // Register with health monitor including recovery action
        healthMonitor.registerTask(
                "notification-backup-service",
                5, // Check every 5 minutes
                status -> {
                    // Recovery action: Reset service state if unhealthy
                    LoggerUtil.warn(this.getClass(), "Attempting to recover notification backup service");
                    resetService();
                }
        );

        // Record initial execution to establish baseline
        healthMonitor.recordTaskExecution("notification-backup-service");
        LoggerUtil.info(this.getClass(), "Notification backup service initialized");
    }

    /**
     * Registers a scheduled end notification backup for a user.
     * Will track the notification but not automatically end session.
     */
    public void registerScheduleEndNotification(String username, Integer userId) {
        // Cancel any existing task
        cancelExistingTask(username);

        LocalDateTime now = getStandardCurrentTime();

        // Record notification time
        scheduleEndNotificationTimes.put(username, now);

        // Schedule backup task for monitoring purposes
        ScheduledFuture<?> task = taskScheduler.schedule(() -> handleScheduleEndBackup(username, userId), toInstantWithDelay(now, 11));
        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(), String.format("Registered schedule end notification backup for user %s", username));
    }

    /**
     * Registers an hourly warning notification backup.
     * Will track the notification but not automatically end session.
     */
    public void registerHourlyWarningNotification(String username, Integer userId) {
        // Cancel any existing task
        cancelExistingTask(username);
        LocalDateTime now = getStandardCurrentTime();

        // Record notification time
        hourlyNotificationTimes.put(username, now);

        // Schedule backup task for monitoring purposes
        ScheduledFuture<?> task = taskScheduler.schedule(() -> handleHourlyWarningBackup(username, userId), toInstantWithDelay(now, 6));
        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(), String.format("Registered hourly warning notification backup for user %s", username));
    }

    /**
     * Registers a temporary stop notification backup.
     * Will track notification status for temporary stops.
     */
    public void registerTempStopNotification(String username, Integer userId, LocalDateTime tempStopStart) {
        // Cancel any existing task
        cancelExistingTask(username);
        LocalDateTime now = getStandardCurrentTime();

        // Record notification time
        tempStopNotificationTimes.put(username, tempStopStart);

        // Schedule backup task for monitoring purposes
        ScheduledFuture<?> task = taskScheduler.schedule(() -> handleTempStopBackup(username, userId, tempStopStart), toInstantWithDelay(now, 6));
        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(), String.format("Registered temp stop notification backup for user %s", username));
    }

    /**
     * Cancels existing backup tasks for a user when they've taken action through the normal UI
     */
    public void cancelBackupTask(String username) {
        cancelExistingTask(username);

        // Remove notification records
        scheduleEndNotificationTimes.remove(username);
        hourlyNotificationTimes.remove(username);
        tempStopNotificationTimes.remove(username);

        LoggerUtil.info(this.getClass(), String.format("Cancelled backup notification for user %s (user responded)", username));
    }

    // Handler methods for backup tasks

    private void handleScheduleEndBackup(String username, Integer userId) {
        try {
            // If user hasn't responded to schedule end notification, record continuation
            LoggerUtil.info(this.getClass(), String.format("Handling schedule end backup for user %s", username));

            // Only re-show if this user should still be notified
            if (shouldReShowNotification(username, "SCHEDULE_END")) {
                // Re-publish schedule end event to try showing it again
                ScheduleEndEvent event = new ScheduleEndEvent(username, userId, null);
                eventPublisher.publishEvent(event);

                LoggerUtil.info(this.getClass(), String.format("Re-published schedule end event for user %s", username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in schedule end backup handler for %s: %s", username, e.getMessage()));
        } finally {
            // Always remove notification record after handling
            scheduleEndNotificationTimes.remove(username);
            healthMonitor.recordTaskExecution("notification-backup-service");
        }
    }

    private void handleHourlyWarningBackup(String username, Integer userId) {
        try {
            // Check for notification tracking file
            boolean trackingExists = checkTrackingFile(username);

            // If tracking file exists and is recent, defer backup action
            if (trackingExists && isTrackingFileRecent(username)) {
                LoggerUtil.info(this.getClass(), String.format("Deferring backup action for %s - notification still active", username));
                LocalDateTime now = getStandardCurrentTime();
                // Reschedule for another 2 minutes
                taskScheduler.schedule(() -> handleHourlyWarningBackup(username, userId), Instant.from(now.plus(Duration.ofMinutes(2))));
                return;
            }

            // If needed, re-publish hourly warning event
            if (shouldReShowNotification(username, "HOURLY_WARNING")) {
                HourlyWarningEvent event = new HourlyWarningEvent(username, userId, null);
                eventPublisher.publishEvent(event);
                LoggerUtil.info(this.getClass(), String.format("Re-published hourly warning event for user %s", username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in hourly warning backup handler for %s: %s", username, e.getMessage()));
        } finally {
            // Remove notification record
            hourlyNotificationTimes.remove(username);
            healthMonitor.recordTaskExecution("notification-backup-service");
        }
    }

    private void handleTempStopBackup(String username, Integer userId, LocalDateTime tempStopStart) {
        try {
            if (shouldReShowNotification(username, "TEMP_STOP")) {
                // Re-publish temporary stop warning event
                TempStopWarningEvent event = new TempStopWarningEvent(username, userId, tempStopStart);
                eventPublisher.publishEvent(event);
                LoggerUtil.info(this.getClass(), String.format("Re-published temp stop warning event for user %s", username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in temp stop backup handler for %s: %s", username, e.getMessage()));
        } finally {
            // Remove notification record
            tempStopNotificationTimes.remove(username);
            healthMonitor.recordTaskExecution("notification-backup-service");
        }
    }

    // Utility methods

    private void cancelExistingTask(String username) {
        ScheduledFuture<?> existingTask = scheduledTasks.remove(username);
        if (existingTask != null && !existingTask.isDone() && !existingTask.isCancelled()) {
            existingTask.cancel(false);
        }
    }

    private boolean checkTrackingFile(String username) {
        LocalDateTime timestamp = dataAccessService.readNotificationTrackingFile(username, "HOURLY_WARNING");
        return timestamp != null;
    }

    private boolean isTrackingFileRecent(String username) {
        LocalDateTime notificationTime = dataAccessService.readNotificationTrackingFile(username, "HOURLY_WARNING");

        if (notificationTime == null) {
            return false;
        }

        // Check if notification was shown less than maxMinutes ago
        return ChronoUnit.MINUTES.between(notificationTime, getStandardCurrentTime()) < 3;
    }

    private boolean shouldReShowNotification(String username, String notificationType) {
        // Limit the number of re-shows to 3
        int maxReShows = 3;

        // Use data access service to update and get count
        int count = dataAccessService.updateNotificationCountFile(username, notificationType, maxReShows);

        // Return whether we should re-show (count < maxReShows)
        if (count >= maxReShows) {
            LoggerUtil.info(this.getClass(), String.format("Maximum re-shows reached for %s notification for user %s", notificationType, username));
            return false;
        }

        return true;
    }

    /**
     * Gets stalled schedule end notifications for handling
     */
    public Map<String, LocalDateTime> getStalledScheduleEndNotifications() {
        return scheduleEndNotificationTimes;
    }

    /**
     * Gets stalled hourly warning notifications for handling
     */
    public Map<String, LocalDateTime> getStalledHourlyNotifications() {
        return hourlyNotificationTimes;
    }

    /**
     * Gets stalled temporary stop notifications for handling
     */
    public Map<String, LocalDateTime> getStalledTempStopNotifications() {
        return tempStopNotificationTimes;
    }

    /**
     * Resets service state (for recovery)
     */
    public void resetService() {
        try {
            LoggerUtil.info(this.getClass(), "Resetting notification backup service");

            // Cancel all scheduled tasks
            scheduledTasks.forEach((username, task) -> {
                if (task != null && !task.isDone() && !task.isCancelled()) {
                    task.cancel(false);
                }
            });

            // Clear all maps
            scheduledTasks.clear();
            scheduleEndNotificationTimes.clear();
            hourlyNotificationTimes.clear();
            tempStopNotificationTimes.clear();

            LoggerUtil.info(this.getClass(), "Notification backup service reset completed");

            // Record successful reset
            healthMonitor.recordTaskExecution("notification-backup-service");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error resetting notification backup service: %s", e.getMessage()));
            healthMonitor.recordTaskFailure("notification-backup-service", e.getMessage());
        }
    }

    /**
     * Regular heartbeat to indicate the service is healthy
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void heartbeat() {
        try {
            // Record that the service is alive
            healthMonitor.recordTaskExecution("notification-backup-service");

            // Log at debug level to avoid cluttering logs
            LoggerUtil.debug(this.getClass(), "Notification backup service heartbeat. " +
                    "Active tasks: " + scheduledTasks.size() +
                    ", Schedule notifications: " + scheduleEndNotificationTimes.size() +
                    ", Hourly notifications: " + hourlyNotificationTimes.size() +
                    ", Temp stop notifications: " + tempStopNotificationTimes.size());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in notification backup service heartbeat: " + e.getMessage());
            healthMonitor.recordTaskFailure("notification-backup-service", e.getMessage());
        }
    }

    private LocalDateTime getStandardCurrentTime() {
        // Get standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentTime();
    }
    /**
     * Converts a LocalDateTime to an Instant with the specified delay in minutes.
     *
     * @param dateTime The LocalDateTime to convert
     * @param delayMinutes The number of minutes to add
     * @return An Instant representing the dateTime plus the delay
     */
    private Instant toInstantWithDelay(LocalDateTime dateTime, int delayMinutes) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().plus(Duration.ofMinutes(delayMinutes));
    }
}