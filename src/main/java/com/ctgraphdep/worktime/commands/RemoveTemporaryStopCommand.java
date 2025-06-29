package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.List;

/**
 * Command to remove temporary stop time from a worktime entry
 * Resets temporaryStopCount to 0 and totalTemporaryStopMinutes to 0
 * Follows same pattern as UpdateStartTimeCommand and UpdateEndTimeCommand
 */
public class RemoveTemporaryStopCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final int userScheduleHours;

    public RemoveTemporaryStopCommand(WorktimeOperationContext context, String username,
                                      Integer userId, LocalDate date, int userScheduleHours) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
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

        LoggerUtil.info(this.getClass(), String.format(
                "Validating remove temporary stop: %s on %s", username, date));

        // Validate user permissions
        context.validateUserPermissions(username, "update temporary stop");

        // Validate date is editable (not today, not future) - same as start/end time
        context.validateDateEditable(date, null);
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing remove temporary stop command for %s on %s", username, date));

        try {
            // Load current month entries
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = context.loadUserWorktime(username, year, month);

            // Find existing entry
            WorkTimeTable entry = context.findEntryByDate(entries, userId, date).orElse(null);

            if (entry == null) {
                LoggerUtil.info(this.getClass(), String.format(
                        "No entry found for %s on %s - nothing to remove", username, date));
                return OperationResult.success(
                        "No temporary stop to remove",
                        getOperationType());
            }

            // Check if there's actually a temporary stop to remove
            if (entry.getTotalTemporaryStopMinutes() == null || entry.getTotalTemporaryStopMinutes() == 0) {
                LoggerUtil.info(this.getClass(), String.format(
                        "No temporary stop found for %s on %s - nothing to remove", username, date));
                return OperationResult.success(
                        "No temporary stop to remove",
                        getOperationType(),
                        entry);
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "Current entry: tempStopCount=%d, tempStopMinutes=%d",
                    entry.getTemporaryStopCount(),
                    entry.getTotalTemporaryStopMinutes()));

            // Remove temporary stop using WorktimeEntityBuilder
            WorkTimeTable updatedEntry = WorktimeEntityBuilder.removeTemporaryStop(entry, userScheduleHours);

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
            String message = "Temporary stop removed";

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(context.createFilePathId(username, year, month))
                    .build();

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully removed temporary stop for %s on %s", username, date));

            return OperationResult.successWithSideEffects(
                    message,
                    getOperationType(),
                    updatedEntry,
                    sideEffects
            );

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Failed to remove temporary stop for %s on %s: %s",
                    username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to remove temporary stop: " + e.getMessage(), getOperationType());
        }
    }

    @Override
    protected String getCommandName() {
        return String.format("RemoveTemporaryStop[%s, %s, %dh]", username, date, userScheduleHours); // ← UPDATED
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.REMOVE_TEMPORARY_STOP;
    }
}