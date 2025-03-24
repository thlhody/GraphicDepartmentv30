package com.ctgraphdep.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Configuration for task scheduling.
 * Explicitly configures which TaskScheduler to use for scheduled tasks.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration implements SchedulingConfigurer {

    private final TaskScheduler sessionMonitorScheduler;

    public SchedulingConfiguration(@Qualifier("sessionMonitorScheduler") TaskScheduler sessionMonitorScheduler) {
        this.sessionMonitorScheduler = sessionMonitorScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {

        // Explicitly set the scheduler to use for @Scheduled annotations
        taskRegistrar.setScheduler(sessionMonitorScheduler);
    }
}