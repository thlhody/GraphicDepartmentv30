package com.ctgraphdep.session.util;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;

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
}