package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.event.SessionEndEvent;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class UserSessionService {
    private final DataAccessService dataAccess;
    private final UserWorkTimeService userWorkTimeService;
    private final SessionPersistenceService persistenceService;
    private final SessionMonitorService sessionMonitorService;
    private final PathConfig pathConfig;
    private final ReentrantLock sessionLock = new ReentrantLock();
    private final Map<String, WorkUsersSessionsStates> userSessions = new ConcurrentHashMap<>();
    private static final TypeReference<WorkUsersSessionsStates> SESSION_TYPE = new TypeReference<WorkUsersSessionsStates>() {};
    private final Map<Integer, WorkUsersSessionsStates> activeSessions = new ConcurrentHashMap<>();

    public UserSessionService(
            DataAccessService dataAccess,
            UserWorkTimeService userWorkTimeService, SessionPersistenceService persistenceService,
            PathConfig pathConfig, UserService userService, @Lazy SessionMonitorService sessionMonitorService) {
        this.dataAccess = dataAccess;
        this.userWorkTimeService = userWorkTimeService;
        this.persistenceService = persistenceService;
        this.pathConfig = pathConfig;
        this.sessionMonitorService = sessionMonitorService;

        LoggerUtil.initialize(this.getClass(), "Initializing User Session Service");
    }

    public WorkUsersSessionsStates getCurrentSession(String username, Integer userId) {
        // Clear any stale sessions first
        clearStaleSession(username);

        // Check from userSessions map first
        WorkUsersSessionsStates session = userSessions.get(username);
        if (session != null) {
            return session;
        }

        try {
            session = resolveSession(username, userId);
            userSessions.put(username, session);
            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error getting session for user %s: %s", username, e.getMessage()));

            // Create new session as fallback
            session = initializeSession(username, userId, LocalDateTime.now());
            session.setSessionStatus(WorkCode.WORK_OFFLINE);
            saveSession(username, session);
            userSessions.put(username, session);
            return session;
        }
    }

    private void clearStaleSession(String username) {
        WorkUsersSessionsStates existingSession = userSessions.get(username);
        if (existingSession != null && WorkCode.WORK_OFFLINE.equals(existingSession.getSessionStatus())) {
            userSessions.remove(username);
        }
    }

    private WorkUsersSessionsStates resolveSession(String username, Integer userId) {
        // Get local and network paths using DataAccessService
        Path localPath = dataAccess.getSessionPath(username, userId);
        Path networkPath = dataAccess.getSessionPath(username, userId);  // This will use the correct format

        WorkUsersSessionsStates localSession = null;
        WorkUsersSessionsStates networkSession = null;

        // Try to read local session
        if (dataAccess.fileExists(localPath)) {
            try {
                localSession = dataAccess.readFile(localPath, SESSION_TYPE, false);
                LoggerUtil.debug(this.getClass(),
                        String.format("Found local session for user %s", username));
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Could not read local session for user %s: %s", username, e.getMessage()));
            }
        }

        // Try to read network session
        if (dataAccess.fileExists(networkPath)) {
            try {
                networkSession = dataAccess.readFile(networkPath, SESSION_TYPE, false);
                LoggerUtil.debug(this.getClass(),
                        String.format("Found network session for user %s", username));
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Could not read network session for user %s: %s", username, e.getMessage()));
            }
        }

        // Resolve which session to use
        WorkUsersSessionsStates session = resolveSessionConflict(
                localPath, networkPath, localSession, networkSession, username);

        // If no session exists, create new one
        if (session == null) {
            session = initializeSession(username, userId, LocalDateTime.now());
            session.setSessionStatus(WorkCode.WORK_OFFLINE);
            saveSession(username, session);
            LoggerUtil.info(this.getClass(),
                    String.format("Created new offline session for user %s", username));
        }

        return session;
    }

    private WorkUsersSessionsStates resolveSessionConflict(
            Path localPath,
            Path networkPath,
            WorkUsersSessionsStates localSession,
            WorkUsersSessionsStates networkSession,
            String username) {

        WorkUsersSessionsStates resolvedSession = null;

        if (localSession != null && networkSession != null) {
            // Both exist - use newer one
            if (dataAccess.isFileNewer(networkPath, localPath)) {
                resolvedSession = networkSession;
                // Update local with network version
                dataAccess.writeFile(localPath, networkSession);
                LoggerUtil.info(this.getClass(),
                        String.format("Using newer network session for user %s", username));
            } else {
                resolvedSession = localSession;
                // Update network with local version
                dataAccess.writeFile(networkPath, localSession);
                LoggerUtil.info(this.getClass(),
                        String.format("Using newer local session for user %s", username));
            }
        } else if (localSession != null) {
            resolvedSession = localSession;
            // Copy to network if possible
            if (dataAccess.fileExists(networkPath.getParent())) {
                dataAccess.writeFile(networkPath, localSession);
                LoggerUtil.info(this.getClass(),
                        String.format("Copied local session to network for user %s", username));
            }
        } else if (networkSession != null) {
            resolvedSession = networkSession;
            // Copy to local
            dataAccess.writeFile(localPath, networkSession);
            LoggerUtil.info(this.getClass(),
                    String.format("Copied network session to local for user %s", username));
        }

        return resolvedSession;
    }

    private WorkUsersSessionsStates initializeSession(String username, Integer userId, LocalDateTime startTime) {
        WorkUsersSessionsStates session = new WorkUsersSessionsStates();
        session.setUserId(userId);
        session.setUsername(username);
        session.setSessionStatus(WorkCode.WORK_ONLINE);
        session.setDayStartTime(startTime);
        session.setCurrentStartTime(startTime);
        session.setTotalWorkedMinutes(0);
        session.setFinalWorkedMinutes(0);
        session.setTotalOvertimeMinutes(0);
        session.setLunchBreakDeducted(false);
        session.setWorkdayCompleted(false);
        session.setTemporaryStopCount(0);
        session.setTotalTemporaryStopMinutes(0);
        session.setTemporaryStops(new ArrayList<>());
        session.setLastTemporaryStopTime(null);
        session.setLastActivity(LocalDateTime.now());
        return session;
    }

    private void saveSession(String username, WorkUsersSessionsStates session) {
        Path sessionPath = pathConfig.getSessionFilePath(username, session.getUserId());
        dataAccess.writeFile(sessionPath, session);
        userSessions.put(username, session);
    }

    private void createWorktimeEntry(String username, Integer userId, WorkUsersSessionsStates session) {
        LocalDate today = LocalDate.now();
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(today);
        entry.setDayStartTime(session.getDayStartTime());
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setLunchBreakDeducted(false);
        entry.setAdminSync(SyncStatus.USER_IN_PROCESS);

        userWorkTimeService.saveWorkTimeEntry(username, entry, today.getYear(), today.getMonthValue());
    }

    //temporary stop and resume
    public void startTemporaryStop(String username, Integer userId) {
        executeWithLock(() -> {
            WorkUsersSessionsStates session = getCurrentSession(username, userId);
            if (isValidSessionForOperation(session, WorkCode.WORK_ONLINE)) {
                LocalDateTime now = LocalDateTime.now();
                updateSessionForTemporaryStop(session, now);
                saveSession(username, session);

                LoggerUtil.info(this.getClass(), String.format("Started temporary stop for user %s", username));
            }
            return null;
        });
    }

    private void updateSessionForTemporaryStop(WorkUsersSessionsStates session, LocalDateTime now) {
        int workedMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                session.getCurrentStartTime(), now);

        session.setTotalWorkedMinutes(session.getTotalWorkedMinutes() + workedMinutes);
        session.setSessionStatus(WorkCode.WORK_TEMPORARY_STOP);
        session.setLastTemporaryStopTime(now);
        session.setTemporaryStopCount(session.getTemporaryStopCount() + 1);
        session.setLastActivity(now);
    }

    public void resumeFromTemporaryStop(String username, Integer userId) {
        executeWithLock(() -> {
            WorkUsersSessionsStates session = getCurrentSession(username, userId);
            if (isValidSessionForOperation(session, WorkCode.WORK_TEMPORARY_STOP)) {
                LocalDateTime now = LocalDateTime.now();
                updateSessionForResume(session, now);
                saveSession(username, session);

                LoggerUtil.info(this.getClass(), String.format("Resumed session for user %s after temporary stop", username));
            }
            return null;
        });
    }

    private void updateSessionForResume(WorkUsersSessionsStates session, LocalDateTime now) {
        int stopMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                session.getLastTemporaryStopTime(), now);

        session.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes() + stopMinutes);
        session.setSessionStatus(WorkCode.WORK_ONLINE);
        session.setCurrentStartTime(now);
        session.setLastActivity(now);
    }

    // Add this method to UserSessionService
    public void resumeSession(WorkUsersSessionsStates session) {
        executeWithLock(() -> {
            if (session != null && (
                    WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                            WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()))) {

                // Update last activity
                session.setLastActivity(LocalDateTime.now());

                // If was in temporary stop, handle resumption
                if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                    updateSessionForResume(session, LocalDateTime.now());
                }

                // Save and start monitoring
                saveSession(session.getUsername(), session);
                sessionMonitorService.startMonitoring(session);

                LoggerUtil.info(this.getClass(),
                        String.format("Resumed existing session for user %s", session.getUsername()));
            }
            return null;
        });
    }

    @EventListener
    public void handleSessionEndEvent(SessionEndEvent event) {
        endDay(event.getUsername(), event.getUserId(), event.getFinalMinutes());
    }

    @SuppressWarnings("unused")
    public void endDay(String username, Integer userId, Integer finalMinutes) {
        executeWithLock(() -> {
            WorkUsersSessionsStates session = getCurrentSession(username, userId);
            if (session != null && WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                // Stop monitoring this session
                sessionMonitorService.stopMonitoring(username, userId);

                // Update session state
                session.setSessionStatus(WorkCode.WORK_OFFLINE);
                session.setDayEndTime(LocalDateTime.now());
                session.setFinalWorkedMinutes(finalMinutes);
                session.setWorkdayCompleted(true);
                session.setLastActivity(LocalDateTime.now());

                // Update worktime entry
                updateWorktimeEntry(username, session);

                // Save final state
                String sessionPath = pathConfig.getSessionFilePath(username, userId).toString();
                persistenceService.persistSession(session, sessionPath);

                // Clear session
                userSessions.remove(username);
                activeSessions.remove(userId);

                LoggerUtil.info(this.getClass(),
                        String.format("Ended session for user %s with %d minutes",
                                username, finalMinutes));
            }
            return null;
        });
    }

    public void startDay(String username, Integer userId) {
        executeWithLock(() -> {
            // Clear any existing session
            userSessions.remove(username);
            activeSessions.remove(userId);

            // Create fresh session
            LocalDateTime startTime = LocalDateTime.now().minusMinutes(WorkCode.BUFFER_MINUTES);
            WorkUsersSessionsStates newSession = initializeSession(username, userId, startTime);
            saveSession(username, newSession);
            createWorktimeEntry(username, userId, newSession);

            // Start monitoring this session
            sessionMonitorService.startMonitoring(newSession);

            LoggerUtil.info(this.getClass(),
                    String.format("Started new session for user %s (start time set to %s)",
                            username, startTime));

            return newSession;
        });
    }

    private void updateWorktimeEntry(String username, WorkUsersSessionsStates session) {
        LocalDateTime now = LocalDateTime.now();
        WorkTimeTable entry = createWorkTimeEntryFromSession(session, now);
        entry.setAdminSync(SyncStatus.USER_INPUT);  // Now set to USER_INPUT when completed
        LocalDate workDate = session.getDayStartTime().toLocalDate();
        userWorkTimeService.saveWorkTimeEntry(
                username,
                entry,
                workDate.getYear(),
                workDate.getMonthValue()
        );

        LoggerUtil.info(this.getClass(), String.format("Updated worktime entry for user %s - Total minutes: %d, Overtime: %d",
                        username, entry.getTotalWorkedMinutes(), entry.getTotalOvertimeMinutes()));
    }

    private WorkTimeTable createWorkTimeEntryFromSession(WorkUsersSessionsStates session, LocalDateTime endTime) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(session.getUserId());
        entry.setWorkDate(session.getDayStartTime().toLocalDate());
        entry.setDayStartTime(session.getDayStartTime());
        entry.setDayEndTime(endTime);
        entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
        entry.setTotalOvertimeMinutes(session.getTotalOvertimeMinutes() != null ? session.getTotalOvertimeMinutes() : 0);
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setLunchBreakDeducted(session.getLunchBreakDeducted());
        entry.setAdminSync(SyncStatus.USER_INPUT);
        entry.setTimeOffType(null);
        return entry;
    }

    // Helper methods to reduce duplication

    private <T> T executeWithLock(SessionOperation<T> operation) {
        sessionLock.lock();
        try {
            return operation.execute();
        } finally {
            sessionLock.unlock();
        }
    }

    // Functional interface for lock operations
    @FunctionalInterface
    private interface SessionOperation<T> {
        T execute();
    }

    private boolean isValidSessionForOperation(WorkUsersSessionsStates session, String expectedStatus) {
        return session != null && expectedStatus.equals(session.getSessionStatus());
    }

    private boolean isFileNewer(Path file1, Path file2) {
        try {
            return Files.getLastModifiedTime(file1).compareTo(
                    Files.getLastModifiedTime(file2)) > 0;
        } catch (IOException e) {
            return false;
        }
    }

}