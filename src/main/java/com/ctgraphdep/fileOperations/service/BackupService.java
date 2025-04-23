package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Service for handling file backup operations.
 */
@Service
public class BackupService {
    @Value("${app.backup.extension:.bak}")
    private String backupExtension;

    /**
     * Creates a backup of a file
     * @param originalPath The file to back up
     * @return The result of the operation
     */
    public FileOperationResult createBackup(FilePath originalPath) {
        Path path = originalPath.getPath();
        if (!Files.exists(path)) {
            LoggerUtil.warn(this.getClass(), "Cannot create backup - original file does not exist: " + path);
            return FileOperationResult.failure(path, "Original file does not exist");
        }

        try {
            Path backupPath = getBackupPath(path);
            Files.createDirectories(backupPath.getParent());
            Files.copy(path, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info(this.getClass(), "Created backup: " + backupPath);
            return FileOperationResult.success(backupPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to create backup for %s: %s", path, e.getMessage()));
            return FileOperationResult.failure(path, "Failed to create backup: " + e.getMessage(), e);
        }
    }

    /**
     * Restores a file from its backup
     * @param originalPath The file to restore
     * @return The result of the operation
     */
    public FileOperationResult restoreFromBackup(FilePath originalPath) {
        Path path = originalPath.getPath();
        Path backupPath = getBackupPath(path);

        if (!Files.exists(backupPath)) {
            LoggerUtil.warn(this.getClass(), "Cannot restore - backup file does not exist: " + backupPath);
            return FileOperationResult.failure(path, "Backup file does not exist");
        }

        try {
            Files.copy(backupPath, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info(this.getClass(), "Restored from backup: " + path);
            return FileOperationResult.success(path);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to restore from backup for %s: %s", path, e.getMessage()));
            return FileOperationResult.failure(path, "Failed to restore from backup: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a file's backup
     * @param originalPath The file whose backup should be deleted
     * @return The result of the operation
     */
    public FileOperationResult deleteBackup(FilePath originalPath) {
        Path path = originalPath.getPath();
        try {
            Path backupPath = getBackupPath(path);
            if (Files.deleteIfExists(backupPath)) {
                LoggerUtil.info(this.getClass(), "Deleted backup: " + backupPath);
                return FileOperationResult.success(backupPath);
            }
            return FileOperationResult.success(backupPath); // File didn't exist, but that's ok
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to delete backup for %s: %s", path, e.getMessage()));
            return FileOperationResult.failure(path, "Failed to delete backup: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the path to a file's backup
     * @param originalPath The original file
     * @return The backup path
     */
    public Path getBackupPath(Path originalPath) {
        return originalPath.resolveSibling(originalPath.getFileName() + backupExtension);
    }

    /**
     * Creates an in-memory backup of a file
     * @param originalPath The file to back up
     * @return The file contents, or empty if the file doesn't exist or can't be read
     */
    public Optional<byte[]> createMemoryBackup(FilePath originalPath) {
        Path path = originalPath.getPath();
        if (!Files.exists(path)) {
            LoggerUtil.warn(this.getClass(), "Cannot create memory backup - original file does not exist: " + path);
            return Optional.empty();
        }

        try {
            byte[] content = Files.readAllBytes(path);
            LoggerUtil.debug(this.getClass(), "Created in-memory backup: " + path);
            return Optional.of(content);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to create memory backup for %s: %s", path, e.getMessage()));
            return Optional.empty();
        }
    }
}
