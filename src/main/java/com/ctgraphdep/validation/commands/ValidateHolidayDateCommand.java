package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationCommand;
import com.ctgraphdep.validation.TimeProvider;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Command to validate a holiday date
 */
public class ValidateHolidayDateCommand implements TimeValidationCommand<Void> {
    private final LocalDate date;
    private final TimeProvider timeProvider;

    /**
     * Creates a holiday date validation command
     *
     * @param date The date to validate
     * @param timeProvider Provider for obtaining current date/time
     */
    public ValidateHolidayDateCommand(LocalDate date, TimeProvider timeProvider) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (timeProvider == null) {
            throw new IllegalArgumentException("TimeProvider cannot be null");
        }

        this.date = date;
        this.timeProvider = timeProvider;
    }

    @Override
    public Void execute() {
        try {
            LocalDate currentDate = timeProvider.getCurrentDate();

            // Cannot add holidays for past months
            if (date.isBefore(currentDate.withDayOfMonth(1))) {
                throw new IllegalArgumentException("Cannot add holidays for past months");
            }

            // Cannot add holidays on weekends
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                throw new IllegalArgumentException("Cannot add holidays on weekends");
            }

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error validating holiday date: " + e.getMessage());
            throw e;
        }
    }
}