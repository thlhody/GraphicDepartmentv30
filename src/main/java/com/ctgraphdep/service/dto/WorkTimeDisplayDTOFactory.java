package com.ctgraphdep.service.dto;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.status.GeneralDataStatusDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeDisplayDTO;
import com.ctgraphdep.service.CalculationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Factory service for creating WorkTimeDisplayDTO instances.
 * Extracted from WorkTimeDisplayDTO to separate data structure from construction logic.
 * Responsibilities:
 * - Create DTOs for different entry types (empty, work, time off, special cases)
 * - Calculate display values and formatting
 * - Build tooltips with detailed information
 * - Determine CSS classes for styling
 * This service encapsulates all business logic related to DTO creation,
 * keeping the DTO itself as a pure data container.
 */
@Service
public class WorkTimeDisplayDTOFactory {

    private final CalculationService calculationService;

    public WorkTimeDisplayDTOFactory(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    private static final DateTimeFormatter FRONTEND_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // ========================================================================
    // PUBLIC FACTORY METHODS
    // ========================================================================

    /**
     * Create DTO for empty cell (no entry)
     */
    public WorkTimeDisplayDTO createEmpty(Integer userId, LocalDate date, boolean isWeekend, GeneralDataStatusDTO statusInfo) {
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
     * Create DTO for work time entry (regular working day)
     */
    public WorkTimeDisplayDTO createFromWorkEntry(WorkTimeTable entry, Integer userSchedule,
                                                  boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        // Calculate processed values using backend logic
        WorkTimeCalculationResultDTO calculationResult =
            calculationService.calculateWorkTime(entry.getTotalWorkedMinutes(), userSchedule);

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
    public WorkTimeDisplayDTO createFromTimeOffEntry(WorkTimeTable entry, boolean isWeekend,
                                                     GeneralDataStatusDTO statusInfo) {
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
     * Create DTO for SN with work hours entry (National Holiday with work)
     */
    public WorkTimeDisplayDTO createFromSNWorkEntry(WorkTimeTable entry, boolean isWeekend,
                                                    GeneralDataStatusDTO statusInfo) {
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
     * Create DTO for CO with work hours entry (Vacation with work)
     */
    public WorkTimeDisplayDTO createFromCOWorkEntry(WorkTimeTable entry, boolean isWeekend,
                                                    GeneralDataStatusDTO statusInfo) {
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
                .isSNWithWork(false)
                .isEditable(!statusInfo.isLocked())
                .isWeekend(isWeekend)
                .contributedRegularMinutes(0)
                .contributedOvertimeMinutes(entry.getTotalOvertimeMinutes())
                .totalContributedMinutes(entry.getTotalOvertimeMinutes())
                .build();
    }

    /**
     * Create DTO for CM with work hours entry (Medical Leave with work)
     */
    public WorkTimeDisplayDTO createFromCMWorkEntry(WorkTimeTable entry, boolean isWeekend,
                                                    GeneralDataStatusDTO statusInfo) {
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
     * Create DTO for W with work hours entry (Weekend with work)
     */
    public WorkTimeDisplayDTO createFromWWorkEntry(WorkTimeTable entry, boolean isWeekend,
                                                   GeneralDataStatusDTO statusInfo) {
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

    /**
     * Create DTO for CE with work hours entry (Special Event with work)
     */
    public WorkTimeDisplayDTO createFromCEWorkEntry(WorkTimeTable entry, boolean isWeekend,
                                                    GeneralDataStatusDTO statusInfo) {
        int overtimeHours = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() / 60 : 0;
        String displayText = WorkCode.SPECIAL_EVENT_CODE + overtimeHours;
        String tooltip = buildCEWorkTooltip(entry, statusInfo);

        return WorkTimeDisplayDTO.builder()
                .displayText(displayText)
                .cssClass("ce-work-display" + (isWeekend ? " weekend" : ""))
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
     * Create DTO for CR entry (Recovery Leave - paid from overtime)
     * CR represents a full work day when the user takes a recovery day paid from overtime balance.
     * The full schedule is deducted from overtime and counts as regular work time.
     * Example: User with 8h schedule takes CR → counts as 8h regular work (deducted from overtime)
     */
    public WorkTimeDisplayDTO createFromCREntry(WorkTimeTable entry, Integer userSchedule, boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        int scheduleMinutes = userSchedule * 60;
        String tooltip = buildCRTooltip(entry, userSchedule, statusInfo);

        // CR represents a full work day paid from overtime pool
        // Therefore, CR should contribute the FULL SCHEDULE as regular minutes (not 0)
        // Example: User with 8h schedule takes CR
        //   - Day counts as 8h regular work (filled from overtime)
        //   - Overtime deduction calculator will handle the -8h from overtime pool

        return WorkTimeDisplayDTO.builder()
                .displayText(WorkCode.RECOVERY_LEAVE_CODE)
                .cssClass("cr-display" + (isWeekend ? " weekend" : ""))
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
                .contributedRegularMinutes(scheduleMinutes) // CR counts as full work day (filled from overtime)
                .contributedOvertimeMinutes(0) // No overtime generated on CR days
                .totalContributedMinutes(scheduleMinutes)
                .build();
    }

    /**
     * Create DTO for CN entry (Unpaid Leave)
     */
    public WorkTimeDisplayDTO createFromCNEntry(WorkTimeTable entry, boolean isWeekend,
                                                GeneralDataStatusDTO statusInfo) {
        String displayText = WorkCode.UNPAID_LEAVE_CODE;
        String tooltip = buildCNTooltip(statusInfo);

        return WorkTimeDisplayDTO.builder()
                .displayText(displayText)
                .cssClass("cn-display" + (isWeekend ? " weekend" : ""))
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
     * Create DTO for ZS entry (Short Day - worked < schedule, filled from overtime)
     * Display format: "ZS-X" where X = missing hours (from the stored timeOffType)
     * IMPORTANT: Use the STORED timeOffType value, don't recalculate!
     * The ZS value has already been calculated correctly with lunch deduction during consolidation.
     */
    public WorkTimeDisplayDTO createFromZSEntry(WorkTimeTable entry, Integer userSchedule,
                                                boolean isWeekend, GeneralDataStatusDTO statusInfo) {
        int scheduleMinutes = userSchedule * 60;

        // IMPORTANT: Use the stored timeOffType value (e.g., "ZS-1", "ZS-3")
        // Don't recalculate - the value was already correctly calculated during consolidation
        // with proper lunch deduction handling
        String displayText = entry.getTimeOffType() != null ? entry.getTimeOffType() : "ZS";

        String tooltip = buildZSTooltip(entry, userSchedule, statusInfo);

        // ZS represents a full work day when the user worked less than schedule
        // The missing hours are filled from overtime pool, but it counts as a complete work day
        // Therefore, ZS should contribute the FULL SCHEDULE as regular minutes (not 0)
        // Example: User with 8h schedule works 5h → ZS-3
        //   - Day counts as 8h regular work (filled 3h from overtime)
        //   - Overtime deduction calculator will handle the -3h from overtime pool

        return WorkTimeDisplayDTO.builder()
                .displayText(displayText)
                .cssClass("zs-display" + (isWeekend ? " weekend" : ""))
                .tooltipText(tooltip)
                .rawEntry(entry)
                .userId(entry.getUserId())
                .date(entry.getWorkDate())
                .dateString(formatDateForFrontend(entry.getWorkDate()))
                .statusInfo(statusInfo)
                .hasEntry(true)
                .isTimeOff(true) // ZS is a special time off type
                .isSNWithWork(false)
                .isEditable(!statusInfo.isLocked())
                .isWeekend(isWeekend)
                .contributedRegularMinutes(scheduleMinutes) // ZS counts as full work day (filled from overtime)
                .contributedOvertimeMinutes(0) // No overtime generated on ZS days
                .totalContributedMinutes(scheduleMinutes)
                .build();
    }

    // ========================================================================
    // PRIVATE HELPER METHODS - Date Formatting
    // ========================================================================

    private String formatDateForFrontend(LocalDate date) {
        return date.format(FRONTEND_DATE_FORMAT);
    }

    // ========================================================================
    // PRIVATE HELPER METHODS - CSS Class Determination
    // ========================================================================

    private String determineCssClassForTimeOff(String timeOffType, boolean isWeekend) {
        // Handle ZS-X format (e.g., "ZS-5")
        if (timeOffType != null && timeOffType.startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
            return "zs-display";
        }

        assert timeOffType != null;
        String baseClass = switch (timeOffType) {
            case WorkCode.NATIONAL_HOLIDAY_CODE -> "holiday";
            case WorkCode.TIME_OFF_CODE -> "vacation";
            case WorkCode.MEDICAL_LEAVE_CODE -> "medical";
            case WorkCode.WEEKEND_CODE -> "weekend";
            case WorkCode.RECOVERY_LEAVE_CODE -> "cr-display";
            case WorkCode.UNPAID_LEAVE_CODE -> "cn-display";
            case WorkCode.SPECIAL_EVENT_CODE -> "ce-display";
            case WorkCode.DELEGATION_CODE -> "d-display";
            default -> "";
        };

        return isWeekend ? baseClass + " weekend" : baseClass;
    }

    // ========================================================================
    // PRIVATE HELPER METHODS - Tooltip Building
    // ========================================================================

    /**
     * Build detailed tooltip for regular work entries
     */
    private String buildWorkTooltip(WorkTimeTable entry, WorkTimeCalculationResultDTO calculationResult,
                                   GeneralDataStatusDTO statusInfo) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append("Work Time Entry\n");
        tooltip.append("Date: ").append(entry.getWorkDate()).append("\n");

        if (entry.getDayStartTime() != null) {
            tooltip.append("Start: ").append(entry.getDayStartTime().format(TIME_FORMAT));
        }

        if (entry.getDayEndTime() != null) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("End: ").append(entry.getDayEndTime().format(TIME_FORMAT));
        }

        if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Total worked: ").append(calculationService.minutesToHHmm(entry.getTotalWorkedMinutes()));
        }

        if (calculationResult.getProcessedMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Regular: ").append(calculationService.minutesToHHmm(calculationResult.getProcessedMinutes()));
        }

        if (calculationResult.getOvertimeMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Overtime: ").append(calculationService.minutesToHHmm(calculationResult.getOvertimeMinutes()));
        }

        if (entry.isLunchBreakDeducted()) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Lunch: Deducted");
        }

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip.append("\nStatus: ").append(statusInfo.getFullDisplay());
        }

        return tooltip.toString();
    }

    /**
     * Build tooltip for simple time off entries (no work)
     */
    private String buildTimeOffTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        // Handle ZS-X format specially
        String timeOffType = entry.getTimeOffType();
        String typeLabel;

        if (timeOffType != null && timeOffType.startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
            typeLabel = "Short Day (" + timeOffType + ")";
        } else {
            assert timeOffType != null;
            typeLabel = switch (timeOffType) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "National Holiday";
                case WorkCode.TIME_OFF_CODE -> "Vacation";
                case WorkCode.MEDICAL_LEAVE_CODE -> "Medical Leave";
                case WorkCode.RECOVERY_LEAVE_CODE -> "Recovery Leave";
                case WorkCode.UNPAID_LEAVE_CODE -> "Unpaid Leave";
                case WorkCode.SPECIAL_EVENT_CODE -> "Event Leave";
                case WorkCode.DELEGATION_CODE -> "Delegation";
                case WorkCode.WEEKEND_CODE -> "Weekend";
                default -> timeOffType;
            };
        }

        String tooltip = "Type: " + typeLabel;

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip += " | Status: " + statusInfo.getMediumDisplay();
        }

        return tooltip;
    }

    /**
     * Build tooltip for SN (National Holiday) with work
     */
    private String buildSNWorkTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        return buildSpecialWorkTooltip("National Holiday with Work", entry, statusInfo);
    }

    /**
     * Build tooltip for CO (Vacation) with work
     */
    private String buildCOWorkTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        return buildSpecialWorkTooltip("Vacation Day with Work", entry, statusInfo);
    }

    /**
     * Build tooltip for CM (Medical Leave) with work
     */
    private String buildCMWorkTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        return buildSpecialWorkTooltip("Medical Leave with Work", entry, statusInfo);
    }

    /**
     * Build tooltip for W (Weekend) with work
     */
    private String buildWWorkTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        return buildSpecialWorkTooltip("Weekend with Work", entry, statusInfo);
    }

    /**
     * Build tooltip for CE (Special Event) with work
     */
    private String buildCEWorkTooltip(WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        return buildSpecialWorkTooltip("Special Event with Work", entry, statusInfo);
    }

    /**
     * Build tooltip for CR (Recovery Leave)
     */
    private String buildCRTooltip(WorkTimeTable entry, Integer userSchedule, GeneralDataStatusDTO statusInfo) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append("Recovery Leave (paid from overtime)\n");
        tooltip.append("Date: ").append(entry.getWorkDate()).append("\n");
        tooltip.append("Schedule: ").append(userSchedule).append("h (full day from overtime)");

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip.append("\nStatus: ").append(statusInfo.getFullDisplay());
        }

        return tooltip.toString();
    }

    /**
     * Build tooltip for CN (Unpaid Leave)
     */
    private String buildCNTooltip(GeneralDataStatusDTO statusInfo) {
        String tooltip = "Unpaid Leave";

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip += "\nStatus: " + statusInfo.getFullDisplay();
        }

        return tooltip;
    }

    /**
     * Build tooltip for ZS (Short Day)
     */
    private String buildZSTooltip(WorkTimeTable entry, Integer userSchedule, GeneralDataStatusDTO statusInfo) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append("Short Day (completed from overtime)\n");
        tooltip.append("Date: ").append(entry.getWorkDate()).append("\n");

        int workedMinutes = entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0;
        int scheduleMinutes = userSchedule * 60;
        int missingMinutes = scheduleMinutes - workedMinutes;

        tooltip.append("Worked: ").append(calculationService.minutesToHH(workedMinutes)).append("\n");
        tooltip.append("Schedule: ").append(userSchedule).append("h\n");
        tooltip.append("Filled from overtime: ").append(calculationService.minutesToHH(missingMinutes));

        if (entry.getTotalOvertimeMinutes() != null) {
            tooltip.append("\nRemaining overtime: ").append(calculationService.minutesToHH(Math.abs(entry.getTotalOvertimeMinutes())));
        }

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip.append("\nStatus: ").append(statusInfo.getFullDisplay());
        }

        return tooltip.toString();
    }

    /**
     * Common tooltip building logic for special work entries (time off + work)
     */
    private String buildSpecialWorkTooltip(String title, WorkTimeTable entry, GeneralDataStatusDTO statusInfo) {
        StringBuilder tooltip = new StringBuilder();

        tooltip.append(title).append("\n");
        tooltip.append("Date: ").append(entry.getWorkDate()).append("\n");

        if (entry.getDayStartTime() != null) {
            tooltip.append("Start: ").append(entry.getDayStartTime().format(TIME_FORMAT));
        }

        if (entry.getDayEndTime() != null) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("End: ").append(entry.getDayEndTime().format(TIME_FORMAT));
        }

        if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
            if (!tooltip.isEmpty()) tooltip.append("\n");
            tooltip.append("Overtime: ").append(calculationService.minutesToHHmm(entry.getTotalOvertimeMinutes()));
        }

        // Add status information
        if (statusInfo != null && statusInfo.isDisplayable()) {
            tooltip.append("\nStatus: ").append(statusInfo.getFullDisplay());
        }

        return tooltip.toString();
    }
}
