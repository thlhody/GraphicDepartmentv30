package com.ctgraphdep.validation.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.validation.TimeProvider;

/**
 * Validates that entry doesn't have time off conflict
 * Used when updating work times on entries that shouldn't have time off
 */
public class ValidateTimeOffConflictCommand extends BaseTimeValidationCommand<Void> {
    private final WorkTimeTable entry;
    private final String message;

    public ValidateTimeOffConflictCommand(WorkTimeTable entry, String message, TimeProvider timeProvider) {
        super(timeProvider);
        this.entry = entry;
        this.message = message;
    }

    @Override
    public Void execute() {
        if (entry.getTimeOffType() != null && !entry.getTimeOffType().trim().isEmpty()) {
            throw new IllegalArgumentException(message + ". Remove time off first.");
        }

        debug("No time off conflict for entry on " + entry.getWorkDate());
        return null;
    }
}