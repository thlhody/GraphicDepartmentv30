package com.ctgraphdep.model.dto.worktime;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.dto.status.GeneralDataStatusDTO;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for worktime entries with enhanced special day support.
 * THIS IS MOSTLY A DATA CONTAINER with some computed property methods.
 * Static factory methods have been moved to WorkTimeEntryDTOFactory service.
 * Instance methods that compute derived values from existing fields are acceptable
 * (e.g., isSpecialDayWithWork(), getTimeOffDisplayClass()) as they are computed properties,
 * not factory logic.
 * @see com.ctgraphdep.service.dto.WorkTimeEntryDTOFactory for creation logic
 */
@Getter
@Setter
@Data
@Builder
public class WorkTimeEntryDTO {
    // ========================================================================
    // BASIC FIELDS
    // ========================================================================

    private Integer userId;
    private LocalDate workDate;
    private String formattedDate;

    private LocalDateTime dayStartTime;
    private String formattedStartTime;

    private LocalDateTime dayEndTime;
    private String formattedEndTime;

    private Integer temporaryStopCount;
    private Integer totalTemporaryStopMinutes;
    private String formattedTemporaryStop;

    /** List of temporary stops for detailed display */
    private List<TemporaryStop> temporaryStops;

    private boolean lunchBreakDeducted;
    private boolean lunchBreakApplied;

    private String timeOffType;
    private String timeOffClass;

    // ========================================================================
    // WORK TIME FIELDS
    // ========================================================================

    /** Processed regular work time (up to schedule) */
    private Integer totalWorkedMinutes;
    private String formattedWorkTime;

    /** Actual time worked (before processing) */
    private Integer rawWorkedMinutes;
    private String formattedRawTime;

    /** Overtime fields for all special day types */
    private Integer totalOvertimeMinutes;
    private String formattedOvertimeTime;
    private String formattedScheduledTime;

    private Integer discardedMinutes;

    // ========================================================================
    // SPECIAL DAY FIELDS
    // ========================================================================

    /** Special day work fields (not just SN) */
    private boolean hasSpecialDayWork;
    private String specialDayWorkDisplay;
    private String specialDayType;

    /** Status information */
    private GeneralDataStatusDTO statusInfo;

    // ========================================================================
    // INSTANCE METHODS - Computed Properties (ACCEPTABLE in DTOs)
    // These don't create new instances, just compute values from existing fields
    // ========================================================================

    /**
     * Set total overtime minutes with special day display update
     */
    public void setTotalOvertimeMinutes(Integer totalOvertimeMinutes) {
        this.totalOvertimeMinutes = totalOvertimeMinutes;

        // Update special day display if this is a special day
        if (hasSpecialDayWork && specialDayType != null) {
            generateSpecialDayWorkDisplay();
        }
    }

    /**
     * Generate special day work display text (e.g., "SN4", "CO6", "CM2", "W8")
     */
    private void generateSpecialDayWorkDisplay() {
        if (totalOvertimeMinutes != null && totalOvertimeMinutes > 0 && specialDayType != null) {
            int hours = totalOvertimeMinutes / 60;
            this.specialDayWorkDisplay = specialDayType + hours;
        } else if (specialDayType != null) {
            this.specialDayWorkDisplay = specialDayType;
        }
    }

    /**
     * Check if this entry represents work on any special day (SN/CO/CM/W)
     */
    public boolean isSpecialDayWithWork() {
        return hasSpecialDayWork &&
                totalOvertimeMinutes != null &&
                totalOvertimeMinutes > 0 &&
                isSpecialDayType();
    }

    /**
     * Check if this entry has a special day time off type
     */
    public boolean isSpecialDayType() {
        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(this.timeOffType) ||
                WorkCode.TIME_OFF_CODE.equals(this.timeOffType) ||
                WorkCode.MEDICAL_LEAVE_CODE.equals(this.timeOffType) ||
                WorkCode.SPECIAL_EVENT_CODE.equals(this.timeOffType) ||
                WorkCode.WEEKEND_CODE.equals(this.timeOffType);
    }
}
