package com.ctgraphdep.worktime.util;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CLEANED WorktimeEntityBuilder - Pure entity manipulation, NO VALIDATION
 * All validation is handled at controller level using TimeValidationService
 * This class assumes all input data is already validated
 */
public class WorktimeEntityBuilder {

    // ========================================================================
    // FACTORY METHODS - Entry Creation (CLEAN - NO VALIDATION)
    // ========================================================================

    /**
     * Create a new blank worktime entry with default values
     * ASSUMES: userId and date are already validated
     */
    public static WorkTimeTable createNewEntry(Integer userId, LocalDate date) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);
        entry.setAdminSync(SyncStatusMerge.USER_INPUT);

        // Initialize with safe defaults
        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Created new worktime entry for user %d on %s", userId, date));

        return entry;
    }

    /**
     * Create a time off entry (CO/CM/SN)
     * ASSUMES: userId, date, and timeOffType are already validated
     */
    public static WorkTimeTable createTimeOffEntry(Integer userId, LocalDate date, String timeOffType) {
        WorkTimeTable entry = createNewEntry(userId, date);
        entry.setTimeOffType(timeOffType.toUpperCase());

        // Time off entries have no work time
        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Created %s time off entry for user %d on %s", timeOffType, userId, date));

        return entry;
    }

    /**
     * Create a work entry with start and end times
     * ASSUMES: All parameters are already validated
     */
    public static WorkTimeTable createWorkEntry(Integer userId, LocalDate date,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        WorkTimeTable entry = createNewEntry(userId, date);
        entry.setDayStartTime(startTime);
        entry.setDayEndTime(endTime);
        entry.setTimeOffType(null); // Clear any time off

        // Calculate work time
        recalculateWorkTime(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Created work entry for user %d on %s: %s to %s",
                userId, date, startTime.toLocalTime(), endTime.toLocalTime()));

        return entry;
    }

    /**
     * Create admin blank entry (for removal)
     * ASSUMES: userId and date are already validated
     */
    public static WorkTimeTable createAdminBlankEntry(Integer userId, LocalDate date) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);
        entry.setAdminSync(SyncStatusMerge.ADMIN_BLANK);
        entry.setTimeOffType(null);

        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Created admin blank entry for user %d on %s", userId, date));

        return entry;
    }

    /**
     * Create admin work hours entry (admin sets specific hours)
     * ASSUMES: All parameters are already validated
     */
    public static WorkTimeTable createAdminWorkHoursEntry(Integer userId, LocalDate date, int hours) {
        WorkTimeTable entry = createNewEntry(userId, date);
        entry.setAdminSync(SyncStatusMerge.ADMIN_EDITED);

        // Calculate times based on hours
        LocalDateTime startTime = date.atTime(WorkCode.START_HOUR, 0);
        int totalMinutes = (hours * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION;
        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);

        entry.setDayStartTime(startTime);
        entry.setDayEndTime(endTime);
        entry.setTotalWorkedMinutes(totalMinutes);
        entry.setLunchBreakDeducted(hours > WorkCode.INTERVAL_HOURS_A);
        entry.setTimeOffType(null);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Created admin work hours entry for user %d on %s: %d hours", userId, date, hours));

        return entry;
    }

    // ========================================================================
    // UPDATE METHODS - Entry Modification (CLEAN - NO VALIDATION)
    // ========================================================================

    /**
     * Update start time with recalculation
     * ASSUMES: Entry and startTime are already validated
     */
    public static WorkTimeTable updateStartTime(WorkTimeTable entry, LocalDateTime startTime) {
        entry.setDayStartTime(startTime);
        entry.setAdminSync(SyncStatusMerge.USER_INPUT);

        // Recalculate if both times exist
        recalculateWorkTime(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Updated start time for user %d on %s to %s",
                entry.getUserId(), entry.getWorkDate(),
                startTime != null ? startTime.toLocalTime() : "null"));

        return entry;
    }

    /**
     * Update end time with recalculation
     * ASSUMES: Entry and endTime are already validated
     */
    public static WorkTimeTable updateEndTime(WorkTimeTable entry, LocalDateTime endTime) {
        entry.setDayEndTime(endTime);
        entry.setAdminSync(SyncStatusMerge.USER_INPUT);

        // Recalculate if both times exist
        recalculateWorkTime(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Updated end time for user %d on %s to %s",
                entry.getUserId(), entry.getWorkDate(),
                endTime != null ? endTime.toLocalTime() : "null"));

        return entry;
    }

    /**
     * Add time off to entry (clears work time)
     * ASSUMES: Entry and timeOffType are already validated
     */
    public static WorkTimeTable addTimeOff(WorkTimeTable entry, String timeOffType) {
        entry.setTimeOffType(timeOffType.toUpperCase());
        entry.setAdminSync(SyncStatusMerge.USER_INPUT);

        // Clear work time when setting time off
        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Added %s time off for user %d on %s",
                timeOffType, entry.getUserId(), entry.getWorkDate()));

        return entry;
    }

    /**
     * Remove time off from entry (preserves work time if exists)
     * ASSUMES: Entry is already validated
     */
    public static WorkTimeTable removeTimeOff(WorkTimeTable entry) {
        String oldTimeOffType = entry.getTimeOffType();
        entry.setTimeOffType(null);
        entry.setAdminSync(SyncStatusMerge.USER_INPUT);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Removed %s time off for user %d on %s",
                oldTimeOffType, entry.getUserId(), entry.getWorkDate()));

        return entry;
    }

    /**
     * Clear entry completely (for admin blank operations)
     * ASSUMES: Entry is already validated
     */
    public static WorkTimeTable clearEntry(WorkTimeTable entry) {
        entry.setTimeOffType(null);
        entry.setAdminSync(SyncStatusMerge.ADMIN_BLANK);
        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Cleared entry for user %d on %s", entry.getUserId(), entry.getWorkDate()));

        return entry;
    }

    /**
     * Transform work entry to time off (atomic operation)
     * ASSUMES: All parameters are already validated
     */
    public static WorkTimeTable transformWorkToTimeOff(WorkTimeTable entry, String timeOffType) {
        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Transforming work entry to %s for user %d on %s",
                timeOffType, entry.getUserId(), entry.getWorkDate()));

        return addTimeOff(entry, timeOffType);
    }

    /**
     * Transform time off entry to work entry
     * ASSUMES: All parameters are already validated
     */
    public static WorkTimeTable transformTimeOffToWork(WorkTimeTable entry,
                                                       LocalDateTime startTime, LocalDateTime endTime) {
        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Transforming %s time off to work entry for user %d on %s",
                entry.getTimeOffType(), entry.getUserId(), entry.getWorkDate()));

        entry.setTimeOffType(null);
        entry.setDayStartTime(startTime);
        entry.setDayEndTime(endTime);
        entry.setAdminSync(SyncStatusMerge.USER_INPUT);

        recalculateWorkTime(entry);

        return entry;
    }

    // ========================================================================
    // SN SPECIFIC METHODS (CLEAN - NO VALIDATION)
    // ========================================================================

    /**
     * Create SN entry with work time (for admin use)
     * ASSUMES: All parameters are already validated
     */
    public static WorkTimeTable createSNWithWorkTime(Integer userId, LocalDate date, double workHours) {
        WorkTimeTable entry = createNewEntry(userId, date);
        entry.setTimeOffType("SN");
        entry.setAdminSync(SyncStatusMerge.ADMIN_EDITED);

        // Calculate start and end times based on work hours
        LocalDateTime startTime = date.atTime(8, 0);
        int totalMinutes = (int) Math.round(workHours * 60);
        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);

        entry.setDayStartTime(startTime);
        entry.setDayEndTime(endTime);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);

        // Let recalculateWorkTime handle the SN-specific calculations
        recalculateWorkTime(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Created SN work entry for user %d on %s: input=%.2f hours, processed=%d full hours",
                userId, date, workHours, entry.getTotalOvertimeMinutes() / 60));

        return entry;
    }

    /**
     * Update SN entry with work time (preserves SN status, updates work hours)
     * ASSUMES: All parameters are already validated
     */
    public static WorkTimeTable updateSNWithWorkTime(WorkTimeTable entry, double workHours) {
        // Calculate new work times
        LocalDateTime startTime = entry.getWorkDate().atTime(8, 0);
        int totalMinutes = (int) Math.round(workHours * 60);
        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);

        entry.setDayStartTime(startTime);
        entry.setDayEndTime(endTime);
        entry.setAdminSync(SyncStatusMerge.ADMIN_EDITED);

        // Clear any existing temporary stops (fresh calculation)
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);

        // Recalculate using SN-specific logic
        recalculateWorkTime(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Updated SN work entry for user %d on %s: input=%.2f hours, processed=%d full hours",
                entry.getUserId(), entry.getWorkDate(), workHours, entry.getTotalOvertimeMinutes() / 60));

        return entry;
    }

    // ========================================================================
    // PRIVATE HELPER METHODS (UNCHANGED)
    // ========================================================================

    /**
     * Reset work fields to default values
     */
    private static void resetWorkFields(WorkTimeTable entry) {
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTemporaryStopCount(0);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setLunchBreakDeducted(false);
    }

    /**
     * Recalculate work time based on start/end times and entry type
     */
    private static void recalculateWorkTime(WorkTimeTable entry) {
        if (entry.getDayStartTime() == null || entry.getDayEndTime() == null) {
            resetWorkFields(entry);
            return;
        }

        // Calculate total elapsed time
        Duration elapsed = Duration.between(entry.getDayStartTime(), entry.getDayEndTime());
        int totalElapsedMinutes = (int) elapsed.toMinutes();

        // Handle SN entries (holiday work) specially
        if ("SN".equals(entry.getTimeOffType())) {
            recalculateSNWorkTime(entry, totalElapsedMinutes);
        } else {
            recalculateRegularWorkTime(entry, totalElapsedMinutes);
        }
    }

    /**
     * Calculate work time for SN (holiday) entries
     */
    private static void recalculateSNWorkTime(WorkTimeTable entry, int totalElapsedMinutes) {
        // Account for temporary stops
        int tempStopMinutes = entry.getTotalTemporaryStopMinutes() != null ?
                entry.getTotalTemporaryStopMinutes() : 0;
        int netWorkMinutes = Math.max(0, totalElapsedMinutes - tempStopMinutes);

        // SN business rules:
        entry.setTotalWorkedMinutes(0);  // No regular work on holidays
        entry.setLunchBreakDeducted(false);  // No lunch break on holidays

        // Convert to full hours only (discard partial hours)
        int fullHours = netWorkMinutes / 60;  // Floor division discards partial hours
        int overtimeMinutes = fullHours * 60;
        entry.setTotalOvertimeMinutes(overtimeMinutes);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Holiday work calculation: elapsed=%d, tempStops=%d, netWork=%d → %d full hours (%d overtime minutes)",
                totalElapsedMinutes, tempStopMinutes, netWorkMinutes, fullHours, overtimeMinutes));
    }

    /**
     * Calculate work time for regular entries
     */
    private static void recalculateRegularWorkTime(WorkTimeTable entry, int totalElapsedMinutes) {
        // Account for temporary stops
        int tempStopMinutes = entry.getTotalTemporaryStopMinutes() != null ? entry.getTotalTemporaryStopMinutes() : 0;
        int netWorkMinutes = Math.max(0, totalElapsedMinutes - tempStopMinutes);

        // Lunch break logic
        boolean lunchBreakDeducted = netWorkMinutes > (WorkCode.INTERVAL_HOURS_A * 60);
        if (lunchBreakDeducted) {
            netWorkMinutes -= WorkCode.HALF_HOUR_DURATION; // Deduct 30 minutes
        }

        // Regular vs overtime calculation
        int regularMinutes = Math.min(netWorkMinutes, WorkCode.INTERVAL_HOURS_C);
        int overtimeMinutes = Math.max(0, netWorkMinutes - WorkCode.INTERVAL_HOURS_C);

        entry.setTotalWorkedMinutes(regularMinutes);
        entry.setTotalOvertimeMinutes(overtimeMinutes);
        entry.setLunchBreakDeducted(lunchBreakDeducted);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Regular work calculation: elapsed=%d, tempStops=%d, netWork=%d, regular=%d, overtime=%d, lunch=%s",
                totalElapsedMinutes, tempStopMinutes, netWorkMinutes,
                regularMinutes, overtimeMinutes, lunchBreakDeducted));
    }
}
