package com.ctgraphdep.session.query;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Query to calculate raw work minutes for a session
 */
public class CalculateRawWorkMinutesQuery implements SessionQuery<Integer> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime endTime;

    public CalculateRawWorkMinutesQuery(WorkUsersSessionsStates session, LocalDateTime endTime) {
        this.session = session;
        this.endTime = endTime;
    }

    @Override
    public Integer execute(SessionContext context) {
        try {
            return context.calculateRawWorkMinutes(session, endTime);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error calculating raw work minutes for user %s: %s",
                            session.getUsername(), e.getMessage()));
            return 0;
        }
    }
}