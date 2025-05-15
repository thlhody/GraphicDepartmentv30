package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;

import java.time.LocalDateTime;

/**
 * Command to automatically end a session at a scheduled time
 * with proper calculation updating
 */
public class AutoEndSessionCommand extends BaseSessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final LocalDateTime endTime;

    /**
     * Creates a command to auto-end a session
     *
     * @param username The username
     * @param userId The user ID
     * @param endTime The scheduled end time
     */
    public AutoEndSessionCommand(String username, Integer userId, LocalDateTime endTime) {
        validateUsername(username);
        validateUserId(userId);
        validateCondition(endTime != null, "End time cannot be null");

        this.username = username;
        this.userId = userId;
        this.endTime = endTime;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithDefault(context, ctx -> {
            info(String.format("Executing auto end session for user %s at %s", username, endTime));

            // 1. Get current session
            GetCurrentSessionQuery sessionQuery = ctx.getCommandFactory().createGetCurrentSessionQuery(username, userId);
            WorkUsersSessionsStates session = ctx.executeQuery(sessionQuery);

            if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                warn("No active session to end automatically");
                return false;
            }

            // 2. Update the session with calculations to the scheduled end time
            UpdateSessionCalculationsCommand updateCommand =
                    ctx.getCommandFactory().createUpdateSessionCalculationsCommand(session, endTime);
            session = ctx.executeCommand(updateCommand);

            // 3. Save the updated session to file before ending
            SaveSessionCommand saveCommand =
                    ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // 4. Now end the session
            EndDayCommand endCommand = ctx.getCommandFactory().createEndDayCommand(
                    username, userId, null, endTime);
            ctx.executeCommand(endCommand);

            info(String.format("Successfully ended scheduled session for user %s", username));
            return true;
        }, false);
    }
}