package com.ctgraphdep.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkTimeCounts {
    // Add getters and setters

    private int daysWorked = 0;
    private int snDays = 0;
    private int coDays = 0;
    private int cmDays = 0;
    private int regularMinutes = 0;
    private int overtimeMinutes = 0;

    // Add increment methods for convenience
    public void incrementDaysWorked() {
        this.daysWorked++;
    }

    public void incrementSnDays() {
        this.snDays++;
    }

    public void incrementCoDays() {
        this.coDays++;
    }

    public void incrementCmDays() {
        this.cmDays++;
    }

}