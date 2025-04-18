package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationCommand;
import com.ctgraphdep.validation.TimeProvider;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Command to validate a period (year/month)
 */
public class ValidatePeriodCommand implements TimeValidationCommand<Void> {
    private final int year;
    private final int month;
    private final int maxMonthsAhead;
    private final TimeProvider timeProvider;

    /**
     * Creates a period validation command
     *
     * @param year The year to validate
     * @param month The month to validate
     * @param maxMonthsAhead Maximum number of months into the future allowed
     * @param timeProvider Provider for obtaining current date/time
     */
    public ValidatePeriodCommand(int year, int month, int maxMonthsAhead, TimeProvider timeProvider) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (maxMonthsAhead < 0) {
            throw new IllegalArgumentException("Max months ahead must be non-negative");
        }
        if (timeProvider == null) {
            throw new IllegalArgumentException("TimeProvider cannot be null");
        }

        this.year = year;
        this.month = month;
        this.maxMonthsAhead = maxMonthsAhead;
        this.timeProvider = timeProvider;
    }

    @Override
    public Void execute() {
        try {
            LocalDate currentDate = timeProvider.getCurrentDate();
            YearMonth requested = YearMonth.of(year, month);
            YearMonth current = YearMonth.of(currentDate.getYear(), currentDate.getMonthValue());
            YearMonth maxAllowed = current.plusMonths(maxMonthsAhead);

            if (requested.isAfter(maxAllowed)) {
                throw new IllegalArgumentException(String.format("Cannot view future periods beyond %d months", maxMonthsAhead));
            }

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error validating period: " + e.getMessage());
            throw e;
        }
    }
}