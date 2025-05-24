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

    private final TaskScheduler generalTaskScheduler;

    public SchedulingConfiguration(@Qualifier("generalTaskScheduler") TaskScheduler generalTaskScheduler) {
        this.generalTaskScheduler = generalTaskScheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // Use general scheduler for @Scheduled annotations (except SessionMonitor)
        taskRegistrar.setScheduler(generalTaskScheduler);
    }
}