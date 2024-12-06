package com.ctgraphdep.model.statistics;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorktimeStatistics {
    private double avgWorkedHours;
    private double avgOvertimeHours;
    private double avgWorkDays;
    private int totalUsers;
}