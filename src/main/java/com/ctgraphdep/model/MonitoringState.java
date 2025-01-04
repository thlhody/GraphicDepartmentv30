package com.ctgraphdep.model;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;

@Getter
public class MonitoringState {
    private final LocalDateTime startTime;
    private final ScheduledFuture<?> monitoringTask;

    public MonitoringState(LocalDateTime startTime, ScheduledFuture<?> monitoringTask) {
        this.startTime = startTime;
        this.monitoringTask = monitoringTask;
    }
}