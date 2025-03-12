package com.ctgraphdep.session.query;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Query to check if a session needs resolution
 */
public class NeedsResolutionQuery implements SessionQuery<Boolean> {
    private final WorkUsersSessionsStates session;

    public NeedsResolutionQuery(WorkUsersSessionsStates session) {
        this.session = session;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            return SessionValidator.needsResolution(session, this.getClass());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking if session needs resolution: %s", e.getMessage()));
            return false;
        }
    }
}