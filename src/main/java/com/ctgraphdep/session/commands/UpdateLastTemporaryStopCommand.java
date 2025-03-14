package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Command to update the last temporary stop with end time
 */
public class UpdateLastTemporaryStopCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime endTime;

    public UpdateLastTemporaryStopCommand(WorkUsersSessionsStates session, LocalDateTime endTime) {
        this.session = session;
        this.endTime = endTime;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Updating last temporary stop for user %s", session.getUsername()));

            context.updateLastTemporaryStop(session, endTime);

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating last temporary stop: %s", e.getMessage()));
            return session;
        }
    }
}