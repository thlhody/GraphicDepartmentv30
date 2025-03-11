package com.ctgraphdep.session.util;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Utility class for validating session states
 */
public class SessionValidator {

    /**
     * Validates that a session is in online state
     */
    public static boolean isInOnlineState(WorkUsersSessionsStates session, Class<?> loggerClass) {
        if (session == null || !WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
            LoggerUtil.warn(loggerClass, String.format("Invalid session state: %s - expecting Online", session != null ? session.getSessionStatus() : "null"));
            return false;
        }
        return true;
    }

    /**
     * Validates that a session is in temporary stop state
     */
    public static boolean isInTemporaryStopState(WorkUsersSessionsStates session, Class<?> loggerClass) {
        if (session == null || !WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
            LoggerUtil.warn(loggerClass, String.format("Invalid session state: %s - expecting Temporary Stop", session != null ? session.getSessionStatus() : "null"));
            return false;
        }
        return true;
    }

    /**
     * Validates that a session exists and is not null
     */
    public static boolean exists(WorkUsersSessionsStates session, Class<?> loggerClass) {
        if (session == null) {
            LoggerUtil.warn(loggerClass, "Session is null");
            return false;
        }
        return true;
    }

    /**
     * Validates that a session is completed and can be resumed
     */
    public static boolean isCompletedSession(WorkUsersSessionsStates session, Class<?> loggerClass) {
        if (session == null ||
                !WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()) ||
                !session.getWorkdayCompleted()) {
            LoggerUtil.warn(loggerClass, String.format("Invalid session state: %s - expecting completed offline session", session != null ? session.getSessionStatus() : "null"));
            return false;
        }
        return true;
    }

    /**
     * Validates that a session needs resolution
     * This is used specifically for the session resolution flow
     */
    public static boolean needsResolution(WorkUsersSessionsStates session, Class<?> loggerClass) {
        if (session == null) {
            LoggerUtil.warn(loggerClass, "Session is null, cannot check if needs resolution");
            return false;
        }

        // A session needs resolution if any of these conditions are true:
        // 1. dayEndTime is null (session wasn't properly ended)
        // 2. workdayCompleted is false (session was interrupted, e.g., by midnight handler)
        boolean needsResolution = session.getDayEndTime() == null || !session.getWorkdayCompleted();

        if (needsResolution) {
            LoggerUtil.info(loggerClass, String.format(
                    "Session for user %s needs resolution. Status: %s, EndTime: %s, Completed: %b",
                    session.getUsername(),
                    session.getSessionStatus(),
                    session.getDayEndTime(),
                    session.getWorkdayCompleted()));
        }

        return needsResolution;
    }

    /**
     * Check if session is valid for updating calculations
     * Used to determine if we can apply calculations to this session
     */
    public static boolean isValidForCalculations(WorkUsersSessionsStates session, Class<?> loggerClass) {
        if (session == null) {
            LoggerUtil.warn(loggerClass, "Session is null, cannot update calculations");
            return false;
        }

        // Sessions are valid for calculation if:
        // 1. They are Online
        // 2. They are in Temporary Stop
        // 3. They are Offline but not completed (need resolution)
        boolean isValid = WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) ||
                (WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()) && !session.getWorkdayCompleted());

        if (!isValid) {
            LoggerUtil.warn(loggerClass, String.format(
                    "Session in state %s is not valid for calculations",
                    session.getSessionStatus()));
        }

        return isValid;
    }
}