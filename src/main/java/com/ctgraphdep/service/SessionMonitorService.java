package com.ctgraphdep.service;

import com.ctgraphdep.calculations.CalculationCommandFactory;
import com.ctgraphdep.calculations.CalculationCommandService;
import com.ctgraphdep.calculations.queries.CalculateMinutesBetweenQuery;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.SaveSessionCommand;
import com.ctgraphdep.session.commands.UpdateSessionCalculationsCommand;
import com.ctgraphdep.session.commands.notification.ShowTestNotificationCommand;
import com.ctgraphdep.session.query.*;
import com.ctgraphdep.session.util.SessionValidator;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationFactory;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.IsWorkingHoursCommand;
import com.ctgraphdep.validation.commands.IsWeekdayCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Stream;

/**
 * Service responsible for monitoring active user sessions and triggering
 * appropriate notification based on session state.
 * Refactored to use command pattern instead of direct service calls.
 */
@Service
public class SessionMonitorService {
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final UserService userService;
    private final TaskScheduler taskScheduler;
    private final TimeValidationService validationService;
    private final TimeValidationFactory validationFactory;
    private final CalculationCommandFactory calculationFactory;
    private final CalculationCommandService calculationService;
    private final NotificationService notificationService;
    private final DataAccessService dataAccessService;

    @Autowired
    private SchedulerHealthMonitor healthMonitor;

    @Value("${app.session.monitoring.interval:30}")
    private int monitoringInterval;

    // Track monitored sessions
    private final Map<String, Boolean> notificationShown = new ConcurrentHashMap<>();
    public final Map<String, LocalDateTime> lastHourlyWarning = new ConcurrentHashMap<>();
    public final Map<String, Boolean> continuedAfterSchedule = new ConcurrentHashMap<>();
    private final Map<String, LocalDate> lastStartDayCheck = new ConcurrentHashMap<>();
    private ScheduledFuture<?> monitoringTask;
    private volatile boolean isInitialized = false;

    public SessionMonitorService(
            SessionCommandService commandService, SessionCommandFactory commandFactory,
            UserService userService, @Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler,
            TimeValidationService validationService, TimeValidationFactory validationFactory,
            CalculationCommandFactory calculationFactory, CalculationCommandService calculationService,
            NotificationService notificationService, DataAccessService dataAccessService) {

        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.userService = userService;
        this.taskScheduler = taskScheduler;
        this.validationService = validationService;
        this.validationFactory = validationFactory;
        this.calculationFactory = calculationFactory;
        this.calculationService = calculationService;
        this.notificationService = notificationService;
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        // Schedule initialization with a 6-second delay after system tray is ready
        taskScheduler.schedule(this::delayedInitialization, Instant.now().plusMillis(6000));
    }

    @PostConstruct
    public void registerWithHealthMonitor() {
        healthMonitor.registerTask(
                "session-monitor",
                monitoringInterval,
                status -> {
                    // Recovery action - if we're unhealthy, restart monitoring
                    if (monitoringTask == null || monitoringTask.isCancelled()) {
                        startScheduledMonitoring();
                    }
                }
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
                    ShowTestNotificationCommand command = commandFactory.createShowTestNotificationCommand(username);
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

        // Calculate initial delay to align with the next interval mark
        Duration initialDelay = calculateTimeToNextCheck();

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
            // Record task execution in health monitor
            healthMonitor.recordTaskExecution("session-monitor");

            // Run the actual check
            checkActiveSessions();

            // Always reschedule for the next interval
            Duration nextDelay = calculateTimeToNextCheck();
            monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(nextDelay));

            LoggerUtil.info(this.getClass(), String.format("Next monitoring check scheduled in %d minutes and %d seconds", nextDelay.toMinutes(), nextDelay.toSecondsPart()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in monitoring task: " + e.getMessage(), e);
            // Record failure in health monitor
            healthMonitor.recordTaskFailure("session-monitor", e.getMessage());
            // Reschedule anyway to keep the service running even after errors
            Duration retryDelay = Duration.ofMinutes(5); // Shorter retry interval
            monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(retryDelay));
            LoggerUtil.info(this.getClass(), String.format("Rescheduled after error with %d minute retry delay", retryDelay.toMinutes()));
        }
    }

    /**
     * Calculates time to the next monitoring check based on configured interval
     */
    private Duration calculateTimeToNextCheck() {
        // Use validation system to get time values
        GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);

        LocalDateTime now = timeValues.getCurrentTime();
        LocalDateTime nextCheck;

        // Use the configured monitoring interval (in minutes) from application properties
        int monitoringIntervalMinutes = monitoringInterval;

        // Calculate next interval mark
        int minute = now.getMinute();
        int nextMinute = ((minute / monitoringIntervalMinutes) + 1) * monitoringIntervalMinutes;

        if (nextMinute >= 60) {
            // If we need to go to the next hour
            nextCheck = now.plusHours(1).withMinute(nextMinute % 60).withSecond(0).withNano(0);
        } else {
            // Go to the next interval mark in this hour
            nextCheck = now.withMinute(nextMinute).withSecond(0).withNano(0);
        }

        // Handle special cases for 5:00 AM and 5:00 PM resets
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

        Duration duration = Duration.between(now, nextCheck);
        LoggerUtil.debug(this.getClass(), String.format("Next check scheduled at %s (in %d minutes and %d seconds)", nextCheck, duration.toMinutes(), duration.toSecondsPart()));

        return duration;
    }

    /**
     * Main method for checking active user sessions and triggering notification
     */
    public void checkActiveSessions() {
        LoggerUtil.debug(this.getClass(),
                "Checking active sessions on thread: " +
                        Thread.currentThread().getName());
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

            // Get current session using the command pattern
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Skip if session is null or not online
            if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                return;
            }

            // Update calculations using command pattern
            UpdateSessionCalculationsCommand updateCommand = commandFactory.createUpdateSessionCalculationsCommand(session, session.getDayEndTime());
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
            // Get standardized time values using validation system
            GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);

            LocalDateTime now = timeValues.getCurrentTime();

            // Check if total temporary stop minutes exceed 15 hours
            if (session.getTotalTemporaryStopMinutes() != null &&
                    session.getTotalTemporaryStopMinutes() >= WorkCode.MAX_TEMP_STOP_HOURS * WorkCode.HOUR_DURATION) {
                return;
            }

            CalculateMinutesBetweenQuery minutesQuery = calculationFactory.createCalculateMinutesBetweenQuery(
                    tempStopStart, now);
            int minutesSinceTempStop = calculationService.executeQuery(minutesQuery);

            // Show warning every hour of temporary stop
            if (minutesSinceTempStop >= WorkCode.HOURLY_INTERVAL) {
                // Use the new notification service instead
                notificationService.showTempStopWarning(
                        username,
                        session.getUserId(),
                        tempStopStart
                );
            }
        }
    }

    /**
     * Checks if scheduled work time is complete and shows warning
     */
    private void checkScheduleCompletion(WorkUsersSessionsStates session, User user) {
        LocalDate sessionDate = session.getDayStartTime().toLocalDate();

        // Get standardized time values using validation system
        GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);

        LocalDate today = timeValues.getCurrentDate();

        if (!sessionDate.equals(today)) {
            LoggerUtil.info(this.getClass(),
                    String.format("Skipping schedule notice for past session from %s", sessionDate));
            return;
        }

        // Use WorkScheduleQuery to get schedule info
        WorkScheduleQuery query = commandFactory.createWorkScheduleQuery(sessionDate, user.getSchedule());
        WorkScheduleQuery.ScheduleInfo scheduleInfo = commandService.executeQuery(query);

        int workedMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;

        // Add this debug logging
        LoggerUtil.debug(this.getClass(), String.format("Schedule check - User: %s, Schedule: %d hours, " +
                        "Is 8-hour schedule: %b, Required minutes: %d, " +
                        "Current worked minutes: %d, Notified already: %b",
                session.getUsername(), scheduleInfo.getScheduleHours(), scheduleInfo.isStandardEightHourSchedule(),
                scheduleInfo.getFullDayDuration(),
                workedMinutes, notificationShown.getOrDefault(session.getUsername(), false)));

        // Only show notification if not already shown for this session and schedule is completed
        if (scheduleInfo.isScheduleCompleted(workedMinutes) && !notificationShown.getOrDefault(session.getUsername(), false)) {

            // Use the new notification service
            boolean success = notificationService.showScheduleEndNotification(
                    session.getUsername(),
                    session.getUserId(),
                    session.getFinalWorkedMinutes()
            );

            if (success) {
                notificationShown.put(session.getUsername(), true);

                // Save the updated session
                SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(session);
                commandService.executeCommand(saveCommand);

                LoggerUtil.info(this.getClass(),
                        String.format("Schedule completion notification shown for user %s (worked: %d minutes)",
                                session.getUsername(), workedMinutes));
            }
        }
    }

    /**
     * Shows hourly warnings for users continuing to work after schedule completion
     */
    public void checkHourlyWarning(WorkUsersSessionsStates session) {
        String username = session.getUsername();
        LocalDateTime lastWarning = lastHourlyWarning.get(username);

        // Get standardized time values using validation system
        GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);

        LocalDateTime now = timeValues.getCurrentTime();

        // Calculate next hourly check time (1 hour after the last warning)
        LocalDateTime nextHourlyCheckTime = lastWarning != null
                ? lastWarning.plusHours(1)
                : now;

        // Check if it's time for the next hourly warning
        if (lastWarning == null || now.isAfter(nextHourlyCheckTime)) {
            // Add detailed logging
            LoggerUtil.info(this.getClass(), String.format("Preparing hourly warning for %s - Last warning: %s, Next check: %s",
                    username,
                    lastWarning != null ? lastWarning.toString() : "never",
                    nextHourlyCheckTime));

            // Show hourly warning using the new notification service
            boolean success = notificationService.showHourlyWarning(
                    username,
                    session.getUserId(),
                    session.getFinalWorkedMinutes()
            );

            if (success) {
                // Save the updated session
                SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(session);
                commandService.executeCommand(saveCommand);

                // Update the last warning time to current time
                lastHourlyWarning.put(username, now);
            }
        }
    }

    /**
     * Shows start day reminder if user hasn't started their workday
     * Also checks for and resets stale sessions from previous days
     */
    public void checkStartDayReminder() {
        if (!isInitialized) {
            return;
        }

        try {
            // Only check during working hours on weekdays
            IsWeekdayCommand weekdayCommand = validationFactory.createIsWeekdayCommand();
            IsWorkingHoursCommand workingHoursCommand = validationFactory.createIsWorkingHoursCommand();

            boolean isWeekday = validationService.execute(weekdayCommand);
            boolean isWorkingHours = validationService.execute(workingHoursCommand);

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);

            LocalDate today = timeValues.getCurrentDate();

            // Get current user
            String username = getCurrentActiveUser();
            if (username == null) {
                return;
            }

            // Check if we already showed notification today
            if (lastStartDayCheck.containsKey(username) && lastStartDayCheck.get(username).equals(today)) {
                return;
            }

            User user = userService.getUserByUsername(username).orElseThrow(() -> new RuntimeException("User not found: " + username));

            // Get current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // *** KEY ADDITION: STALE SESSION CHECK ***
            // If there's an active session, check if it's from a previous day
            if (session != null && session.getDayStartTime() != null) {
                LocalDate sessionDate = session.getDayStartTime().toLocalDate();
                boolean isActive = WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());

                // If the session is active and from a previous day, reset it
                if (isActive && !sessionDate.equals(today)) {
                    LoggerUtil.warn(this.getClass(), String.format("Found stale active session from %s for user %s - resetting during morning check", sessionDate, username));

                    // Create fresh session
                    WorkUsersSessionsStates freshSession = createFreshSession(username, user.getUserId());

                    // Save the fresh session
                    SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(freshSession);
                    commandService.executeCommand(saveCommand);

                    // Update our session reference to the fresh session
                    session = freshSession;

                    LoggerUtil.info(this.getClass(), String.format("Reset stale session for user %s during morning check", username));

                    // Record in health monitor
                    healthMonitor.recordTaskWarning("session-midnight-handler", "Stale session detected and reset during morning check");
                }
            }
            // *** END OF STALE SESSION CHECK ***

            // Only continue with normal checks if it's a weekday during working hours
            if (!isWeekday || !isWorkingHours) {
                return;
            }

            // Check for unresolved worktime entries - this has priority
            WorktimeResolutionQuery resolutionQuery = commandFactory.createWorktimeResolutionQuery(username);
            WorktimeResolutionQuery.ResolutionStatus resolutionStatus = commandService.executeQuery(resolutionQuery);

            // Check if the user has completed a session today
            boolean hasCompletedSessionToday = false;
            if (session != null && session.getDayStartTime() != null) {
                hasCompletedSessionToday = session.getDayStartTime().toLocalDate().equals(today) && WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()) && session.getWorkdayCompleted();
            }

            // If they already completed a session today, don't show reminder
            if (hasCompletedSessionToday) {
                return;
            }

            // If there are unresolved worktime entries, show resolution notification instead of start day reminder
            if (resolutionStatus.isNeedsResolution()) {
                LoggerUtil.info(this.getClass(), String.format("User %s has unresolved worktime entries - showing resolution notification", username));

                // Show resolution notification using the new notification service
                notificationService.showResolutionReminder(
                        username,
                        user.getUserId(),
                        WorkCode.RESOLUTION_TITLE,
                        WorkCode.RESOLUTION_MESSAGE,
                        WorkCode.RESOLUTION_MESSAGE_TRAY,
                        WorkCode.ON_FOR_TWELVE_HOURS
                );

                // Update last check date so we don't keep showing it
                lastStartDayCheck.put(username, today);
                return;
            }


            // Use SessionValidator to check if session exists
            if (!SessionValidator.exists(session, this.getClass())) {
                return;
            }

            // Validate that session is in offline state and no active session for today
            if (session != null && WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()) && !hasActiveSessionToday(session)) {
                // Show notification using the new notification service
                notificationService.showStartDayReminder(username, user.getUserId());
                // Update last check date
                lastStartDayCheck.put(username, today);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking start day reminder: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a fresh session
     */
    private WorkUsersSessionsStates createFreshSession(String username, Integer userId) {
        WorkUsersSessionsStates freshSession = new WorkUsersSessionsStates();
        freshSession.setUserId(userId);
        freshSession.setUsername(username);
        freshSession.setSessionStatus(WorkCode.WORK_OFFLINE);
        freshSession.setDayStartTime(null);
        freshSession.setDayEndTime(null);
        freshSession.setCurrentStartTime(null);
        freshSession.setTotalWorkedMinutes(0);
        freshSession.setFinalWorkedMinutes(0);
        freshSession.setTotalOvertimeMinutes(0);
        freshSession.setLunchBreakDeducted(true);
        freshSession.setWorkdayCompleted(false);
        freshSession.setTemporaryStopCount(0);
        freshSession.setTotalTemporaryStopMinutes(0);
        freshSession.setTemporaryStops(List.of());
        freshSession.setLastTemporaryStopTime(null);
        freshSession.setLastActivity(LocalDateTime.now());

        return freshSession;
    }

    /**
     * Gets the currently active user by scanning session files
     */
    private String getCurrentActiveUser() {
        Path localSessionPath = dataAccessService.getLocalSessionPath("", 0).getParent();
        if (!Files.exists(localSessionPath)) {
            return null;
        }

        try (Stream<Path> pathStream = Files.list(localSessionPath)) {
            return pathStream
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
        LoggerUtil.info(this.getClass(), "Started monitoring for user: " + username);
    }

    /**
     * Stops monitoring for a user session
     */
    public void stopMonitoring(String username) {
        clearMonitoring(username);
        LoggerUtil.info(this.getClass(), "Stopped monitoring for user: " + username);
    }

    /**
     * Checks if session has activity today
     */
    private boolean hasActiveSessionToday(WorkUsersSessionsStates session) {
        if (session == null || session.getDayStartTime() == null) {
            return false;
        }

        LocalDate sessionDate = session.getDayStartTime().toLocalDate();

        // Get standardized time values using validation system
        GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);

        LocalDate today = timeValues.getCurrentDate();

        return sessionDate.equals(today);
    }

    public void activateHourlyMonitoring(String username, LocalDateTime timestamp) {
        continuedAfterSchedule.put(username, true);
        lastHourlyWarning.put(username, timestamp);
        LoggerUtil.info(this.getClass(), "Activated hourly monitoring for user: " + username);
    }
}