package com.ctgraphdep.model;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;

@Getter
public class SessionMonitoringTask {
    private final String username;
    private final Integer userId;
    private final LocalDateTime startTime;
    private final ScheduledFuture<?> future;

    public SessionMonitoringTask(String username, Integer userId, LocalDateTime startTime, ScheduledFuture<?> future) {
        this.username = username;
        this.userId = userId;
        this.startTime = startTime;
        this.future = future;
    }
}