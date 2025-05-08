package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.util.SessionEntityBuilder;

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
        // Calculate raw work minutes up to this point
        int rawWorkMinutes = context.executeQuery(context.getCommandFactory().createCalculateRawWorkMinutesQuery(session, stopTime));

        // Create a new temporary stop
        TemporaryStop tempStop = new TemporaryStop();
        tempStop.setStartTime(stopTime);

        // Calculate new stop count
        int stopCount = (session.getTemporaryStopCount() != null) ? session.getTemporaryStopCount() + 1 : 1;

        // Update session using builder
        SessionEntityBuilder.updateSession(session, builder -> builder
                .totalWorkedMinutes(rawWorkMinutes)
                .addTemporaryStop(tempStop)
                .temporaryStopCount(stopCount)
                .lastTemporaryStopTime(stopTime)
                .status(WorkCode.WORK_TEMPORARY_STOP));
        return session;
    }

    @Override
    protected WorkUsersSessionsStates handleError(Exception e) {
        return session;
    }
}