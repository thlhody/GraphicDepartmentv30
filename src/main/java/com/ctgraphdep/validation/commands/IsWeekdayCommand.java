package com.ctgraphdep.validation.commands;

import com.ctgraphdep.validation.TimeProvider;
import com.ctgraphdep.config.ValidationConfig;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Command to check if the current day is a weekday
 */
public class IsWeekdayCommand extends BaseTimeValidationCommand<Boolean> {
    private final boolean defaultOnError;

    /**
     * Creates a command to check if the current day is a weekday
     *
     * @param timeProvider Provider for obtaining current date/time
     * @param defaultOnError Value to return if an error occurs (default: false as per security policy)
     */
    public IsWeekdayCommand(TimeProvider timeProvider, boolean defaultOnError) {
        super(timeProvider);
        this.defaultOnError = defaultOnError;
    }

    /**
     * Creates a command to check if the current day is a weekday
     * Uses the default error policy value
     *
     * @param timeProvider Provider for obtaining current date/time
     */
    public IsWeekdayCommand(TimeProvider timeProvider) {
        this(timeProvider, ValidationConfig.DEFAULT_WEEKDAY_ON_ERROR);
    }

    @Override
    public Boolean execute() {
        return executeValidationWithDefault(
                () -> {
                    LocalDate currentDate = timeProvider.getCurrentDate();
                    DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
                    boolean isWeekday = !(dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY);
                    debug("Current day is " + (isWeekday ? "a weekday" : "a weekend"));
                    return isWeekday;
                },
                defaultOnError
        );
    }
}