package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.queries.CalculateTotalTempStopMinutesQuery;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;

import java.time.LocalDateTime;

/**
 * Command to update temporary stop calculations
 */
public class UpdateTempStopCalculationsCommand extends BaseCalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime currentTime;

    public UpdateTempStopCalculationsCommand(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        this.session = session;
        this.currentTime = currentTime;
    }

    @Override
    public void validate() {
        validateSession(session);
        validateDateTime(currentTime, "Current time");
    }

    @Override
    protected WorkUsersSessionsStates executeCommand(CalculationContext context) {
        if (session.getLastTemporaryStopTime() == null) {
            return session;
        }

        // Calculate total temporary stop minutes
        CalculateTotalTempStopMinutesQuery query = context.getCommandFactory().createCalculateTotalTempStopMinutesQuery(session, currentTime);
        int totalStopMinutes = context.executeQuery(query);

        session.setTotalTemporaryStopMinutes(totalStopMinutes);

        // Update current stop duration if there are any stops
        if (session.getTemporaryStops() != null && !session.getTemporaryStops().isEmpty()) {
            TemporaryStop currentStop = session.getTemporaryStops().get(session.getTemporaryStops().size() - 1);

            int currentStopMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(currentStop.getStartTime(), currentTime
            );
            currentStop.setDuration(currentStopMinutes);
        }

        return session;
    }

    @Override
    protected WorkUsersSessionsStates handleError(Exception e) {
        return session;
    }
}