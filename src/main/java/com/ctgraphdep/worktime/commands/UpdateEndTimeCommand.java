package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.merge.status.StatusAssignmentEngine;
import com.ctgraphdep.merge.status.StatusAssignmentResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class UpdateEndTimeCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final String newEndTime; // HH:mm format
    private final int userScheduleHours;

    private UpdateEndTimeCommand(WorktimeOperationContext context, String username,
                                 Integer userId, LocalDate date, String newEndTime, int userScheduleHours) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.newEndTime = newEndTime;
        this.userScheduleHours = userScheduleHours;
    }

    // Create command for user end time update
    public static UpdateEndTimeCommand forUser(WorktimeOperationContext context, String username, Integer userId, LocalDate date, String endTime) {
        // Validate parameters early
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username required for end time update");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID required for end time update");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date required for end time update");
        }

        // Factory handles user lookup and schedule extraction
        Optional<User> userOpt = context.getUser(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        int userScheduleHours = userOpt.get().getSchedule();

        LoggerUtil.debug(UpdateEndTimeCommand.class, String.format(
                "Factory creating UpdateEndTimeCommand: user=%s, schedule=%dh, date=%s, endTime=%s",
                username, userScheduleHours, date, endTime));

        return new UpdateEndTimeCommand(context, username, userId, date, endTime, userScheduleHours);
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
            // Use UserOwnDataAccessor for user's own data
            WorktimeDataAccessor accessor = context.getDataAccessor(username);

            // Load user entries using accessor
            List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);
            if (entries == null) {
                entries = new java.util.ArrayList<>();
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

            // Parse and set new end time
            LocalDateTime endTime = parseEndTime(newEndTime);
            entry.setDayEndTime(endTime);

            // Apply appropriate calculation based on day type
            if (isSpecialDay) {
                applySpecialDayCalculation(entry, originalTimeOffType);
            } else {
                applyRegularDayCalculation(entry);
            }

            // Ensure timeOffType is preserved
            entry.setTimeOffType(originalTimeOffType);

            // AUTO-UPDATE ZS (Short Day) based on completion status
            checkAndUpdateShortDayStatus(entry, originalTimeOffType);

            LoggerUtil.info(this.getClass(), String.format("Updated end time for %s: start=%s, end=%s, timeOffType=%s, regular=%d, overtime=%d, lunch=%s", date,
                    entry.getDayStartTime() != null ? entry.getDayStartTime().toLocalTime() : "null",
                    entry.getDayEndTime() != null ? entry.getDayEndTime().toLocalTime() : "null",
                    entry.getTimeOffType(),
                    entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                    entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0,
                    entry.isLunchBreakDeducted()));

            // FIXED: Always use UPDATE_END_TIME operation type, regardless of timeOffType
            StatusAssignmentResult statusResult = StatusAssignmentEngine.assignStatus(
                    entry,
                    context.getCurrentUser().getRole(),
                    getOperationType()  // Always UPDATE_END_TIME
            );

            if (!statusResult.isSuccess()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Status assignment failed: %s", statusResult.getMessage()));
                return OperationResult.failure("Cannot update end time: " + statusResult.getMessage(), getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Status assigned: %s → %s", statusResult.getOriginalStatus(), statusResult.getNewStatus()));

            // Replace entry in list
            replaceEntry(entries, entry);

            // Save using accessor
            accessor.writeWorktimeWithStatus(username, entries, year, month, context.getCurrentUser().getRole());

            // Create success message
            String message = String.format("End time updated to %s", endTime != null ? endTime.toLocalTime() : "null");

            if (isSpecialDay) {
                int overtimeHours = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() / 60 : 0;
                message += String.format(" (%s day: %d overtime hours)", originalTimeOffType, overtimeHours);
            }

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(createFilePathId(username, year, month))
                    .build();

            LoggerUtil.info(this.getClass(), String.format("Successfully updated end time for %s on %s: %s", username, date, message));

            return OperationResult.successWithSideEffects(message, getOperationType(), entry, sideEffects);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating end time for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to update end time: " + e.getMessage(), getOperationType());
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

        // PRESERVED: Use existing recalculateWorkTime for regular days
        WorktimeEntityBuilder.recalculateWorkTime(entry, userScheduleHours);

        LoggerUtil.debug(this.getClass(), String.format("Regular day calculation complete: regular=%d, overtime=%d, lunch=%s",
                entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0,
                entry.isLunchBreakDeducted()));
    }

    // Parse time string in HH:mm format and combine with date
    private LocalDateTime parseEndTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }

        LocalTime time = parseTimeString(timeString.trim());
        return date.atTime(time);
    }

    // Parse time string to LocalTime
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

    /**
     * Auto-update ZS (Short Day) based on completion status.
     * ZS is automatically managed when day completion status changes:
     * - Day becomes complete → Remove ZS
     * - Day becomes incomplete (and has no other time-off) → Create/Update ZS
     *
     * Logic:
     * 1. Check if day is complete (totalWorkedMinutes >= schedule)
     * 2. If complete AND has ZS → Remove ZS
     * 3. If incomplete AND has no time-off (or has ZS) → Create/Update ZS with missing hours
     *
     * @param entry The worktime entry to check
     * @param originalTimeOffType The original timeOffType before calculations
     */
    private void checkAndUpdateShortDayStatus(WorkTimeTable entry, String originalTimeOffType) {
        boolean isDayComplete = isDayComplete(entry);
        int rawWorkedMinutes = entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0;
        int adjustedWorkedMinutes = CalculateWorkHoursUtil.calculateAdjustedMinutes(rawWorkedMinutes, userScheduleHours);
        int scheduleMinutes = userScheduleHours * 60;
        boolean hasZS = originalTimeOffType != null && originalTimeOffType.startsWith("ZS-");

        if (isDayComplete) {
            // Day is complete - remove ZS if it exists
            if (hasZS) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Day is now complete for %s (raw: %d min, adjusted: %d min, schedule: %d min). Auto-removing %s",
                        date, rawWorkedMinutes, adjustedWorkedMinutes, scheduleMinutes, originalTimeOffType));
                entry.setTimeOffType(null);
            }
        } else {
            // Day is incomplete - create/update ZS if no other time-off type
            boolean hasOtherTimeOff = originalTimeOffType != null && !originalTimeOffType.trim().isEmpty() && !hasZS;

            if (!hasOtherTimeOff) {
                // Calculate missing hours using ADJUSTED minutes
                int missingMinutes = scheduleMinutes - adjustedWorkedMinutes;
                int missingHours = (int) Math.ceil(missingMinutes / 60.0);
                String newZS = "ZS-" + missingHours;

                if (!newZS.equals(originalTimeOffType)) {
                    LoggerUtil.info(this.getClass(), String.format(
                            "Day is incomplete for %s (raw: %d min, adjusted: %d min, schedule: %d min). Auto-updating ZS: %s → %s",
                            date, rawWorkedMinutes, adjustedWorkedMinutes, scheduleMinutes,
                            originalTimeOffType != null ? originalTimeOffType : "none", newZS));
                    entry.setTimeOffType(newZS);
                }
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Day is incomplete but has other time-off (%s). Not creating ZS.",
                        originalTimeOffType));
            }
        }
    }

    /**
     * Check if the day is complete (reached schedule).
     * A day is complete if:
     * - Has both start and end time
     * - ADJUSTED worked minutes >= schedule (schedule * 60 minutes)
     *
     * IMPORTANT: Uses adjusted minutes (after lunch deduction) for accurate comparison
     *
     * @param entry The worktime entry to check
     * @return true if day is complete, false otherwise
     */
    private boolean isDayComplete(WorkTimeTable entry) {
        // Must have both start and end time
        if (entry.getDayStartTime() == null || entry.getDayEndTime() == null) {
            return false;
        }

        // IMPORTANT: Use ADJUSTED minutes (after lunch deduction) for ZS calculation
        int rawWorkedMinutes = entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0;
        int adjustedWorkedMinutes = CalculateWorkHoursUtil.calculateAdjustedMinutes(rawWorkedMinutes, userScheduleHours);
        int scheduleMinutes = userScheduleHours * 60;

        return adjustedWorkedMinutes >= scheduleMinutes;
    }
}