package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service for writing files with proper locking and backup.
 */
@Service
public class FileWriterService {
    private final ObjectMapper objectMapper;
    private final FilePathResolver pathResolver;
    private final BackupService backupService;
    private final SyncFilesService syncService;
    private final FileTransactionManager transactionManager;
    private final PathConfig pathConfig;
    private final FileObfuscationService obfuscationService;

    public FileWriterService(
            ObjectMapper objectMapper,
            FilePathResolver pathResolver,
            BackupService backupService,
            SyncFilesService syncService,
            FileTransactionManager transactionManager,
            PathConfig pathConfig,
            FileObfuscationService obfuscationService) {
        this.objectMapper = objectMapper;
        this.pathResolver = pathResolver;
        this.backupService = backupService;
        this.syncService = syncService;
        this.transactionManager = transactionManager;
        this.pathConfig = pathConfig;
        this.obfuscationService = obfuscationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Writes data to a file with proper locking and backup
     * @param filePath The file path to write to
     * @param data The data to write
     * @param skipObfuscation Whether to skip obfuscation (for user files)
     * @return The result of the operation
     */
    public <T> FileOperationResult writeFile(FilePath filePath, T data, boolean skipObfuscation) {
        Path path = filePath.getPath();

        // Acquire write lock
        ReentrantReadWriteLock.WriteLock writeLock = pathResolver.getLock(filePath).writeLock();
        writeLock.lock();

        try {
            // Create backup first if file exists
            if (Files.exists(path)) {
                backupService.createBackup(filePath);
            }

            // Create parent directories if needed
            Files.createDirectories(path.getParent());

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(data);

            // Apply obfuscation if needed
            if (!skipObfuscation) {
                content = obfuscationService.obfuscate(content);
            }

            try {
                // Write to file
                Files.write(path, content);
                LoggerUtil.info(this.getClass(), "Successfully wrote to file: " + path);

                // Delete backup after successful write
                backupService.deleteBackup(filePath);

                return FileOperationResult.success(path);
            } catch (Exception e) {
                // Restore from backup if write fails
                try {
                    backupService.restoreFromBackup(filePath);
                    LoggerUtil.warn(this.getClass(), "Successfully restored from backup after write failure");
                } catch (Exception re) {
                    LoggerUtil.error(this.getClass(), "Failed to restore from backup: " + re.getMessage(), re);
                }

                return FileOperationResult.failure(path, "Failed to write file: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error writing file: " + path, e);
            return FileOperationResult.failure(path, "Error writing file: " + e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Writes data to a local file with optional sync to network
     * @param localPath The local file path to write to
     * @param data The data to write
     * @param skipObfuscation Whether to skip obfuscation (for user files)
     * @param syncToNetwork Whether to sync the file to the network
     * @return The result of the operation
     */
    public <T> FileOperationResult writeLocalFile(FilePath localPath, T data, boolean skipObfuscation, boolean syncToNetwork) {
        if (!localPath.isLocal()) {
            return FileOperationResult.failure(localPath.getPath(), "Not a local path");
        }

        // Check if we're in a transaction
        boolean hasTransaction = transactionManager.getCurrentTransaction().isActive();

        if (hasTransaction) {
            // Add to transaction
            try {
                byte[] content = objectMapper.writeValueAsBytes(data);
                if (!skipObfuscation) {
                    content = obfuscationService.obfuscate(content);
                }

                transactionManager.getCurrentTransaction().addWrite(localPath, content);

                if (syncToNetwork && isNetworkAvailable()) {
                    FilePath networkPath = pathResolver.toNetworkPath(localPath);
                    transactionManager.getCurrentTransaction().addSync(localPath, networkPath);
                }

                return FileOperationResult.success(localPath.getPath());
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error adding file operation to transaction: " + e.getMessage(), e);
                return FileOperationResult.failure(localPath.getPath(), "Transaction error: " + e.getMessage(), e);
            }
        } else {
            // Direct write
            FileOperationResult writeResult = writeFile(localPath, data, skipObfuscation);

            // Sync to network if requested and write was successful
            if (syncToNetwork && writeResult.isSuccess() && isNetworkAvailable()) {
                try {
                    FilePath networkPath = pathResolver.toNetworkPath(localPath);
                    syncService.syncToNetwork(localPath, networkPath);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Failed to sync to network: " + e.getMessage(), e);
                    // Don't fail the operation if sync fails - it will be retried later
                }
            }

            return writeResult;
        }
    }

    /**
     * Writes data to a network file
     * @param networkPath The network file path to write to
     * @param data The data to write
     * @param skipObfuscation Whether to skip obfuscation (for user files)
     * @return The result of the operation
     */
    public <T> FileOperationResult writeNetworkFile(FilePath networkPath, T data, boolean skipObfuscation) {
        if (!networkPath.isNetwork()) {
            return FileOperationResult.failure(networkPath.getPath(), "Not a network path");
        }

        // Check if network is available
        if (!isNetworkAvailable()) {
            return FileOperationResult.failure(networkPath.getPath(), "Network not available");
        }

        // Check if we're in a transaction
        boolean hasTransaction = transactionManager.getCurrentTransaction().isActive();

        if (hasTransaction) {
            // Add to transaction
            try {
                byte[] content = objectMapper.writeValueAsBytes(data);
                if (!skipObfuscation) {
                    content = obfuscationService.obfuscate(content);
                }

                transactionManager.getCurrentTransaction().addWrite(networkPath, content);
                return FileOperationResult.success(networkPath.getPath());
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error adding file operation to transaction: " + e.getMessage(), e);
                return FileOperationResult.failure(networkPath.getPath(), "Transaction error: " + e.getMessage(), e);
            }
        } else {
            // Direct write
            return writeFile(networkPath, data, skipObfuscation);
        }
    }

    /**
     * Checks if the current user has permission to write to a file
     * @param filePath The file path
     * @return True if the user has permission
     */
    public boolean hasWritePermission(FilePath filePath) {
        // Get current user
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Users can always write their own files
        return filePath.getUsername()
                .map(username -> username.equals(currentUsername))
                .orElse(false);
    }

    /**
     * Checks if the network is available
     * @return True if the network is available
     */
    private boolean isNetworkAvailable() {
        return pathConfig.isNetworkAvailable();
    }
}