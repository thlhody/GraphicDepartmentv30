package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.UpdateSessionActivityCommand;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Backup service for handling session notification in case the primary notification system fails.
 * This service provides monitoring of notification but no longer automatically ends sessions.
 * Refactored to use the command pattern for all session operations.
 */
@Service
public class SystemNotificationBackupService {

    private final TaskScheduler taskScheduler;
    private final PathConfig pathConfig;
    private final SessionCommandService sessionCommandService;
    private final SessionCommandFactory commandFactory;

    // Track scheduled tasks for users
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> scheduleEndNotificationTimes = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> hourlyNotificationTimes = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> tempStopNotificationTimes = new ConcurrentHashMap<>();

    public SystemNotificationBackupService(@Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler,
                                           PathConfig pathConfig, @Lazy SessionCommandService sessionCommandService, @Lazy SessionCommandFactory commandFactory) {
        this.taskScheduler = taskScheduler;
        this.pathConfig = pathConfig;
        this.sessionCommandService = sessionCommandService;
        this.commandFactory = commandFactory;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Registers a scheduled end notification backup for a user.
     * Will track the notification but not automatically end session.
     */
    public void registerScheduleEndNotification(String username, Integer userId) {
        // Cancel any existing task
        cancelExistingTask(username);
        // Record notification time
        scheduleEndNotificationTimes.put(username, LocalDateTime.now());
        // Schedule backup task for monitoring purposes
        ScheduledFuture<?> task = taskScheduler.schedule(() -> handleScheduleEndBackup(username, userId), Instant.now().plus(Duration.ofMinutes(11)));
        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(), String.format("Registered schedule end notification backup for user %s", username));
    }

    /**
     * Registers an hourly warning notification backup.
     * Will track the notification but not automatically end session.
     */
    public void registerHourlyWarningNotification(String username, Integer userId) {
        // Cancel any existing task
        cancelExistingTask(username);
        // Record notification time
        hourlyNotificationTimes.put(username, LocalDateTime.now());
        // Schedule backup task for monitoring purposes
        ScheduledFuture<?> task = taskScheduler.schedule(() -> handleHourlyWarningBackup(username, userId), Instant.now().plus(Duration.ofMinutes(6)));
        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(), String.format("Registered hourly warning notification backup for user %s", username));
    }

    /**
     * Registers a temporary stop notification backup.
     * Will track notification status for temporary stops.
     */
    public void registerTempStopNotification(String username, Integer userId, LocalDateTime tempStopStart) {
        // Cancel any existing task
        cancelExistingTask(username);
        // Record notification time
        tempStopNotificationTimes.put(username, tempStopStart);
        // Schedule backup task for monitoring purposes
        ScheduledFuture<?> task = taskScheduler.schedule(() -> handleTempStopBackup(username, userId), Instant.now().plus(Duration.ofMinutes(6)));
        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(), String.format("Registered temp stop notification backup for user %s", username));
    }

    /**
     * Cancels existing backup tasks for a user when they've taken action through the normal UI
     */
    public void cancelBackupTask(String username) {
        cancelExistingTask(username);
        // Remove notification records
        scheduleEndNotificationTimes.remove(username);
        hourlyNotificationTimes.remove(username);
        tempStopNotificationTimes.remove(username);
        LoggerUtil.info(this.getClass(), String.format("Cancelled backup notification for user %s (user responded)", username));
    }

    /**
     * Gets stalled schedule end notifications for handling
     * @return A map of usernames to notification times
     */
    public Map<String, LocalDateTime> getStalledScheduleEndNotifications() {
        return scheduleEndNotificationTimes;
    }

    /**
     * Handles backup for schedule end notification in case UI fails
     * Now records a continuation point instead of ending the session
     */
    private void handleScheduleEndBackup(String username, Integer userId) {
        try {
            // Use command to get current session instead of direct service call
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, userId);
            WorkUsersSessionsStates session = sessionCommandService.executeQuery(sessionQuery);

            // Only proceed if session is still online (user hasn't already ended it through UI)
            if (session != null && WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                LoggerUtil.warn(this.getClass(),
                        String.format("BACKUP: User %s did not respond to schedule end notification - recording continuation point",
                                username));

            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in schedule end backup handler for %s: %s", username, e.getMessage()));
        } finally {
            // Remove notification record
            scheduleEndNotificationTimes.remove(username);
        }
    }

    /**
     * Handles backup for hourly warning notification in case UI fails
     * Now records a continuation point instead of ending the session
     */
    private void handleHourlyWarningBackup(String username, Integer userId) {
        try {
            // Check for alternative notification tracking mechanism
            Path trackingFile = pathConfig.getLocalPath().resolve("notification").resolve(username + "_notification.lock");
            boolean trackingExists = Files.exists(trackingFile);

            if (trackingExists) {
                // Read tracking file to see when notification was shown
                String content = new String(Files.readAllBytes(trackingFile));
                LocalDateTime notificationTime = LocalDateTime.parse(content);

                // If notification was shown less than 3 minutes ago, defer backup action
                if (ChronoUnit.MINUTES.between(notificationTime, LocalDateTime.now()) < 3) {
                    LoggerUtil.info(this.getClass(), String.format("Deferring backup action for %s - notification still active", username));

                    // Reschedule for another 2 minutes
                    taskScheduler.schedule(() -> handleHourlyWarningBackup(username, userId), Instant.now().plus(Duration.ofMinutes(2)));
                    return;
                }

                // Clean up tracking file
                Files.deleteIfExists(trackingFile);
            }

            // Use command to get current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, userId);
            WorkUsersSessionsStates session = sessionCommandService.executeQuery(sessionQuery);

            // Only proceed if session is still online
            if (session != null && WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                LoggerUtil.warn(this.getClass(), String.format("BACKUP: User %s did not respond to hourly warning - recording continuation point", username));

            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in hourly warning backup handler for %s: %s", username, e.getMessage()));
        } finally {
            // Remove notification record
            hourlyNotificationTimes.remove(username);
        }
    }

    /**
     * Handles backup for temp stop notification in case UI fails
     * Will continue to track temporary stop status
     */
    private void handleTempStopBackup(String username, Integer userId) {
        try {
            // Use command to get current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, userId);
            WorkUsersSessionsStates session = sessionCommandService.executeQuery(sessionQuery);

            // Only proceed if session is still in temp stop
            if (session != null && WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                LoggerUtil.warn(this.getClass(), String.format("BACKUP: Continuing temporary stop for user %s (notification backup)", username));

                // Continue temp stop tracking
                try {
                    // Use command to update the session timestamp
                    UpdateSessionActivityCommand updateCommand = commandFactory.createUpdateSessionActivityCommand(username, userId);
                    sessionCommandService.executeCommand(updateCommand);

                    LoggerUtil.info(this.getClass(), String.format("Continued temporary stop through backup for user %s", username));
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Failed to update session during temp stop continuation for %s: %s", username, e.getMessage()));
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in temp stop backup handler for %s: %s", username, e.getMessage()));
        } finally {
            // Remove notification record
            tempStopNotificationTimes.remove(username);
        }
    }

    /**
     * Helper method to cancel any existing scheduled tasks for a user
     */
    private void cancelExistingTask(String username) {
        ScheduledFuture<?> existingTask = scheduledTasks.remove(username);
        if (existingTask != null && !existingTask.isDone() && !existingTask.isCancelled()) {
            existingTask.cancel(false);
        }
    }

    /**
     * Removes a schedule end notification record for a user
     * This is called when handling stalled notifications
     *
     * @param username The username to remove notification for
     */
    public void removeScheduleEndNotification(String username) {
        scheduleEndNotificationTimes.remove(username);
        LoggerUtil.info(this.getClass(), String.format("Removed stalled schedule end notification for user %s", username));
    }

    /**
     * Removes an hourly warning notification record for a user
     * This is called when handling stalled notifications
     *
     * @param username The username to remove notification for
     */
    public void removeHourlyNotification(String username) {
        hourlyNotificationTimes.remove(username);
        LoggerUtil.info(this.getClass(), String.format("Removed stalled hourly notification for user %s", username));
    }

    /**
     * Removes a temporary stop notification record for a user
     * This is called when handling stalled notifications
     *
     * @param username The username to remove notification for
     */
    public void removeTempStopNotification(String username) {
        tempStopNotificationTimes.remove(username);
        LoggerUtil.info(this.getClass(), String.format("Removed stalled temporary stop notification for user %s", username));
    }

    /**
     * Gets stalled hourly warning notifications for handling
     * @return A map of usernames to notification times
     */
    public Map<String, LocalDateTime> getStalledHourlyNotifications() {
        return hourlyNotificationTimes;
    }

    /**
     * Gets stalled temporary stop notifications for handling
     * @return A map of usernames to notification times
     */
    public Map<String, LocalDateTime> getStalledTempStopNotifications() {
        return tempStopNotificationTimes;
    }
}