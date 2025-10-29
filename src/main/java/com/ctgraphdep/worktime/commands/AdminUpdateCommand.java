package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.TimeOffTypeRegistry;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.merge.status.StatusAssignmentEngine;
import com.ctgraphdep.merge.status.StatusAssignmentResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class AdminUpdateCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final Integer userId;
    private final LocalDate date;
    private final String value;

    private AdminUpdateCommand(WorktimeOperationContext context, Integer userId, LocalDate date, String value) {
        super(context);
        this.userId = userId;
        this.date = date;
        this.value = value;
    }

    // FACTORY METHOD: Create command for admin worktime update
    public static AdminUpdateCommand forUpdate(WorktimeOperationContext context, Integer userId, LocalDate date, String value) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID required for admin update");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date required for admin update");
        }

        return new AdminUpdateCommand(context, userId, date, value);
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
        if (!context.isCurrentUserAdmin()) {
            throw new SecurityException("Only administrators can perform admin updates");
        }

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

            // Find existing entry to track holiday balance changes AND determine if entry exists
            WorkTimeTable existingEntry = findExistingEntry(adminEntries, userId, date);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Existing entry for user %d on %s: %s", userId, date,
                    existingEntry != null ?
                            String.format("EXISTS - timeOff=%s, minutes=%d, status=%s",
                                    existingEntry.getTimeOffType(),
                                    existingEntry.getTotalWorkedMinutes() != null ? existingEntry.getTotalWorkedMinutes() : 0,
                                    existingEntry.getAdminSync()) :
                            "NONE"));

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

    // Process admin update using StatusAssignmentEngine and WorktimeEntityBuilder methods
    private AdminUpdateResult processAdminUpdate(List<WorkTimeTable> adminEntries, Integer userId, LocalDate date, String value) {
        if (value == null || value.trim().isEmpty() || "BLANK".equalsIgnoreCase(value.trim()) || "REMOVE".equalsIgnoreCase(value.trim())) {
            // Find or create entry to reset
            WorkTimeTable entryToReset = findExistingEntry(adminEntries, userId, date);

            if (entryToReset == null) {
                // Create empty entry for reset
                entryToReset = WorktimeEntityBuilder.createEmptyEntry(userId, date);
                adminEntries.add(entryToReset);
            }

            // Reset to empty state
            WorktimeEntityBuilder.resetEntryToEmpty(entryToReset);

            // Assign admin edit status
            assignStatusToEntry(entryToReset);

            // Replace in list
            addOrReplaceEntry(adminEntries, entryToReset);

            String message = String.format("Admin reset entry to empty for user %d on %s", userId, date);
            LoggerUtil.debug(this.getClass(), String.format("Reset admin entry: userId=%d, date=%s", userId, date));

            return new AdminUpdateResult(message, entryToReset);
        }

        String trimmedValue = value.trim().toUpperCase();

        // Check for special day work format first (TYPE:HOURS)
        if (isSpecialDayWorkFormat(trimmedValue)) {
            return processSpecialDayWorkUpdate(adminEntries, userId, date, trimmedValue);
        }

        // Handle regular admin updates using existing WorktimeEntityBuilder methods
        WorkTimeTable newEntry = createAdminEntry(userId, date, trimmedValue);

        // Use StatusAssignmentEngine for status assignment
        assignStatusToEntry(newEntry);

        // Add or replace entry in list
        addOrReplaceEntry(adminEntries, newEntry);

        String operation = determineOperation(trimmedValue);
        String message = String.format("Admin %s for user %d on %s (Status: %s)",
                operation, userId, date, newEntry.getAdminSync());

        LoggerUtil.debug(this.getClass(), String.format(
                "Created/updated admin entry: userId=%d, date=%s, operation=%s, status=%s",
                userId, date, operation, newEntry.getAdminSync()));

        return new AdminUpdateResult(message, newEntry);
    }

    // Process special day work update using WorktimeEntityBuilder methods
    private AdminUpdateResult processSpecialDayWorkUpdate(List<WorkTimeTable> adminEntries, Integer userId, LocalDate date, String specialDayValue) {
        // Parse special day value (e.g., "SN:5" -> type="SN", hours=5.0)
        SpecialDayParseResult parseResult = parseSpecialDayValue(specialDayValue);

        WorkTimeTable entry;
        WorkTimeTable existingEntry = findExistingEntry(adminEntries, userId, date);

        if (existingEntry != null) {
            // Update existing entry using WorktimeEntityBuilder
            entry = WorktimeEntityBuilder.updateSpecialDayWithWorkTime(existingEntry, parseResult.timeOffType, parseResult.workHours);
            LoggerUtil.info(this.getClass(), String.format(
                    "Updated existing entry using WorktimeEntityBuilder.updateSpecialDayWithWorkTime() for %s:%.2f",
                    parseResult.timeOffType, parseResult.workHours));
        } else {
            // Create new entry using WorktimeEntityBuilder
            entry = WorktimeEntityBuilder.createSpecialDayWithWorkTime(userId, date, parseResult.timeOffType, parseResult.workHours);
            adminEntries.add(entry);
            LoggerUtil.info(this.getClass(), String.format(
                    "Created new entry using WorktimeEntityBuilder.createSpecialDayWithWorkTime() for %s:%.2f",
                    parseResult.timeOffType, parseResult.workHours));
        }

        // Use StatusAssignmentEngine for status assignment
        assignStatusToEntry(entry);

        // Replace entry in list (in case it was updated)
        addOrReplaceEntry(adminEntries, entry);

        String message = String.format("Admin special day work (%s:%.2fh) for user %d on %s (Status: %s)",
                parseResult.timeOffType, parseResult.workHours, userId, date, entry.getAdminSync());

        LoggerUtil.debug(this.getClass(), String.format(
                "Processed special day work: userId=%d, date=%s, type=%s, hours=%.2f, status=%s",
                userId, date, parseResult.timeOffType, parseResult.workHours, entry.getAdminSync()));

        return new AdminUpdateResult(message, entry);
    }

    // Assign status using StatusAssignmentEngine with error handling
    private void assignStatusToEntry(WorkTimeTable entry) {
        String currentUserRole = context.getCurrentUser().getRole();

        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                entry,
                currentUserRole,
                getOperationType()
        );

        if (!statusResult.isSuccess()) {
            LoggerUtil.warn(this.getClass(), String.format("Status assignment failed for admin update: %s", statusResult.getMessage()));
            throw new RuntimeException("Cannot process admin update: " + statusResult.getMessage());
        }

        LoggerUtil.info(this.getClass(), String.format("Status assigned for admin update: %s → %s",
                statusResult.getOriginalStatus(), statusResult.getNewStatus()));

    }

    // Check if value matches special day work format (TYPE:HOURS)
    // Uses centralized TimeOffTypeRegistry for pattern validation
    private boolean isSpecialDayWorkFormat(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        // ✅ CENTRALIZED: Pattern managed in TimeOffTypeRegistry
        return TimeOffTypeRegistry.isSpecialDayWorkFormat(value);
    }

    // Parse special day work value (e.g., "SN:5" -> type="SN", hours=5.0)
    private SpecialDayParseResult parseSpecialDayValue(String specialDayValue) {
        String[] parts = specialDayValue.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid special day format: " + specialDayValue + ". Expected format: 'SN:5', 'CO:6', etc.");
        }

        String timeOffType = parts[0].trim().toUpperCase();
        double workHours;

        try {
            workHours = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid work hours in special day value: " + specialDayValue);
        }

        // ✅ CENTRALIZED: Validate using TimeOffTypeRegistry
        if (!TimeOffTypeRegistry.isSpecialDayType(timeOffType)) {
            throw new IllegalArgumentException("Invalid time off type with hours: " + timeOffType +
                ". Expected: " + TimeOffTypeRegistry.getSpecialDayTypesDisplay() +
                ". Note: " + TimeOffTypeRegistry.getPlainTimeOffTypesDisplay() + " do not support hour components.");
        }

        return new SpecialDayParseResult(timeOffType, workHours);
    }

    // Create admin entry using existing WorktimeEntityBuilder methods
    private WorkTimeTable createAdminEntry(Integer userId, LocalDate date, String value) {
        // ✅ CENTRALIZED: Check for any valid time off type using registry
        if (TimeOffTypeRegistry.isValidTimeOffType(value)) {
            // Status will be assigned by StatusAssignmentEngine
            return WorktimeEntityBuilder.createTimeOffEntry(userId, date, value);
        }

        // Check for work hours (1-24) - regular work hours
        try {
            int hours = Integer.parseInt(value);
            if (hours >= 1 && hours <= 24) {
                // Status will be assigned by StatusAssignmentEngine
                return WorktimeEntityBuilder.createAdminWorkHoursEntry(userId, date, hours);
            } else {
                throw new IllegalArgumentException("Work hours must be between 1 and 24");
            }
        } catch (NumberFormatException e) {
            // ✅ CENTRALIZED: Error messages use registry display methods
            throw new IllegalArgumentException("Invalid admin value: " + value +
                ". Expected: work hours (1-24), time off type (" + TimeOffTypeRegistry.getAllTimeOffTypesDisplay() +
                "), or special day work with hours (" + TimeOffTypeRegistry.getSpecialDayWorkExamples() +
                "). Note: " + TimeOffTypeRegistry.getPlainTimeOffTypesDisplay() + " do not support hour components.");
        }
    }

    // Find existing admin entry for the user and date
    private WorkTimeTable findExistingEntry(List<WorkTimeTable> adminEntries, Integer userId, LocalDate date) {
        return adminEntries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst()
                .orElse(null);
    }

//    // Remove entry by date and user ID - UTILITY METHOD
//    private boolean removeEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
//        return entries.removeIf(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()));
//    }

    // Add or replace entry in list - UTILITY METHOD
    private void addOrReplaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry ->
                updatedEntry.getUserId().equals(entry.getUserId()) &&
                        updatedEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(updatedEntry);
        entries.sort(java.util.Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    // ========================================================================
    // HOLIDAY BALANCE LOGIC - UNCHANGED
    // ========================================================================

    // Calculate holiday balance change needed based on the operation
    private HolidayBalanceChange calculateHolidayBalanceChange(WorkTimeTable existingEntry, String newValue) {
        // Get current balance for tracking - using context method
        Integer currentBalance = getUserHolidayBalance(userId);

        boolean wasVacation = isVacationEntry(existingEntry);
        boolean willBeVacation = isVacationValue(newValue);

        if (wasVacation && !willBeVacation) {
            // Was CO, now not CO → restore 1 holiday day
            return new HolidayBalanceChange(currentBalance, currentBalance + 1, "Restored holiday day (removed CO time off)");
        } else if (!wasVacation && willBeVacation) {
            // Wasn't CO, now is CO → deduct 1 holiday day
            if (currentBalance > 0) {
                return new HolidayBalanceChange(currentBalance, currentBalance - 1, "Deducted holiday day (added CO time off)");
            } else {
                throw new IllegalArgumentException("Insufficient holiday balance to add CO time off");
            }
        }

        // No balance change needed
        return new HolidayBalanceChange(currentBalance, currentBalance, "No balance change");
    }

    // Get user holiday balance using context
    private Integer getUserHolidayBalance(Integer userId) {
        try {
            Optional<User> userOpt = context.getUserById(userId);
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

    // Apply holiday balance change using context operations
    private boolean applyHolidayBalanceChange(HolidayBalanceChange change) {
        if (!change.hasChange()) {
            return true; // No change needed
        }

        try {
            return context.updateUserHolidayBalance(userId, change.getNewBalance());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating holiday balance for user %d: %s", userId, e.getMessage()), e);
            return false;
        }
    }

    // Check if entry is a vacation (CO) entry
    private boolean isVacationEntry(WorkTimeTable entry) {
        return entry != null && WorkCode.TIME_OFF_CODE.equals(entry.getTimeOffType());
    }

    // Check if value will create a vacation (CO) entry
    private boolean isVacationValue(String value) {
        return value != null && WorkCode.TIME_OFF_CODE.equalsIgnoreCase(value.trim());
    }

    // Determine the operation type for logging
    private String determineOperation(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "entry removal";
        }

        String trimmedValue = value.trim().toUpperCase();

        // Check for special day work format
        if (isSpecialDayWorkFormat(trimmedValue)) {
            return "special day work (" + trimmedValue + ")";
        }

        // ✅ CENTRALIZED: Check for regular time off using registry
        if (TimeOffTypeRegistry.isValidTimeOffType(trimmedValue)) {
            return "time off (" + trimmedValue + ")";
        }

        // Check for work hours
        try {
            Integer.parseInt(trimmedValue);
            return "work hours (" + trimmedValue + "h)";
        } catch (NumberFormatException e) {
            return "update";
        }
    }

    // ========================================================================
    // HELPER CLASSES - ENHANCED
    // ========================================================================

    // Helper class for special day parsing results
    private record SpecialDayParseResult(String timeOffType, double workHours) {
    }

    @Override
    protected String getCommandName() {
        return String.format("AdminUpdate[userId=%d, date=%s, value=%s]", userId, date, value);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.ADMIN_UPDATE;
    }

    // ========================================================================
    // HELPER CLASSES - UNCHANGED
    // ========================================================================

    // Helper class to track holiday balance changes
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

    // Helper class to hold admin update results
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