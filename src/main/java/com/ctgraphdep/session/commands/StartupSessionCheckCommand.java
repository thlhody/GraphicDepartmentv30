package com.ctgraphdep.session.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.service.SessionMidnightHandler;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetLocalUserQuery;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;

/**
 * Command that runs at application startup to check for and reset
 * active sessions from previous days that weren't properly closed.
 */
public class StartupSessionCheckCommand implements SessionCommand<Void> {

    private final SessionMidnightHandler sessionMidnightHandler;

    public StartupSessionCheckCommand(SessionMidnightHandler sessionMidnightHandler) {
        this.sessionMidnightHandler = sessionMidnightHandler;
    }

    @Override
    public Void execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), "Performing startup session check");

            // Get local user
            GetLocalUserQuery userQuery = context.getCommandFactory().createGetLocalUserQuery();
            User localUser = context.executeQuery(userQuery);

            if (localUser == null) {
                LoggerUtil.warn(this.getClass(), "No local user found, skipping startup session check");
                return null;
            }

            // Get current session for the user
            WorkUsersSessionsStates session = context.getCurrentSession(localUser.getUsername(), localUser.getUserId());

            if (session == null) {
                LoggerUtil.info(this.getClass(), "No active session found during startup check");
                return null;
            }

            // Get current date for comparison
            // Get standardized time values using the new validation system
            GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
            LocalDate today = timeValues.getCurrentDate();

            // Check if session is active (online or temporary stop)
            boolean isActive = WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                    WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());

            // Check if session is from a previous day
            boolean isFromPreviousDay = session.getDayStartTime() != null &&
                    session.getDayStartTime().toLocalDate().isBefore(today);

            // Log current session state
            LoggerUtil.info(this.getClass(), String.format(
                    "Session state: active=%b, fromPreviousDay=%b, status=%s, startDate=%s",
                    isActive,
                    isFromPreviousDay,
                    session.getSessionStatus(),
                    session.getDayStartTime() != null ? session.getDayStartTime().toLocalDate() : "null"
            ));

            // Reset the session if it's active and from a previous day
            if (isActive && isFromPreviousDay) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Found active %s session from previous day (%s), resetting...",
                        session.getSessionStatus(),
                        session.getDayStartTime().toLocalDate()
                ));

                // Use the midnight handler to reset the session
                sessionMidnightHandler.resetUserSession(localUser);

                LoggerUtil.info(this.getClass(), "Successfully reset stale session from previous day");
            }

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during startup session check: " + e.getMessage(), e);
            return null;
        }
    }
}