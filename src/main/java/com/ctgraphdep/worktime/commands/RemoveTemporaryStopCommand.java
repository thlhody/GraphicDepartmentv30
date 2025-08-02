package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.StatusAssignmentEngine;
import com.ctgraphdep.worktime.util.StatusAssignmentResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REFACTORED: Command to remove temporary stop time from a worktime entry using accessor pattern.
 * Uses UserOwnDataAccessor for user's own data operations.
 * BUSINESS LOGIC PRESERVED: Resets temporaryStopCount to 0 and totalTemporaryStopMinutes to 0
 * with recalculation using user schedule.
 */
public class RemoveTemporaryStopCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final int userScheduleHours;

    public RemoveTemporaryStopCommand(WorktimeOperationContext context, String username, Integer userId,
                                      LocalDate date, int userScheduleHours) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.userScheduleHours = userScheduleHours;
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

        LoggerUtil.info(this.getClass(), String.format("Validating remove temporary stop: %s on %s", username, date));

        // PRESERVED: Original validation logic
        context.validateUserPermissions(username, "update temporary stop");

        try {
            context.validateHolidayDate(date);
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Date validation for %s: %s", date, e.getMessage()));
            // Continue - this is for editable date validation
        }

        LoggerUtil.debug(this.getClass(), "Remove temporary stop validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Executing remove temporary stop command for %s on %s using UserOwnDataAccessor", username, date));

        int year = date.getYear();
        int month = date.getMonthValue();

        try {
            // NEW: Use UserOwnDataAccessor for user's own data
            WorktimeDataAccessor accessor = context.getDataAccessor(username);

            // Load current month entries using accessor
            List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);
            if (entries == null) {
                entries = new ArrayList<>();
            }

            // PRESERVED: Find existing entry logic
            Optional<WorkTimeTable> existingEntryOpt = findEntryByDate(entries, userId, date);

            if (existingEntryOpt.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "No entry found for %s on %s - nothing to remove", username, date));
                return OperationResult.success("No temporary stop to remove", getOperationType());
            }

            WorkTimeTable entry = existingEntryOpt.get();

            // PRESERVED: Check if there's actually a temporary stop to remove
            if (entry.getTotalTemporaryStopMinutes() == null || entry.getTotalTemporaryStopMinutes() == 0) {
                LoggerUtil.info(this.getClass(), String.format(
                        "No temporary stop found for %s on %s - nothing to remove", username, date));
                return OperationResult.success("No temporary stop to remove", getOperationType(), entry);
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "Current entry: tempStopCount=%d, tempStopMinutes=%d",
                    entry.getTemporaryStopCount(), entry.getTotalTemporaryStopMinutes()));

            // PRESERVED: Remove temporary stop using WorktimeEntityBuilder (SAME BUSINESS LOGIC)
            WorkTimeTable updatedEntry = WorktimeEntityBuilder.removeTemporaryStop(entry, userScheduleHours);

            StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(updatedEntry, context.getCurrentUser().getRole(), getOperationType());

            if (!statusResult.isSuccess()) {
                LoggerUtil.warn(this.getClass(), String.format("Status assignment failed: %s", statusResult.getMessage()));
                return OperationResult.failure("Cannot remove temporary stop: " + statusResult.getMessage(), getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format("Status assigned: %s → %s", statusResult.getOriginalStatus(), statusResult.getNewStatus()));

            LoggerUtil.info(this.getClass(), String.format(
                    "Updated entry: tempStopCount=%d, tempStopMinutes=%d, totalWorkedMinutes=%d",
                    updatedEntry.getTemporaryStopCount(), updatedEntry.getTotalTemporaryStopMinutes(),
                    updatedEntry.getTotalWorkedMinutes()));

            // PRESERVED: Replace entry in list
            replaceEntry(entries, updatedEntry);

            // NEW: Save using accessor instead of deprecated context methods
            accessor.writeWorktimeWithStatus(username, entries, year, month, context.getCurrentUser().getRole());

            // PRESERVED: Create same success message and side effects
            String message = "Temporary stop removed";

            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(createFilePathId(username, year, month))
                    .build();

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully removed temporary stop for %s on %s", username, date));

            return OperationResult.successWithSideEffects(message, getOperationType(), updatedEntry, sideEffects);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Failed to remove temporary stop for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to remove temporary stop: " + e.getMessage(), getOperationType());
        }
    }

    /**
     * PRESERVED: Find entry by date and user ID - same logic
     */
    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    /**
     * PRESERVED: Replace entry in list - same logic
     */
    private void replaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry ->
                updatedEntry.getUserId().equals(entry.getUserId()) &&
                        updatedEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(updatedEntry);
        entries.sort(java.util.Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    /**
     * PRESERVED: Create file path ID - same logic as original context method
     */
    private String createFilePathId(String username, int year, int month) {
        return String.format("%s/%d/%d", username, year, month);
    }

    @Override
    protected String getCommandName() {
        return String.format("RemoveTemporaryStop[%s, %s, %dh]", username, date, userScheduleHours);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.REMOVE_TEMPORARY_STOP;
    }
}