package com.ctgraphdep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.ctgraphdep.utils.LoggerUtil;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadDiagnosticsConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        int coreCount = Runtime.getRuntime().availableProcessors();
        int poolSize = Math.max(8, coreCount / 2);  // At least 4 threads, half of core count

        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("SessionMonitor-");
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);

        scheduler.initialize();
        return scheduler;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void logThreadInfo() {
        try {
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            ThreadGroup parentGroup;
            while ((parentGroup = rootGroup.getParent()) != null) {
                rootGroup = parentGroup;
            }

            int activeCount = rootGroup.activeCount();
            Thread[] threads = new Thread[activeCount];
            rootGroup.enumerate(threads, true);

            LoggerUtil.info(this.getClass(), "Total Active Threads: " + activeCount);

            for (int i = 0; i < threads.length; i++) {
                if (threads[i] != null) {
                    LoggerUtil.info(this.getClass(),
                            String.format("Thread %d: %s - State: %s",
                                    i,
                                    threads[i].getName(),
                                    threads[i].getState())
                    );
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error logging thread information", e);
        }
    }
}