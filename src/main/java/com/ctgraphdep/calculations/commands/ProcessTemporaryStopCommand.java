package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Command to process starting a temporary stop
 */
public class ProcessTemporaryStopCommand extends BaseCalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime stopTime;

    /**
     * Creates a new command to process temporary stop
     *
     * @param session The session to update
     * @param stopTime The time when temporary stop started
     */
    public ProcessTemporaryStopCommand(WorkUsersSessionsStates session, LocalDateTime stopTime) {
        this.session = session;
        this.stopTime = stopTime;
    }

    @Override
    public void validate() {
        validateSession(session);
        validateDateTime(stopTime, "Stop time");
    }

    @Override
    protected WorkUsersSessionsStates executeCommand(CalculationContext context) {
        // First, calculate raw work minutes up to this point
        int rawWorkMinutes = context.executeQuery(context.getCommandFactory().createCalculateRawWorkMinutesQuery(session, stopTime));
        session.setTotalWorkedMinutes(rawWorkMinutes);

        // Initialize temporary stop list if needed
        if (session.getTemporaryStops() == null) {
            session.setTemporaryStops(new ArrayList<>());
        }

        // Create a new temporary stop
        TemporaryStop tempStop = new TemporaryStop();
        tempStop.setStartTime(stopTime);
        session.getTemporaryStops().add(tempStop);

        // Update temporary stop count
        int stopCount = (session.getTemporaryStopCount() != null) ? session.getTemporaryStopCount() + 1 : 1;
        session.setTemporaryStopCount(stopCount);

        // Update last temporary stop time
        session.setLastTemporaryStopTime(stopTime);

        // Update session status
        session.setSessionStatus(WorkCode.WORK_TEMPORARY_STOP);

        return session;
    }

    @Override
    protected WorkUsersSessionsStates handleError(Exception e) {
        return session;
    }
}