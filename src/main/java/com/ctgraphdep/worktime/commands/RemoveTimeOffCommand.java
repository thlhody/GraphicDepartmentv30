package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.StatusAssignmentEngine;
import com.ctgraphdep.worktime.util.StatusAssignmentResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * REFACTORED: Remove Time Off Command using accessor pattern with PRESERVED business logic.
 * PRESERVED Key Behaviors:
 * - ADMIN: Can remove any time off entry (CO/CM/SN) for any date
 * - USER: Can only remove CO/CM from FUTURE dates (canceling future time off requests)
 * - USER: Cannot remove past CO/CM (can't cancel already taken time off)
 * - Holiday balance restoration for CO cancellations (one-by-one basis)
 * - Proper validation based on user role and date restrictions
 */
public class RemoveTimeOffCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;

    public RemoveTimeOffCommand(WorktimeOperationContext context, String username, Integer userId, LocalDate date) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
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

        LoggerUtil.info(this.getClass(), String.format("Validating remove time off: %s on %s", username, date));

        // PRESERVED: Original permission validation
        context.validateUserPermissions(username, "remove time off");

        boolean isCurrentUserAdmin = context.isCurrentUserAdmin();
        String currentUsername = context.getCurrentUsername();

        // PRESERVED: ADMIN vs USER specific validation (EXACT SAME LOGIC)
        if (isCurrentUserAdmin && !currentUsername.equals(username)) {
            // Admin removing time off for another user
            validateAdminRemoveTimeOff();
        } else {
            // User removing their own time off
            validateUserRemoveTimeOff();
        }

        LoggerUtil.debug(this.getClass(), "Remove time off validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Executing remove time off command for %s on %s", username, date));

        try {
            // PRESERVED: Load and validate entry (SAME BUSINESS LOGIC)
            EntryContext entryContext = loadAndValidateEntry();
            if (entryContext.hasError()) {
                return entryContext.getErrorResult();
            }

            // ✅ UPDATED: Process removal (now returns OperationResult)
            OperationResult removalResult = processTimeOffRemoval(entryContext);
            if (!removalResult.isSuccess()) {
                return removalResult; // Early return on failure
            }

            // Extract the updated entry from the result
            WorkTimeTable updatedEntry = removalResult.getEntryData();

            // PRESERVED: Handle holiday balance restoration (SAME BUSINESS LOGIC)
            HolidayBalanceResult balanceResult = handleHolidayBalanceRestoration(
                    entryContext.getOldTimeOffType(), entryContext.isAdminOperation());

            // NEW: Save and invalidate cache
            saveAndInvalidateCache(entryContext);

            // PRESERVED: Create success result (SAME LOGIC)
            return createSuccessResult(entryContext, updatedEntry, balanceResult);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error removing time off for %s on %s: %s",
                    username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to remove time off: " + e.getMessage(), getOperationType());
        }
    }

    // ========================================================================
    // PRESERVED VALIDATION METHODS (EXACT SAME BUSINESS LOGIC)
    // ========================================================================

    /**
     * PRESERVED: Validate admin removing time off (can remove any type, any date)
     */
    private void validateAdminRemoveTimeOff() {
        LoggerUtil.debug(this.getClass(), "Validating admin remove time off operation");

        // PRESERVED: Admin can remove any time off type from any date
        try {
            context.validateHolidayDate(date);
        } catch (Exception e) {
            // PRESERVED: For admin operations, be more lenient with date validation
            LoggerUtil.warn(this.getClass(), String.format("Admin date validation warning for %s: %s", date, e.getMessage()));
            // Don't throw exception for admin operations
        }
    }

    /**
     * PRESERVED: Validate user removing time off (FUTURE dates only, canceling future requests)
     */
    private void validateUserRemoveTimeOff() {
        LoggerUtil.debug(this.getClass(), "Validating user remove time off operation");

        LocalDate today = LocalDate.now();

        // PRESERVED: Users can only remove time off from FUTURE dates (canceling future requests)
        if (!date.isAfter(today)) {
            throw new IllegalArgumentException(String.format("Users can only cancel future time off requests. Date %s is not allowed. " +
                    "Past time off cannot be canceled as it has already been taken.", date));
        }

        // PRESERVED: Users process one-by-one time off cancellations
        LoggerUtil.debug(this.getClass(), String.format("User canceling future time off request for %s", date));
    }

    // ========================================================================
    // REFACTORED HELPER METHODS (NEW ACCESSOR LOGIC, PRESERVED BUSINESS LOGIC)
    // ========================================================================

    /**
     * REFACTORED: Load entries and validate the removal operation using accessor pattern
     */
    private EntryContext loadAndValidateEntry() {
        int year = date.getYear();
        int month = date.getMonthValue();

        // PRESERVED: Determine operation context (admin vs user)
        boolean isAdminOperation = context.isCurrentUserAdmin() && !context.getCurrentUsername().equals(username);

        // NEW: Use appropriate accessor based on context
        WorktimeDataAccessor accessor;
        if (isAdminOperation) {
            accessor = context.getDataAccessor("admin");
        } else {
            accessor = context.getDataAccessor(username);
        }

        // NEW: Load appropriate entries using accessor
        List<WorkTimeTable> entries;
        if (isAdminOperation) {
            entries = accessor.readWorktime("admin", year, month);
        } else {
            entries = accessor.readWorktime(username, year, month);
        }

        if (entries == null) {
            entries = new java.util.ArrayList<>();
        }

        // PRESERVED: Find existing entry (SAME LOGIC)
        Optional<WorkTimeTable> existingEntry = findEntryByDate(entries, userId, date);
        if (existingEntry.isEmpty()) {
            String message = String.format("No time off entry found for %s on %s", username, date);
            LoggerUtil.warn(this.getClass(), message);
            return EntryContext.withError(OperationResult.failure(message, getOperationType()));
        }

        WorkTimeTable entry = existingEntry.get();
        String oldTimeOffType = entry.getTimeOffType();

        if (oldTimeOffType == null || oldTimeOffType.trim().isEmpty()) {
            String message = String.format("Entry for %s on %s has no time off to remove", username, date);
            LoggerUtil.warn(this.getClass(), message);
            return EntryContext.withError(OperationResult.failure(message, getOperationType()));
        }

        // PRESERVED: Additional validation for user operations (SAME LOGIC)
        if (!isAdminOperation && WorkCode.NATIONAL_HOLIDAY_CODE.equals(oldTimeOffType)) {
            return EntryContext.withError(OperationResult.permissionFailure(getOperationType(),
                    "Users cannot remove national holidays (SN). Only admin can modify SN entries."));
        }

        return new EntryContext(entries, entry, oldTimeOffType, isAdminOperation, year, month, accessor);
    }

    /**
     * PRESERVED: Process the actual time off removal (SAME BUSINESS LOGIC)
     */
    private OperationResult processTimeOffRemoval(EntryContext entryContext) {
        LoggerUtil.info(this.getClass(), String.format("Removing %s time off from %s on %s",
                entryContext.getOldTimeOffType(), username, date));

        try {
            // PRESERVED: Remove time off using builder (SAME BUSINESS LOGIC)
            WorkTimeTable updatedEntry = WorktimeEntityBuilder.removeTimeOff(entryContext.getEntry());

            // ✅ NEW: Use StatusAssignmentEngine to set correct status
            StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                    updatedEntry,
                    context.getCurrentUser().getRole(),
                    getOperationType()
            );

            if (!statusResult.isSuccess()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Status assignment failed: %s", statusResult.getMessage()));
                return OperationResult.failure("Cannot remove time off: " + statusResult.getMessage(), getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Status assigned: %s → %s", statusResult.getOriginalStatus(), statusResult.getNewStatus()));

            // Update the entry in the context
            replaceEntry(entryContext.getEntries(), updatedEntry);

            // Return success with the updated entry
            return OperationResult.success("Time off removal processed successfully", getOperationType(), updatedEntry);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error processing time off removal: %s", e.getMessage()), e);
            return OperationResult.failure("Failed to process time off removal: " + e.getMessage(), getOperationType());
        }
    }

    /**
     * PRESERVED: Handle holiday balance restoration for CO entries (SAME BUSINESS LOGIC)
     */
    private HolidayBalanceResult handleHolidayBalanceRestoration(String oldTimeOffType, boolean isAdminOperation) {
        if (!WorkCode.TIME_OFF_CODE.equals(oldTimeOffType)) {
            return new HolidayBalanceResult(false, null, null);
        }

        // PRESERVED: Only restore balance for user operations or admin operations on current user
        if (isAdminOperation && !context.getCurrentUsername().equals(username)) {
            return new HolidayBalanceResult(false, null, null);
        }

        // PRESERVED: Get old balance BEFORE updating
        Integer oldBalance = context.getCurrentHolidayBalance();

        // PRESERVED: Check if vacation day should be restored (not for national holidays)
        if (context.shouldProcessVacationDay(date, "remove vacation")) {
            boolean balanceUpdated = context.updateHolidayBalance(1); // Restore 1 day

            if (balanceUpdated) {
                Integer newBalance = context.getCurrentHolidayBalance();
                LoggerUtil.info(this.getClass(), String.format("Restored 1 vacation day for %s (canceled future request). Balance: %d → %d",
                        username, oldBalance, newBalance));
                return new HolidayBalanceResult(true, oldBalance, newBalance);
            }
        }

        return new HolidayBalanceResult(false, oldBalance, oldBalance);
    }

    /**
     * REFACTORED: Save entries and invalidate cache using accessor
     */
    private void saveAndInvalidateCache(EntryContext entryContext) {
        try {
            // NEW: Save entries using accessor
            if (entryContext.isAdminOperation()) {
                entryContext.getAccessor().writeWorktimeWithStatus("admin", entryContext.getEntries(),
                        entryContext.getYear(), entryContext.getMonth(), context.getCurrentUser().getRole());
                // PRESERVED: Admin operations DON'T update tracker
            } else {
                entryContext.getAccessor().writeWorktimeWithStatus(username, entryContext.getEntries(),
                        entryContext.getYear(), entryContext.getMonth(), context.getCurrentUser().getRole());
                // PRESERVED: Only for USER operations, remove from tracker
                try {
                    context.removeTimeOffFromTracker(username, userId, date, entryContext.getYear());
                    LoggerUtil.debug(this.getClass(), String.format("Removed from time off tracker for user %s on %s", username, date));
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Failed to remove from tracker for %s on %s: %s",
                            username, date, e.getMessage()));
                    // PRESERVED: Don't fail entire operation for tracker sync issue
                }
            }

            // PRESERVED: Invalidate cache (same logic)
            context.invalidateTimeOffCache(username, entryContext.getYear());
            context.refreshTimeOffTracker(username, userId, entryContext.getYear());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving entries for %s: %s", username, e.getMessage()), e);
            throw e;
        }
    }

    /**
     * PRESERVED: Create the success operation result (SAME LOGIC)
     */
    private OperationResult createSuccessResult(EntryContext entryContext, WorkTimeTable updatedEntry, HolidayBalanceResult balanceResult) {
        // PRESERVED: Create success message (SAME LOGIC)
        String operationType = entryContext.isAdminOperation() ? "removed" : "canceled";
        String message = String.format("Successfully %s %s time off from %s on %s",
                operationType, entryContext.getOldTimeOffType(), username, date);

        if (balanceResult.isUpdated()) {
            message += String.format(". Holiday balance restored: %d → %d (vacation request canceled)",
                    balanceResult.getOldBalance(), balanceResult.getNewBalance());
        } else if ("CO".equals(entryContext.getOldTimeOffType()) && context.isExistingNationalHoliday(date)) {
            message += " (no vacation day restored - national holiday)";
        }

        // PRESERVED: Create side effects tracking (SAME LOGIC)
        OperationResult.OperationSideEffects.Builder sideEffectsBuilder = OperationResult.OperationSideEffects.builder()
                .fileUpdated(entryContext.isAdminOperation() ?
                        String.format("admin/%d/%d", entryContext.getYear(), entryContext.getMonth()) :
                        createFilePathId(username, entryContext.getYear(), entryContext.getMonth()))
                .cacheInvalidated(createCacheKey(username, entryContext.getYear()));

        // PRESERVED: Balance change tracking
        if (balanceResult.isUpdated()) {
            sideEffectsBuilder.holidayBalanceChanged(balanceResult.getOldBalance(), balanceResult.getNewBalance());
        }

        LoggerUtil.info(this.getClass(), message);

        return OperationResult.successWithSideEffects(message, getOperationType(), updatedEntry, sideEffectsBuilder.build());
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Find entry by date and user ID
     */
    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    /**
     * Replace entry in list
     */
    private void replaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry ->
                updatedEntry.getUserId().equals(entry.getUserId()) &&
                        updatedEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(updatedEntry);
        entries.sort(java.util.Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    /**
     * Create file path ID
     */
    private String createFilePathId(String username, int year, int month) {
        return String.format("%s/%d/%d", username, year, month);
    }

    /**
     * Create cache key
     */
    private String createCacheKey(String username, int year) {
        return String.format("%s-%d", username, year);
    }

    @Override
    protected String getCommandName() {
        return String.format("RemoveTimeOff[%s, %s]", username, date);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.REMOVE_TIME_OFF;
    }

    // ========================================================================
    // PRESERVED HELPER CLASSES (ENHANCED WITH ACCESSOR)
    // ========================================================================

    /**
     * ENHANCED: Context holder for entry processing (now includes accessor)
     */
    @Getter
    private static class EntryContext {
        private final List<WorkTimeTable> entries;
        private final WorkTimeTable entry;
        private final String oldTimeOffType;
        private final boolean adminOperation;
        private final int year;
        private final int month;
        private final WorktimeDataAccessor accessor;
        private final OperationResult errorResult;

        private EntryContext(List<WorkTimeTable> entries, WorkTimeTable entry, String oldTimeOffType,
                             boolean adminOperation, int year, int month, WorktimeDataAccessor accessor) {
            this.entries = entries;
            this.entry = entry;
            this.oldTimeOffType = oldTimeOffType;
            this.adminOperation = adminOperation;
            this.year = year;
            this.month = month;
            this.accessor = accessor;
            this.errorResult = null;
        }

        private EntryContext(OperationResult errorResult) {
            this.entries = null;
            this.entry = null;
            this.oldTimeOffType = null;
            this.adminOperation = false;
            this.year = 0;
            this.month = 0;
            this.accessor = null;
            this.errorResult = errorResult;
        }

        public static EntryContext withError(OperationResult errorResult) {
            return new EntryContext(errorResult);
        }

        public boolean hasError() {
            return errorResult != null;
        }
    }

    /**
     * PRESERVED: Result holder for holiday balance operations (SAME LOGIC)
     */
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