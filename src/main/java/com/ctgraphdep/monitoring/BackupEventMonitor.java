package com.ctgraphdep.monitoring;

import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

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