package com.ctgraphdep.worktime.display.calculators;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeDisplayDTO;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.display.counters.TimeOffDayCounter;
import com.ctgraphdep.worktime.display.counters.WorkDayCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Centralized calculator for work time summaries.
 * This class coordinates ALL summary calculations by delegating to specialized counters:
 * - TimeOffDayCounter: Counts SN, CO, CM days
 * - WorkDayCounter: Counts work days (regular, ZS, CR, D)
 * - OvertimeDeductionCalculator: Calculates CR/ZS deductions
 * Provides TWO calculation methods:
 * 1. calculateMonthSummary(): From raw WorkTimeTable entries (user view)
 * 2. calculateSummaryFromDTOs(): From WorkTimeDisplayDTOs (admin view)
 * Both methods produce identical WorkTimeSummary results using consistent logic.
 */
@Component
@RequiredArgsConstructor
public class WorkTimeSummaryCalculator {

    private final TimeOffDayCounter timeOffDayCounter;
    private final WorkDayCounter workDayCounter;
    private final OvertimeDeductionCalculator overtimeDeductionCalculator;

    /**
     * Calculate month summary from WorkTimeTable entries (user view).
     *
     * @param displayableEntries Filtered worktime entries for display
     * @param year Year
     * @param month Month
     * @param user User whose schedule to use
     * @return Complete work time summary
     */
    public WorkTimeSummary calculateMonthSummary(List<WorkTimeTable> displayableEntries, int year, int month, User user) {
        // Calculate total work days in month (excluding weekends)
        int totalWorkDays = calculateTotalWorkDaysInMonth(year, month);

        // Use specialized counters
        TimeOffDayCounter.TimeOffDayCounts timeOffCounts = timeOffDayCounter.countFromEntries(displayableEntries);
        int daysWorked = workDayCounter.countFromEntries(displayableEntries);
        OvertimeDeductionCalculator.DeductionResult deductions = overtimeDeductionCalculator.calculateForUser(displayableEntries, user);

        // Calculate time totals
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalDiscardedMinutes = 0;

        int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;

        for (WorkTimeTable entry : displayableEntries) {
            // Skip in-process entries
            if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            // Regular work entry (no time off type)
            if (entry.getTimeOffType() == null && entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                int discardedForEntry = CalculateWorkHoursUtil.calculateDiscardedMinutes(entry.getTotalWorkedMinutes(), userSchedule);
                WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(entry.getTotalWorkedMinutes(), userSchedule);
                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += result.getOvertimeMinutes();
                totalDiscardedMinutes += discardedForEntry;

                LoggerUtil.debug(this.getClass(), String.format(
                        "Regular work entry %s: %d raw minutes, %d processed, %d overtime, %d discarded",
                        entry.getWorkDate(), entry.getTotalWorkedMinutes(),
                        result.getProcessedMinutes(), result.getOvertimeMinutes(), discardedForEntry));
            }

            // Special day types with overtime work (SN:5, CO:6, etc.)
            if (isSpecialDayType(entry.getTimeOffType()) && entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
                totalOvertimeMinutes += entry.getTotalOvertimeMinutes();
                LoggerUtil.debug(this.getClass(), String.format(
                        "Added %s overtime: %d minutes for %s",
                        entry.getTimeOffType(), entry.getTotalOvertimeMinutes(), entry.getWorkDate()));
            }
        }

        // Apply CR/ZS deductions (move from overtime → regular)
        int adjustedRegularMinutes = totalRegularMinutes + deductions.getTotalDeductions();
        int adjustedOvertimeMinutes = totalOvertimeMinutes - deductions.getTotalDeductions();

        // Calculate remaining work days
        int remainingWorkDays = totalWorkDays - (daysWorked + timeOffCounts.getTotalDays());

        LoggerUtil.info(this.getClass(), String.format(
                "Month summary calculated: totalWorkDays=%d, daysWorked=%d, snDays=%d, coDays=%d, cmDays=%d, " +
                "remainingWorkDays=%d, totalRegular=%d (adjusted: %d), totalOvertime=%d (adjusted: %d), deductions=%s",
                totalWorkDays, daysWorked, timeOffCounts.getSnDays(), timeOffCounts.getCoDays(), timeOffCounts.getCmDays(),
                remainingWorkDays, totalRegularMinutes, adjustedRegularMinutes, totalOvertimeMinutes, adjustedOvertimeMinutes,
                deductions.getDescription()));

        return WorkTimeSummary.builder()
                .totalWorkDays(totalWorkDays)
                .daysWorked(daysWorked)
                .remainingWorkDays(remainingWorkDays)
                .snDays(timeOffCounts.getSnDays())
                .coDays(timeOffCounts.getCoDays())
                .cmDays(timeOffCounts.getCmDays())
                .totalRegularMinutes(adjustedRegularMinutes)
                .totalOvertimeMinutes(adjustedOvertimeMinutes)
                .totalMinutes(adjustedRegularMinutes + adjustedOvertimeMinutes)
                .discardedMinutes(totalDiscardedMinutes)
                .build();
    }

    /**
     * Calculate summary from WorkTimeDisplayDTOs (admin view).
     *
     * @param userDTOs Map of date to display DTO
     * @param totalWorkDays Total work days in month
     * @param user User whose schedule to use
     * @return Complete work time summary
     */
    public WorkTimeSummary calculateSummaryFromDTOs(Map<LocalDate, WorkTimeDisplayDTO> userDTOs, int totalWorkDays, User user) {
        // Use specialized counters
        TimeOffDayCounter.TimeOffDayCounts timeOffCounts = timeOffDayCounter.countFromDTOs(userDTOs);
        int daysWorked = workDayCounter.countFromDTOs(userDTOs);
        OvertimeDeductionCalculator.DeductionResult deductions = overtimeDeductionCalculator.calculateForUserFromDTOs(userDTOs, user);

        // Calculate time totals from DTOs
        int totalRegularMinutes = userDTOs.values().stream()
                .filter(dto -> dto.isHasEntry() && dto.getContributedRegularMinutes() > 0)
                .mapToInt(WorkTimeDisplayDTO::getContributedRegularMinutes)
                .sum();

        int totalOvertimeMinutes = userDTOs.values().stream()
                .filter(dto -> dto.isHasEntry() && dto.getContributedOvertimeMinutes() > 0)
                .mapToInt(WorkTimeDisplayDTO::getContributedOvertimeMinutes)
                .sum();

        // Apply CR/ZS deductions (move from overtime → regular)
        int adjustedRegularMinutes = totalRegularMinutes + deductions.getTotalDeductions();
        int adjustedOvertimeMinutes = totalOvertimeMinutes - deductions.getTotalDeductions();

        // Calculate remaining work days
        int remainingWorkDays = totalWorkDays - (daysWorked + timeOffCounts.getTotalDays());

        LoggerUtil.info(this.getClass(), String.format(
                "Admin summary for user: daysWorked=%d (includes CR/ZS/D), regular=%d (adjusted: %d), " +
                "overtime=%d (adjusted: %d), deductions=%s",
                daysWorked, totalRegularMinutes, adjustedRegularMinutes, totalOvertimeMinutes, adjustedOvertimeMinutes,
                deductions.getDescription()));

        return WorkTimeSummary.builder()
                .totalWorkDays(totalWorkDays)
                .daysWorked(daysWorked)
                .remainingWorkDays(remainingWorkDays)
                .snDays(timeOffCounts.getSnDays())
                .coDays(timeOffCounts.getCoDays())
                .cmDays(timeOffCounts.getCmDays())
                .totalRegularMinutes(adjustedRegularMinutes)
                .totalOvertimeMinutes(adjustedOvertimeMinutes)
                .totalMinutes(adjustedRegularMinutes + adjustedOvertimeMinutes)
                .discardedMinutes(0)  // Discarded minutes not tracked in DTO view
                .build();
    }

    /**
     * Calculate total work days in a month (excluding weekends).
     */
    private int calculateTotalWorkDaysInMonth(int year, int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        return (int) firstDay.datesUntil(lastDay.plusDays(1))
                .filter(date -> date.getDayOfWeek().getValue() < 6) // Exclude weekends (Sat=6, Sun=7)
                .count();
    }

    /**
     * Check if time-off type is a special day type (eligible for overtime work).
     */
    private boolean isSpecialDayType(String timeOffType) {
        if (timeOffType == null) {
            return false;
        }
        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(timeOffType) ||
               WorkCode.TIME_OFF_CODE.equals(timeOffType) ||
               WorkCode.MEDICAL_LEAVE_CODE.equals(timeOffType) ||
               WorkCode.SPECIAL_EVENT_CODE.equals(timeOffType) ||
               WorkCode.WEEKEND_CODE.equals(timeOffType);
    }
}
