package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationCommand;
import com.ctgraphdep.validation.TimeValidationService;

import java.time.LocalDate;

/**
 * Command to check if a session is active for the current day
 */
public class HasActiveSessionTodayCommand implements TimeValidationCommand<Boolean> {
    private final LocalDate sessionDate;
    private final TimeValidationService validationService;
    private final GetStandardTimeValuesCommand timeCommand;

    public HasActiveSessionTodayCommand(LocalDate sessionDate, TimeValidationService validationService, GetStandardTimeValuesCommand timeCommand) {
        this.sessionDate = sessionDate;
        this.validationService = validationService;
        this.timeCommand = timeCommand;
    }

    public HasActiveSessionTodayCommand(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
        this.validationService = null;
        this.timeCommand = new GetStandardTimeValuesCommand();
    }

    @Override
    public Boolean execute() {
        try {
            if (sessionDate == null) {
                return false;
            }

            // Get current date
            LocalDate currentDate;
            if (validationService != null) {
                GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);
                currentDate = timeValues.getCurrentDate();
            } else {
                currentDate = timeCommand.execute().getCurrentDate();
            }

            return sessionDate.equals(currentDate);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking if session is active today: " + e.getMessage());
            return false; // Default to false in case of error
        }
    }
}