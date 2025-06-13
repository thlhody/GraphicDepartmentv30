package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * REFACTORED Command to add national holiday for all non-admin users.
 * This implementation now matches the original service system:
 * - Uses TimeValidationService for proper date validation
 * - Gets non-admin users from UserService via context
 * - Uses "remove existing entries first, then add new ones" logic
 * - Proper logging integration with LoggerUtil
 * - Comprehensive error handling and side effects tracking
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

        // Validate admin permissions
        context.requireAdminPrivileges("add national holiday");

        // Use TimeValidationService for proper holiday date validation (matches old system)
        try {
            context.validateHolidayDate(date);
            LoggerUtil.debug(this.getClass(), String.format("Holiday date validation passed for: %s", date));
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Holiday date validation failed for %s: %s", date, e.getMessage()));
            throw e; // Re-throw to maintain validation contract
        }
    }

    @Override
    protected OperationResult executeCommand() {
        int year = date.getYear();
        int month = date.getMonthValue();

        LoggerUtil.info(this.getClass(), String.format("Executing add national holiday command for %s (%d/%d)", date, month, year));

        try {
            // Load admin entries for the month
            List<WorkTimeTable> adminEntries = context.loadAdminWorktime(year, month);
            int initialEntryCount = adminEntries.size();

            LoggerUtil.debug(this.getClass(), String.format("Loaded %d existing admin entries for %d/%d", initialEntryCount, year, month));

            // Get all non-admin users using the correct method from UserService
            List<User> nonAdminUsers = context.getNonAdminUsers();

            if (nonAdminUsers.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "No non-admin users found for national holiday creation");
                return OperationResult.success(
                        String.format("No non-admin users found for national holiday on %s", date),
                        getOperationType(),
                        new ArrayList<>()
                );
            }

            LoggerUtil.info(this.getClass(), String.format("Found %d non-admin users for national holiday creation", nonAdminUsers.size()));

            // IMPORTANT: Use old system logic - Remove existing entries first, then add new ones
            int removedCount = context.removeEntriesByDate(adminEntries, date);
            if (removedCount > 0) {
                LoggerUtil.info(this.getClass(), String.format("Removed %d existing entries for date %s before adding national holiday", removedCount, date));
            }

            // Create national holiday entries for all non-admin users
            List<WorkTimeTable> newHolidayEntries = createNationalHolidayEntries(nonAdminUsers, date);

            LoggerUtil.info(this.getClass(), String.format("Created %d national holiday entries for %s", newHolidayEntries.size(), date));

            // Add new holiday entries to admin entries
            context.addEntries(adminEntries, newHolidayEntries);

            // Save updated admin entries
            context.saveAdminWorktime(adminEntries, year, month);


            // Create success result with comprehensive information
            String successMessage = String.format("Successfully added national holiday for %s: %d users affected, %d existing entries removed, %d new entries created",
                                                                                                    date, nonAdminUsers.size(), removedCount, newHolidayEntries.size());

            LoggerUtil.info(this.getClass(), successMessage);

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder().fileUpdated(String.format("admin/%d/%d", year, month))
                    .cacheInvalidated(String.format("all-users-%d", year)).build();

            return OperationResult.successWithSideEffects(successMessage, getOperationType(), newHolidayEntries, sideEffects);

        } catch (Exception e) {
            String errorMessage = String.format("Failed to add national holiday for %s: %s", date, e.getMessage());

            LoggerUtil.error(this.getClass(), errorMessage, e);

            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    /**
     * Create national holiday entries for all non-admin users
     */
    private List<WorkTimeTable> createNationalHolidayEntries(List<User> nonAdminUsers, LocalDate date) {
        List<WorkTimeTable> holidayEntries = new ArrayList<>();

        for (User user : nonAdminUsers) {
            try {
                // Create national holiday entry using EntityBuilder
                WorkTimeTable nationalHolidayEntry = WorktimeEntityBuilder.createTimeOffEntry(user.getUserId(), date, "SN");

                // Set as admin edited to ensure it takes precedence over user entries
                nationalHolidayEntry.setAdminSync(SyncStatusMerge.ADMIN_EDITED);

                holidayEntries.add(nationalHolidayEntry);

                LoggerUtil.debug(this.getClass(), String.format("Created national holiday entry for user %s (ID: %d) on %s", user.getUsername(), user.getUserId(), date));

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error creating national holiday entry for user %s (ID: %d): %s", user.getUsername(), user.getUserId(), e.getMessage()));
                // Continue with other users - don't fail the entire operation for one user
            }
        }

        return holidayEntries;
    }

    @Override
    protected String getCommandName() {
        return String.format("AddNationalHoliday[%s]", date);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.ADD_NATIONAL_HOLIDAY;
    }
}