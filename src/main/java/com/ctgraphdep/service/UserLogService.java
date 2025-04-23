package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Service for logging user activity to individual log files
 * with periodic network synchronization and graceful network failure handling
 */
@Service
public class UserLogService {

    private final PathConfig pathConfig;
    private final DataAccessService dataAccessService;

    // Configurable retry parameters
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 120000; // 2 minutes

    @Autowired
    public UserLogService(PathConfig pathConfig, DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
        this.pathConfig = pathConfig;

        // Ensure network log directory exists
        try {
            if (pathConfig.isNetworkAvailable()) {
                Path networkLogDir = pathConfig.getNetworkLogDirectory();
                Files.createDirectories(networkLogDir);
                LoggerUtil.info(this.getClass(), "Created network log directory: " + networkLogDir);
            }
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Failed to create network log directory", e);
        }
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
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.info(this.getClass(), "Network unavailable, skipping log sync");
            return;
        }

        try {
            LoggerUtil.info(this.getClass(), "Starting log synchronization for user: " + username);

            if (username == null || username.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Could not determine username, skipping log sync");
                return;
            }

            // Resolve source and target log paths
            Path sourceLogPath = pathConfig.getLocalLogPath();
            Path targetLogPath = pathConfig.getNetworkLogPath(username);

            // Check if local log file exists
            if (Files.exists(sourceLogPath)) {
                // Sync log file with retry mechanism
                syncWithRetry(sourceLogPath, targetLogPath);
                LoggerUtil.info(this.getClass(), "Synced log file to network: " + targetLogPath);
            } else {
                LoggerUtil.warn(this.getClass(), "Local log file not found: " + sourceLogPath);
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
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network is not available. Cannot sync logs.");
            return new SyncResult(false, "Network is currently unavailable. Log sync will be performed automatically when connection is restored.");
        }

        try {
            // Resolve source and target log paths
            Path sourceLogPath = pathConfig.getLocalLogPath();
            Path targetLogPath = pathConfig.getNetworkLogPath(username);

            LoggerUtil.info(this.getClass(), "Source log path: " + sourceLogPath);
            LoggerUtil.info(this.getClass(), "Target log path: " + targetLogPath);

            // Check if local log file exists
            if (!Files.exists(sourceLogPath)) {
                LoggerUtil.warn(this.getClass(), "Local log file does not exist: " + sourceLogPath);
                return new SyncResult(false, "Local log file not found");
            }

            // Sync log file with retry mechanism
            syncWithRetry(sourceLogPath, targetLogPath);

            LoggerUtil.info(this.getClass(), "Successfully synced log file to network for user: " + username);
            return new SyncResult(true, "Logs successfully synced to network");
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error during manual log synchronization", e);
            return new SyncResult(false, "Error syncing logs: " + e.getMessage());
        }
    }

    /**
     * Sync file with robust retry mechanism
     */
    private void syncWithRetry(Path source, Path target) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // Ensure target directory exists
                Files.createDirectories(target.getParent());

                // Copy log file
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

                LoggerUtil.info(this.getClass(), "Successfully synced log on attempt " + (attempt + 1));
                return; // Success, exit method
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

        // If all retries fail, throw comprehensive exception
        throw new IOException(
                "Failed to sync log after " + MAX_RETRIES + " attempts",
                lastException
        );
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
}