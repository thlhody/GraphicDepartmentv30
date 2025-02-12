package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class CalculateSessionService {
    private final DataAccessService dataAccess;
    private final SessionPersistenceService persistenceService;

    public CalculateSessionService(DataAccessService dataAccess, SessionPersistenceService persistenceService) {
        this.dataAccess = dataAccess;
        this.persistenceService = persistenceService;
    }

    // Core validation
    public boolean isValidSession(WorkUsersSessionsStates session) {
        return session != null &&
                session.getUsername() != null &&
                session.getUserId() != null &&
                session.getDayStartTime() != null &&
                (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
    }

    // For checking if session should end
    public boolean shouldEndSession(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        if (!isValidSession(session)) return false;
        Integer schedule = getScheduleFromLocalUser(session.getUsername());
        int effectiveMinutes = calculateEffectiveMinutes(session, currentTime);
        return effectiveMinutes >= WorkCode.calculateFullDayDuration(schedule);
    }

    // For temporary stop maximum duration checks
    public boolean isStuckTemporaryStop(WorkUsersSessionsStates session) {
        return isValidSession(session) &&
                WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) &&
                session.getLastTemporaryStopTime() != null &&
                ChronoUnit.HOURS.between(
                        session.getLastTemporaryStopTime(),
                        LocalDateTime.now()
                ) >= WorkCode.MAX_TEMP_STOP_HOURS;
    }

    // For session recovery
    public boolean isSessionFromPreviousDay(WorkUsersSessionsStates session) {
        return isValidSession(session) &&
                !session.getDayStartTime().toLocalDate().equals(LocalDateTime.now().toLocalDate());
    }

    // Calculate final minutes for ending session
    public Integer calculateFinalMinutes(User user, WorkUsersSessionsStates session) {
        if (!isValidSession(session)) return 0;

        // Previous day sessions use full schedule time
        if (isSessionFromPreviousDay(session)) {
            return WorkCode.calculateFullDayDuration(user.getSchedule());
        }

        // Calculate actual worked minutes minus breaks
        return calculateEffectiveMinutes(session, LocalDateTime.now());
    }

    // Helper methods
    private Integer getScheduleFromLocalUser(String username) {
        try {
            return dataAccess.readLocalUsers().stream()
                    .filter(user -> username.equals(user.getUsername()))
                    .findFirst()
                    .map(User::getSchedule)
                    .orElse(WorkCode.INTERVAL_HOURS_C);
        } catch (Exception e) {
            return WorkCode.INTERVAL_HOURS_C;
        }
    }

    private Integer calculateEffectiveMinutes(WorkUsersSessionsStates session, LocalDateTime currentTime) {
        int totalMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                session.getDayStartTime(),
                currentTime
        );
        return totalMinutes - (session.getTotalTemporaryStopMinutes() != null ?
                session.getTotalTemporaryStopMinutes() : 0);
    }

    public int calculateCurrentTempStopDuration(WorkUsersSessionsStates session) {
        if (!WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) ||
                session.getLastTemporaryStopTime() == null) {
            return 0;
        }

        return CalculateWorkHoursUtil.calculateMinutesBetween(
                session.getLastTemporaryStopTime(),
                LocalDateTime.now()
        );
    }


    // New method without persistence
    public void calculateCurrentWorkWithoutPersist(WorkUsersSessionsStates session, Integer userSchedule) {
        if (!isValidSession(session)) return;

        // Do calculations
        calculateCurrentWorkInternal(session, userSchedule);
    }

    // Original method with persistence
    public void calculateCurrentWork(WorkUsersSessionsStates session, Integer userSchedule) {
        if (!isValidSession(session)) return;

        // Do calculations
        calculateCurrentWorkInternal(session, userSchedule);

        // Persist changes
        persistenceService.persistSession(session);
    }

    // Internal calculation logic
    private void calculateCurrentWorkInternal(WorkUsersSessionsStates session, Integer userSchedule) {
        // Original calculation logic here
    }
}