package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

/**
 * Command to activate hourly monitoring for a user
 */
public class ActivateHourlyMonitoringCommand extends BaseNotificationCommand<Boolean> {

    /**
     * Creates a new command to activate hourly monitoring
     *
     * @param username The username
     */
    public ActivateHourlyMonitoringCommand(String username) {
        super(username, null);  // UserID not needed for this command
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Activating hourly monitoring for user %s", username));

            // Get standardized time values using the validation system
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);

            // Use the service method instead of direct map manipulation
            ctx.getSessionMonitorService().activateHourlyMonitoring(username, timeValues.getCurrentTime());

            info(String.format("Successfully activated hourly monitoring for user %s", username));

            return true;
        });
    }
}