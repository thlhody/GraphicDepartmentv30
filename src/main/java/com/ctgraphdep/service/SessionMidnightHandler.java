package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.notification.service.NotificationBackupService;
import com.ctgraphdep.notification.service.NotificationMonitorService;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.SaveSessionCommand;
import com.ctgraphdep.session.query.GetLocalUserQuery;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Component responsible for resetting sessions at midnight.
 * Simply resets session file to 0 for the next day without saving to worktime.
 */
@Component
public class SessionMidnightHandler {
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final SessionMonitorService sessionMonitorService;
    private final SchedulerHealthMonitor healthMonitor;
    private final NotificationService notificationService;
    private final NotificationMonitorService notificationMonitorService;
    private final NotificationBackupService notificationBackupService;

    public SessionMidnightHandler(
            SessionCommandService commandService, SessionCommandFactory commandFactory,
            SessionMonitorService sessionMonitorService, SchedulerHealthMonitor healthMonitor,
            NotificationService notificationService, NotificationMonitorService notificationMonitorService, NotificationBackupService notificationBackupService) {
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.sessionMonitorService = sessionMonitorService;
        this.healthMonitor = healthMonitor;
        this.notificationService = notificationService;
        this.notificationMonitorService = notificationMonitorService;
        this.notificationBackupService = notificationBackupService;
        LoggerUtil.initialize(this.getClass(), null);
    }
    /**
    * The cron expression follows the pattern: second minute hour day-of-month month day-of-week
    * So 0 59 23 * * * means:
    * 0 - at the 0th second
    * 59 - at the 59th minute
    * 23 - at the 23rd hour (11 PM)
    * * - any day of the month
    * * - any month
    * * - any day of the week
    */

    @Scheduled(cron = "0 59 23 * * *")
    public void resetLocalUserSession() {
        try {
            // Get local user using the new query
            GetLocalUserQuery userQuery = commandFactory.createGetLocalUserQuery();
            User localUser = commandService.executeQuery(userQuery);

            if (localUser == null) {
                LoggerUtil.warn(this.getClass(), "No local user found, skipping session reset");
                return;
            }

            // Reset the user's session
            resetUserSession(localUser);

            // Add notification system reset
            resetNotificationSystem(localUser.getUsername());
            notificationMonitorService.clearUserState(String.valueOf(localUser));
            notificationBackupService.cancelBackupTask(String.valueOf(localUser));

            LoggerUtil.info(this.getClass(), "Completed midnight reset for user " + localUser.getUsername());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during session reset: " + e.getMessage(), e);
        }
    }

    /**
     * Resets a user's session to a fresh state.
     * Made public so it can be called from startup commands.
     */
    public void resetUserSession(User user) {
        try {
            // Create fresh blank session
            WorkUsersSessionsStates freshSession = createFreshSession(user.getUsername(), user.getUserId());

            // Use SaveSessionCommand to persist the session
            SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(freshSession);
            commandService.executeCommand(saveCommand);

            LoggerUtil.info(this.getClass(), String.format("Reset session file to 0 for user %s", user.getUsername()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error resetting session for user %s: %s", user.getUsername(), e.getMessage()), e);
        }
    }

    /**
     * Resets notification system components.
     * This ensures a clean notification state after midnight reset.
     */
    private void resetNotificationSystem(String username) {
        try {
            LoggerUtil.info(this.getClass(), "Resetting notification system for user: " + username);

            // 1. Clear monitoring service state
            if (sessionMonitorService != null) {
                sessionMonitorService.clearMonitoring(username);
                LoggerUtil.info(this.getClass(), "Cleared session monitoring state");
            }

            // 2. Reset notification service - now using the new interface
            if (notificationService != null) {
                notificationService.resetService();
                LoggerUtil.info(this.getClass(), "Reset notification service");
            }

            // 3. Reset health monitor status
            if (healthMonitor != null) {
                healthMonitor.resetTaskFailures("notification-service");
                healthMonitor.resetTaskFailures("notification-display-service");
                healthMonitor.resetTaskFailures("notification-checker");
                LoggerUtil.info(this.getClass(), "Reset health monitor statuses");
            }

            LoggerUtil.info(this.getClass(), "Notification system reset completed successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during notification system reset: " + e.getMessage(), e);
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

}