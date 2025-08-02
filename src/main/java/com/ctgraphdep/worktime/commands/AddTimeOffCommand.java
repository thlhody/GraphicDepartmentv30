package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.StatusAssignmentEngine;
import com.ctgraphdep.worktime.util.StatusAssignmentResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class AddTimeOffCommand extends WorktimeOperationCommand<List<WorkTimeTable>> {
    private final String username;
    private final Integer userId;
    private final List<LocalDate> dates;
    private final String timeOffType;

    private AddTimeOffCommand(WorktimeOperationContext context, String username, Integer userId,
                             List<LocalDate> dates, String timeOffType) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.dates = dates;
        this.timeOffType = timeOffType;
    }

    // FACTORY METHOD: Create command for user time off addition
    public static AddTimeOffCommand forUser(WorktimeOperationContext context, String username, Integer userId, List<LocalDate> dates, String timeOffType) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username required for time off addition");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID required for time off addition");
        }
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("Dates list required for time off addition");
        }
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            throw new IllegalArgumentException("Time off type required for time off addition");
        }

        return new AddTimeOffCommand(context, username, userId, dates, timeOffType);
    }

    // FACTORY METHOD: Create command for admin time off addition
    public static AddTimeOffCommand forAdmin(WorktimeOperationContext context, String targetUsername, Integer targetUserId, List<LocalDate> dates, String timeOffType) {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Target username required for admin time off addition");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("Target user ID required for admin time off addition");
        }
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("Dates list required for admin time off addition");
        }
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            throw new IllegalArgumentException("Time off type required for admin time off addition");
        }

        return new AddTimeOffCommand(context, targetUsername, targetUserId, dates, timeOffType);
    }

    @Override
    protected void validate() {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("Dates list cannot be null or empty");
        }
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            throw new IllegalArgumentException("Time off type cannot be null or empty");
        }

        // Validate permissions
        context.validateUserPermissions(username, "add time off");

        // Validate time off type permissions for SN
        if (WorkCode.NATIONAL_HOLIDAY_CODE.equalsIgnoreCase(timeOffType) && !context.isCurrentUserAdmin()) {
            throw new IllegalArgumentException("Only admin can create national holidays");
        }

        // Validate holiday balance for CO (vacation)
        if (WorkCode.TIME_OFF_CODE.equalsIgnoreCase(timeOffType)) {
            int actualDaysNeeded = context.calculateActualVacationDaysNeeded(dates);
            if (actualDaysNeeded > 0) {
                context.validateSufficientHolidayBalance(actualDaysNeeded, "add vacation time off");
            }
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating time off addition: user=%s, dates=%d, type=%s", username, dates.size(), timeOffType));
    }

    // AddTimeOffCommand with Part 2 file-based validation and StatusAssignmentEngine
    @Override
    protected OperationResult executeCommand() {
        try {
            LoggerUtil.info(this.getClass(), String.format("=== STARTING AddTimeOffCommand for %s: %d dates, type=%s ===", username, dates.size(), timeOffType));

            String currentUserRole = context.getCurrentUser().getRole();

            // PART 2 VALIDATION: Load files and filter conflicting dates
            LoggerUtil.info(this.getClass(), "=== PART 2: File-based conflict detection and cleanup ===");

            DateFilterResult filterResult = filterDatesAgainstExistingEntries(dates, username, userId);

            List<LocalDate> validDates = filterResult.getValidDates();
            List<LocalDate> skippedConflicts = filterResult.getSkippedConflicts();

            if (validDates.isEmpty()) {
                String message = String.format("No valid dates remaining after conflict resolution. " +
                                "Total requested: %d, Skipped conflicts: %d (%s)",
                        dates.size(), skippedConflicts.size(), skippedConflicts);
                LoggerUtil.warn(this.getClass(), message);
                return OperationResult.failure(message, getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format("File-based filtering complete: %d valid dates, %d conflicts skipped",
                    validDates.size(), skippedConflicts.size()));

            // Group valid dates by month for efficient processing
            var datesByMonth = validDates.stream()
                    .collect(Collectors.groupingBy(date -> YearMonth.of(date.getYear(), date.getMonthValue())));
            LoggerUtil.info(this.getClass(), String.format(
                    "Grouped valid dates into %d months: %s", datesByMonth.size(), datesByMonth.keySet()));

            List<WorkTimeTable> allCreatedEntries = new ArrayList<>();
            int totalEntriesCreated = 0;

            // Process each month with valid dates
            for (var monthEntry : datesByMonth.entrySet()) {
                YearMonth yearMonth = monthEntry.getKey();
                List<LocalDate> monthDates = monthEntry.getValue();

                LoggerUtil.info(this.getClass(), String.format("Processing month %s with %d valid dates: %s", yearMonth, monthDates.size(), monthDates));

                // Use appropriate accessor based on context
                WorktimeDataAccessor accessor = context.getDataAccessor(username);
                LoggerUtil.info(this.getClass(), String.format("Using accessor type: %s", accessor.getClass().getSimpleName()));

                // Load entries for the month
                List<WorkTimeTable> entries = accessor.readWorktime(username, yearMonth.getYear(), yearMonth.getMonthValue());
                LoggerUtil.info(this.getClass(), String.format("Loaded %d existing entries for %s - %d/%d",
                        entries != null ? entries.size() : 0, username, yearMonth.getYear(), yearMonth.getMonthValue()));

                if (entries == null) {
                    entries = new ArrayList<>();
                }

                // Create time off entries for valid dates only
                for (LocalDate date : monthDates) {
                    LoggerUtil.info(this.getClass(), String.format("Creating %s entry for %s on %s (conflict-free)",
                            timeOffType, username, date));

                    // Double-check: should not exist at this point due to filtering
                    if (findEntryByDate(entries, userId, date).isEmpty()) {
                        WorkTimeTable timeOffEntry = WorktimeEntityBuilder.createTimeOffEntry(userId, date, timeOffType);

                        // Use StatusAssignmentEngine instead of hardcoded status assignment
                        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                                timeOffEntry,
                                currentUserRole,
                                getOperationType()
                        );

                        if (!statusResult.isSuccess()) {
                            LoggerUtil.warn(this.getClass(), String.format(
                                    "Status assignment failed for %s entry on %s: %s",
                                    timeOffType, date, statusResult.getMessage()));
                            return OperationResult.failure("Cannot create time off entry: " + statusResult.getMessage(), getOperationType());
                        }

                        LoggerUtil.info(this.getClass(), String.format(
                                "Status assigned for %s entry on %s: %s → %s (Role: %s)",
                                timeOffType, date, statusResult.getOriginalStatus(), statusResult.getNewStatus(), currentUserRole));

                        addOrReplaceEntry(entries, timeOffEntry);
                        allCreatedEntries.add(timeOffEntry);
                        totalEntriesCreated++;

                        LoggerUtil.debug(this.getClass(), String.format(
                                "Created %s time off entry for %s on %s with status: %s",
                                timeOffType, username, date, timeOffEntry.getAdminSync()));
                    } else {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Unexpected: Entry already exists for %s on %s even after filtering", username, date));
                    }
                }

                // Save updated entries using accessor
                try {
                    accessor.writeWorktimeWithStatus(username, entries, yearMonth.getYear(), yearMonth.getMonthValue(), currentUserRole);
                    LoggerUtil.info(this.getClass(), String.format(
                            "Successfully saved entries for %s - %d/%d", username, yearMonth.getYear(), yearMonth.getMonthValue()));
                } catch (Exception writeError) {
                    LoggerUtil.error(this.getClass(), String.format(
                            "FAILED to save entries for %s - %d/%d: %s",
                            username, yearMonth.getYear(), yearMonth.getMonthValue(), writeError.getMessage()), writeError);
                    throw writeError;
                }
            }

            // Handle holiday balance for CO (vacation) - PRESERVED LOGIC
            boolean balanceUpdated = false;
            if (WorkCode.TIME_OFF_CODE.equalsIgnoreCase(timeOffType) && totalEntriesCreated > 0) {
                int actualDaysNeeded = context.calculateActualVacationDaysNeeded(validDates);
                if (actualDaysNeeded > 0) {
                    balanceUpdated = context.updateHolidayBalance(-actualDaysNeeded);
                }
            }

            // Create comprehensive success message
            String finalMessage = getString(totalEntriesCreated, skippedConflicts, balanceUpdated);
            LoggerUtil.info(this.getClass(), finalMessage);

            // Add time off to tracker
            if (totalEntriesCreated > 0) {
                context.addTimeOffRequestsToTracker(username, userId, validDates, timeOffType, validDates.get(0).getYear());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "=== COMPLETED AddTimeOffCommand: Created %d entries, Skipped %d conflicts ===",
                    totalEntriesCreated, skippedConflicts.size()));

            return OperationResult.success(finalMessage, getOperationType(), allCreatedEntries);

        } catch (Exception e) {
            String errorMessage = String.format("Failed to add %s time off for %s: %s", timeOffType, username, e.getMessage());
            LoggerUtil.error(this.getClass(), String.format(
                    "=== FAILED AddTimeOffCommand for %s: %s ===", username, e.getMessage()), e);
            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    // ========================================================================
    // HELPER METHODS (PRESERVED BUSINESS LOGIC)
    // ========================================================================

    private @NotNull String getString(int totalEntriesCreated, List<LocalDate> skippedConflicts, boolean balanceUpdated) {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(String.format("Successfully added %d %s time off entries for %s",
                totalEntriesCreated, timeOffType.toUpperCase(), username));

        if (!skippedConflicts.isEmpty()) {
            messageBuilder.append(String.format(". Skipped %d conflicting dates: %s",
                    skippedConflicts.size(), skippedConflicts));
        }

        if (WorkCode.TIME_OFF_CODE.equalsIgnoreCase(timeOffType) && balanceUpdated) {
            Integer newBalance = context.getCurrentHolidayBalance();
            messageBuilder.append(String.format(". Holiday balance updated to %d days",
                    newBalance != null ? newBalance : 0));
        }

        return messageBuilder.toString();
    }

    // Filter dates against existing worktime entries (Part 2 validation)
    private DateFilterResult filterDatesAgainstExistingEntries(List<LocalDate> requestedDates, String username, Integer userId) {
        LoggerUtil.info(this.getClass(), String.format("Filtering %d requested dates against existing worktime files", requestedDates.size()));

        List<LocalDate> validDates = new ArrayList<>();
        List<LocalDate> skippedConflicts = new ArrayList<>();

        // Group dates by month to minimize file reads
        Map<YearMonth, List<LocalDate>> datesByMonth = requestedDates.stream()
                .collect(Collectors.groupingBy(date -> YearMonth.of(date.getYear(), date.getMonthValue())));

        LoggerUtil.debug(this.getClass(), String.format("Checking %d months for conflicts: %s",
                datesByMonth.size(), datesByMonth.keySet()));

        for (Map.Entry<YearMonth, List<LocalDate>> monthGroup : datesByMonth.entrySet()) {
            YearMonth yearMonth = monthGroup.getKey();
            List<LocalDate> monthDates = monthGroup.getValue();

            LoggerUtil.debug(this.getClass(), String.format("Loading worktime file for %s - %d/%d to check %d dates",
                    username, yearMonth.getYear(), yearMonth.getMonthValue(), monthDates.size()));

            // Load existing entries for this month
            WorktimeDataAccessor accessor = context.getDataAccessor(username);
            List<WorkTimeTable> existingEntries = accessor.readWorktime(username, yearMonth.getYear(), yearMonth.getMonthValue());

            if (existingEntries == null) {
                existingEntries = new ArrayList<>();
            }

            LoggerUtil.debug(this.getClass(), String.format("Loaded %d existing entries for conflict checking", existingEntries.size()));

            // Check each date in this month
            for (LocalDate date : monthDates) {
                boolean hasConflict = existingEntries.stream()
                        .anyMatch(entry -> entry.getUserId().equals(userId) &&
                                entry.getWorkDate().equals(date) &&
                                entry.getTimeOffType() != null &&
                                !entry.getTimeOffType().trim().isEmpty());

                if (hasConflict) {
                    // Find the conflicting entry for logging
                    Optional<WorkTimeTable> conflictingEntry = existingEntries.stream()
                            .filter(entry -> entry.getUserId().equals(userId) && entry.getWorkDate().equals(date))
                            .findFirst();

                    String conflictType = conflictingEntry.map(WorkTimeTable::getTimeOffType).orElse("unknown");
                    skippedConflicts.add(date);
                    LoggerUtil.info(this.getClass(), String.format("CONFLICT: Skipping %s - already has %s (%s)",
                            date, getTimeOffDescription(conflictType), conflictType));
                } else {
                    validDates.add(date);
                    LoggerUtil.debug(this.getClass(), String.format("CLEAR: Date %s has no conflicts", date));
                }
            }
        }

        LoggerUtil.info(this.getClass(), String.format("File filtering complete: %d valid, %d conflicts. Valid dates: %s",
                validDates.size(), skippedConflicts.size(), validDates));

        return new DateFilterResult(validDates, skippedConflicts);
    }

    // Helper class to return filtering results
    @Getter
    private static class DateFilterResult {
        private final List<LocalDate> validDates;
        private final List<LocalDate> skippedConflicts;

        public DateFilterResult(List<LocalDate> validDates, List<LocalDate> skippedConflicts) {
            this.validDates = validDates;
            this.skippedConflicts = skippedConflicts;
        }
    }

    // Get user-friendly description for time-off types
    private String getTimeOffDescription(String timeOffType) {
        if (timeOffType == null) return "unknown";

        return switch (timeOffType.toUpperCase()) {
            case WorkCode.NATIONAL_HOLIDAY_CODE -> "National Holiday";
            case WorkCode.TIME_OFF_CODE -> "Vacation";
            case WorkCode.MEDICAL_LEAVE_CODE -> "Medical Leave";
            case WorkCode.WEEKEND_CODE -> "Weekend Work";
            default -> timeOffType;
        };
    }

    // Find entry by date and user ID - UTILITY METHOD
    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    // Add or replace entry in list - UTILITY METHOD
    private void addOrReplaceEntry(List<WorkTimeTable> entries, WorkTimeTable newEntry) {
        entries.removeIf(entry ->
                newEntry.getUserId().equals(entry.getUserId()) &&
                        newEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(newEntry);
        entries.sort(java.util.Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    @Override
    protected String getCommandName() {
        return String.format("AddTimeOff[user=%s, dates=%d, type=%s]", username, dates.size(), timeOffType);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.ADD_TIME_OFF;
    }
}