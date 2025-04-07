package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.session.util.SessionEntityBuilder;

/**
 * Query that resolves which session to use for a user (existing local or new)
 */
public class ResolveSessionQuery implements SessionQuery<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    public ResolveSessionQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        try {
            // Try to read local session first
            WorkUsersSessionsStates localSession = context.getCurrentSession(username, userId);

            if (localSession != null) {
                LoggerUtil.debug(this.getClass(), String.format("Found local session for user %s", username));
                return localSession;
            }

            // If no local session exists, create a new one using SessionEntityBuilder
            WorkUsersSessionsStates newSession = SessionEntityBuilder.createSession(username, userId);

            // Update status to offline
            SessionEntityBuilder.updateSession(newSession, builder -> builder.status(WorkCode.WORK_OFFLINE));

            // Save the new session
            context.saveSession(newSession);

            LoggerUtil.info(this.getClass(), String.format("Created new offline session for user %s", username));

            return newSession;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Critical error resolving session for user %s: %s", username, e.getMessage()));

            // Fallback to creating a new offline session using SessionEntityBuilder
            WorkUsersSessionsStates fallbackSession = SessionEntityBuilder.createSession(username, userId);

            // Update status to offline
            SessionEntityBuilder.updateSession(fallbackSession, builder -> builder.status(WorkCode.WORK_OFFLINE));

            context.saveSession(fallbackSession);

            return fallbackSession;
        }
    }
}