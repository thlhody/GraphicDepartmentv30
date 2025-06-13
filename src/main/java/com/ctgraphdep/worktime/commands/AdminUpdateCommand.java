package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * REFACTORED Command to process admin worktime updates with comprehensive holiday balance management.
 * Admin can set hours, time off types, or remove entries with automatic holiday balance adjustments.
 * Key Features:
 * - Holiday balance tracking for CO (vacation) changes
 * - Comprehensive validation and error handling
 * - Side effects tracking for audit trail
 * - Integration with WorktimeEntityBuilder for consistent entry creation
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

        // Validate admin permissions
        context.requireAdminPrivileges("admin update");

        // Validate the value format if not blank/remove
        if (value != null && !value.trim().isEmpty() && !"BLANK".equalsIgnoreCase(value.trim())) {
            validateValueFormat(value.trim());
        }

        LoggerUtil.debug(this.getClass(), "Admin update validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing admin update for user %d on %s with value: %s", userId, date, value));

        try {
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> adminEntries = context.loadAdminWorktime(year, month);

            // Get existing entry to track holiday balance changes
            WorkTimeTable existingEntry = findExistingEntry(adminEntries, userId, date);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Existing entry for user %d on %s: %s", userId, date,
                    existingEntry != null ?
                            String.format("timeOff=%s, minutes=%d",
                                    existingEntry.getTimeOffType(), existingEntry.getTotalWorkedMinutes()) :
                            "none"));

            // Create or update admin entry based on value
            WorkTimeTable newEntry = createOrUpdateAdminEntry(userId, date, value);

            // Track holiday balance changes BEFORE updating entries
            HolidayBalanceChange balanceChange = calculateHolidayBalanceChange(existingEntry, newEntry);

            OperationResult.OperationSideEffects.Builder sideEffectsBuilder =
                    OperationResult.OperationSideEffects.builder()
                            .fileUpdated(String.format("admin/%d/%d", year, month));

            String resultMessage;
            WorkTimeTable resultEntry = null;

            if (newEntry != null) {
                // Add or replace entry in admin file
                context.addOrReplaceEntry(adminEntries, newEntry);
                context.saveAdminWorktime(adminEntries, year, month);

                String operation = determineOperation(value);
                resultMessage = String.format("Admin %s for user %d on %s", operation, userId, date);
                resultEntry = newEntry;

                LoggerUtil.info(this.getClass(), String.format(
                        "Created/updated admin entry: userId=%d, date=%s, operation=%s",
                        userId, date, operation));

            } else {
                // Entry was removed (BLANK operation)
                boolean removed = context.removeEntryByDate(adminEntries, userId, date);
                if (removed) {
                    context.saveAdminWorktime(adminEntries, year, month);
                    resultMessage = String.format("Admin removed entry for user %d on %s", userId, date);

                    LoggerUtil.info(this.getClass(), String.format(
                            "Removed admin entry: userId=%d, date=%s", userId, date));
                } else {
                    resultMessage = String.format("No admin entry found to remove for user %d on %s", userId, date);

                    LoggerUtil.debug(this.getClass(), String.format(
                            "No entry to remove: userId=%d, date=%s", userId, date));
                }
            }

            // Apply holiday balance changes
            if (balanceChange.hasChange()) {
                boolean balanceUpdated = applyHolidayBalanceChange(balanceChange);
                if (balanceUpdated) {
                    sideEffectsBuilder.holidayBalanceChanged(
                            balanceChange.getOldBalance(),
                            balanceChange.getNewBalance());

                    resultMessage += String.format(" (Holiday balance: %d → %d)",
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
                    "Admin update completed successfully: %s", resultMessage));

            return OperationResult.successWithSideEffects(
                    resultMessage,
                    getOperationType(),
                    resultEntry,
                    sideEffectsBuilder.build()
            );

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Admin update failed for user %d on %s: %s", userId, date, e.getMessage());
            LoggerUtil.error(this.getClass(), errorMessage, e);
            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    /**
     * Validate admin value format
     */
    private void validateValueFormat(String value) {
        String upperValue = value.toUpperCase();

        // Check for time off types
        if (upperValue.matches("^(CO|CM|SN)$")) {
            return;
        }

        // Check for work hours (1-24)
        try {
            int hours = Integer.parseInt(upperValue);
            if (hours >= 1 && hours <= 24) {
                return;
            } else {
                throw new IllegalArgumentException("Work hours must be between 1 and 24");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid admin value: " + value +
                    ". Expected: work hours (1-24), time off type (CO/CM/SN), or BLANK");
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
     * Create or update admin entry based on the value
     */
    private WorkTimeTable createOrUpdateAdminEntry(Integer userId, LocalDate date, String value) {
        if (value == null || value.trim().isEmpty() || "BLANK".equalsIgnoreCase(value.trim())) {
            // Admin wants to remove/blank the entry - return null to indicate removal
            return null;
        }

        String trimmedValue = value.trim().toUpperCase();

        // Check for time off types
        if (trimmedValue.matches("^(CO|CM|SN)$")) {
            return WorktimeEntityBuilder.createTimeOffEntry(userId, date, trimmedValue);
        }

        // Check for numeric hours (1-24)
        try {
            int hours = Integer.parseInt(trimmedValue);
            if (hours >= 1 && hours <= 24) {
                return WorktimeEntityBuilder.createAdminWorkHoursEntry(userId, date, hours);
            } else {
                throw new IllegalArgumentException("Work hours must be between 1 and 24");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid admin value: " + value +
                    ". Expected: work hours (1-24), time off type (CO/CM/SN), or BLANK");
        }
    }

    /**
     * Calculate holiday balance change needed based on entry changes
     */
    private HolidayBalanceChange calculateHolidayBalanceChange(WorkTimeTable oldEntry, WorkTimeTable newEntry) {
        // Get current balance for tracking
        Integer currentBalance = context.getUserHolidayBalance(userId);

        boolean wasTimeOff = isTimeOffEntry(oldEntry, WorkCode.TIME_OFF_CODE);
        boolean isTimeOff = isTimeOffEntry(newEntry, WorkCode.TIME_OFF_CODE);

        if (wasTimeOff && !isTimeOff) {
            // Was CO, now not CO -> restore 1 holiday day
            return new HolidayBalanceChange(currentBalance, currentBalance + 1,
                    "Restored holiday day (removed CO time off)");
        } else if (!wasTimeOff && isTimeOff) {
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
     * Check if entry is a specific type of time off
     */
    private boolean isTimeOffEntry(WorkTimeTable entry, String timeOffType) {
        return entry != null && timeOffType.equals(entry.getTimeOffType());
    }

    /**
     * Determine the operation type for logging
     */
    private String determineOperation(String value) {
        if (value == null || value.trim().isEmpty() || "BLANK".equalsIgnoreCase(value.trim())) {
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
        return String.format("AdminUpdate[user=%d, date=%s, value=%s]", userId, date, value);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.ADMIN_UPDATE;
    }

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
}