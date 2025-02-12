package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.MonitoringState;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

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
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> tasks;
    private final Map<String, MonitoringState> monitoringStates;
    private final DataAccessService dataAccess;
    private final UserSessionService sessionService;
    private final SessionPersistenceService persistenceService;
    private final SessionRecoveryService sessionRecoveryService;
    private final CalculateSessionService calculateSessionService;
    private final SystemNotificationService notificationService;
    private final UserService userService;
    private final PathConfig pathConfig;

    public BackgroundMonitorExecutor(
            DataAccessService dataAccess,
            UserSessionService sessionService, SessionPersistenceService persistenceService,
            SessionRecoveryService sessionRecoveryService,
            CalculateSessionService calculateSessionService,
            SystemNotificationService notificationService,
            UserService userService,
            PathConfig pathConfig) {
        this.dataAccess = dataAccess;
        this.sessionService = sessionService;
        this.persistenceService = persistenceService;
        this.sessionRecoveryService = sessionRecoveryService;
        this.calculateSessionService = calculateSessionService;
        this.notificationService = notificationService;
        this.userService = userService;
        this.pathConfig = pathConfig;
        this.scheduler = Executors.newScheduledThreadPool(5);
        this.tasks = new ConcurrentHashMap<>();
        this.monitoringStates = new ConcurrentHashMap<>();
        LoggerUtil.initialize(this.getClass(), null);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Schedule initialization with 10-second delay
        scheduler.schedule(() -> {
            LoggerUtil.info(this.getClass(), "Starting delayed initialization of BackgroundMonitorExecutor");
            initializeExistingSessions();
        }, 10, TimeUnit.SECONDS);
    }

    private void initializeExistingSessions() {
        String loggedInUsername = getCurrentLoggedInUsername();
        LoggerUtil.info(this.getClass(), "Logged-in App Username: " + loggedInUsername);

        if (loggedInUsername == null) {
            LoggerUtil.warn(this.getClass(), "No logged-in user found, skipping session initialization");
            return;
        }

        List<WorkUsersSessionsStates> activeSessions = loadActiveSessions();
        LoggerUtil.info(this.getClass(), "Total active sessions found: " + activeSessions.size());

        for (WorkUsersSessionsStates session : activeSessions) {
            if (session.getUsername().equals(loggedInUsername)) {
                initializeUserSession(session);
            }
        }
    }

    private void initializeUserSession(WorkUsersSessionsStates session) {
        LoggerUtil.info(this.getClass(),
                "CRITICAL: Preparing to recover session for MATCHING user: " +
                        session.getUsername() + " (User ID: " + session.getUserId() + ")");

        // Check if session is from previous day
        if (calculateSessionService.isSessionFromPreviousDay(session)) {
            LoggerUtil.info(this.getClass(),
                    String.format("Found session from previous day for user %s, ending automatically",
                            session.getUsername()));

            try {
                // Get user for schedule information
                User user = userService.getUserById(session.getUserId())
                        .orElseThrow(() -> new RuntimeException("User not found"));

                // Calculate final minutes based on the previous day's work
                int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);

                // End the session with calculated minutes
                session.setSessionStatus(WorkCode.WORK_OFFLINE);
                session.setDayEndTime(session.getLastActivity());  // Use last activity as end time
                session.setFinalWorkedMinutes(finalMinutes);
                session.setWorkdayCompleted(true);

                // First persist the updated session
                persistenceService.persistSession(session);

                // Then end the day through user session service
                sessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);

                LoggerUtil.info(this.getClass(),
                        String.format("Successfully ended previous day session for user %s with %d minutes",
                                session.getUsername(), finalMinutes));

                // Don't start monitoring for old sessions
                return;

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        String.format("Error ending previous day session for user %s: %s",
                                session.getUsername(), e.getMessage()));
            }
        }

        // Only recover and monitor current day sessions
        sessionRecoveryService.recoverSession(session.getUsername(), session.getUserId());
        WorkUsersSessionsStates recoveredSession =
                sessionService.getCurrentSession(session.getUsername(), session.getUserId());

        if (calculateSessionService.isValidSession(recoveredSession)) {
            startMonitoring(recoveredSession);
        }
    }

    private String getCurrentLoggedInUsername() {
        try {
            Path localSessionPath = pathConfig.getLocalSessionPath("", 0).getParent();
            LoggerUtil.debug(this.getClass(), "Searching for LOCAL session files in: " + localSessionPath);

            return Files.list(localSessionPath)
                    .filter(this::isValidSessionFile)
                    .max(this::compareByLastModified)
                    .map(this::extractUsernameFromSession)
                    .orElse(null);

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error searching for local session files: " + e.getMessage());
            return null;
        }
    }

    private boolean isValidSessionFile(Path path) {
        String filename = path.getFileName().toString();
        boolean isSessionFile = filename.startsWith("session_") && filename.endsWith(".json");
        LoggerUtil.debug(this.getClass(),
                "Checking local file: " + filename + ", Is valid session file: " + isSessionFile);
        return isSessionFile;
    }

    private int compareByLastModified(Path p1, Path p2) {
        try {
            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error comparing file modification times: " + e.getMessage());
            return 0;
        }
    }

    private String extractUsernameFromSession(Path sessionPath) {
        try {
            WorkUsersSessionsStates session = loadSession(sessionPath);
            if (session != null && session.getUsername() != null) {
                LoggerUtil.info(this.getClass(),
                        "Current logged-in username from local session: " + session.getUsername());
                return session.getUsername();
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error extracting username from session: " + e.getMessage());
        }
        return null;
    }

    private List<WorkUsersSessionsStates> loadActiveSessions() {
        try {
            Path localSessionPath = pathConfig.getLocalSessionPath("", 0).getParent();
            if (!Files.exists(localSessionPath)) {
                return Collections.emptyList();
            }

            return Files.list(localSessionPath)
                    .filter(this::isValidSessionFile)
                    .map(this::loadSession)
                    .filter(Objects::nonNull)
                    .filter(this::isActiveSession)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Failed to load active sessions: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isActiveSession(WorkUsersSessionsStates session) {
        return WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());
    }

    private WorkUsersSessionsStates loadSession(Path sessionPath) {
        try {
            String filename = sessionPath.getFileName().toString();
            String[] parts = filename.replace("session_", "").replace(".json", "").split("_");

            if (parts.length >= 2) {
                String username = parts[0];
                Integer userId = Integer.parseInt(parts[1]);
                return dataAccess.readLocalSessionFile(username, userId);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Failed to load session from " + sessionPath + ": " + e.getMessage());
        }
        return null;
    }

    public void startMonitoring(WorkUsersSessionsStates session) {
        String key = getKey(session.getUsername(), session.getUserId());

        if (monitoringStates.containsKey(key)) {
            return; // Already monitoring
        }

        MonitoringState state = new MonitoringState(
                session.getDayStartTime(),
                scheduler.scheduleAtFixedRate(
                        () -> checkSession(session),
                        1,
                        WorkCode.CHECK_INTERVAL,
                        TimeUnit.MINUTES)
        );

        monitoringStates.put(key, state);
        LoggerUtil.info(this.getClass(),
                "Started monitoring session for user: " + session.getUsername());
    }

    private void checkSession(WorkUsersSessionsStates session) {
        try {
            LoggerUtil.info(this.getClass(),
                    "Checking session for user: " + session.getUsername() +
                            ", User ID: " + session.getUserId());

            WorkUsersSessionsStates currentSession = getCurrentSession(session);
            if (!isValidCurrentSession(currentSession)) {
                stopMonitoring(session.getUsername(), session.getUserId());
                return;
            }

            processSessionCheck(currentSession);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking session for user " + session.getUsername() +
                            ": " + e.getMessage());
        }
    }

    private WorkUsersSessionsStates getCurrentSession(WorkUsersSessionsStates session) {
        return dataAccess.readLocalSessionFile(session.getUsername(), session.getUserId());
    }

    private boolean isValidCurrentSession(WorkUsersSessionsStates session) {
        return session != null && !WorkCode.WORK_OFFLINE.equals(session.getSessionStatus());
    }

    private void processSessionCheck(WorkUsersSessionsStates session) {
        User user = userService.getUserById(session.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (calculateSessionService.shouldEndSession(session, LocalDateTime.now())) {
            int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
            notificationService.showSessionWarning(
                    session.getUsername(),
                    session.getUserId(),
                    finalMinutes
            );
        }
    }

    public void startSessionMonitoring(WorkUsersSessionsStates session, Consumer<WorkUsersSessionsStates> monitor) {
        String key = getKey(session.getUsername(), session.getUserId());
        stopMonitoring(session.getUsername(), session.getUserId());

        if (session.getSessionStatus().equals(WorkCode.WORK_TEMPORARY_STOP)) {
            LoggerUtil.debug(this.getClass(), "Skipping regular monitoring for temp stop session");
            return;
        }

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> monitor.accept(session),
                WorkCode.ONE_MINUTE_DELAY,
                WorkCode.CHECK_INTERVAL,
                TimeUnit.MINUTES
        );
        tasks.put(key, future);

        LoggerUtil.info(this.getClass(),
                String.format("Started session monitoring for user %s with interval %d minutes",
                        session.getUsername(), WorkCode.CHECK_INTERVAL));
    }

    public void startHourlyMonitoring(String username, Integer userId, Runnable monitor) {
        String key = getKey(username, userId) + WorkCode.HOURLY;
        stopAllMonitoring(username, userId);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                monitor,
                WorkCode.ONE_MINUTE_DELAY,
                WorkCode.TEMP_STOP_WARNING_INTERVAL,
                TimeUnit.MINUTES
        );
        tasks.put(key, future);

        LoggerUtil.info(this.getClass(),
                String.format("Started temp stop monitoring for user %s with interval %d minutes",
                        username, WorkCode.TEMP_STOP_WARNING_INTERVAL));
    }

    private void stopAllMonitoring(String username, Integer userId) {
        String baseKey = getKey(username, userId);
        stopTask(baseKey);
        stopTask(baseKey + WorkCode.HOURLY);
    }

    public void stopMonitoring(String username, Integer userId) {
        stopAllMonitoring(username, userId);
        monitoringStates.remove(getKey(username, userId));
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