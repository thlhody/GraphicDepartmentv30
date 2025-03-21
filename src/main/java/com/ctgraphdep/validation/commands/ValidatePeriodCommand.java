package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationCommand;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Command to validate a period (year/month)
 */
public class ValidatePeriodCommand implements TimeValidationCommand<Void> {
    private final int year;
    private final int month;
    private final int maxMonthsAhead;
    private final LocalDate currentDate;

    public ValidatePeriodCommand(int year, int month, int maxMonthsAhead, LocalDate currentDate) {
        this.year = year;
        this.month = month;
        this.maxMonthsAhead = maxMonthsAhead;
        this.currentDate = currentDate;
    }

    public ValidatePeriodCommand(int year, int month, int maxMonthsAhead) {
        this(year, month, maxMonthsAhead, LocalDate.now());
    }

    @Override
    public Void execute() {
        try {
            YearMonth requested = YearMonth.of(year, month);
            YearMonth current = YearMonth.of(currentDate.getYear(), currentDate.getMonthValue());
            YearMonth maxAllowed = current.plusMonths(maxMonthsAhead);

            if (requested.isAfter(maxAllowed)) {
                throw new IllegalArgumentException(
                        String.format("Cannot view future periods beyond %d months", maxMonthsAhead));
            }

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error validating period: " + e.getMessage());
            throw e;
        }
    }
}
