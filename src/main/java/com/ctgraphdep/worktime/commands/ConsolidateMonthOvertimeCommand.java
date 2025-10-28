package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.service.MonthlyOvertimeConsolidationService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.jetbrains.annotations.NotNull;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Command for consolidating monthly overtime for CR (Recovery Leave) and ZS (Short Day) entries.
 * Follows the same pattern as ConsolidateWorkTimeCommand.
 * This command:
 * - Loads month entries from cache (no I/O)
 * - Calculates total overtime pool
 * - Finds CR and ZS entries
 * - Distributes overtime to complete these entries
 * - Deducts from regular work entries
 * - Saves consolidated result
 */
public class ConsolidateMonthOvertimeCommand extends WorktimeOperationCommand<Map<String, Object>> {

    private final MonthlyOvertimeConsolidationService consolidationService;
    private final String username;
    private final Integer userId;
    private final int year;
    private final int month;

    private ConsolidateMonthOvertimeCommand(WorktimeOperationContext context,
                                           MonthlyOvertimeConsolidationService consolidationService,
                                           String username,
                                           Integer userId,
                                           int year,
                                           int month) {
        super(context);
        this.consolidationService = consolidationService;
        this.username = username;
        this.userId = userId;
        this.year = year;
        this.month = month;
    }

    // FACTORY METHOD: Create command for user overtime consolidation
    public static ConsolidateMonthOvertimeCommand forUser(WorktimeOperationContext context,
                                                         MonthlyOvertimeConsolidationService consolidationService,
                                                         String username,
                                                         Integer userId,
                                                         int year,
                                                         int month) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username required for overtime consolidation");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID required for overtime consolidation");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year for overtime consolidation: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month for overtime consolidation: " + month);
        }

        return new ConsolidateMonthOvertimeCommand(context, consolidationService, username, userId, year, month);
    }

    @Override
    protected void validate() {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        LoggerUtil.info(this.getClass(), String.format(
            "Validating overtime consolidation for %s - %d/%d", username, month, year));

        // Validate month exists (not future month)
        YearMonth targetMonth = YearMonth.of(year, month);
        YearMonth currentMonth = YearMonth.now();
        if (targetMonth.isAfter(currentMonth)) {
            throw new IllegalArgumentException("Cannot consolidate overtime for future months");
        }

        LoggerUtil.debug(this.getClass(), "Overtime consolidation validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
            "Starting overtime consolidation for %s - %d/%d", username, month, year));

        try {

            // Execute consolidation using service
            ServiceResult<MonthlyOvertimeConsolidationService.ConsolidationResult> result =
                consolidationService.consolidateMonth(username, userId, year, month);

            if (result.isSuccess()) {
                MonthlyOvertimeConsolidationService.ConsolidationResult consolidation = result.getData();

                // Create result data map (same pattern as ConsolidateWorkTimeCommand line 104-109)
                Map<String, Object> resultData = getStringObjectMap(consolidation);

                // Create side effects tracking (same pattern as ConsolidateWorkTimeCommand line 112-114)
                OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(String.format("worktime-%s/%d/%d", username, year, month))
                    .build();

                String successMessage = String.format(
                    "Overtime consolidation completed for %s - %d/%d: %d CR entries, %d ZS entries, distributed %d minutes from overtime pool",
                    username, month, year,
                    consolidation.getCrEntriesProcessed(),
                    consolidation.getZsEntriesProcessed(),
                    consolidation.getOvertimeDistributed());

                LoggerUtil.info(this.getClass(), successMessage);

                return OperationResult.successWithSideEffects(successMessage, getOperationType(), resultData, sideEffects);

            } else {
                // Handle service failure
                String errorMessage = String.format("Overtime consolidation failed for %s - %d/%d: %s",
                    username, month, year, result.getErrorMessage());

                LoggerUtil.warn(this.getClass(), errorMessage);

                return OperationResult.failure(errorMessage, getOperationType());
            }

        } catch (Exception e) {
            String errorMessage = String.format("Overtime consolidation error for %s - %d/%d: %s",
                username, month, year, e.getMessage());

            LoggerUtil.error(this.getClass(), errorMessage, e);

            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    private @NotNull Map<String, Object> getStringObjectMap(MonthlyOvertimeConsolidationService.ConsolidationResult consolidation) {
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("username", username);
        resultData.put("userId", userId);
        resultData.put("year", year);
        resultData.put("month", month);
        resultData.put("crEntriesProcessed", consolidation.getCrEntriesProcessed());
        resultData.put("zsEntriesProcessed", consolidation.getZsEntriesProcessed());
        resultData.put("overtimeDistributed", consolidation.getOvertimeDistributed());
        resultData.put("remainingOvertimePool", consolidation.getRemainingOvertimePool());
        return resultData;
    }

    @Override
    protected String getCommandName() {
        return String.format("ConsolidateMonthOvertime[%s-%d/%d]", username, month, year);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.CONSOLIDATE_OVERTIME;
    }
}