package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;

import java.time.LocalDateTime;

// Command to update the last activity timestamp of a session
public class UpdateSessionActivityCommand extends BaseSessionCommand<Boolean> {
    private final String username;
    private final Integer userId;

    public UpdateSessionActivityCommand(String username, Integer userId) {
        validateUsername(username);
        validateUserId(userId);

        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithDefault(context, ctx -> {
            debug(String.format("Updating activity timestamp for user %s", username));

            LocalDateTime timeDate = getStandardCurrentTime(context);

            // Get current session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);
            if (session == null) {
                warn(String.format("Session not found for user %s", username));
                return false;
            }

            // Update last activity timestamp with standardized time
            session.setLastActivity(timeDate);
            debug(String.format("Updated last activity to %s", timeDate));

            // Save the session using command factory
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            info(String.format("Activity timestamp updated for user %s", username));
            return true;
        }, false);
    }
}