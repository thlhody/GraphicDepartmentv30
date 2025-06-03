package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.monitoring.MonitoringStateService;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.notification.service.NotificationBackupService;
import com.ctgraphdep.security.UserContextService;
import com.ctgraphdep.service.cache.StatusCacheService;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.cache.SessionCacheService;
import com.ctgraphdep.session.commands.SaveSessionCommand;
import com.ctgraphdep.session.query.GetLocalUserQuery;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ENHANCED: Component responsible for resetting sessions at midnight.
 * Now includes status cache management and coordination with cache services.
 * Key responsibilities:
 * 1. Reset user session files to fresh state
 * 2. Clear all monitoring state
 * 3. Reset notification system
 * 4. Refresh status cache with updated user data
 * 5. Clear session cache for fresh start
 */
@Component
public class SessionMidnightHandler {
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final SchedulerHealthMonitor healthMonitor;
    private final NotificationService notificationService;
    private final NotificationBackupService notificationBackupService;
    private final MonitoringStateService monitoringStateService;
    private final StatusCacheService statusCacheService; // NEW: Status cache integration
    private final SessionCacheService sessionCacheService; // NEW: Session cache integration
    private final UserContextService userContextService;

    public SessionMidnightHandler(
            SessionCommandService commandService,
            SessionCommandFactory commandFactory,
            SchedulerHealthMonitor healthMonitor,
            NotificationService notificationService,
            NotificationBackupService notificationBackupService,
            MonitoringStateService monitoringStateService,
            StatusCacheService statusCacheService, // NEW
            SessionCacheService sessionCacheService, UserContextService userContextService) { // NEW
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.healthMonitor = healthMonitor;
        this.notificationService = notificationService;
        this.notificationBackupService = notificationBackupService;
        this.monitoringStateService = monitoringStateService;
        this.statusCacheService = statusCacheService; // NEW
        this.sessionCacheService = sessionCacheService; // NEW
        this.userContextService = userContextService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * ENHANCED: The cron expression follows the pattern: second minute hour day-of-month month day-of-week
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
            LoggerUtil.info(this.getClass(), "Starting midnight reset process...");

            // Get local user using the new query
            GetLocalUserQuery userQuery = commandFactory.createGetLocalUserQuery();
            User localUser = commandService.executeQuery(userQuery);

            if (localUser == null) {
                LoggerUtil.warn(this.getClass(), "No local user found, skipping session reset");
                return;
            }

            String username = localUser.getUsername();
            LoggerUtil.info(this.getClass(), String.format("Performing midnight reset for user: %s", username));

            // STEP 1: Reset the user's session file
            resetUserSession(localUser);

            // STEP 2: Clear all monitoring state using centralized service
            monitoringStateService.clearUserState(username);
            LoggerUtil.info(this.getClass(), String.format("Cleared all monitoring state for user %s", username));

            // STEP 3: NEW - Clear session cache for fresh start
            sessionCacheService.clearUserCache(username);
            LoggerUtil.info(this.getClass(), String.format("Cleared session cache for user %s", username));

            // STEP 4: NEW - Refresh status cache with updated user data from UserService
            statusCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();
            LoggerUtil.info(this.getClass(), "Refreshed status cache with updated user data from UserService");

            // STEP 5: NEW - Write status cache to file for persistence
            statusCacheService.writeToFile();
            LoggerUtil.info(this.getClass(), "Persisted status cache to file after user data refresh");

            // STEP 6: NEW - Reset UserContextCache (access counter, failure state)
            userContextService.performMidnightReset();
            LoggerUtil.info(this.getClass(), "Reset UserContextCache access counter and failure state");

            // STEP 7: Reset notification system
            resetNotificationSystem(username);

            // STEP 8: Cancel backup task explicitly
            notificationBackupService.cancelBackupTask(username);
            LoggerUtil.info(this.getClass(), "Completed comprehensive midnight reset for user " + username);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during midnight reset: " + e.getMessage(), e);

            // Report error to health monitor
            healthMonitor.recordTaskFailure("midnight-reset", "Midnight reset failed: " + e.getMessage());
        }
    }

    /**
     * ENHANCED: Resets a user's session to a fresh state.
     * Now coordinates with both session cache and status cache.
     * Made public so it can be called from startup commands.
     */
    public void resetUserSession(User user) {
        try {
            String username = user.getUsername();
            Integer userId = user.getUserId();

            LoggerUtil.info(this.getClass(), String.format("Resetting session for user %s (ID: %d)", username, userId));

            // Create fresh blank session
            WorkUsersSessionsStates freshSession = createFreshSession(username, userId);

            // Use SaveSessionCommand to persist the session and refresh cache
            SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(freshSession);
            commandService.executeCommand(saveCommand);

            // NEW: Update status cache to reflect offline status
            statusCacheService.updateUserStatus(username, userId, WorkCode.WORK_OFFLINE, LocalDateTime.now());

            LoggerUtil.info(this.getClass(), String.format("Reset session file and caches for user %s", username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error resetting session for user %s: %s",
                    user.getUsername(), e.getMessage()), e);
        }
    }

    /**
     * ENHANCED: Resets notification system components.
     * This ensures a clean notification state after midnight reset.
     */
    private void resetNotificationSystem(String username) {
        try {
            LoggerUtil.info(this.getClass(), "Resetting notification system for user: " + username);

            // Reset notification service - using the single call interface
            notificationService.resetService();
            LoggerUtil.info(this.getClass(), "Reset notification service");

            // Reset health monitor status
            healthMonitor.resetTaskFailures("notification-service");
            healthMonitor.resetTaskFailures("notification-display-service");
            healthMonitor.resetTaskFailures("notification-checker");
            LoggerUtil.info(this.getClass(), "Reset health monitor statuses");

            // NEW: Record successful reset in health monitor
            healthMonitor.recordTaskExecution("midnight-reset");

            LoggerUtil.info(this.getClass(), "Notification system reset completed successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during notification system reset: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a fresh session with default offline state
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
     * NEW: Manual reset method for emergency use or testing
     * Can be called from admin interface or startup if needed
     */
    public void performManualReset(String username) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Performing manual reset for user: %s", username));

            // Find user
            GetLocalUserQuery userQuery = commandFactory.createGetLocalUserQuery();
            User localUser = commandService.executeQuery(userQuery);

            if (localUser != null && localUser.getUsername().equals(username)) {
                // Perform the same reset process
                resetUserSession(localUser);
                monitoringStateService.clearUserState(username);
                sessionCacheService.clearUserCache(username);

                // Update status to offline
                statusCacheService.updateUserStatus(username, localUser.getUserId(),
                        WorkCode.WORK_OFFLINE, LocalDateTime.now());

                LoggerUtil.info(this.getClass(), String.format("Manual reset completed for user: %s", username));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("User not found for manual reset: %s", username));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during manual reset for user %s: %s",
                    username, e.getMessage()), e);
        }
    }

    /**
     * NEW: Emergency cache reset method
     * Clears all caches and rebuilds from scratch
     */
    public void performEmergencyCacheReset() {
        try {
            LoggerUtil.warn(this.getClass(), "Performing emergency cache reset - clearing all caches");

            // Clear all session cache
            sessionCacheService.clearAllCache();
            LoggerUtil.info(this.getClass(), "Cleared all session cache");

            // Clear and rebuild status cache
            statusCacheService.clearAllCache();
            statusCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();
            statusCacheService.writeToFile();
            LoggerUtil.info(this.getClass(), "Cleared and rebuilt status cache");

            // Clear all monitoring state
            GetLocalUserQuery userQuery = commandFactory.createGetLocalUserQuery();
            User localUser = commandService.executeQuery(userQuery);

            if (localUser != null) {
                monitoringStateService.clearUserState(localUser.getUsername());
                LoggerUtil.info(this.getClass(), "Cleared monitoring state");
            }

            LoggerUtil.info(this.getClass(), "Emergency cache reset completed successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during emergency cache reset: " + e.getMessage(), e);
            healthMonitor.recordTaskFailure("emergency-reset", "Emergency cache reset failed: " + e.getMessage());
        }
    }

    /**
     * NEW: Status check method for health monitoring
     * Returns current reset system status
     */
    public String getMidnightResetStatus() {
        try {
            StringBuilder status = new StringBuilder();
            status.append("Midnight Reset System Status:\n");

            // Check if local user exists
            GetLocalUserQuery userQuery = commandFactory.createGetLocalUserQuery();
            User localUser = commandService.executeQuery(userQuery);

            if (localUser != null) {
                status.append("Local User: ").append(localUser.getUsername()).append("\n");

                // Check session cache status
                String sessionCacheStatus = sessionCacheService.getCacheStatus();
                status.append("Session Cache: ").append(sessionCacheStatus.split("\n")[1]).append("\n");

                // Check status cache status
                String statusCacheStatus = statusCacheService.getCacheStatus();
                status.append("Status Cache: ").append(statusCacheStatus.split("\n")[1]).append("\n");

                // Check monitoring state
                String monitoringMode = monitoringStateService.getMonitoringMode(localUser.getUsername());
                status.append("Monitoring Mode: ").append(monitoringMode).append("\n");

            } else {
                status.append("Local User: NOT FOUND\n");
            }

            return status.toString();

        } catch (Exception e) {
            return "Error getting midnight reset status: " + e.getMessage();
        }
    }
}