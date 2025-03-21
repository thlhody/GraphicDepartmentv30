package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.session.NavigationContext;
import com.ctgraphdep.model.User;

/**
 * Query to determine navigation context for user session page
 */
public class NavigationContextQuery implements SessionQuery<NavigationContext> {
    private final User user;

    public NavigationContextQuery(User user) {
        this.user = user;
    }

    @Override
    public NavigationContext execute(SessionContext context) {
        // Use the SessionStatusQuery to check session status
        SessionStatusQuery statusQuery = context.getCommandFactory().createSessionStatusQuery(user.getUsername(), user.getUserId());
        SessionStatusQuery.SessionStatus status = context.executeQuery(statusQuery);

        boolean completedSessionToday = status.isHasCompletedSessionToday();

        // Determine dashboard URL based on user role
        boolean isTeamLeaderView = user.hasRole("TEAM_LEADER");
        String dashboardUrl = isTeamLeaderView ? "/team-lead" : "/user";

        return new NavigationContext(completedSessionToday, isTeamLeaderView, dashboardUrl);
    }
}