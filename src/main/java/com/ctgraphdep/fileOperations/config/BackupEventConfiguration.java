package com.ctgraphdep.fileOperations.config;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Simplified configuration for the backup event system.
 * Focuses solely on event processing configuration.
 * TaskExecutor is now managed by SchedulerConfig.
 * BackupEventMonitor is now a separate service in the monitoring package.
 */
@Configuration
@EnableAsync
public class BackupEventConfiguration {

    /**
     * Custom application event multicast for better error handling.
     * Ensures that event processing errors don't affect the main application flow.
     * Uses the backupTaskExecutor from SchedulerConfig for async event processing.
     */
    @Bean(name = "applicationEventMulticast")
    public ApplicationEventMulticaster applicationEventMulticaster(
            @Qualifier("backupTaskExecutor") TaskExecutor backupTaskExecutor) {

        SimpleApplicationEventMulticaster multicast = new SimpleApplicationEventMulticaster();

        // Use the backup task executor from SchedulerConfig
        multicast.setTaskExecutor(backupTaskExecutor);

        // Configure error handler to prevent event processing errors from propagating
        multicast.setErrorHandler(throwable -> {
            LoggerUtil.error(BackupEventConfiguration.class,
                    "Error in backup event processing: " + throwable.getMessage(), throwable);

            // Log additional details for debugging
            if (throwable.getCause() != null) {
                LoggerUtil.error(BackupEventConfiguration.class,
                        "Root cause: " + throwable.getCause().getMessage(), throwable.getCause());
            }
        });

        LoggerUtil.info(BackupEventConfiguration.class,
                "Configured application event multicast with backupTaskExecutor and custom error handling");

        return multicast;
    }
}