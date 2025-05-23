package com.ctgraphdep.fileOperations.service;

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
 * Enhanced service for handling file backup operations with different criticality levels.
 */
@Service
public class BackupService {
    @Value("${app.backup.extension:.bak}")
    private String backupExtension;

    // These values are used across multiple methods so they remain instance variables
    @Value("${app.backup.max.level1:3}")
    private int maxBackupsLevel1;

    @Value("${app.backup.max.level2:5}")
    private int maxBackupsLevel2;

    @Value("${app.backup.max.level3:10}")
    private int maxBackupsLevel3;

    @Value("${app.backup.retention.days:30}")
    private int backupRetentionDays;

    private final PathConfig pathConfig;

    /**
     * Criticality levels for different types of files
     */
    public enum CriticalityLevel {
        LEVEL1_LOW,      // Status files - no special backup needed
        LEVEL2_MEDIUM,   // Session files - keep backups but minimal rotation
        LEVEL3_HIGH      // Worktime and Register files - comprehensive backup strategy
    }

    @Autowired
    public BackupService(PathConfig pathConfig) {
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void init() {
        LoggerUtil.info(this.getClass(), "BackupService initializing...");
        try {
            // Verify backup directory structure exists
            Path baseBackupDir = pathConfig.getLocalPath().resolve(pathConfig.getBackupPath());

            // Create criticality level directories if they don't exist
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
     * Creates a backup of a file with the specified criticality level
     * @param originalPath The file to back up
     * @param level The criticality level determining backup strategy
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
                // For low criticality, create both a simple .bak and a structured backup
                Path simpleBackupPath = getSimpleBackupPath(path);
                Files.createDirectories(simpleBackupPath.getParent());
                Files.copy(path, simpleBackupPath, StandardCopyOption.REPLACE_EXISTING);
                LoggerUtil.debug(this.getClass(), "Created simple backup: " + simpleBackupPath);

                // Also create a copy in the structured directory
                Path backupDir = getBackupDirectory(originalPath, level);
                Path structuredBackupPath = backupDir.resolve(path.getFileName().toString() + ".bak");
                Files.createDirectories(structuredBackupPath.getParent());
                Files.copy(path, structuredBackupPath, StandardCopyOption.REPLACE_EXISTING);
                LoggerUtil.debug(this.getClass(), "Created Level 1 structured backup: " + structuredBackupPath);

                return FileOperationResult.success(simpleBackupPath);
            }

            // For Level 2 and 3, create a comprehensive backup with timestamp
            Path backupDir = getBackupDirectory(originalPath, level);
            LoggerUtil.debug(this.getClass(), "Using backup directory for " + level + ": " + backupDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String originalFilename = path.getFileName().toString();
            Path timestampedBackupPath = backupDir.resolve(originalFilename + "." + timestamp + ".bak");

            // Ensure parent directories exist
            Files.createDirectories(timestampedBackupPath.getParent());

            // Create the backup with explicit logging
            try {
                Files.copy(path, timestampedBackupPath, StandardCopyOption.REPLACE_EXISTING);
                LoggerUtil.info(this.getClass(), "Created " + level + " backup: " + timestampedBackupPath);
            } catch (IOException e) {
                LoggerUtil.error(this.getClass(), "Failed to create timestamped backup at " + timestampedBackupPath + ": " + e.getMessage());
                throw e;
            }

            // For Level 2 & 3, also create a simple .bak file for quick recovery
            Path simpleBackupPath = getSimpleBackupPath(path);
            Files.createDirectories(simpleBackupPath.getParent());
            Files.copy(path, simpleBackupPath, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.debug(this.getClass(), "Created simple backup: " + simpleBackupPath);

            // Enforce backup rotation policy
            enforceBackupRotationPolicy(originalPath, level);

            return FileOperationResult.success(timestampedBackupPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to create %s backup for %s: %s",
                    level, path, e.getMessage()), e);
            return FileOperationResult.failure(path, "Failed to create backup: " + e.getMessage(), e);
        }
    }
    /**
     * Creates a simple .bak backup file next to the original
     */
    private FileOperationResult createSimpleBackup(FilePath originalPath) {
        Path path = originalPath.getPath();
        try {
            Path simpleBackupPath = getSimpleBackupPath(path);
            Files.createDirectories(simpleBackupPath.getParent());
            Files.copy(path, simpleBackupPath, StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.debug(this.getClass(), "Created simple backup: " + simpleBackupPath);
            return FileOperationResult.success(simpleBackupPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to create simple backup for %s: %s",
                    path, e.getMessage()));
            return FileOperationResult.failure(path, "Failed to create simple backup: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the backup directory for a file based on criticality level
     */
    private Path getBackupDirectory(FilePath originalPath, CriticalityLevel level) {
        // Base backup directory
        Path backupSubDir = getPath(level);

        // Get the original filename and extract key components
        String filename = originalPath.getPath().getFileName().toString();
        LoggerUtil.debug(this.getClass(), "Creating backup directory for file: " + filename);

        // Extract file type from the filename (e.g., "registru" from "registru_oana_5_2025_05.json")
        String[] parts = filename.split("_");
        if (parts.length > 0) {
            String fileType = parts[0]; // e.g., "registru", "worktime", "session"
            backupSubDir = backupSubDir.resolve(fileType);

            // Add username if available in the filename or from FilePath object
            String username = null;
            if (parts.length > 1) {
                username = parts[1]; // Extract username from filename
            } else {
                // Try to get username from FilePath object
                username = originalPath.getUsername().orElse(null);
            }

            if (username != null) {
                backupSubDir = backupSubDir.resolve(username);
            }

            // Add year/month organization if available in the filename
            // For files like "registru_oana_5_2025_05.json"
            if (parts.length > 3 && parts[3].matches("\\d{4}")) {
                backupSubDir = backupSubDir.resolve(parts[3]); // year
                if (parts.length > 4 && parts[4].matches("\\d{2}")) {
                    backupSubDir = backupSubDir.resolve(parts[4].replace(".json", "")); // month
                }
            }
        }

        try {
            Files.createDirectories(backupSubDir);
            LoggerUtil.info(this.getClass(), "Created/verified backup directory: " + backupSubDir);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error creating backup directory: " + e.getMessage());
        }

        return backupSubDir;
    }

    private @NotNull Path getPath(CriticalityLevel level) {
        Path baseBackupDir = pathConfig.getLocalPath().resolve(pathConfig.getBackupPath());

        // Get the appropriate level directory based on criticality
        final String levelDir;
        switch (level) {
            case LEVEL1_LOW -> levelDir = pathConfig.getLevelLow();
            case LEVEL2_MEDIUM -> levelDir = pathConfig.getLevelMedium();
            case LEVEL3_HIGH -> levelDir = pathConfig.getLevelHigh();
            default -> levelDir = pathConfig.getLevelLow();
        }

        // Add criticality level subdirectory
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
     * Restores a file from the latest comprehensive backup
     * @param originalPath The file to restore
     * @param level The criticality level
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
     * Finds the latest backup file for a given path and criticality level
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
                        .filter(p -> p.getFileName().toString().endsWith(".bak"))
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
     * Lists all available backups for a file
     * @param originalPath The original file path
     * @param level The criticality level
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
                        .filter(p -> p.getFileName().toString().endsWith(".bak"))
                        .sorted(Comparator.comparing((Path p) -> {
                            try {
                                return Files.getLastModifiedTime(p);
                            } catch (IOException e) {
                                return FileTime.fromMillis(0);
                            }
                        }).reversed()) // Newest first
                        .collect(Collectors.toList());
            }

            LoggerUtil.info(this.getClass(), String.format("Found %d backups for %s with level %s",
                    backups.size(), originalPath.getPath(), level));

            return backups;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error listing available backups: " + e.getMessage());
            return backups;
        }
    }

    /**
     * Enforces backup rotation policy based on criticality level
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
                        .filter(p -> p.getFileName().toString().endsWith(".bak"))
                        .sorted(Comparator.comparing(p -> {
                            try {
                                return Files.getLastModifiedTime(p);
                            } catch (IOException e) {
                                return FileTime.fromMillis(0);
                            }
                        }))
                        .toList();
            }

            // Determine max backups based on criticality level
            final int maxBackups;
            switch (level) {
                case LEVEL2_MEDIUM -> maxBackups = maxBackupsLevel2;
                case LEVEL3_HIGH -> maxBackups = maxBackupsLevel3;
                default -> maxBackups = maxBackupsLevel1;
            }

            // Delete the oldest backups if we have too many
            if (backups.size() > maxBackups) {
                int toDelete = backups.size() - maxBackups;
                LoggerUtil.info(this.getClass(), String.format("Deleting %d oldest backups for %s",
                        toDelete, originalPath.getPath()));

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
     * Syncs local backup to network
     * @param username The username for the backup
     * @param level The criticality level
     */
    public void syncBackupsToNetwork(String username, CriticalityLevel level) {
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network not available, cannot sync backups");
            return;
        }

        try {
            // Get the appropriate level directory based on criticality
            final String levelDir;
            switch (level) {
                case LEVEL1_LOW -> levelDir = pathConfig.getLevelLow();
                case LEVEL2_MEDIUM -> levelDir = pathConfig.getLevelMedium();
                case LEVEL3_HIGH -> levelDir = pathConfig.getLevelHigh();
                default -> levelDir = pathConfig.getLevelLow();
            }

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
            Files.walkFileTree(localBackupDir, new SimpleFileVisitor<Path>() {
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
            Files.walkFileTree(baseBackupDir, new SimpleFileVisitor<Path>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    // Skip simple .bak files (they're managed separately)
                    if (file.getFileName().toString().endsWith(".bak") &&
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
}