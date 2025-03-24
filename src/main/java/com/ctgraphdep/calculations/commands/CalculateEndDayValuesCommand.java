package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.util.SessionEntityBuilder;

import java.time.LocalDateTime;

/**
 * Command to calculate end day values
 */
public class CalculateEndDayValuesCommand extends BaseCalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime endTime;
    private final Integer finalMinutes;

    public CalculateEndDayValuesCommand(WorkUsersSessionsStates session, LocalDateTime endTime, Integer finalMinutes) {
        this.session = session;
        this.endTime = endTime;
        this.finalMinutes = finalMinutes;
    }

    @Override
    public void validate() {
        validateSession(session);
        validateDateTime(endTime, "End time");
        // finalMinutes can be null, so no validation required
    }

    @Override
    protected WorkUsersSessionsStates executeCommand(CalculationContext context) {
        // Use builder to update all values
        return SessionEntityBuilder.updateSession(session, builder -> {
            builder.status(WorkCode.WORK_OFFLINE).dayEndTime(endTime).finalWorkedMinutes(finalMinutes != null ? finalMinutes : session.getFinalWorkedMinutes()).workdayCompleted(true);
        });
    }

    @Override
    protected WorkUsersSessionsStates handleError(Exception e) {
        return session;
    }
}