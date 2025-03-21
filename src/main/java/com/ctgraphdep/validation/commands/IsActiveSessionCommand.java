package com.ctgraphdep.validation.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationCommand;

/**
 * Command to check if a session is active
 */
public class IsActiveSessionCommand implements TimeValidationCommand<Boolean> {
    private final WorkUsersSessionsStates session;

    public IsActiveSessionCommand(WorkUsersSessionsStates session) {
        this.session = session;
    }

    @Override
    public Boolean execute() {
        try {
            return session != null && (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) || WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking if session is active: " + e.getMessage());
            return false;
        }
    }
}