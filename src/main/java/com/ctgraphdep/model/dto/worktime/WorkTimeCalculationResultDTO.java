package com.ctgraphdep.model.dto.worktime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WorkTimeCalculationResultDTO {

    private int rawMinutes;           // Original input minutes
    private int adjustedMinutes;      // After lunch deduction (ADD THIS)
    private int processedMinutes;     // Regular work (capped at schedule)
    private int overtimeMinutes;      // Overtime hours (rounded down)
    private boolean lunchDeducted;
    private int finalTotalMinutes;    // processedMinutes + overtimeMinutes
    private int discardedMinutes;     // adjustedMinutes - finalTotalMinutes (ADD THIS)

    // Constructor for backward compatibility (without new fields)
    public WorkTimeCalculationResultDTO(int rawMinutes, int processedMinutes,
                                        int overtimeMinutes, boolean lunchDeducted,
                                        int finalTotalMinutes) {
        this.rawMinutes = rawMinutes;
        this.processedMinutes = processedMinutes;
        this.overtimeMinutes = overtimeMinutes;
        this.lunchDeducted = lunchDeducted;
        this.finalTotalMinutes = finalTotalMinutes;
        // Calculate the missing fields
        this.adjustedMinutes = lunchDeducted ? rawMinutes - 30 : rawMinutes;
        this.discardedMinutes = adjustedMinutes - finalTotalMinutes;
    }
}