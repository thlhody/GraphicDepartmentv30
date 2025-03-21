package com.ctgraphdep.session.query;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Comprehensive query to check various session-related statuses and conditions
 */
public class SessionStatusQuery implements SessionQuery<SessionStatusQuery.SessionStatus> {
    private final String username;
    private final Integer userId;

    public SessionStatusQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public SessionStatus execute(SessionContext context) {
        try {
            // Get current session using GetCurrentSessionQuery
            GetCurrentSessionQuery sessionQuery = context.getCommandFactory().createGetCurrentSessionQuery(username, userId);
            WorkUsersSessionsStates session = context.executeQuery(sessionQuery);

            // Get standardized time values using the new validation system
            GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
            LocalDate today = timeValues.getCurrentDate();

            // Check for unresolved worktime entries
            UnresolvedWorkTimeQuery unresolvedQuery = new UnresolvedWorkTimeQuery(username, userId);
            var unresolvedEntries = context.executeQuery(unresolvedQuery);

            // Construct and return comprehensive session status
            return new SessionStatus(
                    session,
                    session != null && WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()),
                    session != null && WorkCode.WORK_ONLINE.equals(session.getSessionStatus()),
                    session != null && session.getDayStartTime() != null &&
                            session.getDayStartTime().toLocalDate().equals(today) &&
                            WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()) &&
                            Boolean.TRUE.equals(session.getWorkdayCompleted()),
                    !unresolvedEntries.isEmpty()
            );

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking session status for user %s: %s", username, e.getMessage()));

            // Return a default/safe status
            return new SessionStatus(
                    null,
                    false,
                    false,
                    false,
                    false
            );
        }
    }

    /**
     * Comprehensive session status information
     */
    @Getter
    public static class SessionStatus {
        private final WorkUsersSessionsStates session;
        private final boolean isInTemporaryStop;
        private final boolean isOnline;
        private final boolean hasCompletedSessionToday;
        private final boolean hasUnresolvedWorkEntries;

        public SessionStatus(
                WorkUsersSessionsStates session,
                boolean isInTemporaryStop,
                boolean isOnline,
                boolean hasCompletedSessionToday,
                boolean hasUnresolvedWorkEntries
        ) {
            this.session = session;
            this.isInTemporaryStop = isInTemporaryStop;
            this.isOnline = isOnline;
            this.hasCompletedSessionToday = hasCompletedSessionToday;
            this.hasUnresolvedWorkEntries = hasUnresolvedWorkEntries;
        }
    }
}