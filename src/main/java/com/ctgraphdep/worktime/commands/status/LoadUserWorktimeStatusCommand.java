package com.ctgraphdep.worktime.commands.status;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.accessor.NetworkOnlyAccessor;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * REFACTORED: Command to load worktime data for status display using accessor pattern.
 * Always uses NetworkOnlyAccessor for consistent cross-user viewing.
 * Used by StatusController for worktime status viewing.
 */
public class LoadUserWorktimeStatusCommand extends WorktimeOperationCommand<List<WorkTimeTable>> {
    private final String targetUsername;
    private final int year;
    private final int month;

    public LoadUserWorktimeStatusCommand(WorktimeOperationContext context, String targetUsername,
                                         int year, int month) {
        super(context);
        this.targetUsername = targetUsername;
        this.year = year;
        this.month = month;
    }

    @Override
    protected void validate() {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Target username cannot be null or empty");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating worktime status load: target=%s, period=%d/%d", targetUsername, year, month));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Loading worktime status for %s - %d/%d using NetworkOnlyAccessor", targetUsername, year, month));

        try {
            // Always use NetworkOnlyAccessor for status viewing (consistent cross-user data)
            WorktimeDataAccessor accessor = new NetworkOnlyAccessor(
                    context.getWorktimeDataService(),
                    context.getRegisterDataService(),
                    context.getCheckRegisterDataService(),
                    context.getTimeOffDataService()
            );

            // Load worktime data
            List<WorkTimeTable> entries = accessor.readWorktime(targetUsername, year, month);

            if (entries == null) {
                entries = new ArrayList<>();
            }

            String message = String.format("Loaded %d worktime entries for %s - %d/%d",
                    entries.size(), targetUsername, year, month);

            return OperationResult.success(message, getOperationType(), entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading worktime status for %s - %d/%d: %s", targetUsername, year, month, e.getMessage()), e);
            return OperationResult.failure("Failed to load worktime data: " + e.getMessage(), getOperationType());
        }
    }

    @Override
    protected String getCommandName() {
        return String.format("LoadUserWorktimeStatus[target=%s, period=%d/%d]", targetUsername, year, month);
    }

    @Override
    protected String getOperationType() {
        return "LOAD_WORKTIME_STATUS";
    }
}
