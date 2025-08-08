package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.UpdateSessionActivityCommand;
import com.ctgraphdep.session.query.IsInTempStopMonitoringQuery;

// Command to continue a temporary stop
public class ContinueTempStopCommand extends BaseNotificationCommand<Boolean> {

    public ContinueTempStopCommand(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Continuing temporary stop for user %s", username));

            // Update the session if needed (e.g., refresh last activity timestamp)
            UpdateSessionActivityCommand updateCommand = ctx.getCommandFactory().createUpdateSessionActivityCommand(username, userId);
            ctx.executeCommand(updateCommand);

            // NEW: Check if already in temporary stop monitoring mode
            IsInTempStopMonitoringQuery isInTempStopQuery = ctx.getCommandFactory().createIsInTempStopMonitoringQuery(username);
            boolean inTempStopMonitoring = ctx.executeQuery(isInTempStopQuery);

            if (!inTempStopMonitoring) {
                // If somehow not in temp stop monitoring, explicitly activate it
                debug(String.format("User %s not in temp stop monitoring mode, activating", username));

                // Use pauseScheduleMonitoring which sets up the temp stop tracking
                ctx.getSessionMonitorService().pauseScheduleMonitoring(username);

                // Record temp stop notification explicitly if needed
                ctx.getSessionMonitorService().recordTempStopNotification(username, ctx.getValidationService()
                        .execute(ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand())
                        .getCurrentTime());
            } else {
                debug(String.format("User %s already in temp stop monitoring mode, continuing", username));
            }

            info(String.format("Successfully continued temporary stop for user %s", username));

            return true;
        });
    }
}