package com.ctgraphdep.session.query;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Base class for queries that operate on user sessions.
 *
 * @param <T> The query result type
 */
public abstract class BaseUserSessionQuery<T> extends BaseSessionQuery<T> {

    protected final String username;
    protected final Integer userId;

    /**
     * Creates a new user session query with username and user ID.
     *
     * @param username The username
     * @param userId The user ID
     */
    protected BaseUserSessionQuery(String username, Integer userId) {
        if (username == null || username.trim().isEmpty()) {
            LoggerUtil.logAndThrow(this.getClass(), "Username cannot be null or empty",
                    new IllegalArgumentException("Username cannot be null or empty"));
        }

        if (userId == null) {
            LoggerUtil.logAndThrow(this.getClass(), "User ID cannot be null",
                    new IllegalArgumentException("User ID cannot be null"));
        }

        this.username = username;
        this.userId = userId;
    }

    /**
     * Gets the current session for the user.
     *
     * @param context The session context
     * @return The user's current session or null if no session exists
     */
    protected WorkUsersSessionsStates getCurrentSession(SessionContext context) {
        try {
            WorkUsersSessionsStates session = context.getCurrentSession(username, userId);
            if (session == null) {
                debug("No session found for user: " + username);
            }
            return session;
        } catch (Exception e) {
            error("Error getting current session: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if session is valid (not null).
     *
     * @param session The session to check
     * @return true if the session is valid, false otherwise
     */
    protected boolean isValidSession(WorkUsersSessionsStates session) {
        if (session == null) {
            warn("Invalid session: null");
            return false;
        }
        return true;
    }
}