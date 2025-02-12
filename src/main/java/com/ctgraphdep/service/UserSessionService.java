package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.event.SessionEndEvent;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class UserSessionService {
    private final DataAccessService dataAccess;
    private final UserWorkTimeService userWorkTimeService;
    private final SessionPersistenceService persistenceService;
    private final UserService userService;
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
        this.userService = userService;
        this.pathConfig = pathConfig;
        this.sessionMonitorService = sessionMonitorService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @EventListener
    public void handleSessionEndEvent(SessionEndEvent event) {
        endDay(event.getUsername(), event.getUserId(), event.getFinalMinutes());
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
        try {
            // Try to read local session first
            WorkUsersSessionsStates localSession = dataAccess.readLocalSessionFile(username, userId);

            if (localSession != null) {
                LoggerUtil.debug(this.getClass(),
                        String.format("Found local session for user %s", username));
                return localSession;
            }

            // If no local session and network is available, try specific user's network session
            if (pathConfig.isNetworkAvailable()) {
                // Get the exact network session path for this specific user
                Path networkSessionPath = pathConfig.getNetworkSessionPath(username, userId);

                LoggerUtil.debug(this.getClass(),
                        String.format("Attempting to read network session from path: %s", networkSessionPath));

                // Check if the specific user's network session file exists
                if (Files.exists(networkSessionPath) && Files.size(networkSessionPath) > 0) {
                    try {
                        // Read network session using existing method
                        WorkUsersSessionsStates networkSession =
                                dataAccess.readNetworkSessionFile(username, userId);

                        if (networkSession != null &&
                                username.equals(networkSession.getUsername()) &&
                                userId.equals(networkSession.getUserId())) {

                            // Sync network session to local
                            dataAccess.writeLocalSessionFile(networkSession);

                            LoggerUtil.info(this.getClass(),
                                    String.format("Initialized local session from network for user %s", username));
                            return networkSession;
                        }
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(),
                                String.format("Error reading network session for user %s: %s",
                                        username, e.getMessage()));
                    }
                } else {
                    LoggerUtil.debug(this.getClass(),
                            String.format("No network session file found for user %s", username));
                }
            }

            // If no session exists, create new one
            WorkUsersSessionsStates newSession = initializeSession(username, userId, LocalDateTime.now());
            newSession.setSessionStatus(WorkCode.WORK_OFFLINE);
            saveSession(username, newSession);

            LoggerUtil.info(this.getClass(),
                    String.format("Created new offline session for user %s", username));

            return newSession;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Critical error resolving session for user %s: %s", username, e.getMessage()));

            // Fallback to creating a new offline session
            WorkUsersSessionsStates fallbackSession = initializeSession(username, userId, LocalDateTime.now());
            fallbackSession.setSessionStatus(WorkCode.WORK_OFFLINE);
            saveSession(username, fallbackSession);

            return fallbackSession;
        }
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
        try {
            // Use DataAccessService to write session file
            dataAccess.writeLocalSessionFile(session);

            // Update in-memory session map
            userSessions.put(username, session);

            LoggerUtil.debug(this.getClass(),
                    String.format("Saved session for user %s", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to save session for user %s: %s",
                            username, e.getMessage()));
            throw new RuntimeException("Session persistence failed", e);
        }
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
            LoggerUtil.info(this.getClass(),
                    "Starting temporary stop for user: " + username);

            WorkUsersSessionsStates session = getCurrentSession(username, userId);
            // Log before state change
            LoggerUtil.debug(this.getClass(),
                    "Current session status: " + session.getSessionStatus());

            if (isValidSessionForOperation(session, WorkCode.WORK_ONLINE)) {
                LocalDateTime now = LocalDateTime.now();
                updateSessionForTemporaryStop(session, now);
                saveSession(username, session);

                // Log after state change
                LoggerUtil.debug(this.getClass(),
                        "New session status: " + session.getSessionStatus());
            }
            return null;
        });
    }

    private void updateSessionForTemporaryStop(WorkUsersSessionsStates session, LocalDateTime now) {
        // Calculate work minutes from last start to now
        int workedMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(session.getCurrentStartTime(), now);

        // Add only actual work minutes to total
        session.setTotalWorkedMinutes((session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0) + workedMinutes);

        // Create and add new temporary stop entry
        TemporaryStop stop = new TemporaryStop();
        stop.setStartTime(now);

        // Initialize temporaryStops if null
        if (session.getTemporaryStops() == null) {
            session.setTemporaryStops(new ArrayList<>());
        }
        session.getTemporaryStops().add(stop);

        // Update session state
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

        // Update the last temporary stop entry
        if (!session.getTemporaryStops().isEmpty()) {
            TemporaryStop lastStop = session.getTemporaryStops().get(session.getTemporaryStops().size() - 1);
            lastStop.setEndTime(now);
            lastStop.setDuration(stopMinutes);
        }

        // Update total temporary stop minutes by summing all stop durations
        int totalStopMinutes = session.getTemporaryStops().stream()
                .mapToInt(stop -> stop.getDuration() != null ? stop.getDuration() : 0)
                .sum();
        session.setTotalTemporaryStopMinutes(totalStopMinutes);

        // Calculate final worked minutes (raw work minus stops)
        int actualMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;
        session.setFinalWorkedMinutes(actualMinutes);

        // Update session state for resuming work
        session.setSessionStatus(WorkCode.WORK_ONLINE);
        session.setCurrentStartTime(now);  // Reset start time for next work period
        session.setLastActivity(now);
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

    public void resumeSession(WorkUsersSessionsStates session) {
        executeWithLock(() -> {
            if (session != null && (
                    WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                            WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()))) {

                // Just update last activity without changing status
                session.setLastActivity(LocalDateTime.now());

                // Save the session with its current status
                saveSession(session.getUsername(), session);

                // Start appropriate monitoring based on current status
                sessionMonitorService.startMonitoring(session);

                LoggerUtil.info(this.getClass(),
                        String.format("Resumed existing session for user %s with status %s",
                                session.getUsername(), session.getSessionStatus()));
            }
            return null;
        });
    }
    //end day
    public void endDay(String username, Integer userId, Integer finalMinutes) {
        executeWithLock(() -> {
            try {
                WorkUsersSessionsStates session = loadAndValidateSession(username, userId);
                if (session != null) {
                    processSessionEnd(session, finalMinutes);
                    updateWorkTimeAndPersist(session);
                    cleanupSession(username, userId);
                    logSessionEnd(username, finalMinutes);
                }
            } catch (Exception e) {
                handleEndDayError(username, e);
            }
            return null;
        });
    }

    private WorkUsersSessionsStates loadAndValidateSession(String username, Integer userId) {
        try {
            // Read local session file
            WorkUsersSessionsStates session = dataAccess.readLocalSessionFile(username, userId);

            // Validate session status
            if (session == null || !WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                LoggerUtil.warn(this.getClass(),
                        String.format("No active session found for user %s", username));
                return null;
            }

            return session;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading session for user %s: %s",
                            username, e.getMessage()));
            return null;
        }
    }

    private void processSessionEnd(WorkUsersSessionsStates session, Integer finalMinutes) {
        LocalDateTime now = LocalDateTime.now();
        session.setSessionStatus(WorkCode.WORK_OFFLINE);
        session.setDayEndTime(now);
        session.setFinalWorkedMinutes(finalMinutes);
        session.setWorkdayCompleted(true);
        session.setLastActivity(now);
    }

    private void updateWorkTimeAndPersist(WorkUsersSessionsStates session) {
        try {
            // Update worktime entry
            updateWorktimeEntry(session.getUsername(), session);

            // Persist session using DataAccessService
            dataAccess.writeLocalSessionFile(session);

            // Stop session monitoring
            sessionMonitorService.stopMonitoring(
                    session.getUsername(),
                    session.getUserId()
            );

            LoggerUtil.info(this.getClass(),
                    String.format("Successfully persisted session for user %s",
                            session.getUsername()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to update and persist session for user %s: %s",
                            session.getUsername(), e.getMessage()));
            throw new RuntimeException("Session update and persistence failed", e);
        }
    }

    private void cleanupSession(String username, Integer userId) {
        userSessions.remove(username);
        activeSessions.remove(userId);
    }

    private void logSessionEnd(String username, Integer finalMinutes) {
        LoggerUtil.info(this.getClass(),
                String.format("Successfully ended session for user %s with %d minutes",
                        username, finalMinutes));
    }

    private void handleEndDayError(String username, Exception e) {
        LoggerUtil.error(this.getClass(),
                String.format("Failed to end session for user %s: %s",
                        username, e.getMessage()));
        throw new RuntimeException("Failed to end session", e);
    }
    //end day.
    private void updateWorktimeEntry(String username, WorkUsersSessionsStates session) {
        LocalDateTime now = LocalDateTime.now();
        WorkTimeTable entry = createWorkTimeEntryFromSession(session, now);
        entry.setAdminSync(SyncStatus.USER_INPUT); // Now set to USER_INPUT when completed
        LocalDate workDate = session.getDayStartTime().toLocalDate();

        // Use the session username for authentication
        userWorkTimeService.saveWorkTimeEntry(username, entry, workDate.getYear(), workDate.getMonthValue(), session.getUsername()  // Pass the username from session
        );

        LoggerUtil.info(this.getClass(),
                String.format("Updated worktime entry for user %s - Total minutes: %d, Overtime: %d",
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
}