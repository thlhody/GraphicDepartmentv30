// ===== UpdateSessionCalculationsCommand.java Changes =====
// Replace the existing class with this version that supports cache-only mode:

package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDateTime;

/**
 * Command to update session calculations with optional cache-only mode
 */
public class UpdateSessionCalculationsCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime explicitEndTime; // Optional parameter
    private final boolean cacheOnlyMode; // NEW: Don't write to file if true

    /**
     * Creates a command to update session calculations with cache-only option
     * @param session The session to update
     * @param explicitEndTime The explicit end time to use (optional)
     * @param cacheOnlyMode If true, only update cache, don't write to file
     */
    public UpdateSessionCalculationsCommand(WorkUsersSessionsStates session, LocalDateTime explicitEndTime, boolean cacheOnlyMode) {
        validateCondition(session != null, "Session cannot be null");

        this.session = session;
        this.explicitEndTime = explicitEndTime;
        this.cacheOnlyMode = cacheOnlyMode;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            debug(String.format("Updating calculations for session (user: %s, cache-only: %b)",
                    session.getUsername(), cacheOnlyMode));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);

            // Get user's schedule
            int userSchedule = ctx.getUserService().getUserById(session.getUserId())
                    .map(User::getSchedule).orElse(8); // Default to 8 hours if not found
            debug(String.format("User schedule: %d hours", userSchedule));

            // Use the explicit end time if provided, otherwise use standardized current time
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : timeValues.getCurrentTime();
            debug(String.format("Using end time: %s", endTime));

            // Use centralized calculation service with explicit end time and context
            WorkUsersSessionsStates updatedSession = ctx.updateSessionCalculations(session, endTime, userSchedule);

            // Log updated values
            debug(String.format("Updated calculations - Total worked: %d minutes, Final: %d minutes, Overtime: %d minutes",
                    updatedSession.getTotalWorkedMinutes(),
                    updatedSession.getFinalWorkedMinutes(),
                    updatedSession.getTotalOvertimeMinutes() != null ? updatedSession.getTotalOvertimeMinutes() : 0));

            if (cacheOnlyMode) {
                // *** CACHE-ONLY MODE: Update cache but don't write to file ***
                ctx.getSessionCacheService().updateCalculatedValues(session.getUsername(), updatedSession);
                info(String.format("Session calculations updated in cache only for user %s", session.getUsername()));
            } else {
                // *** NORMAL MODE: Save updated session to file (and cache) ***
                SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(updatedSession);
                ctx.executeCommand(saveCommand);
                info(String.format("Session calculations updated and saved for user %s", session.getUsername()));
            }

            return updatedSession;
        });
    }
}