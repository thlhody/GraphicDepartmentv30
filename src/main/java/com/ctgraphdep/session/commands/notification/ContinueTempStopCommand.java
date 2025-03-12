package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.commands.UpdateSessionActivityCommand;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Command to continue a temporary stop
 */
public class ContinueTempStopCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a new command to continue temporary stop
     *
     * @param username The username
     * @param userId The user ID
     */
    public ContinueTempStopCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Continuing temporary stop for user %s", username));

            // Get standardized time values
            GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
            GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

            // Cancel any backup tasks
            context.getBackupService().cancelBackupTask(username);

            // Record continuation of temporary stop
            context.getContinuationTrackingService().recordTempStopContinuation(username, userId, timeValues.getCurrentTime());

            // Update the session if needed (e.g., refresh last activity timestamp)
            UpdateSessionActivityCommand updateCommand = context.getCommandFactory().createUpdateSessionActivityCommand(username, userId);
            context.executeCommand(updateCommand);

            LoggerUtil.info(this.getClass(), String.format("Successfully continued temporary stop for user %s", username));

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error continuing temporary stop for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}