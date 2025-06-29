package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Command to add/update temporary stop time for a worktime entry
 * Handles user editing of temporary stop minutes with automatic recalculation of work time
 * Follows same pattern as UpdateStartTimeCommand and UpdateEndTimeCommand
 */
public class AddTemporaryStopCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final Integer tempStopMinutes; // Minutes to set
    private final int userScheduleHours;

    public AddTemporaryStopCommand(WorktimeOperationContext context, String username,
                                   Integer userId, LocalDate date, Integer tempStopMinutes, int userScheduleHours) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.tempStopMinutes = tempStopMinutes;
        this.userScheduleHours = userScheduleHours; // ← ADD THIS
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
        if (tempStopMinutes == null || tempStopMinutes < 0) {
            throw new IllegalArgumentException("Temporary stop minutes must be non-negative");
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating add temporary stop: %s on %s, %d minutes", username, date, tempStopMinutes));

        // Validate user permissions
        context.validateUserPermissions(username, "update temporary stop");

        // Validate date is editable (not today, not future) - same as start/end time
        context.validateDateEditable(date, null);

        // Validate temporary stop constraints
        validateTemporaryStopConstraints();
    }

    private void validateTemporaryStopConstraints() {
        // 12 hour cap (720 minutes)
        if (tempStopMinutes > 720) {
            throw new IllegalArgumentException("Temporary stop cannot exceed 12 hours (720 minutes)");
        }

        // Check against total elapsed time if entry exists
        int year = date.getYear();
        int month = date.getMonthValue();
        List<WorkTimeTable> entries = context.loadUserWorktime(username, year, month);

        context.findEntryByDate(entries, userId, date).ifPresent(entry -> {
            // Check if entry has start and end times to calculate elapsed time
            if (entry.getDayStartTime() != null && entry.getDayEndTime() != null) {
                Duration elapsed = Duration.between(entry.getDayStartTime(), entry.getDayEndTime());
                long totalElapsedMinutes = elapsed.toMinutes();

                if (tempStopMinutes > totalElapsedMinutes) {
                    throw new IllegalArgumentException(String.format(
                            "Temporary stop (%d minutes) cannot exceed total elapsed time (%d minutes)",
                            tempStopMinutes, totalElapsedMinutes));
                }
            }
        });
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing add temporary stop command for %s on %s: %d minutes", username, date, tempStopMinutes));

        try {
            // Load current month entries
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = context.loadUserWorktime(username, year, month);

            // Find existing entry or create new one
            WorkTimeTable entry = context.findEntryByDate(entries, userId, date)
                    .orElseGet(() -> WorktimeEntityBuilder.createNewEntry(userId, date));

            LoggerUtil.debug(this.getClass(), String.format(
                    "Current entry: tempStopCount=%d, tempStopMinutes=%d",
                    entry.getTemporaryStopCount(),
                    entry.getTotalTemporaryStopMinutes()));

            // Update temporary stop using entity builder
            WorkTimeTable updatedEntry = WorktimeEntityBuilder.updateTemporaryStop(entry, tempStopMinutes, userScheduleHours);

            LoggerUtil.info(this.getClass(), String.format(
                    "Updated entry: tempStopCount=%d, tempStopMinutes=%d, totalWorkedMinutes=%d",
                    updatedEntry.getTemporaryStopCount(),
                    updatedEntry.getTotalTemporaryStopMinutes(),
                    updatedEntry.getTotalWorkedMinutes()));

            // Add or replace in list
            context.addOrReplaceEntry(entries, updatedEntry);

            // Save back to file
            context.saveUserWorktime(username, entries, year, month);

            // Create success message
            String message = String.format("Temporary stop updated to %d minutes", tempStopMinutes);

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(context.createFilePathId(username, year, month))
                    .build();

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully updated temporary stop for %s on %s: %s", username, date, message));

            return OperationResult.successWithSideEffects(
                    message,
                    getOperationType(),
                    updatedEntry,
                    sideEffects
            );

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Failed to update temporary stop for %s on %s: %s",
                    username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to update temporary stop: " + e.getMessage(), getOperationType());
        }
    }

    @Override
    protected String getCommandName() {
        return String.format("AddTemporaryStop[%s, %s, %d, %dh]", username, date, tempStopMinutes, userScheduleHours);
    }
    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.UPDATE_TEMPORARY_STOP;
    }
}