package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusDTO;
import com.ctgraphdep.model.db.UserStatusRecord;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service for managing user session status with per-user database files.
 * Each user writes only to their own status file, eliminating concurrent write issues.
 */
@Service
public class UserStatusDbService {

    private final PathConfig pathConfig;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    // Cache for status DTOs
    private volatile List<UserStatusDTO> cachedStatuses = null;
    private volatile LocalDateTime cacheTimestamp = null;
    private static final long CACHE_TTL_SECONDS = 900; // Cache valid for 15 minutes

    // Path to the status DB directory
    private Path statusDbDir;

    @Autowired
    public UserStatusDbService(PathConfig pathConfig, UserService userService, ObjectMapper objectMapper) {
        this.pathConfig = pathConfig;
        this.userService = userService;
        this.objectMapper = objectMapper;
        initStatusDbDirectory();
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Initialize the directory for status DB files.
     */
    private void initStatusDbDirectory() {
        // Status DB files will be in a status_db subdirectory of the session directory
        statusDbDir = pathConfig.getNetworkPath().resolve(pathConfig.getUserSession()).resolve("status_db");

        try {
            // Make sure the directory exists
            Files.createDirectories(statusDbDir);
            LoggerUtil.info(this.getClass(), "Initialized network status DB directory: " + statusDbDir);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Failed to initialize network status DB directory: " + e.getMessage());
            // Use local path as fallback if network is unavailable
            statusDbDir = pathConfig.getLocalPath().resolve(pathConfig.getUserSession()).resolve("status_db");

            try {
                Files.createDirectories(statusDbDir);
                LoggerUtil.info(this.getClass(), "Initialized local status DB directory: " + statusDbDir);
            } catch (IOException ex) {
                LoggerUtil.error(this.getClass(), "Failed to initialize local status DB directory: " + ex.getMessage());
            }
        }
    }

    /**
     * Get the path to a user's status DB file.
     */
    private Path getUserStatusFilePath(String username, Integer userId) {
        return statusDbDir.resolve("status_" + username + "_" + userId + ".json");
    }

    /**
     * Updates the current user's session status in their dedicated status file.
     */
    public void updateUserStatus(String username, Integer userId, String status, LocalDateTime lastActive) {
        try {
            User user = userService.getUserByUsername(username).orElse(null);
            if (user == null) {
                LoggerUtil.warn(this.getClass(), "Attempted to update status for unknown user: " + username);
                return;
            }

            // Create user status record
            UserStatusRecord statusRecord = new UserStatusRecord();
            statusRecord.setUsername(username);
            statusRecord.setUserId(userId);
            statusRecord.setName(user.getName());
            statusRecord.setStatus(status);
            statusRecord.setLastActive(lastActive);
            statusRecord.setLastUpdated(LocalDateTime.now());

            // Get file path for this user
            Path statusFilePath = getUserStatusFilePath(username, userId);

            // Write to temp file first (atomic write)
            Path tempFile = statusFilePath.resolveSibling(statusFilePath.getFileName() + ".tmp");
            byte[] content = objectMapper.writeValueAsBytes(statusRecord);
            Files.write(tempFile, content);

            // Atomically replace the actual file (prevents partial writes being seen)
            Files.move(tempFile, statusFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            // Invalidate cache
            invalidateCache();

            LoggerUtil.debug(this.getClass(), String.format("Updated status file for %s to %s", username, status));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating status file for %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Updates a user's status from session data.
     */
    public void updateUserStatusFromSession(String username, Integer userId, String sessionStatus, LocalDateTime lastActivity) {
        if (username == null || userId == null) return;

        try {
            String status = determineStatus(sessionStatus);
            updateUserStatus(username, userId, status, lastActivity);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating status from session data: %s", e.getMessage()), e);
        }
    }

    /**
     * Gets all user statuses for display on the status page.
     * Uses in-memory caching to reduce file reads.
     */
    public List<UserStatusDTO> getAllUserStatuses() {
        try {
            // Check if we have a valid cache
            if (cachedStatuses != null && cacheTimestamp != null) {
                long secondsSinceLastCache = Duration.between(cacheTimestamp, LocalDateTime.now()).getSeconds();

                if (secondsSinceLastCache < CACHE_TTL_SECONDS) {
                    LoggerUtil.debug(this.getClass(), "Returning cached user statuses");
                    return new ArrayList<>(cachedStatuses); // Return a copy of cached list
                }
            }

            // Get all non-admin users from UserService
            List<User> allUsers = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin() &&
                            !user.getRole().equals("ADMIN") &&
                            !user.getRole().equals("ADMINISTRATOR") &&
                            !user.getUsername().equalsIgnoreCase("admin"))
                    .toList();

            // Create a result list
            List<UserStatusDTO> result = new ArrayList<>();

            // Read all status files from directory
            List<UserStatusRecord> statusRecords = readAllStatusRecords();

            // Convert status records to DTOs
            for (User user : allUsers) {
                // Find corresponding status record
                UserStatusRecord record = statusRecords.stream().filter(r -> r.getUsername().equals(user.getUsername())).findFirst().orElse(null);

                // Convert to DTO
                if (record != null) {
                    result.add(UserStatusDTO.builder()
                            .username(record.getUsername())
                            .userId(record.getUserId())
                            .name(record.getName())
                            .status(record.getStatus())
                            .lastActive(formatDateTime(record.getLastActive()))
                            .build());
                } else {
                    // No status record exists - user is offline
                    result.add(UserStatusDTO.builder()
                            .username(user.getUsername())
                            .userId(user.getUserId())
                            .name(user.getName())
                            .status(WorkCode.WORK_OFFLINE)
                            .lastActive(WorkCode.LAST_ACTIVE_NEVER)
                            .build());
                }
            }

            // Sort results by status and then by name
            result.sort(Comparator.comparing((UserStatusDTO dto) -> {
                        // First level sorting - by status with custom order
                        if (WorkCode.WORK_ONLINE.equals(dto.getStatus())) return 1;
                        if (WorkCode.WORK_TEMPORARY_STOP.equals(dto.getStatus())) return 2;
                        return 3; // All other statuses
                    })
                    .thenComparing(UserStatusDTO::getName, String.CASE_INSENSITIVE_ORDER));

            // Update cache
            cachedStatuses = new ArrayList<>(result);
            cacheTimestamp = LocalDateTime.now();

            // Update count caches
            updateCountCaches(result);

            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting all user statuses: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Read all status records from individual user status files.
     */
    private List<UserStatusRecord> readAllStatusRecords() {
        List<UserStatusRecord> records = new ArrayList<>();

        try {
            if (!Files.exists(statusDbDir)) {
                return records;
            }

            // Read all JSON files in the status directory
            try (Stream<Path> files = Files.list(statusDbDir)) {
                List<Path> statusFiles = files.filter(path -> path.toString().endsWith(".json")).toList();

                for (Path file : statusFiles) {
                    try {
                        if (Files.size(file) > 0) {
                            UserStatusRecord record = objectMapper.readValue(Files.readAllBytes(file), UserStatusRecord.class);
                            records.add(record);
                        }
                    } catch (Exception e) {
                        LoggerUtil.debug(this.getClass(), "Skipping invalid status file: " + file.getFileName() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading status records: " + e.getMessage(), e);
        }

        return records;
    }

    // Cache for online/active counts
    private volatile int cachedOnlineCount = 0;
    private volatile int cachedActiveCount = 0;
    private volatile LocalDateTime countCacheTimestamp = null;

    private void updateCountCaches(List<UserStatusDTO> statuses) {
        cachedOnlineCount = (int) statuses.stream().filter(s -> WorkCode.WORK_ONLINE.equals(s.getStatus())).count();
        cachedActiveCount = (int) statuses.stream().filter(s -> WorkCode.WORK_ONLINE.equals(s.getStatus()) || WorkCode.WORK_TEMPORARY_STOP.equals(s.getStatus())).count();
        countCacheTimestamp = LocalDateTime.now();
    }

    /**
     * Get the number of online users with caching
     */
    public int getOnlineUserCount() {
        try {
            // Check if we have a valid count cache
            if (countCacheTimestamp != null) {
                long secondsSinceLastCache = Duration.between(countCacheTimestamp, LocalDateTime.now()).getSeconds();

                if (secondsSinceLastCache < CACHE_TTL_SECONDS) {
                    return cachedOnlineCount;
                }
            }

            // If no cached statuses or cache is expired, force a refresh
            if (cachedStatuses == null || cacheTimestamp == null || Duration.between(cacheTimestamp, LocalDateTime.now()).getSeconds() >= CACHE_TTL_SECONDS) {
                getAllUserStatuses(); // This will update the count cache
                return cachedOnlineCount;
            }

            // If we have cached statuses but no count cache, calculate and cache it
            cachedOnlineCount = (int) cachedStatuses.stream().filter(s -> WorkCode.WORK_ONLINE.equals(s.getStatus())).count();
            countCacheTimestamp = LocalDateTime.now();
            return cachedOnlineCount;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting online user count: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get the number of active users (online or temporary stop) with caching
     */
    public int getActiveUserCount() {
        try {
            // Check if we have a valid count cache
            if (countCacheTimestamp != null) {
                long secondsSinceLastCache = Duration.between(countCacheTimestamp, LocalDateTime.now()).getSeconds();

                if (secondsSinceLastCache < CACHE_TTL_SECONDS) {
                    return cachedActiveCount;
                }
            }

            // If no cached statuses or cache is expired, force a refresh
            if (cachedStatuses == null || cacheTimestamp == null || Duration.between(cacheTimestamp, LocalDateTime.now()).getSeconds() >= CACHE_TTL_SECONDS) {
                getAllUserStatuses(); // This will update the count cache
                return cachedActiveCount;
            }

            // If we have cached statuses but no count cache, calculate and cache it
            cachedActiveCount = (int) cachedStatuses.stream().filter(s -> WorkCode.WORK_ONLINE.equals(s.getStatus()) || WorkCode.WORK_TEMPORARY_STOP.equals(s.getStatus())).count();
            countCacheTimestamp = LocalDateTime.now();
            return cachedActiveCount;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting active user count: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Invalidate all caches periodically
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void invalidateCache() {
        cachedStatuses = null;
        cacheTimestamp = null;
        cachedOnlineCount = 0;
        cachedActiveCount = 0;
        countCacheTimestamp = null;
    }

    /**
     * Check for stale status files and clean them up.
     * If a user's status hasn't been updated in more than 1 hour, mark them as offline.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupStaleSessions() {
        try {
            LoggerUtil.info(this.getClass(), "Running session status cleanup...");

            // Calculate cutoff time for stale sessions (more than 1 hour old)
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);

            // Read all status records
            List<UserStatusRecord> records = readAllStatusRecords();

            for (UserStatusRecord record : records) {
                try {
                    // Skip already offline users
                    if (WorkCode.WORK_OFFLINE.equals(record.getStatus())) {
                        continue;
                    }

                    // If last update is older than 1 hour, mark as offline
                    if (record.getLastUpdated() != null && record.getLastUpdated().isBefore(cutoffTime)) {
                        LoggerUtil.info(this.getClass(), String.format("Marking stale session for %s as offline (last updated: %s)", record.getUsername(), record.getLastUpdated()));

                        // Update the record with OFFLINE status
                        record.setStatus(WorkCode.WORK_OFFLINE);
                        record.setLastUpdated(LocalDateTime.now());

                        // Save the updated record
                        Path statusFilePath = getUserStatusFilePath(record.getUsername(), record.getUserId());
                        byte[] content = objectMapper.writeValueAsBytes(record);
                        Files.write(statusFilePath, content);
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            String.format("Error cleaning up status for %s: %s", record.getUsername(), e.getMessage()));
                }
            }

            // Invalidate cache after cleanup
            invalidateCache();
            LoggerUtil.info(this.getClass(), "Session status cleanup completed");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error cleaning up session statuses: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to determine status string from work code
     */
    private String determineStatus(String workCode) {
        if (workCode == null) {
            return WorkCode.WORK_OFFLINE;
        }

        return switch (workCode) {
            case WorkCode.WORK_ONLINE -> WorkCode.WORK_ONLINE;
            case WorkCode.WORK_TEMPORARY_STOP -> WorkCode.WORK_TEMPORARY_STOP;
            case WorkCode.WORK_OFFLINE -> WorkCode.WORK_OFFLINE;
            default -> WorkCode.STATUS_UNKNOWN;
        };
    }

    /**
     * Format date/time for display
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return WorkCode.LAST_ACTIVE_NEVER;
        }
        return dateTime.format(WorkCode.INPUT_FORMATTER);
    }
}