package com.ctgraphdep.config;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for async processing, specifically for background login merge operations.
 * This enables the first login of the day to complete instantly while merge operations
 * run in the background.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Task executor specifically for background login merge operations.
     * Separate from other executors to avoid resource conflicts.
     */
    @Bean(name = "loginMergeTaskExecutor")
    public TaskExecutor loginMergeTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Configure thread pool for login merge operations
        executor.setCorePoolSize(2);              // Minimum threads for merge operations
        executor.setMaxPoolSize(4);               // Maximum threads for merge operations
        executor.setQueueCapacity(20);            // Queue size for pending merge operations
        executor.setKeepAliveSeconds(300);        // Thread idle timeout (5 minutes)
        executor.setThreadNamePrefix("login-merge-"); // Thread naming for easier debugging

        // Configure rejection policy - run in caller thread if queue full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        LoggerUtil.info(this.getClass(),
                "Initialized login merge task executor with core pool size: " + executor.getCorePoolSize() +
                        ", max pool size: " + executor.getMaxPoolSize() +
                        " for background login optimization");

        return executor;
    }

    /**
     * General async task executor for other async operations.
     * This provides a default async executor for @Async methods that don't specify an executor.
     */
    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Configure for general async operations
        int coreCount = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, coreCount / 4);     // Conservative core size
        int maxPoolSize = Math.max(4, coreCount / 2);      // Allow scaling up

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-");

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        LoggerUtil.info(this.getClass(),
                "Initialized general async task executor with core pool size: " + corePoolSize +
                        ", max pool size: " + maxPoolSize);

        return executor;
    }
}