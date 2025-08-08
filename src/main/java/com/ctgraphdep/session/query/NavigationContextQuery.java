package com.ctgraphdep.session.query;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.session.NavigationContext;
import com.ctgraphdep.model.User;

// Query to determine navigation context for user session page
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

        // Determine dashboard URL based on user role - checking all roles
        String dashboardUrl;
        boolean isTeamLeaderView = false;

        // Normalize the role by removing any ROLE_ prefix
        String normalizedRole = user.getRole().replace("ROLE_", "");

        // Set dashboard URL and team leader flag based on role
        switch (normalizedRole) {
            case SecurityConstants.ROLE_ADMIN:
                dashboardUrl = "/admin";
                break;
            case SecurityConstants.ROLE_TEAM_LEADER:
                dashboardUrl = "/team-lead";
                isTeamLeaderView = true;
                break;
            case SecurityConstants.ROLE_TL_CHECKING:
                dashboardUrl = "/team-checking";
                isTeamLeaderView = true;
                break;
            case SecurityConstants.ROLE_USER_CHECKING:
                dashboardUrl = "/user-checking";
                break;
            case SecurityConstants.ROLE_CHECKING:
                dashboardUrl = "/checking";
                break;
            case SecurityConstants.ROLE_USER:
                dashboardUrl = "/user";
                break;
            default:
                // Default to user dashboard if role not recognized
                dashboardUrl = "/user";
        }

        return new NavigationContext(completedSessionToday, isTeamLeaderView, dashboardUrl);
    }
}