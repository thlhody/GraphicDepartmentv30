package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.VersionModelAttribute;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REFACTORED service for logging user activity to individual log files
 * with periodic network synchronization and graceful network failure handling.
 * Now uses SessionDataService for all log operations and MainDefaultUserContextService for user info.
 */
@Service
public class UserLogService {
    private final DataAccessService dataAccessService;
    private final SessionDataService sessionDataService;       // NEW - Log operations
    private final MainDefaultUserContextService mainDefaultUserContextService;       // NEW - Current user info
    private final UserService userService;                     // NEW - User data from cache

    // Configurable retry parameters
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 120000; // 2 minutes

    @Autowired
    public UserLogService(DataAccessService dataAccessService, SessionDataService sessionDataService, MainDefaultUserContextService mainDefaultUserContextService, UserService userService) {
        this.dataAccessService = dataAccessService;
        this.sessionDataService = sessionDataService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * REFACTORED: Scheduled task to sync application logs to network
     * Runs every 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes in milliseconds
    public void syncLogToNetwork() {
        syncUserLog(getLocalUsername());
    }

    /**
     * REFACTORED: Sync log for a specific user using SessionDataService
     */
    private void syncUserLog(String username) {
        // Use SessionDataService to check network availability
        if (!dataAccessService.isNetworkAvailable()) {
            LoggerUtil.info(this.getClass(), "Network unavailable, skipping log sync");
            return;
        }

        try {
            LoggerUtil.info(this.getClass(), "Starting log synchronization for user: " + username);

            if (username == null || username.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Could not determine username, skipping log sync");
                return;
            }

            // Sync log file with retry mechanism
            boolean success = syncWithRetry(username);

            if (success) {
                LoggerUtil.info(this.getClass(), "Synced log file to network for user: " + username);
            } else {
                LoggerUtil.warn(this.getClass(), "Failed to sync log file for user: " + username);
            }

            LoggerUtil.info(this.getClass(), "Completed log synchronization");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during log synchronization", e);
        }
    }

    /**
     * REFACTORED: Manually triggered log sync with graceful network unavailability handling
     * @return SyncResult with status and message
     */
    public SyncResult manualSync() {
        // Get username from MainDefaultUserContextService (cache-based)
        String username = getLocalUsername();
        LoggerUtil.info(this.getClass(), "Attempting to sync logs for user: " + username);

        // Check network availability via SessionDataService
        if (!dataAccessService.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network is not available. Cannot sync logs.");
            return new SyncResult(false, "Network is currently unavailable. Log sync will be performed automatically when connection is restored.");
        }

        try {
            // Check if local log file exists using SessionDataService
            if (!sessionDataService.localLogExists()) {
                LoggerUtil.warn(this.getClass(), "Local log file does not exist");
                return new SyncResult(false, "Local log file not found");
            }

            // Sync log file with retry mechanism
            boolean success = syncWithRetry(username);

            if (success) {
                LoggerUtil.info(this.getClass(), "Successfully synced log file to network for user: " + username);
                return new SyncResult(true, "Logs successfully synced to network");
            } else {
                return new SyncResult(false, "Failed to sync logs after multiple attempts");
            }
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error during manual log synchronization", e);
            return new SyncResult(false, "Error syncing logs: " + e.getMessage());
        }
    }

    /**
     * REFACTORED: Sync file with robust retry mechanism using SessionDataService
     */
    private boolean syncWithRetry(String username) throws IOException {
        IOException lastException = null;

        // Get the current application version
        String currentVersion = VersionModelAttribute.getCurrentVersion();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Copy log file using SessionDataService with version
                sessionDataService.syncLogToNetwork(username, currentVersion);

                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully synced log for user %s with version %s on attempt %d",
                        username, currentVersion, (attempt + 1)));
                return true; // Success
            } catch (IOException e) {
                lastException = e;
                LoggerUtil.warn(this.getClass(), "Sync attempt " + (attempt + 1) + " failed: " + e.getMessage());

                // Wait before retry, but not on last attempt
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        LoggerUtil.info(this.getClass(), "Waiting " + (RETRY_DELAY_MS / 1000) + " seconds before retry");
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Sync interrupted during retry wait", ie);
                    }
                }
            }
        }

        // If all retries fail, log comprehensive error
        LoggerUtil.error(this.getClass(), "Failed to sync log after " + MAX_RETRIES + " attempts", lastException);
        return false;
    }

    /**
     * REFACTORED: Get username using MainDefaultUserContextService (cache-based)
     */
    private String getLocalUsername() {
        try {
            // First try to get current user from MainDefaultUserContextService (cache-based)
            String currentUsername = mainDefaultUserContextService.getCurrentUsername();

            // If we got a real user (not "system"), use it
            if (currentUsername != null && !"system".equals(currentUsername)) {
                return currentUsername;
            }

            // Fallback: Try to get any local user from UserService (cache-based)
            List<User> users = userService.getNonAdminUsers(null);
            if (users != null && !users.isEmpty()) {
                String fallbackUsername = users.get(0).getUsername();
                LoggerUtil.debug(this.getClass(), String.format(
                        "Using fallback username from cache: %s", fallbackUsername));
                return fallbackUsername;
            }

            // Last resort: system username
            LoggerUtil.warn(this.getClass(), "No users found in cache, using system username");
            return System.getProperty("user.name");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting local username: " + e.getMessage(), e);
            // Ultimate fallback
            return System.getProperty("user.name");
        }
    }

    /**
     * REFACTORED: Get list of user logs with version information using SessionDataService
     */
    public List<UserLogInfo> getUserLogsWithVersionInfo() {
        List<String> usernames = sessionDataService.getUserLogsList();
        List<UserLogInfo> userLogsInfo = new ArrayList<>();

        for (String username : usernames) {
            String logFilename = sessionDataService.getLogFilename(username);
            String version = sessionDataService.extractVersionFromLogFilename(logFilename);
            userLogsInfo.add(new UserLogInfo(username, version));
        }

        return userLogsInfo;
    }

    /**
     * REFACTORED: Get log content for a specific user using SessionDataService
     */
    public Optional<String> getUserLogContent(String username) {
        return sessionDataService.getUserLogContent(username);
    }

    /**
     * Result class to encapsulate sync operation result
     */
    @Getter
    public static class SyncResult {
        private final boolean success;
        private final String message;

        public SyncResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Class to hold username and version information
     */
    @Getter
    public static class UserLogInfo {
        private final String username;
        private final String version;

        public UserLogInfo(String username, String version) {
            this.username = username;
            this.version = version != null && !version.isEmpty() ? version : "Unknown";
        }
    }
}