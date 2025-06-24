package com.ctgraphdep.model.dto.worktime;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@Data
@Builder
public class WorkTimeEntryDTO {
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

    private boolean lunchBreakDeducted;
    private boolean lunchBreakApplied;

    private String timeOffType;
    private String timeOffClass;

    // Work time fields
    private Integer totalWorkedMinutes; // Processed regular work time (up to schedule)
    private String formattedWorkTime;

    // Raw time field for display
    private Integer rawWorkedMinutes; // Actual time worked (before processing)
    private String formattedRawTime;

    // Overtime fields - FIXED: Single field for all overtime
    private Integer totalOvertimeMinutes;
    private String formattedOvertimeTime;
    private String formattedScheduledTime;

    private Integer discardedMinutes;

    // SN overtime specific fields
    private boolean hasSNOvertime;
    private String snOvertimeDisplay;

    /**
     * FIXED: Set SN overtime flag with proper calculation
     */
    public void setHasSNOvertime(boolean hasSNOvertime) {
        this.hasSNOvertime = hasSNOvertime;

        // Auto-generate SN overtime display when set
        if (hasSNOvertime && totalOvertimeMinutes != null && totalOvertimeMinutes > 0) {
            generateSNOvertimeDisplay();
        }
    }

    /**
     * FIXED: Set total overtime minutes with SN display update
     */
    public void setTotalOvertimeMinutes(Integer totalOvertimeMinutes) {
        this.totalOvertimeMinutes = totalOvertimeMinutes;

        // Update SN display if this is an SN day
        if (hasSNOvertime) {
            generateSNOvertimeDisplay();
        }
    }

    /**
     * Generate SN overtime display text (e.g., "SN4" for 4 hours)
     */
    private void generateSNOvertimeDisplay() {
        if (totalOvertimeMinutes != null && totalOvertimeMinutes > 0) {
            int hours = totalOvertimeMinutes / 60;
            this.snOvertimeDisplay = "SN" + hours;
        } else {
            this.snOvertimeDisplay = "SN";
        }
    }

    /**
     * Check if this entry represents work on a national holiday
     */
    public boolean isSNWorkDay() {
        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(this.timeOffType) &&
                totalOvertimeMinutes != null &&
                totalOvertimeMinutes > 0;
    }

    /**
     * Get display class for time off type
     */
    public String getTimeOffDisplayClass() {
        if (isSNWorkDay()) {
            return "sn-work-display";
        } else if (WorkCode.NATIONAL_HOLIDAY_CODE.equals(this.timeOffType)) {
            return "holiday";
        } else if (WorkCode.TIME_OFF_CODE.equals(this.timeOffType)) {
            return "vacation";
        } else if (WorkCode.MEDICAL_LEAVE_CODE.equals(this.timeOffType)) {
            return "medical";
        }
        return "";
    }

    /**
     * Get tooltip text for time off display
     */
    public String getTimeOffTooltip() {
        if (isSNWorkDay()) {
            return "National Holiday with " + formattedOvertimeTime + " overtime work";
        } else if (WorkCode.NATIONAL_HOLIDAY_CODE.equals(this.timeOffType)) {
            return "National Holiday (Free Day)";
        } else if (WorkCode.TIME_OFF_CODE.equals(this.timeOffType)) {
            return "Vacation Day";
        } else if (WorkCode.MEDICAL_LEAVE_CODE.equals(this.timeOffType)) {
            return "Medical Leave";
        }
        return "";
    }

    /**
     * Get overtime display class
     */
    public String getOvertimeDisplayClass() {
        if (isSNWorkDay()) {
            return "badge bg-warning text-dark rounded-pill overtime-display small sn-overtime";
        } else if (formattedOvertimeTime != null) {
            return "badge bg-success rounded-pill overtime-display small";
        }
        return "";
    }

    /**
     * Get overtime tooltip text
     */
    public String getOvertimeTooltip() {
        if (isSNWorkDay()) {
            return "Holiday overtime work: " + formattedOvertimeTime;
        } else if (formattedOvertimeTime != null) {
            return "Overtime work: " + formattedOvertimeTime;
        }
        return "";
    }

    /**
     * ENHANCED: Convert WorkTimeTable to DTO with proper SN overtime handling
     */
    public static WorkTimeEntryDTO fromWorkTimeTable(WorkTimeTable entry, int userSchedule) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        WorkTimeEntryDTOBuilder builder = WorkTimeEntryDTO.builder()
                .userId(entry.getUserId())
                .workDate(entry.getWorkDate())
                .formattedDate(entry.getWorkDate().format(dateFormatter))
                .dayStartTime(entry.getDayStartTime())
                .dayEndTime(entry.getDayEndTime())
                .temporaryStopCount(entry.getTemporaryStopCount())
                .totalTemporaryStopMinutes(entry.getTotalTemporaryStopMinutes())
                .lunchBreakDeducted(entry.isLunchBreakDeducted())
                .timeOffType(entry.getTimeOffType());

        // Format start and end times
        if (entry.getDayStartTime() != null) {
            builder.formattedStartTime(entry.getDayStartTime().format(timeFormatter));
        }

        if (entry.getDayEndTime() != null) {
            builder.formattedEndTime(entry.getDayEndTime().format(timeFormatter));
        }

        // Format temporary stop if available
        if (entry.getTemporaryStopCount() != null && entry.getTemporaryStopCount() > 0) {
            builder.formattedTemporaryStop(entry.getTemporaryStopCount() + " (" + entry.getTotalTemporaryStopMinutes() + "m)");
        }

        // ENHANCED: Handle SN days with special logic
        if (WorkCode.NATIONAL_HOLIDAY_CODE.equals(entry.getTimeOffType())) {
            handleSNDayConversion(entry, builder);
        } else if (entry.getTimeOffType() != null) {
            handleRegularTimeOffConversion(entry, builder);
        } else {
            handleRegularWorkDayConversion(entry, builder, userSchedule);
        }

        return builder.build();
    }

    /**
     * ENHANCED: Handle SN day conversion with overtime support
     */
    private static void handleSNDayConversion(WorkTimeTable entry, WorkTimeEntryDTOBuilder builder) {
        // SN days: totalWorkedMinutes should be 0, all work is overtime
        builder.totalWorkedMinutes(0);
        builder.formattedWorkTime("0:00"); // Always show 0 work time on holidays
        builder.timeOffClass("bg-success"); // Green for holidays

        // Handle SN overtime
        Integer overtimeMinutes = entry.getTotalOvertimeMinutes();
        if (overtimeMinutes != null && overtimeMinutes > 0) {
            builder.totalOvertimeMinutes(overtimeMinutes);
            builder.formattedOvertimeTime(CalculateWorkHoursUtil.minutesToHHmm(overtimeMinutes));
            builder.hasSNOvertime(true);

            // Generate SN display (will be done in setter)
            WorkTimeEntryDTO dto = builder.build();
            dto.generateSNOvertimeDisplay();
        } else {
            builder.totalOvertimeMinutes(0);
            builder.formattedOvertimeTime(null);
            builder.hasSNOvertime(false);
        }

        // No discarded minutes for SN days
        builder.discardedMinutes(0);
    }

    /**
     * Handle regular time off conversion (CO, CM)
     */
    private static void handleRegularTimeOffConversion(WorkTimeTable entry, WorkTimeEntryDTOBuilder builder) {
        // Set time off CSS class
        String cssClass = switch (entry.getTimeOffType()) {
            case WorkCode.TIME_OFF_CODE -> "bg-info";  // CO
            case WorkCode.MEDICAL_LEAVE_CODE -> "bg-warning";  // CM
            default -> "bg-secondary";
        };
        builder.timeOffClass(cssClass);

        // No work time for regular time off
        builder.totalWorkedMinutes(0);
        builder.formattedWorkTime(null);
        builder.totalOvertimeMinutes(0);
        builder.formattedOvertimeTime(null);
        builder.discardedMinutes(0);
        builder.hasSNOvertime(false);
    }

    /**
     * ENHANCED: Handle regular work day conversion with proper calculations
     */
    private static void handleRegularWorkDayConversion(WorkTimeTable entry, WorkTimeEntryDTOBuilder builder, int userSchedule) {
        // Calculate work times if this is a work entry (not time off)
        if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            int rawWorkedMinutes = entry.getTotalWorkedMinutes();

            // Set raw worked time (actual time before processing)
            builder.rawWorkedMinutes(rawWorkedMinutes);
            builder.formattedRawTime(CalculateWorkHoursUtil.minutesToHHmm(rawWorkedMinutes));

            // Use the utility to calculate processed work time breakdown
            WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(
                    rawWorkedMinutes, userSchedule);

            // Set lunch break applied flag
            builder.lunchBreakApplied(result.isLunchDeducted());

            // FIXED: Use processed minutes for work time display (up to schedule)
            int regularMinutes = result.getProcessedMinutes();
            builder.totalWorkedMinutes(regularMinutes);
            builder.formattedWorkTime(CalculateWorkHoursUtil.minutesToHHmm(regularMinutes));

            // Set overtime minutes - prefer stored value, fallback to calculated
            if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
                // Use the stored overtime value (more accurate)
                builder.totalOvertimeMinutes(entry.getTotalOvertimeMinutes());
                builder.formattedOvertimeTime(CalculateWorkHoursUtil.minutesToHHmm(entry.getTotalOvertimeMinutes()));
            } else if (result.getOvertimeMinutes() > 0) {
                // Fallback to calculated overtime
                builder.totalOvertimeMinutes(result.getOvertimeMinutes());
                builder.formattedOvertimeTime(CalculateWorkHoursUtil.minutesToHHmm(result.getOvertimeMinutes()));
            } else {
                builder.totalOvertimeMinutes(0);
                builder.formattedOvertimeTime(null);
            }

            // Calculate discarded minutes - get remainder of adjusted minutes
            int adjustedMinutes = CalculateWorkHoursUtil.calculateAdjustedMinutes(rawWorkedMinutes, userSchedule);
            builder.discardedMinutes(adjustedMinutes % 60);
        } else {
            // No work done
            builder.rawWorkedMinutes(0);
            builder.formattedRawTime(null);
            builder.totalWorkedMinutes(0);
            builder.formattedWorkTime(null);
            builder.totalOvertimeMinutes(0);
            builder.formattedOvertimeTime(null);
            builder.discardedMinutes(0);
        }

        builder.hasSNOvertime(false);
    }
}