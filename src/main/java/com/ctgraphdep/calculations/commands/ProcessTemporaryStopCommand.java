package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationCommand;
import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Command to process starting a temporary stop
 */
public class ProcessTemporaryStopCommand implements CalculationCommand<WorkUsersSessionsStates> {
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
    public WorkUsersSessionsStates execute(CalculationContext context) {
        try {
            if (session == null) {
                LoggerUtil.warn(this.getClass(), "Cannot process temporary stop: session is null");
                return null;
            }

            // First, calculate raw work minutes up to this point
            int rawWorkMinutes = context.executeQuery(context.getCommandFactory().createCalculateRawWorkMinutesQuery(session, stopTime)
            );
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
            int stopCount = (session.getTemporaryStopCount() != null) ?
                    session.getTemporaryStopCount() + 1 : 1;
            session.setTemporaryStopCount(stopCount);

            // Update last temporary stop time
            session.setLastTemporaryStopTime(stopTime);

            // Update session status
            session.setSessionStatus(WorkCode.WORK_TEMPORARY_STOP);

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error processing temporary stop: " + e.getMessage(), e);
            return session;
        }
    }
}