package com.ctgraphdep.worktime.commands.status;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to load worktime data for status display (read-only).
 * Handles both own data (local files) and other user data (network files).
 * Used by StatusService for cross-user worktime viewing.
 */
public class LoadUserWorktimeStatusCommand extends WorktimeOperationCommand<List<WorkTimeTable>> {
    private final String targetUsername;
    private final Integer targetUserId;
    private final int year;
    private final int month;

    public LoadUserWorktimeStatusCommand(WorktimeOperationContext context, String targetUsername,
                                         Integer targetUserId, int year, int month) {
        super(context);
        this.targetUsername = targetUsername;
        this.targetUserId = targetUserId;
        this.year = year;
        this.month = month;
    }

    @Override
    protected void validate() {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Target username cannot be null or empty");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("Target user ID cannot be null");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating worktime status load: target=%s, period=%d/%d", targetUsername, year, month));

        // Note: No permission validation here - frontend handles field visibility
        // Role-based context is determined in execution
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Loading worktime status for %s - %d/%d", targetUsername, year, month));

        try {
            // Determine current user context (respects admin elevation)
            String currentUsername = context.getCurrentUsername();
            boolean isViewingOwnData = targetUsername.equals(currentUsername);

            List<WorkTimeTable> worktimeData;

            if (isViewingOwnData) {
                // Own data - read from LOCAL files for most current data
                LoggerUtil.debug(this.getClass(), String.format("Loading own worktime data from local files for %s", targetUsername));
                worktimeData = context.loadUserWorktime(targetUsername, year, month);

            } else {
                // FIXED: Other user data - read DIRECTLY from user's network worktime file
                LoggerUtil.debug(this.getClass(), String.format(
                        "Loading other user worktime data DIRECTLY from network files for %s", targetUsername));

                try {
                    worktimeData = context.loadWorktimeFromNetwork(targetUsername, year, month);

                    // Filter for target user ID (safety check) and sort by date
                    worktimeData = worktimeData.stream()
                            .filter(entry -> targetUserId.equals(entry.getUserId()))
                            .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                            .collect(Collectors.toList());

                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Direct network file read failed for %s - %d/%d: %s", targetUsername, year, month, e.getMessage()));
                    worktimeData = new ArrayList<>(); // Return empty list as fallback
                }
            }

            if (worktimeData == null) {
                worktimeData = new ArrayList<>();
            }

            String message = String.format("Loaded %d worktime entries for %s - %d/%d (source: %s)",
                    worktimeData.size(), targetUsername, year, month,
                    isViewingOwnData ? "local" : "direct-network");

            LoggerUtil.info(this.getClass(), message);

            return OperationResult.success(message, getOperationType(), worktimeData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading worktime status for %s - %d/%d: %s", targetUsername, year, month, e.getMessage()), e);

            // Return empty list on error as requested
            return OperationResult.success(
                    String.format("No worktime data available for %s - %d/%d", targetUsername, year, month),
                    getOperationType(), new ArrayList<>());
        }
    }

    @Override
    protected String getCommandName() {
        return String.format("LoadUserWorktimeStatus[%s, %d/%d]", targetUsername, year, month);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.LOAD_USER_WORKTIME;
    }
}