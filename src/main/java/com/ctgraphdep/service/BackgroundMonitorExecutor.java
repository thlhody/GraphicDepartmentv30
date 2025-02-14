package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SessionEndRule;
import com.ctgraphdep.model.MonitoringState;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeCalculationResult;
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
    private final UserSessionService userSessionService;
    private final SessionPersistenceService persistenceService;
    private final CalculateSessionService calculateSessionService;
    private final SystemNotificationService notificationService;
    private final UserService userService;
    private final PathConfig pathConfig;

    public BackgroundMonitorExecutor(
            DataAccessService dataAccess,
            UserSessionService userSessionService,
            SessionPersistenceService persistenceService,
            CalculateSessionService calculateSessionService,
            SystemNotificationService notificationService,
            UserService userService,
            PathConfig pathConfig) {
        this.dataAccess = dataAccess;
        this.userSessionService = userSessionService;
        this.persistenceService = persistenceService;
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
        scheduler.schedule(() -> {
            LoggerUtil.info(this.getClass(), "Starting delayed initialization of BackgroundMonitorExecutor");
            initializeExistingSessions();
        }, 10, TimeUnit.SECONDS);
    }

    public void startSessionMonitoring(WorkUsersSessionsStates session, Consumer<WorkUsersSessionsStates> monitor) {
        String key = getKey(session.getUsername(), session.getUserId());
        stopAllMonitoring(session.getUsername(), session.getUserId());

        // Check for previous day session first
        if (calculateSessionService.isSessionFromPreviousDay(session)) {
            handlePreviousDaySession(session);
            return;
        }

        // Skip regular monitoring for temp stop sessions
        if (session.getSessionStatus().equals(WorkCode.WORK_TEMPORARY_STOP)) {
            LoggerUtil.debug(this.getClass(), "Skipping regular monitoring for temp stop session");
            return;
        }

        // Start regular monitoring
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
                String.format("Started hourly monitoring for user %s with interval %d minutes",
                        username, WorkCode.TEMP_STOP_WARNING_INTERVAL));
    }

    private void handlePreviousDaySession(WorkUsersSessionsStates session) {
        try {
            User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Calculate session metrics using standard workday
            WorkTimeCalculationResult result = calculatePreviousDaySession(session, user.getSchedule());

            // Update session with standard workday values first
            session.setTotalWorkedMinutes(result.getRawMinutes());
            session.setFinalWorkedMinutes(result.getFinalTotalMinutes());
            session.setTotalOvertimeMinutes(result.getOvertimeMinutes());
            session.setLunchBreakDeducted(result.isLunchDeducted());

            // Set end time to proper end of workday
            LocalDateTime properEndTime = session.getDayStartTime()
                    .plusMinutes(result.getRawMinutes());
            session.setDayEndTime(properEndTime);

            // First call endDay to handle worktime updates
            userSessionService.endDay(session.getUsername(), session.getUserId(),
                    result.getFinalTotalMinutes());

            // Then update final session state
            session.setSessionStatus(WorkCode.WORK_OFFLINE);
            session.setWorkdayCompleted(true);
            session.setLastActivity(properEndTime);

            // Persist final session state
            persistenceService.persistSession(session);

            LoggerUtil.info(this.getClass(),
                    String.format("Ended previous day session for user %s with %d minutes",
                            session.getUsername(), result.getFinalTotalMinutes()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error handling previous day session: %s", e.getMessage()));
        }
    }

    private WorkTimeCalculationResult calculatePreviousDaySession(WorkUsersSessionsStates session, Integer schedule) {
        // For standard 8-hour schedule
        if (Objects.equals(schedule, WorkCode.INTERVAL_HOURS_C)) {
            int standardMinutes = WorkCode.calculateFullDayDuration(schedule); // 510 minutes (8.5 hours)

            return new WorkTimeCalculationResult(
                    standardMinutes,       // Raw minutes (510)
                    standardMinutes,       // Processed minutes (510)
                    0,                     // No overtime for standard day
                    true,                  // Lunch break is tracked but already included in standardMinutes
                    standardMinutes        // Final total is 510 (8.5 hours)
            );
        } else {
            // For schedules under 8 hours, no lunch addition
            int scheduledMinutes = schedule * WorkCode.HOUR_DURATION;
            return new WorkTimeCalculationResult(
                    scheduledMinutes,      // Raw equals scheduled
                    scheduledMinutes,      // Processed equals scheduled
                    0,                     // No overtime
                    false,                 // No lunch break for non-standard
                    scheduledMinutes       // Final equals scheduled
            );
        }
    }

    private void initializeExistingSessions() {
        String loggedInUsername = getCurrentLoggedInUsername();
        if (loggedInUsername == null) {
            LoggerUtil.warn(this.getClass(), "No logged-in user found, skipping initialization");
            return;
        }

        List<WorkUsersSessionsStates> activeSessions = loadActiveSessions();
        LoggerUtil.info(this.getClass(),
                String.format("Found %d active sessions for user %s",
                        activeSessions.size(), loggedInUsername));

        for (WorkUsersSessionsStates session : activeSessions) {
            if (session.getUsername().equals(loggedInUsername)) {
                processExistingSession(session);
            }
        }
    }

    private void processExistingSession(WorkUsersSessionsStates session) {
        try {
            User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Calculate latest metrics and store in a final variable
            final WorkUsersSessionsStates finalSession = calculateSessionService.calculateSessionMetrics(
                    session,
                    user.getSchedule()
            );

            // Check for applicable rules
            Optional<SessionEndRule> rule = calculateSessionService.checkSessionEndRules(
                    finalSession,
                    user.getSchedule()
            );

            // Use finalSession in lambda instead of session
            rule.ifPresent(r -> {
                if (!r.requiresHourlyMonitoring()) {
                    handlePreviousDaySession(finalSession);
                } else {
                    startMonitoringBasedOnRule(finalSession, r);
                }
            });

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error processing existing session: %s", e.getMessage()));
        }
    }

    private void startMonitoringBasedOnRule(WorkUsersSessionsStates session, SessionEndRule rule) {
        if (session.getSessionStatus().equals(WorkCode.WORK_TEMPORARY_STOP)) {
            startHourlyMonitoring(
                    session.getUsername(),
                    session.getUserId(),
                    () -> checkTempStopWithRule(session)
            );
        } else {
            startSessionMonitoring(session, s -> checkSessionWithRule(s, rule));
        }
    }

    private void checkSessionWithRule(WorkUsersSessionsStates session, SessionEndRule rule) {
        if (rule.requiresNotification()) {
            User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
            notificationService.showSessionWarning(
                    session.getUsername(),
                    session.getUserId(),
                    finalMinutes
            );
        }
    }

    private void checkTempStopWithRule(WorkUsersSessionsStates session) {
        try {
            // Get user for schedule information
            User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Calculate metrics with proper schedule
            calculateSessionService.calculateSessionMetrics(session, user.getSchedule());

            if (session.getTotalTemporaryStopMinutes() >= (WorkCode.MAX_TEMP_STOP_HOURS * WorkCode.HOUR_DURATION)) {
                endSession(session);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking temp stop rule: %s", e.getMessage()));
        }
    }

    private void endSession(WorkUsersSessionsStates session) {
        User user = userService.getUserById(session.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        int finalMinutes = calculateSessionService.calculateFinalMinutes(user, session);
        userSessionService.endDay(session.getUsername(), session.getUserId(), finalMinutes);
        stopAllMonitoring(session.getUsername(), session.getUserId());
    }

    private String getCurrentLoggedInUsername() {
        try {
            Path localSessionPath = pathConfig.getLocalSessionPath("", 0).getParent();
            return Files.list(localSessionPath)
                    .filter(this::isValidSessionFile)
                    .max(this::compareByLastModified)
                    .map(this::extractUsernameFromSession)
                    .orElse(null);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error getting current user: " + e.getMessage());
            return null;
        }
    }

    private boolean isValidSessionFile(Path path) {
        String filename = path.getFileName().toString();
        return filename.startsWith("session_") && filename.endsWith(".json");
    }

    private int compareByLastModified(Path p1, Path p2) {
        try {
            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
        } catch (IOException e) {
            return 0;
        }
    }

    private String extractUsernameFromSession(Path sessionPath) {
        try {
            String filename = sessionPath.getFileName().toString();
            String[] parts = filename.replace("session_", "")
                    .replace(".json", "").split("_");
            return parts.length >= 2 ? parts[0] : null;
        } catch (Exception e) {
            return null;
        }
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
            LoggerUtil.error(this.getClass(), "Error loading active sessions: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private WorkUsersSessionsStates loadSession(Path sessionPath) {
        try {
            String filename = sessionPath.getFileName().toString();
            String[] parts = filename.replace("session_", "")
                    .replace(".json", "").split("_");
            if (parts.length >= 2) {
                String username = parts[0];
                Integer userId = Integer.parseInt(parts[1]);
                return dataAccess.readLocalSessionFile(username, userId);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading session: " + e.getMessage());
        }
        return null;
    }

    private boolean isActiveSession(WorkUsersSessionsStates session) {
        return session != null && (
                WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())
        );
    }

    private void stopAllMonitoring(String username, Integer userId) {
        String baseKey = getKey(username, userId);
        stopTask(baseKey);
        stopTask(baseKey + WorkCode.HOURLY);
        tasks.remove(baseKey);  // Make sure to remove the tasks
        tasks.remove(baseKey + WorkCode.HOURLY);
        monitoringStates.remove(baseKey);
    }
    private void stopTask(String key) {
        ScheduledFuture<?> task = tasks.remove(key);
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
    }

    public void stopMonitoring(String username, Integer userId) {
        stopAllMonitoring(username, userId);
        LoggerUtil.info(this.getClass(),
                String.format("Stopped monitoring for user %s", username));
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