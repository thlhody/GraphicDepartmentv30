package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

public class AutoEndSessionCommand extends BaseWorktimeUpdateSessionCommand<Boolean> {

    private final LocalDateTime endTime;

    // Retry parameters
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    public AutoEndSessionCommand(String username, Integer userId, LocalDateTime endTime) {
        super(username, userId);
        validateCondition(endTime != null, "End time cannot be null");
        this.endTime = endTime;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithDefault(context, ctx -> {
            info(String.format("Executing auto end session for user %s at %s", username, endTime));

            // Stop all monitoring activity to avoid conflicts
            info(String.format("Stopping all monitoring for user %s before end session", username));
            ctx.getSessionMonitorService().stopMonitoring(username);

            try {
                ctx.getSessionMonitorService().clearMonitoring(username);
            } catch (Exception e) {
                warn(String.format("Non-critical error clearing monitoring: %s", e.getMessage()));
            }

            // Pause briefly to allow any in-flight operations to complete
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Execute with retry logic
            return executeWithRetry(() -> {
                // Get current session
                GetCurrentSessionQuery sessionQuery = ctx.getCommandFactory().createGetCurrentSessionQuery(username, userId);
                WorkUsersSessionsStates session = ctx.executeQuery(sessionQuery);

                if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                    warn("No active session to end automatically");
                    return false;
                }

                // Update the session with calculations to the scheduled end time
                UpdateSessionCalculationsCommand updateCommand = ctx.getCommandFactory().createUpdateSessionCalculationsCommand(session, endTime);
                session = ctx.executeCommand(updateCommand);

                // Save the updated session to file before ending
                SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
                ctx.executeCommand(saveCommand);

                // ENHANCED: Apply special day logic to worktime entry if needed
                updateWorktimeEntryWithSpecialDayLogic(session, ctx);

                // Create end day command to properly close the session
                EndDayCommand endDayCommand = ctx.getCommandFactory().createEndDayCommand(username, userId, null, endTime);
                WorkUsersSessionsStates finalSession = ctx.executeCommand(endDayCommand);

                boolean success = finalSession != null && WorkCode.WORK_OFFLINE.equals(finalSession.getSessionStatus());
                info(String.format("Auto end session completed for user %s: %s", username, success ? "SUCCESS" : "FAILED"));

                return success;
            });

        }, false);
    }

    // ========================================================================
    // ABSTRACT METHOD IMPLEMENTATIONS - AutoEndSessionCommand specific logic
    // ========================================================================

    @Override
    protected WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context) {
        // Auto-end should find existing entry or create new one
        return findOrCreateNewEntry(workDate, session, context);
    }

    @Override
    protected void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("auto end session");

        // Set auto-end time
        entry.setDayEndTime(endTime);
        entry.setAdminSync(MergingStatusConstants.USER_INPUT); // Auto-ended by system
    }

    @Override
    protected void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("post-special-day auto end session");

        // Re-apply auto-end sync status
        entry.setAdminSync(MergingStatusConstants.USER_INPUT);
    }

    @Override
    protected String getCommandDescription() {
        return "auto end session";
    }

    // ========================================================================
    // PRESERVED ORIGINAL HELPER METHODS
    // ========================================================================

    private Boolean executeWithRetry(RetryOperation operation) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                debug(String.format("Auto end session attempt %d/%d", attempt, MAX_RETRIES));
                return operation.execute();

            } catch (Exception e) {
                lastException = e;
                warn(String.format("Auto end session attempt %d failed: %s", attempt, e.getMessage()));

                if (attempt < MAX_RETRIES) {
                    try {
                        long delay = INITIAL_RETRY_DELAY_MS * attempt;
                        debug(String.format("Retrying in %d ms", delay));
                        TimeUnit.MILLISECONDS.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                }
            }
        }

        error(String.format("All %d attempts failed for auto end session", MAX_RETRIES), lastException);
        throw lastException;
    }

    @FunctionalInterface
    private interface RetryOperation {
        Boolean execute() throws Exception;
    }
}