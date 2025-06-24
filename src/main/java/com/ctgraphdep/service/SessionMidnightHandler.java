package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.monitoring.MonitoringStateService;
import com.ctgraphdep.monitoring.SchedulerHealthMonitor;
import com.ctgraphdep.notification.api.NotificationService;
import com.ctgraphdep.notification.service.NotificationBackupService;
import com.ctgraphdep.security.LoginMergeCacheService; // NEW IMPORT
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.service.cache.SessionCacheService;
import com.ctgraphdep.session.commands.SaveSessionCommand;
import com.ctgraphdep.session.query.GetLocalUserQuery;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ENHANCED: Component responsible for resetting sessions at midnight.
 * Now includes LoginMergeCacheService integration for daily login optimization.
 * Key responsibilities:
 * 1. Reset user session files to fresh state
 * 2. Clear all monitoring state
 * 3. Reset notification system
 * 4. Refresh status cache with updated user data
 * 5. Clear session cache for fresh start
 * 6. NEW - Reset daily login count for merge optimization
 */
@Component
public class SessionMidnightHandler {
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final SchedulerHealthMonitor healthMonitor;
    private final NotificationService notificationService;
    private final NotificationBackupService notificationBackupService;
    private final MonitoringStateService monitoringStateService;
    private final AllUsersCacheService allUsersCacheService;
    private final SessionCacheService sessionCacheService;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final LoginMergeCacheService loginMergeCacheService; // NEW DEPENDENCY

    public SessionMidnightHandler(
            SessionCommandService commandService,
            SessionCommandFactory commandFactory,
            SchedulerHealthMonitor healthMonitor,
            NotificationService notificationService,
            NotificationBackupService notificationBackupService,
            MonitoringStateService monitoringStateService,
            AllUsersCacheService allUsersCacheService,
            SessionCacheService sessionCacheService,
            MainDefaultUserContextService mainDefaultUserContextService,
            LoginMergeCacheService loginMergeCacheService) { // NEW PARAMETER
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.healthMonitor = healthMonitor;
        this.notificationService = notificationService;
        this.notificationBackupService = notificationBackupService;
        this.monitoringStateService = monitoringStateService;
        this.allUsersCacheService = allUsersCacheService;
        this.sessionCacheService = sessionCacheService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.loginMergeCacheService = loginMergeCacheService; // NEW ASSIGNMENT
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

            // STEP 3: Clear session cache for fresh start
            sessionCacheService.clearUserCache(username);
            LoggerUtil.info(this.getClass(), String.format("Cleared session cache for user %s", username));

            // STEP 4: Refresh status cache with updated user data from UserService
            allUsersCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();
            LoggerUtil.info(this.getClass(), "Refreshed status cache with updated user data from UserService");

            // STEP 5: Write status cache to file for persistence
            allUsersCacheService.writeToFile();
            LoggerUtil.info(this.getClass(), "Persisted status cache to file after user data refresh");

            // STEP 6: Reset MainDefaultUserContextCache (access counter, failure state)
            mainDefaultUserContextService.performMidnightReset();
            LoggerUtil.info(this.getClass(), "Reset MainDefaultUserContextCache access counter and failure state");

            // STEP 7: NEW - Reset daily login count for merge optimization
            String loginStatusBefore = loginMergeCacheService.getStatus();
            loginMergeCacheService.resetDailyLoginCount();
            String loginStatusAfter = loginMergeCacheService.getStatus();

            LoggerUtil.info(this.getClass(), String.format("Reset daily login count - Before: [%s], After: [%s]",
                    loginStatusBefore, loginStatusAfter));
            LoggerUtil.info(this.getClass(), "Next login will trigger full merge for optimal data synchronization");

            // STEP 8: Reset notification system
            resetNotificationSystem(username);

            // STEP 9: Cancel backup task explicitly
            notificationBackupService.cancelBackupTask(username);

            LoggerUtil.info(this.getClass(), "Completed comprehensive midnight reset for user " + username);
            LoggerUtil.info(this.getClass(), loginMergeCacheService.getPerformanceBenefit());

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

            // Update status cache to reflect offline status
            allUsersCacheService.updateUserStatus(username, userId, WorkCode.WORK_OFFLINE, LocalDateTime.now());

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

            // Record successful reset in health monitor
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
     * ENHANCED: Manual reset method for emergency use or testing
     * Now includes login merge cache reset for complete state cleanup
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

                // NEW - Reset login merge cache for complete state reset
                String beforeStatus = loginMergeCacheService.getStatus();
                loginMergeCacheService.resetDailyLoginCount();
                String afterStatus = loginMergeCacheService.getStatus();

                // Update status to offline
                allUsersCacheService.updateUserStatus(username, localUser.getUserId(),
                        WorkCode.WORK_OFFLINE, LocalDateTime.now());

                LoggerUtil.info(this.getClass(), String.format(
                        "Manual reset completed for user: %s. Login cache: [%s] -> [%s]",
                        username, beforeStatus, afterStatus));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("User not found for manual reset: %s", username));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during manual reset for user %s: %s",
                    username, e.getMessage()), e);
        }
    }

    /**
     * ENHANCED: Emergency cache reset method
     * Now includes login merge cache reset for complete system cleanup
     */
    public void performEmergencyCacheReset() {
        try {
            LoggerUtil.warn(this.getClass(), "Performing emergency cache reset - clearing all caches");

            // Clear all session cache
            sessionCacheService.clearAllCache();
            LoggerUtil.info(this.getClass(), "Cleared all session cache");

            // Clear and rebuild status cache
            allUsersCacheService.clearAllCache();
            allUsersCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();
            allUsersCacheService.writeToFile();
            LoggerUtil.info(this.getClass(), "Cleared and rebuilt status cache");

            // NEW - Reset login merge cache for emergency cleanup
            String beforeStatus = loginMergeCacheService.getStatus();
            loginMergeCacheService.resetDailyLoginCount();
            String afterStatus = loginMergeCacheService.getStatus();
            LoggerUtil.info(this.getClass(), String.format(
                    "Reset login merge cache: [%s] -> [%s]", beforeStatus, afterStatus));

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
     * ENHANCED: Status check method for health monitoring
     * Now includes login merge cache status for complete system overview
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
                String statusCacheStatus = allUsersCacheService.getCacheStatus();
                status.append("Status Cache: ").append(statusCacheStatus.split("\n")[1]).append("\n");

                // Check monitoring state
                String monitoringMode = monitoringStateService.getMonitoringMode(localUser.getUsername());
                status.append("Monitoring Mode: ").append(monitoringMode).append("\n");

                // NEW - Check login merge cache status
                status.append("Login Merge Cache: ").append(loginMergeCacheService.getStatus()).append("\n");
                status.append("Performance Optimization: ").append(loginMergeCacheService.getPerformanceBenefit()).append("\n");
                status.append("Initial State: ").append(loginMergeCacheService.isInInitialState() ? "Yes (ready for first login)" : "No").append("\n");

            } else {
                status.append("Local User: NOT FOUND\n");
            }

            return status.toString();

        } catch (Exception e) {
            return "Error getting midnight reset status: " + e.getMessage();
        }
    }

    // ========================================================================
    // NEW - LOGIN MERGE CACHE MANAGEMENT METHODS
    // ========================================================================

    /**
     * NEW: Force full merge on next login (for admin emergency use)
     * Resets login count to 0, so next login will trigger slow merge
     */
    public void forceFullMergeOnNextLogin() {
        try {
            String beforeStatus = loginMergeCacheService.getStatus();
            loginMergeCacheService.forceFullMergeOnNextLogin();
            String afterStatus = loginMergeCacheService.getStatus();

            LoggerUtil.info(this.getClass(), String.format(
                    "Forced full merge on next login: [%s] -> [%s]", beforeStatus, afterStatus));
            LoggerUtil.info(this.getClass(), "Next login will perform full data merge regardless of time");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error forcing full merge: " + e.getMessage(), e);
        }
    }

    /**
     * NEW: Get login optimization status for monitoring
     * Returns current state of login merge optimization
     */
    public String getLoginOptimizationStatus() {
        try {
            StringBuilder status = new StringBuilder();
            status.append("Login Optimization Status:\n");
            status.append("Current State: ").append(loginMergeCacheService.getStatus()).append("\n");
            status.append("Performance: ").append(loginMergeCacheService.getPerformanceBenefit()).append("\n");
            status.append("Ready for First Login: ").append(loginMergeCacheService.isInInitialState() ? "Yes" : "No").append("\n");

            if (!loginMergeCacheService.isInInitialState()) {
                status.append("Today's Login Strategy: Fast Cache Refresh\n");
                status.append("Performance Gain: ~70% faster than full merge\n");
            } else {
                status.append("Next Login Strategy: Full Merge\n");
                status.append("Reason: First login of the day or system reset\n");
            }

            return status.toString();

        } catch (Exception e) {
            return "Error getting login optimization status: " + e.getMessage();
        }
    }

    /**
     * NEW: Manual trigger for login optimization reset (for testing)
     * Useful for testing the optimization without waiting for midnight
     */
    public void manuallyResetLoginOptimization() {
        try {
            LoggerUtil.info(this.getClass(), "Manually resetting login optimization for testing");

            String beforeStatus = loginMergeCacheService.getStatus();
            loginMergeCacheService.resetDailyLoginCount();
            String afterStatus = loginMergeCacheService.getStatus();

            LoggerUtil.info(this.getClass(), String.format(
                    "Manual login optimization reset: [%s] -> [%s]", beforeStatus, afterStatus));
            LoggerUtil.info(this.getClass(), "Next login will trigger full merge for testing");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during manual login optimization reset: " + e.getMessage(), e);
        }
    }
}