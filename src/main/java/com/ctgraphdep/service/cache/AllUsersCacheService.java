package com.ctgraphdep.service.cache;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.fileOperations.data.UserDataService;  // CHANGED: Use UserDataService instead of UserService
import com.ctgraphdep.monitoring.events.NetworkStatusChangedEvent;
import com.ctgraphdep.model.FlagInfo;
import com.ctgraphdep.model.LocalStatusCache;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusInfo;
import com.ctgraphdep.model.dto.UserStatusDTO;
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
 * REFACTORED Thread-safe status cache service - NO CIRCULAR DEPENDENCY.
 * Enhanced to serve as the primary data source for user information (excluding passwords).
 * Now reads user data directly from files via UserDataService instead of UserService.
 * This breaks the circular dependency: AllUsersCacheService → UserDataService (no cycle).
 * Manages in-memory status data to reduce file I/O operations.
 */
@Service
public class AllUsersCacheService {

    private final DataAccessService dataAccessService;
    private final SessionDataService sessionDataService;
    private final UserDataService userDataService;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private volatile boolean isInitialStartup = true;

    // Thread-safe cache - username as key
    private final ConcurrentHashMap<String, AllUsersCacheEntry> statusCache = new ConcurrentHashMap<>();

    // Global cache lock for operations that affect multiple entries
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    @Autowired
    public AllUsersCacheService(DataAccessService dataAccessService,
                                SessionDataService sessionDataService,
                                UserDataService userDataService, MainDefaultUserContextService mainDefaultUserContextService) {
        this.dataAccessService = dataAccessService;
        this.sessionDataService = sessionDataService;
        this.userDataService = userDataService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // USER OBJECT CONVERSION METHODS (For UserServiceImpl Integration)
    // ========================================================================

    /**
     * Get all users as User objects (primary method for UserServiceImpl)
     * Returns complete User objects without passwords
     * @return List of User objects from cache
     */
    public List<User> getAllUsersAsUserObjects() {
        globalLock.readLock().lock();
        try {
            List<User> result = new ArrayList<>();

            for (AllUsersCacheEntry entry : statusCache.values()) {
                if (entry.isValid()) {
                    User user = entry.toUser();
                    if (user != null) {
                        result.add(user);
                    }
                }
            }

            // Sort by name for consistent ordering
            result.sort(Comparator.comparing(User::getName, String.CASE_INSENSITIVE_ORDER));

            LoggerUtil.debug(this.getClass(), "Retrieved " + result.size() + " users as User objects from cache");
            return result;

        } finally {
            globalLock.readLock().unlock();
        }
    }

    /**
     * Get specific user as User object (for UserServiceImpl)
     * @param username Username to lookup
     * @return User object from cache, or empty if not found
     */
    public Optional<User> getUserAsUserObject(String username) {
        AllUsersCacheEntry entry = statusCache.get(username);
        if (entry != null && entry.isValid()) {
            User user = entry.toUser();
            if (user != null) {
                LoggerUtil.debug(this.getClass(), "Retrieved user as User object from cache: " + username);
                return Optional.of(user);
            }
        }

        LoggerUtil.debug(this.getClass(), "User not found in cache: " + username);
        return Optional.empty();
    }

    /**
     * Get user by ID as User object (for UserServiceImpl)
     * @param userId User ID to lookup
     * @return User object from cache, or empty if not found
     */
    public Optional<User> getUserByIdAsUserObject(Integer userId) {
        globalLock.readLock().lock();
        try {
            for (AllUsersCacheEntry entry : statusCache.values()) {
                if (entry.isValid() && userId.equals(entry.getUserId())) {
                    User user = entry.toUser();
                    if (user != null) {
                        LoggerUtil.debug(this.getClass(), "Retrieved user by ID as User object from cache: " + userId);
                        return Optional.of(user);
                    }
                }
            }

            LoggerUtil.debug(this.getClass(), "User not found by ID in cache: " + userId);
            return Optional.empty();

        } finally {
            globalLock.readLock().unlock();
        }
    }

    /**
     * Update user in cache (write-through support)
     * Called after user data changes to keep cache in sync
     * @param user Updated user object
     */
    public void updateUserInCache(User user) {
        if (user == null || user.getUsername() == null) {
            LoggerUtil.warn(this.getClass(), "Cannot update cache: invalid user object");
            return;
        }

        try {
            String username = user.getUsername();
            AllUsersCacheEntry cacheEntry = statusCache.get(username);

            if (cacheEntry != null && cacheEntry.isValid()) {
                // Update existing entry
                cacheEntry.updateFromUser(user);
                LoggerUtil.debug(this.getClass(), "Updated user in cache: " + username);
            } else {
                // Create new entry from complete user data
                AllUsersCacheEntry newEntry = new AllUsersCacheEntry();
                newEntry.initializeFromCompleteUser(user, WorkCode.WORK_OFFLINE);
                statusCache.put(username, newEntry);
                LoggerUtil.info(this.getClass(), "Added new user to cache: " + username);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating user in cache for " + user.getUsername() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Remove user from cache (for user deletion)
     * @param username Username to remove
     */
    public void removeUserFromCache(String username) {
        try {
            AllUsersCacheEntry removed = statusCache.remove(username);
            boolean wasRemoved = removed != null;

            if (wasRemoved) {
                LoggerUtil.info(this.getClass(), "Removed user from cache: " + username);
            } else {
                LoggerUtil.debug(this.getClass(), "User not found in cache for removal: " + username);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error removing user from cache: " + username + " - " + e.getMessage(), e);
        }
    }

    /**
     * Get non-admin users as User objects (for UserServiceImpl)
     * @return List of non-admin User objects from cache
     */
    public List<User> getNonAdminUsersAsUserObjects() {
        return getAllUsersAsUserObjects().stream()
                .filter(user -> !user.isAdmin())
                .collect(Collectors.toList());
    }

    /**
     * Check if cache has any data (for UserServiceImpl empty state handling)
     * @return true if cache has valid user entries
     */
    public boolean hasUserData() {
        return statusCache.values().stream().anyMatch(AllUsersCacheEntry::isValid);
    }

    /**
     * Get count of cached users
     * @return Number of valid user entries in cache
     */
    public int getCachedUserCount() {
        return (int) statusCache.values().stream().filter(AllUsersCacheEntry::isValid).count();
    }

    // ========================================================================
    // INITIALIZATION AND NETWORK EVENTS
    // ========================================================================

    @PostConstruct
    public void initializeCache() {
        LoggerUtil.info(this.getClass(), "=== STARTING CACHE INITIALIZATION ===");
        // STEP 1: Initialize user context FIRST
        initializeUserContext();

        // STEP 2: Then proceed with cache initialization
        if (isInitialStartup) {
            performStartupInitialization();
            isInitialStartup = false;
        } else {
            performNormalInitialization();
        }
    }

    /**
     * Initialize user context from local user file (runs once at startup)
     * This establishes the single PC user before any session operations
     */
    private void initializeUserContext() {
        try {
            LoggerUtil.info(this.getClass(), "Scanning for local user to initialize context...");

            // Scan for ANY local user file to establish default user context
            Optional<User> localUser = userDataService.scanForAnyLocalUser();

            if (localUser.isPresent()) {
                User user = localUser.get();

                // Initialize MainDefaultUserContextService with this default user
                mainDefaultUserContextService.initializeFromUser(user);

                LoggerUtil.info(this.getClass(), String.format(
                        "Initialized user context from local file: %s (ID: %d, Role: %s)",
                        user.getUsername(), user.getUserId(), user.getRole()));

                // Now cache operations can use mainDefaultUserContextService.getCurrentUser()

            } else {
                LoggerUtil.warn(this.getClass(), "No local user file found - MainDefaultUserContextService will use system user until login");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error scanning for local user: " + e.getMessage(), e);
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
            refreshAllUsersFromUserDataServiceWithCompleteData();  // CHANGED: Method name
            syncFromNetworkFlags();
            writeToFile();

            int newUserCount = statusCache.size();
            LoggerUtil.info(this.getClass(), String.format("Status cache updated from network: %d → %d users", currentUserCount, newUserCount));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating status cache from network: " + e.getMessage(), e);
        }
    }

    private void performStartupInitialization() {
        LoggerUtil.info(this.getClass(), "Startup: Always rebuilding status cache from files...");  // CHANGED: Message

        try {
            // Always try to rebuild from scratch (like midnight reset)
            createEmptyCacheFromUserDataServiceWithCompleteData();  // CHANGED: Method name
            syncFromNetworkFlags();
            writeToFile();

            LoggerUtil.info(this.getClass(), "Startup: Successfully rebuilt cache with " + statusCache.size() + " users");

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Startup: File rebuild failed, falling back to local cache: " + e.getMessage());  // CHANGED: Message

            // File reading failed - try to load from local file as fallback using SessionDataService
            try {
                LocalStatusCache savedCache = sessionDataService.readLocalStatusCache();
                if (savedCache != null && savedCache.getUserStatuses() != null && !savedCache.getUserStatuses().isEmpty()) {
                    populateCacheFromFile(savedCache);
                    LoggerUtil.info(this.getClass(), "Startup: Using local cache as fallback with " + statusCache.size() + " users");
                } else {
                    // Even local cache failed - create minimal cache
                    createEmptyCacheFromUserDataServiceWithCompleteData();  // CHANGED: Method name
                    LoggerUtil.warn(this.getClass(), "Startup: Both file reading and local cache failed, created minimal cache");  // CHANGED: Message
                }
            } catch (Exception localError) {
                LoggerUtil.error(this.getClass(), "Startup: Complete initialization failure: " + localError.getMessage());
                createEmptyCacheFromUserDataServiceWithCompleteData(); // Last resort  // CHANGED: Method name
            }
        }
    }

    private void performNormalInitialization() {
        // Your existing smart logic using SessionDataService
        try {
            LocalStatusCache savedCache = sessionDataService.readLocalStatusCache();
            if (savedCache != null && savedCache.getUserStatuses() != null && !savedCache.getUserStatuses().isEmpty()) {
                populateCacheFromFile(savedCache);
            } else {
                rebuildCacheFromNetworkFlags();
                writeToFile();
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during normal initialization: " + e.getMessage());
            createEmptyCacheFromUserDataServiceWithCompleteData();  // CHANGED: Method name
        }
    }

    // ========================================================================
    // STATUS OPERATIONS
    // ========================================================================

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
            AllUsersCacheEntry cacheEntry = statusCache.computeIfAbsent(username, k -> new AllUsersCacheEntry());

            if (!cacheEntry.isValid()) {
                // CHANGED: Try to get complete user data from UserDataService instead of UserService
                Optional<User> userOpt = findUserInUserDataService(username, userId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    cacheEntry.initializeFromCompleteUser(user, status);
                    LoggerUtil.debug(this.getClass(), "Initialized new cache entry with complete data for user: " + username);
                } else {
                    LoggerUtil.warn(this.getClass(), "Cannot initialize cache entry for unknown user: " + username);
                    return;
                }
            }

            if (isAdminUser(cacheEntry)) {
                LoggerUtil.debug(this.getClass(), "Skipping status update for admin user: " + username);
                return;
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

            for (AllUsersCacheEntry entry : statusCache.values()) {
                if (entry.isValid() && !isAdminUser(entry)) { // NEW: Filter out admin
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

    // ========================================================================
    // CACHE REFRESH METHODS (CHANGED TO USE UserDataService)
    // ========================================================================

    /**
     * CHANGED: Update all users from UserDataService with complete data (called at midnight)
     * Refreshes user information like names, roles, and additional user data
     */
    public void refreshAllUsersFromUserDataServiceWithCompleteData() {
        globalLock.writeLock().lock();
        try {
            LoggerUtil.info(this.getClass(), "Refreshing all users from UserDataService with complete data");

            // CHANGED: Get all non-admin users with complete data from UserDataService
            List<User> allUsers = userDataService.readAllUsersForCachePopulation();
            // Get set of valid usernames for cleanup
            Set<String> validUsernames = allUsers.stream()
                    .map(User::getUsername)
                    .collect(Collectors.toSet());

            // Only remove users that no longer exist (keep admin users)
            Set<String> usernamesToRemove = new HashSet<>();
            for (String username : statusCache.keySet()) {
                if (!validUsernames.contains(username)) {
                    usernamesToRemove.add(username);
                }
            }

            // Remove only non-existent users
            for (String username : usernamesToRemove) {
                statusCache.remove(username);
                LoggerUtil.info(this.getClass(), "Removed non-existent user from status cache: " + username);
            }

            // Update/create cache entries for valid users with complete data
            for (User user : allUsers) {
                AllUsersCacheEntry cacheEntry = statusCache.computeIfAbsent(user.getUsername(), k -> new AllUsersCacheEntry());

                if (!cacheEntry.isValid()) {
                    // Initialize new entry with complete user data
                    cacheEntry.initializeFromCompleteUser(user, WorkCode.WORK_OFFLINE);
                } else {
                    // Update existing entry with new user data
                    cacheEntry.updateFromUser(user);
                }
            }

            LoggerUtil.info(this.getClass(), "Refreshed complete information for " + allUsers.size() +
                    " users and removed " + usernamesToRemove.size() + " invalid users");

        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * CHANGED: Create empty cache from UserDataService data with complete user information
     */
    private void createEmptyCacheFromUserDataServiceWithCompleteData() {
        try {
            // CHANGED: Get users from UserDataService instead of UserService
            List<User> allUsers = userDataService.readAllUsersForCachePopulation();

            for (User user : allUsers) {
                AllUsersCacheEntry cacheEntry = new AllUsersCacheEntry();
                cacheEntry.initializeFromCompleteUser(user, WorkCode.WORK_OFFLINE);
                statusCache.put(user.getUsername(), cacheEntry);
            }

            LoggerUtil.info(this.getClass(), "Created empty cache from UserDataService with complete data for " + allUsers.size() + " users");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating empty cache from UserDataService: " + e.getMessage(), e);
        }
    }

    /**
     * CHANGED: Helper method to find user in UserDataService (replaces UserService calls)
     * @param username Username to find
     * @param userId User ID (can be null)
     * @return User if found
     */
    private Optional<User> findUserInUserDataService(String username, Integer userId) {
        try {
            // Try to read from network first (login authority)
            if (userId != null) {
                Optional<User> networkUser = userDataService.adminReadUserNetworkOnly(username, userId);
                if (networkUser.isPresent()) {
                    return networkUser;
                }
            }

            // Fallback: scan all users and find by username
            List<User> allUsers = userDataService.readAllUsersForCachePopulation();
            return allUsers.stream()
                    .filter(user -> username.equals(user.getUsername()))
                    .findFirst();

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error finding user in UserDataService: " + username + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    // ========================================================================
    // EXISTING METHODS (UNCHANGED)
    // ========================================================================

    /**
     * REFACTORED: Sync status from network flags using SessionDataService
     * Updates existing cache entries without rebuilding entire cache
     */
    public void syncFromNetworkFlags() {
        try {
            if (!dataAccessService.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), "Network not available, skipping flag sync");
                return;
            }

            LoggerUtil.debug(this.getClass(), "Syncing status from network flags");

            // Read all network flag files using SessionDataService
            List<Path> flagFiles = sessionDataService.readNetworkStatusFlags();

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

                AllUsersCacheEntry cacheEntry = statusCache.get(username);
                if (cacheEntry != null && cacheEntry.isValid()) {
                    cacheEntry.updateStatus(flagInfo.getStatus(), flagInfo.getTimestamp());
                    updatedCount++;
                } else {
                    LoggerUtil.debug(this.getClass(), "No cache entry found for user with flag: " + username);
                }
            }

            // Mark users with no flags as offline
            int offlineCount = 0;
            for (AllUsersCacheEntry entry : statusCache.values()) {
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
     * REFACTORED: Write cache to local_status.json file using SessionDataService
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
            for (Map.Entry<String, AllUsersCacheEntry> entry : statusCache.entrySet()) {
                AllUsersCacheEntry cacheEntry = entry.getValue();
                if (cacheEntry.isValid()) {
                    UserStatusInfo statusInfo = cacheEntry.toUserStatusInfo();
                    if (statusInfo != null) {
                        userStatuses.put(entry.getKey(), statusInfo);
                    }
                }
            }

            cacheToSave.setUserStatuses(userStatuses);

            // Write to file using SessionDataService
            sessionDataService.writeLocalStatusCache(cacheToSave);

            LoggerUtil.info(this.getClass(), "Successfully wrote status cache to file with " + userStatuses.size() + " users");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error writing status cache to file: " + e.getMessage(), e);
        } finally {
            globalLock.readLock().unlock();
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
                    .append(", Complete: ").append(entry.hasCompleteUserData())
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

            AllUsersCacheEntry cacheEntry = new AllUsersCacheEntry();
            cacheEntry.initializeFromStatusInfo(statusInfo);
            statusCache.put(username, cacheEntry);
        }

        LoggerUtil.info(this.getClass(), "Populated cache from file with " + statusCache.size() + " users");
    }

    /**
     * Rebuild entire cache from network flags and user service
     */
    private void rebuildCacheFromNetworkFlags() {
        // CHANGED: First create entries for all users from UserDataService
        createEmptyCacheFromUserDataServiceWithCompleteData();

        // Then update with network flag data
        syncFromNetworkFlags();

        LoggerUtil.info(this.getClass(), "Rebuilt cache from network flags");
    }

    /**
     * Parse flag filename to extract user, date, time, and status information
     * Same logic as in ReadFileNameStatusService
     */
    private FlagInfo parseFlagFilename(String filename) {
        try {
            // Remove .flag extension
            filename = filename.replace(FileTypeConstants.FLAG_EXTENSION, WorkCode.EMPTY);

            // Split by underscore
            String[] parts = filename.split(WorkCode.SPLIT_UNDERSCORE);

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
            case WorkCode.TODAY: return today;
            case WorkCode.TOMORROW: return today.minusDays(1);
            case WorkCode.YESTERDAY: return today.minusDays(2);
            default:
                if (code.startsWith(WorkCode.DATE) && code.length() == 5) {
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
        if (code.startsWith(WorkCode.TIME) && code.length() == 5) {
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
            case WorkCode.WORK_ON -> WorkCode.WORK_ONLINE;
            case WorkCode.WORK_TS -> WorkCode.WORK_TEMPORARY_STOP;
            default -> WorkCode.WORK_OFFLINE;
        };
    }

    /**
     * Check if cache entry represents an admin user
     */
    private boolean isAdminUser(AllUsersCacheEntry entry) {
        return entry.getRole() != null &&
                (entry.getRole().equals(SecurityConstants.SPRING_ROLE_ADMIN) ||
                        entry.getRole().equals(SecurityConstants.ROLE_ADMIN) ||
                        entry.getUsername().equalsIgnoreCase(SecurityConstants.ADMIN_SIMPLE));
    }
}