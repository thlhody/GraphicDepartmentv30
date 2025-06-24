package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CORRECTED Transform Time Off to Work Command - Proper business logic implementation
 * CORRECTED Key Behaviors:
 * - ADMIN: Can transform any time off entry to work with default schedule for any date
 * - USER: Can transform time off to work for PAST dates only (1 month limit) with manual start/end times
 * - Users log completed work (convert pastime off to actual work done)
 * - Admin uses default 8h schedule calculation
 * - Holiday balance restoration for CO entries
 * - Manual start/end time input for precise user control
 */
public class TransformTimeOffToWorkCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Integer defaultScheduleHours; // Optional: use default schedule if start/end not provided

    public TransformTimeOffToWorkCommand(WorktimeOperationContext context, String username,
                                         Integer userId, LocalDate date,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.defaultScheduleHours = null; // Will use 8h default if times not provided
    }

    @Override
    protected void validate() {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        LoggerUtil.info(this.getClass(), String.format("Validating transform time off to work: %s on %s", username, date));

        // Validate user permissions
        context.validateUserPermissions(username, "transform time off to work");

        boolean isCurrentUserAdmin = context.isCurrentUserAdmin();
        String currentUsername = context.getCurrentUsername();

        // ADMIN vs USER specific validation
        if (isCurrentUserAdmin && !currentUsername.equals(username)) {
            // Admin transforming time off for another user
            validateAdminTransformTimeOff();
        } else {
            // User transforming their own time off
            validateUserTransformTimeOff();
        }

        LoggerUtil.debug(this.getClass(), "Transform time off to work validation completed successfully");
    }

    /**
     * Validate admin transforming time off (can transform any type, any date, default schedule)
     */
    private void validateAdminTransformTimeOff() {
        LoggerUtil.debug(this.getClass(), "Validating admin transform time off to work operation");

        // Admin can transform any time off type for any date
        // Less strict date validation for admin operations
        try {
            context.validateHolidayDate(date);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Admin date validation warning for %s: %s", date, e.getMessage()));
            // Don't throw exception for admin operations
        }
    }

    /**
     * CORRECTED: Validate user transforming time off (PAST dates only, 1 month limit, manual times required)
     */
    private void validateUserTransformTimeOff() {
        LoggerUtil.debug(this.getClass(), "Validating user transform time off to work operation");

        LocalDate today = LocalDate.now();

        // CORRECTED: Users can only transform time off to work for PAST dates (logging completed work)
        if (!date.isBefore(today)) {
            throw new IllegalArgumentException(String.format("Users can only convert time off to work for past dates (logging completed work). Date %s is not allowed.", date));
        }

        // CORRECTED: 1 month limit for work time editing
        LocalDate oneMonthAgo = today.minusMonths(1);
        if (date.isBefore(oneMonthAgo)) {
            throw new IllegalArgumentException(String.format("Users can only edit work time for the past month. Date %s is too old. " + "Limit: %s", date, oneMonthAgo));
        }

        // CORRECTED: Manual start/end time is required for user operations (logging actual work done)
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Users must provide both start time and end time when converting time off to work (to log the actual work hours completed)");
        }

        // Additional date validation
        context.validateDateEditable(date, null);

        LoggerUtil.debug(this.getClass(), String.format("User converting past time off to work log for %s with times %s-%s", date, startTime.toLocalTime(), endTime.toLocalTime()));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Executing transform time off to work command for %s on %s", username, date));

        try {
            int year = date.getYear();
            int month = date.getMonthValue();

            // Determine operation context (admin vs user)
            boolean isAdminOperation = context.isCurrentUserAdmin() && !context.getCurrentUsername().equals(username);

            // Load appropriate entries
            List<WorkTimeTable> entries;
            if (isAdminOperation) {
                entries = context.loadAdminWorktime(year, month);
            } else {
                entries = context.loadUserWorktime(username, year, month);
            }

            // Find existing time off entry
            Optional<WorkTimeTable> existingEntry = context.findEntryByDate(entries, userId, date);
            if (existingEntry.isEmpty()) {
                String message = String.format("No time off entry found to transform for %s on %s", username, date);
                LoggerUtil.warn(this.getClass(), message);
                return OperationResult.failure(message, getOperationType());
            }

            WorkTimeTable entry = existingEntry.get();

            // Check if entry has time off to transform
            if (entry.getTimeOffType() == null || entry.getTimeOffType().trim().isEmpty()) {
                String message = String.format("Entry for %s on %s has no time off to transform", username, date);
                LoggerUtil.warn(this.getClass(), message);
                return OperationResult.failure(message, getOperationType());
            }

            String oldTimeOffType = entry.getTimeOffType();

            // Additional validation for user operations
            if (!isAdminOperation && WorkCode.NATIONAL_HOLIDAY_CODE.equals(oldTimeOffType)) {
                return OperationResult.permissionFailure(getOperationType(), "Users cannot transform national holidays (SN). Only admin can modify SN entries.");
            }

            LoggerUtil.info(this.getClass(), String.format("Transforming %s time off to work entry for %s on %s", oldTimeOffType, username, date));

            // Determine work times
            LocalDateTime workStartTime;
            LocalDateTime workEndTime;

            if (startTime != null && endTime != null) {
                // Use provided times (user logging actual work done)
                workStartTime = startTime;
                workEndTime = endTime;
                LoggerUtil.debug(this.getClass(), String.format("Using provided work times: %s to %s", workStartTime.toLocalTime(), workEndTime.toLocalTime()));
            } else {
                // Use default schedule (admin operation)
                int scheduleHours = defaultScheduleHours != null ? defaultScheduleHours : 8;
                workStartTime = date.atTime(WorkCode.START_HOUR, 0); // Default start time
                int totalMinutes = (scheduleHours * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION;
                workEndTime = workStartTime.plusMinutes(totalMinutes);

                LoggerUtil.debug(this.getClass(), String.format("Using default %dh schedule: %s to %s", scheduleHours, workStartTime.toLocalTime(), workEndTime.toLocalTime()));
            }

            // Track holiday balance for restoration
            Integer oldBalance = null;
            boolean balanceUpdated = false;

            if (WorkCode.TIME_OFF_CODE.equals(oldTimeOffType)) {
                oldBalance = context.getCurrentHolidayBalance();
            }

            // Transform using entity builder
            WorkTimeTable transformedEntry = WorktimeEntityBuilder.transformTimeOffToWork(entry, workStartTime, workEndTime);

            // Update sync status based on operation type
            if (isAdminOperation) {
                transformedEntry.setAdminSync(com.ctgraphdep.enums.SyncStatusMerge.ADMIN_EDITED);
            } else {
                transformedEntry.setAdminSync(com.ctgraphdep.enums.SyncStatusMerge.USER_INPUT);
            }

            // Calculate additional work time properties
            calculateWorkTimeProperties(transformedEntry);

            context.addOrReplaceEntry(entries, transformedEntry);

            // Save entries back
            if (isAdminOperation) {
                context.saveAdminWorktime(entries, year, month);
            } else {
                context.saveUserWorktime(username, entries, year, month);
            }

// CORRECTED: Handle holiday balance restoration for CO (restoring vacation days when converting to work)
            if (WorkCode.TIME_OFF_CODE.equals(oldTimeOffType)) {
                // Only restore balance for user operations or admin operations on current user
                if (!isAdminOperation || context.getCurrentUsername().equals(username)) {

                    // FIXED: Use context method to check for national holiday
                    if (context.shouldProcessVacationDay(date, "convert vacation to work")) {
                        balanceUpdated = context.updateHolidayBalance(1); // Restore 1 day (worked instead of vacation)
                    }
                }
            }

// Update success message


// REMOVE the local isExistingNationalHoliday method

            // Invalidate cache
            context.invalidateTimeOffCache(username, year);
            context.refreshTimeOffTracker(username, userId, year);

            // Create success message
            String timeRange = String.format("%s to %s", workStartTime.toLocalTime(), workEndTime.toLocalTime());
            String message = String.format("Successfully transformed %s time off to work entry (%s) for %s on %s", oldTimeOffType, timeRange, username, date);

            if (balanceUpdated) {
                message += String.format(". Holiday balance restored: %d → %d (worked instead of vacation)", oldBalance, context.getCurrentHolidayBalance());
            } else if (WorkCode.TIME_OFF_CODE.equals(oldTimeOffType) && context.isExistingNationalHoliday(date)) {
                message += " (no vacation day restored - national holiday)";
            }

            // Create side effects tracking
            OperationResult.OperationSideEffects.Builder sideEffectsBuilder = OperationResult.OperationSideEffects.builder()
                            .fileUpdated(isAdminOperation ? String.format("admin/%d/%d", year, month) : context.createFilePathId(username, year, month))
                            .cacheInvalidated(context.createCacheKey(username, year));

            if (balanceUpdated && oldBalance != null) {
                sideEffectsBuilder.holidayBalanceChanged(oldBalance, context.getCurrentHolidayBalance());
            }

            LoggerUtil.info(this.getClass(), message);

            return OperationResult.successWithSideEffects(message, getOperationType(), transformedEntry, sideEffectsBuilder.build());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error transforming time off to work for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to transform time off to work: " + e.getMessage(), getOperationType());
        }
    }

    /**
     * Calculate additional work time properties based on start and end times
     */
    private void calculateWorkTimeProperties(WorkTimeTable entry) {
        if (entry.getDayStartTime() == null || entry.getDayEndTime() == null) {
            return;
        }

        // Calculate total worked minutes
        int totalMinutes = (int) Duration.between(entry.getDayStartTime(), entry.getDayEndTime()).toMinutes();
        entry.setTotalWorkedMinutes(Math.max(0, totalMinutes));

        // Determine lunch break (more than 6 hours)
        boolean lunchBreak = totalMinutes > (6 * 60);
        entry.setLunchBreakDeducted(lunchBreak);

        // Initialize other work-related fields
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);

        LoggerUtil.debug(this.getClass(), String.format("Calculated work time properties: %d minutes, lunch break: %s", totalMinutes, lunchBreak));
    }

    @Override
    protected String getCommandName() {
        if (startTime != null && endTime != null) {
            return String.format("TransformTimeOffToWork[%s, %s, %s-%s]", username, date, startTime.toLocalTime(), endTime.toLocalTime());
        } else {
            return String.format("TransformTimeOffToWork[%s, %s, %dh schedule]", username, date, defaultScheduleHours != null ? defaultScheduleHours : 8);
        }
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.TRANSFORM_TIME_OFF_TO_WORK;
    }
}