package com.ctgraphdep.validation.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.validation.TimeProvider;
import com.ctgraphdep.config.ValidationConfig;

import java.time.LocalDateTime;

/**
 * Command to check if the current time is within working hours
 */
public class IsWorkingHoursCommand extends BaseTimeValidationCommand<Boolean> {
    private final boolean defaultOnError;

    /**
     * Creates a command to check if the current time is within working hours
     *
     * @param timeProvider Provider for obtaining current date/time
     * @param defaultOnError Value to return if an error occurs (default depends on security policy)
     */
    public IsWorkingHoursCommand(TimeProvider timeProvider, boolean defaultOnError) {
        super(timeProvider);
        this.defaultOnError = defaultOnError;
    }

    /**
     * Creates a command to check if the current time is within working hours
     * Uses the default error policy value
     *
     * @param timeProvider Provider for obtaining current date/time
     */
    public IsWorkingHoursCommand(TimeProvider timeProvider) {
        this(timeProvider, ValidationConfig.DEFAULT_WORKING_HOURS_ON_ERROR);
    }

    @Override
    public Boolean execute() {
        return executeValidationWithDefault(
                this.getClass(),
                "IsWorkingHoursCommand",
                () -> {
                    LocalDateTime currentTime = timeProvider.getCurrentDateTime();
                    int hour = currentTime.getHour();
                    return hour >= WorkCode.WORK_START_HOUR && hour < WorkCode.WORK_END_HOUR;
                },
                defaultOnError
        );
    }
}