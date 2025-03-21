package com.ctgraphdep.calculations.queries;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationQuery;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Query to calculate work time based on minutes and schedule
 */
public class CalculateWorkTimeQuery implements CalculationQuery<WorkTimeCalculationResult> {
    private final int minutes;
    private final int schedule;

    public CalculateWorkTimeQuery(int minutes, int schedule) {
        this.minutes = minutes;
        this.schedule = schedule;
    }

    @Override
    public WorkTimeCalculationResult execute(CalculationContext context) {
        try {
            return CalculateWorkHoursUtil.calculateWorkTime(minutes, schedule);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating work time: " + e.getMessage(), e);
            // Return a reasonable default in case of error
            return new WorkTimeCalculationResult(
                    minutes, // raw minutes
                    0,       // processed minutes
                    0,       // overtime minutes
                    false,   // lunch deducted
                    0        // final total minutes
            );
        }
    }
}
