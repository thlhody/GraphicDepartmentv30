package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.monitoring.MonitoringStateService;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.service.cache.SessionCacheService;
import com.ctgraphdep.session.commands.AutoEndSessionCommand;
import com.ctgraphdep.session.commands.EndDayCommand;
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

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Service responsible for monitoring active user sessions and status synchronization.
 * Now includes both session monitoring and status cache management.
 * Key responsibilities:
 * 1. Monitor active sessions and trigger notifications
 * 2. Update session calculations in cache
 * 3. Periodic session file writing for network sync
 * 4. Status cache synchronization from network flags
 * 5. Status cache file persistence
 */
@Service
public class SessionMonitorService {

    @Autowired
    private CalculationService calculationService;
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final TaskScheduler taskScheduler;
    private final TimeValidationService validationService;
    private final TimeValidationFactory validationFactory;
    private final NotificationService notificationService;
    private final MonitoringStateService monitoringStateService;
    private final MainDefaultUserContextService mainDefaultUserContextService;

    @Autowired
    private SchedulerHealthMonitor healthMonitor;
    @Autowired
    private SessionCacheService sessionCacheService;
    @Autowired
    private AllUsersCacheService allUsersCacheService; // NEW: Status cache integration

    // Track last file write times to coordinate with session commands
    private final Map<String, Long> lastFileWrites = new ConcurrentHashMap<>();
    private static final long MIN_FILE_WRITE_INTERVAL_MS = 5000; // 5 seconds minimum between file writes

    // Track ongoing file operations to avoid conflicts
    private final Set<String> activeFileOperations = ConcurrentHashMap.newKeySet();

    // Track which users have pending file sync needs
    private final Set<String> pendingFileSyncs = ConcurrentHashMap.newKeySet();

    @Value("${app.session.monitoring.interval:30}")
    private int monitoringInterval;

    @Value("${app.session.sync.interval:1800000}") // 30 minutes default
    private long syncInterval;

    private ScheduledFuture<?> monitoringTask;
    private ScheduledFuture<?> syncTask; // NEW: Separate sync task

    private volatile boolean isMonitoringInProgress = false;
    private volatile boolean isInitialized = false;

    public SessionMonitorService(SessionCommandService commandService, SessionCommandFactory commandFactory, @Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler,
            TimeValidationService validationService, TimeValidationFactory validationFactory, NotificationService notificationService, MonitoringStateService monitoringStateService,
                                 MainDefaultUserContextService mainDefaultUserContextService) {

        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.taskScheduler = taskScheduler;
        this.validationService = validationService;
        this.validationFactory = validationFactory;

        this.notificationService = notificationService;
        this.monitoringStateService = monitoringStateService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        // Schedule initialization with a 6-second delay after system tray is ready
        taskScheduler.schedule(this::delayedInitialization, Instant.now().plusMillis(6000));
    }

    @PostConstruct
    public void registerWithHealthMonitor() {
        healthMonitor.registerTask("session-monitor", monitoringInterval, status -> {
            // Recovery action - if we're unhealthy, restart monitoring
            if (monitoringTask == null || monitoringTask.isCancelled()) {
                startScheduledMonitoring();
            }
        });

        // NEW: Register sync task with health monitor
        healthMonitor.registerTask("session-sync", (int) (syncInterval / 60000), status -> {
            // Recovery action for sync task
            if (syncTask == null || syncTask.isCancelled()) {
                startScheduledSync();
            }
        });
    }

    // Delayed initialization to ensure system tray is ready
    private void delayedInitialization() {
        try {
            LoggerUtil.info(this.getClass(), "Starting delayed initialization of session monitoring...");

            // Start monitoring
            startScheduledMonitoring();
            // Start separate sync task
            startScheduledSync();

            isInitialized = true;
            showTestNotification();
            LoggerUtil.info(this.getClass(), "Session monitoring and sync initialized successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in initialization: " + e.getMessage(), e);
            // Even if initialization fails, try to schedule a retry
            try {
                taskScheduler.schedule(this::delayedInitialization, Instant.now().plusSeconds(30));
                LoggerUtil.info(this.getClass(), "Scheduled retry for initialization in 30 seconds");
            } catch (Exception retryEx) {
                LoggerUtil.error(this.getClass(), "Failed to schedule initialization retry: " + retryEx.getMessage());
            }
        }
    }

    // Checks if session is active (online or temporary stop)
    private boolean isActiveSession(WorkUsersSessionsStates session) {
        if (session == null) {
            return false;
        }
        return WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) || WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus());
    }

    // Starts the scheduled sync task for file operations and status cache
    private void startScheduledSync() {
        // Cancel any existing sync task
        if (syncTask != null && !syncTask.isCancelled()) {
            syncTask.cancel(false);
        }

        // Schedule sync task at fixed intervals
        syncTask = taskScheduler.scheduleAtFixedRate(this::performPeriodicSync, Instant.now().plusMillis(syncInterval), Duration.ofMillis(syncInterval));
        LoggerUtil.info(this.getClass(), String.format("Scheduled sync task to run every %d minutes", syncInterval / 60000));
    }

    // Performs periodic sync operations (every 30 minutes) Handles file writing and status cache synchronization
    private void performPeriodicSync() {
        try {
            LoggerUtil.debug(this.getClass(), "Performing periodic sync operations");

            // Record task execution in health monitor
            healthMonitor.recordTaskExecution("session-sync");

            // 1. SYNC SESSION TO FILE: Write active session to file for network visibility
            syncActiveSessionToFile();

            // 2. SYNC STATUS FROM NETWORK: Update status cache from network flags
            allUsersCacheService.syncFromNetworkFlags();

            // 3. PERSIST STATUS CACHE: Write status cache to local file
            allUsersCacheService.writeToFile();

            LoggerUtil.info(this.getClass(), "Completed periodic sync operations successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in periodic sync: " + e.getMessage(), e);
            healthMonitor.recordTaskFailure("session-sync", e.getMessage());
        }
    }

    // Syncs active session to file for network visibility. This ensures other instances can see the current user's activity
    private void syncActiveSessionToFile() {
        try {
            // Use original user context for background sync
            User originalUser = mainDefaultUserContextService.getOriginalUser();

            if (originalUser == null) {
                LoggerUtil.debug(this.getClass(), "No original user found for session sync");
                return;
            }

            // Skip sync for admin users (they don't have sessions)
            if (originalUser.isAdmin()) {
                LoggerUtil.debug(this.getClass(), "Skipping session sync - admin user doesn't have sessions");
                return;
            }

            String username = originalUser.getUsername();
            Integer userId = originalUser.getUserId();

            if (!canWriteToFile(username)) {
                LoggerUtil.debug(this.getClass(), "Skipping file sync - recent file operation detected for user: " + username);

                // Mark as needing sync later
                pendingFileSyncs.add(username);
                return;
            }

            // Remove from pending syncs if we're about to sync
            pendingFileSyncs.remove(username);

            // Get session using original user's credentials
            WorkUsersSessionsStates session = sessionCacheService.readSessionWithFallback(username, userId);

            if (session == null) {
                LoggerUtil.debug(this.getClass(), "No session found in cache for sync: " + username);
                return;
            }

            // Only sync if user has an active session
            if (isActiveSession(session)) {

                // *** COORDINATE WITH FILE OPERATIONS ***
                markFileOperationActive(username);

                try {
                    // Update calculations before writing to file (final calculation)
                    UpdateSessionCalculationsCommand updateCommand = commandFactory.createUpdateSessionCalculationsCacheOnlyCommand(session, getStandardTimeValues().getCurrentTime());
                    session = commandService.executeCommand(updateCommand);

                    // Write to file for network sync
                    SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(session);
                    commandService.executeCommand(saveCommand);

                    // Record successful file write
                    recordFileWrite(username);

                    LoggerUtil.info(this.getClass(), String.format("Synced active session to file for user: %s", username));

                } finally {
                    // Always cleanup file operation marker
                    markFileOperationComplete(username);
                }

            } else {
                LoggerUtil.debug(this.getClass(), String.format("Session not active, skipping file sync for user: %s (status: %s)",
                        username, session.getSessionStatus()));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error syncing session to file: " + e.getMessage(), e);
        }
    }

    // Starts the scheduled monitoring of user sessions
    private void startScheduledMonitoring() {
        // Cancel any existing task
        if (monitoringTask != null && !monitoringTask.isCancelled()) {
            monitoringTask.cancel(false);
        }

        // Calculate initial delay to align with the next interval mark
        Duration initialDelay = calculateTimeToNextCheck();

        // Schedule the first check
        monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(initialDelay));

        LoggerUtil.info(this.getClass(), String.format("Scheduled monitoring task to start in %d minutes and %d seconds", initialDelay.toMinutes(), initialDelay.toSecondsPart()));
    }

    // Runs the monitoring task and reschedules for the next check
    private void runAndRescheduleMonitoring() {
        if (isMonitoringInProgress) {
            LoggerUtil.warn(this.getClass(), "Previous monitoring task still in progress, skipping this execution");

            // Still reschedule for next time
            Duration nextDelay = calculateTimeToNextCheck();
            monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(nextDelay));
            return;
        }

        isMonitoringInProgress = true;

        try {
            // Record task execution in health monitor
            healthMonitor.recordTaskExecution("session-monitor");

            // Run the actual check
            checkActiveSessions();

            // Always reschedule for the next interval
            Duration nextDelay = calculateTimeToNextCheck();
            monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(nextDelay));

            LoggerUtil.debug(this.getClass(), String.format("Next monitoring check scheduled in %d minutes and %d seconds", nextDelay.toMinutes(), nextDelay.toSecondsPart()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in monitoring task: " + e.getMessage(), e);
            // Record failure in health monitor
            healthMonitor.recordTaskFailure("session-monitor", e.getMessage());
            // Reschedule anyway to keep the service running even after errors
            Duration retryDelay = Duration.ofMinutes(5); // Shorter retry interval
            monitoringTask = taskScheduler.schedule(this::runAndRescheduleMonitoring, Instant.now().plus(retryDelay));
            LoggerUtil.info(this.getClass(), String.format("Rescheduled after error with %d minute retry delay", retryDelay.toMinutes()));
        } finally {
            isMonitoringInProgress = false;
        }
    }

    // Main method for checking active user sessions and triggering notifications. Now focuses only on monitoring, not file I/O
    public void checkActiveSessions() {
        LoggerUtil.debug(this.getClass(), "Checking active sessions on thread: " + Thread.currentThread().getName());
        if (!isInitialized) {
            return;
        }

        try {
            checkStartDayReminder();

            User originalUser = mainDefaultUserContextService.getOriginalUser();
            if (originalUser == null) {
                LoggerUtil.debug(this.getClass(), "No original user context for session monitoring");
                return;
            }

            String username = originalUser.getUsername();
            // NEW: Add warning if we detect context confusion
            User currentContextUser = mainDefaultUserContextService.getCurrentUser();
            if (currentContextUser != null && !username.equals(currentContextUser.getUsername())) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Context mismatch detected in background thread: original=%s, current=%s - using original for monitoring",
                        username, currentContextUser.getUsername()));
            }

            LoggerUtil.debug(this.getClass(), String.format("Monitoring sessions for original user: %s", username));

            LoggerUtil.debug(this.getClass(), String.format("Monitoring sessions for original user: %s (elevated: %s)",
                    username, mainDefaultUserContextService.isElevated() ? "yes" : "no"));

            // Get current session using enhanced cache service
            WorkUsersSessionsStates session = sessionCacheService.readSessionWithFallback(username, originalUser.getUserId());

            // Use cache service for active session check
            if (!sessionCacheService.hasActiveSession(username, originalUser.getUserId())) {
                LoggerUtil.debug(this.getClass(), String.format("No active session found for user: %s", username));
                return;
            }

            // Update calculations in cache-only mode and read back the updated session
            boolean updateSuccess = sessionCacheService.updateSessionCalculationsWithWriteThrough(session, true); // true = cache-only mode
            if (updateSuccess) {
                // Read the updated session from cache
                session = sessionCacheService.readSessionWithFallback(username, originalUser.getUserId());
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Failed to update session calculations for user: %s", username));
                // Continue with existing session data
            }

            // Check monitoring based on CURRENT MONITORING MODE
            String monitoringMode = monitoringStateService.getMonitoringMode(username);

            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // Always check temp stop duration when session is in temporary stop state
                checkTempStopDuration(session);
            } else if (MonitoringStateService.MonitoringMode.HOURLY.equals(monitoringMode)) {
                // Use monitoring mode instead of scattered state flag
                checkHourlyWarning(session);
            } else {
                // Default to schedule completion check (pass originalUser instead of user)
                checkScheduleCompletion(session, originalUser);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in monitoring: " + e.getMessage(), e);
        }
    }

    // Shows start day reminder with improved stale session detection, Now uses original user context consistently
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
            LocalDate today = getStandardTimeValues().getCurrentDate();

            // Get original user instead of elevated admin
            User originalUser = mainDefaultUserContextService.getOriginalUser();

            if (originalUser == null) {
                LoggerUtil.debug(this.getClass(), "No original user context for start day reminder");
                return;
            }

            // Skip if original user is admin (admins don't have sessions)
            if (originalUser.isAdmin()) {
                LoggerUtil.debug(this.getClass(), "Skipping start day reminder - admin user doesn't have sessions");
                return;
            }

            String username = originalUser.getUsername();
            Integer userId = originalUser.getUserId();

            // Check if we already showed notification today
            if (monitoringStateService.wasStartDayCheckedToday(username, today)) {
                return;
            }

            // Use original user's credentials for session lookup
            WorkUsersSessionsStates session = sessionCacheService.readSessionWithFallback(username, userId);

            // KEY ENHANCEMENT: Stale session detection and reset
            if (session != null && session.getDayStartTime() != null) {
                LocalDate sessionDate = session.getDayStartTime().toLocalDate();
                boolean isActive = isActiveSession(session);

                // If the session is active and from a previous day, reset it
                if (isActive && !sessionDate.equals(today)) {
                    LoggerUtil.warn(this.getClass(), String.format("Found stale active session from %s for user %s - resetting during morning check", sessionDate, username));

                    // Use original user's credentials for reset
                    resetStaleSession(username, userId);

                    // Get the fresh session
                    session = sessionCacheService.readSessionWithFallback(username, userId);

                    LoggerUtil.info(this.getClass(), String.format("Reset stale session for user %s during morning check", username));

                    // Record in health monitor
                    healthMonitor.recordTaskWarning("session-midnight-handler", "Stale session detected and reset during morning check");
                }
            }

            // Only continue with normal checks if it's a weekday during working hours
            if (!isWeekday || !isWorkingHours) {
                return;
            }

            // Check unresolved entries for original user
            WorktimeResolutionQuery resolutionQuery = commandFactory.createWorktimeResolutionQuery(username);
            WorktimeResolutionQuery.ResolutionStatus resolutionStatus = commandService.executeQuery(resolutionQuery);

            // Check if the user has completed a session today
            boolean hasCompletedSessionToday = false;
            if (session != null && session.getDayStartTime() != null) {
                hasCompletedSessionToday = session.getDayStartTime().toLocalDate().equals(today) &&
                        WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()) &&
                        Boolean.TRUE.equals(session.getWorkdayCompleted());
            }

            // If they already completed a session today, don't show reminder
            if (hasCompletedSessionToday) {
                return;
            }

            // If there are unresolved worktime entries, show resolution notification
            if (resolutionStatus.isNeedsResolution()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "User %s has unresolved worktime entries - showing resolution notification", username));

                // Show notification for original user
                notificationService.showResolutionReminder(username, userId,
                        WorkCode.RESOLUTION_TITLE, WorkCode.RESOLUTION_MESSAGE,
                        WorkCode.RESOLUTION_MESSAGE_TRAY, WorkCode.ON_FOR_TWELVE_HOURS);

                monitoringStateService.recordStartDayCheck(username, today);
                return;
            }

            // Validate that session exists and is offline
            if (SessionValidator.exists(session, this.getClass()) &&
                    session != null && WorkCode.WORK_OFFLINE.equals(session.getSessionStatus()) &&
                    !hasActiveSessionToday(session)) {

                // Show notification for original user
                notificationService.showStartDayReminder(username, userId);

                // Record start day check
                monitoringStateService.recordStartDayCheck(username, today);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking start day reminder: " + e.getMessage(), e);
        }
    }

    // Resets a stale session by creating a fresh one
    private void resetStaleSession(String username, Integer userId) {
        try {
            // Create fresh session
            WorkUsersSessionsStates freshSession = createFreshSession(username, userId);

            // Save to file and refresh cache
            SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(freshSession);
            commandService.executeCommand(saveCommand);

            LoggerUtil.info(this.getClass(), String.format("Created fresh session for user %s", username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error resetting stale session for user %s: %s", username, e.getMessage()), e);
        }
    }

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

    // Checks if temporary stop duration exceeds limits and shows warnings
    private void checkTempStopDuration(WorkUsersSessionsStates session) {
        String username = session.getUsername();
        LocalDateTime tempStopStart = session.getLastTemporaryStopTime();

        if (tempStopStart != null) {
            LocalDateTime now = getStandardTimeValues().getCurrentTime();

            // Check if total temporary stop minutes exceed 15 hours
            if (session.getTotalTemporaryStopMinutes() != null &&
                    session.getTotalTemporaryStopMinutes() >= WorkCode.MAX_TEMP_STOP_HOURS * WorkCode.HOUR_DURATION) {
                return;
            }

            int minutesSinceTempStop = calculationService.calculateMinutesBetween(tempStopStart, now);

            // Use MonitoringStateService to check if notification is due
            if (monitoringStateService.isTempStopNotificationDue(username, minutesSinceTempStop, now) &&
                    notificationService.canShowNotification(username, WorkCode.TEMP_STOP_TYPE, WorkCode.HOURLY_INTERVAL)) {

                // Show notification
                notificationService.showTempStopWarning(username, session.getUserId(), tempStopStart);
            }
        }
    }

    // Checks if scheduled work time is complete and shows warning
    private void checkScheduleCompletion(WorkUsersSessionsStates session, User user) {
        LocalDate sessionDate = session.getDayStartTime().toLocalDate();
        LocalDate today = getStandardTimeValues().getCurrentDate();

        if (!sessionDate.equals(today)) {
            LoggerUtil.info(this.getClass(), String.format("Skipping schedule notice for past session from %s", sessionDate));
            return;
        }

        WorkScheduleQuery query = commandFactory.createWorkScheduleQuery(sessionDate, user.getSchedule());
        WorkScheduleQuery.ScheduleInfo scheduleInfo = commandService.executeQuery(query);

        int workedMinutes = session.getTotalWorkedMinutes() != null ? session.getTotalWorkedMinutes() : 0;

        if (scheduleInfo.isScheduleCompleted(workedMinutes) && !monitoringStateService.wasScheduleNotificationShown(session.getUsername())) {

            boolean success = notificationService.showScheduleEndNotification(session.getUsername(), session.getUserId(), session.getFinalWorkedMinutes());

            if (success) {
                monitoringStateService.markScheduleNotificationShown(session.getUsername());

                // *** ENHANCED: Only write to file if safe, otherwise mark for later sync ***
                if (canWriteToFile(session.getUsername())) {
                    markFileOperationActive(session.getUsername());
                    try {
                        SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(session);
                        commandService.executeCommand(saveCommand);
                        recordFileWrite(session.getUsername());
                        LoggerUtil.debug(this.getClass(), "Wrote session after schedule notification");
                    } finally {
                        markFileOperationComplete(session.getUsername());
                    }
                } else {
                    // Mark for later sync
                    pendingFileSyncs.add(session.getUsername());
                    LoggerUtil.debug(this.getClass(), "Marked session for later sync after schedule notification");
                }

                LoggerUtil.info(this.getClass(), String.format("Schedule completion notification shown for user %s (worked: %d minutes)", session.getUsername(), workedMinutes));
            }
        }
    }

    // Shows hourly warnings for users continuing to work after schedule completion
    public void checkHourlyWarning(WorkUsersSessionsStates session) {
        String username = session.getUsername();
        LocalDateTime now = getStandardTimeValues().getCurrentTime();

        if (monitoringStateService.isHourlyNotificationDue(username, now)) {
            LoggerUtil.info(this.getClass(), String.format("Preparing hourly warning for %s", username));

            boolean success = notificationService.showHourlyWarning(username, session.getUserId(), session.getFinalWorkedMinutes());

            if (success) {
                // *** ENHANCED: Only write to file if safe, otherwise mark for later sync ***
                if (canWriteToFile(username)) {
                    markFileOperationActive(username);
                    try {
                        SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(session);
                        commandService.executeCommand(saveCommand);
                        recordFileWrite(username);
                        LoggerUtil.debug(this.getClass(), "Wrote session after hourly warning");
                    } finally {
                        markFileOperationComplete(username);
                    }
                } else {
                    // Mark for later sync
                    pendingFileSyncs.add(username);
                    LoggerUtil.debug(this.getClass(), "Marked session for later sync after hourly warning");
                }

                monitoringStateService.recordHourlyNotification(username, now);
            }
        }
    }

    // ===== UTILITY METHODS =====

    // Calculates time to the next monitoring check based on configured interval
    private Duration calculateTimeToNextCheck() {
        LocalDateTime now = getStandardTimeValues().getCurrentTime();
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
        if (now.getHour() >= 5 && now.getHour() < 17 && eveningReset.isAfter(now) && eveningReset.isBefore(nextCheck)) {
            nextCheck = eveningReset;
        }

        return Duration.between(now, nextCheck);
    }

    // Checks if session has activity today
    private boolean hasActiveSessionToday(WorkUsersSessionsStates session) {
        if (session == null || session.getDayStartTime() == null) {
            return false;
        }

        LocalDate sessionDate = session.getDayStartTime().toLocalDate();
        LocalDate today = getStandardTimeValues().getCurrentDate();

        return sessionDate.equals(today);
    }

    private GetStandardTimeValuesCommand.StandardTimeValues getStandardTimeValues() {
        GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
        return validationService.execute(timeCommand);
    }

    // Displays a test notification to verify system functioning
    private void showTestNotification() {
        try {
            // Create and execute the test notification command
            ShowTestNotificationCommand command = commandFactory.createShowTestNotificationCommand();
            commandService.executeCommand(command);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error showing test notification: " + e.getMessage());
        }
    }

    //Clears monitoring state for a user
    public void clearMonitoring(String username) {
        monitoringStateService.clearUserState(username);
        clearUserSessionCache(username);
        LoggerUtil.info(this.getClass(), String.format("Cleared monitoring for user %s", username));
    }

    // Stops monitoring for a user session
    public void stopMonitoring(String username) {
        monitoringStateService.stopMonitoring(username);
        LoggerUtil.info(this.getClass(), "Stopped monitoring for user: " + username);
    }

    // Clear user cache (called during session reset/midnight)
    public void clearUserSessionCache(String username) {
        try {
            sessionCacheService.invalidateUserSession(username);
            LoggerUtil.info(this.getClass(), "Cleared session cache for user: " + username);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error clearing session cache for user " + username + ": " + e.getMessage(), e);
        }
    }

    //Activates hourly monitoring for continuing work after schedule completion
    public void activateHourlyMonitoring(String username, LocalDateTime timestamp) {
        monitoringStateService.transitionToHourlyMonitoring(username, timestamp);
        LoggerUtil.info(this.getClass(), "Activated hourly monitoring for user: " + username);
    }

    // Explicitly pauses schedule completion monitoring when a user enters temporary stop.
    public void pauseScheduleMonitoring(String username) {
        try {
            LocalDateTime now = getStandardTimeValues().getCurrentTime();
            monitoringStateService.startTempStopMonitoring(username, now);
            LoggerUtil.info(this.getClass(), String.format("Paused schedule completion monitoring for user %s (entered temporary stop)", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error pausing schedule monitoring for user %s: %s", username, e.getMessage()), e);
        }
    }

    // Resumes regular schedule completion monitoring after temporary stop ends.
    public void resumeScheduleMonitoring(String username) {
        try {
            monitoringStateService.resumeFromTempStop(username, false);
            LoggerUtil.info(this.getClass(), String.format("Resumed schedule monitoring for user %s (exited temporary stop)", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error resuming schedule monitoring for user %s: %s", username, e.getMessage()), e);
        }
    }

    // Determines if a user is currently in temporary stop monitoring mode.
    public boolean isInTempStopMonitoring(String username) {
        return monitoringStateService.isInTempStopMonitoring(username);
    }

    // Activates hourly monitoring with more explicit state management.
    public boolean activateExplicitHourlyMonitoring(String username, LocalDateTime timestamp) {
        try {
            monitoringStateService.transitionToHourlyMonitoring(username, timestamp);
            LoggerUtil.info(this.getClass(), String.format("Explicitly activated hourly monitoring for user %s", username));
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error activating explicit hourly monitoring for user %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    // Deactivates hourly monitoring.
    public void deactivateHourlyMonitoring(String username) {
        try {
            monitoringStateService.stopMonitoring(username);
            LoggerUtil.info(this.getClass(), String.format("Deactivated hourly monitoring for user %s", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error deactivating hourly monitoring for user %s: %s", username, e.getMessage()), e);
        }
    }

    // Enhanced version of startMonitoring that explicitly handles all monitoring states.
    public void startEnhancedMonitoring(String username) {
        try {
            monitoringStateService.startScheduleMonitoring(username);
            LoggerUtil.info(this.getClass(), String.format("Started enhanced monitoring for user %s (schedule completion monitoring active)", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error starting enhanced monitoring for user %s: %s", username, e.getMessage()), e);
        }
    }

    // Records that a temporary stop notification has been shown to a user.
    public void recordTempStopNotification(String username, LocalDateTime timestamp) {
        try {
            monitoringStateService.recordTempStopNotification(username, timestamp);

            // Ensure temp stop monitoring mode is active
            if (!monitoringStateService.isInTempStopMonitoring(username)) {
                pauseScheduleMonitoring(username);
            }

            LoggerUtil.debug(this.getClass(), String.format("Recorded temp stop notification for user %s at %s", username, timestamp));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error recording temp stop notification for user %s: %s", username, e.getMessage()), e);
        }
    }

    // Schedules an automatic end time for a user's session with proper monitoring coordination
    public boolean scheduleAutomaticEnd(String username, Integer userId, LocalDateTime endTime) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Scheduling automatic end for user %s at %s", username, endTime));

            // Create a Runnable that executes the auto end session command
            Runnable endAction = () -> {
                try {
                    LoggerUtil.info(this.getClass(), String.format("Executing scheduled end session for user %s at %s", username, endTime));

                    // First explicitly remove from monitoring maps to ensure no conflicts
                    monitoringStateService.pauseMonitoringBriefly(username, 500); // Pause for 500ms first

                    // Use our dedicated command with early monitoring shutdown
                    AutoEndSessionCommand command = commandFactory.createAutoEndSessionCommand(username, userId, endTime);
                    boolean success = commandService.executeCommand(command);

                    if (success) {
                        LoggerUtil.info(this.getClass(), String.format("Successfully executed scheduled end session for user %s", username));
                    } else {
                        LoggerUtil.warn(this.getClass(), String.format("Failed to execute scheduled end session for user %s, will try backup plan", username));
                        // Backup plan: directly use EndDayCommand if AutoEndSessionCommand fails
                        try {
                            // Try one more time with direct EndDayCommand
                            EndDayCommand endDayCommand = commandFactory.createEndDayCommand(username, userId, null, endTime);
                            commandService.executeCommand(endDayCommand);
                            LoggerUtil.info(this.getClass(), "Successfully executed backup end session plan");
                        } catch (Exception backupError) {
                            LoggerUtil.error(this.getClass(), "Backup end session plan also failed: " + backupError.getMessage());
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error executing scheduled end for user %s: %s", username, e.getMessage()), e);
                }
            };

            // Use the centralized scheduling service
            return monitoringStateService.scheduleAutomaticEnd(username, endTime, endAction);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error scheduling end for user %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    // Cancels a scheduled end time for a user
    public boolean cancelScheduledEnd(String username) {
        return monitoringStateService.cancelScheduledEnd(username);
    }

    //Gets the scheduled end time for a user if any
    public LocalDateTime getScheduledEndTime(String username) {
        return monitoringStateService.getScheduledEndTime(username);
    }

    // ========================================================================
    // FILE OPERATION COORDINATION METHODS
    // ========================================================================

    // Check if it's safe to write to file (avoid conflicts with session commands).
    private boolean canWriteToFile(String username) {
        // Check if there's an ongoing file operation by session commands
        if (activeFileOperations.contains(username)) {
            LoggerUtil.debug(this.getClass(), "File operation in progress for user: " + username);
            return false;
        }

        // Check minimum interval since last write
        Long lastWrite = lastFileWrites.get(username);
        if (lastWrite == null) {
            return true;
        }

        long timeSinceLastWrite = System.currentTimeMillis() - lastWrite;
        boolean canWrite = timeSinceLastWrite >= MIN_FILE_WRITE_INTERVAL_MS;

        if (!canWrite) {
            LoggerUtil.debug(this.getClass(), String.format("Too soon since last write for user %s (%dms ago)", username, timeSinceLastWrite));
        }

        return canWrite;
    }

    // Record file write timestamp for coordination.
    private void recordFileWrite(String username) {
        lastFileWrites.put(username, System.currentTimeMillis());

        // Cleanup old entries periodically
        if (lastFileWrites.size() > 20) {
            cleanupOldFileWrites();
        }
    }

    // Mark file operation as active (called by session commands).
    public void markFileOperationActive(String username) {
        activeFileOperations.add(username);
        LoggerUtil.debug(this.getClass(), "Marked file operation active for user: " + username);
    }

    // Mark file operation as complete (called by session commands).
    public void markFileOperationComplete(String username) {
        activeFileOperations.remove(username);
        LoggerUtil.debug(this.getClass(), "Marked file operation complete for user: " + username);

        // Check if this user has pending sync needs
        if (pendingFileSyncs.contains(username)) {
            LoggerUtil.info(this.getClass(), "User has pending sync needs, scheduling immediate sync: " + username);
            scheduleImmediateSync(username);
        }
    }

    // Schedule immediate sync for user with pending needs.
    private void scheduleImmediateSync(String username) {
        // Schedule sync with small delay to ensure command completion
        taskScheduler.schedule(() -> {
            try {
                if (canWriteToFile(username)) {
                    syncActiveSessionToFile();
                } else {
                    LoggerUtil.debug(this.getClass(), "Still cannot sync for user: " + username);
                }
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error in immediate sync: " + e.getMessage(), e);
            }
        }, Instant.now().plusMillis(2000)); // 2-second delay
    }

    // Cleanup old file write records to prevent memory leaks.
    private void cleanupOldFileWrites() {
        long cutoff = System.currentTimeMillis() - (MIN_FILE_WRITE_INTERVAL_MS * 10);
        int initialSize = lastFileWrites.size();
        lastFileWrites.entrySet().removeIf(entry -> entry.getValue() < cutoff);

        int cleaned = initialSize - lastFileWrites.size();
        if (cleaned > 0) {
            LoggerUtil.debug(this.getClass(), "Cleaned up " + cleaned + " old file write records");
        }
    }
}