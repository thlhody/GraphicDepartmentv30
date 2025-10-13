package com.ctgraphdep.session.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.events.BackupEventListener;
import com.ctgraphdep.fileOperations.service.BackupService;
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
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
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

    private final SessionRegistry sessionRegistry;
    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final SchedulerHealthMonitor healthMonitor;
    private final NotificationService notificationService;
    private final NotificationBackupService notificationBackupService;
    private final MonitoringStateService monitoringStateService;
    private final AllUsersCacheService allUsersCacheService;
    private final SessionCacheService sessionCacheService;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final LoginMergeCacheService loginMergeCacheService;
    private final BackupService backupService;           // For clearSyncedBackupFilesCache()
    private final BackupEventListener backupEventListener;

    public SessionMidnightHandler(
            SessionRegistry sessionRegistry, SessionCommandService commandService, SessionCommandFactory commandFactory,
            SchedulerHealthMonitor healthMonitor, NotificationService notificationService, NotificationBackupService notificationBackupService,
            MonitoringStateService monitoringStateService, AllUsersCacheService allUsersCacheService, SessionCacheService sessionCacheService,
            MainDefaultUserContextService mainDefaultUserContextService, LoginMergeCacheService loginMergeCacheService, BackupService backupService,
            BackupEventListener backupEventListener) {
        this.sessionRegistry = sessionRegistry;
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
        this.backupService = backupService;
        this.backupEventListener = backupEventListener;
        LoggerUtil.initialize(this.getClass(), null);
    }

/**
 "0 0 19 * * *"
  │ │ │  │ │ │
  │ │ │  │ │ └── day of week (any)
  │ │ │  │ └──── month (any)
  │ │ │  └────── day of month (any)
  │ │ └──────── hour (19 = 7 PM)
  │ └────────── minute (0)
  └──────────── second (0)
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
            sessionCacheService.clearAllCache();
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

            // STEP 8: NEW - Clear backup sync caches for fresh daily state
            try {
                // Clear the "already synced files" cache in BackupService
                backupService.clearSyncedBackupFilesCache();

                // Clear the "user synced this session" cache in BackupEventListener
                backupEventListener.clearAllBackupSyncCaches();

                LoggerUtil.info(this.getClass(), "Cleared all backup sync caches for fresh daily state");
                LoggerUtil.info(this.getClass(), "Tomorrow's first login will sync one fresh backup to network");

            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), "Error clearing backup sync caches: " + e.getMessage());
                // Don't fail the entire midnight reset for this
            }

            // STEP 9: Invalidate browser sessions for daily reset (preserves Remember Me)
            try {
                invalidateAllBrowserSessions();
                LoggerUtil.info(this.getClass(), "Invalidated all browser sessions - users will need to login tomorrow");
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), "Error invalidating browser sessions: " + e.getMessage());
                // Don't fail the entire midnight reset for this
            }

            // STEP 10: Reset notification system
            resetNotificationSystem(username);

            // STEP 11: Cancel backup task explicitly
            notificationBackupService.cancelBackupTask(username);

            LoggerUtil.info(this.getClass(), "Completed comprehensive midnight reset for user " + username);
            LoggerUtil.info(this.getClass(), loginMergeCacheService.getPerformanceBenefit());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during midnight reset: " + e.getMessage(), e);

            // Report error to health monitor
            healthMonitor.recordTaskFailure("midnight-reset", "Midnight reset failed: " + e.getMessage());
        }
    }

    // Resets a user's session to a fresh state.
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

    // Resets notification system components. This ensures a clean notification state after midnight reset.
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

    // Manual reset method for emergency use or testing Now includes login merge cache reset for complete state cleanup
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
                sessionCacheService.clearAllCache();

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

    //Emergency cache reset method Now includes login merge cache reset for complete system cleanup
    public void performEmergencyCacheReset() {
        try {
            LoggerUtil.warn(this.getClass(), "Performing emergency cache reset - clearing all caches");

            GetLocalUserQuery userQuery = commandFactory.createGetLocalUserQuery();
            User localUser = commandService.executeQuery(userQuery);

            if (localUser != null) {
                String username = localUser.getUsername();
                Integer userId = localUser.getUserId();

                // Try to force refresh from file before clearing everything
                try {
                    boolean fileRefreshSuccess = sessionCacheService.forceRefreshFromFile(username, userId);
                    if (fileRefreshSuccess) {
                        LoggerUtil.info(this.getClass(), "File refresh successful - preserving valid session data");
                        return; // Don't clear cache if file refresh worked
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "File refresh failed during emergency reset: " + e.getMessage());
                }
            }

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
     * NEW: Invalidate all active browser sessions at midnight.
     * This forces users to login again the next day while preserving Remember Me cookies.
     * Since there's only one user per PC, this effectively logs out the single user.
     */
    private void invalidateAllBrowserSessions() {
        try {
            // Get all active browser sessions
            List<Object> allPrincipals = sessionRegistry.getAllPrincipals();

            if (allPrincipals.isEmpty()) {
                LoggerUtil.info(this.getClass(), "No active browser sessions found to invalidate");
                return;
            }

            int invalidatedCount = 0;

            for (Object principal : allPrincipals) {
                try {
                    // Get all sessions for this principal (user)
                    List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);

                    for (SessionInformation sessionInfo : sessions) {
                        if (!sessionInfo.isExpired()) {
                            // Expire the session (this invalidates it)
                            sessionInfo.expireNow();
                            invalidatedCount++;

                            LoggerUtil.debug(this.getClass(), String.format(
                                    "Expired browser session: %s for user: %s",
                                    sessionInfo.getSessionId(), principal.toString()));
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Error invalidating sessions for principal %s: %s", principal, e.getMessage()));
                }
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Midnight session invalidation completed: %d sessions expired, Remember Me cookies preserved",
                    invalidatedCount));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during browser session invalidation: " + e.getMessage(), e);
        }
    }

    // Status check method for health monitoring
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

}