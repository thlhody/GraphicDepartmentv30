package com.ctgraphdep.calculations.queries;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationQuery;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Query to calculate raw work minutes for a session
 */
public class CalculateRawWorkMinutesQuery implements CalculationQuery<Integer> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime endTime;

    public CalculateRawWorkMinutesQuery(WorkUsersSessionsStates session, LocalDateTime endTime) {
        this.session = session;
        this.endTime = endTime;
    }

    @Override
    public Integer execute(CalculationContext context) {
        try {
            return CalculateWorkHoursUtil.calculateRawWorkMinutes(session, endTime);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error calculating raw work minutes for user %s: %s",
                            session.getUsername(), e.getMessage()), e);
            return 0;
        }
    }
}
