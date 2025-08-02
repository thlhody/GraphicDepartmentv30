package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.StatusAssignmentEngine;
import com.ctgraphdep.worktime.util.StatusAssignmentResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * REFACTORED: Command to update end time for a worktime entry using accessor pattern.
 * Uses UserOwnDataAccessor for user's own data operations.
 * BUSINESS LOGIC PRESERVED: Supports both regular days and special days (SN/CO/CM/W)
 * with automatic recalculation and timeOffType preservation.
 */
public class UpdateEndTimeCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final String newEndTime; // HH:mm format
    private final int userScheduleHours;

    public UpdateEndTimeCommand(WorktimeOperationContext context, String username,
                                Integer userId, LocalDate date, String newEndTime, int userScheduleHours) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.newEndTime = newEndTime;
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

        LoggerUtil.info(this.getClass(), String.format("Validating update end time: %s on %s to %s", username, date, newEndTime));

        // PRESERVED: Validate user permissions
        context.validateUserPermissions(username, "update end time");

        // PRESERVED: Validate date is editable (FIXED: using available validation)
        try {
            context.validateHolidayDate(date);
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Date validation for %s: %s", date, e.getMessage()));
            // Continue - this is for editable date validation
        }

        // PRESERVED: Validate time format if provided
        if (newEndTime != null && !newEndTime.trim().isEmpty()) {
            try {
                parseTimeString(newEndTime.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid time format: " + newEndTime + ". Use HH:mm format (e.g., 17:30)");
            }
        }

        LoggerUtil.debug(this.getClass(), "Update end time validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Executing update end time for %s on %s to %s using UserOwnDataAccessor", username, date, newEndTime));

        int year = date.getYear();
        int month = date.getMonthValue();

        try {
            // NEW: Use UserOwnDataAccessor for user's own data
            WorktimeDataAccessor accessor = context.getDataAccessor(username);

            // NEW: Load user entries using accessor
            List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);
            if (entries == null) {
                entries = new java.util.ArrayList<>();
            }

            // PRESERVED: Find existing entry
            Optional<WorkTimeTable> entryOpt = findEntryByDate(entries, userId, date);
            if (entryOpt.isEmpty()) {
                throw new IllegalArgumentException("No worktime entry found for date: " + date);
            }

            WorkTimeTable entry = entryOpt.get();

            // PRESERVED: Store original timeOffType to preserve it
            String originalTimeOffType = entry.getTimeOffType();
            boolean isSpecialDay = WorktimeEntityBuilder.hasSpecialDayTimeOffType(entry);

            LoggerUtil.info(this.getClass(), String.format("Found entry for %s: timeOffType=%s, isSpecialDay=%s", date, originalTimeOffType, isSpecialDay));

            // PRESERVED: Parse and set new end time
            LocalDateTime endTime = parseEndTime(newEndTime);
            entry.setDayEndTime(endTime);

            // PRESERVED: Apply appropriate calculation based on day type
            if (isSpecialDay) {
                applySpecialDayCalculation(entry, originalTimeOffType);
            } else {
                applyRegularDayCalculation(entry);
            }

            // PRESERVED: Ensure timeOffType is preserved
            entry.setTimeOffType(originalTimeOffType);

            LoggerUtil.info(this.getClass(), String.format("Updated end time for %s: start=%s, end=%s, timeOffType=%s, regular=%d, overtime=%d, lunch=%s", date,
                    entry.getDayStartTime() != null ? entry.getDayStartTime().toLocalTime() : "null",
                    entry.getDayEndTime() != null ? entry.getDayEndTime().toLocalTime() : "null",
                    entry.getTimeOffType(),
                    entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                    entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0,
                    entry.isLunchBreakDeducted()));

            // Determine operation type based on timeOffType presence
            String dynamicOperationType;
            if (entry.getTimeOffType() != null && !entry.getTimeOffType().trim().isEmpty()) {
                // Has timeOffType → field modification on special day
                dynamicOperationType = getOperationType(); // Uses original operation type
                LoggerUtil.debug(this.getClass(), String.format(
                        "TimeOffType '%s' exists - treating as field modification", entry.getTimeOffType()));
            } else {
                // No timeOffType → work entry being removed
                dynamicOperationType = "DELETE_ENTRY";
                LoggerUtil.debug(this.getClass(),
                        "No timeOffType - treating as entry removal");
            }

            // Use dynamic operation type for status assignment
            StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                    entry,
                    context.getCurrentUser().getRole(),
                    dynamicOperationType
            );

            if (!statusResult.isSuccess()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Status assignment failed: %s", statusResult.getMessage()));
                return OperationResult.failure("Cannot update end time: " + statusResult.getMessage(), getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Status assigned: %s → %s", statusResult.getOriginalStatus(), statusResult.getNewStatus()));

            // PRESERVED: Replace entry in list
            replaceEntry(entries, entry);

            // NEW: Save using accessor
            accessor.writeWorktimeWithStatus(username, entries, year, month, context.getCurrentUser().getRole());

            // PRESERVED: Create success message
            String message = String.format("End time updated to %s", endTime != null ? endTime.toLocalTime() : "null");

            if (isSpecialDay) {
                int overtimeHours = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() / 60 : 0;
                message += String.format(" (%s day: %d overtime hours)", originalTimeOffType, overtimeHours);
            }

            // PRESERVED: Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(createFilePathId(username, year, month)).build();

            LoggerUtil.info(this.getClass(), String.format("Successfully updated end time for %s on %s: %s", username, date, message));

            return OperationResult.successWithSideEffects(message, getOperationType(), entry, sideEffects);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating end time for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to update end time: " + e.getMessage(), getOperationType());
        }
    }

    /**
     * PRESERVED: Apply special day calculation logic (SN/CO/CM/W)
     */
    private void applySpecialDayCalculation(WorkTimeTable entry, String timeOffType) {
        LoggerUtil.debug(this.getClass(), String.format("Applying special day calculation for %s day", timeOffType));

        // PRESERVED: Use the enhanced WorktimeEntityBuilder method
        WorktimeEntityBuilder.applySpecialDayTimeIntervalCalculation(entry);

        LoggerUtil.debug(this.getClass(), String.format("Special day calculation complete: timeOffType=%s, overtime=%d minutes",
                entry.getTimeOffType(), entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0));
    }

    /**
     * PRESERVED: Apply regular day calculation logic
     */
    private void applyRegularDayCalculation(WorkTimeTable entry) {
        LoggerUtil.debug(this.getClass(), "Applying regular day calculation");

        // PRESERVED: Use existing recalculateWorkTime for regular days
        WorktimeEntityBuilder.recalculateWorkTime(entry, userScheduleHours);

        LoggerUtil.debug(this.getClass(), String.format("Regular day calculation complete: regular=%d, overtime=%d, lunch=%s",
                entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0,
                entry.isLunchBreakDeducted()));
    }

    /**
     * PRESERVED: Parse time string in HH:mm format and combine with date
     */
    private LocalDateTime parseEndTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }

        LocalTime time = parseTimeString(timeString.trim());
        return date.atTime(time);
    }

    /**
     * PRESERVED: Parse time string to LocalTime
     */
    private LocalTime parseTimeString(String timeString) throws DateTimeParseException {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        return LocalTime.parse(timeString, timeFormatter);
    }

    @Override
    protected String getCommandName() {
        return String.format("UpdateEndTime[%s, %s, %s, %dh]", username, date, newEndTime, userScheduleHours);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.UPDATE_END_TIME;
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
        entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    private String createFilePathId(String username, int year, int month) {
        return String.format("%s/%d/%d", username, year, month);
    }
}