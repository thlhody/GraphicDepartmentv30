package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AddNationalHolidayCommand extends WorktimeOperationCommand<List<WorkTimeTable>> {

    private final LocalDate date;

    private AddNationalHolidayCommand(WorktimeOperationContext context, LocalDate date) {
        super(context);
        this.date = date;
    }

    // Create command for national holiday addition
    public static AddNationalHolidayCommand forDate(WorktimeOperationContext context, LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date required for national holiday addition");
        }

        return new AddNationalHolidayCommand(context, date);
    }

    @Override
    protected void validate() {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        LoggerUtil.debug(this.getClass(), String.format("Validating national holiday date: %s", date));

        // Use TimeValidationService for proper holiday date validation
        try {
            context.validateHolidayDate(date);
            LoggerUtil.debug(this.getClass(), String.format("Holiday date validation passed for: %s", date));
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Holiday date validation failed for %s: %s", date, e.getMessage()));
            throw e;
        }
    }

    @Override
    protected OperationResult executeCommand() {
        int year = date.getYear();
        int month = date.getMonthValue();

        try {
            LoggerUtil.info(this.getClass(), String.format("Adding national holiday for %s using AdminOwnDataAccessor with proper entry transformation", date));

            // Use AdminOwnDataAccessor for admin worktime operations
            WorktimeDataAccessor accessor = context.getDataAccessor("admin");

            // Load admin entries for the month
            List<WorkTimeTable> adminEntries = accessor.readWorktime("admin", year, month);
            if (adminEntries == null) {
                adminEntries = new ArrayList<>();
            }

            // Get all non-admin users
            List<User> nonAdminUsers = context.getNonAdminUsers();
            if (nonAdminUsers.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "No non-admin users found for national holiday creation");
                return OperationResult.failure("No non-admin users found", getOperationType());
            }

            // Process each user individually with proper transformation logic
            List<WorkTimeTable> processedEntries = new ArrayList<>();
            HolidayBalanceTracker balanceTracker = new HolidayBalanceTracker();

            for (User user : nonAdminUsers) {
                EntryTransformationResult result = processUserForNationalHoliday(adminEntries, user, balanceTracker);
                if (result.getTransformedEntry() != null) {
                    processedEntries.add(result.getTransformedEntry());
                }
            }

            // Sort entries for consistency
            sortEntries(adminEntries);

            // Save using AdminOwnDataAccessor
            accessor.writeWorktimeWithStatus("admin", adminEntries, year, month, context.getCurrentUser().getRole());

            // Create success result with comprehensive statistics
            String successMessage = createSuccessMessage(nonAdminUsers.size(), processedEntries.size(), balanceTracker);

            LoggerUtil.info(this.getClass(), successMessage);

            // Create side effects tracking
            OperationResult.OperationSideEffects.Builder sideEffectsBuilder = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(String.format("admin/%d/%d", year, month))
                    .cacheInvalidated(String.format("all-users-%d", year));

            if (balanceTracker.getTotalBalanceChanges() > 0) {
                List<HolidayBalanceChange> successfulChanges = balanceTracker.getSuccessfulChanges();

                if (successfulChanges.size() == 1) {
                    // Single user affected - can provide specific old/new balance
                    HolidayBalanceChange change = successfulChanges.get(0);
                    sideEffectsBuilder.holidayBalanceChanged(change.getOldBalance(), change.getNewBalance());
                } else {
                    // Multiple users affected - use null to indicate aggregate operation
                    sideEffectsBuilder.holidayBalanceChanged(null, null);
                }
            }

            return OperationResult.successWithSideEffects(successMessage, getOperationType(), processedEntries, sideEffectsBuilder.build());

        } catch (Exception e) {
            String errorMessage = String.format("Failed to add national holiday for %s: %s", date, e.getMessage());
            LoggerUtil.error(this.getClass(), errorMessage, e);
            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    // Process individual user for national holiday with proper transformation logic
    private EntryTransformationResult processUserForNationalHoliday(List<WorkTimeTable> adminEntries, User user, HolidayBalanceTracker balanceTracker) {
        Integer userId = user.getUserId();
        WorkTimeTable existingEntry = findExistingEntry(adminEntries, userId, date);

        if (existingEntry != null) {
            // Transform existing entry based on its current type
            return transformExistingEntryToNationalHoliday(adminEntries, existingEntry, user, balanceTracker);
        } else {
            // Create new SN entry for user with no existing entry
            return createNewNationalHolidayEntry(adminEntries, user);
        }
    }

    // Transform existing entry to national holiday based on entry type
    private EntryTransformationResult transformExistingEntryToNationalHoliday(List<WorkTimeTable> adminEntries, WorkTimeTable existingEntry, User user, HolidayBalanceTracker balanceTracker) {
        String originalType = determineOriginalEntryType(existingEntry);
        WorkTimeTable transformedEntry;

        LoggerUtil.info(this.getClass(), String.format(
                "Transforming existing %s entry to SN for user %s (%d) on %s",
                originalType, user.getUsername(), user.getUserId(), date));

        switch (originalType) {
            case "WORK" -> {
                // Work hours → SN with work time (preserve work as overtime)
                transformedEntry = transformWorkToSpecialDayWork(existingEntry);
                LoggerUtil.debug(this.getClass(), String.format(
                        "Transformed work entry to SN with work time: %d minutes → %d overtime minutes",
                        existingEntry.getTotalWorkedMinutes(), transformedEntry.getTotalOvertimeMinutes()));
            }
            case "CO" -> {
                // CO → SN + restore holiday balance
                transformedEntry = transformTimeOffToNationalHoliday(existingEntry);
                restoreHolidayBalanceForUser(user, balanceTracker);
                LoggerUtil.debug(this.getClass(), String.format(
                        "Transformed CO entry to SN and restored holiday balance for user %s", user.getUsername()));
            }
            case "CM" -> {
                // CM → SN (no balance change)
                transformedEntry = transformTimeOffToNationalHoliday(existingEntry);
                LoggerUtil.debug(this.getClass(), String.format(
                        "Transformed CM entry to SN (no balance change) for user %s", user.getUsername()));
            }
            default -> {
                // SN or other → keep as SN (already national holiday)
                transformedEntry = existingEntry;
                transformedEntry.setTimeOffType(WorkCode.NATIONAL_HOLIDAY_CODE);
                LoggerUtil.debug(this.getClass(), String.format(
                        "Entry already SN or unknown type for user %s", user.getUsername()));
            }
        }

        // Apply status using StatusAssignmentEngine (existing entry → ADMIN_EDITED)
        applyStatusToEntry(transformedEntry);

        // Replace entry in list
        addOrReplaceEntry(adminEntries, transformedEntry);

        return new EntryTransformationResult(transformedEntry, originalType, "TRANSFORMED");
    }

    // Create new national holiday entry for user with no existing entry
    private EntryTransformationResult createNewNationalHolidayEntry(List<WorkTimeTable> adminEntries, User user) {
        LoggerUtil.info(this.getClass(), String.format(
                "Creating new SN entry for user %s (%d) on %s",
                user.getUsername(), user.getUserId(), date));

        WorkTimeTable holidayEntry = createSimpleNationalHolidayEntry(user.getUserId(), date);

        // Apply status using StatusAssignmentEngine (new entry → ADMIN_INPUT)
        applyStatusToEntry(holidayEntry);

        adminEntries.add(holidayEntry);

        LoggerUtil.debug(this.getClass(), String.format(
                "Created new SN entry for user %s with status %s", user.getUsername(), holidayEntry.getAdminSync()));

        return new EntryTransformationResult(holidayEntry, "NONE", "CREATED");
    }

    // Apply status using StatusAssignmentEngine
    private void applyStatusToEntry(WorkTimeTable entry) {
        String currentUserRole = context.getCurrentUser().getRole();

        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                entry,
                currentUserRole,
                getOperationType()
        );

        if (!statusResult.isSuccess()) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Status assignment failed for national holiday: %s", statusResult.getMessage()));
            throw new RuntimeException("Cannot assign status to national holiday entry: " + statusResult.getMessage());
        }

        LoggerUtil.debug(this.getClass(), String.format(
                "Status assigned for SN entry: %s → %s",
                statusResult.getOriginalStatus(), statusResult.getNewStatus()));
    }

    // Transform work entry to SN with preserved work time as overtime
    private WorkTimeTable transformWorkToSpecialDayWork(WorkTimeTable workEntry) {
        if (workEntry.getTotalWorkedMinutes() != null && workEntry.getTotalWorkedMinutes() > 0) {
            // Convert work time to hours for special day work
            double workHours = workEntry.getTotalWorkedMinutes() / 60.0;
            return WorktimeEntityBuilder.updateSpecialDayWithWorkTime(workEntry, WorkCode.NATIONAL_HOLIDAY_CODE, workHours);
        } else {
            // No work time to preserve, just convert to simple SN
            return transformTimeOffToNationalHoliday(workEntry);
        }
    }

    // Transform time off entry to national holiday
    private WorkTimeTable transformTimeOffToNationalHoliday(WorkTimeTable timeOffEntry) {
        timeOffEntry.setTimeOffType(WorkCode.NATIONAL_HOLIDAY_CODE);
        // Clear work time (SN without work is just time off)
        timeOffEntry.setDayStartTime(null);
        timeOffEntry.setDayEndTime(null);
        timeOffEntry.setTotalWorkedMinutes(0);
        timeOffEntry.setTotalOvertimeMinutes(0);
        timeOffEntry.setTotalTemporaryStopMinutes(0);
        timeOffEntry.setTemporaryStopCount(0);
        timeOffEntry.setLunchBreakDeducted(false);
        return timeOffEntry;
    }

    // Create simple national holiday entry (time off only, no work)
    private WorkTimeTable createSimpleNationalHolidayEntry(Integer userId, LocalDate date) {
        return WorktimeEntityBuilder.createTimeOffEntry(userId, date, WorkCode.NATIONAL_HOLIDAY_CODE);
    }

    // NEW: Restore holiday balance for user when CO→SN transformation
    private void restoreHolidayBalanceForUser(User user, HolidayBalanceTracker balanceTracker) {
        try {
            // Check if this date should restore vacation balance (not on existing national holidays)
            if (context.shouldProcessVacationDay(date, "convert CO to SN")) {
                Integer oldBalance = user.getPaidHolidayDays();
                boolean updated = context.updateUserHolidayBalance(user.getUserId(), oldBalance + 1);

                if (updated) {
                    balanceTracker.recordBalanceChange(user.getUsername(), oldBalance, oldBalance + 1);
                    LoggerUtil.info(this.getClass(), String.format("Restored holiday balance for %s: %d → %d (CO converted to SN)",
                            user.getUsername(), oldBalance, oldBalance + 1));
                }
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Failed to restore holiday balance for user %s: %s", user.getUsername(), e.getMessage()));
        }
    }

    // Determine original entry type for logging
    private String determineOriginalEntryType(WorkTimeTable entry) {
        if (entry.getTimeOffType() != null) {
            return entry.getTimeOffType(); // CO, CM, SN, W
        } else if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            return "WORK";
        } else {
            return "EMPTY";
        }
    }

    // Create comprehensive success message
    private String createSuccessMessage(int totalUsers, int processedEntries, HolidayBalanceTracker balanceTracker) {
        StringBuilder message = new StringBuilder();
        message.append(String.format("Successfully added national holiday for %s: %d users processed, %d entries created/updated",
                date, totalUsers, processedEntries));

        if (balanceTracker.getTotalBalanceChanges() > 0) {
            message.append(String.format(", %d holiday balances restored", balanceTracker.getTotalBalanceChanges()));

            List<String> affectedUsers = balanceTracker.getUsersAffected();
            if (affectedUsers.size() <= 5) {
                // If few users, list them
                message.append(String.format(" (users: %s)", String.join(", ", affectedUsers)));
            } else {
                // If many users, just show count
                message.append(String.format(" (affecting %d users)", affectedUsers.size()));
            }
        }

        return message.toString();
    }
    // ========================================================================
    // UTILITY METHODS - PRESERVED FROM ORIGINAL
    // ========================================================================

    // Find existing admin entry for the user and date
    private WorkTimeTable findExistingEntry(List<WorkTimeTable> adminEntries, Integer userId, LocalDate date) {
        return adminEntries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst()
                .orElse(null);
    }

    // Add or replace entry in list
    private void addOrReplaceEntry(List<WorkTimeTable> entries, WorkTimeTable newEntry) {
        entries.removeIf(entry ->
                newEntry.getUserId().equals(entry.getUserId()) &&
                        newEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(newEntry);
    }

    // Sort entries by date and user ID - ORIGINAL LOGIC
    private void sortEntries(List<WorkTimeTable> entries) {
        entries.sort(java.util.Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    @Override
    protected String getCommandName() {
        return String.format("AddNationalHoliday[date=%s]", date);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.ADD_NATIONAL_HOLIDAY;
    }

    // ========================================================================
    // HELPER CLASSES
    // ========================================================================

    // Helper class to track individual holiday balance changes
    @Getter
    private static class HolidayBalanceChange {
        private final String username;
        private final Integer oldBalance;
        private final Integer newBalance;
        private final String reason;

        public HolidayBalanceChange(String username, Integer oldBalance, Integer newBalance, String reason) {
            this.username = username;
            this.oldBalance = oldBalance;
            this.newBalance = newBalance;
            this.reason = reason;
        }

        public boolean hasChange() {
            return oldBalance != null && newBalance != null && !oldBalance.equals(newBalance);
        }
    }

    //Track holiday balance changes across multiple users
    @Getter
    private static class HolidayBalanceTracker {
        private final List<HolidayBalanceChange> balanceChanges = new ArrayList<>();

        public void recordBalanceChange(String username, Integer oldBalance, Integer newBalance) {
            HolidayBalanceChange change = new HolidayBalanceChange(
                    username,
                    oldBalance,
                    newBalance,
                    "Holiday restored (CO converted to SN on national holiday)"
            );
            balanceChanges.add(change);
        }

        // Get only users who actually had balance changes
        public List<String> getUsersAffected() {
            return balanceChanges.stream()
                    .filter(HolidayBalanceChange::hasChange)
                    .map(HolidayBalanceChange::getUsername)
                    .collect(Collectors.toList());
        }

        // Get total number of actual balance changes (not just attempts)
        public int getTotalBalanceChanges() {
            return (int) balanceChanges.stream()
                    .filter(HolidayBalanceChange::hasChange)
                    .count();
        }

        // Get individual changes for detailed audit logging
        public List<HolidayBalanceChange> getSuccessfulChanges() {
            return balanceChanges.stream()
                    .filter(HolidayBalanceChange::hasChange)
                    .collect(Collectors.toList());
        }
    }


    // Result of entry transformation operation
    @Getter
    private static class EntryTransformationResult {
        private final WorkTimeTable transformedEntry;
        private final String originalType;
        private final String operation;

        public EntryTransformationResult(WorkTimeTable transformedEntry, String originalType, String operation) {
            this.transformedEntry = transformedEntry;
            this.originalType = originalType;
            this.operation = operation;
        }
    }
}