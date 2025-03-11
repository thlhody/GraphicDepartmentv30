package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;

/**
 * Query to check if a user has a completed session for today
 */
public class HasCompletedSessionForTodayQuery implements SessionQuery<Boolean> {
    private final String username;
    private final Integer userId;

    public HasCompletedSessionForTodayQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            WorkUsersSessionsStates existingSession = context.getDataAccessService().readLocalSessionFile(username, userId);

            return existingSession != null && existingSession.getDayStartTime() != null &&
                    existingSession.getDayStartTime().toLocalDate().equals(LocalDate.now()) &&
                    WorkCode.WORK_OFFLINE.equals(existingSession.getSessionStatus()) &&
                    existingSession.getWorkdayCompleted();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking completed session for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}