package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class CalculateSessionService {

    public CalculateSessionService() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    public boolean isValidSession(WorkUsersSessionsStates session) {
        return session != null &&
                session.getUsername() != null &&
                session.getUserId() != null &&
                session.getDayStartTime() != null &&
                (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
    }

    public boolean shouldEndSession(User user, WorkUsersSessionsStates session, LocalDateTime currentTime) {
        if (!isValidSession(session)) return false;

        Integer maxMinutes = calculateMaxAllowedMinutes(user.getSchedule());
        Integer effectiveMinutes = calculateEffectiveMinutes(session, currentTime);

        return effectiveMinutes >= maxMinutes;
    }

    private Integer calculateMaxAllowedMinutes(int schedule) {
        return Objects.equals(schedule, WorkCode.INTERVAL_HOURS_C) ?
                WorkCode.FULL_DAY_DURATION :
                schedule * WorkCode.HOUR_DURATION;
    }

    private Integer calculateEffectiveMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        return CalculateWorkHoursUtil.calculateMinutesBetween(session.getDayStartTime(), currentTime) -
                (session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0);
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

        return tempStopMinutes >= WorkCode.HOUR_DURATION && // 1 hour threshold
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

    public Integer calculateFinalMinutes(User user, WorkUsersSessionsStates session) {
        if (!isValidSession(session)) return 0;

        if (isSessionFromPreviousDay(session)) {
            return calculateMaxAllowedMinutes(user.getSchedule());
        }

        return session.getTotalWorkedMinutes() != null ?
                session.getTotalWorkedMinutes() -
                        (session.getTotalTemporaryStopMinutes() != null ?
                                session.getTotalTemporaryStopMinutes() : 0) :
                0;
    }

    public void calculateCurrentWork(WorkUsersSessionsStates session, Integer userSchedule) {
        try {
            if (!isValidSession(session)) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            // Calculate raw minutes (total time from start)
            int rawMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                    session.getDayStartTime(),
                    now
            );

            // Calculate work time result using the utility
            WorkTimeCalculationResult result = CalculateWorkHoursUtil.calculateWorkTime(
                    rawMinutes - (session.getTotalTemporaryStopMinutes() != null ?
                            session.getTotalTemporaryStopMinutes() : 0),
                    userSchedule
            );

            // Update session with calculations
            updateSessionWithCalculations(session, rawMinutes, result);

            LoggerUtil.info(this.getClass(),
                    String.format("Calculated work time for user %s - Raw: %d, Breaks: %d (%d stops)",
                            session.getUsername(),
                            rawMinutes,
                            session.getTotalTemporaryStopMinutes(),
                            session.getTemporaryStopCount()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating work time: " + e.getMessage());
        }
    }

    private void updateSessionWithCalculations(
            WorkUsersSessionsStates session,
            int rawMinutes,
            WorkTimeCalculationResult result) {

        session.setTotalWorkedMinutes(rawMinutes);
        session.setFinalWorkedMinutes(result.getFinalTotalMinutes());
        session.setTotalOvertimeMinutes(result.getOvertimeMinutes());
        session.setLunchBreakDeducted(result.isLunchDeducted());
        session.setLastActivity(LocalDateTime.now());
    }

    public Integer calculateTempStopDuration(WorkUsersSessionsStates session) {
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