package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationCommand;

import java.time.LocalDate;

/**
 * Command to validate a time off request
 */
public class ValidateTimeOffRequestCommand implements TimeValidationCommand<Void> {
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int maxMonthsAhead;
    private final int maxMonthsBehind;
    private final LocalDate currentDate;

    public ValidateTimeOffRequestCommand(
            LocalDate startDate,
            LocalDate endDate,
            int maxMonthsAhead,
            int maxMonthsBehind,
            LocalDate currentDate) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.maxMonthsAhead = maxMonthsAhead;
        this.maxMonthsBehind = maxMonthsBehind;
        this.currentDate = currentDate;
    }

    @Override
    public Void execute() {
        try {
            if (startDate == null) {
                throw new IllegalArgumentException("Start date is required");
            }

            if (endDate == null) {
                throw new IllegalArgumentException("End date is required");
            }

            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("End date must be after start date");
            }

            LocalDate maxAllowedDate = currentDate.plusMonths(maxMonthsAhead);
            LocalDate minAllowedDate = currentDate.minusMonths(maxMonthsBehind);

            if (startDate.isBefore(minAllowedDate)) {
                throw new IllegalArgumentException(String.format("Cannot request time off more than %d month(s) in the past", maxMonthsBehind));
            }

            if (startDate.isAfter(maxAllowedDate)) {
                throw new IllegalArgumentException(String.format("Cannot request time off more than %d month(s) in advance", maxMonthsAhead));
            }

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error validating time off request: " + e.getMessage());
            throw e;
        }
    }
}
