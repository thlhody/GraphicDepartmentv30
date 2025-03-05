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
import java.time.*;
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
    private final Map<String, LocalDate> lastStartDayCheck = new ConcurrentHashMap<>();
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
            showTestNotification();
            LoggerUtil.info(this.getClass(), "Session monitoring initialized successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in initialization: " + e.getMessage());
        }
    }

    private void showTestNotification() {
        try {
            // Find a currently active user, if any
            String username = getCurrentActiveUser();
            if (username != null) {
                User user = userService.getUserByUsername(username).orElse(null);
                if (user != null) {
                    LoggerUtil.info(this.getClass(), "Showing test notification for user: " + username);

                    // Use a special test method to avoid unwanted side effects
                    notificationService.showTestNotificationDialog();
                }
            } else {
                LoggerUtil.info(this.getClass(), "No active user found for test notification");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error showing test notification: " + e.getMessage());
        }
    }

    private void startScheduledMonitoring() {
        // Cancel any existing task
        if (monitoringTask != null && !monitoringTask.isCancelled()) {
            monitoringTask.cancel(false);
        }

        // Calculate initial delay to align with the next half-hour mark
        Duration initialDelay = calculateTimeToNextHalfHour();

        // Schedule the first check
        monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(initialDelay));

        LoggerUtil.info(this.getClass(), String.format("Scheduled monitoring task to start in %d minutes and %d seconds",
                        initialDelay.toMinutes(), initialDelay.toSecondsPart()));
    }

    private void runAndRescheduleMonitoring() {
        try {
            // Run the actual check
            checkActiveSessions();

            // Always reschedule for the next half-hour mark
            Duration nextDelay = calculateTimeToNextHalfHour();
            monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(nextDelay)
            );

            LoggerUtil.info(this.getClass(), String.format("Next monitoring check scheduled in %d minutes and %d seconds",
                            nextDelay.toMinutes(), nextDelay.toSecondsPart()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in monitoring task: " + e.getMessage());
            // Reschedule anyway to keep the service running even after errors
            Duration retryDelay = Duration.ofMinutes(5); // Shorter retry interval
            monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(retryDelay)
            );
            LoggerUtil.info(this.getClass(), String.format("Rescheduled after error with %d minute retry delay", retryDelay.toMinutes()));
        }
    }

    /**
     * Calculates time to the next half-hour mark (either XX:00 or XX:30)
     */
    private Duration calculateTimeToNextHalfHour() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextHalfHour;

        // If current minute is less than 30, go to XX:30
        // Otherwise, go to the next hour (XX+1:00)
        if (now.getMinute() < 30) {
            nextHalfHour = now.withMinute(30).withSecond(0).withNano(0);
        } else {
            nextHalfHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        }

        // Handle special cases for 5:00 AM and 5:00 PM resets
        LocalDateTime morningReset = now.toLocalDate().atTime(5, 0, 0);
        LocalDateTime eveningReset = now.toLocalDate().atTime(17, 0, 0);

        // If it's past midnight but before 5:00 AM, check if 5:00 AM is before the next half-hour mark
        if (now.getHour() < 5 && morningReset.isAfter(now) && morningReset.isBefore(nextHalfHour)) {
            nextHalfHour = morningReset;
        }

        // If it's between 5:00 AM and 5:00 PM, check if 5:00 PM is before the next half-hour mark
        if (now.getHour() >= 5 && now.getHour() < 17 &&
                eveningReset.isAfter(now) && eveningReset.isBefore(nextHalfHour)) {
            nextHalfHour = eveningReset;
        }

        return Duration.between(now, nextHalfHour);
    }

    private void checkActiveSessions() {
        if (!isInitialized) {
            return;
        }

        try {
            checkStartDayReminder();
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

    public void checkStartDayReminder() {
        if (!isInitialized) {
            return;
        }

        try {
            // Only check during working hours on weekdays
            if (!isWeekday() || !isWorkingHours()) {
                return;
            }

            // Get current user
            String username = getCurrentActiveUser();
            if (username == null) {
                return;
            }

            // Check if we already showed notification today
            LocalDate today = LocalDate.now();
            if (lastStartDayCheck.containsKey(username) &&
                    lastStartDayCheck.get(username).equals(today)) {
                return;
            }

            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, user.getUserId());

            // Only show if status is Offline and no active session for today
            if (session != null &&
                    WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()) &&
                    !hasActiveSessionToday(session)) {

                // Show notification
                notificationService.showStartDayReminder(username, user.getUserId());

                // Update last check date
                lastStartDayCheck.put(username, today);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking start day reminder: " + e.getMessage());
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
        lastStartDayCheck.remove(username);  // Add this line
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


    // Add helper methods
    private boolean isWorkingHours() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        return hour >= WorkCode.WORK_START_HOUR && hour < WorkCode.WORK_END_HOUR;
    }

    private boolean isWeekday() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private boolean hasActiveSessionToday(WorkUsersSessionsStates session) {
        if (session == null || session.getDayStartTime() == null) {
            return false;
        }

        LocalDate sessionDate = session.getDayStartTime().toLocalDate();
        LocalDate today = LocalDate.now();
        return sessionDate.equals(today);
    }
}