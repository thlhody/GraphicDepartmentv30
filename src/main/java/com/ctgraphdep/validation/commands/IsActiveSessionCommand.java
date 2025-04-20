package com.ctgraphdep.validation.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeProvider;

/**
 * Command to check if a session is active
 */
public class IsActiveSessionCommand extends BaseTimeValidationCommand<Boolean> {
    private final WorkUsersSessionsStates session;

    public IsActiveSessionCommand(WorkUsersSessionsStates session, TimeProvider timeProvider) {
        super(timeProvider);
        this.session = session;
    }

    @Override
    public Boolean execute() {
        return executeValidationWithDefault(() -> {
            boolean isActive = session != null &&
                    (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                            WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));

            debug("Checked if session is active: " + isActive);
            return isActive;
        }, false); // Default to false if there's an error, for safety
    }
}
