package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.session.util.SessionEntityBuilder;

import java.time.format.DateTimeFormatter;

/**
 * Command to handle a session from a previous day
 */
public class HandlePreviousDaySessionCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;

    public HandlePreviousDaySessionCommand(WorkUsersSessionsStates session) {
        this.session = session;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Handling previous day session for user %s from %s", session.getUsername(), session.getDayStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE)));

            // Update session status to offline using the builder
            SessionEntityBuilder.updateSession(session, builder -> builder.status(WorkCode.WORK_OFFLINE));


            // Update the session file
            context.getDataAccessService().writeLocalSessionFile(session);

            LoggerUtil.info(this.getClass(), String.format("Created continuation point for previous day session for user %s", session.getUsername()));

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error handling previous day session: %s", e.getMessage()));

        }
        return null;
    }
}