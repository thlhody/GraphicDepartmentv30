package com.ctgraphdep.session;

import lombok.Value;

/**
 * Value class for navigation context information
 */
@Value
public class NavigationContext {
    boolean completedSessionToday;
    boolean isTeamLeaderView;
    String dashboardUrl;
}