package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.MonitoringState;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class BackgroundMonitorExecutor {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();
    private final Map<String, MonitoringState> monitoringStates = new ConcurrentHashMap<>();
    private final DataAccessService dataAccess;
    private final SessionCalculator sessionCalculator;
    private final SystemNotificationService notificationService;
    private final UserService userService;

    public BackgroundMonitorExecutor(
            DataAccessService dataAccess,
            SessionCalculator sessionCalculator,
            SystemNotificationService notificationService,
            UserService userService) {
        this.dataAccess = dataAccess;
        this.sessionCalculator = sessionCalculator;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @PostConstruct
    public void initializeExistingSessions() {
        List<WorkUsersSessionsStates> activeSessions = loadActiveSessions();
        for(WorkUsersSessionsStates session : activeSessions) {
            startMonitoring(session);
        }
    }

    private void startMonitoring(WorkUsersSessionsStates session) {
        String key = getKey(session.getUsername(), session.getUserId());

        if (monitoringStates.containsKey(key)) {
            return; // Already monitoring
        }

        MonitoringState state = new MonitoringState(
                session.getDayStartTime(),
                scheduler.scheduleAtFixedRate(
                        () -> checkSession(session),
                        1, // Initial 1 minute delay
                        WorkCode.CHECK_INTERVAL,
                        TimeUnit.MINUTES)
        );

        monitoringStates.put(key, state);
        LoggerUtil.info(this.getClass(),
                "Started monitoring session for user: " + session.getUsername());
    }

    private WorkUsersSessionsStates loadSession(Path sessionPath) {
        try {
            return dataAccess.readFile(sessionPath, new TypeReference<WorkUsersSessionsStates>() {}, false);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to load session from " + sessionPath + ": " + e.getMessage());
            return null;
        }
    }

    private List<WorkUsersSessionsStates> loadActiveSessions() {
        try {
            Path sessionPath = dataAccess.getSessionPath("", 0).getParent();
            if (!Files.exists(sessionPath)) {
                return Collections.emptyList();
            }

            return Files.list(sessionPath)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(this::loadSession)
                    .filter(Objects::nonNull)
                    .filter(session -> WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                            WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Failed to load active sessions: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private void checkSession(WorkUsersSessionsStates session) {
        try {
            // Get fresh session state
            WorkUsersSessionsStates currentSession = loadSession(
                    dataAccess.getSessionPath(session.getUsername(), session.getUserId())
            );

            // Stop monitoring if session ended or invalid
            if (currentSession == null ||
                    WorkCode.WORK_OFFLINE.equals(currentSession.getSessionStatus())) {
                stopMonitoring(session.getUsername(), session.getUserId());
                return;
            }

            User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (sessionCalculator.shouldEndSession(user, currentSession, LocalDateTime.now())) {
                int finalMinutes = sessionCalculator.calculateFinalMinutes(user, currentSession);
                notificationService.showSessionWarning(
                        currentSession.getUsername(),
                        currentSession.getUserId(),
                        finalMinutes
                );
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking session: " + e.getMessage());
        }
    }

    public void startSessionMonitoring(WorkUsersSessionsStates session, Consumer<WorkUsersSessionsStates> monitor) {
        String key = getKey(session.getUsername(), session.getUserId());
        if (tasks.containsKey(key)) {
            // Cancel existing monitoring task
            stopTask(key);
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> monitor.accept(session),
                1, // 1 minute initial delay
                WorkCode.CHECK_INTERVAL,
                TimeUnit.MINUTES
        );
        tasks.put(key, future);

        LoggerUtil.info(this.getClass(),
                String.format("Started session monitoring for user %s with interval %d minutes",
                        session.getUsername(), WorkCode.CHECK_INTERVAL));
    }

    public void startHourlyMonitoring(String username, Integer userId, Runnable monitor) {
        String key = getKey(username, userId) + "_hourly";

        // Cancel any existing hourly monitoring
        stopTask(key);

        // Also stop the regular session monitoring since we're switching to hourly
        stopTask(getKey(username, userId));

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                monitor,
                1, // 1 minute initial delay
                WorkCode.HOURLY_CHECK_INTERVAL,
                TimeUnit.MINUTES
        );
        tasks.put(key, future);

        LoggerUtil.info(this.getClass(),
                String.format("Started hourly monitoring for user %s with interval %d minutes",
                        username, WorkCode.HOURLY_CHECK_INTERVAL));
    }

    public void stopMonitoring(String username, Integer userId) {
        // Stop both regular and hourly monitoring
        String baseKey = getKey(username, userId);
        stopTask(baseKey);
        stopTask(baseKey + "_hourly");
        monitoringStates.remove(baseKey);

        LoggerUtil.info(this.getClass(),
                String.format("Stopped all monitoring for user %s", username));
    }

    private void stopTask(String key) {
        ScheduledFuture<?> task = tasks.remove(key);
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
            LoggerUtil.debug(this.getClass(),
                    String.format("Cancelled task for key: %s", key));
        }
    }

    private String getKey(String username, Integer userId) {
        return username + "_" + userId;
    }

    @PreDestroy
    public void shutdown() {
        tasks.values().forEach(future -> future.cancel(false));
        monitoringStates.clear();
        tasks.clear();
        scheduler.shutdown();
        LoggerUtil.info(this.getClass(), "Background monitor executor shutdown complete");
    }
}