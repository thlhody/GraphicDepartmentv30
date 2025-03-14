package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import java.time.LocalDate;

/**
 * Query to check if a user has any unresolved sessions from previous days
 */
public class UnresolvedSessionQuery implements SessionQuery<Boolean> {
    private final String username;
    private final Integer userId;

    public UnresolvedSessionQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {

        // Get standardized time values
        GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

        // Check for incomplete sessions from previous days
        GetCurrentSessionQuery sessionQuery = new GetCurrentSessionQuery(username, userId);
        WorkUsersSessionsStates session = context.executeQuery(sessionQuery);


        boolean hasPreviousDaySession = false;

        if (session != null && session.getDayStartTime() != null) {
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();
            LocalDate today = timeValues.getCurrentDate();

            hasPreviousDaySession = sessionDate.isBefore(today) && (session.getDayEndTime() == null || !session.getWorkdayCompleted());
        }

        // Log if any unresolved sessions are found
        if (hasPreviousDaySession) {
            LoggerUtil.info(this.getClass(), String.format("User %s has unresolved session - needs resolution", username));
        }

        return hasPreviousDaySession;
    }
}