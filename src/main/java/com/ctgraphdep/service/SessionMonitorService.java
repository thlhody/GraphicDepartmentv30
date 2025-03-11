package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.SaveSessionCommand;
import com.ctgraphdep.session.commands.UpdateSessionCalculationsCommand;
import com.ctgraphdep.session.commands.notification.ShowTestNotificationCommand;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Service responsible for monitoring active user sessions and triggering
 * appropriate notification based on session state.
 * Refactored to use command pattern instead of direct service calls.
 */
@Service
public class SessionMonitorService {
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final SystemNotificationService notificationService;
    private final UserService userService;
    private final TaskScheduler taskScheduler;
    private final PathConfig pathConfig;
    private final SystemNotificationBackupService backupService;
    private final ContinuationTrackingService continuationTrackingService;

    // Track monitored sessions
    private final Map<String, Boolean> notificationShown = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastHourlyWarning = new ConcurrentHashMap<>();
    private final Map<String, Boolean> continuedAfterSchedule = new ConcurrentHashMap<>();
    private final Map<String, LocalDate> lastStartDayCheck = new ConcurrentHashMap<>();
    private ScheduledFuture<?> monitoringTask;
    private volatile boolean isInitialized = false;

    public SessionMonitorService(
            SessionCommandService commandService,
            SessionCommandFactory commandFactory,
            @Lazy SystemNotificationService notificationService,
            UserService userService,
            @Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler,
            PathConfig pathConfig,
            SystemNotificationBackupService backupService,
            ContinuationTrackingService continuationTrackingService) {

        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.notificationService = notificationService;
        this.userService = userService;
        this.taskScheduler = taskScheduler;
        this.pathConfig = pathConfig;
        this.backupService = backupService;
        this.continuationTrackingService = continuationTrackingService;
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

    /**
     * Delayed initialization to ensure system tray is ready
     */
    private void delayedInitialization() {
        try {
            LoggerUtil.info(this.getClass(), "Starting delayed initialization of session monitoring...");
            startScheduledMonitoring();
            isInitialized = true;
            showTestNotification();
            LoggerUtil.info(this.getClass(), "Session monitoring initialized successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in initialization: " + e.getMessage(), e);
            // Even if initialization fails, try to schedule a retry
            try {
                taskScheduler.schedule(
                        this::delayedInitialization,
                        Instant.now().plusSeconds(30)
                );
                LoggerUtil.info(this.getClass(), "Scheduled retry for initialization in 30 seconds");
            } catch (Exception retryEx) {
                LoggerUtil.error(this.getClass(), "Failed to schedule initialization retry: " + retryEx.getMessage());
            }
        }
    }

    /**
     * Displays a test notification to verify system functioning
     */
    private void showTestNotification() {
        try {
            // Find a currently active user, if any
            String username = getCurrentActiveUser();
            if (username != null) {
                User user = userService.getUserByUsername(username).orElse(null);
                if (user != null) {
                    LoggerUtil.info(this.getClass(), "Showing test notification for user: " + username);

                    // Create and execute the test notification command
                    ShowTestNotificationCommand command = commandFactory.createShowTestNotificationCommand(
                            username, user.getUserId());
                    commandService.executeCommand(command);
                }
            } else {
                LoggerUtil.info(this.getClass(), "No active user found for test notification");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error showing test notification: " + e.getMessage());
        }
    }
    /**
     * Starts the scheduled monitoring of user sessions
     */
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

    /**
     * Runs the monitoring task and reschedules for the next check
     */
    private void runAndRescheduleMonitoring() {
        try {
            // Run the actual check
            checkActiveSessions();

            // Always reschedule for the next half-hour mark
            Duration nextDelay = calculateTimeToNextHalfHour();
            monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(nextDelay));

            LoggerUtil.info(this.getClass(), String.format("Next monitoring check scheduled in %d minutes and %d seconds",
                    nextDelay.toMinutes(), nextDelay.toSecondsPart()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in monitoring task: " + e.getMessage(), e);
            // Reschedule anyway to keep the service running even after errors
            Duration retryDelay = Duration.ofMinutes(5); // Shorter retry interval
            monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(retryDelay));
            LoggerUtil.info(this.getClass(), String.format("Rescheduled after error with %d minute retry delay", retryDelay.toMinutes()));
        }
    }

//    /**
//     * Calculates time to the next half-hour mark (either XX:00 or XX:30)
//     */
//    private Duration calculateTimeToNextHalfHour() {
//        LocalDateTime now = LocalDateTime.now();
//        LocalDateTime nextHalfHour;
//
//        // If current minute is less than 30, go to XX:30
//        // Otherwise, go to the next hour (XX+1:00)
//        if (now.getMinute() < 30) {
//            nextHalfHour = now.withMinute(30).withSecond(0).withNano(0);
//        } else {
//            nextHalfHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
//        }
//
//        // Handle special cases for 5:00 AM and 5:00 PM resets
//        LocalDateTime morningReset = now.toLocalDate().atTime(5, 0, 0);
//        LocalDateTime eveningReset = now.toLocalDate().atTime(17, 0, 0);
//
//        // If it's past midnight but before 5:00 AM, check if 5:00 AM is before the next half-hour mark
//        if (now.getHour() < 5 && morningReset.isAfter(now) && morningReset.isBefore(nextHalfHour)) {
//            nextHalfHour = morningReset;
//        }
//
//        // If it's between 5:00 AM and 5:00 PM, check if 5:00 PM is before the next half-hour mark
//        if (now.getHour() >= 5 && now.getHour() < 17 &&
//                eveningReset.isAfter(now) && eveningReset.isBefore(nextHalfHour)) {
//            nextHalfHour = eveningReset;
//        }
//
//        return Duration.between(now, nextHalfHour);
//    }

    /**
     * Calculates time to the next monitoring check (every 10 minutes)
     */
    private Duration calculateTimeToNextHalfHour() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextCheck;

        // Calculate next 10-minute mark (XX:00, XX:10, XX:20, XX:30, XX:40, XX:50)
        int minute = now.getMinute();
        int nextMinute = ((minute / 10) + 1) * 10; // Round up to next 10-minute mark

        if (nextMinute >= 60) {
            // If we need to go to the next hour
            nextCheck = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        } else {
            // Go to the next 10-minute mark in this hour
            nextCheck = now.withMinute(nextMinute).withSecond(0).withNano(0);
        }

        // Handle special cases for 5:00 AM and 5:00 PM resets (keep these as they were)
        LocalDateTime morningReset = now.toLocalDate().atTime(5, 0, 0);
        LocalDateTime eveningReset = now.toLocalDate().atTime(17, 0, 0);

        // If it's past midnight but before 5:00 AM, check if 5:00 AM is before the next check
        if (now.getHour() < 5 && morningReset.isAfter(now) && morningReset.isBefore(nextCheck)) {
            nextCheck = morningReset;
        }

        // If it's between 5:00 AM and 5:00 PM, check if 5:00 PM is before the next check
        if (now.getHour() >= 5 && now.getHour() < 17 &&
                eveningReset.isAfter(now) && eveningReset.isBefore(nextCheck)) {
            nextCheck = eveningReset;
        }

        return Duration.between(now, nextCheck);
    }

    /**
     * Main method for checking active user sessions and triggering notification
     */
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

            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));

            // Get current session using the command pattern
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Skip if session is null or not online
            if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                return;
            }

            // Update calculations using command pattern
            UpdateSessionCalculationsCommand updateCommand = commandFactory.createUpdateSessionCalculationsCommand(session,session.getDayEndTime());
            session = commandService.executeCommand(updateCommand);

            // Save session using command pattern
            SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(session);
            commandService.executeCommand(saveCommand);

            // Check based on session status
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                checkTempStopDuration(session);
            } else if (continuedAfterSchedule.getOrDefault(username, false)) {
                checkHourlyWarning(session);
            } else {
                checkScheduleCompletion(session, user);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in monitoring: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if temporary stop duration exceeds limits and shows warnings
     */
    private void checkTempStopDuration(WorkUsersSessionsStates session) {
        String username = session.getUsername();
        LocalDateTime tempStopStart = session.getLastTemporaryStopTime();

        if (tempStopStart != null) {
            LocalDateTime now = LocalDateTime.now();

            // Check if total temporary stop minutes exceed 15 hours
            if (session.getTotalTemporaryStopMinutes() != null &&
                    session.getTotalTemporaryStopMinutes() >= WorkCode.MAX_TEMP_STOP_HOURS * WorkCode.HOUR_DURATION) {

                // Record this as a temp stop continuation rather than ending the session
                continuationTrackingService.recordTempStopContinuation(username, session.getUserId(), now);
                return;
            }

            long minutesSinceTempStop = ChronoUnit.MINUTES.between(tempStopStart, now);
            // Show warning every hour of temporary stop
            if (minutesSinceTempStop >= WorkCode.HOURLY_INTERVAL) {
                // First register backup action
                backupService.registerTempStopNotification(username, session.getUserId(), tempStopStart);

                // Then show notification
                notificationService.showLongTempStopWarning(username, session.getUserId(), tempStopStart);
            }
        }
    }

    /**
     * Checks if scheduled work time is complete and shows warning
     */
    private void checkScheduleCompletion(WorkUsersSessionsStates session, User user) {
        LocalDate sessionDate = session.getDayStartTime().toLocalDate();
        LocalDate today = LocalDate.now();

        if (!sessionDate.equals(today)) {
            LoggerUtil.info(this.getClass(),
                    String.format("Skipping schedule notice for past session from %s", sessionDate));
            return;
        }
        int scheduleMinutes = WorkCode.calculateFullDayDuration(user.getSchedule());
        LoggerUtil.debug(this.getClass(), String.format("Full day duration for schedule %d: %d minutes",
                user.getSchedule(), scheduleMinutes));
        int workedMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;
        boolean isEightHourSchedule = Objects.equals(user.getSchedule(), WorkCode.INTERVAL_HOURS_C);

        // Add this debug logging
        LoggerUtil.debug(this.getClass(),
                String.format("Schedule check - User: %s, Schedule: %d hours, " +
                                "Is 8-hour schedule: %b, Required minutes: %d, " +
                                "Current worked minutes: %d, Notified already: %b",
                        session.getUsername(), user.getSchedule(),
                        isEightHourSchedule, scheduleMinutes,
                        workedMinutes, notificationShown.getOrDefault(session.getUsername(), false)));


        // Only show notification if not already shown for this session
        if (workedMinutes >= scheduleMinutes && !notificationShown.getOrDefault(session.getUsername(), false)) {
            // First register backup action
            backupService.registerScheduleEndNotification(
                    session.getUsername(),
                    session.getUserId(),
                    session.getFinalWorkedMinutes()
            );

            // Then show notification
            notificationService.showSessionWarning(
                    session.getUsername(),
                    session.getUserId(),
                    session.getFinalWorkedMinutes()
            );

            notificationShown.put(session.getUsername(), true);
            LoggerUtil.info(this.getClass(), String.format("Schedule completion notification shown for user %s (worked: %d minutes)", session.getUsername(), workedMinutes));
        }
    }

    /**
     * Shows hourly warnings for users continuing to work after schedule completion
     */
    public void checkHourlyWarning(WorkUsersSessionsStates session) {
        String username = session.getUsername();
        LocalDateTime lastWarning = lastHourlyWarning.get(username);
        LocalDateTime now = LocalDateTime.now();

        // Check if an hour has passed since last warning
        if (lastWarning == null || ChronoUnit.MINUTES.between(lastWarning, now) >= WorkCode.HOURLY_INTERVAL) {
            // Add detailed logging
            LoggerUtil.info(this.getClass(), String.format(
                    "Preparing hourly warning for %s - Last warning: %s, Minutes since: %d",
                    username,
                    lastWarning != null ? lastWarning.toString() : "never",
                    lastWarning != null ? ChronoUnit.MINUTES.between(lastWarning, now) : 0));

            // First register backup action
            backupService.registerHourlyWarningNotification(
                    username,
                    session.getUserId(),
                    session.getFinalWorkedMinutes()
            );

            // Then show notification
            notificationService.showHourlyWarning(
                    username,
                    session.getUserId(),
                    session.getFinalWorkedMinutes()
            );

            lastHourlyWarning.put(username, now);
        }
    }

    /**
     * Shows start day reminder if user hasn't started their workday
     */
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

            // Get current session using command pattern
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Check for unresolved continuation points from yesterday
            boolean hasUnresolvedContinuations = continuationTrackingService.hasUnresolvedMidnightEnd(username);

            // If there are unresolved continuations, don't show start day reminder
            if (hasUnresolvedContinuations) {
                LoggerUtil.info(this.getClass(),
                        String.format("User %s has unresolved continuation points - skipping start day reminder", username));
                return;
            }

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
            LoggerUtil.error(this.getClass(), "Error checking start day reminder: " + e.getMessage(), e);
        }
    }

    /**
     * Activates hourly monitoring when user chooses to continue working after schedule end
     */
    public void activateHourlyMonitoring(String username) {
        continuedAfterSchedule.put(username, true);
        lastHourlyWarning.put(username, LocalDateTime.now());
        // Cancel any backup tasks since user responded
        backupService.cancelBackupTask(username);
        LoggerUtil.info(this.getClass(), String.format("Activated hourly monitoring for user %s", username));
    }


    /**
     * Gets the currently active user by scanning session files
     */
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
            LoggerUtil.error(this.getClass(), "Error getting current user: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if a file is a valid session file
     */
    private boolean isValidSessionFile(Path path) {
        String filename = path.getFileName().toString();
        return filename.startsWith("session_") && filename.endsWith(".json");
    }

    /**
     * Compares two paths by last modified time
     */
    private int compareByLastModified(Path p1, Path p2) {
        try {
            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Extracts username from the session file path
     */
    private String extractUsernameFromSession(Path sessionPath) {
        try {
            String filename = sessionPath.getFileName().toString();
            String[] parts = filename.replace("session_", "").replace(".json", "").split("_");
            return parts.length >= 2 ? parts[0] : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Clears monitoring state for a user
     */
    public void clearMonitoring(String username) {
        notificationShown.remove(username);
        continuedAfterSchedule.remove(username);
        lastHourlyWarning.remove(username);
        lastStartDayCheck.remove(username);
        LoggerUtil.info(this.getClass(), String.format("Cleared monitoring for user %s", username));
    }

    /**
     * Starts monitoring for a user session
     */
    public void startMonitoring(String username) {
        clearMonitoring(username);
        // Cancel any backup tasks since we're starting fresh
        backupService.cancelBackupTask(username);
        LoggerUtil.info(this.getClass(), "Started monitoring for user: " + username);
    }

    /**
     * Stops monitoring for a user session
     */
    public void stopMonitoring(String username) {
        clearMonitoring(username);
        // Cancel any backup tasks since we're stopping monitoring
        backupService.cancelBackupTask(username);
        LoggerUtil.info(this.getClass(), "Stopped monitoring for user: " + username);
    }

    /* Helper methods */

    /**
     * Checks if current time is within working hours
     */
    private boolean isWorkingHours() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        return hour >= WorkCode.WORK_START_HOUR && hour < WorkCode.WORK_END_HOUR;
    }

    /**
     * Checks if today is a weekday
     */
    private boolean isWeekday() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /**
     * Checks if session has activity today
     */
    private boolean hasActiveSessionToday(WorkUsersSessionsStates session) {
        if (session == null || session.getDayStartTime() == null) {
            return false;
        }

        LocalDate sessionDate = session.getDayStartTime().toLocalDate();
        LocalDate today = LocalDate.now();
        return sessionDate.equals(today);
    }
}