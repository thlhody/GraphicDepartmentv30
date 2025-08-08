package com.ctgraphdep.session.query;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import lombok.Getter;

import java.time.LocalDate;

// Comprehensive query to check various session-related statuses and conditions
public class SessionStatusQuery extends BaseUserSessionQuery<SessionStatusQuery.SessionStatus> {

    // Creates a new query for checking session status
    public SessionStatusQuery(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public SessionStatus execute(SessionContext context) {
        return executeWithDefault(context, ctx -> {
            // Log start of the query
            info(String.format("Checking session status for user %s", username));

            // Get current session
            GetCurrentSessionQuery sessionQuery = ctx.getCommandFactory().createGetCurrentSessionQuery(username, userId);
            WorkUsersSessionsStates session = ctx.executeQuery(sessionQuery);

            LocalDate today = getStandardCurrentDate(context);

            // Check for unresolved worktime entries
            UnresolvedWorkTimeQuery unresolvedQuery = new UnresolvedWorkTimeQuery(username);
            var unresolvedEntries = ctx.executeQuery(unresolvedQuery);

            // Log session status details
            debug(String.format("Session status - Online: %b, Temp Stop: %b, Completed Today: %b, Unresolved Entries: %b",
                    session != null && WorkCode.WORK_ONLINE.equals(session.getSessionStatus()),
                    session != null && WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()),
                    session != null && session.getDayStartTime() != null && session.getDayStartTime().toLocalDate().equals(today)
                            && WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())
                            && Boolean.TRUE.equals(session.getWorkdayCompleted()),
                    !unresolvedEntries.isEmpty()
            ));

            // Construct comprehensive session status
            return new SessionStatus(session, session != null && WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()),
                    session != null && WorkCode.WORK_ONLINE.equals(session.getSessionStatus()),
                    session != null && session.getDayStartTime() != null && session.getDayStartTime().toLocalDate().equals(today) &&
                            WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()) && Boolean.TRUE.equals(session.getWorkdayCompleted()),
                    !unresolvedEntries.isEmpty()
            );
        }, new SessionStatus(null, false, false, false, false));
    }

    //Comprehensive session status information
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