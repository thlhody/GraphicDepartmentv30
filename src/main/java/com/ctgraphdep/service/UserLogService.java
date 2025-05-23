package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.VersionModelAttribute;
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
 * Service for logging user activity to individual log files
 * with periodic network synchronization and graceful network failure handling
 */
@Service
public class UserLogService {

    private final DataAccessService dataAccessService;

    // Configurable retry parameters
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 120000; // 2 minutes

    @Autowired
    public UserLogService(DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
    }

    /**
     * Scheduled task to sync application logs to network
     * Runs every 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes in milliseconds
    public void syncLogToNetwork() {
        syncUserLog(getLocalUsername());
    }

    /**
     * Sync log for a specific user
     */
    private void syncUserLog(String username) {
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
     * Manually triggered log sync with graceful network unavailability handling
     * @return SyncResult with status and message
     */
    public SyncResult manualSync() {
        // Get username from local users file
        String username = getLocalUsername();
        LoggerUtil.info(this.getClass(), "Attempting to sync logs for user: " + username);

        // Check network availability first
        if (!dataAccessService.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network is not available. Cannot sync logs.");
            return new SyncResult(false, "Network is currently unavailable. Log sync will be performed automatically when connection is restored.");
        }

        try {
            // Check if local log file exists
            if (!dataAccessService.localLogExists()) {
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
     * Sync file with robust retry mechanism
     */
    private boolean syncWithRetry(String username) throws IOException {
        IOException lastException = null;

        // Get the current application version
        String currentVersion = VersionModelAttribute.getCurrentVersion();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Copy log file using DataAccessService with version
                dataAccessService.syncLogToNetwork(username, currentVersion);

                LoggerUtil.info(this.getClass(), String.format("Successfully synced log for user %s with version %s on attempt %d", username, currentVersion, (attempt + 1)));
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
     * Get username from local users file
     */
    private String getLocalUsername() {
        try {
            // Read local users file using existing method
            List<User> users = dataAccessService.readLocalUser();

            // If users exist, return the first user's username
            if (users != null && !users.isEmpty()) {
                return users.get(0).getUsername();
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading local user", e);
        }

        // Fallback to system username
        return System.getProperty("user.name");
    }

    /**
     * Get list of user logs with version information
     */
    public List<UserLogInfo> getUserLogsWithVersionInfo() {
        List<String> usernames = dataAccessService.getUserLogsList();
        List<UserLogInfo> userLogsInfo = new ArrayList<>();

        for (String username : usernames) {
            String logFilename = dataAccessService.getLogFilename(username);
            String version = dataAccessService.extractVersionFromLogFilename(logFilename);
            userLogsInfo.add(new UserLogInfo(username, version));
        }

        return userLogsInfo;
    }

    /**
     * Get log content for a specific user
     */
    public Optional<String> getUserLogContent(String username) {
        return dataAccessService.getUserLogContent(username);
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