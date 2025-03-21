package com.ctgraphdep.calculations.queries;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationQuery;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Query to calculate raw work minutes for a work time entry
 */
public class CalculateRawWorkMinutesForEntryQuery implements CalculationQuery<Integer> {
    private final WorkTimeTable entry;
    private final LocalDateTime endTime;

    public CalculateRawWorkMinutesForEntryQuery(WorkTimeTable entry, LocalDateTime endTime) {
        this.entry = entry;
        this.endTime = endTime;
    }

    @Override
    public Integer execute(CalculationContext context) {
        try {
            return CalculateWorkHoursUtil.calculateRawWorkMinutes(entry, endTime);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating raw work minutes for entry: " + e.getMessage(), e);
            return 0;
        }
    }
}
