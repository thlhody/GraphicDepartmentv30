package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationCommand;
import com.ctgraphdep.validation.TimeValidationService;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Command to check if the current day is a weekday
 */
public class IsWeekdayCommand implements TimeValidationCommand<Boolean> {
    private final TimeValidationService validationService;
    private final GetStandardTimeValuesCommand timeCommand;

    public IsWeekdayCommand(TimeValidationService validationService, GetStandardTimeValuesCommand timeCommand) {
        this.validationService = validationService;
        this.timeCommand = timeCommand;
    }

    public IsWeekdayCommand() {
        this.validationService = null;
        this.timeCommand = new GetStandardTimeValuesCommand();
    }

    @Override
    public Boolean execute() {
        try {
            // Get current date
            LocalDate currentDate;
            if (validationService != null) {
                GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);
                currentDate = timeValues.getCurrentDate();
            } else {
                currentDate = timeCommand.execute().getCurrentDate();
            }

            // Check if it's a weekend
            DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
            return !(dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking if today is a weekday: " + e.getMessage());
            return true; // Default to true in case of error
        }
    }
}

