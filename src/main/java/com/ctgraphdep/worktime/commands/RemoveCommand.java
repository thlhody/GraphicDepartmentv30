package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.StatusAssignmentEngine;
import com.ctgraphdep.worktime.util.StatusAssignmentResult;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Enhanced RemoveCommand - Unified removal system that handles:
 * 1. Complete entry removal (admin delete button)
 * 2. Time-off field removal (CO/CM removal for users)
 * 3. Individual field removal (future extensibility)
 * MERGED FUNCTIONALITY:
 * - Original RemoveCommand: Complete entry removal with admin/user rules
 * - Original : Time-off specific removal with holiday balance restoration
 * REMOVAL TYPES:
 * - "all": Complete entry removal (admin delete button)
 * - "timeoff": Remove only time-off field, preserve work data
 * - "starttime"/"endtime"/"tempstop": Remove specific fields (future use)
 * USER RULES FOR TIME-OFF REMOVAL:
 * - Can remove CO/CM from previous month + current month + future months
 * - Cannot remove SN (admin only)
 * - Cannot remove from dates older than previous month
 * - CO removal restores 1 vacation day to holiday balance
 * ADMIN RULES:
 * - Can remove any time-off type from any date
 * - Can completely delete entries
 * - Holiday balance not affected by admin operations
 */
public class RemoveCommand extends WorktimeOperationCommand<WorkTimeTable> {

    private final String username;
    private final LocalDate date;
    private final boolean isAdminDelete;
    private final String removalType; // "all", "timeoff", "starttime", etc.
    private final WorktimeDataAccessor accessor;

    // Private constructor - use factory methods
    private RemoveCommand(WorktimeOperationContext context, WorktimeDataAccessor accessor,
                          String username, LocalDate date, boolean isAdminDelete, String removalType) {
        super(context);
        this.accessor = accessor;
        this.username = username;
        this.date = date;
        this.isAdminDelete = isAdminDelete;
        this.removalType = removalType;
    }

    // ========================================================================
    // FACTORY METHODS FOR DIFFERENT REMOVAL SCENARIOS
    // ========================================================================

    // Create command for complete entry removal (admin delete button)
    public static RemoveCommand forCompleteRemoval(WorktimeOperationContext context, WorktimeDataAccessor accessor, String username, LocalDate date, boolean isAdminDelete) {
        return new RemoveCommand(context, accessor, username, date, isAdminDelete, "all");
    }

    // Create command for time-off field removal (user CO/CM removal)
    public static RemoveCommand forTimeOffRemoval(WorktimeOperationContext context, WorktimeDataAccessor accessor, String username, LocalDate date) {
        return new RemoveCommand(context, accessor, username, date, false, "timeoff");
    }

    // Create command for specific field removal (future extensibility)
    public static RemoveCommand forFieldRemoval(WorktimeOperationContext context, WorktimeDataAccessor accessor, String username, LocalDate date, String fieldName) {
        return new RemoveCommand(context, accessor, username, date, false, fieldName);
    }

    // ========================================================================
    // VALIDATION
    // ========================================================================

    @Override
    protected void validate() {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (context.getCurrentUser() == null) {
            throw new IllegalArgumentException("Current user context is required");
        }
        if (removalType == null || removalType.trim().isEmpty()) {
            throw new IllegalArgumentException("Removal type cannot be null or empty");
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating removal: user=%s, date=%s, type=%s, adminDelete=%s",
                username, date, removalType, isAdminDelete));

        // Validate permissions
        context.validateUserPermissions(username, "remove " + removalType);

        // Type-specific validation
        if ("timeoff".equals(removalType)) {
            validateTimeOffRemoval();
        }
    }

    // Validate time-off removal specific rules
    private void validateTimeOffRemoval() {
        boolean isCurrentUserAdmin = context.isCurrentUserAdmin();
        String currentUsername = context.getCurrentUsername();

        if (isCurrentUserAdmin && !currentUsername.equals(username)) {
            // Admin removing time off for another user - more permissive
            validateAdminTimeOffRemoval();
        } else {
            // User removing their own time off - strict rules
            validateUserTimeOffRemoval();
        }
    }

    // Admin time-off removal validation (can remove any type, any date)
    private void validateAdminTimeOffRemoval() {
        LoggerUtil.debug(this.getClass(), "Validating admin time-off removal operation");

        try {
            context.validateHolidayDate(date);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Admin date validation warning for %s: %s", date, e.getMessage()));
            // Don't throw exception for admin operations - more lenient
        }
    }

    // User time-off removal validation (CO/CM only, date range restricted)
    private void validateUserTimeOffRemoval() {
        LoggerUtil.debug(this.getClass(), "Validating user time-off removal operation");

        // Date range validation: previous month + current + future
        if (!isDateWithinUserRemovalRange(date)) {
            throw new IllegalArgumentException(String.format(
                    "Users can only remove time off from previous, current, or future months. " +
                            "Date %s is too old to modify.", date));
        }

        LoggerUtil.debug(this.getClass(), String.format("User time-off removal date %s is within allowed range", date));
    }

    // Check if date is within user removal range (previous month + current + future)
    private boolean isDateWithinUserRemovalRange(LocalDate date) {
        LocalDate today = LocalDate.now();
        LocalDate earliestAllowed = today.withDayOfMonth(1).minusMonths(1); // First day of previous month
        return !date.isBefore(earliestAllowed);
    }

    // ========================================================================
    // EXECUTION
    // ========================================================================

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing RemoveCommand: user=%s, date=%s, type=%s, adminDelete=%s",
                username, date, removalType, isAdminDelete));

        try {
            // Load existing entries
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);

            if (entries == null) {
                entries = new java.util.ArrayList<>();
            }

            // Find the entry to remove/modify
            Optional<WorkTimeTable> existingEntryOpt = findEntryByDate(entries,
                    context.getCurrentUser().getUserId(), date);

            if (existingEntryOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No entry found for %s on %s - nothing to remove", username, date));
                return OperationResult.failure("No entry found to remove", getOperationType());
            }

            WorkTimeTable entry = existingEntryOpt.get();
            String userRole = context.getCurrentUser().getRole();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Found entry to process: timeOffType=%s, removalType=%s",
                    entry.getTimeOffType(), removalType));

            // Route to appropriate removal handler
            OperationResult result = switch (removalType) {
                case "all" -> handleCompleteEntryRemoval(entry, userRole);
                case "timeoff" -> handleTimeOffRemoval(entry, userRole);
                case "starttime", "endtime", "tempstop" -> handleFieldRemoval(entry, userRole, removalType);
                default -> OperationResult.failure("Unknown removal type: " + removalType, getOperationType());
            };

            if (!result.isSuccess()) {
                return result;
            }

            // Update entry in list
            replaceEntry(entries, entry);

            // Save using accessor
            accessor.writeWorktimeWithStatus(username, entries, year, month, userRole);

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(createFilePathId(username, year, month))
                    .build();

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully executed removal for %s on %s: %s", username, date, result.getMessage()));

            return OperationResult.successWithSideEffects(result.getMessage(), getOperationType(), entry, sideEffects);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error executing removal for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to remove: " + e.getMessage(), getOperationType());
        }
    }

    // ========================================================================
    // REMOVAL HANDLERS
    // ========================================================================

    // Handle complete entry removal (admin delete button)
    private OperationResult handleCompleteEntryRemoval(WorkTimeTable entry, String userRole) {
        LoggerUtil.debug(this.getClass(), "Handling complete entry removal");

        // Reset all fields except date and userId
        resetAllFieldsExceptDate(entry);

        // Assign delete status
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(entry, userRole, isAdminDelete ? "ADMIN_DELETE" : "DELETE_ENTRY");

        if (!statusResult.isSuccess()) {
            return OperationResult.failure("Cannot delete entry: " + statusResult.getMessage(), getOperationType());
        }

        LoggerUtil.info(this.getClass(), String.format("Complete entry removal: Status assigned %s → %s",
                statusResult.getOriginalStatus(), statusResult.getNewStatus()));

        return OperationResult.success("Entry deleted successfully", getOperationType(), entry);
    }

    // Handle time-off field removal with holiday balance restoration
    private OperationResult handleTimeOffRemoval(WorkTimeTable entry, String userRole) {
        LoggerUtil.debug(this.getClass(), "Handling time-off field removal");

        String originalTimeOffType = entry.getTimeOffType();

        if (originalTimeOffType == null || originalTimeOffType.trim().isEmpty()) {
            return OperationResult.failure("No time off found to remove", getOperationType());
        }

        // Validate time-off type for user operations
        if (!SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            if (WorkCode.NATIONAL_HOLIDAY_CODE.equals(originalTimeOffType)) {
                return OperationResult.failure("Users cannot remove national holidays (SN). Only admin can modify SN entries.", getOperationType());
            }
            if (!WorkCode.TIME_OFF_CODE.equals(originalTimeOffType) && !WorkCode.MEDICAL_LEAVE_CODE.equals(originalTimeOffType)) {
                return OperationResult.failure("Users can only remove vacation (CO) or medical (CM) time off", getOperationType());
            }
        }

        LoggerUtil.info(this.getClass(), String.format("Removing time-off type '%s' from entry", originalTimeOffType));

        // Remove time-off field but preserve work data
        entry.setTimeOffType(null);

        // Assign status
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                entry, userRole, "REMOVE_TIME_OFF");

        if (!statusResult.isSuccess()) {
            return OperationResult.failure("Cannot remove time off: " + statusResult.getMessage(), getOperationType());
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Time-off removal: Status assigned %s → %s",
                statusResult.getOriginalStatus(), statusResult.getNewStatus()));

        // Handle holiday balance restoration for CO
        HolidayBalanceResult balanceResult = handleHolidayBalanceRestoration(originalTimeOffType, userRole);

        // Create success message
        String message = createTimeOffRemovalMessage(originalTimeOffType, balanceResult);

        // Update tracker for user operations
        if (!SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(userRole) || context.getCurrentUsername().equals(username)) {
            try {
                context.removeTimeOffFromTracker(username, context.getCurrentUser().getUserId(), date, date.getYear());
                LoggerUtil.debug(this.getClass(), String.format("Removed from time off tracker for user %s on %s", username, date));
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to remove from tracker for %s on %s: %s",
                        username, date, e.getMessage()));
                // Don't fail entire operation for tracker sync issue
            }
        }

        // Invalidate cache
        context.invalidateTimeOffCache(username, date.getYear());
        context.refreshTimeOffTracker(username, context.getCurrentUser().getUserId(), date.getYear());

        return OperationResult.success(message, getOperationType(), entry);
    }

    // Handle individual field removal (future extensibility)
    private OperationResult handleFieldRemoval(WorkTimeTable entry, String userRole, String fieldName) {
        LoggerUtil.debug(this.getClass(), String.format("Handling individual field removal: %s", fieldName));

        // Reset specific field
        switch (fieldName) {
            case "starttime" -> entry.setDayStartTime(null);
            case "endtime" -> entry.setDayEndTime(null);
            case "tempstop" -> {
                entry.setTemporaryStopCount(0);
                entry.setTotalTemporaryStopMinutes(0);
            }
            default -> {
                return OperationResult.failure("Unknown field for removal: " + fieldName, getOperationType());
            }
        }

        // Assign status
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                entry, userRole, "REMOVE_FIELD");

        if (!statusResult.isSuccess()) {
            return OperationResult.failure("Cannot remove field: " + statusResult.getMessage(), getOperationType());
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Field removal: %s removed, Status assigned %s → %s",
                fieldName, statusResult.getOriginalStatus(), statusResult.getNewStatus()));

        return OperationResult.success(String.format("Field %s removed successfully", fieldName), getOperationType(), entry);
    }

    // ========================================================================
    // HOLIDAY BALANCE HANDLING
    // ========================================================================

    // Handle holiday balance restoration for CO entries
    private HolidayBalanceResult handleHolidayBalanceRestoration(String originalTimeOffType, String userRole) {
        if (!WorkCode.TIME_OFF_CODE.equals(originalTimeOffType)) {
            return new HolidayBalanceResult(false, null, null);
        }

        // Only restore balance for user operations (not admin operations on other users)
        boolean isAdminOperation = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(userRole) &&
                !context.getCurrentUsername().equals(username);

        if (isAdminOperation) {
            LoggerUtil.debug(this.getClass(), "Skipping holiday balance restoration for admin operation on other user");
            return new HolidayBalanceResult(false, null, null);
        }

        // Get old balance before updating
        Integer oldBalance = context.getCurrentHolidayBalance();

        // Check if vacation day should be restored
        if (context.shouldProcessVacationDay(date, "remove vacation")) {
            boolean balanceUpdated = context.updateHolidayBalance(1); // Restore 1 day

            if (balanceUpdated) {
                Integer newBalance = context.getCurrentHolidayBalance();
                LoggerUtil.info(this.getClass(), String.format(
                        "Restored 1 vacation day for %s (removed CO from %s). Balance: %d → %d",
                        username, date, oldBalance, newBalance));
                return new HolidayBalanceResult(true, oldBalance, newBalance);
            }
        }

        return new HolidayBalanceResult(false, oldBalance, oldBalance);
    }

    // Create success message for time-off removal
    private String createTimeOffRemovalMessage(String originalTimeOffType, HolidayBalanceResult balanceResult) {
        StringBuilder message = new StringBuilder();

        String timeOffDescription = getTimeOffDescription(originalTimeOffType);
        message.append(String.format("Successfully removed %s from %s", timeOffDescription, date));

        if (balanceResult.isUpdated()) {
            message.append(String.format(". Holiday balance restored: %d → %d (vacation request removed)",
                    balanceResult.getOldBalance(), balanceResult.getNewBalance()));
        } else if (WorkCode.TIME_OFF_CODE.equals(originalTimeOffType) && context.isExistingNationalHoliday(date)) {
            message.append(" (no vacation day restored - national holiday)");
        }
        return message.toString();
    }

    // Get user-friendly description for time-off types
    private String getTimeOffDescription(String timeOffType) {
        if (timeOffType == null) return "time off";

        return switch (timeOffType.toUpperCase()) {
            case WorkCode.NATIONAL_HOLIDAY_CODE -> WorkCode.NATIONAL_HOLIDAY_CODE_LONG;
            case WorkCode.TIME_OFF_CODE -> WorkCode.TIME_OFF_CODE_LONG;
            case WorkCode.MEDICAL_LEAVE_CODE -> WorkCode.MEDICAL_LEAVE_CODE_LONG;
            case WorkCode.WEEKEND_CODE -> WorkCode.WEEKEND_CODE_LONG;
            default -> timeOffType;
        };
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    // Reset all fields except date and userId (for complete removal)
    private void resetAllFieldsExceptDate(WorkTimeTable entry) {
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTimeOffType(null);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setLunchBreakDeducted(false);

        LoggerUtil.debug(this.getClass(), String.format(
                "All fields reset except date for userId: %d, workDate: %s",
                entry.getUserId(), entry.getWorkDate()));
    }

    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream().filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    private void replaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry -> updatedEntry.getUserId().equals(entry.getUserId()) &&
                        updatedEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(updatedEntry);
        entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));
    }

    private String createFilePathId(String username, int year, int month) {
        return String.format("%s/%d/%d", username, year, month);
    }

    @Override
    protected String getCommandName() {
        return String.format("Remove[%s, %s, type=%s, adminDelete=%s]", username, date, removalType, isAdminDelete);
    }

    @Override
    protected String getOperationType() {
        return switch (removalType) {
            case "all" -> isAdminDelete ?  OperationResult.OperationType.ADMIN_DELETE :  OperationResult.OperationType.REMOVE_ENTRY;
            case "timeoff" -> OperationResult.OperationType.REMOVE_TIME_OFF;
            default -> OperationResult.OperationType.REMOVE_FIELD;
        };
    }

    // ========================================================================
    // RESULT CLASSES
    // ========================================================================

    // Result holder for holiday balance operations
    @Getter
    private static class HolidayBalanceResult {
        private final boolean updated;
        private final Integer oldBalance;
        private final Integer newBalance;

        public HolidayBalanceResult(boolean updated, Integer oldBalance, Integer newBalance) {
            this.updated = updated;
            this.oldBalance = oldBalance;
            this.newBalance = newBalance;
        }
    }
}