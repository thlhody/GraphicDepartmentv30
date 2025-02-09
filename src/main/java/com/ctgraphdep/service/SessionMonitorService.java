package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class SessionMonitorService {
    private final SystemNotificationService notificationService;
    private final CalculateSessionService calculateSessionService;
    private final UserService userService;
    private final BackgroundMonitorExecutor backgroundMonitor;
    private UserSessionService userSessionService;

    // Track if initial warning has been shown
    private final Map<String, Boolean> initialWarningShown = new ConcurrentHashMap<>();
    private final Map<String, Boolean> tempStopMonitoringActive = new ConcurrentHashMap<>();

    public SessionMonitorService(
            SystemNotificationService notificationService,
            CalculateSessionService calculateSessionService,
            UserService userService,
            BackgroundMonitorExecutor backgroundMonitor) {
        this.notificationService = notificationService;
        this.calculateSessionService = calculateSessionService;
        this.userService = userService;
        this.backgroundMonitor = backgroundMonitor;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Autowired
    public void setUserSessionService(UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    public void startMonitoring(WorkUsersSessionsStates session) {
        if (!calculateSessionService.isValidSession(session)) {
            return;
        }

        String sessionKey = getSessionKey(session.getUsername(), session.getUserId());

        // Stop any existing monitoring
        stopMonitoring(session.getUsername(), session.getUserId());

        initialWarningShown.put(sessionKey, false);
        tempStopMonitoringActive.put(sessionKey, false);

        // Add debug logging
        LoggerUtil.debug(this.getClass(),
                String.format("Starting monitoring for session with status: %s",
                        session.getSessionStatus()));

        if (session.getSessionStatus().equals(WorkCode.WORK_TEMPORARY_STOP)) {
            startTempStopMonitoring(session);
            LoggerUtil.info(this.getClass(),
                    String.format("Started temporary stop monitoring for user %s",
                            session.getUsername()));
        } else {
            backgroundMonitor.startSessionMonitoring(session, this::checkSession);
            LoggerUtil.info(this.getClass(),
                    String.format("Started regular monitoring for user %s",
                            session.getUsername()));
        }
    }

    private void startTempStopMonitoring(WorkUsersSessionsStates session) {
        String sessionKey = getSessionKey(session.getUsername(), session.getUserId());
        if (tempStopMonitoringActive.getOrDefault(sessionKey, false)) {
            return;
        }

        backgroundMonitor.startHourlyMonitoring(
                session.getUsername(),
                session.getUserId(),
                () -> checkTempStop(session)
        );
        tempStopMonitoringActive.put(sessionKey, true);

        LoggerUtil.info(this.getClass(),
                String.format("Started temporary stop monitoring for user %s", session.getUsername()));
    }

    private void checkTempStop(WorkUsersSessionsStates session) {
        try {
            User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Get fresh session state
            WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(
                    session.getUsername(),
                    session.getUserId()
            );

            // Calculate time in temporary stop
            int tempStopMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(
                    currentSession.getLastTemporaryStopTime(),
                    LocalDateTime.now()
            );

            // Show warning every TEMP_STOP_WARNING_INTERVAL minutes (default 60)
            if (tempStopMinutes > 0 && tempStopMinutes % WorkCode.TEMP_STOP_WARNING_INTERVAL == 0) {
                notificationService.showLongTempStopWarning(
                        session.getUsername(),
                        session.getUserId(),
                        session.getLastTemporaryStopTime()
                );
            }

            // Check for max duration
            if (tempStopMinutes >= WorkCode.MAX_TEMP_STOP_HOURS * 60) {
                endSession(session.getUsername(), session.getUserId());
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking temporary stop: " + e.getMessage());
        }
    }

    private void checkSession(WorkUsersSessionsStates session) {
        LoggerUtil.debug(this.getClass(), String.format(
                "Checking session - Username: %s, Status: %s, LastActivity: %s",
                session.getUsername(),
                session.getSessionStatus(),
                session.getLastActivity()));
        try {
            WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(
                    session.getUsername(),
                    session.getUserId()
            );

            LoggerUtil.debug(this.getClass(), String.format(
                    "Checking session - Username: %s, Current Status: %s, Original Status: %s",
                    session.getUsername(),
                    currentSession.getSessionStatus(),
                    session.getSessionStatus()
            ));

            // Skip if status changed to temp stop
            if (WorkCode.WORK_TEMPORARY_STOP.equals(currentSession.getSessionStatus())) {
                LoggerUtil.debug(this.getClass(),
                        "Skipping regular check for temp stop session");
                return;
            }

            User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (calculateSessionService.shouldEndSession(user, session, LocalDateTime.now()) && !session.getSessionStatus().equals(WorkCode.WORK_TEMPORARY_STOP)) {
                String sessionKey = getSessionKey(session.getUsername(), session.getUserId());

                if (!initialWarningShown.getOrDefault(sessionKey, false)) {
                    int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
                    notificationService.showSessionWarning(
                            session.getUsername(),
                            session.getUserId(),
                            finalMinutes);

                    initialWarningShown.put(sessionKey, true);
                    markSessionContinued(session.getUsername(), session.getUserId());
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking session: " + e.getMessage());
        }
    }

    public void markSessionContinued(String username, Integer userId) {
        backgroundMonitor.startHourlyMonitoring(username, userId,
                () -> checkContinuedSession(username, userId));

        LoggerUtil.info(this.getClass(),
                String.format("Started hourly monitoring for user %s", username));
    }

    private void checkContinuedSession(String username, Integer userId) {
        WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);
        if (calculateSessionService.isValidSession(session)) {
            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
            notificationService.showHourlyWarning(username, userId, finalMinutes);
        }
    }

    public void continueTempStop(String username, Integer userId) {
        // Reset monitoring but keep temporary stop status
        String sessionKey = getSessionKey(username, userId);
        tempStopMonitoringActive.put(sessionKey, false);
        startMonitoring(userSessionService.getCurrentSession(username, userId));
    }

    public void resumeFromTempStop(String username, Integer userId) {
        WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);
        userSessionService.resumeFromTemporaryStop(username, userId);
        stopMonitoring(username, userId);
        startMonitoring(session);
    }

    public void endSession(String username, Integer userId) {
        WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);
        User user = userService.getUserById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
        userSessionService.endDay(username, userId, finalMinutes);
        stopMonitoring(username, userId);
    }

    public void stopMonitoring(String username, Integer userId) {
        backgroundMonitor.stopMonitoring(username, userId);
        String sessionKey = getSessionKey(username, userId);
        initialWarningShown.remove(sessionKey);
        tempStopMonitoringActive.remove(sessionKey);
        LoggerUtil.info(this.getClass(),
                String.format("Stopped monitoring for user %s", username));
    }

    private String getSessionKey(String username, Integer userId) {
        return username + "_" + userId;
    }

    @PreDestroy
    public void cleanup() {
        backgroundMonitor.shutdown();
        initialWarningShown.clear();
        tempStopMonitoringActive.clear();
    }
}