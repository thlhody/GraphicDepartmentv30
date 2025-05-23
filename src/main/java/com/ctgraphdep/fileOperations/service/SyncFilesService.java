package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.model.SyncMetadata;
import com.ctgraphdep.fileOperations.model.SyncStatus;
import com.ctgraphdep.fileOperations.model.SyncResult;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    private final Map<Path, ReentrantReadWriteLock> fileLocks = new ConcurrentHashMap<>();

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

                // Step 3: If all went well, delete the backup file
                Files.deleteIfExists(backupPath);
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
     * Synchronizes a file in both directions (bidirectional sync)
     * Only performs sync if files differ, using the most recent version
     * @param localPath The local file path
     * @param networkPath The network file path
     * @return The result of the sync operation
     */
    @Async
    public CompletableFuture<SyncResult> syncBidirectional(FilePath localPath, FilePath networkPath) {
        return CompletableFuture.supplyAsync(() -> {
            Path localFilePath = localPath.getPath();
            Path networkFilePath = networkPath.getPath();

            LoggerUtil.info(this.getClass(), String.format("Performing bidirectional sync between\nLocal: %s\nNetwork: %s",
                    localFilePath, networkFilePath));

            try {
                // Check if both files exist
                boolean localExists = Files.exists(localFilePath);
                boolean networkExists = Files.exists(networkFilePath);

                // Both files exist - compare timestamps to determine direction
                if (localExists && networkExists) {
                    return handleBothFilesExist(localPath, networkPath);
                }

                // Only local file exists - sync to network
                if (localExists) {
                    LoggerUtil.info(this.getClass(), "Only local file exists, syncing to network");
                    FileOperationResult result = syncToNetwork(localPath, networkPath).get();
                    return new SyncResult(result, SyncResult.Direction.LOCAL_TO_NETWORK);
                }

                // Only network file exists - sync to local
                if (networkExists) {
                    LoggerUtil.info(this.getClass(), "Only network file exists, syncing to local");
                    FileOperationResult result = syncToLocal(networkPath, localPath).get();
                    return new SyncResult(result, SyncResult.Direction.NETWORK_TO_LOCAL);
                }

                // Neither file exists - nothing to do
                LoggerUtil.warn(this.getClass(), "Neither file exists, nothing to sync");
                return new SyncResult(
                        FileOperationResult.failure(localFilePath, "Neither file exists"),
                        SyncResult.Direction.NONE
                );

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error during bidirectional sync: " + e.getMessage(), e);
                return new SyncResult(
                        FileOperationResult.failure(localFilePath, "Error during sync: " + e.getMessage(), e),
                        SyncResult.Direction.ERROR
                );
            }
        });
    }

    /**
     * Handles bidirectional sync when both files exist
     */
    private SyncResult handleBothFilesExist(FilePath localPath, FilePath networkPath) throws Exception {
        Path localFilePath = localPath.getPath();
        Path networkFilePath = networkPath.getPath();

        // Compare file sizes first for a quick check
        long localSize = Files.size(localFilePath);
        long networkSize = Files.size(networkFilePath);

        // If sizes differ, use modification timestamps to determine the newer file
        if (localSize != networkSize) {
            return syncBasedOnModificationTime(localPath, networkPath);
        }

        // If sizes are the same, compare content hashes
        byte[] localHash = calculateFileHash(localFilePath);
        byte[] networkHash = calculateFileHash(networkFilePath);

        // If content is identical, no sync needed
        if (Arrays.equals(localHash, networkHash)) {
            LoggerUtil.info(this.getClass(), "Files are identical, no sync needed");
            return new SyncResult(
                    FileOperationResult.success(localFilePath),
                    SyncResult.Direction.NONE
            );
        }

        // Content differs despite same size, use modification timestamps
        return syncBasedOnModificationTime(localPath, networkPath);
    }

    /**
     * Syncs files based on modification timestamp
     */
    private SyncResult syncBasedOnModificationTime(FilePath localPath, FilePath networkPath) throws Exception {
        Path localFilePath = localPath.getPath();
        Path networkFilePath = networkPath.getPath();

        // Get file modification times
        long localModTime = Files.getLastModifiedTime(localFilePath).toMillis();
        long networkModTime = Files.getLastModifiedTime(networkFilePath).toMillis();

        // Local file is newer
        if (localModTime > networkModTime) {
            LoggerUtil.info(this.getClass(), "Local file is newer, syncing to network");
            FileOperationResult result = syncToNetwork(localPath, networkPath).get();
            return new SyncResult(result, SyncResult.Direction.LOCAL_TO_NETWORK);
        }

        // Network file is newer
        if (networkModTime > localModTime) {
            LoggerUtil.info(this.getClass(), "Network file is newer, syncing to local");
            FileOperationResult result = syncToLocal(networkPath, localPath).get();
            return new SyncResult(result, SyncResult.Direction.NETWORK_TO_LOCAL);
        }

        // Times are identical - this is unusual but possible
        // Default to local-to-network in this case
        LoggerUtil.warn(this.getClass(), "Files have identical timestamps but different content, defaulting to local-to-network sync");
        FileOperationResult result = syncToNetwork(localPath, networkPath).get();
        return new SyncResult(result, SyncResult.Direction.LOCAL_TO_NETWORK);
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

                // If successful, delete the backup
                Path backupPath = backupService.getSimpleBackupPath(targetPath);
                if (Files.exists(backupPath)) {
                    Files.deleteIfExists(backupPath);
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
     * Reads a file from the network with proper error handling
     * @param networkPath The network file path to read
     *  typeRef The type reference for deserialization
     * @return The deserialized object, or empty if the file can't be read
     */
    public <T> Optional<T> readFromNetwork(FilePath networkPath, Class<T> typeClass) {
        if (!networkPath.isNetwork()) {
            LoggerUtil.warn(this.getClass(), "Not a network path: " + networkPath.getPath());
            return Optional.empty();
        }

        Path path = networkPath.getPath();
        Path backupPath = backupService.getSimpleBackupPath(path);

        // Try to acquire a read lock
        ReentrantReadWriteLock.ReadLock readLock = getFileLock(path).readLock();
        readLock.lock();

        try {
            // Try to read the main file first
            if (Files.exists(path) && Files.size(path) > 0) {
                try {
                    byte[] content = Files.readAllBytes(path);
                    T result = deserializeContent(content, typeClass);
                    LoggerUtil.debug(this.getClass(), "Successfully read network file: " + path);
                    return Optional.of(result);
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Error reading network file: " + e.getMessage());
                    // Continue to back up file
                }
            }

            // If main file doesn't exist or had errors, try the backup
            if (Files.exists(backupPath) && Files.size(backupPath) > 0) {
                try {
                    byte[] content = Files.readAllBytes(backupPath);
                    T result = deserializeContent(content, typeClass);
                    LoggerUtil.info(this.getClass(), "Successfully read network backup file: " + backupPath);
                    return Optional.of(result);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error reading network backup file: " + e.getMessage());
                }
            }

            // If we get here, neither file could be read
            return Optional.empty();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during network file read: " + e.getMessage(), e);
            return Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Helper method to deserialize content based on type
     */
    private <T> T deserializeContent(byte[] content, Class<T> typeClass) throws IOException {
        // Implement deserialization for different types based on your object mapper
        // For example with Jackson:
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(content, typeClass);
    }

    /**
     * Gets a lock for a specific file path
     * @param path The path to get a lock for
     * @return The lock for the path
     */
    private ReentrantReadWriteLock getFileLock(Path path) {
        return fileLocks.computeIfAbsent(path, k -> new ReentrantReadWriteLock());
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
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
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
     * Calculate a file hash for comparison purposes
     */
    private byte[] calculateFileHash(Path filePath) throws Exception {
        byte[] fileContent = Files.readAllBytes(filePath);
        // Use a hashing algorithm like SHA-256
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return digest.digest(fileContent);
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