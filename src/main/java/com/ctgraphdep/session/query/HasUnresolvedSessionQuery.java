package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;

/**
 * Query to check if a user has any unresolved sessions that need resolution
 */
public class HasUnresolvedSessionQuery implements SessionQuery<Boolean> {
    private final String username;
    private final Integer userId;

    public HasUnresolvedSessionQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            // Get standardized time values
            GetSessionTimeValuesQuery timeQuery = new GetSessionTimeValuesQuery();
            GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

            // Check for unresolved continuation points from midnight session end
            boolean hasUnresolvedContinuations = context.getContinuationTrackingService().hasUnresolvedMidnightEnd(username);

            // Check if current session needs resolution (ended at midnight or incomplete)
            WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

            boolean needsResolution = false;

            if (session != null) {
                // Check if session is from a previous day
                LocalDate sessionDate = session.getDayStartTime() != null ? session.getDayStartTime().toLocalDate() : null;
                LocalDate today = timeValues.getCurrentDate();

                // Session needs resolution if:
                // 1. It's from a previous day AND
                // 2. Either workdayCompleted is false OR it's in a non-complete state
                needsResolution = sessionDate != null && sessionDate.isBefore(today) && (!session.getWorkdayCompleted() || !WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()));
            }

            // Log if session needs resolution
            if (hasUnresolvedContinuations || needsResolution) {
                LoggerUtil.info(this.getClass(), String.format("User %s has unresolved session that needs resolution", username));
            }

            return hasUnresolvedContinuations || needsResolution;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking for unresolved sessions for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }
}