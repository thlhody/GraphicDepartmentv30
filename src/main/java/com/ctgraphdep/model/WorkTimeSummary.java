package com.ctgraphdep.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class WorkTimeSummary {
    private int totalWorkDays;         // Total working days in month (excluding weekends)
    private int daysWorked;            // Actual days worked
    private int remainingWorkDays;     // Days left to work
    private int availablePaidDays;     // Available paid leave days

    private int snDays;                // National holidays
    private int coDays;                // Paid leave days
    private int cmDays;                // Sick leave days

    private int totalRegularMinutes;   // Total regular working minutes
    private int totalOvertimeMinutes;  // Total overtime minutes
    private int totalMinutes;          // Total minutes (regular + overtime)
    private int discardedMinutes;      // Discarded minutes

    // Helper method to get total days off (SN + CO + CM)
    public int getTotalTimeOffDays() {
        return snDays + coDays + cmDays;
    }

    // Helper method to get total working days including paid time off
    public int getTotalWorkedDaysWithTimeOff() {
        return daysWorked + coDays + cmDays; // SN days are not counted as worked
    }

    // Helper method to check if all required days are accounted for
    public boolean isComplete() {
        return (daysWorked + getTotalTimeOffDays()) == totalWorkDays;
    }

}