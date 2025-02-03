package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.MonitoringState;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
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
    private final PathConfig pathConfig;

    public BackgroundMonitorExecutor(
            DataAccessService dataAccess,
            SessionCalculator sessionCalculator,
            SystemNotificationService notificationService,
            UserService userService, PathConfig pathConfig) {
        this.dataAccess = dataAccess;
        this.sessionCalculator = sessionCalculator;
        this.notificationService = notificationService;
        this.userService = userService;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void initializeExistingSessions() {

        // ADD THIS: Get the logged-in app username
        String loggedInUsername = getCurrentLoggedInUsername(); // You'll need to implement this method
        LoggerUtil.info(this.getClass(), "Logged-in App Username: " + loggedInUsername);

        List<WorkUsersSessionsStates> activeSessions = loadActiveSessions();
        LoggerUtil.info(this.getClass(), "Total active sessions found: " + activeSessions.size());

        for(WorkUsersSessionsStates session : activeSessions) {
            // CHANGE THIS: Compare with logged-in app username instead of PC username
            if (session.getUsername().equals(loggedInUsername)) {
                LoggerUtil.info(this.getClass(),
                        "CRITICAL: Preparing to monitor session for MATCHING user: " + session.getUsername() +
                                " (User ID: " + session.getUserId() + ")");
                startMonitoring(session);
            } else {
                LoggerUtil.info(this.getClass(),
                        "SKIPPING session for user: " + session.getUsername() +
                                " (Does NOT match logged-in username: " + loggedInUsername + ")");
            }
        }
    }

    private String getCurrentLoggedInUsername() {
        try {
            // Use PathConfig to get the local session directory
            Path localSessionPath = pathConfig.getLocalSessionPath("", 0).getParent();

            LoggerUtil.debug(this.getClass(), "Searching for LOCAL session files in: " + localSessionPath);

            // List ALL local session files
            List<Path> localSessionFiles = Files.list(localSessionPath)
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        // Check for session files with .json extension
                        boolean isSessionFile = filename.startsWith("session_") &&
                                filename.endsWith(".json");

                        LoggerUtil.debug(this.getClass(),
                                "Checking local file: " + filename +
                                        ", Is valid session file: " + isSessionFile);

                        return isSessionFile;
                    })
                    .sorted((p1, p2) -> {
                        try {
                            // Sort by most recently modified, most recent first
                            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                        } catch (IOException e) {
                            LoggerUtil.error(this.getClass(),
                                    "Error comparing file modification times: " + e.getMessage());
                            return 0;
                        }
                    })
                    .toList();

            // If local session files exist, process the most recent one
            if (!localSessionFiles.isEmpty()) {
                Path mostRecentLocalSessionFile = localSessionFiles.get(0);

                LoggerUtil.info(this.getClass(),
                        "Most recent local session file found: " + mostRecentLocalSessionFile.getFileName());

                // Extract username and userId from filename
                String filename = mostRecentLocalSessionFile.getFileName().toString();
                // Assuming filename format is session_username_userId.json
                String[] parts = filename.replace("session_", "").replace(".json", "").split("_");

                if (parts.length >= 2) {
                    String username = parts[0];
                    Integer userId = Integer.parseInt(parts[1]);

                    // Read session using DataAccessService's local read method
                    WorkUsersSessionsStates session = dataAccess.readLocalSessionFile(username, userId);

                    // Validate the session and extract username
                    if (session != null && session.getUsername() != null) {
                        LoggerUtil.info(this.getClass(),
                                "Current logged-in username from local session: " + session.getUsername());
                        return session.getUsername();
                    }
                }

                LoggerUtil.warn(this.getClass(),
                        "Could not parse username from local session filename: " + filename);
            } else {
                LoggerUtil.warn(this.getClass(), "No local session files found");
            }
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(),
                    "Error searching for local session files: " + e.getMessage());
        }

        // Fallback if no username can be determined
        return null;
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
            // Extract username and userId from the path
            String filename = sessionPath.getFileName().toString();
            String[] parts = filename.replace("session_", "").replace(".json", "").split("_");

            if (parts.length >= 2) {
                String username = parts[0];
                Integer userId = Integer.parseInt(parts[1]);

                // Use DataAccessService to read the local session file
                return dataAccess.readLocalSessionFile(username, userId);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to load session from " + sessionPath + ": " + e.getMessage());
        }
        return null;
    }

    private List<WorkUsersSessionsStates> loadActiveSessions() {
        try {
            // Use PathConfig to get the LOCAL session directory
            Path localSessionPath = pathConfig.getLocalSessionPath("", 0).getParent();

            if (!Files.exists(localSessionPath)) {
                return Collections.emptyList();
            }

            // Only look for files matching session pattern in LOCAL PATH
            return Files.list(localSessionPath)
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        return filename.startsWith("session_") && filename.endsWith(".json");
                    })
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
            LoggerUtil.info(this.getClass(),
                    "Checking session for user: " + session.getUsername() +
                            ", User ID: " + session.getUserId());

            // Get fresh session state using LOCAL session path
            WorkUsersSessionsStates currentSession = dataAccess.readLocalSessionFile(
                    session.getUsername(),
                    session.getUserId()
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
            LoggerUtil.error(this.getClass(),
                    "Error checking session for user " + session.getUsername() +
                            ": " + e.getMessage());
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
                WorkCode.ONE_MINUTE_DELAY, // 1 minute initial delay
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

        // Cancel any existing hourly monitoring
        stopTask(key);

        // Also stop the regular session monitoring since we're switching to hourly
        stopTask(getKey(username, userId));

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                monitor,
                WorkCode.ONE_MINUTE_DELAY, // 1 minute initial delay
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
        stopTask(baseKey + WorkCode.HOURLY);
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