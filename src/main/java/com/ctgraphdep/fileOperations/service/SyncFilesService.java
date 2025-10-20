package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.model.SyncMetadata;
import com.ctgraphdep.fileOperations.model.SyncStatus;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service for file synchronization operations between local and network storage.
 */
@Service
public class SyncFilesService {
    @Value("${app.sync.retry.max:3}")
    private int maxRetries;

    @Value("${app.sync.retry.delay:3600000}")
    private long retryDelay; // Default 1 hour

    @Value("${app.sync.metadata.path:sync_metadata}")
    private String metadataPath;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final BackupService backupService;
    private final TimeValidationService timeValidationService;
    private final FilePathResolver pathResolver;
    private final Map<String, SyncStatus> syncStatusMap = new ConcurrentHashMap<>();

    public SyncFilesService(
            BackupService backupService,
            TimeValidationService timeValidationService,
            FilePathResolver pathResolver) {
        this.backupService = backupService;
        this.timeValidationService = timeValidationService;
        this.pathResolver = pathResolver;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Synchronizes a file from local to network storage
     * @param localPath The local file path
     * @param networkPath The network file path
     * @return The result of the operation
     */
    @Async
    public CompletableFuture<FileOperationResult> syncToNetwork(FilePath localPath, FilePath networkPath) {
        return CompletableFuture.supplyAsync(() -> {
            if (!localPath.isLocal() || !networkPath.isNetwork()) {
                return FileOperationResult.failure(
                        localPath.getPath(),
                        "Invalid path types for sync: local=" + localPath.isLocal() + ", network=" + networkPath.isNetwork()
                );
            }

            Path sourcePath = localPath.getPath();
            Path targetPath = networkPath.getPath();

            LoggerUtil.info(this.getClass(), String.format("Syncing file\nFrom: %s\nTo: %s", sourcePath, targetPath));

            // Create or update sync status
            SyncStatus status = getOrCreateSyncStatus(sourcePath, targetPath);
            status.setSyncInProgress(true);

            try {
                // Ensure network parent directory exists
                Files.createDirectories(targetPath.getParent());

                // Step 1: First write the local file as a backup on the network
                Path backupPath = backupService.getSimpleBackupPath(targetPath);
                Files.copy(sourcePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                LoggerUtil.debug(this.getClass(), "Created backup on network: " + backupPath);

                // Step 2: Then replace the actual network file
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LoggerUtil.debug(this.getClass(), "Updated main file on network: " + targetPath);

                // Step 3: If all went well, try to delete the backup file
                // Use retry logic because Windows may lock the file briefly
                deleteBackupWithRetry(backupPath);
                LoggerUtil.info(this.getClass(), "File sync completed successfully");

                // Update sync status with success
                updateSyncStatusSuccess(status);

                // Store metadata about the sync
                storeSyncMetadata(localPath, networkPath, true, null);

                return FileOperationResult.success(targetPath);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Failed to sync file: %s", e.getMessage()), e);

                // Update status with error
                updateSyncStatusFailure(status, e.getMessage());

                // Store metadata about the failed sync
                storeSyncMetadata(localPath, networkPath, false, e.getMessage());

                return FileOperationResult.failure(targetPath, "Failed to sync file: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Synchronizes a file from network to local storage
     * @param networkPath The network file path
     * @param localPath The local file path
     * @return The result of the operation
     */
    @Async
    public CompletableFuture<FileOperationResult> syncToLocal(FilePath networkPath, FilePath localPath) {
        return CompletableFuture.supplyAsync(() -> {
            if (!networkPath.isNetwork() || !localPath.isLocal()) {
                return FileOperationResult.failure(
                        localPath.getPath(),
                        "Invalid path types for sync: network=" + networkPath.isNetwork() + ", local=" + localPath.isLocal()
                );
            }

            Path sourcePath = networkPath.getPath();
            Path targetPath = localPath.getPath();

            LoggerUtil.info(this.getClass(), String.format("Syncing file from network to local\nFrom: %s\nTo: %s",
                    sourcePath, targetPath));

            try {
                // Ensure local parent directory exists
                Files.createDirectories(targetPath.getParent());

                // Create local backup first
                if (Files.exists(targetPath)) {
                    Path backupPath = backupService.getSimpleBackupPath(targetPath);
                    Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                    LoggerUtil.debug(this.getClass(), "Created local backup: " + backupPath);
                }

                // Copy network file to local
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                LoggerUtil.debug(this.getClass(), "Updated local file: " + targetPath);

                // If successful, try to delete the backup with retry logic
                Path backupPath = backupService.getSimpleBackupPath(targetPath);
                if (Files.exists(backupPath)) {
                    deleteBackupWithRetry(backupPath);
                }

                LoggerUtil.info(this.getClass(), "Network to local file sync completed successfully");

                // Store metadata about the sync
                storeSyncMetadata(networkPath, localPath, true, null);

                return FileOperationResult.success(targetPath);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        String.format("Failed to sync from network to local: %s", e.getMessage()), e);

                // Try to restore from backup if exists
                try {
                    Path backupPath = backupService.getSimpleBackupPath(targetPath);
                    if (Files.exists(backupPath)) {
                        Files.copy(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        LoggerUtil.info(this.getClass(), "Restored local file from backup after failed sync");
                    }
                } catch (Exception be) {
                    LoggerUtil.error(this.getClass(), "Failed to restore from backup: " + be.getMessage(), be);
                }

                // Store metadata about the failed sync
                storeSyncMetadata(networkPath, localPath, false, e.getMessage());

                return FileOperationResult.failure(targetPath, "Failed to sync from network: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Periodically retry failed syncs
     */
    @Scheduled(fixedRateString = "${app.sync.retry.interval:3600000}")
    public void retryFailedSyncs() {
        List<SyncStatus> failedSyncs = syncStatusMap.values().stream()
                .filter(s -> s.isSyncPending() && !s.isSyncInProgress())
                .filter(this::shouldRetrySync)
                .toList();

        if (!failedSyncs.isEmpty()) {
            LoggerUtil.info(this.getClass(), "Retrying " + failedSyncs.size() + " failed sync operations");

            for (SyncStatus status : failedSyncs) {
                retrySync(status);
            }
        }
    }

    /**
     * Determine if a sync should be retried based on retry count and time
     */
    private boolean shouldRetrySync(SyncStatus status) {
        // Don't retry if max retries exceeded
        if (status.getRetryCount() >= maxRetries) {
            return false;
        }

        LocalDateTime lastAttempt = status.getLastAttempt();
        if (lastAttempt == null) {
            return true; // No previous attempt, should retry
        }

        // Get current time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        LocalDateTime currentTime = timeValues.getCurrentTime();

        // Calculate exponential backoff delay
        long currentBackoff = retryDelay * (long)Math.pow(2, status.getRetryCount() - 1);
        // Cap at 1 hour
        long actualDelay = Math.min(currentBackoff, 3600000);

        // Convert milliseconds to nanoseconds for LocalDateTime
        LocalDateTime nextRetryTime = lastAttempt.plusNanos(actualDelay * 1_000_000);

        return currentTime.isAfter(nextRetryTime);
    }

    /**
     * Retry a specific sync operation
     */
    private void retrySync(SyncStatus status) {
        status.incrementRetryCount();
        LoggerUtil.info(this.getClass(), "Retrying sync (attempt #" + status.getRetryCount() + "): "
                + status.getSourcePath() + " -> " + status.getTargetPath());

        // Update last attempt time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        status.setLastAttempt(timeValues.getCurrentTime());

        try {
            FilePath sourcePath = pathResolver.resolve(status.getSourcePath());
            FilePath targetPath = pathResolver.resolve(status.getTargetPath());

            CompletableFuture<FileOperationResult> future = syncToNetwork(sourcePath, targetPath);
            future.thenAccept(result -> {
                if (result.isSuccess()) {
                    status.resetRetryCount();
                    status.setSyncPending(false);
                    status.setLastSuccessfulSync(timeValues.getCurrentTime());
                    status.setErrorMessage(null);
                    LoggerUtil.info(this.getClass(), "Retry sync succeeded: " + status.getSourcePath());
                } else {
                    LoggerUtil.warn(this.getClass(), "Retry sync failed: " + result.getErrorMessage().orElse("Unknown error"));
                }
            });
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during sync retry: " + e.getMessage(), e);
        }
    }

    /**
     * Creates or retrieves a sync status for a file
     */
    private SyncStatus getOrCreateSyncStatus(Path sourcePath, Path targetPath) {
        String key = sourcePath.toString() + "|" + targetPath.toString();
        return syncStatusMap.computeIfAbsent(key, k -> {
            // Create new status
            GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                    .createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);

            SyncStatus status = new SyncStatus();
            status.setSourcePath(sourcePath.toString());
            status.setTargetPath(targetPath.toString());
            status.setCreatedTime(timeValues.getCurrentTime());
            status.setLastAttempt(timeValues.getCurrentTime());

            LoggerUtil.debug(this.getClass(), "Created new sync status for: " + sourcePath);
            return status;
        });
    }

    /**
     * Updates a sync status after successful sync
     */
    private void updateSyncStatusSuccess(SyncStatus status) {
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);

        status.setSyncInProgress(false);
        status.setSyncPending(false);
        status.setLastSuccessfulSync(timeValues.getCurrentTime());
        status.resetRetryCount();
        status.setErrorMessage(null);
    }

    /**
     * Updates a sync status after failed sync
     */
    private void updateSyncStatusFailure(SyncStatus status, String errorMessage) {
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);

        status.setSyncInProgress(false);
        status.setSyncPending(true);
        status.setLastAttempt(timeValues.getCurrentTime());
        status.setErrorMessage(errorMessage);
    }

    /**
     * Stores metadata about a sync operation
     */
    private void storeSyncMetadata(FilePath sourcePath, FilePath targetPath, boolean success, String errorMessage) {
        try {
            SyncMetadata metadata = new SyncMetadata();
            metadata.setSourcePath(sourcePath.getPath().toString());
            metadata.setTargetPath(targetPath.getPath().toString());
            metadata.setSuccess(success);
            metadata.setErrorMessage(errorMessage);

            GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                    .createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
            metadata.setTimestamp(timeValues.getCurrentTime());

            // Store metadata for reporting and analytics
            // Implementation depends on your storage mechanism
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error storing sync metadata: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a backup file with retry logic to handle Windows file locking issues
     * @param backupPath The path to the backup file to delete
     */
    private void deleteBackupWithRetry(Path backupPath) {
        int maxAttempts = 3;
        long waitTimeMs = 100; // Start with 100ms wait

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (Files.deleteIfExists(backupPath)) {
                    LoggerUtil.debug(this.getClass(), "Successfully deleted backup file: " + backupPath);
                    return;
                }
                // File doesn't exist, which is fine
                return;
            } catch (FileSystemException e) {
                if (attempt < maxAttempts) {
                    // Windows file locking - wait and retry
                    LoggerUtil.debug(this.getClass(),
                        String.format("Backup deletion attempt %d/%d failed, retrying in %dms: %s",
                            attempt, maxAttempts, waitTimeMs, backupPath));
                    try {
                        Thread.sleep(waitTimeMs);
                        waitTimeMs *= 2; // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LoggerUtil.warn(this.getClass(), "Backup deletion retry interrupted: " + backupPath);
                        return;
                    }
                } else {
                    // Last attempt failed - log as warning but don't fail the sync
                    LoggerUtil.warn(this.getClass(),
                        String.format("Could not delete backup file after %d attempts (file may be locked): %s",
                            maxAttempts, backupPath));
                }
            } catch (IOException e) {
                // Other IO errors - log and continue
                LoggerUtil.warn(this.getClass(),
                    "Error deleting backup file (continuing anyway): " + e.getMessage());
                return;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LoggerUtil.info(this.getClass(), "File sync service scheduler shutdown completed");
    }
}