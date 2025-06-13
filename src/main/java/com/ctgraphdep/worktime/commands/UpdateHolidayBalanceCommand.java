package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * REFACTORED Command to update holiday balance for a user (admin operation)
 * Key Behaviors:
 * - ADMIN ONLY: Only administrators can update user holiday balances
 * - Validation: Range validation (0-365 days), user existence check
 * - File Operations: Uses UserDataService through context (admin vs user paths)
 * - Cache Management: Updates AllUsersCacheService and invalidates TimeOffCache
 * - Audit Trail: Comprehensive logging with old vs new balance tracking
 * - Side Effects: Tracks balance changes for audit and notifications
 * This replaces HolidayManagementService.updateUserHolidayDays functionality
 */
public class UpdateHolidayBalanceCommand extends WorktimeOperationCommand<Integer> {
    private final Integer userId;
    private final Integer newBalance;

    public UpdateHolidayBalanceCommand(WorktimeOperationContext context, Integer userId, Integer newBalance) {
        super(context);
        this.userId = userId;
        this.newBalance = newBalance;
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

        // Validate admin permissions
        context.requireAdminPrivileges("update holiday balance");

        // Validate user exists
        String username = context.getUsernameFromUserId(userId);
        if (username == null) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }

        LoggerUtil.debug(this.getClass(), String.format("Validation passed for updating holiday balance: user %s (ID: %d) to %d days", username, userId, newBalance));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Executing holiday balance update for userId=%d, newBalance=%d", userId, newBalance));

        try {
            // Get username for operations
            String username = context.getUsernameFromUserId(userId);
            if (username == null) {
                return OperationResult.failure("User not found", getOperationType());
            }

            // Get current balance for audit trail
            Integer oldBalance = context.getUserHolidayBalance(userId);

            LoggerUtil.info(this.getClass(), String.format("Holiday balance update for user %s (ID: %d): %d → %d days", username, userId, oldBalance, newBalance));

            // Perform the update using enhanced context
            boolean updateSuccess = context.updateUserHolidayBalance(userId, newBalance);

            if (!updateSuccess) {
                String errorMessage = String.format("Failed to update holiday balance for user %s (ID: %d)", username, userId);
                LoggerUtil.error(this.getClass(), errorMessage);
                return OperationResult.failure(errorMessage, getOperationType());
            }

            // Invalidate relevant caches
            context.invalidateUserCache(username, userId);

            LoggerUtil.info(this.getClass(), String.format("Successfully updated holiday balance for user %s (ID: %d): %d → %d days", username, userId, oldBalance, newBalance));

            // Create comprehensive success message
            String successMessage = String.format("Holiday balance updated for user %s (ID: %d): %d → %d days", username, userId, oldBalance != null ? oldBalance : 0, newBalance);

            // Create side effects tracking
            OperationResult.OperationSideEffects.Builder sideEffectsBuilder = OperationResult.OperationSideEffects.builder().holidayBalanceChanged(oldBalance, newBalance)
                            .cacheInvalidated(context.createCacheKey(username, java.time.LocalDate.now().getYear()));

            // Additional side effect: User file updated
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
}