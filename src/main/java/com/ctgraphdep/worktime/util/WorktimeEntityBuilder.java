package com.ctgraphdep.worktime.util;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ENHANCED Builder class for creating and updating WorkTimeTable entities with validation.
 * Centralizes all worktime entry manipulation logic to eliminate duplication.
 * NEW ENHANCEMENTS:
 * - Integration with TimeValidationService for standardized validation
 * - Hybrid validation approach: EntityBuilder rules + TimeValidationService
 * - Improved error messages and logging
 * - Backward compatibility maintained
 */
public class WorktimeEntityBuilder {

    static {
        LoggerUtil.initialize(WorktimeEntityBuilder.class, null);
    }

    // ========================================================================
    // FACTORY METHODS - Entry Creation (EXISTING - UNCHANGED)
    // ========================================================================

    /**
     * Create a new blank worktime entry with default values
     */
    public static WorkTimeTable createNewEntry(Integer userId, LocalDate date) {
        validateBasicParameters(userId, date);

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
     */
    public static WorkTimeTable createTimeOffEntry(Integer userId, LocalDate date, String timeOffType) {
        validateBasicParameters(userId, date);
        ValidationRules.validateTimeOffType(timeOffType);

        WorkTimeTable entry = createNewEntry(userId, date);
        entry.setTimeOffType(timeOffType.toUpperCase());

        // Time off entries have no work time
        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format("Created %s time off entry for user %d on %s", timeOffType, userId, date));

        return entry;
    }

    /**
     * Create a work entry with start and end times
     */
    public static WorkTimeTable createWorkEntry(Integer userId, LocalDate date,
                                                LocalDateTime startTime, LocalDateTime endTime) {
        validateBasicParameters(userId, date);
        ValidationRules.validateWorkTimes(startTime, endTime);

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
     */
    public static WorkTimeTable createAdminBlankEntry(Integer userId, LocalDate date) {
        validateBasicParameters(userId, date);

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
     */
    public static WorkTimeTable createAdminWorkHoursEntry(Integer userId, LocalDate date, int hours) {
        validateBasicParameters(userId, date);
        ValidationRules.validateWorkHours(hours);

        WorkTimeTable entry = createNewEntry(userId, date);
        entry.setAdminSync(SyncStatusMerge.ADMIN_EDITED);

        // Calculate times based on hours (admin pattern from existing code)
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
    // UPDATE METHODS - Entry Modification (EXISTING - UNCHANGED)
    // ========================================================================

    /**
     * Update start time with validation and recalculation
     */
    public static WorkTimeTable updateStartTime(WorkTimeTable entry, LocalDateTime startTime) {
        validateEntryForUpdate(entry);
        ValidationRules.validateTimeOffConflict(entry, "Cannot update start time when time off is set");

        if (startTime != null && entry.getDayEndTime() != null && !startTime.isBefore(entry.getDayEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }

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
     * Update end time with validation and recalculation
     */
    public static WorkTimeTable updateEndTime(WorkTimeTable entry, LocalDateTime endTime) {
        validateEntryForUpdate(entry);
        ValidationRules.validateTimeOffConflict(entry, "Cannot update end time when time off is set");

        if (endTime != null && entry.getDayStartTime() != null && !endTime.isAfter(entry.getDayStartTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }

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
     */
    public static WorkTimeTable addTimeOff(WorkTimeTable entry, String timeOffType) {
        validateEntryForUpdate(entry);
        ValidationRules.validateTimeOffType(timeOffType);

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
     */
    public static WorkTimeTable removeTimeOff(WorkTimeTable entry) {
        validateEntryForUpdate(entry);

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
     */
    public static WorkTimeTable clearEntry(WorkTimeTable entry) {
        validateEntryForUpdate(entry);

        entry.setTimeOffType(null);
        entry.setAdminSync(SyncStatusMerge.ADMIN_BLANK);
        resetWorkFields(entry);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Cleared entry for user %d on %s", entry.getUserId(), entry.getWorkDate()));

        return entry;
    }

    /**
     * Transform work entry to time off (atomic operation)
     */
    public static WorkTimeTable transformWorkToTimeOff(WorkTimeTable entry, String timeOffType) {
        validateEntryForUpdate(entry);
        ValidationRules.validateTimeOffType(timeOffType);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Transforming work entry to %s for user %d on %s",
                timeOffType, entry.getUserId(), entry.getWorkDate()));

        return addTimeOff(entry, timeOffType);
    }

    /**
     * Transform time off entry to work entry
     */
    public static WorkTimeTable transformTimeOffToWork(WorkTimeTable entry,
                                                       LocalDateTime startTime, LocalDateTime endTime) {
        validateEntryForUpdate(entry);
        ValidationRules.validateWorkTimes(startTime, endTime);

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
    // PRIVATE HELPER METHODS (EXISTING - UNCHANGED)
    // ========================================================================

    /**
     * Reset all work-related fields to default values
     */
    private static void resetWorkFields(WorkTimeTable entry) {
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);
    }

    /**
     * Recalculate work time based on start and end times, accounting for temporary stops
     */
    private static void recalculateWorkTime(WorkTimeTable entry) {
        if (entry.getDayStartTime() == null || entry.getDayEndTime() == null) {
            entry.setTotalWorkedMinutes(0);
            entry.setLunchBreakDeducted(false);
            return;
        }

        // Calculate total elapsed time
        int totalElapsedMinutes = (int) Duration.between(entry.getDayStartTime(), entry.getDayEndTime()).toMinutes();
        if (totalElapsedMinutes < 0) {
            totalElapsedMinutes = 0;
        }

        // ✅ CORRECT: Only subtract temporary stops to get net work time
        int tempStopMinutes = entry.getTotalTemporaryStopMinutes() != null ?
                entry.getTotalTemporaryStopMinutes() : 0;
        int netWorkMinutes = Math.max(0, totalElapsedMinutes - tempStopMinutes);

        // Store net work time (lunch break is already accounted for in elapsed time)
        entry.setTotalWorkedMinutes(netWorkMinutes);

        // Flag that lunch break should be considered in overtime calculations
        boolean lunchBreak = netWorkMinutes >= (4 * 60); // 4 hours threshold
        entry.setLunchBreakDeducted(lunchBreak);

        LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                "Recalculated work time: elapsed=%d, tempStops=%d, netWork=%d minutes",
                totalElapsedMinutes, tempStopMinutes, netWorkMinutes));
    }

    /**
     * Validate basic parameters for entry creation
     */
    private static void validateBasicParameters(Integer userId, LocalDate date) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
    }

    /**
     * Validate entry for update operations
     */
    private static void validateEntryForUpdate(WorkTimeTable entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Entry cannot be null");
        }
        if (entry.getUserId() == null) {
            throw new IllegalArgumentException("Entry must have user ID");
        }
        if (entry.getWorkDate() == null) {
            throw new IllegalArgumentException("Entry must have work date");
        }
    }

    // ========================================================================
    // ENHANCED VALIDATION RULES - With TimeValidationService Integration
    // ========================================================================

    public static class ValidationRules {

        // Optional TimeValidationService for enhanced validation
        private static TimeValidationService timeValidationService;

        /**
         * Set TimeValidationService for enhanced validation capabilities
         * This allows EntityBuilder to use standardized validation when available
         */
        public static void setTimeValidationService(TimeValidationService service) {
            timeValidationService = service;
            LoggerUtil.debug(WorktimeEntityBuilder.class,
                    "TimeValidationService integration " + (service != null ? "enabled" : "disabled"));
        }

        /**
         * Validate time off type
         */
        public static void validateTimeOffType(String timeOffType) {
            if (timeOffType == null || timeOffType.trim().isEmpty()) {
                throw new IllegalArgumentException("Time off type cannot be null or empty");
            }

            String type = timeOffType.trim().toUpperCase();
            if (!type.matches("^(CO|CM|SN)$")) {
                throw new IllegalArgumentException("Invalid time off type: " + timeOffType +
                        ". Valid types: CO (vacation), CM (medical), SN (national holiday)");
            }
        }

        /**
         * Validate work times
         */
        public static void validateWorkTimes(LocalDateTime startTime, LocalDateTime endTime) {
            if (startTime != null && endTime != null) {
                if (!endTime.isAfter(startTime)) {
                    throw new IllegalArgumentException("End time must be after start time");
                }

                // Check for reasonable work duration (max 24 hours)
                Duration duration = Duration.between(startTime, endTime);
                if (duration.toHours() > 24) {
                    throw new IllegalArgumentException("Work duration cannot exceed 24 hours");
                }
            }
        }

        /**
         * Validate work hours for admin entries
         */
        public static void validateWorkHours(int hours) {
            if (hours < 1 || hours > 24) {
                throw new IllegalArgumentException("Work hours must be between 1 and 24");
            }
        }

        /**
         * Validate time off conflict
         */
        public static void validateTimeOffConflict(WorkTimeTable entry, String message) {
            if (entry.getTimeOffType() != null && !entry.getTimeOffType().trim().isEmpty()) {
                throw new IllegalArgumentException(message + ". Remove time off first.");
            }
        }

        /**
         * ENHANCED: Validate user can edit date with optional TimeValidationService integration
         */
        public static void validateUserCanEditDate(LocalDate date, String reason) {
            // Basic validation (always performed)
            LocalDate today = LocalDate.now();

            if (date.equals(today)) {
                throw new IllegalArgumentException("Cannot edit current day");
            }

            if (date.isAfter(today)) {
                throw new IllegalArgumentException("Cannot edit future dates");
            }

            // Additional custom validation
            if (reason != null && !reason.trim().isEmpty()) {
                throw new IllegalArgumentException(reason);
            }

            // Enhanced validation using TimeValidationService if available
            if (timeValidationService != null) {
                try {
                    // Use TimeValidationService for additional date validation
                    var validateCommand = timeValidationService.getValidationFactory()
                            .createValidatePeriodCommand(date.getYear(), date.getMonthValue(), 24);
                    timeValidationService.execute(validateCommand);

                    LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                            "TimeValidationService validated date %s successfully", date));
                } catch (Exception e) {
                    LoggerUtil.warn(WorktimeEntityBuilder.class, String.format(
                            "TimeValidationService validation failed for date %s: %s", date, e.getMessage()));
                    throw new IllegalArgumentException("Date validation failed: " + e.getMessage());
                }
            }
        }

        /**
         * ENHANCED: Validate time off can be added to date with TimeValidationService integration
         */
        public static void validateTimeOffDate(LocalDate date, String timeOffType) {
            // Basic validation first
            validateUserCanEditDate(date, null);

            // Check for weekend (except admin SN)
            if (date.getDayOfWeek().getValue() >= 6) {
                if (!"SN".equals(timeOffType)) {
                    throw new IllegalArgumentException("Cannot add time off on weekends (except national holidays)");
                }
            }

            // Enhanced validation using TimeValidationService if available and for holidays
            if (timeValidationService != null && "SN".equals(timeOffType)) {
                try {
                    // Use TimeValidationService for holiday-specific validation
                    var holidayCommand = timeValidationService.getValidationFactory()
                            .createValidateHolidayDateCommand(date);
                    timeValidationService.execute(holidayCommand);

                    LoggerUtil.debug(WorktimeEntityBuilder.class, String.format(
                            "TimeValidationService validated holiday date %s successfully", date));
                } catch (Exception e) {
                    LoggerUtil.warn(WorktimeEntityBuilder.class, String.format(
                            "TimeValidationService holiday validation failed for date %s: %s", date, e.getMessage()));
                    throw new IllegalArgumentException("Holiday date validation failed: " + e.getMessage());
                }
            }
        }

        /**
         * Validate admin permissions for operation
         */
        public static void validateAdminOperation(String timeOffType, String operation) {
            if ("SN".equals(timeOffType) && !"admin".equals(operation)) {
                throw new IllegalArgumentException("Only admin can edit national holidays");
            }
        }
    }
}