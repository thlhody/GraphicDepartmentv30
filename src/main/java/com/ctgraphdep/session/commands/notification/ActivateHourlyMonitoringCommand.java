package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;
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

            // IMPROVEMENT: Verify session state before activating hourly monitoring
            boolean canActivate = validateSessionState(ctx);
            if (!canActivate) {
                warn(String.format("Cannot activate hourly monitoring for user %s - invalid session state", username));
                return false;
            }

            // Use the service method instead of direct map manipulation
            ctx.getSessionMonitorService().activateHourlyMonitoring(username, timeValues.getCurrentTime());

            info(String.format("Successfully activated hourly monitoring for user %s", username));

            return true;
        });
    }

    /**
     * Validates that the session is in an appropriate state for hourly monitoring.
     * Hourly monitoring should only be active if the user is in WORK_ONLINE status.
     *
     * @param context The session context
     * @return true if session state is valid for hourly monitoring
     */
    private boolean validateSessionState(SessionContext context) {
        try {
            // Get the current session
            GetCurrentSessionQuery sessionQuery = context.getCommandFactory().createGetCurrentSessionQuery(username, null);
            WorkUsersSessionsStates session = context.executeQuery(sessionQuery);

            // If session doesn't exist, we can't activate hourly monitoring
            if (session == null) {
                warn(String.format("Session not found for user %s, cannot activate hourly monitoring", username));
                return false;
            }

            // Check session status - should be WORK_ONLINE
            boolean isOnline = WorkCode.WORK_ONLINE.equals(session.getSessionStatus());

            if (!isOnline) {
                warn(String.format("Session status for user %s is %s, not WORK_ONLINE - cannot activate hourly monitoring", username, session.getSessionStatus()));
            }

            return isOnline;

        } catch (Exception e) {
            // Log error but don't fail validation - default to allowing activation in case of errors
            error(String.format("Error validating session state for user %s: %s", username, e.getMessage()), e);
            return true; // Default to allowing activation on error for backward compatibility
        }
    }
}
