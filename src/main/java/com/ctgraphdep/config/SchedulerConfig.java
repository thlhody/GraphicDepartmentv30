package com.ctgraphdep.config;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Centralized configuration for all task schedulers and executors in the application.
 * This class manages all thread pools to provide better control and monitoring.
 */
@Configuration
public class SchedulerConfig {

    @Bean(name = "sessionMonitorScheduler")
    public TaskScheduler sessionMonitorTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        // SINGLE THREAD for SessionMonitor to prevent conflicts
        scheduler.setPoolSize(1);  // <-- CHANGED: Only 1 thread
        scheduler.setThreadNamePrefix("SessionMonitor-");

        // Enhanced error handling
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.setErrorHandler(throwable -> LoggerUtil.error(this.getClass(), "SessionMonitor task execution error", throwable));

        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10); // Increased timeout
        scheduler.initialize();

        LoggerUtil.info(this.getClass(), "Initialized SessionMonitor scheduler with single thread");
        return scheduler;
    }

    @Bean(name = "generalTaskScheduler")
    @Primary  // Mark as primary to resolve TaskExecutor ambiguity
    public TaskScheduler generalTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        // Multithreaded for other tasks
        int coreCount = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.max(4, coreCount / 2);
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("GeneralTask-");

        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.setErrorHandler(throwable -> LoggerUtil.error(this.getClass(), "General task execution error", throwable));

        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();

        LoggerUtil.info(this.getClass(), "Initialized GeneralTask scheduler with " + poolSize + " threads (marked as @Primary)");
        return scheduler;
    }

    @Bean(name = "stalledNotificationTaskScheduler")
    public TaskScheduler stalledNotificationChecker() {
        // Keep existing configuration
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("stalled-notification-");
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.setErrorHandler(throwable -> LoggerUtil.error(this.getClass(), "Stalled notification task execution error", throwable));
        scheduler.initialize();

        LoggerUtil.info(this.getClass(), "Initialized StalledNotification scheduler with 5 threads");
        return scheduler;
    }

    /**
     * MOVED FROM BackupEventConfiguration: Custom task executor for backup operations.
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        LoggerUtil.info(this.getClass(),
                "Initialized backup task executor with core pool size: " + executor.getCorePoolSize() +
                        ", max pool size: " + executor.getMaxPoolSize());

        return executor;
    }
}