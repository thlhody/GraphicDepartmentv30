package com.ctgraphdep.validation.commands;

import com.ctgraphdep.validation.TimeProvider;

import java.time.LocalDate;

/**
 * Validates basic parameters for worktime entry creation
 * Centralizes null checking for userId and date
 */
public class ValidateBasicParametersCommand extends BaseTimeValidationCommand<Void> {
    private final Integer userId;
    private final LocalDate date;

    public ValidateBasicParametersCommand(Integer userId, LocalDate date, TimeProvider timeProvider) {
        super(timeProvider);
        this.userId = userId;
        this.date = date;
    }

    @Override
    public Void execute() {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        // Additional validation can be added here
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }

        debug("Validated basic parameters: userId=" + userId + ", date=" + date);
        return null;
    }
}