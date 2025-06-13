package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.util.WorktimeEntityBuilder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CORRECTED Add Time Off Command - Proper business logic implementation
 * CORRECTED Key Behaviors:
 * - ADMIN: Can add 1 entry at a time (CO/CM/SN) for any date
 * - USER: Can add 1 day or multiple days (CO/CM only, no SN) for FUTURE dates only
 * - Users request future time off (not past) - this is time off planning
 * - Holiday balance deduction for CO requests (future time off planning)
 * - Proper validation based on user role and date direction
 */
public class AddTimeOffCommand extends WorktimeOperationCommand<List<WorkTimeTable>> {
    private final String username;
    private final Integer userId;
    private final List<LocalDate> dates;
    private final String timeOffType;

    public AddTimeOffCommand(WorktimeOperationContext context, String username, Integer userId, List<LocalDate> dates, String timeOffType) {
        super(context);
        this.username = username;
        this.userId = userId;
        this.dates = dates;
        this.timeOffType = timeOffType;
    }

    @Override
    protected void validate() {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("Dates cannot be null or empty");
        }
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            throw new IllegalArgumentException("Time off type cannot be null or empty");
        }

        LoggerUtil.info(this.getClass(), String.format("Validating add time off: %s, %d dates, type %s", username, dates.size(), timeOffType));

        // Validate time off type
        WorktimeEntityBuilder.ValidationRules.validateTimeOffType(timeOffType);

        // Validate user permissions
        context.validateUserPermissions(username, "add time off");

        boolean isCurrentUserAdmin = context.isCurrentUserAdmin();
        String currentUsername = context.getCurrentUsername();

        // ADMIN vs USER specific validation
        if (isCurrentUserAdmin && !currentUsername.equals(username)) {
            // Admin adding time off for another user
            validateAdminAddTimeOff();
        } else {
            // User adding time off for themselves
            validateUserAddTimeOff();
        }

        LoggerUtil.debug(this.getClass(), "Add time off validation completed successfully");
    }

    /**
     * Validate admin adding time off (can add CO/CM/SN, one entry at a time, any date)
     */
    private void validateAdminAddTimeOff() {
        LoggerUtil.debug(this.getClass(), "Validating admin add time off operation");

        // Admin can add any type including SN
        if (!timeOffType.matches("^(CO|CM|SN)$")) {
            throw new IllegalArgumentException("Admin can add CO, CM, or SN time off types");
        }

        // Admin should add one entry at a time for individual users
        if (dates.size() > 1) {
            LoggerUtil.warn(this.getClass(), String.format("Admin attempting to add %d time off entries at once - restricting to single entry", dates.size()));
            throw new IllegalArgumentException("Admin should add time off one entry at a time for individual users");
        }

        // Validate each date using TimeValidationService (admin can use any date)
        for (LocalDate date : dates) {
            try {
                context.validateHolidayDate(date);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Admin date validation warning for %s: %s", date, e.getMessage()));
                // Be lenient for admin operations
            }
        }
    }

    /**
     * Validate user adding time off (CO/CM only, multiple dates allowed, FUTURE dates only)
     */
    private void validateUserAddTimeOff() {
        LoggerUtil.debug(this.getClass(), "Validating user add time off operation");

        // Users cannot add SN (national holidays)
        if (WorkCode.NATIONAL_HOLIDAY_CODE.equals(timeOffType)) {
            throw new IllegalArgumentException("Users cannot add national holidays (SN). Only admin can add SN.");
        }

        // Users can only add CO or CM
        if (!timeOffType.matches("^(CO|CM)$")) {
            throw new IllegalArgumentException("Users can only add CO (vacation) or CM (medical) time off");
        }

        // CORRECTED: Users can only add time off for FUTURE dates (requesting time off in advance)
        LocalDate today = LocalDate.now();
        for (LocalDate date : dates) {
            // Users can only request time off for future dates (not today or past)
            if (!date.isAfter(today)) {
                throw new IllegalArgumentException(String.format("Users can only request time off for future dates. Date %s is not allowed. Use future dates to plan time off in advance.", date));
            }

            // Validate time off date (weekend check, etc.)
            context.validateTimeOffDate(date);
        }

        if (WorkCode.TIME_OFF_CODE.equalsIgnoreCase(timeOffType)) {
            // Use the corrected method with clear exception message
            context.validateSufficientHolidayBalance(dates.size(), "future time off request");
        }
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Executing add time off command for %s: %d dates, type %s", username, dates.size(), timeOffType));

        try {
            // Track for side effects
            Integer oldHolidayBalance = null;
            boolean balanceUpdated = false;

            // Get initial balance if CO type (for future time off planning)
            if (WorkCode.TIME_OFF_CODE.equalsIgnoreCase(timeOffType)) {
                oldHolidayBalance = context.getCurrentHolidayBalance();
            }

            // Process each month separately for efficient file handling
            int totalEntriesCreated = 0;

            // Group dates by month for efficient processing
            var datesByMonth = dates.stream().collect(Collectors.groupingBy(YearMonth::from));

            // Process each month
            for (var monthEntry : datesByMonth.entrySet()) {
                var yearMonth = monthEntry.getKey();
                var monthDates = monthEntry.getValue();

                int monthEntriesCreated = processMonthTimeOff(yearMonth, monthDates);
                totalEntriesCreated += monthEntriesCreated;

                LoggerUtil.debug(this.getClass(), String.format("Processed %d time off entries for %s - %d/%d", monthEntriesCreated, username, yearMonth.getYear(), yearMonth.getMonthValue()));
            }

            // Calculate actual vacation days needed (excluding existing SN days)
            if (WorkCode.TIME_OFF_CODE.equalsIgnoreCase(timeOffType) && totalEntriesCreated > 0) {
                // Use context method instead of local helper
                int actualVacationDaysUsed = context.calculateActualVacationDaysNeeded(dates);

                if (actualVacationDaysUsed > 0) {
                    balanceUpdated = context.updateHolidayBalance(-actualVacationDaysUsed);
                    if (balanceUpdated) {
                        LoggerUtil.info(this.getClass(), String.format("Deducted %d vacation days for %s (%d total dates, %d were already SN)", actualVacationDaysUsed,
                                                                                                        username, dates.size(), dates.size() - actualVacationDaysUsed));
                    }
                }
            }

            // Invalidate cache for affected year
            if (totalEntriesCreated > 0) {
                int year = dates.get(0).getYear();
                context.invalidateTimeOffCache(username, year);
                context.refreshTimeOffTracker(username, userId, year);
            }

            // Create success result
            String message = String.format("Successfully added %d %s time off entries for %s", totalEntriesCreated, timeOffType, username);

            if (WorkCode.TIME_OFF_CODE.equalsIgnoreCase(timeOffType) && balanceUpdated) {
                Integer newBalance = context.getCurrentHolidayBalance();
                message += String.format(". Holiday balance: %d → %d (reserved for future time off)", oldHolidayBalance, newBalance != null ? newBalance : 0);
            }

            // Create side effects
            OperationResult.OperationSideEffects.Builder sideEffectsBuilder = OperationResult.OperationSideEffects.builder().cacheInvalidated(context.createCacheKey(username, dates.get(0).getYear()));

            if (balanceUpdated && oldHolidayBalance != null) {
                sideEffectsBuilder.holidayBalanceChanged(oldHolidayBalance, context.getCurrentHolidayBalance());
            }

            LoggerUtil.info(this.getClass(), message);
            // No specific entry data for bulk operation
            return OperationResult.successWithSideEffects(message, getOperationType(), null, sideEffectsBuilder.build());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error adding time off for %s: %s", username, e.getMessage()), e);
            return OperationResult.failure("Failed to add time off: " + e.getMessage(), getOperationType());
        }
    }

    /**
     * Process time off entries for a specific month
     */
    private int processMonthTimeOff(YearMonth yearMonth, List<LocalDate> monthDates) {
        int year = yearMonth.getYear();
        int month = yearMonth.getMonthValue();

        // Load month entries - use admin or user context based on current user
        List<WorkTimeTable> entries;
        boolean isAdminOperation = context.isCurrentUserAdmin() && !context.getCurrentUsername().equals(username);

        if (isAdminOperation) {
            // Admin operation - load admin entries
            entries = context.loadAdminWorktime(year, month);
        } else {
            // User operation - load user entries
            entries = context.loadUserWorktime(username, year, month);
        }

        int entriesCreated = 0;

        // Create time off entries for each date
        for (LocalDate date : monthDates) {
            // Check if entry already exists
            if (context.findEntryByDate(entries, userId, date).isEmpty()) {
                WorkTimeTable timeOffEntry = WorktimeEntityBuilder.createTimeOffEntry(userId, date, timeOffType);

                // Set appropriate sync status based on operation type
                if (isAdminOperation) {
                    timeOffEntry.setAdminSync(SyncStatusMerge.ADMIN_EDITED);
                } else {
                    timeOffEntry.setAdminSync(SyncStatusMerge.USER_INPUT);
                }

                context.addOrReplaceEntry(entries, timeOffEntry);
                entriesCreated++;

                LoggerUtil.debug(this.getClass(), String.format("Created %s time off entry for %s on %s", timeOffType, username, date));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Time off entry already exists for %s on %s, skipping", username, date));
            }
        }

        // Save entries back
        if (entriesCreated > 0) {
            if (isAdminOperation) {
                context.saveAdminWorktime(entries, year, month);
                // Admin operations DON'T update tracker (admin bypasses user tracker)
            } else {
                context.saveUserWorktime(username, entries, year, month);
                // 🎯 HERE: Only for USER operations, update tracker
                try {
                    context.addTimeOffRequestsToTracker(username, userId, monthDates, timeOffType, year);
                    LoggerUtil.debug(this.getClass(), String.format("Updated time off tracker for user %s - %d/%d", username, year, month));
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Failed to update tracker for %s - %d/%d: %s", username, year, month, e.getMessage()));
                    // Don't fail entire operation for tracker sync issue
                }
            }
        }

        return entriesCreated;
    }

    @Override
    protected String getCommandName() {
        return String.format("AddTimeOff[%s, %d dates, %s]", username, dates.size(), timeOffType);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.ADD_TIME_OFF;
    }
}