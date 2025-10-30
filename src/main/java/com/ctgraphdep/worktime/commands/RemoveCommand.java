package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.merge.status.StatusAssignmentEngine;
import com.ctgraphdep.merge.status.StatusAssignmentResult;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Enhanced RemoveCommand - Unified removal system using timestamped edit approach:
 * 1. Complete entry removal (admin delete button) - reset to empty with edit status
 * 2. Time-off field removal (CO/CM removal for users) - remove field with edit status
 * 3. Individual field removal (future extensibility) - remove field with edit status
 * This ensures consistent merge logic and proper audit trail.
 */
public class RemoveCommand extends WorktimeOperationCommand<WorkTimeTable> {

    private final String username;
    private final LocalDate date;
    private final String removalType; // "all", "timeoff", "starttime", etc.
    private final WorktimeDataAccessor accessor;
    private final com.ctgraphdep.worktime.rules.TimeOffOperationRules timeOffRules;

    // Private constructor - use factory methods
    private RemoveCommand(WorktimeOperationContext context, WorktimeDataAccessor accessor,
                          String username, LocalDate date, String removalType,
                          com.ctgraphdep.worktime.rules.TimeOffOperationRules timeOffRules) {
        super(context);
        this.accessor = accessor;
        this.username = username;
        this.date = date;
        this.removalType = removalType;
        this.timeOffRules = timeOffRules;
    }

    // ========================================================================
    // FACTORY METHODS FOR DIFFERENT REMOVAL SCENARIOS
    // ========================================================================

    // Create command for complete entry removal (admin delete button)
    public static RemoveCommand forCompleteRemoval(WorktimeOperationContext context, WorktimeDataAccessor accessor, String username, LocalDate date,
                                                    com.ctgraphdep.worktime.rules.TimeOffOperationRules timeOffRules) {
        return new RemoveCommand(context, accessor, username, date, "all", timeOffRules);
    }

    // Create command for time-off field removal (user CO/CM removal)
    public static RemoveCommand forTimeOffRemoval(WorktimeOperationContext context, WorktimeDataAccessor accessor, String username, LocalDate date,
                                                   com.ctgraphdep.worktime.rules.TimeOffOperationRules timeOffRules) {
        return new RemoveCommand(context, accessor, username, date, "timeoff", timeOffRules);
    }

    // Create command for specific field removal (future extensibility)
    public static RemoveCommand forFieldRemoval(WorktimeOperationContext context, WorktimeDataAccessor accessor, String username, LocalDate date, String fieldName,
                                                 com.ctgraphdep.worktime.rules.TimeOffOperationRules timeOffRules) {
        return new RemoveCommand(context, accessor, username, date, fieldName, timeOffRules);
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

        LoggerUtil.info(this.getClass(), String.format("Validating removal: user=%s, date=%s, type=%s", username, date, removalType));

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
            validateAdminTimeOffRemoval();
        } else {
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
        }
    }

    // User time-off removal validation (CO/CM only, date range restricted)
    private void validateUserTimeOffRemoval() {
        LoggerUtil.debug(this.getClass(), "Validating user time-off removal operation");

        if (!isDateWithinUserRemovalRange(date)) {
            throw new IllegalArgumentException(String.format("Users can only remove time off from previous, current, or future months. " +
                            "Date %s is too old to modify.", date));
        }

        LoggerUtil.debug(this.getClass(), String.format("User time-off removal date %s is within allowed range", date));
    }

    // Check if date is within user removal range (previous month + current + future)
    private boolean isDateWithinUserRemovalRange(LocalDate date) {
        LocalDate today = LocalDate.now();
        LocalDate earliestAllowed = today.withDayOfMonth(1).minusMonths(1);
        return !date.isBefore(earliestAllowed);
    }

    // ========================================================================
    // EXECUTION
    // ========================================================================

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Executing RemoveCommand: user=%s, date=%s, type=%s", username, date, removalType));

        try {
            // Load existing entries
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);

            if (entries == null) {
                entries = new java.util.ArrayList<>();
            }

            // Find the entry to remove/modify
            Optional<WorkTimeTable> existingEntryOpt = findEntryByDate(entries, context.getCurrentUser().getUserId(), date);

            if (existingEntryOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("No entry found for %s on %s - nothing to remove", username, date));
                return OperationResult.failure("No entry found to remove", getOperationType());
            }

            WorkTimeTable entry = existingEntryOpt.get();
            String userRole = context.getCurrentUser().getRole();
            Integer userSchedule = context.getCurrentUser().getSchedule();

            LoggerUtil.debug(this.getClass(), String.format("Found entry to process: timeOffType=%s, removalType=%s", entry.getTimeOffType(), removalType));

            // Route to appropriate removal handler
            OperationResult result = switch (removalType) {
                case "all" -> handleCompleteEntryReset(entry, userRole);
                case "timeoff" -> handleTimeOffRemoval(entry, userRole, userSchedule);
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
                    .fileUpdated(createFilePathId(username, year, month)).build();

            LoggerUtil.info(this.getClass(), String.format("Successfully executed removal for %s on %s: %s", username, date, result.getMessage()));

            return OperationResult.successWithSideEffects(result.getMessage(), getOperationType(), entry, sideEffects);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error executing removal for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to remove: " + e.getMessage(), getOperationType());
        }
    }

    // ========================================================================
    // REMOVAL HANDLERS
    // ========================================================================

    // Handle complete entry reset (admin delete button) - reset to empty with edit status
    private OperationResult handleCompleteEntryReset(WorkTimeTable entry, String userRole) {
        LoggerUtil.debug(this.getClass(), "Handling complete entry reset to empty state");

        // Reset entry to empty state using WorktimeEntityBuilder
        WorktimeEntityBuilder.resetEntryToEmpty(entry);

        // Assign timestamped edit status
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(entry, userRole, OperationResult.OperationType.REMOVE_TIME_OFF);

        if (!statusResult.isSuccess()) {
            return OperationResult.failure("Cannot reset entry: " + statusResult.getMessage(), getOperationType());
        }

        LoggerUtil.info(this.getClass(), String.format("Complete entry reset: Status assigned %s → %s", statusResult.getOriginalStatus(), statusResult.getNewStatus()));

        return OperationResult.success("Entry reset to empty state successfully", getOperationType(), entry);
    }

    // Handle time-off field removal with holiday balance restoration
    private OperationResult handleTimeOffRemoval(WorkTimeTable entry, String userRole, Integer userSchedule) {
        LoggerUtil.debug(this.getClass(), "Handling time-off field removal");

        String originalTimeOffType = entry.getTimeOffType();
        if (originalTimeOffType == null || originalTimeOffType.trim().isEmpty()) {
            return OperationResult.failure("No time off found to remove", getOperationType());
        }

        // Validate permissions first
        OperationResult permissionResult = validateTimeOffRemovalPermissions(originalTimeOffType, userRole);
        if (!permissionResult.isSuccess()) {
            return permissionResult;
        }

        LoggerUtil.info(this.getClass(), String.format("Removing time-off type '%s' from entry", originalTimeOffType));

        // STEP 1: Handle special removal logic BEFORE removing
        if (timeOffRules.requiresSpecialRemovalLogic(originalTimeOffType)) {
            if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(originalTimeOffType)) {
                // CR: Refill overtime
                handleCRRefill(entry, userSchedule);
            } else if (WorkCode.WEEKEND_CODE.equalsIgnoreCase(originalTimeOffType)) {
                // W: Complete tombstone (reset entire entry to empty)
                LoggerUtil.info(this.getClass(), String.format("W (Weekend) removal - tombstoning entry for %s on %s", username, date));
                return handleWeekendTombstone(entry, userRole, originalTimeOffType);
            }
        }

        // STEP 2: Remove time-off field
        entry.setTimeOffType(null);

        // STEP 3: Remove from tracker IMMEDIATELY
        removeFromTimeOffTracker(userRole);

        // STEP 4: Determine path and process accordingly
        if (shouldResetEntry(entry)) {
            return handleEntryReset(entry, userRole, originalTimeOffType);
        } else {
            return handleEntryRecalculation(entry, userRole, userSchedule, originalTimeOffType);
        }
    }

    // Validate time-off removal permissions using centralized business rules
    private OperationResult validateTimeOffRemovalPermissions(String timeOffType, String userRole) {
        // Admin can remove any type (except auto-managed types)
        if (SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(userRole)) {
            if (timeOffRules.isRemovalBlocked(timeOffType)) {
                String reason = timeOffRules.getCannotRemoveReason(timeOffType);
                return OperationResult.failure(reason, getOperationType());
            }
            return OperationResult.success("Permissions validated", getOperationType());
        }

        // User removal: use centralized rules
        if (timeOffRules.isRemovalBlocked(timeOffType)) {
            String reason = timeOffRules.getCannotRemoveReason(timeOffType);
            return OperationResult.failure(reason, getOperationType());
        }

        return OperationResult.success("Permissions validated", getOperationType());
    }

    /**
     * Handle CR (Recovery Leave) overtime refill logic.
     * CR Logic:
     * - When ADDED: Fills regular time with schedule hours, deducts from overtime (during consolidation)
     * - When REMOVED: Removes those regular hours, refills overtime
     *
     * This is the inverse operation of CR addition.
     *
     * @param entry The worktime entry with CR to be removed
     * @param userSchedule User's schedule in hours (e.g., 8)
     */
    private void handleCRRefill(WorkTimeTable entry, Integer userSchedule) {
        if (userSchedule == null) {
            userSchedule = 8; // Default schedule
        }

        int scheduleMinutes = userSchedule * 60;

        LoggerUtil.info(this.getClass(), String.format(
                "Handling CR refill: Removing %d minutes from regular time and adding to overtime",
                scheduleMinutes));

        // STEP 1: Remove regular hours that CR added
        int currentWorkedMinutes = entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0;
        int newWorkedMinutes = Math.max(0, currentWorkedMinutes - scheduleMinutes);
        entry.setTotalWorkedMinutes(newWorkedMinutes);

        LoggerUtil.debug(this.getClass(), String.format(
                "Regular time: %d → %d minutes (removed %d)",
                currentWorkedMinutes, newWorkedMinutes, scheduleMinutes));

        // STEP 2: Refill overtime with schedule hours
        int currentOvertimeMinutes = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0;
        int newOvertimeMinutes = currentOvertimeMinutes + scheduleMinutes;
        entry.setTotalOvertimeMinutes(newOvertimeMinutes);

        LoggerUtil.info(this.getClass(), String.format(
                "Overtime refilled: %d → %d minutes (added %d back)",
                currentOvertimeMinutes, newOvertimeMinutes, scheduleMinutes));
    }

    /**
     * Handle W (Weekend) removal - complete tombstone (reset to empty).
     * Weekend Removal Logic:
     * - Removing W = "I didn't actually work this weekend"
     * - Complete entry reset (clear all work times and data)
     * - Different from CO/CM/CE which preserve work times and recalculate
     *
     * @param entry The worktime entry with W to be removed
     * @param userRole Current user role for status assignment
     * @param originalTimeOffType Original time-off type (W)
     * @return OperationResult indicating success or failure
     */
    private OperationResult handleWeekendTombstone(WorkTimeTable entry, String userRole, String originalTimeOffType) {
        LoggerUtil.info(this.getClass(), String.format(
                "Tombstoning W (Weekend) entry for %s on %s - complete reset to empty",
                username, date));

        // STEP 1: Reset entry to completely empty state
        WorktimeEntityBuilder.resetEntryToEmpty(entry);

        // STEP 2: Remove from tracker
        removeFromTimeOffTracker(userRole);

        // STEP 3: Assign status for empty entry
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                entry, userRole, OperationResult.OperationType.REMOVE_TIME_OFF);

        if (!statusResult.isSuccess()) {
            return OperationResult.failure("Cannot remove weekend: " + statusResult.getMessage(), getOperationType());
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Weekend tombstone complete: Status %s → %s. Entry completely reset.",
                statusResult.getOriginalStatus(), statusResult.getNewStatus()));

        // STEP 4: Handle holiday balance restoration (W doesn't affect balance)
        HolidayBalanceResult balanceResult = new HolidayBalanceResult(false, null, null);

        // STEP 5: Create success message
        String message = String.format("Successfully removed Weekend Work from %s (entry completely reset)", date);

        // STEP 6: Invalidate caches
        invalidateCaches();

        return OperationResult.success(message, getOperationType(), entry);
    }

    // Remove from time off tracker
    private void removeFromTimeOffTracker(String userRole) {
        try {
            context.loadUserTrackerSession(username, context.getCurrentUser().getUserId(), date.getYear());
        } catch (Exception e){
            LoggerUtil.warn(this.getClass(), String.format("Failed to load session from tracker for %s on %s: %s", username, date, e.getMessage()));
        }

        // Update tracker for user operations
        if (!SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(userRole) || context.getCurrentUsername().equals(username)) {
            try {
                boolean removed = context.removeTimeOffFromTracker(username, context.getCurrentUser().getUserId(), date, date.getYear());
                if (removed) {
                    LoggerUtil.debug(this.getClass(), String.format("Successfully removed from time off tracker for user %s on %s", username, date));
                } else {
                    LoggerUtil.warn(this.getClass(), String.format("Failed to remove from tracker for %s on %s: method returned false", username, date));
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to remove from tracker for %s on %s: %s", username, date, e.getMessage()));
            }
        }
    }

    // Check if entry should be reset to empty (no work times)
    private boolean shouldResetEntry(WorkTimeTable entry) {
        return entry.getDayStartTime() == null && entry.getDayEndTime() == null;
    }

    // Handle entry reset (no work times remaining) - reset to empty with edit status
    private OperationResult handleEntryReset(WorkTimeTable entry, String userRole, String originalTimeOffType) {
        LoggerUtil.info(this.getClass(), "Entry being reset to empty state - no work times remaining");

        // Reset entry to empty state using WorktimeEntityBuilder
        WorktimeEntityBuilder.resetEntryToEmpty(entry);

        // Assign timestamped edit status
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(entry, userRole, OperationResult.OperationType.REMOVE_TIME_OFF);

        if (!statusResult.isSuccess()) {
            return OperationResult.failure("Cannot reset entry: " + statusResult.getMessage(), getOperationType());
        }

        // Handle holiday balance restoration
        HolidayBalanceResult balanceResult = handleHolidayBalanceRestoration(originalTimeOffType, userRole);

        // Create success message
        String message = createTimeOffRemovalMessage(originalTimeOffType, balanceResult);

        // Invalidate cache after operations
        invalidateCaches();

        return OperationResult.success(message, getOperationType(), entry);
    }

    // Handle entry recalculation (has work times)
    private OperationResult handleEntryRecalculation(WorkTimeTable entry, String userRole, Integer userSchedule, String originalTimeOffType) {
        LoggerUtil.info(this.getClass(), "Processing entry with work times - recalculating");

        // Recalculate work time if both start and end times exist
        if (entry.getDayStartTime() != null && entry.getDayEndTime() != null) {
            WorktimeEntityBuilder.recalculateWorkTime(entry, userSchedule);
            LoggerUtil.info(this.getClass(), "Recalculated work time using regular day logic");
        }

        // Assign edit status for modified entry
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(entry, userRole, OperationResult.OperationType.REMOVE_TIME_OFF);
        if (!statusResult.isSuccess()) {
            return OperationResult.failure("Cannot remove time off: " + statusResult.getMessage(), getOperationType());
        }

        LoggerUtil.info(this.getClass(), String.format("Time-off removal: Status assigned %s → %s", statusResult.getOriginalStatus(), statusResult.getNewStatus()));

        // Handle holiday balance restoration
        HolidayBalanceResult balanceResult = handleHolidayBalanceRestoration(originalTimeOffType, userRole);

        // Create success message
        String message = createTimeOffRemovalMessage(originalTimeOffType, balanceResult);

        // Invalidate cache after operations
        invalidateCaches();

        return OperationResult.success(message, getOperationType(), entry);
    }

    // Invalidate caches
    private void invalidateCaches() {
        context.invalidateTimeOffCache(username, date.getYear());
        context.refreshTimeOffTracker(username, context.getCurrentUser().getUserId(), date.getYear());
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

        // Assign timestamped edit status
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(entry, userRole, OperationResult.OperationType.REMOVE_FIELD);

        if (!statusResult.isSuccess()) {
            return OperationResult.failure("Cannot remove field: " + statusResult.getMessage(), getOperationType());
        }

        LoggerUtil.info(this.getClass(), String.format("Field removal: %s removed, Status assigned %s → %s", fieldName, statusResult.getOriginalStatus(), statusResult.getNewStatus()));

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
        boolean isAdminOperation = SecurityConstants.ROLE_ADMIN.equalsIgnoreCase(userRole) && !context.getCurrentUsername().equals(username);

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
                LoggerUtil.info(this.getClass(), String.format("Restored 1 vacation day for %s (removed CO from %s). Balance: %d → %d", username, date, oldBalance, newBalance));
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
            message.append(String.format(". Holiday balance restored: %d → %d (vacation request removed)", balanceResult.getOldBalance(), balanceResult.getNewBalance()));
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
            case WorkCode.RECOVERY_LEAVE_CODE -> WorkCode.RECOVERY_LEAVE_CODE_LONG;
            case WorkCode.UNPAID_LEAVE_CODE -> WorkCode.UNPAID_LEAVE_CODE_LONG;
            case WorkCode.SPECIAL_EVENT_CODE -> WorkCode.SPECIAL_EVENT_CODE_LONG;
            case WorkCode.WEEKEND_CODE -> WorkCode.WEEKEND_CODE_LONG;
            case WorkCode.SHORT_DAY_CODE -> WorkCode.SHORT_DAY_CODE_LONG;
            default -> timeOffType;
        };
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream().filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate())).findFirst();
    }

    private void replaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry -> updatedEntry.getUserId().equals(entry.getUserId()) && updatedEntry.getWorkDate().equals(entry.getWorkDate()));
        entries.add(updatedEntry);
        entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));
    }

    private String createFilePathId(String username, int year, int month) {
        return String.format("%s/%d/%d", username, year, month);
    }

    @Override
    protected String getCommandName() {
        return String.format("Remove[%s, %s, type=%s]", username, date, removalType);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.REMOVE_TIME_OFF;
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