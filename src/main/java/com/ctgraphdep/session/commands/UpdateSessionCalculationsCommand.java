package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;

import java.time.LocalDateTime;

public class UpdateSessionCalculationsCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime explicitEndTime; // Optional parameter
    private final boolean cacheOnlyMode; // Don't write to file if true

    public UpdateSessionCalculationsCommand(WorkUsersSessionsStates session, LocalDateTime explicitEndTime, boolean cacheOnlyMode) {
        validateCondition(session != null, "Session cannot be null");

        this.session = session;
        this.explicitEndTime = explicitEndTime;
        this.cacheOnlyMode = cacheOnlyMode;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            String username = session.getUsername();
            Integer userId = session.getUserId();

            debug(String.format("Updating calculations for session (user: %s, cache-only: %b)", username, cacheOnlyMode));


            // Get user's schedule
            int userSchedule = ctx.getUserService().getUserById(userId).map(User::getSchedule).orElse(8); // Default to 8 hours if not found
            debug(String.format("User schedule: %d hours", userSchedule));

            // Use the explicit end time if provided, otherwise use standardized current time
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : getStandardCurrentTime(context);
            debug(String.format("Using end time: %s", endTime));

            // Update session calculations using centralized calculation service
            WorkUsersSessionsStates updatedSession = ctx.updateSessionCalculations(session, endTime, userSchedule);

            // Log updated values
            debug(String.format("Updated calculations - Total worked: %d minutes, Final: %d minutes, Overtime: %d minutes",
                    updatedSession.getTotalWorkedMinutes(), updatedSession.getFinalWorkedMinutes(),
                    updatedSession.getTotalOvertimeMinutes() != null ? updatedSession.getTotalOvertimeMinutes() : 0));

            // Delegate to SessionCacheService for all cache/file operations
            boolean success = ctx.getSessionCacheService().updateSessionCalculationsWithWriteThrough(updatedSession, cacheOnlyMode);

            if (!success) {
                debug(String.format("Failed to update session calculations for user %s (cache-only: %b)", username, cacheOnlyMode));
                throw new RuntimeException("Failed to update session calculations");
            }

            // Read back the updated session from cache to ensure consistency
            WorkUsersSessionsStates finalSession = ctx.getSessionCacheService().readSessionWithFallback(username, userId);

            if (finalSession == null) {
                debug(String.format("Failed to read back updated session for user %s", username));
                throw new RuntimeException("Failed to read updated session from cache");
            }

            if (cacheOnlyMode) {
                info(String.format("Session calculations updated in cache-only mode for user %s", username));
            } else {
                info(String.format("Session calculations updated with write-through for user %s", username));
            }

            return finalSession;
        });
    }
}