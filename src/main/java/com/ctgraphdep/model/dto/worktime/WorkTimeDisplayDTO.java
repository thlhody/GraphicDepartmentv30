package com.ctgraphdep.model.dto.worktime;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.status.GeneralDataStatusDTO;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * DTO for frontend worktime display with pre-calculated display values.
 * Eliminates frontend calculation inconsistencies by processing everything in backend.
 * Contains:
 * - Pre-calculated display text (what users see in cells)
 * - CSS classes for styling
 * - Raw data for editing functionality
 * - Metadata for frontend logic
 */
@Data
@Builder
public class WorkTimeDisplayDTO {

    // ========================================================================
    // DISPLAY VALUES (Pre-calculated by backend)
    // ========================================================================

    /** What users see in the cell (e.g., "10h", "SN5", "CO", "-") */
    private String displayText;

    /** CSS class for cell styling (e.g., "holiday", "vacation", "medical", "sn-work-display") */
    private String cssClass;

    /** Tooltip text with detailed information */
    private String tooltipText;

    // ========================================================================
    // RAW DATA (For editing functionality)
    // ========================================================================

    /** Original WorkTimeTable entry (null if no entry exists) */
    private WorkTimeTable rawEntry;

    /** User ID for this cell */
    private Integer userId;

    /** Date for this cell */
    private LocalDate date;

    /** Formatted date string for frontend attributes */
    private String dateString;

    // ========================================================================
    // NEW: COMPREHENSIVE STATUS INFORMATION
    // ========================================================================

    /** Complete status information decoded from Universal Merge status */
    private GeneralDataStatusDTO statusInfo;

    // ========================================================================
    // METADATA (For frontend logic)
    // ========================================================================

    /** Whether this cell has any entry */
    private boolean hasEntry;

    /** Whether this is a time off entry */
    private boolean isTimeOff;

    /** Whether this is SN with work hours */
    private boolean isSNWithWork;

    /** Whether this cell is editable */
    private boolean isEditable;

    /** Whether this is a weekend day */
    private boolean isWeekend;

    // ========================================================================
    // CALCULATED VALUES (For consistency verification)
    // ========================================================================

    /** Processed work hours that contribute to totals */
    private Integer contributedRegularMinutes;

    /** Processed overtime minutes that contribute to totals */
    private Integer contributedOvertimeMinutes;

    /** Total minutes that contribute to display totals */
    private Integer totalContributedMinutes;

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Create DTO for empty cell (no entry)
     */
    public static WorkTimeDisplayDTO createEmpty(Integer userId, LocalDate date, boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        return WorkTimeDisplayDTO.builder()
                .displayText("-")
                .cssClass(isWeekend ? "weekend" : "")
                .tooltipText("No entry for this date")
                .rawEntry(null)
                .userId(userId)
                .date(date)
                .dateString(formatDateForFrontend(date))
                .statusInfo(statusInfo)
                .hasEntry(false)
                .isTimeOff(false)
                .isSNWithWork(false)
                .isEditable(!statusInfo.isLocked())
                .isWeekend(isWeekend)
                .contributedRegularMinutes(0)
                .contributedOvertimeMinutes(0)
                .totalContributedMinutes(0)
                .build();
    }

    /**
     * Create DTO for work time entry
     */
    public static WorkTimeDisplayDTO createFromWorkEntry(WorkTimeTable entry, Integer userSchedule, boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        // Calculate processed values using backend logic
        WorkTimeCalculationResultDTO calculationResult = CalculateWorkHoursUtil.calculateWorkTime(entry.getTotalWorkedMinutes(), userSchedule);

        int totalProcessedMinutes = calculationResult.getProcessedMinutes() + calculationResult.getOvertimeMinutes();
        String displayHours = String.valueOf(totalProcessedMinutes / 60);

        return WorkTimeDisplayDTO.builder()
                .displayText(displayHours)
                .cssClass(isWeekend ? "weekend" : "")
                .tooltipText(buildWorkTooltip(entry, calculationResult, statusInfo))
                .rawEntry(entry)
                .userId(entry.getUserId())
                .date(entry.getWorkDate())
                .dateString(formatDateForFrontend(entry.getWorkDate()))
                .statusInfo(statusInfo)
                .hasEntry(true)
                .isTimeOff(false)
                .isSNWithWork(false)
                .isEditable(true)
                .isWeekend(isWeekend)
                .contributedRegularMinutes(calculationResult.getProcessedMinutes())
                .contributedOvertimeMinutes(calculationResult.getOvertimeMinutes())
                .totalContributedMinutes(totalProcessedMinutes)
                .build();
    }

    /**
     * Create DTO for time off entry (CO, CM, SN without work)
     */
    public static WorkTimeDisplayDTO createFromTimeOffEntry(WorkTimeTable entry, boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        String cssClass = determineCssClassForTimeOff(entry.getTimeOffType(), isWeekend);
        String tooltip = buildTimeOffTooltip(entry, statusInfo);

        return WorkTimeDisplayDTO.builder()
                .displayText(entry.getTimeOffType())
                .cssClass(cssClass)
                .tooltipText(tooltip)
                .rawEntry(entry)
                .userId(entry.getUserId())
                .date(entry.getWorkDate())
                .dateString(formatDateForFrontend(entry.getWorkDate()))
                .statusInfo(statusInfo)
                .hasEntry(true)
                .isTimeOff(true)
                .isSNWithWork(false)
                .isEditable(!statusInfo.isLocked())
                .isWeekend(isWeekend)
                .contributedRegularMinutes(0)
                .contributedOvertimeMinutes(0)
                .totalContributedMinutes(0)
                .build();
    }

    /**
     * Create DTO for SN with work hours entry
     */
    public static WorkTimeDisplayDTO createFromSNWorkEntry(WorkTimeTable entry, boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        int overtimeHours = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() / 60 : 0;
        String displayText = WorkCode.NATIONAL_HOLIDAY_CODE + overtimeHours;
        String tooltip = buildSNWorkTooltip(entry, statusInfo);

        return WorkTimeDisplayDTO.builder()
                .displayText(displayText)
                .cssClass("sn-work-display" + (isWeekend ? " weekend" : ""))
                .tooltipText(tooltip)
                .rawEntry(entry)
                .userId(entry.getUserId())
                .date(entry.getWorkDate())
                .dateString(formatDateForFrontend(entry.getWorkDate()))
                .statusInfo(statusInfo)
                .hasEntry(true)
                .isTimeOff(true)
                .isSNWithWork(true)
                .isEditable(!statusInfo.isLocked())
                .isWeekend(isWeekend)
                .contributedRegularMinutes(0)
                .contributedOvertimeMinutes(entry.getTotalOvertimeMinutes())
                .totalContributedMinutes(entry.getTotalOvertimeMinutes())
                .build();
    }
    /**
     * Create DTO for CO with work hours entry
     */
    public static WorkTimeDisplayDTO createFromCOWorkEntry(WorkTimeTable entry, boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        int overtimeHours = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() / 60 : 0;
        String displayText = WorkCode.TIME_OFF_CODE + overtimeHours;
        String tooltip = buildCOWorkTooltip(entry, statusInfo);

        return WorkTimeDisplayDTO.builder()
                .displayText(displayText)
                .cssClass("co-work-display" + (isWeekend ? " weekend" : ""))
                .tooltipText(tooltip)
                .rawEntry(entry)
                .userId(entry.getUserId())
                .date(entry.getWorkDate())
                .dateString(formatDateForFrontend(entry.getWorkDate()))
                .statusInfo(statusInfo)
                .hasEntry(true)
                .isTimeOff(true)
                .isSNWithWork(false) // Keep this false, add new flags if needed
                .isEditable(!statusInfo.isLocked())
                .isWeekend(isWeekend)
                .contributedRegularMinutes(0)
                .contributedOvertimeMinutes(entry.getTotalOvertimeMinutes())
                .totalContributedMinutes(entry.getTotalOvertimeMinutes())
                .build();
    }

    /**
     * Create DTO for CM with work hours entry
     */
    public static WorkTimeDisplayDTO createFromCMWorkEntry(WorkTimeTable entry, boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        int overtimeHours = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() / 60 : 0;
        String displayText = WorkCode.MEDICAL_LEAVE_CODE + overtimeHours;
        String tooltip = buildCMWorkTooltip(entry, statusInfo);

        return WorkTimeDisplayDTO.builder()
                .displayText(displayText)
                .cssClass("cm-work-display" + (isWeekend ? " weekend" : ""))
                .tooltipText(tooltip)
                .rawEntry(entry)
                .userId(entry.getUserId())
                .date(entry.getWorkDate())
                .dateString(formatDateForFrontend(entry.getWorkDate()))
                .statusInfo(statusInfo)
                .hasEntry(true)
                .isTimeOff(true)
                .isSNWithWork(false)
                .isEditable(!statusInfo.isLocked())
                .isWeekend(isWeekend)
                .contributedRegularMinutes(0)
                .contributedOvertimeMinutes(entry.getTotalOvertimeMinutes())
                .totalContributedMinutes(entry.getTotalOvertimeMinutes())
                .build();
    }

    /**
     * Create DTO for W with work hours entry
     */
    public static WorkTimeDisplayDTO createFromWWorkEntry(WorkTimeTable entry, boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        int overtimeHours = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() / 60 : 0;
        String displayText = WorkCode.WEEKEND_CODE + overtimeHours;
        String tooltip = buildWWorkTooltip(entry, statusInfo);

        return WorkTimeDisplayDTO.builder()
                .displayText(displayText)
                .cssClass("w-work-display" + (isWeekend ? " weekend" : ""))
                .tooltipText(tooltip)
                .rawEntry(entry)
                .userId(entry.getUserId())
                .date(entry.getWorkDate())
                .dateString(formatDateForFrontend(entry.getWorkDate()))
                .statusInfo(statusInfo)
                .hasEntry(true)
                .isTimeOff(true)
                .isSNWithWork(false)
                .isEditable(!statusInfo.isLocked())
                .isWeekend(isWeekend)
                .contributedRegularMinutes(0)
                .contributedOvertimeMinutes(entry.getTotalOvertimeMinutes())
                .totalContributedMinutes(entry.getTotalOvertimeMinutes())
                .build();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private static String formatDateForFrontend(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("yyyy-M-d"));
    }

    private static String determineCssClassForTimeOff(String timeOffType, boolean isWeekend) {
        String baseClass = switch (timeOffType) {
            case WorkCode.NATIONAL_HOLIDAY_CODE -> "holiday";
            case WorkCode.TIME_OFF_CODE -> "vacation";
            case WorkCode.MEDICAL_LEAVE_CODE -> "medical";
            case WorkCode.WEEKEND_CODE -> "weekend";
            default -> "";
        };

        return isWeekend ? baseClass + " weekend" : baseClass;
    }

    private static String buildWorkTooltip(WorkTimeTable entry, WorkTimeCalculationResultDTO calculationResult, GeneralDataStatusDTO statusInfo) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append("Work Time Entry\n");
        tooltip.append("Date: ").append(entry.getWorkDate()).append("\n");

        if (entry.getDayStartTime() != null) {
            tooltip.append("Start: ").append(entry.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getDayEndTime() != null) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("End: ").append(entry.getDayEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Total worked: ").append(CalculateWorkHoursUtil.minutesToHHmm(entry.getTotalWorkedMinutes()));
        }

        if (calculationResult.getProcessedMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Regular: ").append(CalculateWorkHoursUtil.minutesToHHmm(calculationResult.getProcessedMinutes()));
        }

        if (calculationResult.getOvertimeMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Overtime: ").append(CalculateWorkHoursUtil.minutesToHHmm(calculationResult.getOvertimeMinutes()));
        }

        if (entry.isLunchBreakDeducted()) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Lunch: Deducted");
        }

        // NEW: Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip.append("\nStatus: ").append(statusInfo.getFullDisplay());
        }

        return tooltip.toString();
    }

    private static String buildTimeOffTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        String typeLabel = switch (entry.getTimeOffType()) {
            case WorkCode.NATIONAL_HOLIDAY_CODE -> "National Holiday";
            case WorkCode.TIME_OFF_CODE -> "Vacation";
            case WorkCode.MEDICAL_LEAVE_CODE -> "Medical Leave";
            default -> entry.getTimeOffType();
        };

        String tooltip = "Type: " + typeLabel;

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip += " | Status: " + statusInfo.getMediumDisplay();
        }

        return tooltip;
    }

    /**
     * UPDATED: Build tooltip for SN work entries including detailed work information and status
     */
    private static String buildSNWorkTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append("National Holiday with Work\n");
        tooltip.append("Date: ").append(entry.getWorkDate()).append("\n");

        if (entry.getDayStartTime() != null) {
            tooltip.append("Start: ").append(entry.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getDayEndTime() != null) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("End: ").append(entry.getDayEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Overtime: ").append(CalculateWorkHoursUtil.minutesToHHmm(entry.getTotalOvertimeMinutes()));
        }

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip.append("\nStatus: ").append(statusInfo.getFullDisplay());
        }

        return tooltip.toString();
    }

    /**
     * UPDATED: Build tooltip for CO work entries including detailed work information and status
     */
    private static String buildCOWorkTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append("Vacation Day with Work\n");
        tooltip.append("Date: ").append(entry.getWorkDate()).append("\n");

        if (entry.getDayStartTime() != null) {
            tooltip.append("Start: ").append(entry.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getDayEndTime() != null) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("End: ").append(entry.getDayEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Overtime: ").append(CalculateWorkHoursUtil.minutesToHHmm(entry.getTotalOvertimeMinutes()));
        }

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip.append("\nStatus: ").append(statusInfo.getFullDisplay());
        }

        return tooltip.toString();
    }

    /**
     * UPDATED: Build tooltip for CM work entries including detailed work information and status
     */
    private static String buildCMWorkTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append("Medical Leave with Work\n");
        tooltip.append("Date: ").append(entry.getWorkDate()).append("\n");

        if (entry.getDayStartTime() != null) {
            tooltip.append("Start: ").append(entry.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getDayEndTime() != null) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("End: ").append(entry.getDayEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Overtime: ").append(CalculateWorkHoursUtil.minutesToHHmm(entry.getTotalOvertimeMinutes()));
        }

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip.append("\nStatus: ").append(statusInfo.getFullDisplay());
        }

        return tooltip.toString();
    }

    /**
     * UPDATED: Build tooltip for W work entries including detailed work information and status
     */
    private static String buildWWorkTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append("Weekend with Work\n");
        tooltip.append("Date: ").append(entry.getWorkDate()).append("\n");

        if (entry.getDayStartTime() != null) {
            tooltip.append("Start: ").append(entry.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getDayEndTime() != null) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("End: ").append(entry.getDayEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Overtime: ").append(CalculateWorkHoursUtil.minutesToHHmm(entry.getTotalOvertimeMinutes()));
        }

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip.append("\nStatus: ").append(statusInfo.getFullDisplay());
        }

        return tooltip.toString();
    }
}