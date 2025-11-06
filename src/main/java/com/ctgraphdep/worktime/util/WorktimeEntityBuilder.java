package com.ctgraphdep.worktime.util;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.service.CalculationService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class WorktimeEntityBuilder {

    private static CalculationService calculationService;

    public WorktimeEntityBuilder(CalculationService calculationService) {
        WorktimeEntityBuilder.calculationService = calculationService;
    }

    // ========================================================================
    // FACTORY METHODS - Entry Creation (CLEAN - NO VALIDATION)
    // ========================================================================

    // Create a new blank worktime entry with default values
    public static WorkTimeTable createNewEntry(Integer userId, LocalDate date) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);

        // Initialize with safe defaults
        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Created new worktime entry for user %d on %s", userId, date));

        return entry;
    }

    // Create a time off entry (CO/CM/SN)
    public static WorkTimeTable createTimeOffEntry(Integer userId, LocalDate date, String timeOffType) {
        WorkTimeTable entry = createNewEntry(userId, date);
        entry.setTimeOffType(timeOffType.toUpperCase());

        // Time off entries have no work time
        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Created %s time off entry for user %d on %s", timeOffType, userId, date));

        return entry;
    }

    // Create admin work hours entry (admin sets specific hours)
    public static WorkTimeTable createAdminWorkHoursEntry(Integer userId, LocalDate date, int hours) {
        WorkTimeTable entry = createNewEntry(userId, date);

        // Calculate times based on hours
        LocalDateTime startTime = date.atTime(WorkCode.START_HOUR, 0);
        int totalMinutes = (hours * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION;
        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);

        entry.setDayStartTime(startTime);
        entry.setDayEndTime(endTime);
        entry.setTotalWorkedMinutes(totalMinutes);
        entry.setLunchBreakDeducted(hours > WorkCode.INTERVAL_HOURS_A && hours < WorkCode.INTERVAL_HOURS_B);
        entry.setTimeOffType(null);
        entry.setTemporaryStopCount(WorkCode.DEFAULT_ZERO);
        entry.setTotalTemporaryStopMinutes(WorkCode.DEFAULT_ZERO);
        entry.setTotalOvertimeMinutes(WorkCode.DEFAULT_ZERO);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Created admin work hours entry for user %d on %s: %d hours", userId, date, hours));

        return entry;
    }

    // Create CR (Recovery Leave) entry - time off paid from overtime
    // Per spec: CR should only have date + timeOffType, no work times
    // Deduction calculation happens in display service based on user schedule
    public static WorkTimeTable createRecoveryLeaveEntry(Integer userId, LocalDate date, int scheduleHours) {
        WorkTimeTable entry = createNewEntry(userId, date);
        entry.setTimeOffType(WorkCode.RECOVERY_LEAVE_CODE);

        // CR is a time-off entry, not a work entry - reset all work fields
        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
            "Created CR (Recovery Leave) entry for user %d on %s (deducts %d schedule hours from overtime)",
            userId, date, scheduleHours));

        return entry;
    }

    // ========================================================================
    // TEMPORARY STOP UPDATE METHODS - Entry Modification (CLEAN - NO VALIDATION)
    // ========================================================================

    // Update temporary stop minutes with recalculation
    public static WorkTimeTable updateTemporaryStop(WorkTimeTable entry, Integer tempStopMinutes, Integer userSchedule) {
        entry.setTemporaryStopCount(1); // Always set to 1 when user edits
        entry.setTotalTemporaryStopMinutes(tempStopMinutes);

        // Recalculate work time if both start and end times exist
        recalculateWorkTime(entry, userSchedule);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Updated temporary stop for user %d on %s to %d minutes",
                entry.getUserId(), entry.getWorkDate(), tempStopMinutes));

        return entry;
    }

    // Remove temporary stop (reset to 0)
    public static WorkTimeTable removeTemporaryStop(WorkTimeTable entry, Integer userSchedule) {
        entry.setTemporaryStopCount(WorkCode.DEFAULT_ZERO);
        entry.setTotalTemporaryStopMinutes(WorkCode.DEFAULT_ZERO);

        // Recalculate work time if both start and end times exist
        recalculateWorkTime(entry, userSchedule);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Removed temporary stop for user %d on %s", entry.getUserId(), entry.getWorkDate()));

        return entry;
    }

    // ========================================================================
    // SPECIAL DAY METHODS
    // ========================================================================

    // Create special day entry with work time (for admin use) Supports all special day types: SN, CO, CM, CE, W
    public static WorkTimeTable createSpecialDayWithWorkTime(Integer userId, LocalDate date, String timeOffType, double workHours) {
        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Creating %s entry with work time for user %d on %s: %.2f hours",
                timeOffType, userId, date, workHours));

        WorkTimeTable entry = createNewEntry(userId, date);
        entry.setTimeOffType(timeOffType);

        // Calculate start and end times based on work hours
        LocalDateTime startTime = date.atTime(8, WorkCode.DEFAULT_ZERO); // Default 08:00 start
        int totalMinutes = (int) Math.round(workHours * 60);
        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);

        entry.setDayStartTime(startTime);
        entry.setDayEndTime(endTime);
        entry.setTemporaryStopCount(WorkCode.DEFAULT_ZERO);
        entry.setTotalTemporaryStopMinutes(WorkCode.DEFAULT_ZERO);

        // Apply special day calculation logic
        applySpecialDayWorkTimeCalculation(entry, timeOffType);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Created %s work entry for user %d on %s: input=%.2f hours, processed=%d full hours",
                timeOffType, userId, date, workHours, entry.getTotalOvertimeMinutes() / 60));

        return entry;
    }

    // Update existing entry with special day work time Preserves entry structure while updating work time
    public static WorkTimeTable updateSpecialDayWithWorkTime(WorkTimeTable entry, String timeOffType, double workHours) {
        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Updating %s entry with work time for user %d on %s: %.2f hours",
                timeOffType, entry.getUserId(), entry.getWorkDate(), workHours));

        // Update timeOffType and sync status
        entry.setTimeOffType(timeOffType);

        // Calculate new work times
        LocalDateTime startTime = entry.getWorkDate().atTime(8, WorkCode.DEFAULT_ZERO); // Default 08:00 start
        int totalMinutes = (int) Math.round(workHours * 60);
        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);

        entry.setDayStartTime(startTime);
        entry.setDayEndTime(endTime);

        // Clear any existing temporary stops (fresh calculation)
        entry.setTemporaryStopCount(WorkCode.DEFAULT_ZERO);
        entry.setTotalTemporaryStopMinutes(WorkCode.DEFAULT_ZERO);

        // Apply special day calculation logic
        applySpecialDayWorkTimeCalculation(entry, timeOffType);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Updated %s work entry for user %d on %s: input=%.2f hours, processed=%d full hours",
                timeOffType, entry.getUserId(), entry.getWorkDate(), workHours, entry.getTotalOvertimeMinutes() / 60));

        return entry;
    }

    // Apply special day work time calculation
    private static void applySpecialDayWorkTimeCalculation(WorkTimeTable entry, String timeOffType) {
        if (entry.getDayStartTime() == null || entry.getDayEndTime() == null) {
            LoggerUtil.info(WorktimeEntityBuilder.class, "Cannot calculate special day work time: missing start or end time");
            entry.setTotalWorkedMinutes(WorkCode.DEFAULT_ZERO);
            entry.setTotalOvertimeMinutes(WorkCode.DEFAULT_ZERO);
            entry.setLunchBreakDeducted(false);
            return;
        }

        // Calculate total elapsed minutes with proper rounding
        // FIXED: Use Math.round() instead of truncating - toMinutes() loses seconds!
        Duration elapsed = Duration.between(entry.getDayStartTime(), entry.getDayEndTime());
        long totalSeconds = elapsed.getSeconds();
        int totalElapsedMinutes = (int) Math.round(totalSeconds / 60.0);

        // Apply temp stop deduction (should be 0 for fresh admin entries)
        int tempStopMinutes = entry.getTotalTemporaryStopMinutes() != null ? entry.getTotalTemporaryStopMinutes() : WorkCode.DEFAULT_ZERO;
        int netWorkMinutes = Math.max(WorkCode.DEFAULT_ZERO, totalElapsedMinutes - tempStopMinutes);

        // Special day business rules: All work becomes overtime, full hours only
        entry.setTotalWorkedMinutes(WorkCode.DEFAULT_ZERO);  // No regular work on special days
        entry.setLunchBreakDeducted(false);  // No lunch break on special days

        // Convert to full hours only (discard partial hours)
        int fullHours = netWorkMinutes / 60;  // Floor division discards partial hours
        int overtimeMinutes = fullHours * 60;
        entry.setTotalOvertimeMinutes(overtimeMinutes);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("%s work calculation: elapsed=%d, tempStops=%d, netWork=%d → %d full hours (%d overtime minutes)",
                timeOffType, totalElapsedMinutes, tempStopMinutes, netWorkMinutes, fullHours, overtimeMinutes));
    }

    // Check if an entry has a special day timeOffType that requires special processing
    public static boolean hasSpecialDayTimeOffType(WorkTimeTable entry) {
        if (entry == null || entry.getTimeOffType() == null || entry.getTimeOffType().trim().isEmpty()) {
            return false;
        }

        String timeOffType = entry.getTimeOffType().trim().toUpperCase();
        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(timeOffType) ||
               WorkCode.TIME_OFF_CODE.equals(timeOffType) ||
               WorkCode.MEDICAL_LEAVE_CODE.equals(timeOffType) ||
               WorkCode.WEEKEND_CODE.equals(timeOffType) ||
               WorkCode.SPECIAL_EVENT_CODE.equals(timeOffType);  // CE can also have overtime work
        // CR (Recovery Leave) is NOT a special day - it's a time-off entry paid from overtime
        // CN (Unpaid Leave) is NOT a special day - it's a time-off entry (unpaid)
        // ZS (Short Day) is NOT a special day - it's a short work day filled from overtime
        // D (Delegation) is NOT a special day - it's a normal work day at different location
    }

    // Apply special day calculation to existing entry with start/end times
    public static void applySpecialDayTimeIntervalCalculation(WorkTimeTable entry) {
        if (!hasSpecialDayTimeOffType(entry)) {
            LoggerUtil.debug(WorktimeEntityBuilder.class, "Not a special day entry - skipping special calculation");
            return;
        }

        String timeOffType = entry.getTimeOffType();
        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Applying special day time interval calculation for %s entry", timeOffType));

        applySpecialDayWorkTimeCalculation(entry, timeOffType);
    }

    // ========================================================================
    // PRIVATE HELPER METHODS (UNCHANGED)
    // ========================================================================

    // Reset work fields to default values
    private static void resetWorkFields(WorkTimeTable entry) {
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTemporaryStopCount(WorkCode.DEFAULT_ZERO);
        entry.setTotalWorkedMinutes(WorkCode.DEFAULT_ZERO);
        entry.setTotalTemporaryStopMinutes(WorkCode.DEFAULT_ZERO);
        entry.setTotalOvertimeMinutes(WorkCode.DEFAULT_ZERO);
        entry.setLunchBreakDeducted(false);
    }

    // NEW METHOD - ADD THIS
    public static void resetEntryToEmpty(WorkTimeTable entry) {
        // Reset all work-related fields but preserve identity
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTimeOffType(null);
        entry.setTemporaryStopCount(WorkCode.DEFAULT_ZERO);
        entry.setTotalTemporaryStopMinutes(WorkCode.DEFAULT_ZERO);
        entry.setTotalWorkedMinutes(WorkCode.DEFAULT_ZERO);
        entry.setTotalOvertimeMinutes(WorkCode.DEFAULT_ZERO);
        entry.setLunchBreakDeducted(false);
    }

    // Create empty entry for admin reset operations
    public static WorkTimeTable createEmptyEntry(Integer userId, LocalDate date) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);

        // All other fields will be null/0/false by default
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTimeOffType(null);
        entry.setTemporaryStopCount(WorkCode.DEFAULT_ZERO);
        entry.setTotalTemporaryStopMinutes(WorkCode.DEFAULT_ZERO);
        entry.setTotalWorkedMinutes(WorkCode.DEFAULT_ZERO);
        entry.setTotalOvertimeMinutes(WorkCode.DEFAULT_ZERO);
        entry.setLunchBreakDeducted(false);

        return entry;
    }

    // Recalculate work time based on start/end times and entry type
    public static void recalculateWorkTime(WorkTimeTable entry, int userScheduleHours) {
        if (entry.getDayStartTime() == null || entry.getDayEndTime() == null) {
            resetWorkFields(entry);
            return;
        }

        // Calculate total elapsed time with proper rounding
        // FIXED: Use Math.round() instead of truncating - toMinutes() loses seconds!
        Duration elapsed = Duration.between(entry.getDayStartTime(), entry.getDayEndTime());
        long totalSeconds = elapsed.getSeconds();
        int totalElapsedMinutes = (int) Math.round(totalSeconds / 60.0);

        // FIXED: Handle ALL special day entries (SN, CO, CM, W) specially - not just SN!
        if (hasSpecialDayTimeOffType(entry)) {
            recalculateSpecialDayWorkTime(entry, totalElapsedMinutes);
        } else {
            // MODIFIED: Pass user schedule to regular work calculation
            recalculateRegularWorkTime(entry, totalElapsedMinutes, userScheduleHours);
        }
    }

    // Calculate work time for special day entries (SN, CO, CM, W)
    // FIXED: Renamed from recalculateSNWorkTime to handle ALL special days
    private static void recalculateSpecialDayWorkTime(WorkTimeTable entry, int totalElapsedMinutes) {
        // Account for temporary stops
        int tempStopMinutes = entry.getTotalTemporaryStopMinutes() != null ?
                entry.getTotalTemporaryStopMinutes() : WorkCode.DEFAULT_ZERO;
        int netWorkMinutes = Math.max(WorkCode.DEFAULT_ZERO, totalElapsedMinutes - tempStopMinutes);

        // Special day business rules: All work becomes overtime, full hours only
        entry.setTotalWorkedMinutes(WorkCode.DEFAULT_ZERO);  // No regular work on special days
        entry.setLunchBreakDeducted(false);  // No lunch break on special days

        // Convert to full hours only (discard partial hours)
        int fullHours = netWorkMinutes / 60;  // Floor division discards partial hours
        int overtimeMinutes = fullHours * 60;
        entry.setTotalOvertimeMinutes(overtimeMinutes);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Special day (%s) work calculation: elapsed=%d, tempStops=%d, netWork=%d → %d full hours (%d overtime minutes)",
                entry.getTimeOffType(), totalElapsedMinutes, tempStopMinutes, netWorkMinutes, fullHours, overtimeMinutes));
    }

    // Calculate work time for regular entries using proven CalculateWorkHoursUtil
    private static void recalculateRegularWorkTime(WorkTimeTable entry, int totalElapsedMinutes, int userScheduleHours) {
        // 1. Calculate net work minutes (elapsed - temp stops)
        int tempStopMinutes = entry.getTotalTemporaryStopMinutes() != null ?
                entry.getTotalTemporaryStopMinutes() : WorkCode.DEFAULT_ZERO;
        int netWorkMinutes = Math.max(WorkCode.DEFAULT_ZERO, totalElapsedMinutes - tempStopMinutes);

        // 2. USE CALCULATION SERVICE for consistency
        WorkTimeCalculationResultDTO result = calculationService.calculateWorkTime(netWorkMinutes, userScheduleHours);

        // 3. Apply results to entry
        entry.setTotalWorkedMinutes(netWorkMinutes);        // Regular time
        entry.setTotalOvertimeMinutes(result.getOvertimeMinutes());       // Overtime
        entry.setLunchBreakDeducted(result.isLunchDeducted());           // Lunch break

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Regular work calculation using CalculationService: elapsed=%d, tempStops=%d, netWork=%d, schedule=%dh → regular=%d, overtime=%d, lunch=%s",
                totalElapsedMinutes, tempStopMinutes, netWorkMinutes, userScheduleHours, result.getProcessedMinutes(), result.getOvertimeMinutes(), result.isLunchDeducted()));
    }
}
