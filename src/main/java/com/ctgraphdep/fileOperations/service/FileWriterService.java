package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.events.FileEventPublisher;
import com.ctgraphdep.service.cache.MainDefaultUserContextCache;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * COMPLETELY REFACTORED: Enhanced service for writing files with comprehensive locking,
 * retry mechanisms, and request deduplication to eliminate file access conflicts.
 * Key Features:
 * - File-level locking with timeout
 * - Exponential backoff retry for file conflicts
 * - Request deduplication to prevent rapid clicks
 * - Coordinated async operations
 * - Event-driven backup system integration
 */
@Service
public class FileWriterService {

    // === CORE DEPENDENCIES ===
    private final ObjectMapper objectMapper;
    private final FilePathResolver pathResolver;
    private final SyncFilesService syncService;
    private final PathConfig pathConfig;
    private final FileObfuscationService obfuscationService;
    private final FileEventPublisher fileEventPublisher;
    private final MainDefaultUserContextCache mainDefaultUserContextCache;

    // === FILE LOCKING SYSTEM ===
    // Per-file locks to prevent concurrent access to same file
    private final Map<Path, ReentrantReadWriteLock> fileLocks = new ConcurrentHashMap<>();
    private static final long FILE_LOCK_TIMEOUT_MS = 5000; // 5 seconds max wait for lock

    // === RETRY CONFIGURATION ===
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 500;  // Start with 500ms
    private static final long MAX_RETRY_DELAY_MS = 3000;     // Cap at 3 seconds

    // === REQUEST DEDUPLICATION ===
    // Prevent rapid clicks by tracking recent write attempts
    private final Map<String, Long> lastWriteAttempts = new ConcurrentHashMap<>();
    private static final long MIN_WRITE_INTERVAL_MS = 1000; // 1 second between writes per user/file

    // === ASYNC OPERATION TRACKING ===
    // Track ongoing async operations to coordinate with new requests
    private final Map<String, CompletableFuture<Void>> pendingSyncs = new ConcurrentHashMap<>();

    @Autowired
    public FileWriterService(
            ObjectMapper objectMapper,
            FilePathResolver pathResolver,
            SyncFilesService syncService,
            PathConfig pathConfig,
            FileObfuscationService obfuscationService,
            FileEventPublisher fileEventPublisher,
            MainDefaultUserContextCache mainDefaultUserContextCache) {
        this.objectMapper = objectMapper;
        this.pathResolver = pathResolver;
        this.syncService = syncService;
        this.pathConfig = pathConfig;
        this.obfuscationService = obfuscationService;
        this.fileEventPublisher = fileEventPublisher;
        this.mainDefaultUserContextCache = mainDefaultUserContextCache;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // PUBLIC API METHODS
    // ========================================================================

    /**
     * Primary write method with full protection against concurrent access conflicts.
     * This is the main entry point for all file write operations.
     *
     * @param filePath The file path to write to
     * @param data The data to write
     * @param skipObfuscation Whether to skip obfuscation (for user files)
     * @return The result of the operation
     */
    public <T> FileOperationResult writeFile(FilePath filePath, T data, boolean skipObfuscation) {
        return writeFileWithBackupControl(filePath, data, skipObfuscation, true);
    }

    /**
     * Write file with control over backup creation.
     *
     * @param filePath The file path to write to
     * @param data The data to write
     * @param skipObfuscation Whether to skip obfuscation
     * @param shouldCreateBackup Whether backup should be created via events
     * @return The result of the operation
     */
    public <T> FileOperationResult writeFileWithBackupControl(FilePath filePath, T data, boolean skipObfuscation, boolean shouldCreateBackup) {

        Path path = filePath.getPath();
        String username = getCurrentUsername();
        Integer userId = filePath.getUserId().orElse(null);

        // 1. REQUEST DEDUPLICATION - Prevent rapid clicks
        if (!canAttemptWrite(username, path)) {
            LoggerUtil.warn(this.getClass(), String.format("Write attempt too soon for user %s, file %s - ignoring duplicate request", username, path.getFileName()));

            // Return success to avoid UI errors from rapid clicking
            return FileOperationResult.success(path);
        }

        // 2. EXECUTE WITH RETRY AND LOCKING
        return executeWriteWithRetry(filePath, data, skipObfuscation, shouldCreateBackup, username, userId);
    }

    /**
     * Writes to local file and syncs to network if available.
     * Used by data services for operations that need network synchronization.
     *
     * @param localPath The local file path to write to
     * @param data The data to write
     * @param skipObfuscation Whether to skip obfuscation
     * @return The result of the write operation
     */
    public <T> FileOperationResult writeWithNetworkSync(FilePath localPath, T data, boolean skipObfuscation) {
        if (!localPath.isLocal()) {
            return FileOperationResult.failure(localPath.getPath(), "Path must be local for network sync operation");
        }

        // Write the file with backup enabled
        FileOperationResult writeResult = writeFileWithBackupControl(localPath, data, skipObfuscation, true);

        // If write successful, trigger async network sync
        if (writeResult.isSuccess() && isNetworkAvailable()) {
            triggerAsyncNetworkSync(localPath, getCurrentUsername());
        }

        return writeResult;
    }

    /**
     * Writes to local file and syncs to network if available.
     * Used by data services for operations that need network synchronization.
     *
     * @param localPath The local file path to write to
     * @param data The data to write
     * @param skipObfuscation Whether to skip obfuscation
     * @return The result of the write operation
     */
    public <T> FileOperationResult writeWithNetworkSyncNoBackup(FilePath localPath, T data, boolean skipObfuscation) {
        if (!localPath.isLocal()) {
            return FileOperationResult.failure(localPath.getPath(), "Path must be local for network sync operation");
        }

        // Write the file with backup enabled
        FileOperationResult writeResult = writeFileWithBackupControl(localPath, data, skipObfuscation, false);

        // If write successful, trigger async network sync
        if (writeResult.isSuccess() && isNetworkAvailable()) {
            triggerAsyncNetworkSync(localPath, getCurrentUsername());
        }

        return writeResult;
    }


    // ========================================================================
    // CORE IMPLEMENTATION WITH LOCKING AND RETRY
    // ========================================================================

    /**
     * Execute write operation with comprehensive retry logic and file locking.
     */
    private <T> FileOperationResult executeWriteWithRetry(FilePath filePath, T data,
                                                          boolean skipObfuscation, boolean shouldCreateBackup, String username, Integer userId) {

        Path path = filePath.getPath();
        Exception lastException = null;

        // Try operation with exponential backoff retry
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    LoggerUtil.info(this.getClass(), String.format(
                            "Retry #%d for file write: %s (user: %s)", attempt, path.getFileName(), username));
                }

                // Execute the actual write with locking
                FileOperationResult result = executeLockedWrite(filePath, data, skipObfuscation,
                        shouldCreateBackup, username, userId);

                if (result.isSuccess()) {
                    // Record successful write attempt
                    recordWriteAttempt(username, path);

                    if (attempt > 0) {
                        LoggerUtil.info(this.getClass(), String.format(
                                "Write succeeded on retry #%d for file: %s", attempt, path.getFileName()));
                    }

                    return result;
                }

                // If write failed but wasn't a file conflict, don't retry
                if (!isRetriableError(result)) {
                    return result;
                }

                lastException = result.getException().orElse(null);

            } catch (Exception e) {
                lastException = e;

                // Only retry on file access errors
                if (!isFileAccessError(e)) {
                    break;
                }
            }

            // Calculate delay for next retry (exponential backoff)
            if (attempt < MAX_RETRIES - 1) {
                long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << attempt), MAX_RETRY_DELAY_MS);

                LoggerUtil.warn(this.getClass(), String.format(
                        "File access conflict detected, retrying in %dms (attempt %d/%d): %s",
                        delay, attempt + 1, MAX_RETRIES,
                        lastException != null ? lastException.getMessage() : "Unknown error"));

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // All retries failed
        LoggerUtil.error(this.getClass(), String.format(
                "Failed to write file after %d retries: %s", MAX_RETRIES, path.getFileName()), lastException);

        return FileOperationResult.failure(path,
                "Failed to write file after retries: " +
                        (lastException != null ? lastException.getMessage() : "Unknown error"),
                lastException);
    }

    /**
     * Execute write operation with file locking protection.
     */
    private <T> FileOperationResult executeLockedWrite(FilePath filePath, T data,
                                                       boolean skipObfuscation, boolean shouldCreateBackup, String username, Integer userId) {

        Path path = filePath.getPath();
        long operationStartTime = System.currentTimeMillis();

        LoggerUtil.info(this.getClass(), String.format(
                "Starting file write operation: %s (user: %s, backup: %s)",
                path.getFileName(), username, shouldCreateBackup));

        // Publish write start event
        fileEventPublisher.publishFileWriteStart(filePath, username, userId, shouldCreateBackup, data);

        // Get file-specific lock
        ReentrantReadWriteLock.WriteLock writeLock = getFileLock(path).writeLock();

        try {
            // Try to acquire write lock with timeout
            if (!writeLock.tryLock(FILE_LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Could not acquire file lock within timeout: " + path.getFileName());
            }

            try {
                // Perform the actual file write operation
                return performFileWrite(filePath, data, skipObfuscation, shouldCreateBackup,
                        username, userId, operationStartTime);

            } finally {
                writeLock.unlock();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - operationStartTime;

            FileOperationResult result = FileOperationResult.failure(path, "Write operation interrupted", e);
            fileEventPublisher.publishFileWriteFailure(filePath, username, userId, shouldCreateBackup, result, e, duration);
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - operationStartTime;

            FileOperationResult result = FileOperationResult.failure(path, "Write operation failed: " + e.getMessage(), e);
            fileEventPublisher.publishFileWriteFailure(filePath, username, userId, shouldCreateBackup, result, e, duration);
            return result;
        }
    }

    /**
     * Perform the actual file write operation (called while holding lock).
     */
    private <T> FileOperationResult performFileWrite(FilePath filePath, T data,
                                                     boolean skipObfuscation, boolean shouldCreateBackup, String username, Integer userId,
                                                     long operationStartTime) throws Exception {

        Path path = filePath.getPath();

        // Create parent directories if needed
        Files.createDirectories(path.getParent());

        // Serialize data to JSON
        byte[] content = objectMapper.writeValueAsBytes(data);

        // Apply obfuscation if requested
        if (!skipObfuscation) {
            content = obfuscationService.obfuscate(content);
        }

        // Write to file
        Files.write(path, content);

        // Calculate operation duration
        long operationDuration = System.currentTimeMillis() - operationStartTime;

        LoggerUtil.info(this.getClass(), String.format(
                "Successfully wrote file: %s (duration: %dms)", path, operationDuration));

        // Create success result
        FileOperationResult result = FileOperationResult.success(path);

        // Publish success event - this triggers backup creation
        fileEventPublisher.publishFileWriteSuccess(filePath, username, userId, shouldCreateBackup, result, operationDuration);

        return result;
    }

    // ========================================================================
    // ASYNC NETWORK SYNC COORDINATION
    // ========================================================================

    /**
     * Trigger async network sync without blocking current operation.
     * Coordinates with ongoing sync operations to avoid conflicts.
     */
    private void triggerAsyncNetworkSync(FilePath localPath, String username) {
        String syncKey = username + ":" + localPath.getPath().getFileName().toString();

        // Check if sync is already in progress for this file
        CompletableFuture<Void> existingSync = pendingSyncs.get(syncKey);
        if (existingSync != null && !existingSync.isDone()) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Network sync already in progress for: %s - skipping duplicate sync request", localPath.getPath().getFileName()));
            return;
        }

        // IMPORTANT: Create and register the future BEFORE starting async work
        // This prevents race condition where two threads both see no pending sync
        CompletableFuture<Void> syncFuture = new CompletableFuture<>();

        // Atomically register the sync - if another thread already registered, use theirs
        CompletableFuture<Void> registeredFuture = pendingSyncs.putIfAbsent(syncKey, syncFuture);
        if (registeredFuture != null) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Network sync already registered for: %s - skipping duplicate", localPath.getPath().getFileName()));
            return;
        }

        // Now start the actual async operation
        CompletableFuture.runAsync(() -> {
            try {
                // Small delay to ensure local write is completely finished
                Thread.sleep(200);

                FilePath networkPath = pathResolver.toNetworkPath(localPath);

                // Perform the sync
                syncService.syncToNetwork(localPath, networkPath);

                // Mark as successfully completed
                syncFuture.complete(null);

            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Async network sync failed for %s: %s", localPath.getPath().getFileName(), e.getMessage()));
                // Mark as failed
                syncFuture.completeExceptionally(e);
            }
        });

        // Clean up completed sync after it finishes
        syncFuture.whenComplete((result, throwable) -> {
            pendingSyncs.remove(syncKey);

            // Cleanup old sync records periodically
            if (pendingSyncs.size() > 50) {
                cleanupCompletedSyncs();
            }
        });

        LoggerUtil.info(this.getClass(), String.format(
                "Network sync initiated for: %s", localPath.getPath().getFileName()));
    }

    /**
     * Clean up completed sync operations from tracking map.
     */
    private void cleanupCompletedSyncs() {
        pendingSyncs.entrySet().removeIf(entry -> entry.getValue().isDone());
    }

    // ========================================================================
    // REQUEST DEDUPLICATION SYSTEM
    // ========================================================================

    /**
     * Check if write attempt is allowed (prevents rapid clicks).
     */
    private boolean canAttemptWrite(String username, Path path) {
        String key = username + ":" + path.getFileName().toString();
        Long lastAttempt = lastWriteAttempts.get(key);

        if (lastAttempt == null) {
            return true;
        }

        long timeSinceLastAttempt = System.currentTimeMillis() - lastAttempt;
        return timeSinceLastAttempt >= MIN_WRITE_INTERVAL_MS;
    }

    /**
     * Record write attempt timestamp for deduplication.
     */
    private void recordWriteAttempt(String username, Path path) {
        String key = username + ":" + path.getFileName().toString();
        lastWriteAttempts.put(key, System.currentTimeMillis());

        // Periodic cleanup of old entries
        if (lastWriteAttempts.size() > 100) {
            cleanupOldWriteAttempts();
        }
    }

    /**
     * Cleanup old write attempt records to prevent memory leaks.
     */
    private void cleanupOldWriteAttempts() {
        long cutoff = System.currentTimeMillis() - (MIN_WRITE_INTERVAL_MS * 10); // 10 seconds ago
        lastWriteAttempts.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    // ========================================================================
    // FILE LOCKING SYSTEM
    // ========================================================================

    /**
     * Get file-specific lock (similar to SyncFilesService pattern).
     */
    private ReentrantReadWriteLock getFileLock(Path path) {
        return fileLocks.computeIfAbsent(path.normalize(), k -> new ReentrantReadWriteLock());
    }

    // ========================================================================
    // ERROR HANDLING AND DETECTION
    // ========================================================================

    /**
     * Check if exception indicates a file access conflict that should be retried.
     */
    private boolean isFileAccessError(Exception e) {
        if (e instanceof FileSystemException) {
            return true;
        }

        String message = e.getMessage();
        if (message == null) return false;

        return message.contains("process cannot access the file") ||
                message.contains("being used by another process") ||
                message.contains("FileSystemException") ||
                message.contains("Access is denied");
    }

    /**
     * Check if operation result indicates a retriable error.
     */
    private boolean isRetriableError(FileOperationResult result) {
        if (result.isSuccess()) {
            return false;
        }

        Exception exception = result.getException().orElse(null);
        return exception != null && isFileAccessError(exception);
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get current authenticated username using the MainDefaultUserContextCache directly.
     * This works for both web requests and background tasks and avoids circular dependencies.
     */
    private String getCurrentUsername() {
        try {
            String username = mainDefaultUserContextCache.getCurrentUsername();
            LoggerUtil.debug(this.getClass(), "Current username resolved to: " + username);
            return username;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting current username: " + e.getMessage(), e);
            return "system"; // Final fallback
        }
    }

    /**
     * Check if network is available for sync operations.
     */
    private boolean isNetworkAvailable() {
        return pathConfig.isNetworkAvailable();
    }
}