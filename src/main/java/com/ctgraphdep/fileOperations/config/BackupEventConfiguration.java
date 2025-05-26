package com.ctgraphdep.fileOperations.config;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for the backup event system.
 * Ensures proper async processing and error handling for file operation events.
 */
@Configuration
@EnableAsync
public class BackupEventConfiguration {

    /**
     * Custom task executor for backup operations.
     * Provides dedicated thread pool for backup processing to avoid blocking main operations.
     */
    @Bean(name = "backupTaskExecutor")
    public TaskExecutor backupTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Configure thread pool for backup operations
        executor.setCorePoolSize(2);              // Minimum threads for backup operations
        executor.setMaxPoolSize(4);               // Maximum threads for backup operations
        executor.setQueueCapacity(50);            // Queue size for pending backup operations
        executor.setKeepAliveSeconds(60);         // Thread idle timeout
        executor.setThreadNamePrefix("backup-event-"); // Thread naming for easier debugging

        // Configure rejection policy
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        LoggerUtil.info(BackupEventConfiguration.class,
                "Initialized backup task executor with core pool size: " + executor.getCorePoolSize() +
                        ", max pool size: " + executor.getMaxPoolSize());

        return executor;
    }

    /**
     * Custom application event multicaster for better error handling.
     * Ensures that event processing errors don't affect the main application flow.
     */
    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster applicationEventMulticaster() {
        SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();

        // Use our custom task executor for async event processing
        multicaster.setTaskExecutor(backupTaskExecutor());

        // Configure error handler to prevent event processing errors from propagating
        multicaster.setErrorHandler(throwable -> {
            LoggerUtil.error(BackupEventConfiguration.class,
                    "Error in backup event processing: " + throwable.getMessage(), throwable);

            // Log additional details for debugging
            if (throwable.getCause() != null) {
                LoggerUtil.error(BackupEventConfiguration.class,
                        "Root cause: " + throwable.getCause().getMessage(), throwable.getCause());
            }
        });

        LoggerUtil.info(BackupEventConfiguration.class, "Configured application event multicaster with custom error handling");

        return multicaster;
    }

    /**
     * Bean for monitoring backup event system health.
     * Can be used to track event processing statistics and performance.
     */
    @Bean
    public BackupEventMonitor backupEventMonitor() {
        return new BackupEventMonitor();
    }

    /**
     * Simple monitoring component for backup events.
     * Tracks statistics and provides health information.
     */
    public static class BackupEventMonitor {
        private volatile long totalEventsProcessed = 0;
        private volatile long totalBackupsCreated = 0;
        private volatile long totalBackupFailures = 0;
        private volatile long lastEventTimestamp = 0;

        public void recordEventProcessed() {
            totalEventsProcessed++;
            lastEventTimestamp = System.currentTimeMillis();
        }

        public void recordBackupCreated() {
            totalBackupsCreated++;
        }

        public void recordBackupFailure() {
            totalBackupFailures++;
        }

        public long getTotalEventsProcessed() {
            return totalEventsProcessed;
        }

        public long getTotalBackupsCreated() {
            return totalBackupsCreated;
        }

        public long getTotalBackupFailures() {
            return totalBackupFailures;
        }

        public long getLastEventTimestamp() {
            return lastEventTimestamp;
        }

        public double getBackupSuccessRate() {
            long total = totalBackupsCreated + totalBackupFailures;
            if (total == 0) return 0.0;
            return (double) totalBackupsCreated / total * 100.0;
        }

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

        @Override
        public String toString() {
            return String.format("BackupEventMonitor{events=%d, backups=%d, failures=%d, successRate=%.1f%%, status='%s'}",
                    totalEventsProcessed, totalBackupsCreated, totalBackupFailures, getBackupSuccessRate(), getHealthStatus());
        }
    }
}