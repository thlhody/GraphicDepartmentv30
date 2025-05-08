package com.ctgraphdep.model.dto.session;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO for displaying session information with all calculated values
 */
@Data
@Getter
@Setter
public class WorkSessionDTO {
    // Basic session information
    private String username;
    private String sessionStatus; // WORK_ONLINE, WORK_TEMPORARY_STOP, WORK_OFFLINE
    private String formattedStatus; // Online, Temporary Stop, Offline

    // Time information
    private LocalDateTime dayStartTime;
    private LocalDateTime dayEndTime;
    private LocalDateTime currentTime;
    private String formattedDayStartTime;
    private String formattedCurrentTime;
    private LocalDateTime estimatedEndTime; // Based on schedule + lunch rules
    private String formattedEstimatedEndTime;

    // Work time calculations
    private Integer rawWorkMinutes; // Minutes worked without adjustments
    private Integer actualWorkMinutes; // After rounding and schedule capping
    private String formattedRawWorkTime; // HH:MM format
    private String formattedActualWorkTime; // HH:MM format

    // Breaks information
    private Integer temporaryStopCount;
    private Integer totalTemporaryStopMinutes;
    private String formattedTotalTemporaryStopTime;
    private LocalDateTime lastTemporaryStopTime;
    private String formattedLastTemporaryStopTime;

    // Lunch break information
    private Boolean lunchBreakDeducted;
    private Integer lunchBreakMinutes; // Usually 30 minutes if applicable

    // Calculation details
    private Integer discardedMinutes; // Minutes lost due to rounding, lunch breaks

    // Overtime information
    private Integer overtimeMinutes;
    private String formattedOvertimeMinutes;

    // Schedule information
    private Integer userSchedule; // Hours scheduled
    private Boolean workdayCompleted;

    // End time scheduling
    private LocalDateTime scheduledEndTime;
    private String formattedScheduledEndTime;
}