package com.ctgraphdep.service.cache;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.fileOperations.events.NetworkStatusChangedEvent;
import com.ctgraphdep.model.FlagInfo;
import com.ctgraphdep.model.LocalStatusCache;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusInfo;
import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Thread-safe status cache service.
 * Manages in-memory status data to reduce file I/O operations.
 * Follows the same pattern as SessionCacheService.
 */
@Service
public class StatusCacheService {

    private final DataAccessService dataAccessService;
    private final UserService userService;

    private volatile boolean isInitialStartup = true;

    // Thread-safe cache - username as key
    private final ConcurrentHashMap<String, StatusCacheEntry> statusCache = new ConcurrentHashMap<>();

    // Global cache lock for operations that affect multiple entries
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    @Autowired
    public StatusCacheService(DataAccessService dataAccessService, UserService userService) {
        this.dataAccessService = dataAccessService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void initializeCache() {
        if (isInitialStartup) {
            performStartupInitialization();
            isInitialStartup = false;
        } else {
            // Normal operation - use existing smart logic
            performNormalInitialization();
        }
    }
    /**
     * Listens for network status changes and updates cache when network becomes available
     */
    @EventListener
    public void handleNetworkStatusChanged(NetworkStatusChangedEvent event) {
        if (event.isNetworkAvailable()) {
            LoggerUtil.info(this.getClass(), "Received network available event - updating status cache");

            // Add small delay to ensure network is stable
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(2000); // 2-second delay for network stability
                    updateCacheFromNetwork();
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error in delayed cache update: " + e.getMessage());
                }
            });
        } else {
            LoggerUtil.debug(this.getClass(), "Network became unavailable - cache will use existing data");
        }
    }

    /**
     * Updates cache from network when network becomes available
     */
    private void updateCacheFromNetwork() {
        try {
            int currentUserCount = statusCache.size();

            LoggerUtil.info(this.getClass(), "Updating status cache from network...");

            // Rebuild cache from network (like midnight reset)
            clearAllCache();
            refreshAllUsersFromUserService();
            syncFromNetworkFlags();
            writeToFile();

            int newUserCount = statusCache.size();
            LoggerUtil.info(this.getClass(), String.format("Status cache updated from network: %d → %d users", currentUserCount, newUserCount));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating status cache from network: " + e.getMessage(), e);
        }
    }

    private void performStartupInitialization() {
        LoggerUtil.info(this.getClass(), "Startup: Always rebuilding status cache from network...");

        try {
            // Always try to rebuild from scratch (like midnight reset)
            createEmptyCacheFromUserService();
            syncFromNetworkFlags();
            writeToFile();

            LoggerUtil.info(this.getClass(), "Startup: Successfully rebuilt cache with " + statusCache.size() + " users");

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Startup: Network rebuild failed, falling back to local cache: " + e.getMessage());

            // Network failed - try to load from local file as fallback
            try {
                LocalStatusCache savedCache = dataAccessService.readLocalStatusCache();
                if (savedCache != null && savedCache.getUserStatuses() != null && !savedCache.getUserStatuses().isEmpty()) {
                    populateCacheFromFile(savedCache);
                    LoggerUtil.info(this.getClass(), "Startup: Using local cache as fallback with " + statusCache.size() + " users");
                } else {
                    // Even local cache failed - create minimal cache
                    createEmptyCacheFromUserService();
                    LoggerUtil.warn(this.getClass(), "Startup: Both network and local cache failed, created minimal cache");
                }
            } catch (Exception localError) {
                LoggerUtil.error(this.getClass(), "Startup: Complete initialization failure: " + localError.getMessage());
                createEmptyCacheFromUserService(); // Last resort
            }
        }
    }

    private void performNormalInitialization() {
        // Your existing smart logic
        try {
            LocalStatusCache savedCache = dataAccessService.readLocalStatusCache();
            if (savedCache != null && savedCache.getUserStatuses() != null && !savedCache.getUserStatuses().isEmpty()) {
                populateCacheFromFile(savedCache);
            } else {
                rebuildCacheFromNetworkFlags();
                writeToFile();
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during normal initialization: " + e.getMessage());
            createEmptyCacheFromUserService();
        }
    }

    /**
     * Update user status in cache (memory-only)
     * Called by commands - does NOT write to file
     * @param username The username
     * @param userId The user ID
     * @param status The new status
     * @param timestamp The timestamp
     */
    public void updateUserStatus(String username, Integer userId, String status, LocalDateTime timestamp) {
        try {
            StatusCacheEntry cacheEntry = statusCache.computeIfAbsent(username, k -> new StatusCacheEntry());

            if (!cacheEntry.isValid()) {
                // Initialize entry if not valid
                User user = userService.getUserByUsername(username).orElse(null);
                if (user != null) {
                    cacheEntry.initializeFromUserData(username, userId, user.getName(), user.getRole(), status);
                    LoggerUtil.debug(this.getClass(), "Initialized new cache entry for user: " + username);
                } else {
                    LoggerUtil.warn(this.getClass(), "Cannot initialize cache entry for unknown user: " + username);
                    return;
                }
            }

            // Update status
            cacheEntry.updateStatus(status, timestamp);
            LoggerUtil.debug(this.getClass(), "Updated status in cache for user: " + username + " to " + status);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating status for user " + username + ": " + e.getMessage(), e);
        }
    }

    /**
     * Get all user statuses from cache (primary read method)
     * Always reads from memory - fast UI access
     * @return List of UserStatusDTO for display
     */
    public List<UserStatusDTO> getAllUserStatuses() {
        globalLock.readLock().lock();
        try {
            List<UserStatusDTO> result = new ArrayList<>();

            for (StatusCacheEntry entry : statusCache.values()) {
                if (entry.isValid()) {
                    UserStatusDTO dto = entry.toUserStatusDTO();
                    if (dto != null) {
                        result.add(dto);
                    }
                }
            }

            // Sort by status and name (same as original logic)
            result.sort(Comparator.comparing((UserStatusDTO dto) -> {
                        // First level sorting - by status with custom order
                        if (WorkCode.WORK_ONLINE.equals(dto.getStatus())) return 1;
                        if (WorkCode.WORK_TEMPORARY_STOP.equals(dto.getStatus())) return 2;
                        return 3; // All other statuses
                    })
                    .thenComparing(UserStatusDTO::getName, String.CASE_INSENSITIVE_ORDER));

            LoggerUtil.debug(this.getClass(), "Retrieved " + result.size() + " user statuses from cache");
            return result;

        } finally {
            globalLock.readLock().unlock();
        }
    }

    /**
     * Sync status from network flags (called by SessionMonitorService)
     * Updates existing cache entries without rebuilding entire cache
     */
    public void syncFromNetworkFlags() {
        try {
            if (!dataAccessService.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), "Network not available, skipping flag sync");
                return;
            }

            LoggerUtil.debug(this.getClass(), "Syncing status from network flags");

            // Read all network flag files
            List<Path> flagFiles = dataAccessService.readNetworkStatusFlags();

            // Parse flags and find latest for each user
            Map<String, FlagInfo> latestFlags = new HashMap<>();

            for (Path flagPath : flagFiles) {
                String filename = flagPath.getFileName().toString();

                try {
                    FlagInfo flagInfo = parseFlagFilename(filename);

                    if (flagInfo != null) {
                        String username = flagInfo.getUsername();

                        // Check if this is the latest flag for this user
                        if (!latestFlags.containsKey(username) ||
                                flagInfo.getTimestamp().isAfter(latestFlags.get(username).getTimestamp())) {
                            latestFlags.put(username, flagInfo);
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Error parsing flag: " + filename + " - " + e.getMessage());
                }
            }

            // Update cache with flag information
            int updatedCount = 0;
            for (Map.Entry<String, FlagInfo> entry : latestFlags.entrySet()) {
                String username = entry.getKey();
                FlagInfo flagInfo = entry.getValue();

                StatusCacheEntry cacheEntry = statusCache.get(username);
                if (cacheEntry != null && cacheEntry.isValid()) {
                    cacheEntry.updateStatus(flagInfo.getStatus(), flagInfo.getTimestamp());
                    updatedCount++;
                } else {
                    LoggerUtil.debug(this.getClass(), "No cache entry found for user with flag: " + username);
                }
            }

            // Mark users with no flags as offline
            int offlineCount = 0;
            for (StatusCacheEntry entry : statusCache.values()) {
                if (entry.isValid() && !latestFlags.containsKey(entry.getUsername())) {
                    entry.updateStatus(WorkCode.WORK_OFFLINE, null);
                    offlineCount++;
                }
            }

            LoggerUtil.info(this.getClass(), "Network flag sync completed - Updated: " + updatedCount + ", Set offline: " + offlineCount + " users");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error syncing from network flags: " + e.getMessage(), e);
        }
    }

    /**
     * Write cache to local_status.json file
     * Called by SessionMonitorService every 30 minutes
     */
    public void writeToFile() {
        globalLock.readLock().lock();
        try {
            LoggerUtil.debug(this.getClass(), "Writing status cache to file");

            // Create LocalStatusCache object
            LocalStatusCache cacheToSave = new LocalStatusCache();
            cacheToSave.setLastUpdated(LocalDateTime.now());

            Map<String, UserStatusInfo> userStatuses = new HashMap<>();
            for (Map.Entry<String, StatusCacheEntry> entry : statusCache.entrySet()) {
                StatusCacheEntry cacheEntry = entry.getValue();
                if (cacheEntry.isValid()) {
                    UserStatusInfo statusInfo = cacheEntry.toUserStatusInfo();
                    if (statusInfo != null) {
                        userStatuses.put(entry.getKey(), statusInfo);
                    }
                }
            }

            cacheToSave.setUserStatuses(userStatuses);

            // Write to file using DataAccessService
            dataAccessService.writeLocalStatusCache(cacheToSave);

            LoggerUtil.info(this.getClass(), "Successfully wrote status cache to file with " + userStatuses.size() + " users");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error writing status cache to file: " + e.getMessage(), e);
        } finally {
            globalLock.readLock().unlock();
        }
    }

    /**
     * Update all users from UserService (called at midnight)
     * Refreshes user information like names and roles
     */
    public void refreshAllUsersFromUserService() {
        globalLock.writeLock().lock();
        try {
            LoggerUtil.info(this.getClass(), "Refreshing all users from UserService");

            // Get all non-admin users
            List<User> allUsers = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin() &&
                            !user.getRole().equals("ROLE_ADMIN") &&
                            !user.getUsername().equalsIgnoreCase("admin"))
                    .toList();

            // Get set of valid usernames for cleanup
            Set<String> validUsernames = allUsers.stream()
                    .map(User::getUsername)
                    .collect(Collectors.toSet());

            // Remove users that no longer exist or are admin
            Set<String> usernamesToRemove = new HashSet<>();
            for (String username : statusCache.keySet()) {
                if (!validUsernames.contains(username) || username.equalsIgnoreCase("admin")) {
                    usernamesToRemove.add(username);
                }
            }

            // Remove invalid users
            for (String username : usernamesToRemove) {
                statusCache.remove(username);
                LoggerUtil.info(this.getClass(),
                        username.equalsIgnoreCase("admin") ?
                                "Removed admin user from status cache" :
                                "Removed non-existent user from status cache: " + username);
            }

            // Update/create cache entries for valid users
            for (User user : allUsers) {
                if (user.isAdmin() || user.getUsername().equalsIgnoreCase("admin")) {
                    continue;
                }

                StatusCacheEntry cacheEntry = statusCache.computeIfAbsent(user.getUsername(), k -> new StatusCacheEntry());

                if (!cacheEntry.isValid()) {
                    // Initialize new entry
                    cacheEntry.initializeFromUserData(user.getUsername(), user.getUserId(),
                            user.getName(), user.getRole(), WorkCode.WORK_OFFLINE);
                } else {
                    // Update user info only
                    cacheEntry.updateUserInfo(user.getName(), user.getRole());
                }
            }

            LoggerUtil.info(this.getClass(), "Refreshed information for " + allUsers.size() +
                    " users and removed " + usernamesToRemove.size() + " invalid users");

        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Clear entire cache (for complete reset)
     */
    public void clearAllCache() {
        globalLock.writeLock().lock();
        try {
            statusCache.clear();
            LoggerUtil.info(this.getClass(), "Cleared entire status cache");
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Get cache statistics for monitoring
     */
    public String getCacheStatus() {
        globalLock.readLock().lock();
        try {
            StringBuilder status = new StringBuilder();
            status.append("Status Cache Status:\n");
            status.append("Total cached users: ").append(statusCache.size()).append("\n");

            statusCache.forEach((username, entry) -> status.append("User: ").append(username)
                    .append(", Valid: ").append(entry.isValid())
                    .append(", Status: ").append(entry.getStatus())
                    .append(", Age: ").append(entry.getCacheAge()).append("ms\n"));

            return status.toString();
        } finally {
            globalLock.readLock().unlock();
        }
    }

    // === HELPER METHODS ===

    /**
     * Populate cache from local_status.json file data
     */
    private void populateCacheFromFile(LocalStatusCache savedCache) {
        for (Map.Entry<String, UserStatusInfo> entry : savedCache.getUserStatuses().entrySet()) {
            String username = entry.getKey();
            UserStatusInfo statusInfo = entry.getValue();

            StatusCacheEntry cacheEntry = new StatusCacheEntry();
            cacheEntry.initializeFromStatusInfo(statusInfo);
            statusCache.put(username, cacheEntry);
        }

        LoggerUtil.info(this.getClass(), "Populated cache from file with " + statusCache.size() + " users");
    }

    /**
     * Rebuild entire cache from network flags and user service
     */
    private void rebuildCacheFromNetworkFlags() {
        // First create entries for all users from UserService
        createEmptyCacheFromUserService();

        // Then update with network flag data
        syncFromNetworkFlags();

        LoggerUtil.info(this.getClass(), "Rebuilt cache from network flags");
    }

    /**
     * Create empty cache from UserService data
     */
    private void createEmptyCacheFromUserService() {
        try {
            List<User> allUsers = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin() &&
                            !user.getRole().equals("ROLE_ADMIN") &&
                            !user.getUsername().equalsIgnoreCase("admin"))
                    .toList();

            for (User user : allUsers) {
                StatusCacheEntry cacheEntry = new StatusCacheEntry();
                cacheEntry.initializeFromUserData(user.getUsername(), user.getUserId(),
                        user.getName(), user.getRole(), WorkCode.WORK_OFFLINE);
                statusCache.put(user.getUsername(), cacheEntry);
            }

            LoggerUtil.info(this.getClass(), "Created empty cache from UserService with " + allUsers.size() + " users");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating empty cache from UserService: " + e.getMessage(), e);
        }
    }

    /**
     * Parse flag filename to extract user, date, time, and status information
     * Same logic as in ReadFileNameStatusService
     */
    private FlagInfo parseFlagFilename(String filename) {
        try {
            // Remove .flag extension
            filename = filename.replace(".flag", "");

            // Split by underscore
            String[] parts = filename.split("_");

            // Expected format: status_username_dateCode_timeCode_statusCode
            if (parts.length >= 5) {
                String username = parts[1];
                String dateCode = parts[2];
                String timeCode = parts[3];
                String statusCode = parts[4];

                // Parse date and time
                LocalDate date = getDateFromCode(dateCode);
                LocalTime time = getTimeFromCode(timeCode);
                LocalDateTime timestamp = LocalDateTime.of(date, time);

                // Get full status from code
                String status = getStatusFromCode(statusCode);

                return new FlagInfo(username, status, timestamp);
            }

            return null;
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error parsing flag filename: " + filename + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert date code to LocalDate (same logic as ReadFileNameStatusService)
     */
    private LocalDate getDateFromCode(String code) {
        LocalDate today = LocalDate.now();

        switch (code) {
            case "1a1": return today;
            case "1b1": return today.minusDays(1);
            case "1c1": return today.minusDays(2);
            default:
                if (code.startsWith("X") && code.length() == 5) {
                    try {
                        int month = Integer.parseInt(code.substring(1, 3));
                        int day = Integer.parseInt(code.substring(3, 5));
                        return LocalDate.of(today.getYear(), month, day);
                    } catch (Exception e) {
                        return today;
                    }
                } else {
                    return today;
                }
        }
    }

    /**
     * Convert time code to LocalTime (same logic as ReadFileNameStatusService)
     */
    private LocalTime getTimeFromCode(String code) {
        if (code.startsWith("T") && code.length() == 5) {
            try {
                int hour = Integer.parseInt(code.substring(1, 3));
                int minute = Integer.parseInt(code.substring(3, 5));
                return LocalTime.of(hour, minute);
            } catch (Exception e) {
                return LocalTime.NOON;
            }
        }
        return LocalTime.NOON;
    }

    /**
     * Convert status code to full status (same logic as ReadFileNameStatusService)
     */
    private String getStatusFromCode(String code) {
        return switch (code) {
            case "ON" -> WorkCode.WORK_ONLINE;
            case "TS" -> WorkCode.WORK_TEMPORARY_STOP;
            default -> WorkCode.WORK_OFFLINE;
        };
    }
}