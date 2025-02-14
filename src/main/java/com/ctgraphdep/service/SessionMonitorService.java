package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SessionEndRule;
import com.ctgraphdep.model.MonitoringState;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class SessionMonitorService {
    private final SystemNotificationService notificationService;
    private final CalculateSessionService calculateSessionService;
    private final UserService userService;
    private final BackgroundMonitorExecutor backgroundMonitor;
    private UserSessionService userSessionService;

    // Monitoring state tracking
    private final Map<String, MonitoringState> monitoringStates = new ConcurrentHashMap<>();

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

    /**
     * Start monitoring a session based on its current state
     */
    public void startMonitoring(WorkUsersSessionsStates session) {
        if (!calculateSessionService.isValidSession(session)) {
            LoggerUtil.warn(this.getClass(),
                    String.format("Invalid session for user %s, monitoring not started",
                            session != null ? session.getUsername() : "unknown"));
            return;
        }

        String sessionKey = getSessionKey(session.getUsername(), session.getUserId());

        // Stop any existing monitoring
        stopMonitoring(session.getUsername(), session.getUserId());

        // Create new monitoring state
        monitoringStates.put(sessionKey, new MonitoringState(
                LocalDateTime.now(),
                session.getSessionStatus()
        ));

        LoggerUtil.debug(this.getClass(),
                String.format("Starting monitoring for session with status: %s",
                        session.getSessionStatus()));

        if (session.getSessionStatus().equals(WorkCode.WORK_TEMPORARY_STOP)) {
            startTempStopMonitoring(session);
        } else {
            startRegularMonitoring(session);
        }
    }

    /**
     * Main session check logic
     */
    private void checkSession(WorkUsersSessionsStates session) {
        try {
            // Get user for schedule information
            final User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Get fresh session state
            WorkUsersSessionsStates sessionState = userSessionService.getCurrentSession(
                    session.getUsername(),
                    session.getUserId()
            );

            // Skip if status changed to temp stop
            if (WorkCode.WORK_TEMPORARY_STOP.equals(sessionState.getSessionStatus())) {
                LoggerUtil.debug(this.getClass(), "Skipping regular check for temp stop session");
                return;
            }

            // Calculate latest metrics
            final WorkUsersSessionsStates finalSession = calculateSessionService.calculateSessionMetrics(
                    sessionState,
                    user.getSchedule()
            );

            // Check for applicable rules
            Optional<SessionEndRule> rule = calculateSessionService.checkSessionEndRules(
                    finalSession,
                    user.getSchedule()
            );

            rule.ifPresent(r -> handleSessionRule(finalSession, user, r));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking session for user %s: %s",
                            session.getUsername(), e.getMessage()));
        }
    }

    /**
     * Handle applicable session rules
     */
    private void handleSessionRule(WorkUsersSessionsStates session, User user, SessionEndRule rule) {
        String sessionKey = getSessionKey(session.getUsername(), session.getUserId());
        MonitoringState state = monitoringStates.get(sessionKey);

        // Skip if we've already handled this rule
        if (state != null && state.getCurrentRule() == rule) {
            return;
        }

        // Update monitoring state
        if (state != null) {
            state.setCurrentRule(rule);
        }

        if (rule.requiresNotification()) {
            int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);

            // Handle different rules with proper notification timing
            switch (rule) {
                case SCHEDULE_END_REACHED:
                    if (state != null && !state.isScheduleEndNotificationShown()) {
                        showNotification(session, finalMinutes, rule);
                        state.setScheduleEndNotificationShown(true);
                    }
                    break;

                case OVERTIME_REACHED:
                    LocalDateTime now = LocalDateTime.now();
                    if (state != null && (state.getLastOvertimeNotification() == null ||
                            ChronoUnit.HOURS.between(state.getLastOvertimeNotification(), now) >= 1)) {
                        showNotification(session, finalMinutes, rule);
                        state.setLastOvertimeNotification(now);
                    }
                    break;

                case LONG_TEMP_STOP:
                    LocalDateTime currentTime = LocalDateTime.now();
                    if (state != null && (state.getLastTempStopNotification() == null ||
                            ChronoUnit.HOURS.between(state.getLastTempStopNotification(), currentTime) >= 1)) {
                        showNotification(session, finalMinutes, rule);
                        state.setLastTempStopNotification(currentTime);
                    }
                    break;
            }
        }

        if (!rule.requiresHourlyMonitoring()) {
            endSession(session.getUsername(), session.getUserId());
        } else if (state != null && !state.isHourlyMonitoring()) {
            switchToHourlyMonitoring(session.getUsername(), session.getUserId());
        }
    }

    /**
     * Start temporary stop monitoring
     */
    private void startTempStopMonitoring(WorkUsersSessionsStates session) {
        backgroundMonitor.startHourlyMonitoring(
                session.getUsername(),
                session.getUserId(),
                () -> checkTempStop(session)
        );

        LoggerUtil.info(this.getClass(),
                String.format("Started temporary stop monitoring for user %s",
                        session.getUsername()));
    }

    /**
     * Start regular session monitoring
     */
    private void startRegularMonitoring(WorkUsersSessionsStates session) {
        backgroundMonitor.startSessionMonitoring(session, this::checkSession);

        LoggerUtil.info(this.getClass(),
                String.format("Started regular monitoring for user %s",
                        session.getUsername()));
    }

    /**
     * Check temporary stop state
     */
    private void checkTempStop(WorkUsersSessionsStates session) {
        try {
            final User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            WorkUsersSessionsStates sessionState = userSessionService.getCurrentSession(
                    session.getUsername(),
                    session.getUserId()
            );

            final WorkUsersSessionsStates finalSession = calculateSessionService.calculateSessionMetrics(
                    sessionState,
                    user.getSchedule()
            );

            Optional<SessionEndRule> rule = calculateSessionService.checkSessionEndRules(
                    finalSession,
                    user.getSchedule()
            );

            rule.ifPresent(r -> handleTempStopRule(finalSession, user, r));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking temporary stop: " + e.getMessage());
        }
    }

    /**
     * Handle temporary stop specific rules
     */
    private void handleTempStopRule(WorkUsersSessionsStates session, User user, SessionEndRule rule) {
        String sessionKey = getSessionKey(session.getUsername(), session.getUserId());
        MonitoringState state = monitoringStates.get(sessionKey);

        if (rule == SessionEndRule.MAX_TEMP_STOP_REACHED) {
            endSession(session.getUsername(), session.getUserId());
        } else if (rule == SessionEndRule.LONG_TEMP_STOP) {
            LocalDateTime now = LocalDateTime.now();
            if (state != null && (state.getLastTempStopNotification() == null ||
                    ChronoUnit.HOURS.between(state.getLastTempStopNotification(), now) >= 1)) {
                notificationService.showLongTempStopWarning(
                        session.getUsername(),
                        session.getUserId(),
                        session.getLastTemporaryStopTime()
                );
                state.setLastTempStopNotification(now);
            }
        }
    }

    public void switchToHourlyMonitoring(String username, Integer userId) {
        String sessionKey = getSessionKey(username, userId);
        MonitoringState state = monitoringStates.get(sessionKey);

        if (state != null) {
            state.setHourlyMonitoring(true);
        }

        backgroundMonitor.startHourlyMonitoring(
                username,
                userId,
                () -> checkHourlySession(username, userId)
        );
    }

    /**
     * Mark session for continued work (after schedule end)
     */

    public void markSessionContinued(String username, Integer userId) {
        try {
            // Clear previous monitoring and notifications
            stopMonitoring(username, userId);
            notificationService.clearNotificationHistory(username);

            // Start new hourly monitoring
            startHourlyMonitoring(username, userId);

            LoggerUtil.info(this.getClass(),
                    String.format("Started hourly monitoring for user %s", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error starting hourly monitoring for user %s: %s",
                            username, e.getMessage()));
        }
    }

    private void startHourlyMonitoring(String username, Integer userId) {
        String sessionKey = getSessionKey(username, userId);

        // Get current session
        WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(username, userId);
        if (currentSession == null) {
            return;
        }

        // Update monitoring state
        MonitoringState state = monitoringStates.get(sessionKey);
        if (state == null) {
            state = new MonitoringState(LocalDateTime.now(), WorkCode.WORK_ONLINE);
            monitoringStates.put(sessionKey, state);
        }
        state.setHourlyMonitoring(true);

        // Start hourly monitoring
        backgroundMonitor.startHourlyMonitoring(
                username,
                userId,
                () -> checkHourlySession(username, userId)
        );

        LoggerUtil.info(this.getClass(),
                String.format("Started hourly monitoring for user %s", username));
    }

    /**
     * Continue temporary stop monitoring
     */
    public void continueTempStop(String username, Integer userId) {
        String sessionKey = getSessionKey(username, userId);
        try {
            // Get fresh session state
            WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(
                    username,
                    userId
            );

            // Update state
            MonitoringState state = monitoringStates.get(sessionKey);
            if (state == null) {
                state = new MonitoringState(LocalDateTime.now(), WorkCode.WORK_TEMPORARY_STOP);
                monitoringStates.put(sessionKey, state);
            }

            // Start temp stop monitoring
            backgroundMonitor.startHourlyMonitoring(
                    username,
                    userId,
                    () -> checkTempStop(currentSession)
            );

            LoggerUtil.info(this.getClass(),
                    String.format("Continued temporary stop monitoring for user %s", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error continuing temporary stop for user %s: %s",
                            username, e.getMessage()));
        }
    }

    /**
     * Resume work from temporary stop
     */
    public void resumeFromTempStop(String username, Integer userId) {
        String sessionKey = getSessionKey(username, userId);
        try {
            // Get fresh session state
            WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(
                    username,
                    userId
            );

            // Resume through UserSessionService
            userSessionService.resumeFromTemporaryStop(username, userId);

            // Stop current monitoring
            stopMonitoring(username, userId);

            // Update state
            MonitoringState state = monitoringStates.get(sessionKey);
            if (state == null) {
                state = new MonitoringState(LocalDateTime.now(), WorkCode.WORK_ONLINE);
                monitoringStates.put(sessionKey, state);
            }
            state.setHourlyMonitoring(false);

            // Start regular monitoring
            startMonitoring(currentSession);

            LoggerUtil.info(this.getClass(),
                    String.format("Resumed from temporary stop for user %s", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error resuming from temporary stop for user %s: %s",
                            username, e.getMessage()));
        }
    }

    /**
     * Check hourly session state
     */
    private void checkHourlySession(String username, Integer userId) {
        try {
            // Get current session
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);

            if (calculateSessionService.isValidSession(session)) {
                final User user = userService.getUserById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                final WorkUsersSessionsStates finalSession = calculateSessionService.calculateSessionMetrics(
                        session,
                        user.getSchedule()
                );

                int finalMinutes = calculateSessionService.calculateFinalMinutes(user, finalSession);
                notificationService.showHourlyWarning(username, userId, finalMinutes);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking hourly session for user %s: %s",
                            username, e.getMessage()));
        }
    }

    private void showNotification(
            WorkUsersSessionsStates session,
            int finalMinutes,
            SessionEndRule rule) {
        if (rule == SessionEndRule.SCHEDULE_END_REACHED) {
            notificationService.showSessionWarning(
                    session.getUsername(),
                    session.getUserId(),
                    finalMinutes
            );
        } else if (rule == SessionEndRule.OVERTIME_REACHED) {
            notificationService.showHourlyWarning(
                    session.getUsername(),
                    session.getUserId(),
                    finalMinutes
            );
        }
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
        monitoringStates.remove(getSessionKey(username, userId));
        LoggerUtil.info(this.getClass(),
                String.format("Stopped monitoring for user %s", username));
    }

    // Helper methods
    private String getSessionKey(String username, Integer userId) {
        return username + "_" + userId;
    }

    @PreDestroy
    public void cleanup() {
        backgroundMonitor.shutdown();
        monitoringStates.clear();
    }


}