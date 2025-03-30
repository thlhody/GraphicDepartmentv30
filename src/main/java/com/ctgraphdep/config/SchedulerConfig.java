package com.ctgraphdep.config;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class SchedulerConfig {
    @Bean(name = "sessionMonitorScheduler")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        // Dynamic thread pool sizing
        int coreCount = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.max(4, coreCount / 2);

        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("SessionMonitor-");

        // Enhanced error handling
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Detailed error tracking
        scheduler.setErrorHandler(throwable -> {
            LoggerUtil.error(this.getClass(),
                    "Scheduler task execution error", throwable);
        });

        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);

        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "stalledNotificationTaskScheduler")
    public TaskScheduler stalledNotificationChecker() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("stalled-notification-");
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);

        // Similar error handling
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.setErrorHandler(throwable -> {
            LoggerUtil.error(this.getClass(),
                    "Stalled notification task execution error", throwable);
        });

        scheduler.initialize();
        return scheduler;
    }
}
