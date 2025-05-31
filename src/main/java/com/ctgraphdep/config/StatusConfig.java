package com.ctgraphdep.config;

import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.StatusCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * REFACTORED: Configuration for the user status system.
 * Now works with StatusCacheService instead of direct ReadFileNameStatusService calls.
 * This provides secondary initialization and verification after the main StatusCacheService
 */
@Configuration
public class StatusConfig {

    private final StatusCacheService statusCacheService; // CHANGED: Use StatusCacheService directly
    private final UserService userService;
    private final TimeValidationService timeValidationService;

    @Autowired
    public StatusConfig(StatusCacheService statusCacheService, UserService userService, TimeValidationService timeValidationService) {
        this.statusCacheService = statusCacheService; // CHANGED
        this.userService = userService;
        this.timeValidationService = timeValidationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * ENHANCED: Initialize and verify the user status system after application is fully ready.
     * This runs after StatusCacheService @PostConstruct initialization to ensure all users
     * are properly represented in the cache AND the local user's status is broadcast.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeAndVerifyUserStatuses() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing user status system after application ready...");

            // Get current cache status for logging
            String cacheStatusBefore = statusCacheService.getCacheStatus();
            LoggerUtil.debug(this.getClass(), "Cache status before initialization:\n" + cacheStatusBefore);

            // STEP 1: Get all users from UserService
            var allUsers = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin() &&
                            !user.getRole().equals(SecurityConstants.SPRING_ROLE_ADMIN) &&
                            !user.getUsername().equalsIgnoreCase(SecurityConstants.ADMIN_SIMPLE))
                    .toList();

            LoggerUtil.info(this.getClass(), String.format("Found %d non-admin users to initialize in status cache", allUsers.size()));

            // STEP 2: Initialize ALL users in cache with offline status
            int usersProcessed = 0;
            LocalDateTime currentTime = getStandardCurrentTime();

            for (var user : allUsers) {
                try {
                    // Initialize all users with offline status and current timestamp
                    // This ensures every user has a cache entry and recent activity time
                    statusCacheService.updateUserStatus(user.getUsername(), user.getUserId(),
                            WorkCode.WORK_OFFLINE, currentTime);

                    usersProcessed++;
                    LoggerUtil.debug(this.getClass(), String.format("Initialized user in cache: %s", user.getUsername()));
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Error initializing status for user %s: %s",
                            user.getUsername(), e.getMessage()));
                }
            }

            // STEP 3: Refresh all user data from UserService (names, roles, etc.)
            statusCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();
            LoggerUtil.info(this.getClass(), "Refreshed user data from UserService");

            // STEP 4: Sync from network flags to get any existing status updates
            statusCacheService.syncFromNetworkFlags();
            LoggerUtil.info(this.getClass(), "Synced existing status from network flags");

            // STEP 5: Persist the initialized and updated cache
            statusCacheService.writeToFile();
            LoggerUtil.info(this.getClass(), "Persisted status cache to file");

            // STEP 6: NEW - Ensure the local user's offline status is broadcast via network flag
            ensureLocalUserStatusBroadcast();

            // Get final cache status for comparison
            String cacheStatusAfter = statusCacheService.getCacheStatus();
            LoggerUtil.debug(this.getClass(), "Cache status after initialization:\n" + cacheStatusAfter);

            LoggerUtil.info(this.getClass(), String.format(
                    "User status system initialization complete - Processed %d users, refreshed data, synced flags, persisted cache, and broadcast local user status",
                    usersProcessed));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during user status system initialization: " + e.getMessage(), e);
        }
    }

    /**
     * NEW: Ensures the local user's offline status is properly broadcast via network flag
     * This makes sure other app instances can see this user's initial offline state
     */
    private void ensureLocalUserStatusBroadcast() {
        try {
            // Find the local user by checking which user has local files
            var allUsers = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin() &&
                            !user.getRole().equals(SecurityConstants.SPRING_ROLE_ADMIN) &&
                            !user.getUsername().equalsIgnoreCase(SecurityConstants.ADMIN_SIMPLE))
                    .toList();

            for (var user : allUsers) {
                try {
                    // Check if this user has a local session file (indicating local user)
                    if (hasLocalSessionFile(user.getUsername(), user.getUserId())) {
                        LoggerUtil.info(this.getClass(), String.format("Found local user: %s - broadcasting offline status", user.getUsername()));

                        // Update status which will create network flag for local user
                        statusCacheService.updateUserStatus(user.getUsername(), user.getUserId(),
                                WorkCode.WORK_OFFLINE, getStandardCurrentTime());

                        LoggerUtil.info(this.getClass(), String.format("Broadcast offline status for local user: %s", user.getUsername()));
                        break; // Only one local user per instance
                    }
                } catch (Exception e) {
                    LoggerUtil.debug(this.getClass(), String.format("Error checking local files for user %s: %s",
                            user.getUsername(), e.getMessage()));
                    // Continue checking other users
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error ensuring local user status broadcast: " + e.getMessage(), e);
        }
    }

    /**
     * NEW: Helper method to check if a user has local session files
     * This helps identify the local user for this app instance
     */
    private boolean hasLocalSessionFile(String username, Integer userId) {
        try {
            // Try to get local session path and check if file exists
            Path sessionPath = Paths.get(System.getProperty("user.home"), "CTTT", "user_session",
                    String.format("session_%s_%d.json", username, userId));

            return Files.exists(sessionPath);
        } catch (Exception e) {
            // If we can't determine, assume not local
            return false;
        }
    }

    /**
     * NEW: Manual cache validation method that can be called for troubleshooting
     * This can be exposed via admin interface or used for debugging
     */
    public void performManualCacheValidation() {
        try {
            LoggerUtil.info(this.getClass(), "Performing manual cache validation...");

            // Get current user count from UserService
            long userServiceCount = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin() &&
                            !user.getRole().equals(SecurityConstants.SPRING_ROLE_ADMIN) &&
                            !user.getUsername().equalsIgnoreCase(SecurityConstants.ADMIN_SIMPLE))
                    .count();

            // Get current cache count
            long cacheCount = statusCacheService.getAllUserStatuses().size();

            LoggerUtil.info(this.getClass(), String.format(
                    "Cache validation - UserService: %d users, Cache: %d users",
                    userServiceCount, cacheCount));

            if (userServiceCount != cacheCount) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Cache count mismatch detected! UserService: %d, Cache: %d - triggering refresh",
                        userServiceCount, cacheCount));

                // Refresh cache to fix mismatch
                statusCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();
                statusCacheService.writeToFile();

                long newCacheCount = statusCacheService.getAllUserStatuses().size();
                LoggerUtil.info(this.getClass(), String.format(
                        "Cache refresh completed - New cache count: %d", newCacheCount));
            } else {
                LoggerUtil.info(this.getClass(), "Cache validation passed - counts match");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during manual cache validation: " + e.getMessage(), e);
        }
    }

    /**
     * NEW: Get cache health status for monitoring
     */
    public String getCacheHealthStatus() {
        try {
            StringBuilder health = new StringBuilder();
            health.append("Status Cache Health Report:\n");

            // User service count
            long userServiceCount = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin() &&
                            !user.getRole().equals(SecurityConstants.SPRING_ROLE_ADMIN) &&
                            !user.getUsername().equalsIgnoreCase(SecurityConstants.ADMIN_SIMPLE))
                    .count();

            // Cache count
            long cacheCount = statusCacheService.getAllUserStatuses().size();

            health.append(String.format("UserService Users: %d\n", userServiceCount));
            health.append(String.format("Cached Users: %d\n", cacheCount));
            health.append(String.format("Count Match: %s\n", userServiceCount == cacheCount ? "✓" : "✗"));

            // Cache details
            health.append("\nCache Details:\n");
            health.append(statusCacheService.getCacheStatus());

            return health.toString();

        } catch (Exception e) {
            return "Error getting cache health status: " + e.getMessage();
        }
    }

    /**
     * NEW: Emergency cache rebuild method
     * Can be called if cache gets corrupted or needs complete reset
     */
    public void performEmergencyCacheRebuild() {
        try {
            LoggerUtil.warn(this.getClass(), "Performing emergency cache rebuild...");

            // Clear all cache
            statusCacheService.clearAllCache();

            // Rebuild from UserService
            statusCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();

            // Sync from network flags
            statusCacheService.syncFromNetworkFlags();

            // Persist rebuilt cache
            statusCacheService.writeToFile();

            LoggerUtil.info(this.getClass(), "Emergency cache rebuild completed successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during emergency cache rebuild: " + e.getMessage(), e);
        }
    }

    private LocalDateTime getStandardCurrentTime() {
        // Get standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentTime();
    }
}