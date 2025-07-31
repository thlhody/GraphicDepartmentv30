package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * REFACTORED: Command to add national holiday for all non-admin users using accessor pattern.
 * Uses AdminOwnDataAccessor for admin worktime file operations.
 * Keeps original business logic intact.
 */
public class AddNationalHolidayCommand extends WorktimeOperationCommand<List<WorkTimeTable>> {

    private final LocalDate date;

    public AddNationalHolidayCommand(WorktimeOperationContext context, LocalDate date) {
        super(context);
        this.date = date;
    }

    @Override
    protected void validate() {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        LoggerUtil.debug(this.getClass(), String.format("Validating national holiday date: %s", date));

        // Use TimeValidationService for proper holiday date validation
        try {
            context.validateHolidayDate(date);
            LoggerUtil.debug(this.getClass(), String.format("Holiday date validation passed for: %s", date));
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Holiday date validation failed for %s: %s", date, e.getMessage()));
            throw e;
        }
    }

    @Override
    protected OperationResult executeCommand() {
        int year = date.getYear();
        int month = date.getMonthValue();

        try {
            LoggerUtil.info(this.getClass(), String.format("Adding national holiday for %s using AdminOwnDataAccessor", date));

            // Use AdminOwnDataAccessor for admin worktime operations
            WorktimeDataAccessor accessor = context.getDataAccessor("admin");

            // Load admin entries for the month
            List<WorkTimeTable> adminEntries = accessor.readWorktime("admin", year, month);
            if (adminEntries == null) {
                adminEntries = new ArrayList<>();
            }

            // Remove existing entries for this date
            int removedCount = removeEntriesByDate(adminEntries, date);

            // Get all non-admin users
            List<User> nonAdminUsers = context.getNonAdminUsers();
            if (nonAdminUsers.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "No non-admin users found for national holiday creation");
                return OperationResult.failure("No non-admin users found", getOperationType());
            }

            // Create national holiday entries for all non-admin users
            List<WorkTimeTable> newHolidayEntries = createNationalHolidayEntries(nonAdminUsers, date);

            LoggerUtil.info(this.getClass(), String.format("Created %d national holiday entries for %s", newHolidayEntries.size(), date));

            // Add new holiday entries to admin entries
            adminEntries.addAll(newHolidayEntries);
            sortEntries(adminEntries);

            // Save using AdminOwnDataAccessor
            accessor.writeWorktimeWithStatus("admin", adminEntries, year, month, context.getCurrentUser().getRole());

            // Create success result
            String successMessage = String.format("Successfully added national holiday for %s: %d users affected, %d existing entries removed, %d new entries created",
                    date, nonAdminUsers.size(), removedCount, newHolidayEntries.size());

            LoggerUtil.info(this.getClass(), successMessage);

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(String.format("admin/%d/%d", year, month))
                    .cacheInvalidated(String.format("all-users-%d", year))
                    .build();

            return OperationResult.successWithSideEffects(successMessage, getOperationType(), newHolidayEntries, sideEffects);

        } catch (Exception e) {
            String errorMessage = String.format("Failed to add national holiday for %s: %s", date, e.getMessage());
            LoggerUtil.error(this.getClass(), errorMessage, e);
            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    /**
     * Create national holiday entries for non-admin users - ORIGINAL LOGIC
     */
    private List<WorkTimeTable> createNationalHolidayEntries(List<User> nonAdminUsers, LocalDate date) {
        List<WorkTimeTable> holidayEntries = new ArrayList<>();

        for (User user : nonAdminUsers) {
            WorkTimeTable holidayEntry = WorktimeEntityBuilder.createTimeOffEntry(user.getUserId(), date, WorkCode.NATIONAL_HOLIDAY_CODE);
            holidayEntry.setAdminSync(MergingStatusConstants.ADMIN_INPUT);
            holidayEntries.add(holidayEntry);

            LoggerUtil.debug(this.getClass(), String.format("Created national holiday entry for user %s (%d) on %s",
                    user.getUsername(), user.getUserId(), date));
        }

        return holidayEntries;
    }

    /**
     * Remove entries by date for all users - ORIGINAL LOGIC
     */
    private int removeEntriesByDate(List<WorkTimeTable> entries, LocalDate date) {
        int removedCount = 0;
        var iterator = entries.iterator();
        while (iterator.hasNext()) {
            WorkTimeTable entry = iterator.next();
            if (entry.getWorkDate().equals(date)) {
                iterator.remove();
                removedCount++;
            }
        }
        return removedCount;
    }

    /**
     * Sort entries by date and user ID - ORIGINAL LOGIC
     */
    private void sortEntries(List<WorkTimeTable> entries) {
        entries.sort(java.util.Comparator.comparing(WorkTimeTable::getWorkDate)
                .thenComparingInt(WorkTimeTable::getUserId));
    }

    @Override
    protected String getCommandName() {
        return String.format("AddNationalHoliday[date=%s]", date);
    }

    @Override
    protected String getOperationType() {
        return "ADD_NATIONAL_HOLIDAY";
    }
}