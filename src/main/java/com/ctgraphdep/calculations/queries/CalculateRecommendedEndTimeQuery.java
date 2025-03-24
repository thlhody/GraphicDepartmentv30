package com.ctgraphdep.calculations.queries;

import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.calculations.CalculationQuery;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDateTime;

/**
 * Query to calculate recommended end time for a work time entry
 */
public class CalculateRecommendedEndTimeQuery implements CalculationQuery<LocalDateTime> {
    private final WorkTimeTable entry;
    private final int userSchedule;

    public CalculateRecommendedEndTimeQuery(WorkTimeTable entry, int userSchedule) {
        this.entry = entry;
        this.userSchedule = userSchedule;
    }

    @Override
    public LocalDateTime execute(CalculationContext context) {
        try {
            return CalculateWorkHoursUtil.calculateRecommendedEndTime(entry, userSchedule);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating recommended end time: " + e.getMessage(), e);

            // Get standardized time values instead of using LocalDateTime.now()
            GetStandardTimeValuesCommand timeCommand = context.getSessionContext().getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getSessionContext().getValidationService().execute(timeCommand);

            // Return standardized current time as fallback
            return timeValues.getCurrentTime();
        }
    }
}