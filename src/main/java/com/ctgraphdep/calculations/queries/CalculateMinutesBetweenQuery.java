package com.ctgraphdep.calculations.queries;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationQuery;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Query to calculate minutes between two time points
 */
public class CalculateMinutesBetweenQuery implements CalculationQuery<Integer> {
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public CalculateMinutesBetweenQuery(LocalDateTime startTime, LocalDateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public Integer execute(CalculationContext context) {
        try {
            return CalculateWorkHoursUtil.calculateMinutesBetween(startTime, endTime);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating minutes between times: " + e.getMessage(), e);
            return 0;
        }
    }
}

