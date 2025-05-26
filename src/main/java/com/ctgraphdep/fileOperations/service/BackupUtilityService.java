package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Admin service for managing and recovering backup files.
 * This service provides utilities for administrators to view, restore, and manage backups.
 */
@Service
public class BackupUtilityService {
    private final PathConfig pathConfig;
    private final BackupService backupService;

    @Autowired
    public BackupUtilityService(
            PathConfig pathConfig,
            BackupService backupService) {
        this.pathConfig = pathConfig;
        this.backupService = backupService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Lists all available backups for a specific user
     * @param username The username
     * @param userId The user ID
     * @param fileType The type of file to find backups for
     * @return Map of backup paths and their timestamps
     */
    public Map<String, LocalDateTime> listAvailableBackups(String username, Integer userId, String fileType) {
        Map<String, LocalDateTime> backups = new HashMap<>();

        try {
            // Get base backup directory
            Path baseBackupDir = pathConfig.getLocalPath().resolve(pathConfig.getBackupPath());

            // Determine criticality level based on file type
            BackupService.CriticalityLevel level = determineCriticalityLevelByType(fileType);
            Path levelDir = baseBackupDir.resolve(level.toString().toLowerCase());

            // Look for backups in the appropriate directory structure
            searchForBackups(levelDir, username, userId, fileType, backups);

            // If no backups found locally, check network backup
            if (backups.isEmpty() && pathConfig.isNetworkAvailable()) {
                Path networkBackupDir = pathConfig.getNetworkPath()
                        .resolve(pathConfig.getBackupPath())
                        .resolve(username)
                        .resolve(level.toString().toLowerCase());

                if (Files.exists(networkBackupDir)) {
                    searchForBackups(networkBackupDir, username, userId, fileType, backups);
                }
            }

            LoggerUtil.info(this.getClass(), String.format("Found %d backups for user %s, file type %s with criticality %s", backups.size(), username, fileType, level));

            return backups;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error listing available backups: " + e.getMessage());
            return backups;
        }
    }

    /**
     * Helper method to search recursively for backups
     */
    private void searchForBackups(Path directory, String username, Integer userId, String fileType,
                                  Map<String, LocalDateTime> backups) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        // Recursively walk directory tree
        try (Stream<Path> paths = Files.walk(directory, 5)) { // Limit depth to avoid excessively deep searches
            List<Path> backupFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String fileName = p.getFileName().toString().toLowerCase();
                        return fileName.contains(username.toLowerCase()) &&
                                fileName.contains(fileType.toLowerCase()) &&
                                fileName.endsWith(FileTypeConstants.BACKUP_EXTENSION);
                    })
                    .toList();

            // Extract timestamp information for each backup
            for (Path backup : backupFiles) {
                String fileName = backup.getFileName().toString();
                LocalDateTime timestamp;

                // Try to extract timestamp from filename (format: originalfile.yyyyMMdd_HHmmss.bak)
                int timestampIndex = fileName.lastIndexOf(".");
                if (timestampIndex > 0 && timestampIndex < fileName.length() - 4) {
                    String timestampStr = fileName.substring(timestampIndex + 1, fileName.length() - 4);
                    try {
                        timestamp = LocalDateTime.parse(timestampStr,
                                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    } catch (Exception e) {
                        // If parsing fails, use file last modified time
                        timestamp = LocalDateTime.now();
                    }
                } else {
                    // If no timestamp in filename, use file last modified time
                    timestamp = LocalDateTime.now();
                }

                backups.put(backup.toString(), timestamp);
            }
        }
    }

    /**
     * Determines criticality level based on file type string
     */
    private BackupService.CriticalityLevel determineCriticalityLevelByType(String fileType) {
        fileType = fileType.toLowerCase();

        if (fileType.contains("status")) {
            return BackupService.CriticalityLevel.LEVEL1_LOW;
        } else if (fileType.contains("session")) {
            return BackupService.CriticalityLevel.LEVEL2_MEDIUM;
        } else if (fileType.contains("worktime") || fileType.contains("register")) {
            return BackupService.CriticalityLevel.LEVEL3_HIGH;
        }

        return BackupService.CriticalityLevel.LEVEL2_MEDIUM;
    }

    /**
     * Restores a file from a specific backup
     * @param backupPath Path to the backup file
     * @param targetFilePath Path where the file should be restored
     * @return The result of the operation
     */
    public FileOperationResult restoreFromBackup(String backupPath, String targetFilePath) {
        try {
            Path backup = Path.of(backupPath);
            Path target = Path.of(targetFilePath);

            // Create parent directories if they don't exist
            Files.createDirectories(target.getParent());

            // Create a backup of the current file first
            if (Files.exists(target)) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path adminBackup = target.resolveSibling(target.getFileName() + ".admin_restore_backup." + timestamp);
                Files.copy(target, adminBackup, StandardCopyOption.REPLACE_EXISTING);
                LoggerUtil.info(this.getClass(), "Created admin backup before restoration: " + adminBackup);
            }

            // Copy the backup to the target path
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully restored %s from backup %s", targetFilePath, backupPath));

            return FileOperationResult.success(target);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error restoring from backup: " + e.getMessage());
            return FileOperationResult.failure(Path.of(targetFilePath),
                    "Failed to restore from backup: " + e.getMessage(), e);
        }
    }

    /**
     * Gets metadata about a specific backup file
     * @param backupPath Path to the backup file
     * @return Map containing metadata information
     */
    public Map<String, Object> getBackupMetadata(String backupPath) {
        Map<String, Object> metadata = new HashMap<>();
        try {
            Path backup = Path.of(backupPath);
            if (!Files.exists(backup)) {
                metadata.put("error", "Backup file does not exist");
                return metadata;
            }

            // Basic file information
            metadata.put("path", backupPath);
            metadata.put("size", Files.size(backup));
            metadata.put("lastModified", Files.getLastModifiedTime(backup).toInstant().toString());

            // Extract type information from path
            String path = backupPath.toLowerCase();
            if (path.contains("worktime")) {
                metadata.put("type", "Worktime");
                metadata.put("criticality", "HIGH");
            } else if (path.contains("register")) {
                metadata.put("type", "Register");
                metadata.put("criticality", "HIGH");
            } else if (path.contains("session")) {
                metadata.put("type", "Session");
                metadata.put("criticality", "MEDIUM");
            } else if (path.contains("status")) {
                metadata.put("type", "Status");
                metadata.put("criticality", "LOW");
            } else {
                metadata.put("type", "Unknown");
                metadata.put("criticality", "MEDIUM");
            }

            // Extract username if possible
            String fileName = backup.getFileName().toString();
            int usernameStart = fileName.indexOf("_");
            int usernameEnd = fileName.indexOf("_", usernameStart + 1);
            if (usernameStart >= 0 && usernameEnd > usernameStart) {
                metadata.put("username", fileName.substring(usernameStart + 1, usernameEnd));
            }

            // Try to read and validate file content (size only)
            try {
                byte[] content = Files.readAllBytes(backup);
                metadata.put("contentSize", content.length);
                metadata.put("valid", content.length > 0);
            } catch (Exception e) {
                metadata.put("valid", false);
                metadata.put("readError", e.getMessage());
            }

            return metadata;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading backup metadata: " + e.getMessage());
            metadata.put("error", e.getMessage());
            return metadata;
        }
    }

    /**
     * Creates an additional backup of a file for administrative purposes
     * @param username The username
     * @param userId The user ID
     * @param fileType The file type (worktime, register, etc.)
     * @param year The year
     * @param month The month
     * @return Result of the backup operation
     */
    public FileOperationResult createAdminBackup(String username, Integer userId,
                                                 String fileType, int year, int month) {
        try {
            // Determine file path based on type
            FilePath filePath = getFilePath(username, userId, fileType, year, month);
            if (filePath == null) {
                return FileOperationResult.failure(null, "Invalid file type: " + fileType);
            }

            Path path = filePath.getPath();
            if (!Files.exists(path)) {
                return FileOperationResult.failure(path, "File does not exist");
            }

            // Determine criticality level
            BackupService.CriticalityLevel level = determineCriticalityLevelByType(fileType);

            // Create admin-specific backup with date and admin tag
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path adminBackupDir = pathConfig.getLocalPath().resolve(pathConfig.getBackupPath()).resolve("admin_backups");

            Files.createDirectories(adminBackupDir);

            Path adminBackup = adminBackupDir.resolve(
                    path.getFileName() + ".admin_backup." + timestamp +FileTypeConstants.BACKUP_EXTENSION);

            Files.copy(path, adminBackup, StandardCopyOption.REPLACE_EXISTING);

            LoggerUtil.info(this.getClass(), "Created admin backup: " + adminBackup);

            // Also create a standard backup
            backupService.createBackup(filePath, level);

            return FileOperationResult.success(adminBackup);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating admin backup: " + e.getMessage());
            return FileOperationResult.failure(null, "Failed to create admin backup: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a FilePath object based on file type and parameters
     */
    private FilePath getFilePath(String username, Integer userId, String fileType, int year, int month) {
        fileType = fileType.toLowerCase();

        return switch (fileType) {
            case "worktime" -> FilePath.local(pathConfig.getLocalWorktimePath(username, year, month));
            case "register" -> FilePath.local(pathConfig.getLocalRegisterPath(username, userId, year, month));
            case "session" -> FilePath.local(pathConfig.getLocalSessionPath(username, userId));
            case "check_register" ->
                    FilePath.local(pathConfig.getLocalCheckRegisterPath(username, userId, year, month));
            case "admin_worktime" -> FilePath.local(pathConfig.getLocalAdminWorktimePath(year, month));
            case "admin_register" ->
                    FilePath.local(pathConfig.getLocalAdminRegisterPath(username, userId, year, month));
            case "lead_check_register" ->
                    FilePath.local(pathConfig.getLocalCheckLeadRegisterPath(username, userId, year, month));
            case "timeoff_tracker" -> FilePath.local(pathConfig.getLocalTimeOffTrackerPath(username, userId, year));
            default -> null;
        };

    }
}