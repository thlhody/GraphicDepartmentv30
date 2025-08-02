package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.StatusAssignmentEngine;
import com.ctgraphdep.worktime.util.StatusAssignmentResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class AddTemporaryStopCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final Integer tempStopMinutes;
    private final int userScheduleHours;

    private AddTemporaryStopCommand(WorktimeOperationContext context, String username, Integer userId,
                                   LocalDate date, Integer tempStopMinutes, int userScheduleHours) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.tempStopMinutes = tempStopMinutes;
        this.userScheduleHours = userScheduleHours;
    }

    // Create command for user temporary stop update
    public static AddTemporaryStopCommand forUser(WorktimeOperationContext context, String username, Integer userId, LocalDate date, Integer tempStopMinutes) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username required for temporary stop update");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID required for temporary stop update");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date required for temporary stop update");
        }
        if (tempStopMinutes == null || tempStopMinutes < 0) {
            throw new IllegalArgumentException("Valid temporary stop minutes required");
        }

        Optional<User> userOpt = context.getUser(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        int userScheduleHours = userOpt.get().getSchedule();

        return new AddTemporaryStopCommand(context, username, userId, date, tempStopMinutes, userScheduleHours);
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

        // Validate user permissions (only own data)
        context.validateUserPermissions(username, "add temporary stop");

        LoggerUtil.info(this.getClass(), String.format(
                "Validating temporary stop: user=%s, date=%s, minutes=%d", username, date, tempStopMinutes));
    }

    @Override
    protected OperationResult executeCommand() {
        int year = date.getYear();
        int month = date.getMonthValue();

        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Adding temporary stop for %s on %s: %d minutes using UserOwnDataAccessor", username, date, tempStopMinutes));

            // Use UserOwnDataAccessor for user's own data
            WorktimeDataAccessor accessor = context.getDataAccessor(username);

            // Load user entries
            List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);
            if (entries == null) {
                return OperationResult.failure("No worktime entries found for the month", getOperationType());
            }

            // Find existing entry for the date
            Optional<WorkTimeTable> existingEntryOpt = findEntryByDate(entries, userId, date);
            if (existingEntryOpt.isEmpty()) {
                return OperationResult.failure("No work entry found for the specified date", getOperationType());
            }

            WorkTimeTable entry = existingEntryOpt.get();

            // Validate entry has start time
            if (entry.getDayStartTime() == null) {
                return OperationResult.failure("Cannot add temporary stop: entry has no start time", getOperationType());
            }

            // Update temporary stop using builder - ORIGINAL LOGIC
            WorkTimeTable updatedEntry = WorktimeEntityBuilder.updateTemporaryStop(entry, tempStopMinutes, userScheduleHours);


            StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(updatedEntry, context.getCurrentUser().getRole(), getOperationType());

            if (!statusResult.isSuccess()) {
                LoggerUtil.warn(this.getClass(), String.format("Status assignment failed: %s", statusResult.getMessage()));
                return OperationResult.failure("Cannot update temporary stop: " + statusResult.getMessage(), getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format("Status assigned: %s → %s", statusResult.getOriginalStatus(), statusResult.getNewStatus()));

            // Replace entry in list
            replaceEntry(entries, updatedEntry);

            // Save using accessor
            accessor.writeWorktimeWithStatus(username, entries, year, month, context.getCurrentUser().getRole());

            String message = String.format("Updated temporary stop to %d minutes for %s on %s", tempStopMinutes, username, date);
            LoggerUtil.info(this.getClass(), message);

            return OperationResult.success(message, getOperationType(), updatedEntry);

        } catch (Exception e) {
            String errorMessage = String.format("Failed to add temporary stop for %s on %s: %s", username, date, e.getMessage());
            LoggerUtil.error(this.getClass(), errorMessage, e);
            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    // Find entry by date and user ID - UTILITY METHOD
    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream().filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    // Replace entry in list - UTILITY METHOD
    private void replaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry -> updatedEntry.getUserId().equals(entry.getUserId()) &&
                        updatedEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(updatedEntry);
        entries.sort(java.util.Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));
    }

    @Override
    protected String getCommandName() {
        return String.format("AddTemporaryStop[user=%s, date=%s, minutes=%d]", username, date, tempStopMinutes);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.ADD_TEMPORARY_STOP;
    }
}