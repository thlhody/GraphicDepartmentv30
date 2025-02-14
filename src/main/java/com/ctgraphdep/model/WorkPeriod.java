package com.ctgraphdep.model;

import com.ctgraphdep.utils.CalculateWorkHoursUtil;

import java.time.LocalDateTime;

public class WorkPeriod {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int minutes;

    public WorkPeriod(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.minutes = CalculateWorkHoursUtil.calculateMinutesBetween(startTime, endTime);
    }

    // Getters
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public int getMinutes() { return minutes; }
}
