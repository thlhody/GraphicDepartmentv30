package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Command to automatically end a session at a scheduled time
 * with proper monitoring shutdown before file operations
 */
public class AutoEndSessionCommand extends BaseSessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final LocalDateTime endTime;

    // Retry parameters
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    /**
     * Creates a command to auto-end a session
     *
     * @param username The username
     * @param userId The user ID
     * @param endTime The scheduled end time
     */
    public AutoEndSessionCommand(String username, Integer userId, LocalDateTime endTime) {
        validateUsername(username);
        validateUserId(userId);
        validateCondition(endTime != null, "End time cannot be null");

        this.username = username;
        this.userId = userId;
        this.endTime = endTime;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithDefault(context, ctx -> {
            info(String.format("Executing auto end session for user %s at %s", username, endTime));

            // IMPORTANT: First stop all monitoring activity to avoid conflicts
            info(String.format("Stopping all monitoring for user %s before end session", username));

            // Stop monitoring in MonitoringStateService first
            ctx.getSessionMonitorService().stopMonitoring(username);

            // Additional call to clear monitoring state to ensure complete cleanup
            try {
                ctx.getSessionMonitorService().clearMonitoring(username);
            } catch (Exception e) {
                // Log but continue - this is a best-effort cleanup
                warn(String.format("Non-critical error clearing monitoring: %s", e.getMessage()));
            }

            // Pause briefly to allow any in-flight operations to complete
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Now proceed with session operations with retries
            return executeWithRetry(() -> {
                // 1. Get current session
                GetCurrentSessionQuery sessionQuery = ctx.getCommandFactory().createGetCurrentSessionQuery(username, userId);
                WorkUsersSessionsStates session = ctx.executeQuery(sessionQuery);

                if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                    warn("No active session to end automatically");
                    return false;
                }

                // 2. Update the session with calculations to the scheduled end time
                UpdateSessionCalculationsCommand updateCommand = ctx.getCommandFactory().createUpdateSessionCalculationsCommand(session, endTime);
                session = ctx.executeCommand(updateCommand);

                // 3. Save the updated session to file before ending
                SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
                ctx.executeCommand(saveCommand);

                // 4. Now end the session
                EndDayCommand endCommand = ctx.getCommandFactory().createEndDayCommand(username, userId, null, endTime);
                ctx.executeCommand(endCommand);

                info(String.format("Successfully ended scheduled session for user %s", username));
                return true;
                });  // Maximum of 3 retries
        }, false);
    }

    /**
     * Executes with retry logic for file access conflicts
     */
    private boolean executeWithRetry(RetryOperation operation ) {
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    info(String.format("Retry #%d for auto end session for user %s", attempt, username));
                }
                return operation.execute();
            } catch (Exception e) {
                lastException = e;

                // Check if this is a file access error
                if (isFileAccessError(e)) {
                    long delayMs = INITIAL_RETRY_DELAY_MS * (long)Math.pow(2, attempt);
                    warn(String.format("File access conflict detected, will retry in %d ms (attempt %d/%d)", delayMs, attempt + 1, MAX_RETRIES));

                    try {
                        TimeUnit.MILLISECONDS.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    // Not a file access error, don't retry
                    error(String.format("Error executing auto end session: %s", e.getMessage()), e);
                    break;
                }
            }
        }

        error(String.format("Failed to execute auto end session after %d retries: %s", MAX_RETRIES, lastException.getMessage()), lastException);

        return false;
    }

    /**
     * Determines if an exception is related to file access conflicts
     */
    private boolean isFileAccessError(Exception e) {
        String message = e.getMessage();
        if (message == null) return false;

        // Check for common file access error messages
        return message.contains("process cannot access the file") ||
                message.contains("being used by another process") ||
                message.contains("Failed to write file");
    }

    /**
     * Functional interface for retry operations
     */
    @FunctionalInterface
    private interface RetryOperation {
        boolean execute() throws Exception;
    }
}