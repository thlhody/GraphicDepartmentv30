package com.ctgraphdep.notification;

import com.ctgraphdep.enums.NotificationType;
import com.ctgraphdep.model.dto.QueuedNotification;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.service.SystemNotificationService;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Queue-based notification manager that ensures reliability and proper ordering
 * of system notifications.
 */
@Component
public class NotificationQueueManager {

    private final PriorityQueue<QueuedNotification> notificationQueue;
    private final ReentrantLock queueLock = new ReentrantLock();
    private final ConcurrentHashMap<String, QueuedNotification> pendingNotifications = new ConcurrentHashMap<>();
    private final SystemNotificationService notificationService;
    private final SchedulerHealthMonitor healthMonitor;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> processingTask;

    @Autowired
    public NotificationQueueManager(
            SystemNotificationService notificationService,
            @Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler,
            SchedulerHealthMonitor healthMonitor) {

        this.notificationService = notificationService;
        this.taskScheduler = taskScheduler;
        this.healthMonitor = healthMonitor;

        // Initialize priority queue sorted by priority (higher first), then creation time
        this.notificationQueue = new PriorityQueue<>(Comparator.comparingInt(QueuedNotification::getPriority).reversed()
                        .thenComparing(QueuedNotification::getCreatedAt)
        );
    }

    @PostConstruct
    public void init() {
        // Register this service with the health monitor
        healthMonitor.registerTask(
                "notification-queue-processor",
                1, // Expected to run every minute
                status -> {
                    // Recovery action: restart processing task if it's unhealthy
                    if (processingTask == null || processingTask.isCancelled()) {
                        startProcessingTask();
                    }
                }
        );

        // Start the processing task
        startProcessingTask();
    }

    /**
     * Starts or restarts the notification processing task
     */
    private void startProcessingTask() {
        // Cancel existing task if it exists
        if (processingTask != null && !processingTask.isCancelled()) {
            processingTask.cancel(false);
        }

        // Schedule new processing task to run every 5 seconds
        processingTask = taskScheduler.scheduleAtFixedRate(
                this::processNotificationQueue,
                Instant.now().plusMillis(1000), // Start after 1 second
                Duration.ofSeconds(5) // Then every 5 seconds
        );

        LoggerUtil.info(this.getClass(), "Notification queue processing task started");
    }

    /**
     * Adds a notification to the queue
     *
     * @param type         Type of notification
     * @param username     Username to display notification to
     * @param userId       User ID
     * @param finalMinutes Final worked minutes (if applicable)
     * @param priority     Priority (higher values = processed first)
     * @return Unique ID for the queued notification
     */
    public String enqueueNotification(NotificationType type, String username, Integer userId,
                                      Integer finalMinutes, int priority) {
        queueLock.lock();
        try {
            // Create notification
            QueuedNotification notification = new QueuedNotification(
                    type, username, userId, finalMinutes, priority);

            // Add to queue
            notificationQueue.add(notification);

            // Track by ID
            pendingNotifications.put(notification.getId(), notification);

            LoggerUtil.info(this.getClass(),
                    String.format("Notification queued: %s, Type: %s, User: %s (Queue size: %d)",
                            notification.getId(), type, username, notificationQueue.size()));

            return notification.getId();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Adds a notification to the queue with default priority (5)
     */
    @SuppressWarnings("unused")
    public String enqueueNotification(NotificationType type, String username, Integer userId,
                                      Integer finalMinutes) {
        return enqueueNotification(type, username, userId, finalMinutes, 5);
    }

    /**
     * Enqueues a temporary stop notification
     */
    @SuppressWarnings("unused")
    public String enqueueTempStopNotification(String username, Integer userId, LocalDateTime tempStopStart) {
        queueLock.lock();
        try {
            // Create notification
            QueuedNotification notification = new QueuedNotification(NotificationType.TEMP_STOP, username, userId, null, 7);
            notification.setTempStopStart(tempStopStart);

            // Add to queue
            notificationQueue.add(notification);

            // Track by ID
            pendingNotifications.put(notification.getId(), notification);

            LoggerUtil.info(this.getClass(),
                    String.format("Temp stop notification queued: %s, User: %s (Queue size: %d)",
                            notification.getId(), username, notificationQueue.size()));

            return notification.getId();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Enqueues a resolution reminder notification
     */
    @SuppressWarnings("unused")
    public String enqueueResolutionReminder(String username, Integer userId, String title,
                                            String message, String trayMessage, Integer timeoutPeriod) {
        queueLock.lock();
        try {
            // Create notification
            QueuedNotification notification = new QueuedNotification(
                    NotificationType.RESOLUTION, username, userId, null, 8);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setTrayMessage(trayMessage);
            notification.setTimeoutPeriod(timeoutPeriod);

            // Add to queue
            notificationQueue.add(notification);

            // Track by ID
            pendingNotifications.put(notification.getId(), notification);

            LoggerUtil.info(this.getClass(),
                    String.format("Resolution reminder queued: %s, User: %s (Queue size: %d)",
                            notification.getId(), username, notificationQueue.size()));

            return notification.getId();
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Enqueues a start day reminder notification
     */
    @SuppressWarnings("unused")
    public String enqueueStartDayReminder(String username, Integer userId) {
        return enqueueNotification(NotificationType.START_DAY, username, userId, null, 9);
    }

    /**
     * Cancels a pending notification if it hasn't been processed yet
     *
     * @param notificationId The ID of the notification to cancel
     * @return True if the notification was cancelled, false if not found or already processed
     */
    public boolean cancelNotification(String notificationId) {
        queueLock.lock();
        try {
            QueuedNotification notification = pendingNotifications.get(notificationId);
            if (notification != null && !notification.isProcessed()) {
                notificationQueue.remove(notification);
                pendingNotifications.remove(notificationId);
                LoggerUtil.info(this.getClass(), "Cancelled notification: " + notificationId);
                return true;
            }
            return false;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Processes the next notification in the queue
     * Called on a fixed schedule, but can also be triggered manually
     */
    @Scheduled(fixedRate = 5000) // Run every 5 seconds
    public void processNotificationQueue() {
        // Record health check execution
        healthMonitor.recordTaskExecution("notification-queue-processor");

        // Check if we can acquire the lock without blocking
        if (!queueLock.tryLock()) {
            LoggerUtil.debug(this.getClass(), "Queue is locked, skipping processing cycle");
            return;
        }

        try {
            // Process up to 3 notifications per cycle to avoid blocking too long
            int processed = 0;
            while (!notificationQueue.isEmpty() && processed < 3) {
                QueuedNotification notification = notificationQueue.poll();
                if (notification != null) {
                    // Release lock during potentially lengthy processing
                    queueLock.unlock();

                    try {
                        processNotification(notification);
                        processed++;
                    } catch (Exception e) {
                        LoggerUtil.error(this.getClass(),
                                String.format("Error processing notification %s: %s",
                                        notification.getId(), e.getMessage()));

                        // Handle retry logic
                        handleNotificationFailure(notification, e);
                    } finally {
                        // Re-acquire lock for the next iteration
                        queueLock.lock();
                    }
                }
            }

            if (processed > 0) {
                LoggerUtil.info(this.getClass(),
                        String.format("Processed %d notifications, %d remaining in queue",
                                processed, notificationQueue.size()));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in notification queue processing: " + e.getMessage(), e);
            // Record failure in health monitor
            healthMonitor.recordTaskFailure("notification-queue-processor", e.getMessage());
        } finally {
            // Always release the lock
            if (queueLock.isHeldByCurrentThread()) {
                queueLock.unlock();
            }
        }
    }

    /**
     * Processes a single notification
     */
    private void processNotification(QueuedNotification notification) {
        LoggerUtil.info(this.getClass(),
                String.format("Processing notification: %s, Type: %s, User: %s",
                        notification.getId(), notification.getType(), notification.getUsername()));

        boolean success;

        try {
            success = switch (notification.getType()) {
                case SESSION_WARNING -> notificationService.showSessionWarning(
                        notification.getUsername(), notification.getUserId(), notification.getFinalMinutes());
                case HOURLY_WARNING -> notificationService.showHourlyWarning(
                        notification.getUsername(), notification.getUserId(), notification.getFinalMinutes());
                case TEMP_STOP -> notificationService.showLongTempStopWarning(
                        notification.getUsername(), notification.getUserId(), notification.getTempStopStart());
                case START_DAY -> notificationService.showStartDayReminder(
                        notification.getUsername(), notification.getUserId());
                case RESOLUTION -> notificationService.showResolutionReminder(
                        notification.getUsername(), notification.getUserId(),
                        notification.getTitle(), notification.getMessage(),
                        notification.getTrayMessage(), notification.getTimeoutPeriod());
            };

            notification.setProcessed(true);

            if (success) {
                LoggerUtil.info(this.getClass(),
                        String.format("Successfully processed notification: %s", notification.getId()));

                // Remove from pending notifications
                pendingNotifications.remove(notification.getId());

                // Record successful notification
                healthMonitor.recordTaskExecution("notification-queue-processor");
            } else {
                LoggerUtil.warn(this.getClass(),
                        String.format("Notification service returned false for %s", notification.getId()));

                // Record failure to help identify patterns
                healthMonitor.recordTaskFailure("notification-queue-processor", "Notification display failed");
                throw new RuntimeException("Notification display failed");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error displaying notification %s: %s", notification.getId(), e.getMessage()));

            // Record failure
            healthMonitor.recordTaskFailure("notification-queue-processor", e.getMessage());
            throw e; // Rethrow for retry handling
        }
    }

    /**
     * Handles notification failure by implementing retry logic
     */
    private void handleNotificationFailure(QueuedNotification notification, Exception error) {
        queueLock.lock();
        try {
            notification.incrementRetryCount();
            notification.setLastError(error.getMessage());

            // If we haven't reached max retries, add back to queue with reduced priority
            if (notification.getRetryCount() < notification.getMaxRetries()) {
                // Reduce priority slightly for retries but maintain relative ordering
                notification.setPriority(Math.max(1, notification.getPriority() - 1));

                // Add back to queue
                notificationQueue.add(notification);

                LoggerUtil.warn(this.getClass(),
                        String.format("Requested notification %s for retry %d/%d",
                                notification.getId(), notification.getRetryCount(), notification.getMaxRetries()));
            } else {
                LoggerUtil.error(this.getClass(),
                        String.format("Notification %s failed after %d retries, giving up",
                                notification.getId(), notification.getMaxRetries()));

                // Remove from pending notifications
                pendingNotifications.remove(notification.getId());
            }
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Clears all pending notifications from the queue.
     * Used during system reset to ensure a clean state.
     *
     * @return The number of notifications that were cleared
     */
    public int clearQueue() {
        queueLock.lock();
        try {
            int count = notificationQueue.size();
            notificationQueue.clear();
            pendingNotifications.clear();

            // Record successful operation in health monitor
            healthMonitor.recordTaskExecution("notification-queue-processor");

            LoggerUtil.info(this.getClass(), "Cleared notification queue with " + count + " pending items");
            return count;
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Gets the current queue size
     */
    public int getQueueSize() {
        queueLock.lock();
        try {
            return notificationQueue.size();
        } finally {
            queueLock.unlock();
        }
    }

}