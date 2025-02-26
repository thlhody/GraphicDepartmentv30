package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FileBackupService {

    public void createBackup(Path originalPath) {
        if (!Files.exists(originalPath)) {
            LoggerUtil.warn(this.getClass(), "Cannot create backup - original file does not exist: " + originalPath);
            return;
        }

        try {
            Path backupPath = getBackupPath(originalPath);
            Files.createDirectories(backupPath.getParent());
            Files.copy(originalPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info(this.getClass(), "Created backup: " + backupPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to create backup for %s: %s", originalPath, e.getMessage()));
            throw new RuntimeException("Backup creation failed", e);
        }
    }

    public void restoreFromBackup(Path originalPath) {
        Path backupPath = getBackupPath(originalPath);
        if (!Files.exists(backupPath)) {
            LoggerUtil.warn(this.getClass(), "Cannot restore - backup file does not exist: " + backupPath);
            return;
        }

        try {
            Files.copy(backupPath, originalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info(this.getClass(), "Restored from backup: " + originalPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to restore from backup for %s: %s", originalPath, e.getMessage()));
            throw new RuntimeException("Backup restoration failed", e);
        }
    }

    public void deleteBackup(Path originalPath) {
        try {
            Path backupPath = getBackupPath(originalPath);
            if (Files.deleteIfExists(backupPath)) {
                LoggerUtil.info(this.getClass(), "Deleted backup: " + backupPath);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to delete backup for %s: %s", originalPath, e.getMessage()));
        }
    }

    public Path getBackupPath(Path originalPath) {
        return originalPath.resolveSibling(originalPath.getFileName() + WorkCode.BACKUP_EXTENSION);
    }
}
