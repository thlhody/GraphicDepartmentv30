package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * with periodic network synchronization
 */
@Service
public class UserLogService {
    private static final Logger logger = LoggerFactory.getLogger(UserLogService.class);

    private final PathConfig pathConfig;
    private final DataAccessService dataAccessService;

    // Configurable retry parameters
    private static final int MAX_RETRIES = 3;
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
                logger.info("Created network log directory: {}", networkLogDir.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create network log directory", e);
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
            logger.info("Network unavailable, skipping log sync");
            return;
        }

        try {
            logger.info("Starting log synchronization for user: {}", username);

            if (username == null || username.isEmpty()) {
                logger.warn("Could not determine username, skipping log sync");
                return;
            }

            // Resolve source and target log paths
            Path sourceLogPath = pathConfig.getLocalLogPath();
            Path targetLogPath = pathConfig.getNetworkLogPath(username);

            // Check if local log file exists
            if (Files.exists(sourceLogPath)) {
                // Sync log file with retry mechanism
                syncWithRetry(sourceLogPath, targetLogPath);

                logger.info("Synced log file to network: {}", targetLogPath.toAbsolutePath());
            } else {
                logger.warn("Local log file not found: {}", sourceLogPath.toAbsolutePath());
            }

            logger.info("Completed log synchronization");
        } catch (Exception e) {
            logger.error("Error during log synchronization", e);
        }
    }

    /**
     * Manually triggered log sync
     */
    public void manualSync() {
        try {
            // Get username from local users file
            String username = getLocalUsername();
            logger.info("Attempting to sync logs for user: {}", username);

            // Check network availability
            if (!pathConfig.isNetworkAvailable()) {
                logger.warn("Network is not available. Cannot sync logs.");
                throw new IOException("Network is not available");
            }

            // Resolve source and target log paths
            Path sourceLogPath = pathConfig.getLocalLogPath();
            Path targetLogPath = pathConfig.getNetworkLogPath(username);

            logger.info("Source log path: {}", sourceLogPath);
            logger.info("Target log path: {}", targetLogPath);

            // Check if local log file exists
            if (!Files.exists(sourceLogPath)) {
                logger.warn("Local log file does not exist: {}", sourceLogPath);
                throw new IOException("Local log file not found");
            }

            // Sync log file with retry mechanism
            syncWithRetry(sourceLogPath, targetLogPath);

            logger.info("Successfully synced log file to network for user: {}", username);
        } catch (Exception e) {
            logger.error("Error during manual log synchronization", e);
            throw new RuntimeException("Failed to sync logs", e);
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

                logger.info("Successfully synced log on attempt {}", attempt + 1);
                return; // Success, exit method
            } catch (IOException e) {
                lastException = e;
                logger.warn("Sync attempt {} failed: {}", attempt + 1, e.getMessage());

                // Wait before retry, but not on last attempt
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        logger.info("Waiting {} seconds before retry", RETRY_DELAY_MS / 1000);
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
            logger.error("Error reading local user", e);
        }

        // Fallback to system username
        return System.getProperty("user.name");
    }
}