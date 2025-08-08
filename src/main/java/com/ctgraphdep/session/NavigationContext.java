package com.ctgraphdep.session;

// Value class for navigation context information
public record NavigationContext(boolean completedSessionToday, boolean isTeamLeaderView, String dashboardUrl) {
}