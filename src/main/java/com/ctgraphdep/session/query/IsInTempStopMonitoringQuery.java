package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;

// Query to check if a user is in temporary stop monitoring mode.
// This examines the SessionMonitorService's internal tracking to determine if special temporary stop notifications should be displayed.
public class IsInTempStopMonitoringQuery extends BaseSessionQuery<Boolean> {
    private final String username;

    // Creates a query to check if a user is in temporary stop monitoring mode
    public IsInTempStopMonitoringQuery(String username) {
        validateUsername(username);
        this.username = username;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithDefault(context, ctx -> {
            debug(String.format("Checking if user %s is in temporary stop monitoring mode", username));

            // Delegate to the SessionMonitorService method
            boolean isInTempStop = ctx.getSessionMonitorService().isInTempStopMonitoring(username);

            debug(String.format("User %s is %s temporary stop monitoring mode", username, isInTempStop ? "in" : "not in"));

            return isInTempStop;
        }, false); // Default to false if there's an error
    }
}