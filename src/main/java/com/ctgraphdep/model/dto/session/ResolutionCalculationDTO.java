package com.ctgraphdep.model.dto.session;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for resolution calculations (used for unresolved entries)
 */
@Data
@Getter
@Setter
public class ResolutionCalculationDTO {
    // Status
    private Boolean success;
    private String errorMessage;

    // Basic information
    private LocalDate workDate;
    private String formattedWorkDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String formattedStartTime;
    private String formattedEndTime;

    // Calculation results
    private Integer totalElapsedMinutes;
    private Integer breakMinutes;
    private Boolean lunchDeducted;
    private Integer lunchBreakMinutes;
    private Integer netWorkMinutes;
    private Integer overtimeMinutes;
    private Integer rawMinutes;
    private Integer discardedMinutes;

    // Formatted strings for display
    private String formattedTotalElapsed;
    private String formattedBreakTime;
    private String formattedNetWorkTime;
    private String formattedOvertimeMinutes;

    // Recommended end time
    private LocalDateTime recommendedEndTime;
    private String formattedRecommendedEndTime;
}