package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationCommand;
import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.util.SessionEntityBuilder;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Command to calculate end day values
 */
public class CalculateEndDayValuesCommand implements CalculationCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime endTime;
    private final Integer finalMinutes;

    public CalculateEndDayValuesCommand(
            WorkUsersSessionsStates session,
            LocalDateTime endTime,
            Integer finalMinutes) {
        this.session = session;
        this.endTime = endTime;
        this.finalMinutes = finalMinutes;
    }

    @Override
    public WorkUsersSessionsStates execute(CalculationContext context) {
        if (session == null) {
            return null;
        }

        try {
            // Use builder to update all values
            return SessionEntityBuilder.updateSession(session, builder -> {
                builder.status(WorkCode.WORK_OFFLINE)
                        .dayEndTime(endTime)
                        .finalWorkedMinutes(finalMinutes != null ? finalMinutes : session.getFinalWorkedMinutes())
                        .workdayCompleted(true);
            });
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating end day values: " + e.getMessage(), e);
            return session;
        }
    }
}