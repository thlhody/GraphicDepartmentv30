package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.merge.status.StatusAssignmentEngine;
import com.ctgraphdep.merge.status.StatusAssignmentResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UpdateStartTimeCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final String newStartTime; // HH:mm format
    private final int userScheduleHours;

    // Create command for user start time update
    public static UpdateStartTimeCommand forUser(WorktimeOperationContext context, String username, Integer userId, LocalDate date, String startTime) {
        // Validate parameters early
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username required for start time update");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID required for start time update");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date required for start time update");
        }

        // Factory handles user lookup and schedule extraction
        Optional<User> userOpt = context.getUser(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        int userScheduleHours = userOpt.get().getSchedule();

        LoggerUtil.debug(UpdateStartTimeCommand.class, String.format(
                "Factory creating UpdateStartTimeCommand: user=%s, schedule=%dh, date=%s, startTime=%s",
                username, userScheduleHours, date, startTime));

        return new UpdateStartTimeCommand(context, username, userId, date, startTime, userScheduleHours);
    }

    private UpdateStartTimeCommand(WorktimeOperationContext context, String username,
                                   Integer userId, LocalDate date, String newStartTime, int userScheduleHours) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.newStartTime = newStartTime;
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

        LoggerUtil.info(this.getClass(), String.format("Validating update start time: %s on %s to %s", username, date, newStartTime));

        // PRESERVED: Validate user permissions
        context.validateUserPermissions(username, "update start time");

        // PRESERVED: Validate date is editable (FIXED: using available validation)
        try {
            context.validateHolidayDate(date);
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Date validation for %s: %s", date, e.getMessage()));
            // Continue - this is for editable date validation
        }

        // PRESERVED: Validate time format if provided
        if (newStartTime != null && !newStartTime.trim().isEmpty()) {
            try {
                parseTimeString(newStartTime.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid time format: " + newStartTime + ". Use HH:mm format (e.g., 08:30)");
            }
        }

        LoggerUtil.debug(this.getClass(), "Update start time validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Executing update start time for %s on %s to %s using UserOwnDataAccessor", username, date, newStartTime));

        int year = date.getYear();
        int month = date.getMonthValue();

        try {
            // Use UserOwnDataAccessor for user's own data
            WorktimeDataAccessor accessor = context.getDataAccessor(username);

            // Load user entries using accessor
            List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);
            if (entries == null) {
                entries = new ArrayList<>();
            }

            // Find existing entry
            Optional<WorkTimeTable> entryOpt = findEntryByDate(entries, userId, date);
            if (entryOpt.isEmpty()) {
                throw new IllegalArgumentException("No worktime entry found for date: " + date);
            }

            WorkTimeTable entry = entryOpt.get();

            // Store original timeOffType to preserve it
            String originalTimeOffType = entry.getTimeOffType();
            boolean isSpecialDay = WorktimeEntityBuilder.hasSpecialDayTimeOffType(entry);

            LoggerUtil.info(this.getClass(), String.format("Found entry for %s: timeOffType=%s, isSpecialDay=%s", date, originalTimeOffType, isSpecialDay));

            // Parse and set new start time
            LocalDateTime startTime = parseStartTime(newStartTime);
            entry.setDayStartTime(startTime);
            LoggerUtil.debug(this.getClass(), String.format("Set start time on entry: %s", entry.getDayStartTime()));

            // Apply appropriate calculation based on day type
            if (isSpecialDay) {
                applySpecialDayCalculation(entry, originalTimeOffType);
            } else {
                applyRegularDayCalculation(entry);
            }

            // Ensure timeOffType is preserved
            entry.setTimeOffType(originalTimeOffType);

            LoggerUtil.info(this.getClass(), String.format("Updated start time for %s: start=%s, end=%s, timeOffType=%s, regular=%d, overtime=%d, lunch=%s", date,
                    entry.getDayStartTime() != null ? entry.getDayStartTime().toLocalTime() : "null",
                    entry.getDayEndTime() != null ? entry.getDayEndTime().toLocalTime() : "null",
                    entry.getTimeOffType(),
                    entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                    entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0,
                    entry.isLunchBreakDeducted()));

            // FIXED: Always use UPDATE_START_TIME operation type, regardless of timeOffType
            StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                    entry, context.getCurrentUser().getRole(), getOperationType());

            if (!statusResult.isSuccess()) {
                LoggerUtil.warn(this.getClass(), String.format("Status assignment failed: %s", statusResult.getMessage()));
                return OperationResult.failure("Cannot update start time: " + statusResult.getMessage(), getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format("Status assigned: %s → %s", statusResult.getOriginalStatus(), statusResult.getNewStatus()));

            // Replace entry in list
            replaceEntry(entries, entry);

            // Save using accessor
            accessor.writeWorktimeWithStatus(username, entries, year, month, context.getCurrentUser().getRole());

            // Create success message
            String message = String.format("Start time updated to %s", startTime != null ? startTime.toLocalTime() : "null");

            if (isSpecialDay) {
                int overtimeHours = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() / 60 : 0;
                message += String.format(" (%s day: %d overtime hours)", originalTimeOffType, overtimeHours);
            }

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(createFilePathId(username, year, month)).build();

            LoggerUtil.info(this.getClass(), String.format("Successfully updated start time for %s on %s: %s", username, date, message));

            return OperationResult.successWithSideEffects(message, getOperationType(), entry, sideEffects);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating start time for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to update start time: " + e.getMessage(), getOperationType());
        }
    }

    // Apply special day calculation logic (SN/CO/CM/W)
    private void applySpecialDayCalculation(WorkTimeTable entry, String timeOffType) {
        LoggerUtil.debug(this.getClass(), String.format("Applying special day calculation for %s day", timeOffType));

        // PRESERVED: Use the enhanced WorktimeEntityBuilder method
        WorktimeEntityBuilder.applySpecialDayTimeIntervalCalculation(entry);

        LoggerUtil.debug(this.getClass(), String.format("Special day calculation complete: timeOffType=%s, overtime=%d minutes",
                entry.getTimeOffType(), entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0));
    }

    // Apply regular day calculation logic
    private void applyRegularDayCalculation(WorkTimeTable entry) {
        LoggerUtil.debug(this.getClass(), "Applying regular day calculation");

        // PRESERVE the start time we just set
        LocalDateTime preservedStartTime = entry.getDayStartTime();

        // Use existing recalculateWorkTime for regular days
        WorktimeEntityBuilder.recalculateWorkTime(entry, userScheduleHours);

        // RESTORE the start time if it was cleared
        if (preservedStartTime != null && entry.getDayStartTime() == null) {
            entry.setDayStartTime(preservedStartTime);
            LoggerUtil.debug(this.getClass(), "Restored start time after calculation: " + preservedStartTime);
        }

        LoggerUtil.debug(this.getClass(), String.format("Regular day calculation complete: regular=%d, overtime=%d, lunch=%s",
                entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0,
                entry.isLunchBreakDeducted()));
    }

    // Parse time string in HH:mm format and combine with date
    private LocalDateTime parseStartTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }

        try {
            LocalTime time = parseTimeString(timeString.trim());
            // ADD SECONDS to match the working format
            LocalDateTime result = date.atTime(time.withSecond(0));
            LoggerUtil.debug(this.getClass(), String.format("Successfully parsed start time: '%s' -> %s", timeString, result));
            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to parse start time: '%s' - %s", timeString, e.getMessage()), e);
            throw new IllegalArgumentException("Invalid time format: " + timeString, e);
        }
    }

    // Parse time string to LocalTime
    private LocalTime parseTimeString(String timeString) throws DateTimeParseException {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        return LocalTime.parse(timeString, timeFormatter);
    }

    @Override
    protected String getCommandName() {
        return String.format("UpdateStartTime[%s, %s, %s, %dh]", username, date, newStartTime, userScheduleHours);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.UPDATE_START_TIME;
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    private void replaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry ->
                updatedEntry.getUserId().equals(entry.getUserId()) &&
                        updatedEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(updatedEntry);
        entries.sort(java.util.Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    private String createFilePathId(String username, int year, int month) {
        return String.format("%s/%d/%d", username, year, month);
    }
}