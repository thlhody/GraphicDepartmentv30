package com.ctgraphdep.session.query;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.session.NavigationContext;
import com.ctgraphdep.model.User;
import com.ctgraphdep.session.config.CommandConstants;

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
                dashboardUrl = SecurityConstants.ADMIN_URL;
                break;
            case SecurityConstants.ROLE_TEAM_LEADER:
                dashboardUrl = SecurityConstants.TEAM_LEAD_URL;
                isTeamLeaderView = true;
                break;
            case SecurityConstants.ROLE_TL_CHECKING:
                dashboardUrl = SecurityConstants.TL_CHECKING_URL;
                isTeamLeaderView = true;
                break;
            case SecurityConstants.ROLE_USER_CHECKING:
                dashboardUrl = SecurityConstants.USER_CHECKING_URL;
                break;
            case SecurityConstants.ROLE_CHECKING:
                dashboardUrl = SecurityConstants.CHECKING_URL;
                break;
            case SecurityConstants.ROLE_USER:
                dashboardUrl = SecurityConstants.USER_URL;

                break;
            default:
                // Default to user dashboard if role not recognized
                dashboardUrl = SecurityConstants.USER_URL;
        }

        return new NavigationContext(completedSessionToday, isTeamLeaderView, dashboardUrl);
    }
}