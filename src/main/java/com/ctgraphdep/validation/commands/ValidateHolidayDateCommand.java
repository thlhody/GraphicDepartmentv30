package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeProvider;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Command to validate a holiday date
 * Enhanced to include additional validation rules from WorktimeManagementService
 */
public class ValidateHolidayDateCommand extends BaseTimeValidationCommand<Void> {
    private final LocalDate date;

    /**
     * Creates a holiday date validation command
     *
     * @param date The date to validate
     * @param timeProvider Provider for obtaining current date/time
     */
    public ValidateHolidayDateCommand(LocalDate date, TimeProvider timeProvider) {
        super(timeProvider);
        if (date == null) {
            warn("Date cannot be null");
        }
        this.date = date;
    }

    // In ValidateHolidayDateCommand.java
    @Override
    public Void execute() {
        LocalDate currentDate = timeProvider.getCurrentDate();
        YearMonth requested = YearMonth.from(date);
        YearMonth current = YearMonth.from(currentDate);

        if (requested.isBefore(current)) {
            // Just log a warning without a stacktrace
            throw new IllegalArgumentException("Cannot add holidays for past months: " + requested);
        }

        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            // Just log a warning without a stacktrace
            throw new IllegalArgumentException("Cannot add holidays on weekends: " + date.getDayOfWeek());
        }

        debug("Validated holiday date: " + date);
        return null;
    }
}
