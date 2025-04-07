package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;

/**
 * Command to persist a session
 */
public class SaveSessionCommand extends BaseSessionCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;

    /**
     * Creates a command to save a session
     *
     * @param session The session to save
     */
    public SaveSessionCommand(WorkUsersSessionsStates session) {
        validateCondition(session != null, "Session cannot be null");
        validateCondition(session != null && session.getUsername() != null, "Session username cannot be null");
        this.session = session;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            String username = session.getUsername();
            debug(String.format("Saving session for user %s", username));

            // Use DataAccessService to write session file
            ctx.getDataAccessService().writeLocalSessionFile(session);

            // Also update the user's status in the centralized status system
            // This ensures that the status is always in sync with the session file
            ctx.getSessionStatusService().updateSessionStatus(session);

            info(String.format("Saved session for user %s with status %s", username, session.getSessionStatus()));

            return session;
        });
    }
}