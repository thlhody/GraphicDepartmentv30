package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Enhanced service for writing files with proper locking, backup, and criticality levels.
 */
@Service
public class FileWriterService {
    private final ObjectMapper objectMapper;
    private final FilePathResolver pathResolver;
    private final BackupService backupService;
    private final SyncFilesService syncService;
    private final PathConfig pathConfig;
    private final FileObfuscationService obfuscationService;

    @Autowired
    public FileWriterService(
            ObjectMapper objectMapper,
            FilePathResolver pathResolver,
            BackupService backupService,
            SyncFilesService syncService,
            PathConfig pathConfig,
            FileObfuscationService obfuscationService) {
        this.objectMapper = objectMapper;
        this.pathResolver = pathResolver;
        this.backupService = backupService;
        this.syncService = syncService;
        this.pathConfig = pathConfig;
        this.obfuscationService = obfuscationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Determines the criticality level of a file based on its path and type
     * @param filePath The file path
     * @return The appropriate criticality level
     */
    private BackupService.CriticalityLevel determineCriticalityLevel(FilePath filePath) {
        Path path = filePath.getPath();
        String pathStr = path.toString().toLowerCase();
        String fileName = path.getFileName().toString().toLowerCase();

        LoggerUtil.debug(this.getClass(), "Determining criticality for: " + pathStr);

        // LEVEL1_LOW - Status files, temporary files
        if (pathStr.contains("status") || fileName.startsWith("status_") ||
                pathStr.contains("temp") || pathStr.contains("cache")) {
            return BackupService.CriticalityLevel.LEVEL1_LOW;
        }

        // LEVEL3_HIGH - Critical user and business data
        if (pathStr.contains("worktime") || pathStr.contains("registru") ||
                pathStr.contains("register") || pathStr.contains("timeoff") ||
                (pathStr.contains("user") && !pathStr.contains("session")) ||
                pathStr.contains("check") || pathStr.contains("bonus") ||
                fileName.contains("worktime") || fileName.contains("registru") ||
                fileName.contains("register") || fileName.contains("timeoff")) {
            LoggerUtil.info(this.getClass(), "High criticality file detected: " + pathStr);
            return BackupService.CriticalityLevel.LEVEL3_HIGH;
        }

        // LEVEL2_MEDIUM - Session files and everything else
        if (pathStr.contains("session") || fileName.contains("session") ||
                pathStr.contains("team")) {
            return BackupService.CriticalityLevel.LEVEL2_MEDIUM;
        }

        // Default to medium criticality for unknown files
        return BackupService.CriticalityLevel.LEVEL2_MEDIUM;
    }
    /**
     * Writes data to a file with proper locking and backup based on criticality level
     * This is the primary method used by the application
     * @param filePath The file path to write to
     * @param data The data to write
     * @param skipObfuscation Whether to skip obfuscation (for user files)
     * @return The result of the operation
     */
    public <T> FileOperationResult writeFile(FilePath filePath, T data, boolean skipObfuscation) {
        Path path = filePath.getPath();

        // Determine criticality level for this file
        BackupService.CriticalityLevel criticalityLevel = determineCriticalityLevel(filePath);

        // Log the determined criticality level
        LoggerUtil.info(this.getClass(), String.format(
                "Writing file %s with criticality level %s", path, criticalityLevel));

        // Acquire write lock
        ReentrantReadWriteLock.WriteLock writeLock = pathResolver.getLock(filePath).writeLock();
        writeLock.lock();

        try {
            // Create backup based on criticality level if file exists
            if (Files.exists(path)) {
                FileOperationResult backupResult = backupService.createBackup(filePath, criticalityLevel);
                if (!backupResult.isSuccess()) {
                    LoggerUtil.warn(this.getClass(), "Failed to create backup: " +
                            backupResult.getErrorMessage().orElse("Unknown error"));
                } else {
                    LoggerUtil.info(this.getClass(), "Successfully created backup with criticality level " +
                            criticalityLevel);
                }
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

                // For low criticality files, delete backup after successful write
                if (criticalityLevel == BackupService.CriticalityLevel.LEVEL1_LOW) {
                    backupService.deleteSimpleBackup(filePath);
                }

                return FileOperationResult.success(path);
            } catch (Exception e) {
                // Restore from backup if write fails
                try {
                    // For low/medium criticality, use simple backup
                    if (criticalityLevel != BackupService.CriticalityLevel.LEVEL3_HIGH) {
                        backupService.restoreFromSimpleBackup(filePath);
                    } else {
                        // For high criticality, use latest backup
                        backupService.restoreFromLatestBackup(filePath, criticalityLevel);
                    }
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

    /**
     * This methods writes locally and syncs to network if needed
     * Used by DataAccessService
     */
    public <T> FileOperationResult writeWithNetworkSync(FilePath localPath, T data, boolean skipObfuscation) {
        if (!localPath.isLocal()) {
            return FileOperationResult.failure(localPath.getPath(), "Not a local path");
        }

        // Direct write
        FileOperationResult writeResult = writeFile(localPath, data, skipObfuscation);

        // Sync to network if requested and write was successful
        if (writeResult.isSuccess() && isNetworkAvailable()) {
            try {
                FilePath networkPath = pathResolver.toNetworkPath(localPath);
                syncService.syncToNetwork(localPath, networkPath);

                // For high criticality files, also sync backups to network
                BackupService.CriticalityLevel criticalityLevel = determineCriticalityLevel(localPath);
                if (criticalityLevel == BackupService.CriticalityLevel.LEVEL3_HIGH) {
                    // Get current username
                    String username = SecurityContextHolder.getContext().getAuthentication().getName();
                    backupService.syncBackupsToNetwork(username, criticalityLevel);
                    LoggerUtil.info(this.getClass(), "Synced high criticality backups to network for " + username);
                }
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Failed to sync to network: " + e.getMessage(), e);
                // Don't fail the operation if sync fails - it will be retried later
            }
        }

        return writeResult;
    }
}