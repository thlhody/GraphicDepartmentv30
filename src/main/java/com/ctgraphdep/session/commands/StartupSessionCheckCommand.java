package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.service.SessionMidnightHandler;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetLocalUserQuery;

import java.time.LocalDate;

//Command that runs at application startup to check for and reset active sessions from previous days that weren't properly closed.
public class StartupSessionCheckCommand extends BaseSessionCommand<Void> {

    private final SessionMidnightHandler sessionMidnightHandler;

    //Creates a command to check for stale sessions at startup
    public StartupSessionCheckCommand(SessionMidnightHandler sessionMidnightHandler) {
        validateCondition(sessionMidnightHandler != null, "Session midnight handler cannot be null");
        this.sessionMidnightHandler = sessionMidnightHandler;
    }

    @Override
    public Void execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info("Performing startup session check");

            // Get local user
            GetLocalUserQuery userQuery = ctx.getCommandFactory().createGetLocalUserQuery();
            User localUser = ctx.executeQuery(userQuery);

            if (localUser == null) {
                warn("No local user found, skipping startup session check");
                return null;
            }

            String username = localUser.getUsername();
            Integer userId = localUser.getUserId();

            // Force refresh from file to ensure we have the latest data
            boolean refreshSuccess = ctx.getSessionCacheService().forceRefreshFromFile(username, userId);

            if (refreshSuccess) {
                info("Successfully refreshed session from file during startup");
            } else {
                warn("Could not refresh session from file - may be first run or file missing");
            }

            // Get current session for the user
            WorkUsersSessionsStates session = ctx.getCurrentSession(localUser.getUsername(), localUser.getUserId());

            if (session == null) {
                info("No active session found during startup check");
                return null;
            }

            LocalDate today = getStandardCurrentDate(context);
            debug("Current date: " + today);

            // Check if session is active (online or temporary stop)
            boolean isActive = WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) || WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());

            // Check if session is from a previous day
            boolean isFromPreviousDay = session.getDayStartTime() != null && session.getDayStartTime().toLocalDate().isBefore(today);

            // Log current session state
            info(String.format("Session state: active=%b, fromPreviousDay=%b, status=%s, startDate=%s", isActive, isFromPreviousDay, session.getSessionStatus(),
                    session.getDayStartTime() != null ? session.getDayStartTime().toLocalDate() : "null"));

            // Reset the session if it's active and from a previous day
            if (isActive && isFromPreviousDay) {
                warn(String.format("Found active %s session from previous day (%s), resetting...", session.getSessionStatus(), session.getDayStartTime().toLocalDate()));

                // Use the midnight handler to reset the session
                sessionMidnightHandler.resetUserSession(localUser);

                info("Successfully reset stale session from previous day");
            }

            return null;
        });
    }
}