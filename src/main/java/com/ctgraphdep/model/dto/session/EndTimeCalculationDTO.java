package com.ctgraphdep.model.dto.session;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for end time calculations (used for end time scheduler)
 */
@Data
@Getter
@Setter
public class EndTimeCalculationDTO {
    // Status
    private Boolean success;
    private String message;

    // Calculation results
    private Integer totalElapsedMinutes;
    private Integer breakMinutes;
    private Boolean lunchDeducted;
    private Integer lunchBreakMinutes;
    private Integer netWorkMinutes; // Processed minutes
    private Integer overtimeMinutes;
    private Integer rawMinutes;
    private Integer finalMinutes; // Final total after all calculations

    // Formatted values for display
    private String formattedTotalElapsed;
    private String formattedBreakTime;
    private String formattedNetWorkTime;
    private String formattedOvertimeMinutes;
}
