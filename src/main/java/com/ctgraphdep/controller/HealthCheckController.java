package com.ctgraphdep.controller;

import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.NotificationQueueManager;
import com.ctgraphdep.service.SessionMonitorService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for monitoring system health and performing manual operations
 * like forcing checks or clearing queues.
 */
@RestController
@RequestMapping("/api/health")
public class HealthCheckController {

    private final SchedulerHealthMonitor healthMonitor;
    private final SessionMonitorService sessionMonitorService;
    private final NotificationQueueManager notificationQueue;

    @Autowired
    public HealthCheckController(
            SchedulerHealthMonitor healthMonitor,
            SessionMonitorService sessionMonitorService,
            NotificationQueueManager notificationQueue) {

        this.healthMonitor = healthMonitor;
        this.sessionMonitorService = sessionMonitorService;
        this.notificationQueue = notificationQueue;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Get overall health status of all monitored tasks
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // Get health status of all monitored tasks
            status.put("tasks", healthMonitor.getHealthStatus());

            // Get notification queue size
            status.put("notificationQueueSize", notificationQueue.getQueueSize());

            // Add overall system health
            boolean allHealthy = healthMonitor.getHealthStatus().values().stream()
                    .allMatch(Boolean::booleanValue);
            status.put("healthy", allHealthy);

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting health status: " + e.getMessage());
            status.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(status);
        }
    }

    /**
     * Get detailed status for a specific monitored task
     */
    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Object> getTaskDetails(@PathVariable String taskId) {
        try {
            SchedulerHealthMonitor.TaskStatus status = healthMonitor.getTaskStatus(taskId);

            if (status == null) {
                return ResponseEntity.notFound().build();
            }

            // Create a map with task details
            Map<String, Object> details = new HashMap<>();
            details.put("healthy", status.isHealthy());
            details.put("lastExecutionTime", status.getLastExecutionTime());
            details.put("minutesSinceLastExecution", status.getMinutesSinceLastExecution());
            details.put("executionCount", status.getExecutionCount());
            details.put("consecutiveFailures", status.getConsecutiveFailures());
            details.put("expectedIntervalMinutes", status.getExpectedIntervalMinutes());
            details.put("lastError", status.getLastError());

            return ResponseEntity.ok(details);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting task details: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Force an immediate session check
     */
    @PostMapping("/force-session-check")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> forceSessionCheck() {
        Map<String, Object> result = new HashMap<>();

        try {
            LoggerUtil.info(this.getClass(), "Forcing immediate session check via API");

            // Execute session check directly
            sessionMonitorService.checkActiveSessions();

            result.put("success", true);
            result.put("message", "Session check executed successfully");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in forced session check: " + e.getMessage());

            result.put("success", false);
            result.put("error", e.getMessage());

            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Force a specific notification check
     */
    @PostMapping("/force-check/{checkType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> forceSpecificCheck(@PathVariable String checkType) {
        Map<String, Object> result = new HashMap<>();

        try {
            LoggerUtil.info(this.getClass(), "Forcing " + checkType + " check via API");

            switch (checkType.toLowerCase()) {
                case "startday":
                    sessionMonitorService.checkStartDayReminder();
                    result.put("message", "Start day reminder check executed");
                    break;

                case "status":
                    // Use a StatusStatusCleaner component if available
                    result.put("message", "Status cleaner not implemented in this endpoint");
                    break;

                case "scheduler":
                    // Trigger health monitor check
                    healthMonitor.checkTaskHealth();
                    result.put("message", "Scheduler health check executed");
                    break;

                default:
                    return ResponseEntity.badRequest().body(
                            Map.of("error", "Unknown check type: " + checkType));
            }

            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in forced check: " + e.getMessage());

            result.put("success", false);
            result.put("error", e.getMessage());

            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Process next notification in queue
     */
    @PostMapping("/process-notification")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> processNextNotification() {
        Map<String, Object> result = new HashMap<>();

        try {
            LoggerUtil.info(this.getClass(), "Processing next notification via API");

            // Get current queue size
            int sizeBefore = notificationQueue.getQueueSize();

            // Process one notification
            notificationQueue.processNotificationQueue();

            // Get new queue size
            int sizeAfter = notificationQueue.getQueueSize();

            result.put("success", true);
            result.put("queueSizeBefore", sizeBefore);
            result.put("queueSizeAfter", sizeAfter);
            result.put("notificationsProcessed", Math.max(0, sizeBefore - sizeAfter));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing notification: " + e.getMessage());

            result.put("success", false);
            result.put("error", e.getMessage());

            return ResponseEntity.internalServerError().body(result);
        }
    }
}