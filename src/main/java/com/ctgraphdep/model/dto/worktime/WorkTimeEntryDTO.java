package com.ctgraphdep.model.dto.worktime;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.status.GeneralDataStatusDTO;
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

    // ENHANCED: Overtime fields for all special day types
    private Integer totalOvertimeMinutes;
    private String formattedOvertimeTime;
    private String formattedScheduledTime;

    private Integer discardedMinutes;

    // ENHANCED: Special day work fields (not just SN)
    private boolean hasSpecialDayWork;
    private String specialDayWorkDisplay;
    private String specialDayType;

    // Add status information
    private GeneralDataStatusDTO statusInfo;


    /**
     * ENHANCED: Set total overtime minutes with special day display update
     */
    public void setTotalOvertimeMinutes(Integer totalOvertimeMinutes) {
        this.totalOvertimeMinutes = totalOvertimeMinutes;

        // Update special day display if this is a special day
        if (hasSpecialDayWork && specialDayType != null) {
            generateSpecialDayWorkDisplay();
        }
    }

    /**
     * ENHANCED: Generate special day work display text (e.g., "SN4", "CO6", "CM2", "W8")
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
     * ENHANCED: Check if this entry represents work on any special day (SN/CO/CM/W)
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
                WorkCode.WEEKEND_CODE.equals(this.timeOffType);
    }

    /**
     * LEGACY: Check if this entry represents work on a national holiday (for backward compatibility)
     */
    public boolean isSNWorkDay() {
        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(this.timeOffType) &&
                totalOvertimeMinutes != null &&
                totalOvertimeMinutes > 0;
    }

    /**
     * ENHANCED: Get display class for time off type including special day work
     */
    public String getTimeOffDisplayClass() {
        if (isSpecialDayWithWork()) {
            return switch (this.timeOffType) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "sn-work-display";
                case WorkCode.TIME_OFF_CODE -> "co-work-display";
                case WorkCode.MEDICAL_LEAVE_CODE -> "cm-work-display";
                case WorkCode.WEEKEND_CODE -> "w-work-display";
                default -> "special-work-display";
            };
        } else if (isSpecialDayType()) {
            return switch (this.timeOffType) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "holiday";
                case WorkCode.TIME_OFF_CODE -> "vacation";
                case WorkCode.MEDICAL_LEAVE_CODE -> "medical";
                case WorkCode.WEEKEND_CODE -> "weekend";
                default -> "time-off";
            };
        }
        return "";
    }

    /**
     * ENHANCED: Get tooltip text for time off display including special day work
     */
    public String getTimeOffTooltip() {
        if (isSpecialDayWithWork()) {
            String dayName = switch (this.timeOffType) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "National Holiday";
                case WorkCode.TIME_OFF_CODE -> "Time Off Day";
                case WorkCode.MEDICAL_LEAVE_CODE -> "Medical Leave";
                case WorkCode.WEEKEND_CODE -> "Weekend";
                default -> "Special Day";
            };
            return dayName + " with " + formattedOvertimeTime + " overtime work";
        } else if (isSpecialDayType()) {
            return switch (this.timeOffType) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "National Holiday (Free Day)";
                case WorkCode.TIME_OFF_CODE -> "Vacation Day";
                case WorkCode.MEDICAL_LEAVE_CODE -> "Medical Leave";
                case WorkCode.WEEKEND_CODE -> "Weekend Day";
                default -> "Time Off Day";
            };
        }
        return "";
    }

    /**
     * ENHANCED: Get overtime display class for all special day types
     */
    public String getOvertimeDisplayClass() {
        if (isSpecialDayWithWork()) {
            return switch (this.timeOffType) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "badge bg-warning text-dark rounded-pill overtime-display small sn-overtime";
                case WorkCode.TIME_OFF_CODE -> "badge bg-info text-white rounded-pill overtime-display small co-overtime";
                case WorkCode.MEDICAL_LEAVE_CODE -> "badge bg-orange text-white rounded-pill overtime-display small cm-overtime";
                case WorkCode.WEEKEND_CODE -> "badge bg-secondary text-white rounded-pill overtime-display small w-overtime";
                default -> "badge bg-primary text-white rounded-pill overtime-display small special-overtime";
            };
        } else if (formattedOvertimeTime != null) {
            return "badge bg-success rounded-pill overtime-display small";
        }
        return "";
    }

    /**
     * ENHANCED: Get overtime tooltip text for all special day types
     */
    public String getOvertimeTooltip() {
        if (isSpecialDayWithWork()) {
            String dayName = switch (this.timeOffType) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "Holiday";
                case WorkCode.TIME_OFF_CODE -> "Time Off";
                case WorkCode.MEDICAL_LEAVE_CODE -> "Medical Leave";
                case WorkCode.WEEKEND_CODE -> "Weekend";
                default -> "Special Day";
            };
            return dayName + " overtime work: " + formattedOvertimeTime;
        } else if (formattedOvertimeTime != null) {
            return "Overtime work: " + formattedOvertimeTime;
        }
        return "";
    }

    /**
     * ENHANCED: Convert WorkTimeTable to DTO with proper special day overtime handling
     */
    public static WorkTimeEntryDTO fromWorkTimeTable(WorkTimeTable entry, int userSchedule, GeneralDataStatusDTO statusInfo) {
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
                .timeOffType(entry.getTimeOffType())
                .statusInfo(statusInfo);

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

        // ENHANCED: Handle all special day types with work time
        if (entry.getTimeOffType() != null && isSpecialDayTypeStatic(entry.getTimeOffType())) {
            handleSpecialDayConversion(entry, builder);
        } else if (entry.getTimeOffType() != null) {
            handleRegularTimeOffConversion(entry, builder);
        } else {
            handleRegularWorkDayConversion(entry, builder, userSchedule);
        }

        return builder.build();
    }

    /**
     * ENHANCED: Handle all special day types (SN/CO/CM/W) with work time
     */
    private static void handleSpecialDayConversion(WorkTimeTable entry, WorkTimeEntryDTOBuilder builder) {
        // Set special day type for display
        builder.specialDayType(entry.getTimeOffType());

        // Check if this special day has work time
        boolean hasWorkTime = entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0;

        if (hasWorkTime) {
            // Special day WITH work time
            builder.totalWorkedMinutes(0); // Always 0 for special days
            builder.formattedWorkTime("0:00");
            builder.totalOvertimeMinutes(entry.getTotalOvertimeMinutes());
            builder.formattedOvertimeTime(CalculateWorkHoursUtil.minutesToHHmm(entry.getTotalOvertimeMinutes()));
            builder.hasSpecialDayWork(true);

            // Set appropriate CSS class for special day with work
            String cssClass = switch (entry.getTimeOffType()) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "bg-warning text-dark"; // Yellow for SN
                case WorkCode.TIME_OFF_CODE -> "bg-info text-white"; // Blue for CO
                case WorkCode.MEDICAL_LEAVE_CODE -> "bg-orange text-white"; // Orange for CM
                case WorkCode.WEEKEND_CODE -> "bg-secondary text-white"; // Gray for W
                default -> "bg-primary text-white";
            };
            builder.timeOffClass(cssClass);
        } else {
            // Special day WITHOUT work time (regular time off)
            builder.totalWorkedMinutes(0);
            builder.formattedWorkTime(null);
            builder.totalOvertimeMinutes(0);
            builder.formattedOvertimeTime(null);
            builder.hasSpecialDayWork(false);

            // Set appropriate CSS class for regular time off
            String cssClass = switch (entry.getTimeOffType()) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "bg-success"; // Green for SN
                case WorkCode.TIME_OFF_CODE -> "bg-info"; // Blue for CO
                case WorkCode.MEDICAL_LEAVE_CODE -> "bg-warning"; // Orange for CM
                case WorkCode.WEEKEND_CODE -> "bg-secondary"; // Gray for W
                default -> "bg-secondary";
            };
            builder.timeOffClass(cssClass);
        }

        // No discarded minutes for special days
        builder.discardedMinutes(0);
    }

    /**
     * Handle regular time off conversion (non-special days)
     */
    private static void handleRegularTimeOffConversion(WorkTimeTable entry, WorkTimeEntryDTOBuilder builder) {
        // Set time off CSS class for non-special time off types
        builder.timeOffClass("bg-secondary");

        // No work time for regular time off
        builder.totalWorkedMinutes(0);
        builder.formattedWorkTime(null);
        builder.totalOvertimeMinutes(0);
        builder.formattedOvertimeTime(null);
        builder.discardedMinutes(0);
        builder.hasSpecialDayWork(false);
    }

    /**
     * Handle regular work day conversion with proper calculations
     */
    private static void handleRegularWorkDayConversion(WorkTimeTable entry, WorkTimeEntryDTOBuilder builder, int userSchedule) {
        // Calculate work times if this is a work entry (not time off)
        if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            int rawWorkedMinutes = entry.getTotalWorkedMinutes();

            // Use calculation utility for consistency
            WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(rawWorkedMinutes, userSchedule);

            builder.totalWorkedMinutes(result.getProcessedMinutes());
            builder.formattedWorkTime(CalculateWorkHoursUtil.minutesToHHmm(result.getProcessedMinutes()));
            builder.totalOvertimeMinutes(result.getOvertimeMinutes());

            if (result.getOvertimeMinutes() > 0) {
                builder.formattedOvertimeTime(CalculateWorkHoursUtil.minutesToHHmm(result.getOvertimeMinutes()));
            }

            builder.lunchBreakApplied(result.isLunchDeducted());
            builder.hasSpecialDayWork(false);

            int discardedMinutes = CalculateWorkHoursUtil.calculateDiscardedMinutes(rawWorkedMinutes, userSchedule);
            builder.discardedMinutes(discardedMinutes);

        } else {
            // No work time
            builder.totalWorkedMinutes(0);
            builder.formattedWorkTime(null);
            builder.totalOvertimeMinutes(0);
            builder.formattedOvertimeTime(null);
            builder.hasSpecialDayWork(false);
        }

    }

    /**
     * ENHANCED: Check if a time off type is a special day type (static method)
     */
    private static boolean isSpecialDayTypeStatic(String timeOffType) {
        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(timeOffType) ||
                WorkCode.TIME_OFF_CODE.equals(timeOffType) ||
                WorkCode.MEDICAL_LEAVE_CODE.equals(timeOffType) ||
                WorkCode.WEEKEND_CODE.equals(timeOffType);
    }
}