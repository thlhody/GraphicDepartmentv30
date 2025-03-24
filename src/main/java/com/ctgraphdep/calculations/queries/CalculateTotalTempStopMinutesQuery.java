package com.ctgraphdep.calculations.queries;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationQuery;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Query to calculate total temporary stop minutes
 */
public class CalculateTotalTempStopMinutesQuery implements CalculationQuery<Integer> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime currentTime;

    public CalculateTotalTempStopMinutesQuery(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        this.session = session;
        this.currentTime = currentTime;
    }

    @Override
    public Integer execute(CalculationContext context) {
        try {
            if (session == null || session.getTemporaryStops() == null || session.getTemporaryStops().isEmpty()) {
                return 0;
            }

            int totalStopMinutes = 0;

            for (TemporaryStop stop : session.getTemporaryStops()) {
                if (stop.getEndTime() != null) {
                    // Add duration of completed stops - using CalculateWorkHoursUtil
                    totalStopMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(stop.getStartTime(), stop.getEndTime());
                } else {
                    // For current ongoing stop, calculate time up to now - using CalculateWorkHoursUtil
                    totalStopMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(stop.getStartTime(), currentTime);
                }
            }

            return totalStopMinutes;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating total temporary stop minutes: " + e.getMessage(), e);
            return 0;
        }
    }
}
