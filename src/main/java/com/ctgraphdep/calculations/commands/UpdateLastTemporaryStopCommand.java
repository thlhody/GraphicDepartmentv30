package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;

import java.time.LocalDateTime;

/**
 * Command to update the last temporary stop with end time and duration
 */
public class UpdateLastTemporaryStopCommand extends BaseCalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime endTime;

    public UpdateLastTemporaryStopCommand(WorkUsersSessionsStates session, LocalDateTime endTime) {
        this.session = session;
        this.endTime = endTime;
    }

    @Override
    public void validate() {
        validateSession(session);
        validateDateTime(endTime, "End time");
    }

    @Override
    protected WorkUsersSessionsStates executeCommand(CalculationContext context) {
        if (session.getTemporaryStops() == null || session.getTemporaryStops().isEmpty()) {
            return session;
        }

        TemporaryStop lastStop = session.getTemporaryStops().get(session.getTemporaryStops().size() - 1);
        int stopMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(lastStop.getStartTime(), endTime);
        lastStop.setEndTime(endTime);
        lastStop.setDuration(stopMinutes);

        return session;
    }

    @Override
    protected WorkUsersSessionsStates handleError(Exception e) {
        return session;
    }
}