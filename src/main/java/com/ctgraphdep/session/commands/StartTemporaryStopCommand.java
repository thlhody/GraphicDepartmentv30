package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

// Command to start a temporary stop (break) during a work session
public class StartTemporaryStopCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    public StartTemporaryStopCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        LoggerUtil.info(this.getClass(), String.format("Starting temporary stop for user: %s", username));

        // Get the current session
        WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

        // Validate session is in the correct state
        if (!SessionValidator.isInOnlineState(session, this.getClass())) {
            return session; // Return early if validation fails
        }

        // Get standardized time values
        GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

        // Process temporary stop
        processTemporaryStop(session, timeValues.getCurrentTime(), context);

        // Save the updated session
        SaveSessionCommand saveCommand = new SaveSessionCommand(session);
        context.executeCommand(saveCommand);

        LoggerUtil.info(this.getClass(), String.format("Temporary stop started for user %s", username));

        return session;
    }

    // Processes the temporary stop operation on a session
    private void processTemporaryStop(WorkUsersSessionsStates session, LocalDateTime now, SessionContext context) {
        // Use calculation service to process the temporary stop
        context.getCalculationService().processTemporaryStop(session, now);
    }

}