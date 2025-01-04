package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class SessionCalculator {
    public boolean shouldEndSession(User user, WorkUsersSessionsStates session, LocalDateTime currentTime) {
        if (!isValidSession(session)) return false;

        int maxMinutes = calculateMaxAllowedMinutes(user);
        int effectiveMinutes = calculateEffectiveMinutes(session, currentTime);

        return effectiveMinutes >= maxMinutes;
    }

    private int calculateMaxAllowedMinutes(User user) {
        return Objects.equals(user.getSchedule(), WorkCode.INTERVAL_HOURS_C) ?
                WorkCode.FULL_DAY_DURATION :
                user.getSchedule() * WorkCode.HOUR_DURATION;
    }

    private int calculateEffectiveMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        long totalMinutes = ChronoUnit.MINUTES.between(session.getDayStartTime(), currentTime);
        return (int) totalMinutes - (session.getTotalTemporaryStopMinutes() != null ?
                session.getTotalTemporaryStopMinutes() : 0);
    }

    public boolean shouldShowTempStopWarning(WorkUsersSessionsStates session) {
        if (!isValidSession(session) ||
                !WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) ||
                session.getLastTemporaryStopTime() == null) {
            return false;
        }

        long tempStopMinutes = ChronoUnit.MINUTES.between(
                session.getLastTemporaryStopTime(),
                LocalDateTime.now()
        );

        return tempStopMinutes >= WorkCode.HOUR_DURATION && // 1 hours threshold
                tempStopMinutes < (WorkCode.MAX_TEMP_STOP_HOURS * 60) && // Less than max allowed
                tempStopMinutes % WorkCode.TEMP_STOP_WARNING_INTERVAL == 0; // Check for hourly interval
    }

    public boolean isStuckTemporaryStop(WorkUsersSessionsStates session) {
        return isValidSession(session) &&
                WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) &&
                session.getLastTemporaryStopTime() != null &&
                ChronoUnit.HOURS.between(
                        session.getLastTemporaryStopTime(),
                        LocalDateTime.now()
                ) >= WorkCode.MAX_TEMP_STOP_HOURS;
    }

    public boolean isSessionFromPreviousDay(WorkUsersSessionsStates session) {
        return isValidSession(session) &&
                !session.getDayStartTime().toLocalDate().equals(LocalDateTime.now().toLocalDate());
    }

    public int calculateFinalMinutes(User user, WorkUsersSessionsStates session) {
        if (!isValidSession(session)) return 0;

        if (isSessionFromPreviousDay(session)) {
            return calculateMaxAllowedMinutes(user);
        }

        return session.getTotalWorkedMinutes() != null ?
                session.getTotalWorkedMinutes() -
                        (session.getTotalTemporaryStopMinutes() != null ?
                                session.getTotalTemporaryStopMinutes() : 0) :
                0;
    }

    public boolean isValidSession(WorkUsersSessionsStates session) {
        return session != null &&
                session.getUsername() != null &&
                session.getUserId() != null &&
                session.getDayStartTime() != null &&
                (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
    }

    public int calculateTempStopDuration(WorkUsersSessionsStates session) {
        if (!isValidSession(session) ||
                !WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) ||
                session.getLastTemporaryStopTime() == null) {
            return 0;
        }

        return (int) ChronoUnit.MINUTES.between(
                session.getLastTemporaryStopTime(),
                LocalDateTime.now()
        );
    }
}