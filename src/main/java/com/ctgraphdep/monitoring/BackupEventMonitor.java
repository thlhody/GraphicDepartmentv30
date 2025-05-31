package com.ctgraphdep.monitoring;

import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedicated service for monitoring backup event system health.
 * Tracks statistics and provides health information for backup operations.
 * Integrates with the broader monitoring infrastructure.
 */
@Service
public class BackupEventMonitor {

    // Statistics tracking
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicLong totalBackupsCreated = new AtomicLong(0);
    private final AtomicLong totalBackupFailures = new AtomicLong(0);
    @Getter
    private volatile long lastEventTimestamp = 0;

    // Health status tracking
    private volatile LocalDateTime lastHealthCheck = LocalDateTime.now();

    // ========================================================================
    // EVENT RECORDING METHODS (Called by FileEventPublisher)
    // ========================================================================

    /**
     * Records that an event was processed by the backup system.
     */
    public void recordEventProcessed() {
        totalEventsProcessed.incrementAndGet();
        lastEventTimestamp = System.currentTimeMillis();
        LoggerUtil.debug(this.getClass(), "Recorded backup event processed (total: " + totalEventsProcessed.get() + ")");
    }

    /**
     * Records that a backup was successfully created.
     */
    public void recordBackupCreated() {
        totalBackupsCreated.incrementAndGet();
        LoggerUtil.debug(this.getClass(), "Recorded backup created (total: " + totalBackupsCreated.get() + ")");
    }

    /**
     * Records that a backup operation failed.
     */
    public void recordBackupFailure() {
        long failures = totalBackupFailures.incrementAndGet();
        LoggerUtil.warn(this.getClass(), "Recorded backup failure (total: " + failures + ")");
    }

    // ========================================================================
    // STATISTICS GETTERS
    // ========================================================================

    public long getTotalEventsProcessed() {
        return totalEventsProcessed.get();
    }

    public long getTotalBackupsCreated() {
        return totalBackupsCreated.get();
    }

    public long getTotalBackupFailures() {
        return totalBackupFailures.get();
    }

    public double getBackupSuccessRate() {
        long total = totalBackupsCreated.get() + totalBackupFailures.get();
        if (total == 0) return 0.0;
        return (double) totalBackupsCreated.get() / total * 100.0;
    }

    /**
     * Gets the current health status of the backup system.
     */
    public String getHealthStatus() {
        long timeSinceLastEvent = System.currentTimeMillis() - lastEventTimestamp;

        if (lastEventTimestamp == 0) {
            return "No events processed yet";
        } else if (timeSinceLastEvent < 60000) { // Less than 1 minute
            return "Healthy - Recent activity";
        } else if (timeSinceLastEvent < 300000) { // Less than 5 minutes
            return "Normal - Some activity";
        } else {
            return "Idle - No recent activity";
        }
    }

    /**
     * Gets detailed health information.
     */
    public String getDetailedHealthStatus() {
        return String.format(
                """
                        BackupEventMonitor Health Report [%s]:
                          Events Processed: %d
                          Backups Created: %d
                          Backup Failures: %d
                          Success Rate: %.1f%%
                          Status: %s
                          Last Event: %s
                          Last Health Check: %s""",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                totalEventsProcessed.get(),
                totalBackupsCreated.get(),
                totalBackupFailures.get(),
                getBackupSuccessRate(),
                getHealthStatus(),
                lastEventTimestamp > 0 ?
                        LocalDateTime.ofEpochSecond(lastEventTimestamp / 1000, 0, java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.now()))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "Never",
                lastHealthCheck.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }

    // ========================================================================
    // HEALTH ISSUE HANDLING
    // ========================================================================

    /**
     * Handles backup monitoring issues detected by SchedulerHealthMonitor.
     */
    private void handleBackupMonitoringIssues(SchedulerHealthMonitor.TaskStatus taskStatus) {
        try {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Backup monitoring issue detected - Consecutive failures: %d, Minutes since last execution: %d",
                    taskStatus.getConsecutiveFailures(), taskStatus.getMinutesSinceLastExecution()
            ));

            // Log current state for debugging
            LoggerUtil.info(this.getClass(), "Current backup system state:\n" + getDetailedHealthStatus());

            // Could implement recovery actions here:
            // - Reset counters
            // - Send alerts
            // - Trigger manual backup verification
            // - etc.

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error handling backup monitoring issues: " + e.getMessage(), e);
        }
    }

    /**
     * Manual reset of statistics (for admin/debug purposes).
     */
    public void resetStatistics() {
        totalEventsProcessed.set(0);
        totalBackupsCreated.set(0);
        totalBackupFailures.set(0);
        lastEventTimestamp = 0;
        lastHealthCheck = LocalDateTime.now();
        LoggerUtil.info(this.getClass(), "Backup event statistics have been reset");
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    @Override
    public String toString() {
        return String.format("BackupEventMonitor{events=%d, backups=%d, failures=%d, successRate=%.1f%%, status='%s'}",
                totalEventsProcessed.get(), totalBackupsCreated.get(), totalBackupFailures.get(),
                getBackupSuccessRate(), getHealthStatus());
    }
}