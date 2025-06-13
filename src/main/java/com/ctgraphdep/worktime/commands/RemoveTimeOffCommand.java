package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * CORRECTED Remove Time Off Command - Proper business logic implementation
 * CORRECTED Key Behaviors:
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

        LoggerUtil.info(this.getClass(), String.format(
                "Validating remove time off: %s on %s", username, date));

        // Validate user permissions
        context.validateUserPermissions(username, "remove time off");

        boolean isCurrentUserAdmin = context.isCurrentUserAdmin();
        String currentUsername = context.getCurrentUsername();

        // ADMIN vs USER specific validation
        if (isCurrentUserAdmin && !currentUsername.equals(username)) {
            // Admin removing time off for another user
            validateAdminRemoveTimeOff();
        } else {
            // User removing their own time off
            validateUserRemoveTimeOff();
        }

        LoggerUtil.debug(this.getClass(), "Remove time off validation completed successfully");
    }

    /**
     * Validate admin removing time off (can remove any type, any date)
     */
    private void validateAdminRemoveTimeOff() {
        LoggerUtil.debug(this.getClass(), "Validating admin remove time off operation");

        // Admin can remove any time off type from any date
        // Use TimeValidationService for date validation
        try {
            context.validateHolidayDate(date);
        } catch (Exception e) {
            // For admin operations, be more lenient with date validation
            LoggerUtil.warn(this.getClass(), String.format(
                    "Admin date validation warning for %s: %s", date, e.getMessage()));
            // Don't throw exception for admin operations
        }
    }

    /**
     * CORRECTED: Validate user removing time off (FUTURE dates only, canceling future requests)
     */
    private void validateUserRemoveTimeOff() {
        LoggerUtil.debug(this.getClass(), "Validating user remove time off operation");

        LocalDate today = LocalDate.now();

        // CORRECTED: Users can only remove time off from FUTURE dates (canceling future requests)
        if (!date.isAfter(today)) {
            throw new IllegalArgumentException(String.format(
                    "Users can only cancel future time off requests. Date %s is not allowed. " +
                            "Past time off cannot be canceled as it has already been taken.", date));
        }

        // Users process one-by-one time off cancellations
        LoggerUtil.debug(this.getClass(), String.format(
                "User canceling future time off request for %s", date));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing remove time off command for %s on %s", username, date));

        try {
            // Load and validate entry
            EntryContext entryContext = loadAndValidateEntry();
            if (entryContext.hasError()) {
                return entryContext.getErrorResult();
            }

            // Process the removal
            WorkTimeTable updatedEntry = processTimeOffRemoval(entryContext);

            // Handle holiday balance restoration
            HolidayBalanceResult balanceResult = handleHolidayBalanceRestoration(
                    entryContext.getOldTimeOffType(), entryContext.isAdminOperation());

            // Save and finalize
            saveAndInvalidateCache(entryContext);

            // Create success result
            return createSuccessResult(entryContext, updatedEntry, balanceResult);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error removing time off for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to remove time off: " + e.getMessage(), getOperationType());
        }
    }

// ========================================================================
// HELPER METHODS
// ========================================================================

    /**
     * Load entries and validate the removal operation
     */
    private EntryContext loadAndValidateEntry() {
        int year = date.getYear();
        int month = date.getMonthValue();

        // Determine operation context (admin vs user)
        boolean isAdminOperation = context.isCurrentUserAdmin() &&
                !context.getCurrentUsername().equals(username);

        // Load appropriate entries
        List<WorkTimeTable> entries;
        if (isAdminOperation) {
            entries = context.loadAdminWorktime(year, month);
        } else {
            entries = context.loadUserWorktime(username, year, month);
        }

        // Find existing entry
        Optional<WorkTimeTable> existingEntry = context.findEntryByDate(entries, userId, date);
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

        // Additional validation for user operations
        if (!isAdminOperation && "SN".equals(oldTimeOffType)) {
            return EntryContext.withError(OperationResult.permissionFailure(getOperationType(),
                    "Users cannot remove national holidays (SN). Only admin can modify SN entries."));
        }

        return new EntryContext(entries, entry, oldTimeOffType, isAdminOperation, year, month);
    }

    /**
     * Process the actual time off removal
     */
    private WorkTimeTable processTimeOffRemoval(EntryContext entryContext) {
        LoggerUtil.info(this.getClass(), String.format(
                "Removing %s time off from %s on %s",
                entryContext.getOldTimeOffType(), username, date));

        // Remove time off using builder
        WorkTimeTable updatedEntry = WorktimeEntityBuilder.removeTimeOff(entryContext.getEntry());

        // Update sync status based on operation type
        if (entryContext.isAdminOperation()) {
            updatedEntry.setAdminSync(com.ctgraphdep.enums.SyncStatusMerge.ADMIN_EDITED);
        } else {
            updatedEntry.setAdminSync(com.ctgraphdep.enums.SyncStatusMerge.USER_INPUT);
        }

        context.addOrReplaceEntry(entryContext.getEntries(), updatedEntry);

        return updatedEntry;
    }

    /**
     * Handle holiday balance restoration for CO entries
     */
    private HolidayBalanceResult handleHolidayBalanceRestoration(String oldTimeOffType, boolean isAdminOperation) {
        if (!"CO".equals(oldTimeOffType)) {
            return new HolidayBalanceResult(false, null, null);
        }

        // Only restore balance for user operations or admin operations on current user
        if (isAdminOperation && !context.getCurrentUsername().equals(username)) {
            return new HolidayBalanceResult(false, null, null);
        }

        // FIXED: Get old balance BEFORE updating
        Integer oldBalance = context.getCurrentHolidayBalance();

        // Check if vacation day should be restored (not for national holidays)
        if (context.shouldProcessVacationDay(date, "remove vacation")) {
            boolean balanceUpdated = context.updateHolidayBalance(1); // Restore 1 day

            if (balanceUpdated) {
                Integer newBalance = context.getCurrentHolidayBalance();
                LoggerUtil.info(this.getClass(), String.format(
                        "Restored 1 vacation day for %s (canceled future request). Balance: %d → %d",
                        username, oldBalance, newBalance));
                return new HolidayBalanceResult(true, oldBalance, newBalance);
            }
        }

        return new HolidayBalanceResult(false, oldBalance, oldBalance);
    }

    /**
     * Save entries and invalidate cache
     */
    private void saveAndInvalidateCache(EntryContext entryContext) {
        // Save entries back
        if (entryContext.isAdminOperation()) {
            context.saveAdminWorktime(entryContext.getEntries(), entryContext.getYear(), entryContext.getMonth());
            // Admin operations DON'T update tracker
        } else {
            context.saveUserWorktime(username, entryContext.getEntries(), entryContext.getYear(), entryContext.getMonth());
            // 🎯 HERE: Only for USER operations, remove from tracker
            try {
                context.removeTimeOffFromTracker(username, userId, date, entryContext.getYear());
                LoggerUtil.debug(this.getClass(), String.format(
                        "Removed from time off tracker for user %s on %s", username, date));
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to remove from tracker for %s on %s: %s", username, date, e.getMessage()));
                // Don't fail entire operation for tracker sync issue
            }
        }

        // Invalidate cache (existing logic)
        context.invalidateTimeOffCache(username, entryContext.getYear());
        context.refreshTimeOffTracker(username, userId, entryContext.getYear());
    }

    /**
     * Create the success operation result
     */
    private OperationResult createSuccessResult(EntryContext entryContext, WorkTimeTable updatedEntry, HolidayBalanceResult balanceResult) {
        // Create success message
        String operationType = entryContext.isAdminOperation() ? "removed" : "canceled";
        String message = String.format("Successfully %s %s time off from %s on %s",
                operationType, entryContext.getOldTimeOffType(), username, date);

        if (balanceResult.isUpdated()) {
            message += String.format(". Holiday balance restored: %d → %d (vacation request canceled)",
                    balanceResult.getOldBalance(), balanceResult.getNewBalance());
        } else if ("CO".equals(entryContext.getOldTimeOffType()) && context.isExistingNationalHoliday(date)) {
            message += " (no vacation day restored - national holiday)";
        }

        // Create side effects tracking
        OperationResult.OperationSideEffects.Builder sideEffectsBuilder =
                OperationResult.OperationSideEffects.builder()
                        .fileUpdated(entryContext.isAdminOperation() ?
                                String.format("admin/%d/%d", entryContext.getYear(), entryContext.getMonth()) :
                                context.createFilePathId(username, entryContext.getYear(), entryContext.getMonth()))
                        .cacheInvalidated(context.createCacheKey(username, entryContext.getYear()));

        // FIXED: Now properly checks if balance was updated
        if (balanceResult.isUpdated()) {
            sideEffectsBuilder.holidayBalanceChanged(balanceResult.getOldBalance(), balanceResult.getNewBalance());
        }

        LoggerUtil.info(this.getClass(), message);

        return OperationResult.successWithSideEffects(
                message,
                getOperationType(),
                updatedEntry,
                sideEffectsBuilder.build()
        );
    }

    // ========================================================================
    // HELPER CLASSES
    // ========================================================================

    /**
     * Context holder for entry processing
     */
    @Getter
    private static class EntryContext {
        private final List<WorkTimeTable> entries;
        private final WorkTimeTable entry;
        private final String oldTimeOffType;
        private final boolean adminOperation;
        private final int year;
        private final int month;
        private final OperationResult errorResult;

        private EntryContext(List<WorkTimeTable> entries, WorkTimeTable entry, String oldTimeOffType,
                             boolean adminOperation, int year, int month) {
            this.entries = entries;
            this.entry = entry;
            this.oldTimeOffType = oldTimeOffType;
            this.adminOperation = adminOperation;
            this.year = year;
            this.month = month;
            this.errorResult = null;
        }

        private EntryContext(OperationResult errorResult) {
            this.entries = null;
            this.entry = null;
            this.oldTimeOffType = null;
            this.adminOperation = false;
            this.year = 0;
            this.month = 0;
            this.errorResult = errorResult;
        }

        public static EntryContext withError(OperationResult errorResult) {
            return new EntryContext(errorResult);
        }

        public boolean hasError() { return errorResult != null; }
    }

    /**
     * Result holder for holiday balance operations
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
    @Override
    protected String getCommandName() {
        return String.format("RemoveTimeOff[%s, %s]", username, date);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.REMOVE_TIME_OFF;
    }
}