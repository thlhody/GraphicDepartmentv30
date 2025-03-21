package com.ctgraphdep.calculations.queries;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationQuery;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Query to calculate raw work minutes between two time points
 */
public class CalculateRawWorkMinutesBetweenQuery implements CalculationQuery<Integer> {
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final List<TemporaryStop> stops;

    public CalculateRawWorkMinutesBetweenQuery(
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<TemporaryStop> stops) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.stops = stops;
    }

    @Override
    public Integer execute(CalculationContext context) {
        try {
            return CalculateWorkHoursUtil.calculateRawWorkMinutes(startTime, endTime, stops);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating raw work minutes between times: " + e.getMessage(), e);
            return 0;
        }
    }
}
