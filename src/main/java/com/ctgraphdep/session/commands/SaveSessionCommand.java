package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;

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

            if (session.getLastActivity() == null) {
                session.setLastActivity(getStandardCurrentTime(context));
            }

            // Single call handles cache + file write + status update coordination
            boolean success = ctx.getSessionCacheService().writeSessionWithWriteThrough(session);

            validateCondition(success, "Session cannot be null");

            // Status service update (if needed separately)
            ctx.getSessionStatusService().updateSessionStatus(session);

            return session;
        });
    }
}