package com.ctgraphdep.monitoring;

import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Monitors the health of various scheduled tasks in the application.
 * Detects missed or delayed executions and provides recovery mechanisms.
 */
@Component
public class SchedulerHealthMonitor {

    private final MainDefaultUserContextService mainDefaultUserContextService;

    @Value("${app.session.monitoring.interval:30}")
    private int expectedMonitoringInterval;

    private final Map<String, TaskStatus> monitoredTasks = new ConcurrentHashMap<>();
    private final Map<String, Consumer<TaskStatus>> recoveryActions = new ConcurrentHashMap<>();

    public SchedulerHealthMonitor(MainDefaultUserContextService mainDefaultUserContextService) {
        this.mainDefaultUserContextService = mainDefaultUserContextService;
    }

    /**
     * Registers a task to be monitored
     *
     * @param taskId             Unique identifier for the task
     * @param expectedIntervalMinutes Expected interval in minutes
     * @param recoveryAction     Action to execute if task is unhealthy
     */
    public void registerTask(String taskId, int expectedIntervalMinutes, Consumer<TaskStatus> recoveryAction) {
        TaskStatus status = new TaskStatus(expectedIntervalMinutes);
        monitoredTasks.put(taskId, status);

        if (recoveryAction != null) {
            recoveryActions.put(taskId, recoveryAction);
        }

        LoggerUtil.info(this.getClass(),
                String.format("Registered task for health monitoring: %s (expected interval: %d minutes)",
                        taskId, expectedIntervalMinutes));
    }

    /**
     * Updates the last execution time for a monitored task
     *
     * @param taskId Unique identifier for the task
     */
    public void recordTaskExecution(String taskId) {
        TaskStatus status = monitoredTasks.get(taskId);
        if (status != null) {
            status.recordExecution();
            status.resetConsecutiveFailures();
            LoggerUtil.debug(this.getClass(), "Recorded execution for task: " + taskId);
        }
    }

    /**
     * Records a task failure
     *
     * @param taskId Unique identifier for the task
     * @param error  The error that occurred
     */
    public void recordTaskFailure(String taskId, String error) {
        TaskStatus status = monitoredTasks.get(taskId);
        if (status != null) {
            status.incrementFailures();
            status.setLastError(error);
            LoggerUtil.warn(this.getClass(),
                    String.format("Recorded failure for task %s: %s (consecutive failures: %d)",
                            taskId, error, status.getConsecutiveFailures()));
        }
    }

    /**
     * Records a warning for a task without marking it as failed
     * This helps track issues that need investigation without triggering recovery actions
     *
     * @param taskId Unique identifier for the task
     * @param warning The warning message
     */
    public void recordTaskWarning(String taskId, String warning) {
        TaskStatus status = monitoredTasks.get(taskId);
        if (status != null) {
            status.setLastWarning(warning);
            status.incrementWarningCount();
            LoggerUtil.warn(this.getClass(),
                    String.format("Warning for task %s: %s (total warnings: %d)",
                            taskId, warning, status.getWarningCount()));
        }
    }


    /**
     * Check if a task is healthy (executing within expected interval)
     *
     * @param taskId Unique identifier for the task
     * @return true if the task is healthy, false otherwise
     */
    public boolean isTaskHealthy(String taskId) {
        TaskStatus status = monitoredTasks.get(taskId);

        if (status == null) {
            return false;
        }

        return status.isHealthy();
    }

    /**
     * Get the health status for all monitored tasks
     *
     * @return Map of task IDs to health status (true = healthy, false = unhealthy)
     */
    public Map<String, Boolean> getHealthStatus() {
        Map<String, Boolean> health = new ConcurrentHashMap<>();

        monitoredTasks.forEach((taskId, status) -> health.put(taskId, status.isHealthy()));

        return health;
    }

    /**
     * Get detailed information about the status of a task
     *
     * @param taskId Unique identifier for the task
     * @return TaskStatus object with detailed information, or null if task not found
     */
    public TaskStatus getTaskStatus(String taskId) {
        return monitoredTasks.get(taskId);
    }

    /**
     * Scheduled task that checks the health of all monitored tasks
     * and triggers recovery actions for unhealthy tasks
     */
    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkTaskHealth() {
        LoggerUtil.debug(this.getClass(), "Running scheduled task health check");

        monitoredTasks.forEach((taskId, status) -> {
            if (!status.isHealthy()) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Unhealthy task detected: %s - Last execution: %s, Minutes since: %d",
                                taskId, status.getLastExecutionTime(), status.getMinutesSinceLastExecution()));

                // Trigger recovery action if one exists
                Consumer<TaskStatus> recoveryAction = recoveryActions.get(taskId);
                if (recoveryAction != null) {
                    try {
                        LoggerUtil.info(this.getClass(), "Triggering recovery action for task: " + taskId);
                        recoveryAction.accept(status);
                    } catch (Exception e) {
                        LoggerUtil.error(this.getClass(),
                                String.format("Error executing recovery action for %s: %s",
                                        taskId, e.getMessage()));
                    }
                }
            }
        });
    }
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkUserContextHealth() {
        boolean healthy = mainDefaultUserContextService.isCacheHealthy();
        if (!healthy) {
            LoggerUtil.error(this.getClass(), "MainDefaultUserContextCache is unhealthy - attempting emergency refresh");

            // Try emergency refresh
            boolean refreshed = mainDefaultUserContextService.forceRefresh();
            if (refreshed) {
                LoggerUtil.info(this.getClass(), "Emergency refresh successful");
            } else {
                LoggerUtil.error(this.getClass(), "Emergency refresh failed - user context may be compromised");
            }
        }
    }

    /**
     * Resets the consecutive failures counter for a task.
     * Useful during system reset to clear error states.
     *
     * @param taskId The ID of the task to reset
     */
    public void resetTaskFailures(String taskId) {
        TaskStatus status = monitoredTasks.get(taskId);
        if (status != null) {
            status.resetConsecutiveFailures();
            status.setLastError(null);
            status.setLastWarning(null);
            status.recordExecution(); // Update the last execution time
            LoggerUtil.info(this.getClass(), "Reset failure counters for task: " + taskId);
        }
    }

    /**
     * Status class for tracked tasks
     */
    @Getter
    @Setter
    public static class TaskStatus {
        private LocalDateTime lastExecutionTime;
        private final AtomicLong executionCount = new AtomicLong(0);
        private final AtomicLong consecutiveFailures = new AtomicLong(0);
        private final AtomicLong warningCount = new AtomicLong(0);
        private final int expectedIntervalMinutes;
        private String lastError;
        private String lastWarning;

        public TaskStatus(int expectedIntervalMinutes) {
            this.expectedIntervalMinutes = expectedIntervalMinutes;
            this.lastExecutionTime = LocalDateTime.now(); // Initialize with current time
        }

        public void recordExecution() {
            lastExecutionTime = LocalDateTime.now();
            executionCount.incrementAndGet();
        }

        public void incrementFailures() {
            consecutiveFailures.incrementAndGet();
        }

        public void resetConsecutiveFailures() {
            consecutiveFailures.set(0);
        }

        public void incrementWarningCount() {
            warningCount.incrementAndGet();
        }

        public long getWarningCount() {
            return warningCount.get();
        }

        public boolean isHealthy() {
            // Task is unhealthy if it hasn't executed within 1.5x expected interval
            // or has too many consecutive failures
            LocalDateTime now = LocalDateTime.now();
            Duration sinceLastExecution = Duration.between(lastExecutionTime, now);

            long minutesPassed = sinceLastExecution.toMinutes();
            long unhealthyThreshold = (long) (expectedIntervalMinutes * 1.5);

            boolean timingHealthy = minutesPassed <= unhealthyThreshold;
            boolean failureHealthy = consecutiveFailures.get() < 3;

            return timingHealthy && failureHealthy;
        }

        public long getMinutesSinceLastExecution() {
            return Duration.between(lastExecutionTime, LocalDateTime.now()).toMinutes();
        }

        public long getConsecutiveFailures() {
            return consecutiveFailures.get();
        }
    }
}