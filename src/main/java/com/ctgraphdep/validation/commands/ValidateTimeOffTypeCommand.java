package com.ctgraphdep.validation.commands;

import com.ctgraphdep.validation.TimeProvider;

/**
 * Validates time off type (CO/CM/SN) format and values
 * Centralizes time off type validation logic
 */
public class ValidateTimeOffTypeCommand extends BaseTimeValidationCommand<Void> {
    private final String timeOffType;

    public ValidateTimeOffTypeCommand(String timeOffType, TimeProvider timeProvider) {
        super(timeProvider);
        this.timeOffType = timeOffType;
    }

    @Override
    public Void execute() {
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            throw new IllegalArgumentException("Time off type cannot be null or empty");
        }

        String type = timeOffType.trim().toUpperCase();
        if (!type.matches("^(CO|CM|SN|CR|CN|ZS|D|CE)$")) {
            throw new IllegalArgumentException("Invalid time off type: " + timeOffType +
                    ". Valid types: CO (vacation), CM (medical), SN (national holiday), " +
                    "CR (recovery leave), CN (unpaid leave), ZS (short day), D(delegation), CE(event holiday)");
        }

        debug("Validated time off type: " + type);
        return null;
    }
}