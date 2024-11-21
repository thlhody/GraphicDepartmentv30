package com.ctgraphdep.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkTimeResult {

    private final int totalRegularMinutes;
    private final int totalOvertimeMinutes;


    public WorkTimeResult(int totalRegularMinutes, int totalOvertimeMinutes) {
        this.totalRegularMinutes = totalRegularMinutes;
        this.totalOvertimeMinutes = totalOvertimeMinutes;

    }

}
