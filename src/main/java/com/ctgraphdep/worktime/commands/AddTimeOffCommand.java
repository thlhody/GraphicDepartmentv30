package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.merge.status.StatusAssignmentEngine;
import com.ctgraphdep.merge.status.StatusAssignmentResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import lombok.Getter;

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
    private final com.ctgraphdep.worktime.rules.TimeOffOperationRules timeOffRules;

    private AddTimeOffCommand(WorktimeOperationContext context, String username, Integer userId,
                              List<LocalDate> dates, String timeOffType,
                              com.ctgraphdep.worktime.rules.TimeOffOperationRules timeOffRules) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.dates = dates;
        this.timeOffType = timeOffType;
        this.timeOffRules = timeOffRules;
    }

    // FACTORY METHOD: Create command for user time off addition
    public static AddTimeOffCommand forUser(WorktimeOperationContext context, String username, Integer userId, List<LocalDate> dates, String timeOffType,
                                           com.ctgraphdep.worktime.rules.TimeOffOperationRules timeOffRules) {
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

        return new AddTimeOffCommand(context, username, userId, dates, timeOffType, timeOffRules);
    }

    // FACTORY METHOD: Create command for admin time off addition
    public static AddTimeOffCommand forAdmin(WorktimeOperationContext context, String targetUsername, Integer targetUserId, List<LocalDate> dates, String timeOffType,
                                            com.ctgraphdep.worktime.rules.TimeOffOperationRules timeOffRules) {
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

        return new AddTimeOffCommand(context, targetUsername, targetUserId, dates, timeOffType, timeOffRules);
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

    @Override
    protected OperationResult executeCommand() {
        try {
            LoggerUtil.info(this.getClass(), String.format("=== STARTING AddTimeOffCommand for %s: %d dates, type=%s ===", username, dates.size(), timeOffType));

            String currentUserRole = context.getCurrentUser().getRole();

            // PART 1: Filter dates against existing time off conflicts (not work time conflicts)
            DateFilterResult filterResult = filterDatesForTimeOffConflicts(dates, username, userId);
            List<LocalDate> validDates = filterResult.getValidDates();
            List<LocalDate> skippedConflicts = filterResult.getSkippedConflicts();

            if (validDates.isEmpty()) {
                return createNoValidDatesResult(dates.size(), skippedConflicts);
            }

            LoggerUtil.info(this.getClass(), String.format("Date filtering complete: %d valid dates, %d conflicts skipped",
                    validDates.size(), skippedConflicts.size()));

            // PART 2: Process dates by month
            var datesByMonth = groupDatesByMonth(validDates);
            ProcessingResult processingResult = processDatesByMonth(datesByMonth, currentUserRole);

            if (!processingResult.success()) {
                return processingResult.getFailureResult();
            }

            // PART 3: Handle side effects and create final result
            return createFinalResult(processingResult, skippedConflicts, validDates);

        } catch (Exception e) {
            return handleExecutionError(e);
        }
    }

    // ========================================================================
    // PART 1: DATE FILTERING (MODIFIED TO ALLOW WORK TIME)
    // ========================================================================

    private DateFilterResult filterDatesForTimeOffConflicts(List<LocalDate> requestedDates, String username, Integer userId) {
        LoggerUtil.info(this.getClass(), String.format("Filtering %d requested dates against existing TIME OFF conflicts only", requestedDates.size()));

        List<LocalDate> validDates = new ArrayList<>();
        List<LocalDate> skippedConflicts = new ArrayList<>();

        // Group dates by month to minimize file reads
        Map<YearMonth, List<LocalDate>> datesByMonth = requestedDates.stream()
                .collect(Collectors.groupingBy(date -> YearMonth.of(date.getYear(), date.getMonthValue())));

        LoggerUtil.debug(this.getClass(), String.format("Checking %d months for TIME OFF conflicts: %s",
                datesByMonth.size(), datesByMonth.keySet()));

        for (Map.Entry<YearMonth, List<LocalDate>> monthGroup : datesByMonth.entrySet()) {
            YearMonth yearMonth = monthGroup.getKey();
            List<LocalDate> monthDates = monthGroup.getValue();

            // Load existing entries for this month
            WorktimeDataAccessor accessor = context.getDataAccessor(username);
            List<WorkTimeTable> existingEntries = accessor.readWorktime(username, yearMonth.getYear(), yearMonth.getMonthValue());

            if (existingEntries == null) {
                existingEntries = new ArrayList<>();
            }

            LoggerUtil.debug(this.getClass(), String.format("Loaded %d existing entries for TIME OFF conflict checking", existingEntries.size()));

            // Check each date in this month for conflicts
            for (LocalDate date : monthDates) {
                // STEP 1: Check if date has existing WORK TIME that cannot be overlaid
                boolean hasWorkTime = existingEntries.stream()
                        .anyMatch(entry -> entry.getUserId().equals(userId) &&
                                entry.getWorkDate().equals(date) &&
                                (entry.getDayStartTime() != null || entry.getDayEndTime() != null));

                if (hasWorkTime && timeOffRules.requiresClearWorktime(timeOffType)) {
                    String reason = timeOffRules.getCannotAddOverWorktimeReason(timeOffType);
                    skippedConflicts.add(date);
                    LoggerUtil.info(this.getClass(), String.format("WORK TIME CONFLICT: Skipping %s - %s", date, reason));
                    continue; // Skip this date and move to next
                }

                // STEP 1.5: Special check - W (Weekend) cannot be changed to other types
                boolean hasWeekendLock = existingEntries.stream()
                        .anyMatch(entry -> entry.getUserId().equals(userId) &&
                                entry.getWorkDate().equals(date) &&
                                WorkCode.WEEKEND_CODE.equalsIgnoreCase(entry.getTimeOffType()) &&
                                !WorkCode.WEEKEND_CODE.equalsIgnoreCase(timeOffType));

                if (hasWeekendLock) {
                    skippedConflicts.add(date);
                    LoggerUtil.info(this.getClass(), String.format(
                            "WEEKEND LOCK: Skipping %s - Weekend work (W) cannot be changed to %s. Remove W first, then add new type.",
                            date, timeOffType));
                    continue; // Skip this date and move to next
                }

                // STEP 2: Check for TIME OFF conflicts only
                boolean hasTimeOffConflict = existingEntries.stream()
                        .anyMatch(entry -> entry.getUserId().equals(userId) &&
                                entry.getWorkDate().equals(date) &&
                                entry.getTimeOffType() != null &&
                                !entry.getTimeOffType().trim().isEmpty() &&
                                !entry.getTimeOffType().equals(timeOffType)); // NEW: Allow overwriting same type

                if (hasTimeOffConflict) {
                    Optional<WorkTimeTable> conflictingEntry = existingEntries.stream()
                            .filter(entry -> entry.getUserId().equals(userId) && entry.getWorkDate().equals(date))
                            .findFirst();

                    String conflictType = conflictingEntry.map(WorkTimeTable::getTimeOffType).orElse("unknown");
                    skippedConflicts.add(date);
                    LoggerUtil.info(this.getClass(), String.format("TIME OFF CONFLICT: Skipping %s - already has different time off %s (requesting %s)",
                            date, conflictType, timeOffType));
                } else {
                    validDates.add(date);
                    LoggerUtil.debug(this.getClass(), String.format("VALID: Date %s has no time off conflicts (work time allowed)", date));
                }
            }
        }

        LoggerUtil.info(this.getClass(), String.format("Time off conflict filtering complete: %d valid, %d conflicts. Valid dates: %s",
                validDates.size(), skippedConflicts.size(), validDates));

        return new DateFilterResult(validDates, skippedConflicts);
    }

    // ========================================================================
    // PART 2: DATE PROCESSING WITH SMART LOGIC
    // ========================================================================

    private Map<YearMonth, List<LocalDate>> groupDatesByMonth(List<LocalDate> validDates) {
        var datesByMonth = validDates.stream()
                .collect(Collectors.groupingBy(date -> YearMonth.of(date.getYear(), date.getMonthValue())));

        LoggerUtil.info(this.getClass(), String.format("Grouped valid dates into %d months: %s",
                datesByMonth.size(), datesByMonth.keySet()));

        return datesByMonth;
    }

    private ProcessingResult processDatesByMonth(Map<YearMonth, List<LocalDate>> datesByMonth, String currentUserRole) {
        List<WorkTimeTable> allProcessedEntries = new ArrayList<>();
        int totalEntriesCreated = 0;
        int totalEntriesModified = 0;

        for (var monthEntry : datesByMonth.entrySet()) {
            YearMonth yearMonth = monthEntry.getKey();
            List<LocalDate> monthDates = monthEntry.getValue();

            LoggerUtil.info(this.getClass(), String.format("Processing month %s with %d valid dates: %s",
                    yearMonth, monthDates.size(), monthDates));

            try {
                // Process this month
                MonthProcessingResult monthResult = processMonthDates(yearMonth, monthDates, currentUserRole);

                if (!monthResult.isSuccess()) {
                    return ProcessingResult.failure(monthResult.getErrorMessage());
                }

                // Aggregate results
                allProcessedEntries.addAll(monthResult.getProcessedEntries());
                totalEntriesCreated += monthResult.getEntriesCreated();
                totalEntriesModified += monthResult.getEntriesModified();

            } catch (Exception e) {
                String errorMessage = String.format("Error processing month %s: %s", yearMonth, e.getMessage());
                LoggerUtil.error(this.getClass(), errorMessage, e);
                return ProcessingResult.failure(errorMessage);
            }
        }

        LoggerUtil.info(this.getClass(), String.format("Month processing complete: %d created, %d modified, %d total processed",
                totalEntriesCreated, totalEntriesModified, allProcessedEntries.size()));

        return ProcessingResult.success(allProcessedEntries, totalEntriesCreated, totalEntriesModified);
    }

    private MonthProcessingResult processMonthDates(YearMonth yearMonth, List<LocalDate> monthDates, String currentUserRole) {
        // Load entries for the month
        WorktimeDataAccessor accessor = context.getDataAccessor(username);
        List<WorkTimeTable> entries = accessor.readWorktime(username, yearMonth.getYear(), yearMonth.getMonthValue());

        if (entries == null) {
            entries = new ArrayList<>();
        }

        LoggerUtil.info(this.getClass(), String.format("Loaded %d existing entries for %s - %d/%d",
                entries.size(), username, yearMonth.getYear(), yearMonth.getMonthValue()));

        List<WorkTimeTable> processedEntries = new ArrayList<>();
        int entriesCreated = 0;
        int entriesModified = 0;

        // SMART LOGIC: Process each date with existing vs new entry handling
        for (LocalDate date : monthDates) {
            Optional<WorkTimeTable> existingEntry = findEntryByDate(entries, userId, date);

            if (existingEntry.isEmpty()) {
                // PATH 1: No entry exists - create new time off entry
                WorkTimeTable newEntry = createNewTimeOffEntry(date, currentUserRole);
                if (newEntry != null) {
                    addOrReplaceEntry(entries, newEntry);
                    processedEntries.add(newEntry);
                    entriesCreated++;
                    LoggerUtil.info(this.getClass(), String.format("CREATED new %s entry for %s on %s",
                            timeOffType, username, date));
                }
            } else {
                // PATH 2: Entry exists - add time off and convert to special day logic
                WorkTimeTable modifiedEntry = addTimeOffToExistingEntry(existingEntry.get(), currentUserRole);
                if (modifiedEntry != null) {
                    addOrReplaceEntry(entries, modifiedEntry);
                    processedEntries.add(modifiedEntry);
                    entriesModified++;
                    LoggerUtil.info(this.getClass(), String.format("MODIFIED existing entry for %s on %s - added %s and converted work to overtime",
                            username, date, timeOffType));
                }
            }
        }

        // Save updated entries
        try {
            accessor.writeWorktimeWithStatus(username, entries, yearMonth.getYear(), yearMonth.getMonthValue(), currentUserRole);
            LoggerUtil.info(this.getClass(), String.format("Successfully saved entries for %s - %d/%d",
                    username, yearMonth.getYear(), yearMonth.getMonthValue()));
        } catch (Exception writeError) {
            LoggerUtil.error(this.getClass(), String.format("FAILED to save entries for %s - %d/%d: %s",
                    username, yearMonth.getYear(), yearMonth.getMonthValue(), writeError.getMessage()), writeError);
            throw writeError;
        }

        return MonthProcessingResult.success(processedEntries, entriesCreated, entriesModified);
    }

    // ========================================================================
    // SMART LOGIC METHODS
    // ========================================================================

    private WorkTimeTable createNewTimeOffEntry(LocalDate date, String currentUserRole) {
        LoggerUtil.info(this.getClass(), String.format("Creating new %s entry for %s on %s", timeOffType, username, date));

        WorkTimeTable timeOffEntry;

        // SPECIAL HANDLING for CR (Recovery Leave) - Create full work day paid from overtime
        if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(timeOffType)) {
            int userSchedule = context.getCurrentUser().getSchedule();
            LoggerUtil.info(this.getClass(), String.format(
                "Creating CR (Recovery Leave) entry as full work day for %s on %s: schedule=%d hours",
                username, date, userSchedule));

            // Create recovery leave entry with full schedule hours
            // This creates a regular work entry (totalWorkedMinutes = schedule + lunch)
            // Overtime deduction happens during monthly consolidation
            timeOffEntry = WorktimeEntityBuilder.createRecoveryLeaveEntry(userId, date, userSchedule);

            LoggerUtil.info(this.getClass(), String.format("CR entry created: %s - %d minutes worked (%dh schedule + 30min lunch), will be deducted from overtime balance",
                date, timeOffEntry.getTotalWorkedMinutes(), userSchedule));
        }
        // SPECIAL HANDLING for CN (Unpaid Leave) - Simple time off entry, no work
        else if (WorkCode.UNPAID_LEAVE_CODE.equalsIgnoreCase(timeOffType)) {
            LoggerUtil.info(this.getClass(), String.format(
                "Creating CN (Unpaid Leave) entry for %s on %s: no work, no deductions",
                username, date));

            // Create simple time off entry with no work time
            timeOffEntry = WorktimeEntityBuilder.createTimeOffEntry(userId, date, timeOffType);

            LoggerUtil.info(this.getClass(), String.format("CN entry created: %s - unpaid leave, no work time", date));
        }
        // STANDARD HANDLING for CO/CM/SN/ZS
        else {
            timeOffEntry = WorktimeEntityBuilder.createTimeOffEntry(userId, date, timeOffType);
        }

        // Assign status
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(timeOffEntry, currentUserRole, getOperationType());

        if (!statusResult.isSuccess()) {
            LoggerUtil.warn(this.getClass(), String.format("Status assignment failed for new %s entry on %s: %s", timeOffType, date, statusResult.getMessage()));
            return null;
        }

        LoggerUtil.info(this.getClass(), String.format("Status assigned for new %s entry on %s: %s → %s (Role: %s)",
                timeOffType, date, statusResult.getOriginalStatus(), statusResult.getNewStatus(), currentUserRole));

        return timeOffEntry;
    }

    private WorkTimeTable addTimeOffToExistingEntry(WorkTimeTable existingEntry, String currentUserRole) {
        LocalDate date = existingEntry.getWorkDate();

        LoggerUtil.info(this.getClass(), String.format(
                "Adding %s to existing work entry for %s on %s - converting work time to special day overtime",
                timeOffType, username, date));

        // Log original state
        LoggerUtil.debug(this.getClass(), String.format("Original entry state: start=%s, end=%s, regular=%d, overtime=%d, timeOff=%s",
                existingEntry.getDayStartTime(), existingEntry.getDayEndTime(),
                existingEntry.getTotalWorkedMinutes() != null ? existingEntry.getTotalWorkedMinutes() : 0,
                existingEntry.getTotalOvertimeMinutes() != null ? existingEntry.getTotalOvertimeMinutes() : 0,
                existingEntry.getTimeOffType()));

        // STEP 1: Set time off type FIRST
        existingEntry.setTimeOffType(timeOffType);

        // STEP 2: Apply special day calculation ONLY if entry has work times
        // For tombstone entries (no work time), just setting timeOffType is enough
        if (existingEntry.getDayStartTime() != null && existingEntry.getDayEndTime() != null) {
            LoggerUtil.info(this.getClass(), String.format("Converting work time to %s overtime for %s on %s", timeOffType, username, date));

            // Apply special day calculation - converts work to overtime
            WorktimeEntityBuilder.applySpecialDayTimeIntervalCalculation(existingEntry);

            // Verify times are still present after calculation
            if (existingEntry.getDayStartTime() == null || existingEntry.getDayEndTime() == null) {
                LoggerUtil.error(this.getClass(), String.format(
                        "BUG: Special day calculation cleared start/end times for %s entry on %s!", timeOffType, date));
                return null;
            }
        } else {
            LoggerUtil.info(this.getClass(), String.format(
                    "Adding %s to tombstone entry (no work time) for %s on %s", timeOffType, username, date));
            // Tombstone entry - just ensure work fields are zeroed
            existingEntry.setTotalWorkedMinutes(0);
            existingEntry.setTotalOvertimeMinutes(0);
            existingEntry.setLunchBreakDeducted(false);
        }

        // STEP 3: Assign status
        StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                existingEntry, currentUserRole, getOperationType());

        if (!statusResult.isSuccess()) {
            LoggerUtil.warn(this.getClass(), String.format("Status assignment failed for modified %s entry on %s: %s",
                    timeOffType, date, statusResult.getMessage()));
            return null;
        }

        // Log final state
        if (existingEntry.getDayStartTime() != null && existingEntry.getDayEndTime() != null) {
            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully converted work day to %s day for %s on %s: start=%s, end=%s, regular=%d → overtime=%d (Status: %s → %s)",
                    timeOffType, username, date,
                    existingEntry.getDayStartTime().toLocalTime(),
                    existingEntry.getDayEndTime().toLocalTime(),
                    0, // Special days have 0 regular work
                    existingEntry.getTotalOvertimeMinutes() != null ? existingEntry.getTotalOvertimeMinutes() : 0,
                    statusResult.getOriginalStatus(), statusResult.getNewStatus()));
        } else {
            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully added %s to tombstone entry for %s on %s (Status: %s → %s)",
                    timeOffType, username, date,
                    statusResult.getOriginalStatus(), statusResult.getNewStatus()));
        }

        return existingEntry;
    }

    // ========================================================================
    // PART 3: FINAL RESULT CREATION
    // ========================================================================

    private OperationResult createFinalResult(ProcessingResult processingResult, List<LocalDate> skippedConflicts, List<LocalDate> validDates) {

        int totalProcessed = processingResult.totalEntriesCreated() + processingResult.totalEntriesModified();

        // Handle holiday balance for CO (vacation)
        boolean balanceUpdated = false;
        if (WorkCode.TIME_OFF_CODE.equalsIgnoreCase(timeOffType) && totalProcessed > 0) {
            int actualDaysNeeded = context.calculateActualVacationDaysNeeded(validDates);
            if (actualDaysNeeded > 0) {
                balanceUpdated = context.updateHolidayBalance(-actualDaysNeeded);
            }
        }

        // Create comprehensive success message
        String finalMessage = createSuccessMessage(processingResult, skippedConflicts, balanceUpdated);
        LoggerUtil.info(this.getClass(), finalMessage);

        // Add time off to tracker
        if (totalProcessed > 0) {
            context.addTimeOffRequestsToTracker(username, userId, validDates, timeOffType, validDates.get(0).getYear());
        }

        LoggerUtil.info(this.getClass(), String.format(
                "=== COMPLETED AddTimeOffCommand: Created %d, Modified %d, Skipped %d conflicts ===",
                processingResult.totalEntriesCreated(), processingResult.totalEntriesModified(), skippedConflicts.size()));

        return OperationResult.success(finalMessage, getOperationType(), processingResult.allProcessedEntries());
    }

    private String createSuccessMessage(ProcessingResult processingResult, List<LocalDate> skippedConflicts, boolean balanceUpdated) {
        StringBuilder messageBuilder = new StringBuilder();

        int totalProcessed = processingResult.totalEntriesCreated() + processingResult.totalEntriesModified();

        if (processingResult.totalEntriesModified() > 0) {
            // Mixed operations
            messageBuilder.append(String.format("Successfully processed %d %s time off requests for %s: %d new entries, %d work days converted to %s+overtime",
                    totalProcessed, timeOffType.toUpperCase(), username,
                    processingResult.totalEntriesCreated(), processingResult.totalEntriesModified(), timeOffType));
        } else {
            // Only new entries
            messageBuilder.append(String.format("Successfully added %d %s time off entries for %s",
                    totalProcessed, timeOffType.toUpperCase(), username));
        }

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

    // ========================================================================
    // SUPPORTING RESULT CLASSES
    // ========================================================================

    private record ProcessingResult(boolean success, List<WorkTimeTable> allProcessedEntries, int totalEntriesCreated, int totalEntriesModified, String errorMessage) {
        private ProcessingResult(boolean success, List<WorkTimeTable> allProcessedEntries, int totalEntriesCreated, int totalEntriesModified, String errorMessage) {
            this.success = success;
            this.allProcessedEntries = allProcessedEntries != null ? allProcessedEntries : new ArrayList<>();
            this.totalEntriesCreated = totalEntriesCreated;
            this.totalEntriesModified = totalEntriesModified;
            this.errorMessage = errorMessage;
        }

        public static ProcessingResult success(List<WorkTimeTable> allProcessedEntries, int totalEntriesCreated, int totalEntriesModified) {
            return new ProcessingResult(true, allProcessedEntries, totalEntriesCreated, totalEntriesModified, null);
        }

        public static ProcessingResult failure(String errorMessage) {
            return new ProcessingResult(false, null, 0, 0, errorMessage);
        }

        public OperationResult getFailureResult() {
            return OperationResult.failure(errorMessage, OperationResult.OperationType.ADD_TIME_OFF);
        }
    }

    @Getter
    private static class MonthProcessingResult {
        private final boolean success;
        private final List<WorkTimeTable> processedEntries;
        private final int entriesCreated;
        private final int entriesModified;
        private final String errorMessage;

        private MonthProcessingResult(boolean success, List<WorkTimeTable> processedEntries,
                                      int entriesCreated, int entriesModified, String errorMessage) {
            this.success = success;
            this.processedEntries = processedEntries != null ? processedEntries : new ArrayList<>();
            this.entriesCreated = entriesCreated;
            this.entriesModified = entriesModified;
            this.errorMessage = errorMessage;
        }

        public static MonthProcessingResult success(List<WorkTimeTable> processedEntries, int entriesCreated, int entriesModified) {
            return new MonthProcessingResult(true, processedEntries, entriesCreated, entriesModified, null);
        }
    }

    // ========================================================================
    // ERROR HANDLING
    // ========================================================================

    private OperationResult handleExecutionError(Exception e) {
        String errorMessage = String.format("Failed to add %s time off for %s: %s", timeOffType, username, e.getMessage());
        LoggerUtil.error(this.getClass(), String.format("=== FAILED AddTimeOffCommand for %s: %s ===", username, e.getMessage()), e);
        return OperationResult.failure(errorMessage, getOperationType());
    }

    private OperationResult createNoValidDatesResult(int totalRequested, List<LocalDate> skippedConflicts) {
        String message = String.format("No valid dates remaining after conflict resolution. " +
                        "Total requested: %d, Skipped conflicts: %d (%s)",
                totalRequested, skippedConflicts.size(), skippedConflicts);
        LoggerUtil.warn(this.getClass(), message);
        return OperationResult.failure(message, getOperationType());
    }

    // ========================================================================
    // HELPER METHODS (PRESERVED BUSINESS LOGIC)
    // ========================================================================

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

    @Override
    protected String getCommandName() {
        return String.format("AddTimeOff[user=%s, dates=%d, type=%s]", username, dates.size(), timeOffType);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.ADD_TIME_OFF;
    }
}