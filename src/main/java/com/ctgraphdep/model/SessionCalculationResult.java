package com.ctgraphdep.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public  class SessionCalculationResult {
    // Getters and setters
    private int totalWorkMinutes;          // Pure work time excluding breaks
    private int finalWorkMinutes;          // Work time after lunch deduction and rounding
    private int totalTemporaryStopMinutes; // Total time in temporary stops
    private int overtimeMinutes;           // Overtime minutes
    private boolean lunchBreakDeducted;    // Whether lunch break was deducted
    private List<WorkPeriod> workPeriods;  // List of actual work periods

    public SessionCalculationResult() {
        this.workPeriods = new ArrayList<>();
    }

    public void setTotalWorkMinutes(int totalWorkMinutes) { this.totalWorkMinutes = totalWorkMinutes; }

    public void setFinalWorkMinutes(int finalWorkMinutes) { this.finalWorkMinutes = finalWorkMinutes; }

    public void setTotalTemporaryStopMinutes(int totalTemporaryStopMinutes) { this.totalTemporaryStopMinutes = totalTemporaryStopMinutes; }

    public void setOvertimeMinutes(int overtimeMinutes) { this.overtimeMinutes = overtimeMinutes; }

    public void setLunchBreakDeducted(boolean lunchBreakDeducted) { this.lunchBreakDeducted = lunchBreakDeducted; }

    public void setWorkPeriods(List<WorkPeriod> workPeriods) { this.workPeriods = workPeriods; }
}