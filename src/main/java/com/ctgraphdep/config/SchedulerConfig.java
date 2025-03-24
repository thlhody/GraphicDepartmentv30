package com.ctgraphdep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean(name = "sessionMonitorScheduler")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("SessionMonitor-");
        scheduler.initialize();
        return scheduler;
    }
    // Add a scheduled task to check for stalled notification
    @Bean(name = "stalledNotificationTaskScheduler")
    public TaskScheduler stalledNotificationChecker() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stalled-notification-");
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }
}
