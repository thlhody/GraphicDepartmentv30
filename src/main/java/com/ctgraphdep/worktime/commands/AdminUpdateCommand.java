package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * FIXED: Admin Update Command using accessor pattern with comprehensive holiday balance management.
 * Handles admin worktime updates (hours, time off types, or remove entries) with automatic holiday balance adjustments.
 * Key Features:
 * - Uses AdminOwnDataAccessor for admin worktime operations
 * - Holiday balance tracking for CO (vacation) changes
 * - Comprehensive validation and error handling
 * - Side effects tracking for audit trail
 * - Integration with existing WorktimeEntityBuilder methods (FIXED)
 */
public class AdminUpdateCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final Integer userId;
    private final LocalDate date;
    private final String value;

    public AdminUpdateCommand(WorktimeOperationContext context, Integer userId, LocalDate date, String value) {
        super(context);
        this.userId = userId;
        this.date = date;
        this.value = value;
    }

    @Override
    protected void validate() {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating admin update: userId=%d, date=%s, value=%s", userId, date, value));

        // Validate admin permissions - assumes context has this method
        if (!context.isCurrentUserAdmin()) {
            throw new SecurityException("Only administrators can perform admin updates");
        }

        // Validate the value format if not blank/remove

        LoggerUtil.debug(this.getClass(), "Admin update validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing admin update for userId=%d on %s with value=%s using AdminOwnDataAccessor",
                userId, date, value));

        int year = date.getYear();
        int month = date.getMonthValue();

        try {
            // Use AdminOwnDataAccessor for admin worktime operations
            WorktimeDataAccessor accessor = context.getDataAccessor("admin");

            // Load admin entries for the month
            List<WorkTimeTable> adminEntries = accessor.readWorktime("admin", year, month);
            if (adminEntries == null) {
                adminEntries = new java.util.ArrayList<>();
            }

            // Find existing entry to track holiday balance changes
            WorkTimeTable existingEntry = findExistingEntry(adminEntries, userId, date);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Existing entry for user %d on %s: %s", userId, date,
                    existingEntry != null ?
                            String.format("timeOff=%s, minutes=%d",
                                    existingEntry.getTimeOffType(),
                                    existingEntry.getTotalWorkedMinutes() != null ? existingEntry.getTotalWorkedMinutes() : 0) :
                            "none"));

            // Calculate holiday balance change BEFORE making changes
            HolidayBalanceChange balanceChange = calculateHolidayBalanceChange(existingEntry, value);

            // Process the admin update
            AdminUpdateResult updateResult = processAdminUpdate(adminEntries, userId, date, value);

            // Save updated entries using accessor
            accessor.writeWorktimeWithStatus("admin", adminEntries, year, month, context.getCurrentUser().getRole());

            // Apply holiday balance changes if needed
            OperationResult.OperationSideEffects.Builder sideEffectsBuilder =
                    OperationResult.OperationSideEffects.builder()
                            .fileUpdated(String.format("admin/%d/%d", year, month));

            if (balanceChange.hasChange()) {
                boolean balanceUpdated = applyHolidayBalanceChange(balanceChange);
                if (balanceUpdated) {
                    sideEffectsBuilder.holidayBalanceChanged(
                            balanceChange.getOldBalance(),
                            balanceChange.getNewBalance());

                    updateResult.message += String.format(" (Holiday balance: %d → %d)",
                            balanceChange.getOldBalance(), balanceChange.getNewBalance());

                    LoggerUtil.info(this.getClass(), String.format(
                            "Holiday balance updated for user %d: %d → %d (%s)",
                            userId, balanceChange.getOldBalance(), balanceChange.getNewBalance(),
                            balanceChange.getReason()));
                } else {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Admin entry updated but holiday balance update failed for user %d", userId));
                }
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Admin update completed successfully: %s", updateResult.message));

            return OperationResult.successWithSideEffects(
                    updateResult.message,
                    getOperationType(),
                    updateResult.resultEntry,
                    sideEffectsBuilder.build()
            );

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Admin update failed for userId=%d on %s: %s", userId, date, e.getMessage());
            LoggerUtil.error(this.getClass(), errorMessage, e);
            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    /**
     * Process admin update based on value - FIXED to use existing WorktimeEntityBuilder methods
     */
    private AdminUpdateResult processAdminUpdate(List<WorkTimeTable> adminEntries, Integer userId, LocalDate date, String value) {
        if (value == null || value.trim().isEmpty() ||
                "BLANK".equalsIgnoreCase(value.trim()) ||
                "REMOVE".equalsIgnoreCase(value.trim())) {
            // Remove entry
            boolean removed = removeEntryByDate(adminEntries, userId, date);
            String message;
            if (removed) {
                message = String.format("Admin removed entry for user %d on %s", userId, date);
                LoggerUtil.debug(this.getClass(), String.format("Removed admin entry: userId=%d, date=%s", userId, date));
            } else {
                message = String.format("No admin entry found to remove for user %d on %s", userId, date);
                LoggerUtil.debug(this.getClass(), String.format("No entry to remove: userId=%d, date=%s", userId, date));
            }
            return new AdminUpdateResult(message, null);
        }

        String trimmedValue = value.trim().toUpperCase();

        // Create new entry using EXISTING WorktimeEntityBuilder methods
        WorkTimeTable newEntry = createAdminEntry(userId, date, trimmedValue);

        // Add or replace entry in list
        addOrReplaceEntry(adminEntries, newEntry);

        String operation = determineOperation(trimmedValue);
        String message = String.format("Admin %s for user %d on %s", operation, userId, date);

        LoggerUtil.debug(this.getClass(), String.format(
                "Created/updated admin entry: userId=%d, date=%s, operation=%s", userId, date, operation));

        return new AdminUpdateResult(message, newEntry);
    }

    /**
     * Create admin entry using existing WorktimeEntityBuilder methods - FIXED
     */
    private WorkTimeTable createAdminEntry(Integer userId, LocalDate date, String value) {
        // Check for time off types (CO/CM/SN)
        if (value.matches("^(CO|CM|SN)$")) {
            WorkTimeTable entry = WorktimeEntityBuilder.createTimeOffEntry(userId, date, value);
            entry.setAdminSync(MergingStatusConstants.ADMIN_INPUT);
            return entry;
        }

        // Check for work hours (1-24)
        try {
            int hours = Integer.parseInt(value);
            if (hours >= 1 && hours <= 24) {
                WorkTimeTable entry = WorktimeEntityBuilder.createAdminWorkHoursEntry(userId, date, hours);
                entry.setAdminSync(MergingStatusConstants.ADMIN_INPUT);
                return entry;
            } else {
                throw new IllegalArgumentException("Work hours must be between 1 and 24");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid admin value: " + value +
                    ". Expected: work hours (1-24) or time off type (CO/CM/SN)");
        }
    }

    /**
     * Find existing admin entry for the user and date
     */
    private WorkTimeTable findExistingEntry(List<WorkTimeTable> adminEntries, Integer userId, LocalDate date) {
        return adminEntries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Remove entry by date and user ID - UTILITY METHOD
     */
    private boolean removeEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.removeIf(entry ->
                userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()));
    }

    /**
     * Add or replace entry in list - UTILITY METHOD
     */
    private void addOrReplaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry ->
                updatedEntry.getUserId().equals(entry.getUserId()) &&
                        updatedEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(updatedEntry);
        entries.sort(java.util.Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    /**
     * Calculate holiday balance change needed based on the operation
     */
    private HolidayBalanceChange calculateHolidayBalanceChange(WorkTimeTable existingEntry, String newValue) {
        // Get current balance for tracking - using context method
        Integer currentBalance = getUserHolidayBalance(userId);

        boolean wasVacation = isVacationEntry(existingEntry);
        boolean willBeVacation = isVacationValue(newValue);

        if (wasVacation && !willBeVacation) {
            // Was CO, now not CO -> restore 1 holiday day
            return new HolidayBalanceChange(currentBalance, currentBalance + 1,
                    "Restored holiday day (removed CO time off)");
        } else if (!wasVacation && willBeVacation) {
            // Wasn't CO, now is CO -> deduct 1 holiday day
            if (currentBalance > 0) {
                return new HolidayBalanceChange(currentBalance, currentBalance - 1,
                        "Deducted holiday day (added CO time off)");
            } else {
                throw new IllegalArgumentException("Insufficient holiday balance to add CO time off");
            }
        }

        // No balance change needed
        return new HolidayBalanceChange(currentBalance, currentBalance, "No balance change");
    }

    /**
     * Get user holiday balance using context
     */
    private Integer getUserHolidayBalance(Integer userId) {
        try {
            Optional<com.ctgraphdep.model.User> userOpt = context.getUserById(userId);
            if (userOpt.isPresent()) {
                return userOpt.get().getPaidHolidayDays();
            } else {
                LoggerUtil.warn(this.getClass(), String.format("User not found with ID: %d", userId));
                return 0;
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting holiday balance for user %d: %s", userId, e.getMessage()), e);
            return 0;
        }
    }

    /**
     * Apply holiday balance change using context operations
     */
    private boolean applyHolidayBalanceChange(HolidayBalanceChange change) {
        if (!change.hasChange()) {
            return true; // No change needed
        }

        try {
            return context.updateUserHolidayBalance(userId, change.getNewBalance());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating holiday balance for user %d: %s", userId, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Check if entry is a vacation (CO) entry
     */
    private boolean isVacationEntry(WorkTimeTable entry) {
        return entry != null && WorkCode.TIME_OFF_CODE.equals(entry.getTimeOffType());
    }

    /**
     * Check if value will create a vacation (CO) entry
     */
    private boolean isVacationValue(String value) {
        return value != null && WorkCode.TIME_OFF_CODE.equalsIgnoreCase(value.trim());
    }

    /**
     * Determine the operation type for logging
     */
    private String determineOperation(String value) {
        if (value == null || value.trim().isEmpty() ||
                "BLANK".equalsIgnoreCase(value.trim()) ||
                "REMOVE".equalsIgnoreCase(value.trim())) {
            return "entry removal";
        }

        String trimmedValue = value.trim().toUpperCase();
        if (trimmedValue.matches("^(CO|CM|SN)$")) {
            return "time off (" + trimmedValue + ")";
        }

        try {
            Integer.parseInt(trimmedValue);
            return "work hours (" + trimmedValue + "h)";
        } catch (NumberFormatException e) {
            return "update";
        }
    }

    @Override
    protected String getCommandName() {
        return String.format("AdminUpdate[userId=%d, date=%s, value=%s]", userId, date, value);
    }

    @Override
    protected String getOperationType() {
        return "ADMIN_UPDATE";
    }

    // ========================================================================
    // HELPER CLASSES
    // ========================================================================

    /**
     * Helper class to track holiday balance changes
     */
    @Getter
    private static class HolidayBalanceChange {
        private final Integer oldBalance;
        private final Integer newBalance;
        private final String reason;

        public HolidayBalanceChange(Integer oldBalance, Integer newBalance, String reason) {
            this.oldBalance = oldBalance;
            this.newBalance = newBalance;
            this.reason = reason;
        }

        public boolean hasChange() {
            return oldBalance != null && newBalance != null && !oldBalance.equals(newBalance);
        }
    }

    /**
     * Helper class to hold admin update results
     */
    @Getter
    private static class AdminUpdateResult {
        private String message;
        private final WorkTimeTable resultEntry;

        public AdminUpdateResult(String message, WorkTimeTable resultEntry) {
            this.message = message;
            this.resultEntry = resultEntry;
        }
    }
}