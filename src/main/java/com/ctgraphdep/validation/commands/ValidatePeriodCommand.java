package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeProvider;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Command to validate a period (year/month)
 * Modified to handle future period restrictions with graceful error logging
 */
public class ValidatePeriodCommand extends BaseTimeValidationCommand<Void> {
    private final int year;
    private final int month;
    private final int maxMonthsAhead;

    /**
     * Creates a period validation command
     *
     * @param year           The year to validate
     * @param month          The month to validate
     * @param maxMonthsAhead Maximum number of months into the future allowed
     * @param timeProvider   Provider for obtaining current date/time
     */
    public ValidatePeriodCommand(int year, int month, int maxMonthsAhead, TimeProvider timeProvider) {
        super(timeProvider);

        if (month < 1 || month > 12) {
            warn(String.format("Month must be between 1 and 12, value entered: %s", month));
        }
        if (year < 2000 || year > 2100) {
            warn(String.format("Invalid year: %s", year));
        }
        if (maxMonthsAhead < 0) {
            warn("Max months ahead must be non-negative");
        }

        this.year = year;
        this.month = month;
        this.maxMonthsAhead = maxMonthsAhead;
    }

    @Override
    public Void execute() {
        LocalDate currentDate = timeProvider.getCurrentDate();
        YearMonth requested = YearMonth.of(year, month);
        YearMonth current = YearMonth.from(currentDate);
        YearMonth maxAllowed = current.plusMonths(maxMonthsAhead);

        // Check if period is in the future
        if (requested.isAfter(maxAllowed)) {
            // Throw with a clean message for controllers to handle
            throw new IllegalArgumentException(String.format("Future period %s beyond allowed limit of %d months (%s)", requested, maxMonthsAhead, maxAllowed));
        }

        // Log success at debug level
        debug("Successfully validated period: " + requested);
        return null;
    }
}