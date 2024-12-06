package com.ctgraphdep.model.statistics;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserWorktimeStats {
    private int totalWorkDays;
    private int workedDays;
    private double attendanceRate;
    private int totalHours;
    private int overtimeHours;
}