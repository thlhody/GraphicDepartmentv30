package com.ctgraphdep.calculations.commands;


import com.ctgraphdep.calculations.CalculationCommand;
import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.queries.CalculateTotalTempStopMinutesQuery;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Command to add a break as temporary stop
 */
public class AddBreakAsTempStopCommand implements CalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public AddBreakAsTempStopCommand(
            WorkUsersSessionsStates session,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        this.session = session;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public WorkUsersSessionsStates execute(CalculationContext context) {
        if (session == null) {
            return null;
        }

        try {
            // Create the temporary stop
            TemporaryStop breakStop = new TemporaryStop();
            breakStop.setStartTime(startTime);
            breakStop.setEndTime(endTime);
            breakStop.setDuration(CalculateWorkHoursUtil.calculateMinutesBetween(startTime, endTime));

            // Calculate new stop count
            int newStopCount = session.getTemporaryStopCount() != null ? session.getTemporaryStopCount() + 1 : 1;

            // Ensure temp stops list is initialized
            if (session.getTemporaryStops() == null) {
                session.setTemporaryStops(new ArrayList<>());
            }

            // Update session
            session.getTemporaryStops().add(breakStop);
            session.setTemporaryStopCount(newStopCount);

            // Calculate new total temporary stop minutes
            CalculateTotalTempStopMinutesQuery query =
                    context.getCommandFactory().createCalculateTotalTempStopMinutesQuery(session, endTime);
            int totalStopMinutes = context.executeQuery(query);
            session.setTotalTemporaryStopMinutes(totalStopMinutes);

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error adding break as temporary stop: " + e.getMessage(), e);
            return session;
        }
    }
}

