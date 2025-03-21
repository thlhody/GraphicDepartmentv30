package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Command to update the last activity timestamp of a session
 */
public class UpdateSessionActivityCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a new command to update session activity
     *
     * @param username The username
     * @param userId The user ID
     */
    public UpdateSessionActivityCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Updating activity timestamp for user %s", username));

            // Get standardized time values using the new validation system
            GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);

            // Get current session
            WorkUsersSessionsStates session = context.getCurrentSession(username, userId);
            if (session == null) {
                LoggerUtil.warn(this.getClass(), String.format("Session not found for user %s", username));
                return false;
            }

            // Update last activity timestamp with standardized time
            session.setLastActivity(timeValues.getCurrentTime());

            // Save the session using command factory
            SaveSessionCommand saveCommand = context.getCommandFactory().createSaveSessionCommand(session);
            context.executeCommand(saveCommand);

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating session activity for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}