package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.model.db.UserStatusRecord;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
/**
 * Service for managing user session status with per-user database files.
 * Uses DataAccessService for file operations to maintain consistency with other services.
 */
@Service
public class UserStatusDbService {

    private final UserService userService;
    private final PathConfig pathConfig;
    private final DataAccessService dataAccessService;

    @Value("${user.status.cache.timeout:30}")
    private long cacheTimeoutSeconds;

    // Cache for status DTOs
    private volatile List<UserStatusDTO> cachedStatuses = null;
    private volatile LocalDateTime cacheTimestamp = null;

    // Cache for online/active counts
    private volatile int cachedOnlineCount = 0;
    private volatile int cachedActiveCount = 0;
    private volatile LocalDateTime countCacheTimestamp = null;

    @Autowired
    public UserStatusDbService(
            UserService userService, PathConfig pathConfig,
            DataAccessService dataAccessService) {
        this.userService = userService;
        this.pathConfig = pathConfig;
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), null);
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

            // Write status using DataAccessService
            dataAccessService.writeUserStatus(username, userId, statusRecord);

            // Invalidate cache immediately
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

                if (secondsSinceLastCache < cacheTimeoutSeconds) {
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

            // Read all status records using DataAccessService
            Map<String, UserStatusRecord> statusRecords = dataAccessService.readAllUserStatuses();

            // Convert status records to DTOs
            for (User user : allUsers) {
                // Find corresponding status record
                UserStatusRecord record = statusRecords.get(user.getUsername());

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

                if (secondsSinceLastCache < cacheTimeoutSeconds) {
                    return cachedOnlineCount;
                }
            }

            // If no cached statuses or cache is expired, force a refresh
            if (cachedStatuses == null || cacheTimestamp == null || Duration.between(cacheTimestamp, LocalDateTime.now()).getSeconds() >= cacheTimeoutSeconds) {
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

                if (secondsSinceLastCache < cacheTimeoutSeconds) {
                    return cachedActiveCount;
                }
            }

            // If no cached statuses or cache is expired, force a refresh
            if (cachedStatuses == null || cacheTimestamp == null || Duration.between(cacheTimestamp, LocalDateTime.now()).getSeconds() >= cacheTimeoutSeconds) {
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
     * Invalidate all caches to force refresh from the database files
     */
    public void invalidateCache() {
        //LoggerUtil.debug(this.getClass(), "Invalidating status cache");
        cachedStatuses = null;
        cacheTimestamp = null;
        cachedOnlineCount = 0;
        cachedActiveCount = 0;
        countCacheTimestamp = null;
    }

    /**
     * Scheduled method to invalidate cache periodically
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void scheduledCacheInvalidation() {
        invalidateCache();
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

            // Read all status records using DataAccessService
            Map<String, UserStatusRecord> statusRecords = dataAccessService.readAllUserStatuses();

            for (UserStatusRecord record : statusRecords.values()) {
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
                        dataAccessService.writeUserStatus(record.getUsername(), record.getUserId(), record);
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
     * Check if a user's status file exists
     * @param username The username
     * @param userId The user ID
     * @return true if the status file exists, false otherwise
     */
    public boolean userStatusExists(String username, Integer userId) {
        try {
            // Check local status file
            Path localStatusPath = pathConfig.getLocalStatusFilePath(username, userId);
            if (Files.exists(localStatusPath) && Files.size(localStatusPath) > 0) {
                return true;
            }

            // If network is available, also check network status file
            if (pathConfig.isNetworkAvailable()) {
                Path networkStatusPath = pathConfig.getNetworkStatusFilePath(username, userId);
                return Files.exists(networkStatusPath) && Files.size(networkStatusPath) > 0;
            }

            return false;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking status existence for %s: %s", username, e.getMessage()));
            return false;
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