package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.config.FileTypeConstants.CriticalityLevel;
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
 * FIXED: Admin service for managing and recovering backup files.
 * This service provides utilities for administrators to view, restore, and manage backups.
 * Key Fix:
 * - Fixed file search logic to use actual filename prefixes instead of logical types
 * - Now correctly finds register/registru and check_register/check_registru backups
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
     * FIXED: Lists all available backups for a specific user.
     * Now correctly converts logical file types to actual filename prefixes.
     *
     * @param username The username
     * @param fileType The logical file type (e.g., "register", "check_register")
     * @return Map of backup paths and their timestamps
     */
    public Map<String, LocalDateTime> listAvailableBackups(String username, String fileType) {
        Map<String, LocalDateTime> backups = new HashMap<>();

        try {
            // Get base backup directory
            Path baseBackupDir = pathConfig.getLocalPath().resolve(pathConfig.getBackupPath());

            // Use FileTypeConstants to determine criticality level
            CriticalityLevel level = determineCriticalityLevelByType(fileType);
            Path levelDir = baseBackupDir.resolve(getLevelDirectoryName(level));

            // FIXED: Convert logical file type to actual filename prefix
            String actualFilenamePrefix = FileTypeConstants.getFilenamePrefix(fileType);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Searching for backups - User: %s, LogicalType: %s, ActualPrefix: %s, Level: %s",
                    username, fileType, actualFilenamePrefix, level));

            // Look for backups in the appropriate directory structure
            searchForBackups(levelDir, username, fileType, actualFilenamePrefix, backups);

            // If no backups found locally, check network backup
            if (backups.isEmpty() && pathConfig.isNetworkAvailable()) {
                Path networkBackupDir = pathConfig.getNetworkPath()
                        .resolve(pathConfig.getBackupPath())
                        .resolve(username)
                        .resolve(getLevelDirectoryName(level));

                if (Files.exists(networkBackupDir)) {
                    searchForBackups(networkBackupDir, username, fileType, actualFilenamePrefix, backups);
                    LoggerUtil.info(this.getClass(), "Searched network backup directory: " + networkBackupDir);
                }
            }

            // Enhanced logging with FileTypeConstants information
            int maxBackups = FileTypeConstants.getMaxBackups(level);
            String description = FileTypeConstants.getCriticalityDescription(level);

            LoggerUtil.info(this.getClass(), String.format(
                    "Found %d backups for user %s, file type %s (%s) with criticality %s (max: %d, %s)",
                    backups.size(), username, fileType, actualFilenamePrefix, level, maxBackups, description));

            return backups;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error listing available backups: " + e.getMessage(), e);
            return backups;
        }
    }

    /**
     * FIXED: Helper method to search recursively for backups using actual filename prefix
     */
    private void searchForBackups(Path directory, String username, String logicalFileType,
                                  String actualFilenamePrefix, Map<String, LocalDateTime> backups) throws IOException {
        if (!Files.exists(directory)) {
            LoggerUtil.debug(this.getClass(), "Backup directory does not exist: " + directory);
            return;
        }

        LoggerUtil.debug(this.getClass(), String.format(
                "Searching directory: %s for files matching user: %s, prefix: %s, %s",
                directory, username, actualFilenamePrefix, logicalFileType));

        // Recursively walk directory tree
        try (Stream<Path> paths = Files.walk(directory, 5)) { // Limit depth to avoid excessively deep searches
            List<Path> backupFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String fileName = p.getFileName().toString().toLowerCase();
                        String usernameLower = username.toLowerCase();
                        String prefixLower = actualFilenamePrefix.toLowerCase();

                        boolean matches = fileName.contains(usernameLower) &&
                                fileName.startsWith(prefixLower) &&  // FIXED: Use actual prefix, check start
                                fileName.endsWith(FileTypeConstants.BACKUP_EXTENSION);

                        if (matches) {
                            LoggerUtil.debug(this.getClass(), String.format(
                                    "Found matching backup file: %s (user: %s, prefix: %s)",
                                    fileName, usernameLower, prefixLower));
                        }

                        return matches;
                    })
                    .toList();

            LoggerUtil.info(this.getClass(), String.format(
                    "Found %d backup files in directory %s for user %s with prefix %s",
                    backupFiles.size(), directory, username, actualFilenamePrefix));

            // Extract timestamp information for each backup
            for (Path backup : backupFiles) {
                String fileName = backup.getFileName().toString();
                LocalDateTime timestamp = extractTimestampFromBackupFile(fileName);
                backups.put(backup.toString(), timestamp);

                LoggerUtil.debug(this.getClass(), String.format(
                        "Added backup: %s with timestamp: %s", fileName, timestamp));
            }
        }
    }

    /**
     * NEW: Extract timestamp from backup filename with better logic
     */
    private LocalDateTime extractTimestampFromBackupFile(String fileName) {
        try {
            // Try to extract timestamp from filename (format: originalfile.yyyyMMdd_HHmmss.bak)
            int timestampIndex = fileName.lastIndexOf(".");
            if (timestampIndex > 0 && timestampIndex < fileName.length() - 4) {
                String afterLastDot = fileName.substring(timestampIndex + 1);

                // Check if it's just .bak (simple backup)
                if (afterLastDot.equals("bak")) {
                    // Look for timestamp before .bak
                    String withoutBak = fileName.substring(0, timestampIndex);
                    int prevDotIndex = withoutBak.lastIndexOf(".");
                    if (prevDotIndex > 0) {
                        String possibleTimestamp = withoutBak.substring(prevDotIndex + 1);
                        if (possibleTimestamp.matches("\\d{8}_\\d{6}")) {
                            return LocalDateTime.parse(possibleTimestamp,
                                    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                        }
                    }
                } else {
                    // Check if the part before .bak is timestamped
                    String timestampStr = afterLastDot.replace(".bak", "");
                    if (timestampStr.matches("\\d{8}_\\d{6}")) {
                        return LocalDateTime.parse(timestampStr,
                                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    }
                }
            }

            // If no timestamp in filename, use file last modified time
            return LocalDateTime.now().minusHours(1); // Default to 1 hour ago

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error parsing timestamp from filename: " + fileName + " - " + e.getMessage());
            return LocalDateTime.now().minusHours(1);
        }
    }

    /**
     * Determines criticality level based on file type using FileTypeConstants.
     * Replaces hardcoded string matching with centralized logic.
     *
     * @param fileType The logical file type string
     * @return The criticality level from FileTypeConstants
     */
    private CriticalityLevel determineCriticalityLevelByType(String fileType) {
        if (fileType == null || fileType.isEmpty()) {
            return CriticalityLevel.LEVEL2_MEDIUM;
        }

        // Use FileTypeConstants centralized mapping
        CriticalityLevel level = FileTypeConstants.getCriticalityLevel(fileType.toLowerCase());

        LoggerUtil.debug(this.getClass(), String.format("File type %s mapped to %s criticality (%s)",
                fileType, level, FileTypeConstants.getCriticalityDescription(level)));

        return level;
    }

    /**
     * NEW: Get level directory name from criticality level
     */
    private String getLevelDirectoryName(CriticalityLevel level) {
        return switch (level) {
            case LEVEL1_LOW -> pathConfig.getLevelLow();
            case LEVEL2_MEDIUM -> pathConfig.getLevelMedium();
            case LEVEL3_HIGH -> pathConfig.getLevelHigh();
        };
    }

    /**
     * Restores a file from a specific backup
     * @param backupPath Path to the backup file
     * @param targetFilePath Path where the file should be restored
     * @return The result of the operation
     */
    public FileOperationResult restoreFromBackup(String backupPath, String targetFilePath) {
        Path target = Path.of(targetFilePath);

        try {
            Path backup = Path.of(backupPath);

            LoggerUtil.info(this.getClass(), String.format(
                    "Restoring backup from %s to %s", backupPath, targetFilePath));

            // Verify backup file exists
            if (!Files.exists(backup)) {
                String error = "Backup file does not exist: " + backupPath;
                LoggerUtil.error(this.getClass(), error);
                return FileOperationResult.failure(target, error);
            }

            // Create parent directories if they don't exist
            Files.createDirectories(target.getParent());

            // Create a backup of the current file first (if it exists)
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
            LoggerUtil.error(this.getClass(), "Error restoring from backup: " + e.getMessage(), e);
            return FileOperationResult.failure(target,
                    "Failed to restore from backup: " + e.getMessage(), e);
        }
    }

    /**
     * Gets metadata about a specific backup file.
     * Now uses FileTypeConstants for richer metadata.
     *
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

            // Extract original filename for FileTypeConstants analysis
            String fileName = backup.getFileName().toString();
            String originalFileName = extractOriginalFilename(fileName);

            if (originalFileName != null) {
                // Use FileTypeConstants for comprehensive analysis
                String fileType = FileTypeConstants.extractFileTypeFromFilename(originalFileName);
                CriticalityLevel level = FileTypeConstants.getCriticalityLevelForFilename(originalFileName);

                metadata.put("originalFilename", originalFileName);
                metadata.put("detectedFileType", fileType != null ? fileType : "unknown");
                metadata.put("criticalityLevel", level.toString());
                metadata.put("criticalityDescription", FileTypeConstants.getCriticalityDescription(level));
                metadata.put("maxBackups", FileTypeConstants.getMaxBackups(level));

                // Enhanced type information
                if (fileType != null) {
                    metadata.put("filenamePrefix", FileTypeConstants.getFilenamePrefix(fileType));
                    metadata.put("isKnownType", true);
                } else {
                    metadata.put("isKnownType", false);
                }

                // Legacy type mapping for backward compatibility
                metadata.put("type", mapToLegacyType(fileType, originalFileName));
                metadata.put("criticality", mapToLegacyCriticality(level));

            } else {
                // Fallback to old logic if filename extraction fails
                metadata.put("type", "Unknown");
                metadata.put("criticality", "MEDIUM");
                metadata.put("isKnownType", false);
            }

            // Extract username if possible
            String username = extractUsernameFromBackupFilename(fileName);
            if (username != null) {
                metadata.put("username", username);
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
     * Helper method to extract original filename from backup filename.
     * Handles formats like: originalfile.yyyyMMdd_HHmmss.bak
     */
    private String extractOriginalFilename(String backupFilename) {
        if (!backupFilename.endsWith(FileTypeConstants.BACKUP_EXTENSION)) {
            return null;
        }

        // Remove .bak extension
        String withoutBak = backupFilename.substring(0, backupFilename.length() - FileTypeConstants.BACKUP_EXTENSION.length());

        // Check if it has timestamp format
        int lastDotIndex = withoutBak.lastIndexOf(".");
        if (lastDotIndex > 0) {
            String possibleTimestamp = withoutBak.substring(lastDotIndex + 1);
            // If it looks like a timestamp, remove it
            if (possibleTimestamp.matches("\\d{8}_\\d{6}")) {
                return withoutBak.substring(0, lastDotIndex);
            }
        }

        // If no timestamp found, return as is
        return withoutBak;
    }

    /**
     * Helper method to extract username from backup filename.
     */
    private String extractUsernameFromBackupFilename(String fileName) {
        int usernameStart = fileName.indexOf("_");
        int usernameEnd = fileName.indexOf("_", usernameStart + 1);
        if (usernameStart >= 0 && usernameEnd > usernameStart) {
            return fileName.substring(usernameStart + 1, usernameEnd);
        }
        return null;
    }

    /**
     * Maps file type to legacy type strings for backward compatibility.
     */
    private String mapToLegacyType(String fileType, String originalFileName) {
        if (fileType != null) {
            return switch (fileType) {
                case FileTypeConstants.WORKTIME_TARGET -> "Worktime";
                case FileTypeConstants.REGISTER_TARGET -> "Register";
                case FileTypeConstants.SESSION_TARGET -> "Session";
                case FileTypeConstants.CHECK_REGISTER_TARGET -> "Check Register";
                case FileTypeConstants.TIMEOFF_TRACKER_TARGET -> "Time Off";
                case FileTypeConstants.ADMIN_WORKTIME_TARGET -> "Admin Worktime";
                case FileTypeConstants.ADMIN_REGISTER_TARGET -> "Admin Register";
                case FileTypeConstants.ADMIN_BONUS_TARGET -> "Admin Bonus";
                default -> "Other";
            };
        }

        // Fallback based on filename analysis
        String lowerFileName = originalFileName.toLowerCase();
        if (lowerFileName.contains("worktime")) return "Worktime";
        if (lowerFileName.contains("register") || lowerFileName.contains("registru")) return "Register";
        if (lowerFileName.contains("session")) return "Session";
        if (lowerFileName.contains("status")) return "Status";
        return "Unknown";
    }

    /**
     * Maps criticality level to legacy strings for backward compatibility.
     */
    private String mapToLegacyCriticality(CriticalityLevel level) {
        return switch (level) {
            case LEVEL1_LOW -> "LOW";
            case LEVEL2_MEDIUM -> "MEDIUM";
            case LEVEL3_HIGH -> "HIGH";
        };
    }

    /**
     * Creates an additional backup of a file for administrative purposes.
     * Now uses FileTypeConstants for criticality determination.
     *
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
                String error = "File does not exist: " + path;
                LoggerUtil.warn(this.getClass(), error);
                return FileOperationResult.failure(path, error);
            }

            // Use FileTypeConstants to determine criticality level
            CriticalityLevel level = determineCriticalityLevelByType(fileType);

            // Create admin-specific backup with date and admin tag
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path adminBackupDir = pathConfig.getLocalPath().resolve(pathConfig.getBackupPath()).resolve("admin_backups");

            Files.createDirectories(adminBackupDir);

            Path adminBackup = adminBackupDir.resolve(
                    path.getFileName() + ".admin_backup." + timestamp + FileTypeConstants.BACKUP_EXTENSION);

            Files.copy(path, adminBackup, StandardCopyOption.REPLACE_EXISTING);

            LoggerUtil.info(this.getClass(), String.format(
                    "Created admin backup: %s (criticality: %s, max backups: %d)",
                    adminBackup, level, FileTypeConstants.getMaxBackups(level)));

            // Also create a standard backup using the determined criticality level
            backupService.createBackup(filePath, level);

            return FileOperationResult.success(adminBackup);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating admin backup: " + e.getMessage(), e);
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

    /**
     * Gets comprehensive diagnostics for backup utility operations.
     * Uses FileTypeConstants for detailed analysis.
     *
     * @param fileType The file type to analyze
     * @return Diagnostic information string
     */
    public String getBackupUtilityDiagnostics(String fileType) {
        StringBuilder diag = new StringBuilder();
        diag.append("=== BACKUP UTILITY DIAGNOSTICS ===\n");
        diag.append("File Type: ").append(fileType).append("\n");

        // FIXED: Show both logical type and actual prefix
        String actualPrefix = FileTypeConstants.getFilenamePrefix(fileType);
        diag.append("Logical Type: ").append(fileType).append("\n");
        diag.append("Actual Filename Prefix: ").append(actualPrefix).append("\n");

        // Use FileTypeConstants for analysis
        CriticalityLevel level = determineCriticalityLevelByType(fileType);
        diag.append("Criticality Level: ").append(level).append("\n");
        diag.append("Max Backups: ").append(FileTypeConstants.getMaxBackups(level)).append("\n");
        diag.append("Strategy: ").append(FileTypeConstants.getCriticalityDescription(level)).append("\n");

        // Check if it's a known file type
        boolean isKnown = FileTypeConstants.getCriticalityLevel(fileType) != CriticalityLevel.LEVEL2_MEDIUM ||
                Objects.equals("session", fileType.toLowerCase());
        diag.append("Known File Type: ").append(isKnown ? "Yes" : "No").append("\n");

        if (isKnown) {
            diag.append("Filename Prefix: ").append(FileTypeConstants.getFilenamePrefix(fileType)).append("\n");
        }

        // FIXED: Add search pattern info
        diag.append("\nSearch Pattern Analysis:\n");
        diag.append("UI sends fileType: ").append(fileType).append("\n");
        diag.append("Search uses prefix: ").append(actualPrefix).append("\n");
        diag.append("Will match files starting with: ").append(actualPrefix).append("_\n");

        return diag.toString();
    }
}