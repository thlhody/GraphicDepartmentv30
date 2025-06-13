package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Command to transform a work entry to time off entry (atomic operation)
 */
public class TransformWorkToTimeOffCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final String timeOffType;

    public TransformWorkToTimeOffCommand(WorktimeOperationContext context, String username,
                                         Integer userId, LocalDate date, String timeOffType) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.timeOffType = timeOffType;
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
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            throw new IllegalArgumentException("Time off type cannot be null or empty");
        }

        // Validate time off type
        WorktimeEntityBuilder.ValidationRules.validateTimeOffType(timeOffType);

        // Validate user permissions
        context.validateUserPermissions(username, "transform work to time off");

        // Validate date is editable
        context.validateDateEditable(date, null);

        // Validate time off date constraints
        WorktimeEntityBuilder.ValidationRules.validateTimeOffDate(date, timeOffType);

        if ("CO".equalsIgnoreCase(timeOffType)) {
            context.validateSufficientHolidayBalance(1, "transform work to vacation");
        }

        // Validate admin permissions for SN
        if ("SN".equalsIgnoreCase(timeOffType) && !context.isCurrentUserAdmin()) {
            throw new IllegalArgumentException("Only admin can create national holidays");
        }
    }
    @Override
    protected OperationResult executeCommand() {
        int year = date.getYear();
        int month = date.getMonthValue();
        List<WorkTimeTable> entries = context.loadUserWorktime(username, year, month);

        // Find existing entry
        Optional<WorkTimeTable> existingEntry = context.findEntryByDate(entries, userId, date);
        if (existingEntry.isEmpty()) {
            return OperationResult.failure("No work entry found to transform", getOperationType());
        }

        WorkTimeTable entry = existingEntry.get();

        // Check if entry already has time off
        if (entry.getTimeOffType() != null && !entry.getTimeOffType().trim().isEmpty()) {
            return OperationResult.failure(String.format(
                            "Entry already has time off type: %s. Remove it first.", entry.getTimeOffType()),
                    getOperationType());
        }

        // Check if entry has work time worth transforming
        boolean hasWorkTime = (entry.getDayStartTime() != null || entry.getDayEndTime() != null ||
                (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0));

        if (!hasWorkTime) {
            return OperationResult.failure("Entry has no work time to transform", getOperationType());
        }

        // Transform using builder
        WorkTimeTable transformedEntry = WorktimeEntityBuilder.transformWorkToTimeOff(entry, timeOffType);
        context.addOrReplaceEntry(entries, transformedEntry);
        context.saveUserWorktime(username, entries, year, month);

        // Handle holiday balance update for CO
        boolean balanceUpdated = false;
        Integer oldBalance = context.getCurrentHolidayBalance();
        if ("CO".equalsIgnoreCase(timeOffType)) {

            // FIXED: Use context method to check for national holiday
            if (context.shouldProcessVacationDay(date, "convert work to vacation")) {
                balanceUpdated = context.updateHolidayBalance(-1); // Reduce by 1 day
            }
        }

        // Update success message
        String message = String.format("Transformed work entry to %s time off", timeOffType.toUpperCase());

        if ("CO".equalsIgnoreCase(timeOffType)) {
            if (balanceUpdated) {
                message += String.format(". Holiday balance: %d → %d (vacation day used)",
                        oldBalance, context.getCurrentHolidayBalance());
            } else if (context.isExistingNationalHoliday(date)) {
                message += " (no vacation day deducted - national holiday)";
            }
        }
        // Invalidate cache
        context.invalidateTimeOffCache(username, year);

        // Create side effects
        OperationResult.OperationSideEffects.Builder sideEffectsBuilder =
                OperationResult.OperationSideEffects.builder()
                        .fileUpdated(context.createFilePathId(username, year, month))
                        .cacheInvalidated(context.createCacheKey(username, year));

        if (balanceUpdated) {
            sideEffectsBuilder.holidayBalanceChanged(oldBalance, context.getCurrentHolidayBalance());
        }

        return OperationResult.successWithSideEffects(
                message, // <- Use the detailed message instead!
                getOperationType(),
                transformedEntry,
                sideEffectsBuilder.build()
        );
    }

    @Override
    protected String getCommandName() {
        return String.format("TransformWorkToTimeOff[%s, %s, %s]", username, date, timeOffType);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.TRANSFORM_WORK_TO_TIME_OFF;
    }
}