package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Command to update start time for a worktime entry
 * Handles simple field editing with automatic recalculation of work time
 */
public class UpdateStartTimeCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final String newStartTime; // HH:mm format

    public UpdateStartTimeCommand(WorktimeOperationContext context, String username,
                                  Integer userId, LocalDate date, String newStartTime) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.newStartTime = newStartTime;
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
                "Validating update start time: %s on %s to %s", username, date, newStartTime));

        // Validate user permissions
        context.validateUserPermissions(username, "update start time");

        // Validate date is editable (not today, not future)
        context.validateDateEditable(date, null);

        // Validate time format if provided
        if (newStartTime != null && !newStartTime.trim().isEmpty()) {
            try {
                parseTimeString(newStartTime.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid time format: " + newStartTime +
                        ". Please use HH:mm format (e.g., 09:00)");
            }
        }

        LoggerUtil.debug(this.getClass(), "Update start time validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing update start time command for %s on %s to %s", username, date, newStartTime));

        try {
            // Load current month entries
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = context.loadUserWorktime(username, year, month);

            // Find existing entry or create new one
            WorkTimeTable entry = context.findEntryByDate(entries, userId, date)
                    .orElseGet(() -> WorktimeEntityBuilder.createNewEntry(userId, date));

            // Parse new start time
            LocalDateTime startTime = parseStartTime(newStartTime);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Current entry: start=%s, end=%s, totalMinutes=%d",
                    entry.getDayStartTime() != null ? entry.getDayStartTime().toLocalTime() : "null",
                    entry.getDayEndTime() != null ? entry.getDayEndTime().toLocalTime() : "null",
                    entry.getTotalWorkedMinutes()));

            // Update start time using entity builder (handles validation and recalculation)
            WorkTimeTable updatedEntry = WorktimeEntityBuilder.updateStartTime(entry, startTime);

            LoggerUtil.info(this.getClass(), String.format(
                    "Updated entry: start=%s, end=%s, totalMinutes=%d, lunchBreak=%s",
                    updatedEntry.getDayStartTime() != null ? updatedEntry.getDayStartTime().toLocalTime() : "null",
                    updatedEntry.getDayEndTime() != null ? updatedEntry.getDayEndTime().toLocalTime() : "null",
                    updatedEntry.getTotalWorkedMinutes(),
                    updatedEntry.isLunchBreakDeducted()));

            // Add or replace in list
            context.addOrReplaceEntry(entries, updatedEntry);

            // Save back to file
            context.saveUserWorktime(username, entries, year, month);

            // Create success message
            String message = String.format("Start time updated to %s",
                    startTime != null ? startTime.toLocalTime() : "null");

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(context.createFilePathId(username, year, month))
                    .build();

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully updated start time for %s on %s: %s", username, date, message));

            return OperationResult.successWithSideEffects(
                    message,
                    getOperationType(),
                    updatedEntry,
                    sideEffects
            );

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating start time for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to update start time: " + e.getMessage(), getOperationType());
        }
    }

    /**
     * Parse time string in HH:mm format and combine with date
     */
    private LocalDateTime parseStartTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }

        LocalTime time = parseTimeString(timeString.trim());
        return date.atTime(time);
    }

    /**
     * Parse time string to LocalTime
     */
    private LocalTime parseTimeString(String timeString) throws DateTimeParseException {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        return LocalTime.parse(timeString, timeFormatter);
    }

    @Override
    protected String getCommandName() {
        return String.format("UpdateStartTime[%s, %s, %s]", username, date, newStartTime);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.UPDATE_START_TIME;
    }
}