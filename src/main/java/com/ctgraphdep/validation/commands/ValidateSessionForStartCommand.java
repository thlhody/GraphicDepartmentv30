package com.ctgraphdep.validation.commands;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.validation.TimeProvider;

import java.time.LocalDate;

/**
 * Command to validate if a session needs to be reset before starting a new one.
 * This adds an extra fail-safe to ensure sessions never span multiple days and
 * incomplete sessions from the current day are properly handled.
 */
public class ValidateSessionForStartCommand extends BaseTimeValidationCommand<Boolean> {
    private final WorkUsersSessionsStates session;

    /**
     * Creates a command to validate session before starting a new one
     *
     * @param session The session to validate
     * @param timeProvider Provider for obtaining current date/time
     */
    public ValidateSessionForStartCommand(WorkUsersSessionsStates session, TimeProvider timeProvider) {
        super(timeProvider);
        this.session = session;
    }

    @Override
    public Boolean execute() {
        return executeValidationWithDefault(() -> {
            // If no session exists, no reset needed
            if (session == null || session.getDayStartTime() == null) {
                debug("No existing session or start time found, no reset needed");
                return false;
            }

            LocalDate currentDate = timeProvider.getCurrentDate();
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();

            // Case 1: Session from previous day - needs reset regardless of state
            if (sessionDate.isBefore(currentDate)) {
                info(String.format("Found session from previous day (%s), needs reset", sessionDate));
                return true;
            }

            // Session is valid, no reset needed
            debug("Session validation passed, no reset needed");
            return false;
        }, false); // Default to false (no reset) on error for safety
    }
}
