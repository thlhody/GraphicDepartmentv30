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
import com.ctgraphdep.utils.CalculateWorkHoursUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RemoveTemporaryStopCommand extends WorktimeOperationCommand<WorkTimeTable> {
    private final String username;
    private final Integer userId;
    private final LocalDate date;
    private final int userScheduleHours;

    private RemoveTemporaryStopCommand(WorktimeOperationContext context, String username, Integer userId,
                                      LocalDate date, int userScheduleHours) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.date = date;
        this.userScheduleHours = userScheduleHours;
    }

    // Create command for user temporary stop removal
    public static RemoveTemporaryStopCommand forUser(WorktimeOperationContext context, String username, Integer userId, LocalDate date) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username required for temporary stop removal");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID required for temporary stop removal");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date required for temporary stop removal");
        }

        Optional<User> userOpt = context.getUser(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + username);
        }

        int userScheduleHours = userOpt.get().getSchedule();

        return new RemoveTemporaryStopCommand(context, username, userId, date, userScheduleHours);
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
        LoggerUtil.info(this.getClass(), String.format("Executing remove temporary stop command for %s on %s using UserOwnDataAccessor", username, date));

        int year = date.getYear();
        int month = date.getMonthValue();

        try {
            // Use UserOwnDataAccessor for user's own data
            WorktimeDataAccessor accessor = context.getDataAccessor(username);

            // Load current month entries using accessor
            List<WorkTimeTable> entries = accessor.readWorktime(username, year, month);
            if (entries == null) {
                entries = new ArrayList<>();
            }

            // Find existing entry logic
            Optional<WorkTimeTable> existingEntryOpt = findEntryByDate(entries, userId, date);

            if (existingEntryOpt.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No entry found for %s on %s - nothing to remove", username, date));
                return OperationResult.success("No temporary stop to remove", getOperationType());
            }

            WorkTimeTable entry = existingEntryOpt.get();

            // PRESERVED: Check if there's actually a temporary stop to remove
            if (entry.getTotalTemporaryStopMinutes() == null || entry.getTotalTemporaryStopMinutes() == 0) {
                LoggerUtil.info(this.getClass(), String.format("No temporary stop found for %s on %s - nothing to remove", username, date));
                return OperationResult.success("No temporary stop to remove", getOperationType(), entry);
            }

            LoggerUtil.debug(this.getClass(), String.format("Current entry: tempStopCount=%d, tempStopMinutes=%d",
                    entry.getTemporaryStopCount(), entry.getTotalTemporaryStopMinutes()));

            // Store original timeOffType before updates
            String originalTimeOffType = entry.getTimeOffType();

            // PRESERVED: Remove temporary stop using WorktimeEntityBuilder (SAME BUSINESS LOGIC)
            WorkTimeTable updatedEntry = WorktimeEntityBuilder.removeTemporaryStop(entry, userScheduleHours);

            // AUTO-UPDATE ZS (Short Day) based on completion status
            checkAndUpdateShortDayStatus(updatedEntry, originalTimeOffType);

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

            LoggerUtil.info(this.getClass(), String.format("Successfully removed temporary stop for %s on %s", username, date));

            return OperationResult.successWithSideEffects(message, getOperationType(), updatedEntry, sideEffects);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to remove temporary stop for %s on %s: %s", username, date, e.getMessage()), e);
            return OperationResult.failure("Failed to remove temporary stop: " + e.getMessage(), getOperationType());
        }
    }

    // Find entry by date and user ID - same logic
    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    //  Replace entry in list - same logic
    private void replaceEntry(List<WorkTimeTable> entries, WorkTimeTable updatedEntry) {
        entries.removeIf(entry -> updatedEntry.getUserId().equals(entry.getUserId()) &&
                        updatedEntry.getWorkDate().equals(entry.getWorkDate())
        );
        entries.add(updatedEntry);
        entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));
    }

    // Create file path ID - same logic as original context method
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

    /**
     * Auto-update ZS (Short Day) based on completion status.
     * Removing temporary stops increases total worked time, which can change day completion status.
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