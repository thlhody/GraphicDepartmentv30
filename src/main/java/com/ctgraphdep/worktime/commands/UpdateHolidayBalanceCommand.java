package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.User;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.Optional;

/**
 * REFACTORED: Command to update holiday balance for a user using accessor pattern.
 * ADMIN OPERATION: Only administrators can update user holiday balances.
 * Key Behaviors:
 * - ADMIN ONLY: Only administrators can update user holiday balances
 * - Validation: Range validation (0-365 days), user existence check
 * - File Operations: Uses context operations for holiday balance updates
 * - Cache Management: Updates AllUsersCacheService and invalidates TimeOffCache
 * - Audit Trail: Comprehensive logging with old vs new balance tracking
 * - Side Effects: Tracks balance changes for audit and notifications
 */
public class UpdateHolidayBalanceCommand extends WorktimeOperationCommand<Integer> {
    private final Integer userId;
    private final Integer newBalance;

    private UpdateHolidayBalanceCommand(WorktimeOperationContext context, Integer userId, Integer newBalance) {
        super(context);
        this.userId = userId;
        this.newBalance = newBalance;
    }

    public static UpdateHolidayBalanceCommand forUser(WorktimeOperationContext context, Integer userId, Integer newBalance){
        if (userId == null) {
            throw new IllegalArgumentException("User ID required for updating holiday balance");
        }
        return new UpdateHolidayBalanceCommand(context,  userId, newBalance);
    }

    @Override
    protected void validate() {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (newBalance == null) {
            throw new IllegalArgumentException("New balance cannot be null");
        }
        if (newBalance < 0) {
            throw new IllegalArgumentException("Holiday balance cannot be negative");
        }
        if (newBalance > 365) {
            throw new IllegalArgumentException("Holiday balance cannot exceed 365 days");
        }

        LoggerUtil.info(this.getClass(), String.format("Validating holiday balance update: userId=%d, newBalance=%d", userId, newBalance));

        // PRESERVED: Validate admin permissions (FIXED: using available validation)
        if (!context.isCurrentUserAdmin()) {
            throw new SecurityException("Only administrators can update holiday balances");
        }

        // PRESERVED: Validate user exists
        Optional<User> userOpt = context.getUserById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }

        LoggerUtil.debug(this.getClass(), String.format("Validation passed for updating holiday balance: user %s (ID: %d) to %d days",
                userOpt.get().getUsername(), userId, newBalance));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Executing holiday balance update for userId=%d, newBalance=%d", userId, newBalance));

        try {
            // PRESERVED: Get user information
            Optional<User> userOpt = context.getUserById(userId);
            if (userOpt.isEmpty()) {
                return OperationResult.failure("User not found", getOperationType());
            }

            User user = userOpt.get();
            String username = user.getUsername();

            // PRESERVED: Get current balance for audit trail
            Integer oldBalance = user.getPaidHolidayDays();

            LoggerUtil.info(this.getClass(), String.format("Holiday balance update for user %s (ID: %d): %d → %d days",
                    username, userId, oldBalance, newBalance));

            // PRESERVED: Perform the update using context operations
            boolean updateSuccess = context.updateUserHolidayBalance(userId, newBalance);

            if (!updateSuccess) {
                String errorMessage = String.format("Failed to update holiday balance for user %s (ID: %d)", username, userId);
                LoggerUtil.error(this.getClass(), errorMessage);
                return OperationResult.failure(errorMessage, getOperationType());
            }

            // PRESERVED: Invalidate relevant caches
            context.invalidateUserCache(username, userId);

            LoggerUtil.info(this.getClass(), String.format("Successfully updated holiday balance for user %s (ID: %d): %d → %d days",
                    username, userId, oldBalance != null ? oldBalance : 0, newBalance));

            // PRESERVED: Create comprehensive success message
            String successMessage = String.format("Holiday balance updated for user %s (ID: %d): %d → %d days",
                    username, userId, oldBalance != null ? oldBalance : 0, newBalance);

            // PRESERVED: Create side effects tracking
            OperationResult.OperationSideEffects.Builder sideEffectsBuilder = OperationResult.OperationSideEffects.builder()
                    .holidayBalanceChanged(oldBalance, newBalance)
                    .cacheInvalidated(createCacheKey(username, LocalDate.now().getYear()));

            // PRESERVED: Additional side effect: User file updated
            sideEffectsBuilder.fileUpdated(String.format("user/%s/holiday-balance", username));

            return OperationResult.successWithSideEffects(successMessage, getOperationType(), newBalance, sideEffectsBuilder.build());

        } catch (Exception e) {
            String errorMessage = String.format("Error updating holiday balance for userId %d: %s", userId, e.getMessage());

            LoggerUtil.error(this.getClass(), errorMessage, e);

            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    @Override
    protected String getCommandName() {
        return String.format("UpdateHolidayBalance[userId=%d, newBalance=%d]", userId, newBalance);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.UPDATE_HOLIDAY_BALANCE;
    }

    private String createCacheKey(String username, int year) {
        return String.format("%s-%d", username, year);
    }
}