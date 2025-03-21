package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationCommand;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Command to get standard date values based on the current context
 */
public class GetStandardDateValuesCommand implements TimeValidationCommand<Map<String, Object>> {
    private final GetStandardTimeValuesCommand.StandardTimeValues timeValues;

    public GetStandardDateValuesCommand(GetStandardTimeValuesCommand.StandardTimeValues timeValues) {
        this.timeValues = timeValues;
    }

    @Override
    public Map<String, Object> execute() {
        try {
            Map<String, Object> result = new HashMap<>();

            LocalDate currentDate = timeValues.getCurrentDate();
            result.put("currentDate", currentDate);
            result.put("currentYear", currentDate.getYear());
            result.put("currentMonth", currentDate.getMonthValue());
            result.put("maxFutureDate", currentDate.plusMonths(6));
            result.put("minPastDate", currentDate.minusMonths(1));
            result.put("currentYearMonth", YearMonth.of(currentDate.getYear(), currentDate.getMonthValue()));

            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting standard date values: " + e.getMessage());
            throw e;
        }
    }
}
