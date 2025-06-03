package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.model.LocalStatusCache;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Domain service for all session-related data operations.
 * Handles user sessions, status cache, and network status operations with event-driven backups.
 */
@Service
public class SessionDataService {
    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;

    public SessionDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathResolver = pathResolver;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ===== SESSION OPERATIONS =====

    /**
     * Writes a session file to local storage and syncs to network with event-driven backups.
     */
    public void writeLocalSessionFile(WorkUsersSessionsStates session) {
        validateSession(session);

        try {
            // Create a file path object
            FilePath localPath = pathResolver.getLocalPath(
                    session.getUsername(),
                    session.getUserId(),
                    FilePathResolver.FileType.SESSION,
                    FilePathResolver.createParams()
            );

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSyncNoBackup(localPath, session, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write session file: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Saved session for user %s with status %s", session.getUsername(), session.getSessionStatus()));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), "Failed to write session file: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a session file from local storage.
     */
    public WorkUsersSessionsStates readLocalSessionFile(String username, Integer userId) {
        // Create a file path object
        FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.SESSION, FilePathResolver.createParams());

        // Read the file
        Optional<WorkUsersSessionsStates> result = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

        return result.orElse(null);
    }

    /**
     * Reads a session file from the network in read-only mode.
     */
    public WorkUsersSessionsStates readNetworkSessionFileReadOnly(String username, Integer userId) {
        try {
            // Create a file path object
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.SESSION, FilePathResolver.createParams());

            // Read the file in read-only mode
            Optional<WorkUsersSessionsStates> result = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {}, true);

            return result.orElse(null);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading network session for user %s: %s", username, e.getMessage()));
            return null;
        }
    }

    /**
     * Reads a session file from local storage in read-only mode (no locking).
     */
    public WorkUsersSessionsStates readLocalSessionFileReadOnly(String username, Integer userId) {
        try {
            // Create a file path object
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.SESSION, FilePathResolver.createParams());

            // Read the file in read-only mode
            Optional<WorkUsersSessionsStates> result = fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {}, true);

            return result.orElse(null);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading local session for user %s: %s", username, e.getMessage()));
            return null;
        }
    }

    // ===== STATUS CACHE OPERATIONS =====

    /**
     * Reads local status cache.
     */
    public LocalStatusCache readLocalStatusCache() {
        try {
            FilePath cachePath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.STATUS, new HashMap<>());
            Optional<LocalStatusCache> cache = fileReaderService.readLocalFile(cachePath, new TypeReference<>() {}, true);
            return cache.orElse(new LocalStatusCache());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading local status cache: " + e.getMessage(), e);
            return new LocalStatusCache();
        }
    }

    /**
     * Writes local status cache using event-driven backups.
     */
    public void writeLocalStatusCache(LocalStatusCache cache) {
        try {
            FilePath cachePath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.STATUS, new HashMap<>());

            // Use FileWriterService without network sync for cache data - still triggers events for backup
            FileOperationResult result = fileWriterService.writeFileWithBackupControl(cachePath, cache, true,false);

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write local status cache: " + result.getErrorMessage().orElse("Unknown error"));
                return;
            }

            LoggerUtil.debug(this.getClass(), "Successfully wrote local status cache using event system");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error writing local status cache: " + e.getMessage(), e);
        }
    }

    // ===== NETWORK STATUS FLAGS =====

    /**
     * Creates network status flag.
     */
    public void createNetworkStatusFlag(String username, String dateCode, String timeCode, String statusCode) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.warn(this.getClass(), "Network unavailable, cannot create status flag");
                return;
            }

            // We need to use the raw Path API here because we're creating a file with a dynamic name
            Path networkFlagsDir = pathConfig.getNetworkStatusFlagsDirectory();
            Files.createDirectories(networkFlagsDir);

            // Delete any existing status flags for this user
            try (Stream<Path> files = Files.list(networkFlagsDir)) {
                files.filter(path -> path.getFileName().toString().startsWith("status_" + username + "_")).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        LoggerUtil.error(this.getClass(), "Error deleting old network status flag: " + e.getMessage());
                    }
                });
            }

            // Create the new flag file on network
            String flagFilename = String.format(pathConfig.getStatusFlagFormat(), username, dateCode, timeCode, statusCode);
            Path networkFlagPath = networkFlagsDir.resolve(flagFilename);
            Files.createFile(networkFlagPath);

            LoggerUtil.debug(this.getClass(), "Created network status flag: " + flagFilename);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating network status flag for " + username + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads network status flags.
     */
    public List<Path> readNetworkStatusFlags() {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.warn(this.getClass(), "Network unavailable, cannot read status flags");
                return List.of();
            }

            Path networkFlagsDir = pathConfig.getNetworkStatusFlagsDirectory();
            if (!Files.exists(networkFlagsDir)) {
                return List.of();
            }

            try (Stream<Path> files = Files.list(networkFlagsDir)) {
                return files.filter(path -> path.getFileName().toString().matches("status_.*\\.flag"))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading network status flags: " + e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Deletes network status flag.
     */
    public boolean deleteNetworkStatusFlag(Path flagPath) {
        try {
            return Files.deleteIfExists(flagPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error deleting network status flag: " + e.getMessage(), e);
            return false;
        }
    }

    // ===== LOG OPERATIONS =====

    /**
     * Checks if local log file exists.
     */
    public boolean localLogExists() {
        try {
            Path localLogPath = pathConfig.getLocalLogPath();
            return Files.exists(localLogPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking if local log exists: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the list of usernames with available logs.
     */
    public List<String> getUserLogsList() {
        try {
            Path logDir = pathConfig.getNetworkLogDirectory();
            if (!Files.exists(logDir)) {
                return List.of();
            }

            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
                LoggerUtil.info(this.getClass(), "Created network logs directory: " + logDir);
            }

            try (Stream<Path> files = Files.list(logDir)) {
                return files
                        .filter(path -> path.getFileName().toString().startsWith("ctgraphdep-logger_") &&
                                path.getFileName().toString().endsWith(FileTypeConstants.LOG_EXTENSION))
                        .map(path -> {
                            String filename = path.getFileName().toString();
                            // Extract username from filename format: ctgraphdep-logger_username.log
                            return filename.substring(18, filename.length() - 4);
                        })
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error listing log files: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Reads log content for a specific user.
     */
    public Optional<String> getUserLogContent(String username) {
        try {
            // First try to find existing log files for this user (either format)
            String logFilename = getLogFilename(username);

            if (!logFilename.isEmpty()) {
                // If we found a filename, use it directly
                Path logPath = pathConfig.getNetworkLogDirectory().resolve(logFilename);
                if (Files.exists(logPath)) {
                    return Optional.of(Files.readString(logPath));
                }
            }

            // If no file found by name search, try the default path with Unknown version
            Path defaultLogPath = pathConfig.getNetworkLogPath(username, "Unknown");
            if (Files.exists(defaultLogPath)) {
                return Optional.of(Files.readString(defaultLogPath));
            }

            // No log file found
            return Optional.empty();

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error reading log for " + username + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Gets the log filename for a specific user.
     */
    public String getLogFilename(String username) {
        try {
            Path logDir = pathConfig.getNetworkLogDirectory();
            if (!Files.exists(logDir)) {
                return "";
            }

            try (Stream<Path> files = Files.list(logDir)) {
                return files
                        .filter(path -> {
                            String filename = path.getFileName().toString();
                            // Match both old format (ctgraphdep-logger_username.log)
                            // and new format with version (ctgraphdep-logger_username_vX.Y.Z.log)
                            return filename.contains("_" + username + "_") ||
                                    filename.equals("ctgraphdep-logger_" + username + FileTypeConstants.LOG_EXTENSION);
                        })
                        .map(path -> path.getFileName().toString())
                        .findFirst()
                        .orElse("");
            }
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error getting log filename for " + username + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * Extract version from log filename.
     */
    public String extractVersionFromLogFilename(String filename) {
        return pathConfig.extractVersionFromLogFilename(filename);
    }

    /**
     * Syncs the local log file to the network for a specific user with version information.
     */
    public void syncLogToNetwork(String username, String version) throws IOException {
        Path sourceLogPath = pathConfig.getLocalLogPath();
        Path targetLogPath = pathConfig.getNetworkLogPath(username, version);

        // First, try to delete any existing logs for this user (regardless of version)
        try {
            Path logDir = pathConfig.getNetworkLogDirectory();
            if (Files.exists(logDir)) {
                try (Stream<Path> files = Files.list(logDir)) {
                    files.filter(path -> {
                        String filename = path.getFileName().toString();
                        return filename.startsWith("ctgraphdep-logger_" + username + "_") ||
                                filename.equals("ctgraphdep-logger_" + username + FileTypeConstants.LOG_EXTENSION);
                    }).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                            LoggerUtil.debug(this.getClass(), "Deleted old log file: " + path.getFileName());
                        } catch (IOException e) {
                            LoggerUtil.warn(this.getClass(), "Could not delete old log file: " + e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error cleaning up old log files: " + e.getMessage());
            // Continue with sync even if cleanup fails
        }

        // Ensure target directory exists
        Path networkLogsDir = targetLogPath.getParent();
        if (!Files.exists(networkLogsDir)) {
            Files.createDirectories(networkLogsDir);
        }

        // Copy log file with new name containing version
        Files.copy(sourceLogPath, targetLogPath, StandardCopyOption.REPLACE_EXISTING);

        LoggerUtil.info(this.getClass(),
                String.format("Synced log for user %s with version %s to %s",
                        username, version, targetLogPath));
    }

    // ===== UTILITY METHODS =====

    /**
     * Validates a session object has required fields.
     */
    private void validateSession(WorkUsersSessionsStates session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        if (session.getUsername() == null || session.getUserId() == null) {
            throw new IllegalArgumentException("Session must have both username and userId");
        }
    }

}