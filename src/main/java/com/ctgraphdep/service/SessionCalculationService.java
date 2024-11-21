package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SessionCalculationService {

    public SessionCalculationService() {
        LoggerUtil.initialize(this.getClass(), "Initializing Session Calculation Service");
    }

    private boolean isActiveSession(WorkUsersSessionsStates session) {
        return WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());
    }

    public void calculateCurrentWork(WorkUsersSessionsStates session, int userSchedule) {
        try {
            if (session == null || !isActiveSession(session)) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            // Total Work Raw - always just time from start to now
            int rawMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                    session.getDayStartTime(),
                    now
            );

            // Calculate work time including breaks and schedule
            // This will be raw time minus breaks for actual work calculations
            WorkTimeCalculationResult result = CalculateWorkHoursUtil.calculateWorkTime(
                    rawMinutes - session.getTotalTemporaryStopMinutes(),
                    userSchedule
            );

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

        // Just the raw time from start to now
        session.setTotalWorkedMinutes(rawMinutes);

        // Processed time (raw - breaks, with schedule rules)
        session.setFinalWorkedMinutes(result.getFinalTotalMinutes());
        session.setTotalOvertimeMinutes(result.getOvertimeMinutes());
        session.setLunchBreakDeducted(result.isLunchDeducted());
        session.setLastActivity(LocalDateTime.now());
    }

}