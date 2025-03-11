package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery.SessionTimeValues;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;


// Command to resume work after a temporary stop
public class ResumeFromTemporaryStopCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final String username;
    private final Integer userId;

    public ResumeFromTemporaryStopCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        LoggerUtil.info(this.getClass(), String.format("Resuming work for user %s after temporary stop", username));

        // Get standardized time values
        GetSessionTimeValuesQuery timeQuery = new GetSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

        // Get the current session
        WorkUsersSessionsStates session = context.getCurrentSession(username, userId);

        // Validate session is in temporary stop state
        if (!SessionValidator.isInTemporaryStopState(session, this.getClass())) {
            return session;
        }

        // Process the resume operation
        context.getCalculationService().processResumeFromTempStop(session, timeValues.getCurrentTime());

        // Save the updated session
        SaveSessionCommand saveCommand = new SaveSessionCommand(session);
        context.executeCommand(saveCommand);

        LoggerUtil.info(this.getClass(), String.format("Resumed work for user %s after temporary stop", username));

        return session;
    }
}