package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationCommand;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Command to validate a holiday date
 */
 public class ValidateHolidayDateCommand implements TimeValidationCommand<Void> {
    private final LocalDate date;
    private final LocalDate currentDate;

    public ValidateHolidayDateCommand(LocalDate date, LocalDate currentDate) {
        this.date = date;
        this.currentDate = currentDate;
    }

    public ValidateHolidayDateCommand(LocalDate date) {
        this(date, LocalDate.now());
    }

    @Override
    public Void execute() {
        try {
            if (date.isBefore(currentDate.withDayOfMonth(1))) {
                throw new IllegalArgumentException("Cannot add holidays for past months");
            }

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