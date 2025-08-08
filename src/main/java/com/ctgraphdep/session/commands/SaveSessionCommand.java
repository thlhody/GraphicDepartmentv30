package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;

import java.time.LocalDateTime;

public class SaveSessionCommand extends BaseSessionCommand<WorkUsersSessionsStates> {

    private final WorkUsersSessionsStates session;

    // Creates a command to save a session
    public SaveSessionCommand(WorkUsersSessionsStates session) {
        validateCondition(session != null, "Session cannot be null");
        validateCondition(session != null && session.getUsername() != null, "Session username cannot be null");
        this.session = session;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            String username = session.getUsername();

            if (session.getLastActivity() == null) {
                session.setLastActivity(LocalDateTime.now());
            }

            // Single call handles cache + file write + status update coordination
            boolean success = ctx.getSessionCacheService().writeSessionWithWriteThrough(session);
            if (!success) {
                throw new RuntimeException("Failed to save session for user: " + username);
            }

            // Status service update (if needed separately)
            ctx.getSessionStatusService().updateSessionStatus(session);

            return session;
        });
    }
}