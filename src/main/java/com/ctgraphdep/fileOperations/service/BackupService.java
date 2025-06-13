package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.config.FileTypeConstants.CriticalityLevel;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * REFACTORED: Enhanced service for handling file backup operations with different criticality levels.
 * Key Changes:
 * - Now uses FileTypeConstants.CriticalityLevel enum (moved from this class)
 * - Leverages FileTypeConstants utility methods for max backup counts
 * - Simplified criticality logic using centralized FileTypeConstants
 * - Cleaner API with consistent enum usage
 * - Better integration with the centralized file type system
 */
@Service
public class BackupService {
    @Value("${app.backup.extension:.bak}")
    private String backupExtension;

    @Value("${app.backup.retention.days:30}")
    private int backupRetentionDays;

    private final PathConfig pathConfig;

    @Autowired
    public BackupService(PathConfig pathConfig) {
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        LoggerUtil.info(this.getClass(), "BackupService initializing with FileTypeConstants integration...");
        try {
            // Verify backup directory structure exists
            Path baseBackupDir = pathConfig.getLocalPath().resolve(pathConfig.getBackupPath());

            // Create criticality level directories using FileTypeConstants enum
            Path level1Dir = baseBackupDir.resolve(pathConfig.getLevelLow());
            Path level2Dir = baseBackupDir.resolve(pathConfig.getLevelMedium());
            Path level3Dir = baseBackupDir.resolve(pathConfig.getLevelHigh());
            Path adminBackupsDir = baseBackupDir.resolve(pathConfig.getAdminBackup());

            if (!Files.exists(level1Dir)) Files.createDirectories(level1Dir);
            if (!Files.exists(level2Dir)) Files.createDirectories(level2Dir);
            if (!Files.exists(level3Dir)) Files.createDirectories(level3Dir);
            if (!Files.exists(adminBackupsDir)) Files.createDirectories(adminBackupsDir);

            // Also create on network if available
            if (pathConfig.isNetworkAvailable()) {
                Path networkBaseBackupDir = pathConfig.getNetworkPath().resolve(pathConfig.getBackupPath());
                if (!Files.exists(networkBaseBackupDir)) {
                    Files.createDirectories(networkBaseBackupDir);
                }
            }

            LoggerUtil.info(this.getClass(), "BackupService initialized - backup directory structure ready at " + baseBackupDir);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing backup directories: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a backup of a file with the specified criticality level.
     * Now uses FileTypeConstants.CriticalityLevel enum and utility methods.
     *
     * @param originalPath The file to back up
     * @param level The criticality level determining backup strategy (from FileTypeConstants)
     * @return The result of the operation
     */
    public FileOperationResult createBackup(FilePath originalPath, CriticalityLevel level) {
        LoggerUtil.info(this.getClass(), "Creating " + level + " backup for " + originalPath.getPath());
        Path path = originalPath.getPath();
        if (!Files.exists(path)) {
            LoggerUtil.warn(this.getClass(), "Cannot create backup - original file does not exist: " + path);
            return FileOperationResult.failure(path, "Original file does not exist");
        }

        try {
            // For Level 1 (low criticality), just create a simple backup
            if (level == CriticalityLevel.LEVEL1_LOW) {
                return createLevel1Backup(originalPath);
            }

            // For Level 2 and 3, create comprehensive backup with timestamp
            return createComprehensiveBackup(originalPath, level);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to create %s backup for %s: %s", level, path, e.getMessage()), e);
            return FileOperationResult.failure(path, "Failed to create backup: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a Level 1 (low criticality) backup - simple strategy.
     */
    private FileOperationResult createLevel1Backup(FilePath originalPath) throws IOException {
        Path path = originalPath.getPath();

        // Create both a simple .bak and a structured backup for Level 1
        Path simpleBackupPath = getSimpleBackupPath(path);
        Files.createDirectories(simpleBackupPath.getParent());
        Files.copy(path, simpleBackupPath, StandardCopyOption.REPLACE_EXISTING);
        LoggerUtil.debug(this.getClass(), "Created simple backup: " + simpleBackupPath);

        // Also create a copy in the structured directory
        Path backupDir = getBackupDirectory(originalPath, CriticalityLevel.LEVEL1_LOW);
        Path structuredBackupPath = backupDir.resolve(path.getFileName().toString() + FileTypeConstants.BACKUP_EXTENSION);
        Files.createDirectories(structuredBackupPath.getParent());
        Files.copy(path, structuredBackupPath, StandardCopyOption.REPLACE_EXISTING);
        LoggerUtil.debug(this.getClass(), "Created Level 1 structured backup: " + structuredBackupPath);

        return FileOperationResult.success(simpleBackupPath);
    }

    /**
     * Creates comprehensive backup for Level 2 and Level 3 criticality.
     */
    private FileOperationResult createComprehensiveBackup(FilePath originalPath, CriticalityLevel level) throws IOException {
        Path path = originalPath.getPath();
        Path backupDir = getBackupDirectory(originalPath, level);
        LoggerUtil.debug(this.getClass(), "Using backup directory for " + level + ": " + backupDir);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String originalFilename = path.getFileName().toString();
        Path timestampedBackupPath = backupDir.resolve(originalFilename + "." + timestamp + FileTypeConstants.BACKUP_EXTENSION);

        // Ensure parent directories exist
        Files.createDirectories(timestampedBackupPath.getParent());

        // Create the timestamped backup
        Files.copy(path, timestampedBackupPath, StandardCopyOption.REPLACE_EXISTING);
        LoggerUtil.info(this.getClass(), "Created " + level + " backup: " + timestampedBackupPath);

        // For Level 2 & 3, also create a simple .bak file for quick recovery
        Path simpleBackupPath = getSimpleBackupPath(path);
        Files.createDirectories(simpleBackupPath.getParent());
        Files.copy(path, simpleBackupPath, StandardCopyOption.REPLACE_EXISTING);
        LoggerUtil.debug(this.getClass(), "Created simple backup: " + simpleBackupPath);

        // Enforce backup rotation policy using FileTypeConstants utility
        enforceBackupRotationPolicy(originalPath, level);

        return FileOperationResult.success(timestampedBackupPath);
    }

    /**
     * Gets the backup directory for a file based on criticality level.
     * Now uses FileTypeConstants for file type detection.
     */
    private Path getBackupDirectory(FilePath originalPath, CriticalityLevel level) {
        // Base backup directory
        Path backupSubDir = getPath(level);

        // Get the original filename and extract key components using FileTypeConstants
        String filename = originalPath.getPath().getFileName().toString();
        LoggerUtil.debug(this.getClass(), "Creating backup directory for file: " + filename);

        // Use FileTypeConstants to detect file type
        String fileType = FileTypeConstants.extractFileTypeFromFilename(filename);
        if (fileType != null) {
            // Use the detected file type for directory structure
            backupSubDir = backupSubDir.resolve(fileType);
            LoggerUtil.debug(this.getClass(), "Detected file type: " + fileType);
        } else {
            // Fallback to extracting from filename manually for unknown types
            String[] parts = filename.split("_");
            if (parts.length > 0) {
                String fileTypeFromName = parts[0]; // e.g., "registru", "worktime", "session"
                backupSubDir = backupSubDir.resolve(fileTypeFromName);
            }
        }

        // Add username if available
        String username = extractUsernameFromFilename(filename);
        if (username == null) {
            username = originalPath.getUsername().orElse(null);
        }
        if (username != null) {
            backupSubDir = backupSubDir.resolve(username);
        }

        // Add year/month organization if available in the filename
        backupSubDir = addDateOrganization(backupSubDir, filename);

        try {
            Files.createDirectories(backupSubDir);
            LoggerUtil.info(this.getClass(), "Created/verified backup directory: " + backupSubDir);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error creating backup directory: " + e.getMessage());
        }

        return backupSubDir;
    }

    /**
     * Helper method to extract username from filename patterns.
     */
    private String extractUsernameFromFilename(String filename) {
        String[] parts = filename.split("_");
        if (parts.length > 1) {
            return parts[1]; // Usually username is second part: "type_username_..."
        }
        return null;
    }

    /**
     * Helper method to add date-based organization to back up directory.
     */
    private Path addDateOrganization(Path backupSubDir, String filename) {
        // Extract year/month from patterns like "registru_oana_5_2025_05.json"
        String[] parts = filename.split("_");
        if (parts.length > 3 && parts[3].matches("\\d{4}")) {
            backupSubDir = backupSubDir.resolve(parts[3]); // year
            if (parts.length > 4 && parts[4].matches("\\d{2}")) {
                backupSubDir = backupSubDir.resolve(parts[4].replace(FileTypeConstants.JSON_EXTENSION, "")); // month
            }
        }
        return backupSubDir;
    }

    /**
     * Gets the appropriate level directory path based on criticality.
     */
    private @NotNull Path getPath(CriticalityLevel level) {
        Path baseBackupDir = pathConfig.getLocalPath().resolve(pathConfig.getBackupPath());

        // Get the appropriate level directory based on criticality
        final String levelDir = switch (level) {
            case LEVEL1_LOW -> pathConfig.getLevelLow();
            case LEVEL2_MEDIUM -> pathConfig.getLevelMedium();
            case LEVEL3_HIGH -> pathConfig.getLevelHigh();
        };

        return baseBackupDir.resolve(levelDir);
    }

    /**
     * Restores a file from its simple backup
     * @param originalPath The file to restore
     * @return The result of the operation
     */
    public FileOperationResult restoreFromSimpleBackup(FilePath originalPath) {
        Path path = originalPath.getPath();
        Path backupPath = getSimpleBackupPath(path);

        if (!Files.exists(backupPath)) {
            LoggerUtil.warn(this.getClass(), "Cannot restore - simple backup file does not exist: " + backupPath);
            return FileOperationResult.failure(path, "Backup file does not exist");
        }

        try {
            Files.copy(backupPath, path, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info(this.getClass(), "Restored from simple backup: " + path);
            return FileOperationResult.success(path);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to restore from simple backup for %s: %s", path, e.getMessage()));
            return FileOperationResult.failure(path, "Failed to restore from simple backup: " + e.getMessage(), e);
        }
    }

    /**
     * Restores a file from the latest comprehensive backup.
     * Now uses FileTypeConstants.CriticalityLevel.
     *
     * @param originalPath The file to restore
     * @param level The criticality level (from FileTypeConstants)
     * @return The result of the operation
     */
    public FileOperationResult restoreFromLatestBackup(FilePath originalPath, CriticalityLevel level) {
        // For Level 1, just use simple backup
        if (level == CriticalityLevel.LEVEL1_LOW) {
            return restoreFromSimpleBackup(originalPath);
        }

        Path path = originalPath.getPath();

        try {
            // Find the latest backup file
            Optional<Path> latestBackup = findLatestBackup(originalPath, level);

            if (latestBackup.isPresent()) {
                Path backupPath = latestBackup.get();
                Files.copy(backupPath, path, StandardCopyOption.REPLACE_EXISTING);
                LoggerUtil.info(this.getClass(), "Restored from latest " + level + " backup: " + backupPath);
                return FileOperationResult.success(path);
            } else {
                // Fall back to simple backup if no comprehensive backup exists
                LoggerUtil.warn(this.getClass(), "No comprehensive backup found, attempting simple backup restore");
                return restoreFromSimpleBackup(originalPath);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to restore from %s backup for %s: %s",
                    level, path, e.getMessage()));
            return FileOperationResult.failure(path, "Failed to restore from backup: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the latest backup file for a given path and criticality level.
     * Now uses FileTypeConstants.CriticalityLevel.
     */
    public Optional<Path> findLatestBackup(FilePath originalPath, CriticalityLevel level) {
        Path backupDir = getBackupDirectory(originalPath, level);
        String filenamePrefix = originalPath.getPath().getFileName().toString();

        try {
            if (!Files.exists(backupDir)) {
                return Optional.empty();
            }

            try (Stream<Path> files = Files.list(backupDir)) {
                return files
                        .filter(p -> p.getFileName().toString().startsWith(filenamePrefix + "."))
                        .filter(p -> p.getFileName().toString().endsWith(FileTypeConstants.BACKUP_EXTENSION))
                        .max(Comparator.comparing(p -> {
                            try {
                                return Files.getLastModifiedTime(p);
                            } catch (IOException e) {
                                return FileTime.fromMillis(0);
                            }
                        }));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error finding latest backup: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lists all available backups for a file.
     * Now uses FileTypeConstants.CriticalityLevel and includes max backup info.
     *
     * @param originalPath The original file path
     * @param level The criticality level (from FileTypeConstants)
     * @return List of available backup paths sorted by date (newest first)
     */
    public List<Path> listAvailableBackups(FilePath originalPath, CriticalityLevel level) {
        Path backupDir = getBackupDirectory(originalPath, level);
        String filenamePrefix = originalPath.getPath().getFileName().toString();
        List<Path> backups = new ArrayList<>();

        try {
            if (!Files.exists(backupDir)) {
                return backups;
            }

            try (Stream<Path> files = Files.list(backupDir)) {
                backups = files
                        .filter(p -> p.getFileName().toString().startsWith(filenamePrefix + "."))
                        .filter(p -> p.getFileName().toString().endsWith(FileTypeConstants.BACKUP_EXTENSION))
                        .sorted(Comparator.comparing((Path p) -> {
                            try {
                                return Files.getLastModifiedTime(p);
                            } catch (IOException e) {
                                return FileTime.fromMillis(0);
                            }
                        }).reversed()) // Newest first
                        .collect(Collectors.toList());
            }

            int maxBackups = FileTypeConstants.getMaxBackups(level);
            LoggerUtil.info(this.getClass(), String.format("Found %d backups for %s with level %s (max: %d)",
                    backups.size(), originalPath.getPath(), level, maxBackups));

            return backups;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error listing available backups: " + e.getMessage());
            return backups;
        }
    }

    /**
     * Enforces backup rotation policy based on criticality level.
     * Now uses FileTypeConstants.getMaxBackups() for consistent limits.
     */
    private void enforceBackupRotationPolicy(FilePath originalPath, CriticalityLevel level) {
        // Skip for Level 1 as we don't keep multiple backups
        if (level == CriticalityLevel.LEVEL1_LOW) {
            return;
        }

        Path backupDir = getBackupDirectory(originalPath, level);
        String filenamePrefix = originalPath.getPath().getFileName().toString();

        try {
            if (!Files.exists(backupDir)) {
                return;
            }

            List<Path> backups;
            try (Stream<Path> files = Files.list(backupDir)) {
                backups = files
                        .filter(p -> p.getFileName().toString().startsWith(filenamePrefix + "."))
                        .filter(p -> p.getFileName().toString().endsWith(FileTypeConstants.BACKUP_EXTENSION))
                        .sorted(Comparator.comparing(p -> {
                            try {
                                return Files.getLastModifiedTime(p);
                            } catch (IOException e) {
                                return FileTime.fromMillis(0);
                            }
                        }))
                        .toList();
            }

            // Use FileTypeConstants to get max backups for this level
            int maxBackups = FileTypeConstants.getMaxBackups(level);

            // Delete the oldest backups if we have too many
            if (backups.size() > maxBackups) {
                int toDelete = backups.size() - maxBackups;
                LoggerUtil.info(this.getClass(), String.format("Deleting %d oldest backups for %s (max allowed: %d)",
                        toDelete, originalPath.getPath(), maxBackups));

                for (int i = 0; i < toDelete; i++) {
                    try {
                        Files.deleteIfExists(backups.get(i));
                    } catch (IOException e) {
                        LoggerUtil.warn(this.getClass(), "Could not delete old backup: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error enforcing backup rotation policy: " + e.getMessage());
        }
    }

    /**
     * Deletes a file's simple backup
     * @param originalPath The file whose backup should be deleted
     */
    public void deleteSimpleBackup(FilePath originalPath) {
        Path path = originalPath.getPath();
        try {
            Path backupPath = getSimpleBackupPath(path);
            if (Files.deleteIfExists(backupPath)) {
                LoggerUtil.info(this.getClass(), "Deleted simple backup: " + backupPath);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to delete simple backup for %s: %s", path, e.getMessage()));
        }
    }

    /**
     * Gets the path to a file's simple backup
     * @param originalPath The original file
     * @return The backup path
     */
    public Path getSimpleBackupPath(Path originalPath) {
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

    /**
     * Syncs local backup to network.
     * Now uses FileTypeConstants.CriticalityLevel.
     *
     * @param username The username for the backup
     * @param level The criticality level (from FileTypeConstants)
     */
    public void syncBackupsToNetwork(String username, CriticalityLevel level) {
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network not available, cannot sync backups");
            return;
        }

        try {
            // Get the appropriate level directory based on criticality
            final String levelDir = switch (level) {
                case LEVEL1_LOW -> pathConfig.getLevelLow();
                case LEVEL2_MEDIUM -> pathConfig.getLevelMedium();
                case LEVEL3_HIGH -> pathConfig.getLevelHigh();
            };

            // Source local backup directory
            Path localBackupDir = pathConfig.getLocalPath()
                    .resolve(pathConfig.getBackupPath())
                    .resolve(levelDir);

            if (!Files.exists(localBackupDir)) {
                LoggerUtil.warn(this.getClass(), "Local backup directory doesn't exist: " + localBackupDir);
                return;
            }

            // Target network backup directory
            Path networkBackupDir = pathConfig.getNetworkPath()
                    .resolve(pathConfig.getBackupPath())
                    .resolve(username) // Add username subdirectory for organization
                    .resolve(levelDir);

            Files.createDirectories(networkBackupDir);

            // Use a visitor to copy files recursively
            Files.walkFileTree(localBackupDir, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    Path relativePath = localBackupDir.relativize(file);
                    Path targetPath = networkBackupDir.resolve(relativePath);
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                    Path relativePath = localBackupDir.relativize(dir);
                    Path targetPath = networkBackupDir.resolve(relativePath);
                    Files.createDirectories(targetPath);
                    return FileVisitResult.CONTINUE;
                }
            });

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully synced %s backups to network for user %s", level, username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error syncing backups to network: " + e.getMessage());
        }
    }

    /**
     * Scheduled job to clean up old backups based on retention policy
     */
    @Scheduled(cron = "0 0 3 * * ?") // Run at 3 AM every day
    public void cleanupOldBackups() {
        try {
            Path baseBackupDir = pathConfig.getLocalPath().resolve(pathConfig.getBackupPath());
            if (!Files.exists(baseBackupDir)) {
                return;
            }

            LoggerUtil.info(this.getClass(), "Starting scheduled backup cleanup job");

            // Calculate cutoff date
            final long retentionMillis = backupRetentionDays * 24L * 60L * 60L * 1000L;
            FileTime cutoffTime = FileTime.fromMillis(System.currentTimeMillis() - retentionMillis);

            // Walk through all backup files
            Files.walkFileTree(baseBackupDir, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    // Skip simple .bak files (they're managed separately)
                    if (file.getFileName().toString().endsWith(FileTypeConstants.BACKUP_EXTENSION) &&
                            !file.getFileName().toString().contains(".")) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Check if file is older than cutoff
                    if (attrs.lastModifiedTime().compareTo(cutoffTime) < 0) {
                        Files.deleteIfExists(file);
                        LoggerUtil.debug(BackupService.this.getClass(),
                                "Deleted old backup file: " + file);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });

            LoggerUtil.info(this.getClass(), "Completed scheduled backup cleanup job");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during backup cleanup: " + e.getMessage());
        }
    }

    // ===== DIAGNOSTIC AND UTILITY METHODS =====

    /**
     * Gets diagnostic information about backup configuration for a file.
     * Uses FileTypeConstants for comprehensive analysis.
     *
     * @param originalPath The file to analyze
     * @return Diagnostic information string
     */
    public String getBackupDiagnostics(FilePath originalPath) {
        String filename = originalPath.getPath().getFileName().toString();

        // Use FileTypeConstants for complete analysis
        String fileTypeDiag = FileTypeConstants.getFileTypeDiagnostics(filename);
        CriticalityLevel level = FileTypeConstants.getCriticalityLevelForFilename(filename);

        StringBuilder diag = new StringBuilder();
        diag.append("=== BACKUP DIAGNOSTICS ===\n");
        diag.append(fileTypeDiag);
        diag.append("\nBackup Configuration:\n");
        diag.append("Backup Directory: ").append(getBackupDirectory(originalPath, level)).append("\n");
        diag.append("Simple Backup Path: ").append(getSimpleBackupPath(originalPath.getPath())).append("\n");

        // Check existing backups
        List<Path> existingBackups = listAvailableBackups(originalPath, level);
        diag.append("Existing Backups: ").append(existingBackups.size()).append("\n");

        if (!existingBackups.isEmpty()) {
            diag.append("Latest Backup: ").append(existingBackups.get(0).getFileName()).append("\n");
        }

        return diag.toString();
    }
}