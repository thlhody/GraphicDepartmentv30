package com.ctgraphdep.validation.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.validation.TimeProvider;
import com.ctgraphdep.validation.TimeValidationCommand;

/**
 * Validates that a WorkTimeTable entry is valid for update operations
 * Ensures entry has required fields for modification
 */
public class ValidateEntryForUpdateCommand extends BaseTimeValidationCommand<Void> {
    private final WorkTimeTable entry;

    public ValidateEntryForUpdateCommand(WorkTimeTable entry, TimeProvider timeProvider) {
        super(timeProvider);
        this.entry = entry;
    }

    @Override
    public Void execute() {
        if (entry == null) {
            throw new IllegalArgumentException("Entry cannot be null");
        }
        if (entry.getUserId() == null) {
            throw new IllegalArgumentException("Entry must have user ID");
        }
        if (entry.getWorkDate() == null) {
            throw new IllegalArgumentException("Entry must have work date");
        }

        debug("Validated entry for update: userId=" + entry.getUserId() + ", date=" + entry.getWorkDate());
        return null;
    }
}
