package com.ctgraphdep.model;

import com.ctgraphdep.enums.SessionEndRule;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;

@Getter
@Setter
public class MonitoringState {
    private final LocalDateTime startTime;
    private final String initialStatus;
    private SessionEndRule currentRule;
    private boolean hourlyMonitoring;
    private boolean scheduleEndNotificationShown; // Add this
    private LocalDateTime lastOvertimeNotification; // Add this
    private LocalDateTime lastTempStopNotification; // Add this

    public MonitoringState(LocalDateTime startTime, String initialStatus) {
        this.startTime = startTime;
        this.initialStatus = initialStatus;
        this.hourlyMonitoring = false;
        this.scheduleEndNotificationShown = false;
        this.lastOvertimeNotification = null;
        this.lastTempStopNotification = null;
    }
}