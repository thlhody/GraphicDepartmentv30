package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Query to check if a session is from a previous day
 */
public class IsPreviousDaySessionQuery implements SessionQuery<Boolean> {
    private final WorkUsersSessionsStates session;

    public IsPreviousDaySessionQuery(WorkUsersSessionsStates session) {
        this.session = session;
    }

    @Override
    public Boolean execute(SessionContext context) {
        // Get standardized time values
        GetSessionTimeValuesQuery timeQuery = new GetSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

        if (session == null ||
                session.getDayStartTime() == null ||
                WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
            return false;
        }

        LocalDate sessionDate = session.getDayStartTime().toLocalDate();
        LocalDate today = timeValues.getCurrentDate();

        return sessionDate.isBefore(today);
    }
}