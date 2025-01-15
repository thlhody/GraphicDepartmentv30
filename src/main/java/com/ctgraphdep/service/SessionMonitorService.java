package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
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
    private final SessionCalculator sessionCalculator;
    private final UserService userService;
    private final BackgroundMonitorExecutor backgroundMonitor;
    private UserSessionService userSessionService;

    // Track if initial warning has been shown
    private final Map<String, Boolean> initialWarningShown = new ConcurrentHashMap<>();
    private final Map<String, Boolean> tempStopMonitoringActive = new ConcurrentHashMap<>();

    public SessionMonitorService(
            SystemNotificationService notificationService,
            SessionCalculator sessionCalculator,
            UserService userService,
            BackgroundMonitorExecutor backgroundMonitor) {
        this.notificationService = notificationService;
        this.sessionCalculator = sessionCalculator;
        this.userService = userService;
        this.backgroundMonitor = backgroundMonitor;
    }

    @Autowired
    public void setUserSessionService(UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    public void startMonitoring(WorkUsersSessionsStates session) {
        if (!sessionCalculator.isValidSession(session)) {
            return;
        }

        String sessionKey = getSessionKey(session.getUsername(), session.getUserId());
        initialWarningShown.put(sessionKey, false);
        tempStopMonitoringActive.put(sessionKey, false);

        if (session.getSessionStatus().equals(WorkCode.WORK_TEMPORARY_STOP)) {
            startTempStopMonitoring(session);
        } else {
            backgroundMonitor.startSessionMonitoring(session, this::checkSession);
        }

        LoggerUtil.info(this.getClass(),
                String.format("Started monitoring session for user %s", session.getUsername()));
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

            if (sessionCalculator.isStuckTemporaryStop(currentSession)) {
                endSession(session.getUsername(), session.getUserId());
                return;
            }

            if (sessionCalculator.shouldShowTempStopWarning(currentSession)) {
                notificationService.showLongTempStopWarning(
                        session.getUsername(),
                        session.getUserId(),
                        session.getLastTemporaryStopTime()
                );
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking temporary stop: " + e.getMessage());
        }
    }

    private void checkSession(WorkUsersSessionsStates session) {
        try {
            User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (sessionCalculator.shouldEndSession(user, session, LocalDateTime.now())) {
                String sessionKey = getSessionKey(session.getUsername(), session.getUserId());

                if (!initialWarningShown.getOrDefault(sessionKey, false)) {
                    int finalMinutes = sessionCalculator.calculateFinalMinutes(user, session);
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
        if (sessionCalculator.isValidSession(session)) {
            User user = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            int finalMinutes = sessionCalculator.calculateFinalMinutes(user, session);
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
        int finalMinutes = sessionCalculator.calculateFinalMinutes(user, session);
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