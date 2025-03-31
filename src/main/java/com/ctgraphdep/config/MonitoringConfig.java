package com.ctgraphdep.config;

import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.NotificationQueueManager;
import com.ctgraphdep.service.SystemNotificationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

@Configuration
public class MonitoringConfig {

    @Bean
    @ConditionalOnProperty(name = "app.health.monitoring.enabled", havingValue = "true", matchIfMissing = true)
    public SchedulerHealthMonitor schedulerHealthMonitor() {
        return new SchedulerHealthMonitor();
    }

    @Bean
    @ConditionalOnProperty(name = "app.notification.queue.enabled", havingValue = "true", matchIfMissing = true)
    public NotificationQueueManager notificationQueueManager(
            SystemNotificationService notificationService,
            @Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler,
            SchedulerHealthMonitor healthMonitor) {
        return new NotificationQueueManager(notificationService, taskScheduler, healthMonitor);
    }
}