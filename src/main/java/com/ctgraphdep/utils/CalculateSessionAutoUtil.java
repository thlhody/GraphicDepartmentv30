package com.ctgraphdep.utils;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CalculateSessionAutoUtil {
    /**
     * Calculate all session metrics
     */
    public static SessionCalculationResult calculateSessionMetrics(WorkUsersSessionsStates session, Integer schedule) {
        SessionCalculationResult result = new SessionCalculationResult();

        // Default to 8 hours if schedule is null
        int workSchedule = schedule != null ? schedule : WorkCode.INTERVAL_HOURS_C;

        // Calculate work periods and total work minutes
        result.setWorkPeriods(calculateWorkPeriods(session));
        result.setTotalWorkMinutes(calculateTotalWorkMinutes(result.getWorkPeriods()));

        // Calculate temporary stop minutes
        result.setTotalTemporaryStopMinutes(calculateTemporaryStopMinutes(session));

        // Apply work time calculations (lunch break, rounding, overtime)
        WorkTimeCalculationResult timeResult = CalculateWorkHoursUtil.calculateWorkTime(
                result.getTotalWorkMinutes(),
                workSchedule
        );

        result.setFinalWorkMinutes(timeResult.getFinalTotalMinutes());
        result.setOvertimeMinutes(timeResult.getOvertimeMinutes());
        result.setLunchBreakDeducted(timeResult.isLunchDeducted());

        return result;
    }

    /**
     * Calculate work periods between temporary stops
     */
    private static List<WorkPeriod> calculateWorkPeriods(WorkUsersSessionsStates session) {
        List<WorkPeriod> periods = new ArrayList<>();
        LocalDateTime periodStart = session.getDayStartTime();

        if (session.getTemporaryStops() == null || session.getTemporaryStops().isEmpty()) {
            // No stops - single work period
            LocalDateTime endTime = session.getDayEndTime() != null ?
                    session.getDayEndTime() : LocalDateTime.now();
            periods.add(new WorkPeriod(periodStart, endTime));
            return periods;
        }

        // Process each temporary stop
        for (TemporaryStop stop : session.getTemporaryStops()) {
            // Add work period before this stop
            periods.add(new WorkPeriod(periodStart, stop.getStartTime()));

            // Next period starts after this stop
            if (stop.getEndTime() != null) {
                periodStart = stop.getEndTime();
            } else if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // Current stop is active - no more periods
                break;
            }
        }

        // Add final work period if not in temporary stop
        if (!WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
            LocalDateTime endTime = session.getDayEndTime() != null ?
                    session.getDayEndTime() : LocalDateTime.now();
            periods.add(new WorkPeriod(periodStart, endTime));
        }

        return periods;
    }

    /**
     * Calculate total work minutes from work periods
     */
    private static int calculateTotalWorkMinutes(List<WorkPeriod> workPeriods) {
        return workPeriods.stream()
                .mapToInt(WorkPeriod::getMinutes)
                .sum();
    }

    /**
     * Calculate total temporary stop minutes
     */
    private static int calculateTemporaryStopMinutes(WorkUsersSessionsStates session) {
        if (session.getTemporaryStops() == null || session.getTemporaryStops().isEmpty()) {
            return 0;
        }

        // Sum all completed stops
        int completedStopMinutes = session.getTemporaryStops().stream()
                .filter(stop -> stop.getStartTime() != null && stop.getEndTime() != null)
                .mapToInt(stop -> CalculateWorkHoursUtil.calculateMinutesBetween(
                        stop.getStartTime(),
                        stop.getEndTime()
                ))
                .sum();

        // Add current active stop if exists
        int currentStopMinutes = 0;
        if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) &&
                !session.getTemporaryStops().isEmpty()) {
            TemporaryStop lastStop = session.getTemporaryStops().get(
                    session.getTemporaryStops().size() - 1);
            if (lastStop.getStartTime() != null && lastStop.getEndTime() == null) {
                currentStopMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                        lastStop.getStartTime(),
                        LocalDateTime.now()
                );
            }
        }

        return completedStopMinutes + currentStopMinutes;
    }

    /**
     * Check if session is from previous day
     */
    public static boolean isSessionFromPreviousDay(WorkUsersSessionsStates session) {
        return session != null &&
                session.getDayStartTime() != null &&
                !session.getDayStartTime().toLocalDate()
                        .equals(LocalDateTime.now().toLocalDate());
    }

    /**
     * Update session with calculation results
     */
    public static void updateSessionWithCalculations(
            WorkUsersSessionsStates session,
            SessionCalculationResult result) {
        session.setTotalWorkedMinutes(result.getTotalWorkMinutes());
        session.setFinalWorkedMinutes(result.getFinalWorkMinutes());
        session.setTotalOvertimeMinutes(result.getOvertimeMinutes());
        session.setLunchBreakDeducted(result.isLunchBreakDeducted());
        session.setTotalTemporaryStopMinutes(result.getTotalTemporaryStopMinutes());
        session.setLastActivity(LocalDateTime.now());
    }
}