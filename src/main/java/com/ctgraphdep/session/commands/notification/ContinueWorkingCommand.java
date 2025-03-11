package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;

public class ContinueWorkingCommand implements SessionCommand<Void> {
    private final String username;
    private final Integer userId;
    private final boolean isHourly;

    public ContinueWorkingCommand(String username, Integer userId, boolean isHourly) {
        this.username = username;
        this.userId = userId;
        this.isHourly = isHourly;
    }

    @Override
    public Void execute(SessionContext context) {
        // Get standardized time values
        GetSessionTimeValuesQuery timeQuery = new GetSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);
        // Cancel any backup tasks
        context.getBackupService().cancelBackupTask(username);

        // Activate hourly monitoring if needed
        if (!isHourly) {
            ActivateHourlyMonitoringCommand command = new ActivateHourlyMonitoringCommand(username);
            context.executeCommand(command);
        }

        // Record continuation point
        context.getContinuationTrackingService().recordContinuationPoint(username, userId, timeValues.getCurrentTime(), isHourly);

        return null;
    }
}