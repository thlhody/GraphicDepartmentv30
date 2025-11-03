package com.ctgraphdep.service.dto;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.status.GeneralDataStatusDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.service.CalculationService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Factory service for creating WorkTimeEntryDTO instances.
 * Extracted from WorkTimeEntryDTO to separate data structure from construction logic.
 * Responsibilities:
 * - Convert WorkTimeTable entities to DTOs
 * - Handle all special day types (SN, CO, CM, W) with proper formatting
 * - Calculate work time, overtime, and discarded minutes
 * - Apply CSS classes and generate display values
 * This service encapsulates all business logic related to DTO creation,
 * keeping the DTO itself as a pure data container.
 */
@Service
public class WorkTimeEntryDTOFactory {

    private final CalculationService calculationService;

    public WorkTimeEntryDTOFactory(CalculationService calculationService) {
        this.calculationService = calculationService;
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // ========================================================================
    // PUBLIC FACTORY METHOD
    // ========================================================================

    /**
     * Convert WorkTimeTable entity to DTO with proper special day overtime handling
     */
    public WorkTimeEntryDTO fromWorkTimeTable(WorkTimeTable entry, int userSchedule, GeneralDataStatusDTO statusInfo) {
        WorkTimeEntryDTO.WorkTimeEntryDTOBuilder builder = WorkTimeEntryDTO.builder()
                .userId(entry.getUserId())
                .workDate(entry.getWorkDate())
                .formattedDate(entry.getWorkDate().format(DATE_FORMATTER))
                .dayStartTime(entry.getDayStartTime())
                .dayEndTime(entry.getDayEndTime())
                .temporaryStopCount(entry.getTemporaryStopCount())
                .totalTemporaryStopMinutes(entry.getTotalTemporaryStopMinutes())
                .temporaryStops(entry.getTemporaryStops())
                .lunchBreakDeducted(entry.isLunchBreakDeducted())
                .timeOffType(entry.getTimeOffType())
                .statusInfo(statusInfo);

        // Format start and end times
        if (entry.getDayStartTime() != null) {
            builder.formattedStartTime(entry.getDayStartTime().format(TIME_FORMATTER));
        }

        if (entry.getDayEndTime() != null) {
            builder.formattedEndTime(entry.getDayEndTime().format(TIME_FORMATTER));
        }

        // Format temporary stop if available
        if (entry.getTemporaryStopCount() != null && entry.getTemporaryStopCount() > 0) {
            builder.formattedTemporaryStop(entry.getTemporaryStopCount() + " (" + entry.getTotalTemporaryStopMinutes() + "m)");
        }

        // Handle different entry types
        if (entry.getTimeOffType() != null && isSpecialDayType(entry.getTimeOffType())) {
            handleSpecialDayConversion(entry, builder);
        } else if (entry.getTimeOffType() != null) {
            handleRegularTimeOffConversion(builder);
        } else {
            handleRegularWorkDayConversion(entry, builder, userSchedule);
        }

        return builder.build();
    }

    // ========================================================================
    // PRIVATE CONVERSION METHODS
    // ========================================================================

    /**
     * Handle all special day types (SN/CO/CM/W) with work time
     */
    private void handleSpecialDayConversion(WorkTimeTable entry, WorkTimeEntryDTO.WorkTimeEntryDTOBuilder builder) {
        // Set special day type for display
        builder.specialDayType(entry.getTimeOffType());

        // Check if this special day has work time
        boolean hasWorkTime = entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0;

        builder.totalWorkedMinutes(0);
        if (hasWorkTime) {
            // Special day WITH work time
            builder.formattedWorkTime("0:00");
            builder.totalOvertimeMinutes(entry.getTotalOvertimeMinutes());
            builder.formattedOvertimeTime(calculationService.minutesToHHmm(entry.getTotalOvertimeMinutes()));
            builder.hasSpecialDayWork(true);

            // Set raw and scheduled for special day with work
            builder.rawWorkedMinutes(entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0);
            builder.formattedRawTime(entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0 ?
                    calculationService.minutesToHHmm(entry.getTotalWorkedMinutes()) : "-");
            builder.formattedScheduledTime("-");  // No scheduled time for special days

            // Set appropriate CSS class for special day with work
            String cssClass = switch (entry.getTimeOffType()) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "bg-warning text-dark";
                case WorkCode.TIME_OFF_CODE -> "bg-info text-white";
                case WorkCode.MEDICAL_LEAVE_CODE -> "bg-orange text-white";
                case WorkCode.WEEKEND_CODE -> "bg-secondary text-white";
                default -> "bg-primary text-white";
            };
            builder.timeOffClass(cssClass);
        } else {
            // Special day WITHOUT work time (regular time off)
            builder.formattedWorkTime(null);
            builder.totalOvertimeMinutes(0);
            builder.formattedOvertimeTime(null);
            builder.hasSpecialDayWork(false);

            // No raw or scheduled time for pure time off
            builder.rawWorkedMinutes(null);
            builder.formattedRawTime(null);
            builder.formattedScheduledTime(null);

            // Set appropriate CSS class for regular time off
            String cssClass = switch (entry.getTimeOffType()) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> "bg-success";
                case WorkCode.TIME_OFF_CODE -> "bg-info";
                case WorkCode.MEDICAL_LEAVE_CODE -> "bg-warning";
                case WorkCode.WEEKEND_CODE -> "bg-secondary";
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
    private void handleRegularTimeOffConversion(WorkTimeEntryDTO.WorkTimeEntryDTOBuilder builder) {
        // Set time off CSS class for non-special time off types
        builder.timeOffClass("bg-secondary");

        // No work time for regular time off
        builder.totalWorkedMinutes(0);
        builder.formattedWorkTime(null);
        builder.totalOvertimeMinutes(0);
        builder.formattedOvertimeTime(null);
        builder.discardedMinutes(0);
        builder.hasSpecialDayWork(false);

        // No raw or scheduled time for time off
        builder.rawWorkedMinutes(null);
        builder.formattedRawTime(null);
        builder.formattedScheduledTime(null);
    }

    /**
     * Handle regular work day conversion with proper calculations
     */
    private void handleRegularWorkDayConversion(WorkTimeTable entry, WorkTimeEntryDTO.WorkTimeEntryDTOBuilder builder,
                                               int userSchedule) {
        // Calculate work times if this is a work entry (not time off)
        if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            int rawWorkedMinutes = entry.getTotalWorkedMinutes();

            // Use calculation service for consistency
            WorkTimeCalculationResultDTO result = calculationService.calculateWorkTime(rawWorkedMinutes, userSchedule);

            builder.totalWorkedMinutes(result.getProcessedMinutes());
            builder.formattedWorkTime(calculationService.minutesToHHmm(result.getProcessedMinutes()));
            builder.totalOvertimeMinutes(result.getOvertimeMinutes());

            if (result.getOvertimeMinutes() > 0) {
                builder.formattedOvertimeTime(calculationService.minutesToHHmm(result.getOvertimeMinutes()));
            }

            builder.lunchBreakApplied(result.isLunchDeducted());
            builder.hasSpecialDayWork(false);

            int discardedMinutes = calculationService.calculateDiscardedMinutes(rawWorkedMinutes, userSchedule);
            builder.discardedMinutes(discardedMinutes);

            // Set raw and scheduled time for regular work days
            builder.rawWorkedMinutes(rawWorkedMinutes);
            builder.formattedRawTime(calculationService.minutesToHHmm(rawWorkedMinutes));

            int scheduleMinutes = userSchedule * 60;
            builder.formattedScheduledTime(calculationService.minutesToHHmm(scheduleMinutes));

        } else {
            // No work time
            builder.totalWorkedMinutes(0);
            builder.formattedWorkTime(null);
            builder.totalOvertimeMinutes(0);
            builder.formattedOvertimeTime(null);
            builder.hasSpecialDayWork(false);
            builder.discardedMinutes(0);

            // No raw or scheduled time when no work
            builder.rawWorkedMinutes(null);
            builder.formattedRawTime(null);
            builder.formattedScheduledTime(null);
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Check if a time off type is a special day type (SN, CO, CM, W)
     */
    private boolean isSpecialDayType(String timeOffType) {
        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(timeOffType) ||
                WorkCode.TIME_OFF_CODE.equals(timeOffType) ||
                WorkCode.MEDICAL_LEAVE_CODE.equals(timeOffType) ||
                WorkCode.WEEKEND_CODE.equals(timeOffType);
    }
}
