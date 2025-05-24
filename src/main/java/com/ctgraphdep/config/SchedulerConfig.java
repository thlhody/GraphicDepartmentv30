package com.ctgraphdep.config;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

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
        return scheduler;
    }

    @Bean(name = "generalTaskScheduler")
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
        return scheduler;
    }
}
