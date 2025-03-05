package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Backup service for handling session notifications in case the primary notification system fails.
 * This service provides a safety net to ensure critical actions are taken even if UI notifications fail.
 */
@Service
public class SystemNotificationBackupService {

    private final UserSessionService userSessionService;
    private final UserService userService;
    private final SessionMonitorService sessionMonitorService;
    private final TaskScheduler taskScheduler;

    // Track scheduled tasks for users
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> scheduleEndNotificationTimes = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> hourlyNotificationTimes = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> tempStopNotificationTimes = new ConcurrentHashMap<>();

    public SystemNotificationBackupService(
            UserSessionService userSessionService,
            UserService userService,
            SessionMonitorService sessionMonitorService,
            @Qualifier("sessionMonitorScheduler") TaskScheduler taskScheduler) {
        this.userSessionService = userSessionService;
        this.userService = userService;
        this.sessionMonitorService = sessionMonitorService;
        this.taskScheduler = taskScheduler;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Registers a scheduled end notification backup for a user.
     * Will automatically end session if notification handling fails.
     */
    public void registerScheduleEndNotification(String username, Integer userId, Integer finalMinutes) {
        // Cancel any existing task
        cancelExistingTask(username);

        // Record notification time
        scheduleEndNotificationTimes.put(username, LocalDateTime.now());

        // Schedule backup task for 11 minutes later (1 minute after primary notification would auto-close)
        ScheduledFuture<?> task = taskScheduler.schedule(
                () -> handleScheduleEndBackup(username, userId, finalMinutes),
                Instant.now().plus(Duration.ofMinutes(11))
        );

        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(),
                String.format("Registered schedule end notification backup for user %s", username));
    }

    /**
     * Registers an hourly warning notification backup.
     * Will automatically end session if notification handling fails.
     */
    public void registerHourlyWarningNotification(String username, Integer userId, Integer finalMinutes) {
        // Cancel any existing task
        cancelExistingTask(username);

        // Record notification time
        hourlyNotificationTimes.put(username, LocalDateTime.now());

        // Schedule backup task for 6 minutes later (1 minute after primary notification would auto-close)
        ScheduledFuture<?> task = taskScheduler.schedule(
                () -> handleHourlyWarningBackup(username, userId, finalMinutes),
                Instant.now().plus(Duration.ofMinutes(6))
        );

        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(),
                String.format("Registered hourly warning notification backup for user %s", username));
    }

    /**
     * Registers a temporary stop notification backup.
     * Will automatically continue temp stop if notification handling fails.
     */
    public void registerTempStopNotification(String username, Integer userId, LocalDateTime tempStopStart) {
        // Cancel any existing task
        cancelExistingTask(username);

        // Record notification time
        tempStopNotificationTimes.put(username, LocalDateTime.now());

        // Schedule backup task for 6 minutes later (1 minute after primary notification would auto-close)
        ScheduledFuture<?> task = taskScheduler.schedule(
                () -> handleTempStopBackup(username, userId),
                Instant.now().plus(Duration.ofMinutes(6))
        );

        scheduledTasks.put(username, task);
        LoggerUtil.info(this.getClass(),
                String.format("Registered temp stop notification backup for user %s", username));
    }

    /**
     * Cancels existing backup tasks for a user when they've taken action through the normal UI
     */
    public void cancelBackupTask(String username) {
        cancelExistingTask(username);
        LoggerUtil.info(this.getClass(),
                String.format("Cancelled backup notification for user %s (user responded)", username));
    }

    /**
     * Handles backup for schedule end notification in case UI fails
     */
    private void handleScheduleEndBackup(String username, Integer userId, Integer finalMinutes) {
        try {
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);

            // Only proceed if:
            // 1. Session is still online (user hasn't already ended it through UI)
            // 2. We're not in temp stop mode
            if (session != null &&
                    WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {

                LoggerUtil.warn(this.getClass(),
                        String.format("BACKUP: Ending session for user %s due to no response to schedule end notification",
                                username));

                // End the session as if user didn't respond to notification
                userSessionService.endDay(username, userId, finalMinutes);
                sessionMonitorService.clearMonitoring(username);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error in schedule end backup handler for %s: %s",
                            username, e.getMessage()));
        } finally {
            // Remove notification record
            scheduleEndNotificationTimes.remove(username);
        }
    }

    /**
     * Handles backup for hourly warning notification in case UI fails
     */
    private void handleHourlyWarningBackup(String username, Integer userId, Integer finalMinutes) {
        try {
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);

            // Only proceed if session is still online
            if (session != null &&
                    WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {

                LoggerUtil.warn(this.getClass(),
                        String.format("BACKUP: Ending session for user %s due to no response to hourly warning",
                                username));

                // End the session as if user didn't respond to notification
                userSessionService.endDay(username, userId, finalMinutes);
                sessionMonitorService.clearMonitoring(username);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error in hourly warning backup handler for %s: %s",
                            username, e.getMessage()));
        } finally {
            // Remove notification record
            hourlyNotificationTimes.remove(username);
        }
    }

    /**
     * Handles backup for temp stop notification in case UI fails
     */
    private void handleTempStopBackup(String username, Integer userId) {
        try {
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, userId);

            // Only proceed if session is still in temp stop
            if (session != null &&
                    WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {

                LoggerUtil.warn(this.getClass(),
                        String.format("BACKUP: Continuing temporary stop for user %s (notification backup)",
                                username));

                // Continue temp stop as if user didn't respond
                sessionMonitorService.continueTempStop(username, userId);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error in temp stop backup handler for %s: %s",
                            username, e.getMessage()));
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
     * Checks if any sessions have active notifications that haven't received responses
     * and might have failed to auto-close properly
     */
    public void checkForStalledNotifications() {
        // Handle schedule end notifications
        scheduleEndNotificationTimes.forEach((username, time) -> {
            if (ChronoUnit.MINUTES.between(time, LocalDateTime.now()) >= 15) {
                try {
                    User user = userService.getUserByUsername(username).orElse(null);
                    if (user != null) {
                        WorkUsersSessionsStates session = userSessionService.getCurrentSession(username, user.getUserId());

                        if (session != null && WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                            LoggerUtil.warn(this.getClass(),
                                    String.format("Detected stalled schedule end notification for user %s, forcing session end", username));

                            userSessionService.endDay(username, user.getUserId(), session.getFinalWorkedMinutes());
                            sessionMonitorService.clearMonitoring(username);

                            // Remove after handling
                            scheduleEndNotificationTimes.remove(username);
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            String.format("Error handling stalled notification for %s: %s", username, e.getMessage()));
                }
            }
        });

        // Similar cleanup could be implemented for hourly and temp stop notifications
    }
}