package com.ctgraphdep.model.dto.worktime;

import lombok.Data;

/**
 * DTO for tracking work time counts and totals
 * Used internally by WorktimeDisplayService for calculations
 */

@Data
public class WorkTimeCountsDTO {
    // Day counts
    private int daysWorked = 0;
    private int snDays = 0;
    private int coDays = 0;
    private int cmDays = 0;

    // Time totals (in minutes)
    private int regularMinutes = 0;
    private int overtimeMinutes = 0;
    private int discardedMinutes = 0;

    // Utility methods
    public int getTotalTimeOffDays() {
        return snDays + coDays + cmDays;
    }

    public int getTotalMinutes() {
        return regularMinutes + overtimeMinutes;
    }

    @Override
    public String toString() {
        return String.format("WorkTimeCountsDTO{daysWorked=%d, snDays=%d, coDays=%d, cmDays=%d, regular=%dmin, overtime=%dmin, discarded=%dmin}",
                daysWorked, snDays, coDays, cmDays, regularMinutes, overtimeMinutes, discardedMinutes);
    }
}