package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Command to persist a session
 */
public class SaveSessionCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;

    public SaveSessionCommand(WorkUsersSessionsStates session) {
        this.session = session;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        try {
            // Use DataAccessService to write session file
            context.getDataAccessService().writeLocalSessionFile(session);

            // Also update the user's status in the centralized status system
            // This ensures that the status is always in sync with the session file
            context.getSessionStatusService().updateSessionStatus(session);

            LoggerUtil.debug(this.getClass(), String.format("Saved session for user %s", session.getUsername()));

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Session persistence failed for user %s: %s", session.getUsername(), e.getMessage()));
        }
        return null;
    }
}