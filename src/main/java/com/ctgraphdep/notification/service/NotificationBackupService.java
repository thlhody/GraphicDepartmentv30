package com.ctgraphdep.notification.service;


import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.monitoring.MonitoringStateService;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationEventPublisher;
import com.ctgraphdep.notification.events.*;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Service for handling notification backup and auto-dismiss functionality.
 * Uses MonitoringStateService for state management.
 */
@Service
public class NotificationBackupService {

    private final MonitoringStateService monitoringStateService;
    private final TimeValidationService timeValidationService;
    private final TaskScheduler taskScheduler;
    private final NotificationEventPublisher eventPublisher;
    private final SchedulerHealthMonitor healthMonitor;

    // Track scheduled backup tasks
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public NotificationBackupService(
            MonitoringStateService monitoringStateService, TimeValidationService timeValidationService,
            @Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler,
            NotificationEventPublisher eventPublisher,
            SchedulerHealthMonitor healthMonitor) {

        this.monitoringStateService = monitoringStateService;
        this.timeValidationService = timeValidationService;
        this.taskScheduler = taskScheduler;
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
        // Debug the scheduled time to verify
        LocalDateTime scheduledTime = now.plusMinutes(11);

        // Record notification time in the centralized state service
        monitoringStateService.recordNotificationTime(username, WorkCode.SCHEDULE_END_TYPE);


        LoggerUtil.debug(this.getClass(),
                String.format("Scheduling backup notification for %s at %s (in %d minutes)",
                        username, scheduledTime, 11));

        // Schedule backup task for monitoring purposes
        ScheduledFuture<?> task = taskScheduler.schedule(
                () -> handleScheduleEndBackup(username, userId),
                scheduledTime.atZone(ZoneId.systemDefault()).toInstant()
        );

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

        // Record notification time in the centralized state service
        monitoringStateService.recordNotificationTime(username, WorkCode.HOURLY_TYPE);

        // Schedule backup task for monitoring purposes
        ScheduledFuture<?> task = taskScheduler.schedule(() -> handleHourlyWarningBackup(username, userId), toInstantWithDelay(now));
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

        // Record notification time in the centralized state service
        monitoringStateService.recordNotificationTime(username, WorkCode.TEMP_STOP_TYPE);

        // Schedule backup task for monitoring purposes
        ScheduledFuture<?> task = taskScheduler.schedule(() -> handleTempStopBackup(username, userId, tempStopStart), toInstantWithDelay(now));
        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(), String.format("Registered temp stop notification backup for user %s", username));
    }

    /**
     * Cancels existing backup tasks for a user when they've taken action through the normal UI
     */
    public void cancelBackupTask(String username) {
        cancelExistingTask(username);
        LoggerUtil.info(this.getClass(), String.format("Cancelled backup notification for user %s (user responded)", username));
    }

    // Handler methods for backup tasks
    private void handleScheduleEndBackup(String username, Integer userId) {
        try {
            // Check if this backup task has been cancelled but is still executing
            if (!scheduledTasks.containsKey(username)) {
                LoggerUtil.debug(this.getClass(), String.format("Skipping cancelled backup task for user %s", username));
                return;
            }

            // Only log at debug level that we're handling the backup
            LoggerUtil.debug(this.getClass(),
                    String.format("Handling schedule end backup for user %s", username));

            // Only re-show if this user should still be notified
            if (shouldReShowNotification(username, WorkCode.SCHEDULE_END_TYPE)) {
                // Log before attempting to re-publish
                LoggerUtil.debug(this.getClass(),
                        String.format("Attempting to re-publish schedule end event for user %s", username));

                // Re-publish schedule end event to try showing it again
                ScheduleEndEvent event = new ScheduleEndEvent(username, userId, null);
                eventPublisher.publishEvent(event);
            }
        } catch (Exception e) {
            // Keep error level logging for genuine errors
            LoggerUtil.error(this.getClass(),
                    String.format("Error in schedule end backup handler for %s: %s",
                            username, e.getMessage()));
        } finally {
            // Record task execution in health monitor
            healthMonitor.recordTaskExecution("notification-backup-service");
        }
    }

    private void handleHourlyWarningBackup(String username, Integer userId) {
        try {
            // If needed, re-publish hourly warning event
            if (shouldReShowNotification(username, WorkCode.HOURLY_TYPE)) {
                HourlyWarningEvent event = new HourlyWarningEvent(username, userId, null);
                eventPublisher.publishEvent(event);
                LoggerUtil.info(this.getClass(), String.format("Re-published hourly warning event for user %s", username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in hourly warning backup handler for %s: %s", username, e.getMessage()));
        } finally {
            // Record task execution in health monitor
            healthMonitor.recordTaskExecution("notification-backup-service");
        }
    }

    private void handleTempStopBackup(String username, Integer userId, LocalDateTime tempStopStart) {
        try {
            if (shouldReShowNotification(username, WorkCode.TEMP_STOP_TYPE)) {
                // Re-publish temporary stop warning event
                TempStopWarningEvent event = new TempStopWarningEvent(username, userId, tempStopStart);
                eventPublisher.publishEvent(event);
                LoggerUtil.info(this.getClass(), String.format("Re-published temp stop warning event for user %s", username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in temp stop backup handler for %s: %s", username, e.getMessage()));
        } finally {
            // Record task execution in health monitor
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

    private boolean shouldReShowNotification(String username, String notificationType) {
        // Limit the number of re-shows to 3
        int maxReShows = 3;

        // Use centralized state service to track and increment the count
        int count = monitoringStateService.incrementNotificationCount(username, notificationType, maxReShows);

        // Return whether we should re-show (count < maxReShows)
        if (count >= maxReShows) {
            LoggerUtil.info(this.getClass(), String.format("Maximum re-shows reached for %s notification for user %s", notificationType, username));
            return false;
        }

        return true;
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

            // Clear task map
            scheduledTasks.clear();

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

            LoggerUtil.debug(this.getClass(), "Notification backup service heartbeat. Active tasks: " + scheduledTasks.size());
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
     * @return An Instant representing the dateTime plus the delay
     */
    private Instant toInstantWithDelay(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().plus(Duration.ofMinutes(6));
    }
}