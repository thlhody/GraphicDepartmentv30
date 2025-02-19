package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class SessionMonitorService {
    private final UserSessionCalcService userSessionCalcService;
    private final UserSessionService userSessionService;
    private final SystemNotificationService notificationService;
    private final UserService userService;
    private final TaskScheduler taskScheduler;
    private final PathConfig pathConfig;

    // Track monitored sessions
    private final Map<String, Boolean> notificationShown = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastHourlyWarning = new ConcurrentHashMap<>();
    private final Map<String, Boolean> continuedAfterSchedule = new ConcurrentHashMap<>();
    private ScheduledFuture<?> monitoringTask;
    private volatile boolean isInitialized = false;

    public SessionMonitorService(
            UserSessionCalcService userSessionCalcService,
            @Lazy UserSessionService userSessionService,
            @Lazy SystemNotificationService notificationService,
            UserService userService,
            @Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler,
            PathConfig pathConfig) {
        this.userSessionCalcService = userSessionCalcService;
        this.userSessionService = userSessionService;
        this.notificationService = notificationService;
        this.userService = userService;
        this.taskScheduler = taskScheduler;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        // Schedule initialization with a 6-second delay after system tray is ready
        taskScheduler.schedule(
                this::delayedInitialization,
                Instant.now().plusMillis(6000)
        );
    }

    private void delayedInitialization() {
        try {
            LoggerUtil.info(this.getClass(), "Starting delayed initialization of session monitoring...");
            startScheduledMonitoring();
            isInitialized = true;
            LoggerUtil.info(this.getClass(), "Session monitoring initialized successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in initialization: " + e.getMessage());
        }
    }

    private void startScheduledMonitoring() {
        // Cancel any existing task
        if (monitoringTask != null && !monitoringTask.isCancelled()) {
            monitoringTask.cancel(false);
        }

        // Schedule new monitoring task
        monitoringTask = taskScheduler.scheduleWithFixedDelay(this::checkActiveSessions, Duration.ofMinutes(WorkCode.CHECK_INTERVAL));
        LoggerUtil.info(this.getClass(), "Scheduled monitoring task started");
    }

    private void checkActiveSessions() {
        if (!isInitialized) {
            return;
        }

        try {
            String username = getCurrentActiveUser();
            if (username == null) {
                return;
            }

            User user = userService.getUserByUsername(username).orElseThrow(() -> new RuntimeException("User not found: " + username));
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, user.getUserId());

            // Skip if session is null or not online
            if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                return;
            }

            // Update calculations
            userSessionCalcService.updateSessionCalculations(session);
            userSessionService.saveSession(username, session);

            // Check based on session status
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                checkTempStopDuration(session);
            } else if (continuedAfterSchedule.getOrDefault(username, false)) {
                checkHourlyWarning(session);
            } else {
                checkScheduleCompletion(session, user);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in monitoring: " + e.getMessage());
        }
    }

    private void checkTempStopDuration(WorkUsersSessionsStates session) {
        String username = session.getUsername();
        LocalDateTime tempStopStart = session.getLastTemporaryStopTime();

        if (tempStopStart != null) {
            LocalDateTime now = LocalDateTime.now();

            // Check if total temporary stop minutes exceed 15 hours
            if (session.getTotalTemporaryStopMinutes() != null &&
                    session.getTotalTemporaryStopMinutes() >= WorkCode.MAX_TEMP_STOP_HOURS * WorkCode.HOUR_DURATION) {

                // End session through UserSessionService
                userSessionService.endDay(
                        username,
                        session.getUserId(),
                        session.getTotalWorkedMinutes()
                );
                return;
            }

            long minutesSinceTempStop = ChronoUnit.MINUTES.between(tempStopStart, now);
            // Show warning every hour of temporary stop
            if (minutesSinceTempStop >= WorkCode.HOURLY_INTERVAL) {
                notificationService.showLongTempStopWarning(
                        username,
                        session.getUserId(),
                        tempStopStart
                );
            }
        }
    }

    private void checkScheduleCompletion(WorkUsersSessionsStates session, User user) {
        int scheduleMinutes = WorkCode.calculateFullDayDuration(user.getSchedule());
        int workedMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;

        // Only show notification if not already shown for this session
        if (workedMinutes >= scheduleMinutes && !notificationShown.getOrDefault(session.getUsername(), false)) {
            notificationService.showSessionWarning(
                    session.getUsername(),
                    session.getUserId(),
                    session.getFinalWorkedMinutes()
            );
            notificationShown.put(session.getUsername(), true);
            LoggerUtil.info(this.getClass(), String.format("Schedule completion notification shown for user %s (worked: %d minutes)",
                            session.getUsername(), workedMinutes));
        }
    }

    private void checkHourlyWarning(WorkUsersSessionsStates session) {
        String username = session.getUsername();
        LocalDateTime lastWarning = lastHourlyWarning.get(username);
        LocalDateTime now = LocalDateTime.now();

        // Check if an hour has passed since last warning
        if (lastWarning == null || ChronoUnit.MINUTES.between(lastWarning, now) >= WorkCode.HOURLY_INTERVAL) {
            notificationService.showHourlyWarning(username, session.getUserId(), session.getFinalWorkedMinutes());
            lastHourlyWarning.put(username, now);
        }
    }

    public void activateHourlyMonitoring(String username) {
        continuedAfterSchedule.put(username, true);
        lastHourlyWarning.put(username, LocalDateTime.now());
        LoggerUtil.info(this.getClass(), String.format("Activated hourly monitoring for user %s", username));
    }

    public void resumeFromTempStop(String username, Integer userId) {
        try {
            userSessionService.resumeFromTemporaryStop(username, userId);
            clearMonitoring(username);  // Clear monitoring states for fresh start
            LoggerUtil.info(this.getClass(),
                    String.format("Resumed work for user %s from temporary stop", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error resuming from temp stop for %s: %s", username, e.getMessage()));
        }
    }

    public void continueTempStop(String username, Integer userId) {
        try {
            // Just log that user chose to continue temp stop
            LoggerUtil.info(this.getClass(), String.format("User %s chose to continue temporary stop", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error continuing temp stop for %s: %s", username, e.getMessage()));
        }
    }

    private String getCurrentActiveUser() {
        try {
            Path localSessionPath = pathConfig.getLocalSessionPath("", 0).getParent();
            if (!Files.exists(localSessionPath)) {
                return null;
            }
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
            String[] parts = filename.replace("session_", "").replace(".json", "").split("_");
            return parts.length >= 2 ? parts[0] : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void endSession(String username, Integer userId) {
        try {
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);
            if (session != null) {
                userSessionService.endDay(username, userId, session.getFinalWorkedMinutes());
                clearMonitoring(username);
                LoggerUtil.info(this.getClass(), "Session ended for user: " + username);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error ending session for %s: %s", username, e.getMessage()));
        }
    }

    public void clearMonitoring(String username) {
        notificationShown.remove(username);
        continuedAfterSchedule.remove(username);
        lastHourlyWarning.remove(username);
        LoggerUtil.info(this.getClass(), String.format("Cleared monitoring for user %s", username));
    }

    public void startMonitoring(String username) {
        clearMonitoring(username);
        LoggerUtil.info(this.getClass(), "Started monitoring for user: " + username);
    }

    public void stopMonitoring(String username) {
        clearMonitoring(username);
        LoggerUtil.info(this.getClass(), "Stopped monitoring for user: " + username);
    }
}