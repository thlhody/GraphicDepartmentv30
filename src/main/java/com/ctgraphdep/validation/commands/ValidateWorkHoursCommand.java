package com.ctgraphdep.validation.commands;

import com.ctgraphdep.validation.TimeProvider;

/**
 * Validates work hours for admin entries (1-24 hour range)
 */
public class ValidateWorkHoursCommand extends BaseTimeValidationCommand<Void> {
    private final int hours;

    public ValidateWorkHoursCommand(int hours, TimeProvider timeProvider) {
        super(timeProvider);
        this.hours = hours;
    }

    @Override
    public Void execute() {
        if (hours < 1 || hours > 24) {
            throw new IllegalArgumentException("Work hours must be between 1 and 24");
        }

        debug("Validated work hours: " + hours);
        return null;
    }
}

