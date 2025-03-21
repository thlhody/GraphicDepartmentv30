package com.ctgraphdep.validation.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationCommand;
import com.ctgraphdep.validation.TimeValidationService;

import java.time.LocalDateTime;

/**
 * Command to check if the current time is within working hours
 */
public class IsWorkingHoursCommand implements TimeValidationCommand<Boolean> {
    @Override
    public Boolean execute() {
        try {
            // Get time values directly from the command
            GetStandardTimeValuesCommand timeCommand = new GetStandardTimeValuesCommand();
            LocalDateTime currentTime = timeCommand.execute().getCurrentTime();

            // Check if it's within working hours
            int hour = currentTime.getHour();
            return hour >= WorkCode.WORK_START_HOUR && hour < WorkCode.WORK_END_HOUR;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking working hours: " + e.getMessage());
            return true; // Default to true in case of error
        }
    }
}

