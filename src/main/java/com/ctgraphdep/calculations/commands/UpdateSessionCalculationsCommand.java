package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CommandExecutorUtil;

import java.time.LocalDateTime;

/**
 * Command to update session calculations
 */
public class UpdateSessionCalculationsCommand extends BaseSessionCalculationsCommand<WorkUsersSessionsStates> {

    /**
     * Creates a command to update session calculations
     *
     * @param session The session to update
     * @param currentTime The current time
     * @param userSchedule The user's scheduled working hours
     */
    public UpdateSessionCalculationsCommand(
            WorkUsersSessionsStates session,
            LocalDateTime currentTime,
            int userSchedule) {
        super(session, currentTime, userSchedule);
    }

    @Override
    protected WorkUsersSessionsStates executeCommand(CalculationContext context) {
        // Delegate to appropriate specialized command based on session status
        if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
            // Use factory instead of direct instantiation
            context.executeCommand(
                    context.getCommandFactory().createUpdateOnlineSessionCalculationsCommand(
                            session, currentTime, userSchedule));
        } else if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
            context.executeCommand(
                    context.getCommandFactory().createUpdateTempStopCalculationsCommand(
                            session, currentTime));
        }

        // Always update last activity
        session.setLastActivity(currentTime);

        return session;
    }
    @Override
    protected WorkUsersSessionsStates handleError(Exception e) {
        return session;
    }
}