package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.SessionCalculationResult;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.enums.SessionEndRule;
import com.ctgraphdep.utils.CalculateSessionAutoUtil;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CalculateSessionService {
    private final SessionPersistenceService persistenceService;

    public CalculateSessionService(SessionPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Core validation and rule checking
     */
    public boolean isValidSession(WorkUsersSessionsStates session) {
        return session != null &&
                session.getUsername() != null &&
                session.getUserId() != null &&
                session.getDayStartTime() != null &&
                (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
    }

    /**
     * Checks if any session end rule applies
     */
    public Optional<SessionEndRule> checkSessionEndRules(WorkUsersSessionsStates session, Integer schedule) {
        if (!isValidSession(session)) {
            return Optional.empty();
        }

        SessionEndRule applicableRule = SessionEndRule.findApplicableRule(session, schedule);
        return Optional.ofNullable(applicableRule);
    }

    /**
     * Calculate all session metrics and apply rules
     */
    public WorkUsersSessionsStates calculateSessionMetrics(WorkUsersSessionsStates session, Integer schedule) {
        try {
            if (!isValidSession(session)) {
                return session;
            }

            // Use new utility to calculate all metrics
            SessionCalculationResult result =
                    CalculateSessionAutoUtil.calculateSessionMetrics(session, schedule);

            // Update session with results
            CalculateSessionAutoUtil.updateSessionWithCalculations(session, result);

            // Log calculation results
            LoggerUtil.debug(this.getClass(),
                    String.format("Calculated metrics for session - User: %s, Total: %d, Final: %d, TempStop: %d",
                            session.getUsername(),
                            result.getTotalWorkMinutes(),
                            result.getFinalWorkMinutes(),
                            result.getTotalTemporaryStopMinutes()));

            // Persist changes
            persistenceService.persistSession(session);

            return session;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error calculating session metrics: %s", e.getMessage()));
            return session;
        }
    }

    /**
     * Calculate final minutes for ending session
     */
    public Integer calculateFinalMinutes(User user, WorkUsersSessionsStates session) {
        if (!isValidSession(session)) {
            return 0;
        }

        // For previous day sessions, use schedule duration
        if (CalculateSessionAutoUtil.isSessionFromPreviousDay(session)) {
            return WorkCode.calculateFullDayDuration(user.getSchedule());
        }

        // Calculate using full metrics
        SessionCalculationResult result =
                CalculateSessionAutoUtil.calculateSessionMetrics(session, user.getSchedule());
        return result.getFinalWorkMinutes();
    }

    /**
     * Check if session is from previous day
     */
    public boolean isSessionFromPreviousDay(WorkUsersSessionsStates session) {
        return CalculateSessionAutoUtil.isSessionFromPreviousDay(session);
    }

    /**
     * Calculate current temporary stop duration
     */
    public int calculateCurrentTempStopDuration(WorkUsersSessionsStates session) {
        if (!isActiveTemporaryStop(session)) {
            return 0;
        }

        return CalculateWorkHoursUtil.calculateMinutesBetween(
                session.getLastTemporaryStopTime(),
                LocalDateTime.now()
        );
    }

    private boolean isActiveTemporaryStop(WorkUsersSessionsStates session) {
        return isValidSession(session) &&
                WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) &&
                session.getLastTemporaryStopTime() != null;
    }
}