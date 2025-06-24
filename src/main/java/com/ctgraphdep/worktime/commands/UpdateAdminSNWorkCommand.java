package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.List;

/**
 * Command to update admin SN entry with work time
 * Handles setting work hours for national holidays (SN entries)
 * Business Rules:
 * - Only admin can set work time for SN entries
 * - Work time becomes overtime (no regular hours on holidays)
 * - Only full hours are counted (partial hours discarded)
 * - No lunch break deduction for holiday work
 */
public class UpdateAdminSNWorkCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final Integer userId;
    private final LocalDate date;
    private final double workHours;

    public UpdateAdminSNWorkCommand(WorktimeOperationContext context, Integer userId,
                                    LocalDate date, double workHours) {
        super(context);
        this.userId = userId;
        this.date = date;
        this.workHours = workHours;
    }

    @Override
    protected void validate() {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (workHours < 1 || workHours > 24) {
            throw new IllegalArgumentException("SN work hours must be between 1 and 24");
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating admin SN work update: userId=%d, date=%s, hours=%.2f",
                userId, date, workHours));

        // Validate admin permissions
        context.requireAdminPrivileges("update SN work time");

        LoggerUtil.debug(this.getClass(), "Admin SN work update validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing admin SN work update for user %d on %s with %.2f hours",
                userId, date, workHours));

        try {
            int year = date.getYear();
            int month = date.getMonthValue();

            // Load admin entries
            List<WorkTimeTable> adminEntries = context.loadAdminWorktime(year, month);

            // Find existing entry or create new SN entry
            WorkTimeTable entry = context.findEntryByDate(adminEntries, userId, date)
                    .map(existingEntry -> {
                        // If entry exists, check if it's SN or convert it to SN
                        if ("SN".equals(existingEntry.getTimeOffType())) {
                            // Update existing SN entry with new work hours
                            return WorktimeEntityBuilder.updateSNWithWorkTime(existingEntry, workHours);
                        } else {
                            // Convert existing entry to SN with work hours
                            LoggerUtil.info(this.getClass(), String.format(
                                    "Converting existing entry (type: %s) to SN with work time for user %d on %s",
                                    existingEntry.getTimeOffType(), userId, date));
                            return WorktimeEntityBuilder.createSNWithWorkTime(userId, date, workHours);
                        }
                    })
                    .orElseGet(() -> {
                        // Create new SN entry with work hours
                        LoggerUtil.info(this.getClass(), String.format(
                                "Creating new SN work entry for user %d on %s", userId, date));
                        return WorktimeEntityBuilder.createSNWithWorkTime(userId, date, workHours);
                    });

            // Calculate the processed hours for logging
            int processedFullHours = entry.getTotalOvertimeMinutes() / 60;

            LoggerUtil.info(this.getClass(), String.format(
                    "SN work processed: user=%d, date=%s, input=%.2f hours, processed=%d full hours (%d minutes)",
                    userId, date, workHours, processedFullHours, entry.getTotalOvertimeMinutes()));

            // Add or replace in list
            context.addOrReplaceEntry(adminEntries, entry);

            // Save back to admin file
            context.saveAdminWorktime(adminEntries, year, month);

            // Create success message
            String message = String.format("Set SN work time: %.2f hours → %d full hours",
                    workHours, processedFullHours);

            if (workHours != processedFullHours) {
                message += String.format(" (%.1f hours discarded)", workHours - processedFullHours);
            }

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(String.format("admin/%d/%d", year, month))
                    .build();

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully updated SN work time for user %d on %s: %s", userId, date, message));

            return OperationResult.successWithSideEffects(
                    message,
                    getOperationType(),
                    entry,
                    sideEffects
            );

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating SN work time for user %d on %s: %s", userId, date, e.getMessage()), e);
            return OperationResult.failure("Failed to update SN work time: " + e.getMessage(), getOperationType());
        }
    }

    @Override
    protected String getCommandName() {
        return String.format("UpdateAdminSNWork[userId=%d, date=%s, hours=%.2f]", userId, date, workHours);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.ADMIN_UPDATE_SN_WORK;
    }
}