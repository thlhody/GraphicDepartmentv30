package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REFACTORED: Admin special day work command using AdminOwnDataAccessor.
 * Handles admin special day work time updates (SN:5, CO:6, etc.).
 * Keeps original business logic intact.
 */
public class AdminUpdateSpecialDayWithWorkCommand extends WorktimeOperationCommand<WorkTimeTable> {

    private static final Pattern SPECIAL_DAY_PATTERN = Pattern.compile("^(SN|CO|CM|W):(\\d+(?:\\.\\d+)?)$");

    private final Integer userId;
    private final LocalDate date;
    private final String specialDayValue; // "CO:6", "SN:5", etc.

    // Parsed values
    private String timeOffType;
    private double workHours;

    public AdminUpdateSpecialDayWithWorkCommand(WorktimeOperationContext context, Integer userId, LocalDate date, String specialDayValue) {
        super(context);
        this.userId = userId;
        this.date = date;
        this.specialDayValue = specialDayValue;
    }

    @Override
    protected void validate() {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (specialDayValue == null || specialDayValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Special day value cannot be null or empty");
        }

        // Parse and validate the input format
        parseSpecialDayValue();

        LoggerUtil.info(this.getClass(), String.format("Validating admin special day work update: userId=%d, date=%s, type=%s, hours=%.2f", userId, date, timeOffType, workHours));

    }

    @Override
    protected OperationResult executeCommand() {
        int year = date.getYear();
        int month = date.getMonthValue();

        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing admin special day work update: userId=%d, date=%s, type=%s, hours=%.2f using AdminOwnDataAccessor",
                    userId, date, timeOffType, workHours));

            // Use AdminOwnDataAccessor for admin worktime operations
            WorktimeDataAccessor accessor = context.getDataAccessor("admin");

            // Load admin entries
            List<WorkTimeTable> adminEntries = accessor.readWorktime("admin", year, month);
            if (adminEntries == null) {
                adminEntries = new java.util.ArrayList<>();
            }

            // Find or create entry
            WorkTimeTable entry = findOrCreateEntry(adminEntries, userId, date);

            // Apply special day work update - ORIGINAL LOGIC
            applySpecialDayWorkUpdate(entry);

            // Replace entry in list
            replaceEntry(adminEntries, entry);

            // Save using AdminOwnDataAccessor
            accessor.writeWorktimeWithStatus("admin", adminEntries, year, month, context.getCurrentUser().getRole());

            String message = String.format("Updated special day work: userId=%d, date=%s, %s with %.2f hours",
                    userId, date, timeOffType, workHours);
            LoggerUtil.info(this.getClass(), message);

            return OperationResult.success(message, getOperationType(), entry);

        } catch (Exception e) {
            String errorMessage = String.format("Failed to update special day work for userId=%d, date=%s: %s", userId, date, e.getMessage());
            LoggerUtil.error(this.getClass(), errorMessage, e);
            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    /**
     * Parse special day value format - ORIGINAL LOGIC
     */
    private void parseSpecialDayValue() {
        Matcher matcher = SPECIAL_DAY_PATTERN.matcher(specialDayValue.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid special day format. Expected format: 'SN:5', 'CO:6', 'CM:4', or 'W:8'");
        }

        String typeCode = matcher.group(1);
        workHours = Double.parseDouble(matcher.group(2));

        // Map type codes to work codes - ORIGINAL LOGIC
        switch (typeCode) {
            case WorkCode.NATIONAL_HOLIDAY_CODE -> timeOffType = WorkCode.NATIONAL_HOLIDAY_CODE;
            case WorkCode.TIME_OFF_CODE -> timeOffType = WorkCode.TIME_OFF_CODE;
            case WorkCode.MEDICAL_LEAVE_CODE -> timeOffType = WorkCode.MEDICAL_LEAVE_CODE;
            case WorkCode.WEEKEND_CODE -> timeOffType = WorkCode.WEEKEND_CODE;
            default -> throw new IllegalArgumentException("Invalid time off type code: " + typeCode);
        }
    }

    /**
     * Find or create entry - ORIGINAL LOGIC
     */
    private WorkTimeTable findOrCreateEntry(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        Optional<WorkTimeTable> existingOpt = findEntryByDate(entries, userId, date);

        if (existingOpt.isPresent()) {
            return existingOpt.get();
        } else {
            WorkTimeTable newEntry = WorktimeEntityBuilder.createNewEntry(userId, date);
            newEntry.setAdminSync(MergingStatusConstants.ADMIN_INPUT);
            entries.add(newEntry);
            return newEntry;
        }
    }

    /**
     * Apply special day work update - FIXED: Proper LocalDateTime creation
     */
    private void applySpecialDayWorkUpdate(WorkTimeTable entry) {
        // Set time off type
        entry.setTimeOffType(timeOffType);

        // Calculate work time interval (08:00 + work hours) - ORIGINAL LOGIC
        LocalTime startTime = LocalTime.of(8, 0);
        LocalTime endTime = startTime.plusMinutes((long) (workHours * 60));

        // FIXED: Combine date with time to create proper LocalDateTime
        entry.setDayStartTime(date.atTime(startTime));  // FIXED - use date.atTime()
        entry.setDayEndTime(date.atTime(endTime));      // FIXED - use date.atTime()

        // Calculate total worked minutes (all become overtime for special days) - ORIGINAL LOGIC
        int totalMinutes = (int) (workHours * 60);
        entry.setTotalWorkedMinutes(totalMinutes);
        entry.setTotalOvertimeMinutes(totalMinutes); // All work on special days is overtime

        // Set admin status
        entry.setAdminSync(MergingStatusConstants.ADMIN_INPUT);

        LoggerUtil.debug(this.getClass(), String.format(
                "Applied special day work: %s from %s to %s, total: %d minutes (all overtime)",
                timeOffType, startTime, endTime, totalMinutes));
    }

    /**
     * Find entry by date and user ID - UTILITY METHOD
     */
    private Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    /**
     * Replace entry in list - UTILITY METHOD
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

    @Override
    protected String getCommandName() {
        return String.format("AdminUpdateSpecialDayWithWork[userId=%d, date=%s, value=%s]", userId, date, specialDayValue);
    }

    @Override
    protected String getOperationType() {
        return "ADMIN_UPDATE_SPECIAL_DAY_WITH_WORK";
    }
}