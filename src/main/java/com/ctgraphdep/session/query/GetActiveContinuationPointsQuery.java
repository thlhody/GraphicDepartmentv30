package com.ctgraphdep.session.query;

import com.ctgraphdep.model.ContinuationPoint;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Query to retrieve active continuation points for a user on a specific date
 */
public class GetActiveContinuationPointsQuery implements SessionQuery<List<ContinuationPoint>> {
    private final String username;
    private final LocalDate sessionDate;

    public GetActiveContinuationPointsQuery(String username, LocalDate sessionDate) {
        this.username = username;
        this.sessionDate = sessionDate;
    }

    @Override
    public List<ContinuationPoint> execute(SessionContext context) {
        try {
            return context.getContinuationTrackingService()
                    .getActiveContinuationPoints(username, sessionDate);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error retrieving continuation points for user %s: %s",
                            username, e.getMessage()));
            return Collections.emptyList();
        }
    }
}