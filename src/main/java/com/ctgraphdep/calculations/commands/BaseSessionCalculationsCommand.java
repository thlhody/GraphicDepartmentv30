package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.ValidationUtil;

import java.time.LocalDateTime;

/**
 * Base class for session calculation commands that share common validation logic.
 *
 * @param <T> Return type of the command
 */
public abstract class BaseSessionCalculationsCommand<T> extends BaseCalculationCommand<T> {

    protected final WorkUsersSessionsStates session;
    protected final LocalDateTime currentTime;
    protected final int userSchedule;

    /**
     * Creates a base session calculation command with common validations
     *
     * @param session The session to update
     * @param currentTime The current time
     * @param userSchedule The user's scheduled working hours
     */
    protected BaseSessionCalculationsCommand(WorkUsersSessionsStates session, LocalDateTime currentTime, int userSchedule) {
        this.session = session;
        this.currentTime = currentTime;
        this.userSchedule = userSchedule;
    }

    @Override
    public void validate() {
        validateSession(session);
        validateDateTime(currentTime, "Current time");
        ValidationUtil.validatePositive(userSchedule, "User schedule");
    }
}