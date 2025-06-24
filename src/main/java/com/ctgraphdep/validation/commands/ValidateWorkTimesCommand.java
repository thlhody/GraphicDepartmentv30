package com.ctgraphdep.validation.commands;

import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeProvider;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Validates work start and end times
 * Uses GetStandardTimeValuesCommand for consistent time logic
 */
public class ValidateWorkTimesCommand extends BaseTimeValidationCommand<Void> {
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    public ValidateWorkTimesCommand(LocalDateTime startTime, LocalDateTime endTime, TimeProvider timeProvider) {
        super(timeProvider);
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public Void execute() {
        if (startTime != null && endTime != null) {
            if (!endTime.isAfter(startTime)) {
                throw new IllegalArgumentException("End time must be after start time");
            }

            // Check for reasonable work duration (max 24 hours)
            Duration duration = Duration.between(startTime, endTime);
            if (duration.toHours() > 24) {
                throw new IllegalArgumentException("Work duration cannot exceed 24 hours");
            }

            // Additional validation using standard time values if needed
            GetStandardTimeValuesCommand timeCommand = new GetStandardTimeValuesCommand();
            var timeValues = timeCommand.execute();

            // Example: Validate against current day
            if (startTime.toLocalDate().isAfter(timeValues.getCurrentDate())) {
                warn("Work times are for future date: " + startTime.toLocalDate());
            }
        }

        debug("Validated work times: " + startTime + " to " + endTime);
        return null;
    }
}