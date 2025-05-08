package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.queries.CalculateTotalTempStopMinutesQuery;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Command to add a break as temporary stop
 */
public class AddBreakAsTempStopCommand extends BaseCalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public AddBreakAsTempStopCommand(WorkUsersSessionsStates session, LocalDateTime startTime, LocalDateTime endTime) {
        this.session = session;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public void validate() {
        validateSession(session);
        validateDateTime(startTime, "Start time");
        validateDateTime(endTime, "End time");

        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("End time cannot be before start time");
        }
    }

    @Override
    protected WorkUsersSessionsStates executeCommand(CalculationContext context) {
        // Create the temporary stop
        TemporaryStop breakStop = new TemporaryStop();
        breakStop.setStartTime(startTime);
        breakStop.setEndTime(endTime);
        breakStop.setDuration(CalculateWorkHoursUtil.calculateMinutesBetween(startTime, endTime));

        // Calculate new stop count
        int newStopCount = session.getTemporaryStopCount() != null ? session.getTemporaryStopCount() + 1 : 1;

        // Update session using builder
        SessionEntityBuilder.updateSession(session, builder -> builder
                .addTemporaryStop(breakStop)
                .temporaryStopCount(newStopCount));

        // Calculate new total temporary stop minutes
        CalculateTotalTempStopMinutesQuery query = context.getCommandFactory().createCalculateTotalTempStopMinutesQuery(session, endTime);
        int totalStopMinutes = context.executeQuery(query);
        session.setTotalTemporaryStopMinutes(totalStopMinutes);

        return session;
    }

    @Override
    protected WorkUsersSessionsStates handleError(Exception e) {
        return session;
    }
}