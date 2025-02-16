package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserSessionCalcService {
    private final UserService userService;

    public UserSessionCalcService(UserService userService) {
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void updateSessionCalculations(WorkUsersSessionsStates session) {
        if (session == null) return;

        LocalDateTime now = LocalDateTime.now();

        if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
            updateWorkCalculations(session, now);
        } else if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
            updateTempStopCalculations(session, now);
        }
    }

    private void updateWorkCalculations(WorkUsersSessionsStates session, LocalDateTime now) {
        // 1. Calculate total raw worked minutes
        int rawWorkedMinutes = calculateRawWorkMinutes(session, now);
        session.setTotalWorkedMinutes(rawWorkedMinutes);

        // 2. Get user's schedule
        User user = userService.getUserById(session.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        int userSchedule = user.getSchedule();

        // 3. Process through CalculateWorkHoursUtil
        WorkTimeCalculationResult calculationResult =
                CalculateWorkHoursUtil.calculateWorkTime(rawWorkedMinutes, userSchedule);

        // 4. Update session with calculated values
        updateSessionWithCalculations(session, calculationResult);
    }

    private int calculateRawWorkMinutes(WorkUsersSessionsStates session, LocalDateTime now) {
        int totalMinutes = 0;
        LocalDateTime currentTime = session.getDayStartTime();

        // Handle completed temporary stops
        if (session.getTemporaryStops() != null) {
            for (TemporaryStop stop : session.getTemporaryStops()) {
                // Only count stops that have ended
                if (stop.getEndTime() != null) {
                    // Add worked minutes before each stop
                    totalMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(
                            currentTime,
                            stop.getStartTime()
                    );
                    // Move current time to after the stop
                    currentTime = stop.getEndTime();
                }
            }
        }

        // Add minutes from last stop (or start) until now if we're in ONLINE status
        if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
            totalMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(
                    currentTime,
                    now
            );
        }

        return totalMinutes;
    }

    private void updateSessionWithCalculations(WorkUsersSessionsStates session, WorkTimeCalculationResult result) {
        session.setFinalWorkedMinutes(result.getProcessedMinutes());
        session.setTotalOvertimeMinutes(result.getOvertimeMinutes());
        session.setLunchBreakDeducted(result.isLunchDeducted());

        User user = userService.getUserById(session.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        boolean isCompleted = isCompleted(result, user);
        session.setWorkdayCompleted(isCompleted);

        session.setLastActivity(LocalDateTime.now());
    }

    private void updateTempStopCalculations(WorkUsersSessionsStates session, LocalDateTime now) {
        if (session.getLastTemporaryStopTime() != null) {
            int totalTempStopMinutes = 0;

            // Calculate total from all completed stops
            if (session.getTemporaryStops() != null) {
                for (TemporaryStop stop : session.getTemporaryStops()) {
                    if (stop.getEndTime() != null) {
                        // Add duration of completed stops
                        totalTempStopMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(
                                stop.getStartTime(),
                                stop.getEndTime()
                        );
                    } else {
                        // Add duration of current ongoing stop
                        totalTempStopMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(
                                stop.getStartTime(),
                                now
                        );
                    }
                }
            }

            session.setTotalTemporaryStopMinutes(totalTempStopMinutes);

            // Update current stop duration
            if (session.getTemporaryStops() != null && !session.getTemporaryStops().isEmpty()) {
                TemporaryStop currentStop = session.getTemporaryStops()
                        .get(session.getTemporaryStops().size() - 1);
                int currentStopMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                        currentStop.getStartTime(),
                        now
                );
                currentStop.setDuration(currentStopMinutes);
            }
        }

        session.setLastActivity(now);
    }

    private static boolean isCompleted(WorkTimeCalculationResult result, User user) {
        int schedule = user.getSchedule();

        // Calculate required minutes for completion based on schedule
        int requiredMinutes;
        if (schedule == WorkCode.INTERVAL_HOURS_C) { // 8 hours
            requiredMinutes = WorkCode.INTERVAL_HOURS_C * WorkCode.HOUR_DURATION + WorkCode.HALF_HOUR_DURATION; // 8 hours + 30 minutes
        } else {
            requiredMinutes = schedule * WorkCode.HOUR_DURATION; // Just the scheduled hours
        }

        // Update workday completion status
        return result.getRawMinutes() >= requiredMinutes;
    }
}