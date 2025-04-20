package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.SyncStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SyncStatusManager {
    @Value("${app.sync.retry.delay:3600000}")
    private long retryDelay;

    private final PathConfig pathConfig;
    private final TimeValidationService timeValidationService;
    private final ObjectMapper objectMapper;
    private final Map<String, SyncStatus> syncStatusMap = new ConcurrentHashMap<>();
    private User localUser;

    // Function interface for sync operations
    public interface SyncOperation {
        boolean syncFile(Path localPath, Path networkPath);
    }

    // Method to set the sync operation implementation
    // Reference to the sync operation, set after construction
    @Setter
    private SyncOperation syncOperation;

    public SyncStatusManager(
            PathConfig pathConfig,
            TimeValidationService timeValidationService,
            ObjectMapper objectMapper) {
        this.pathConfig = pathConfig;
        this.timeValidationService = timeValidationService;
        this.objectMapper = objectMapper;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        // Load the local user directly from file without using DataAccessService
        try {
            this.localUser = readLocalUser();
            if (localUser != null) {
                LoggerUtil.info(this.getClass(),
                        String.format("Local user initialized: %s (ID: %d)",
                                localUser.getUsername(), localUser.getUserId()));
            } else {
                LoggerUtil.warn(this.getClass(), "No local user found during initialization");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing local user: " + e.getMessage(), e);
        }
    }

    /**
     * Read local user directly from file to avoid circular dependency
     */
    private User readLocalUser() {
        Path localPath = pathConfig.getLocalUsersPath();

        try {
            if (!Files.exists(localPath) || Files.size(localPath) < 3) {
                LoggerUtil.warn(this.getClass(), "Local users file doesn't exist or is empty");
                return null;
            }

            byte[] content = Files.readAllBytes(localPath);
            List<User> users = objectMapper.readValue(content, new TypeReference<>() {
            });

            if (users == null || users.isEmpty()) {
                LoggerUtil.info(this.getClass(), "No users found in local users file");
                return null;
            }

            // Return the first user (assuming single user per instance)
            return users.get(0);

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error reading local user file: " + e.getMessage(), e);
            return null;
        }
    }

    public SyncStatus createSyncStatus(String filename, Path localPath, Path networkPath) {
        // If local user is not initialized yet, try to load it
        if (localUser == null) {
            try {
                localUser = readLocalUser();
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Failed to read local user: " + e.getMessage(), e);
            }

            // If still null after initialization attempt, log warning
            if (localUser == null) {
                LoggerUtil.warn(this.getClass(),
                        "Cannot create sync status: No local user available. Filename: " + filename);
                return null;
            }
        }

        // Check if the file belongs to the local user
        boolean isLocalUserFile = isFileForLocalUser(filename);

        if (!isLocalUserFile) {
            LoggerUtil.debug(this.getClass(),
                    "Skipping sync status creation for non-local user file: " + filename);
            return null;
        }

        // Proceed with creating sync status for the local user's file
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);

        SyncStatus status = new SyncStatus(timeValues, localPath, networkPath);
        syncStatusMap.put(filename, status);

        LoggerUtil.info(this.getClass(), "Created sync status for local user file: " + filename);
        return status;
    }

    /**
     * Determines if a file belongs to the local user based on filename patterns
     */
    private boolean isFileForLocalUser(String filename) {
        if (localUser == null) {
            return false;
        }

        String username = localUser.getUsername();
        Integer userId = localUser.getUserId();

        // Check common filename patterns that would indicate this is for the local user
        // Adjust these patterns based on your actual file naming conventions
        if (filename.contains("_" + username + "_") ||
                filename.contains("session_" + username) ||
                filename.contains("status_" + username) ||
                (userId != null && filename.contains("_" + userId))) {
            return true;
        }

        // Special case for status flag files
        if (filename.startsWith("status_") && filename.endsWith(".flag")) {
            String fileUsername = extractUsernameFromStatusFlag(filename);
            return username.equals(fileUsername);
        }

        return false;
    }

    /**
     * Extract username from status flag filename
     * Status flags typically follow format: status_username_date_time_status.flag
     */
    private String extractUsernameFromStatusFlag(String filename) {
        // Remove "status_" prefix and split by underscore
        String withoutPrefix = filename.substring("status_".length());
        String[] parts = withoutPrefix.split("_");

        // First part should be the username
        if (parts.length > 0) {
            return parts[0];
        }

        return "";
    }

    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void processFailedSyncs() {
        // Make sure syncOperation is set
        if (syncOperation == null) {
            LoggerUtil.error(this.getClass(), "No sync operation implementation set!");
            return;
        }

        Set<String> failedFiles = getFailedSyncs();

        for (String filename : failedFiles) {
            SyncStatus status = syncStatusMap.get(filename);
            if (status != null && shouldRetry(status)) {
                // Only retry if we haven't exceeded max retries
                if (status.getRetryCount() < 5) {
                    retrySync(filename, status);
                } else {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Max retries exceeded for file: %s, marking as failed", filename));
                    status.setSyncPending(false);
                }
            }
        }
    }

    private boolean shouldRetry(SyncStatus status) {
        if (!status.isSyncPending()) {
            return false;
        }

        if (status.getLastAttempt() == null) {
            return true;
        }

        // Get current standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        LocalDateTime currentTime = timeValues.getCurrentTime();

        // Exponential backoff based on retry count
        long currentBackoff = retryDelay * (long)Math.pow(2, status.getRetryCount() - 1);
        // Cap the maximum delay at 1 hour
        long actualDelay = Math.min(currentBackoff, 3600000);

        LocalDateTime nextRetryTime = status.getLastAttempt().plusNanos(actualDelay * 1_000_000);
        return currentTime.isAfter(nextRetryTime);
    }

    private void retrySync(String filename, SyncStatus status) {
        if (pathConfig.isNetworkAvailable() && syncOperation != null) {
            int attempt = status.incrementRetryCount();
            LoggerUtil.info(this.getClass(), String.format("Retrying sync for file: %s (attempt #%d)", filename, attempt));

            // Update last attempt time with standardized time
            GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
            status.setLastAttempt(timeValues.getCurrentTime());

            boolean success = syncOperation.syncFile(status.getLocalPath(), status.getNetworkPath());

            if (success) {
                status.resetRetryCount();
                status.setSyncPending(false);
                status.setLastSuccessfulSync(timeValues.getCurrentTime());
                status.setErrorMessage(null);
            }
        }
    }

    public Set<String> getFailedSyncs() {
        return syncStatusMap.entrySet().stream()
                .filter(e -> e.getValue().isSyncPending() && e.getValue().getErrorMessage() != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @PreDestroy
    public void clearAll() {
        syncStatusMap.clear();
        LoggerUtil.info(this.getClass(), "Cleared all sync statuses");
    }
}