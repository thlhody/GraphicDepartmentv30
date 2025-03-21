package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationCommand;
import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Command to update the last temporary stop with end time and duration
 */
public class UpdateLastTemporaryStopCommand implements CalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime endTime;

    public UpdateLastTemporaryStopCommand(WorkUsersSessionsStates session, LocalDateTime endTime) {
        this.session = session;
        this.endTime = endTime;
    }

    @Override
    public WorkUsersSessionsStates execute(CalculationContext context) {
        if (session == null || session.getTemporaryStops() == null || session.getTemporaryStops().isEmpty()) {
            return session;
        }

        try {
            TemporaryStop lastStop = session.getTemporaryStops().get(session.getTemporaryStops().size() - 1);
            int stopMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(lastStop.getStartTime(), endTime);
            lastStop.setEndTime(endTime);
            lastStop.setDuration(stopMinutes);

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating last temporary stop: " + e.getMessage(), e);
            return session;
        }
    }
}