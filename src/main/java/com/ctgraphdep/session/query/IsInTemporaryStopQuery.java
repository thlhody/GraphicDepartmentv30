package com.ctgraphdep.session.query;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Query to check if a user is in temporary stop state
 */
public class IsInTemporaryStopQuery implements SessionQuery<Boolean> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a new query to check if a user is in temporary stop
     *
     * @param username The username of the user
     * @param userId The user ID
     */
    public IsInTemporaryStopQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            // Get the current session
            WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

            // Check if session exists and is in temporary stop
            return session != null && WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking temporary stop status for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}