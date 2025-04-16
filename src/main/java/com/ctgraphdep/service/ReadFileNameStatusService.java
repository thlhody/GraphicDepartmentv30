package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.FlagInfo;
import com.ctgraphdep.model.LocalStatusCache;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusInfo;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Service for managing user status information via network flag files.
 * Each application instance is responsible for:
 * 1. Managing its own user's status
 * 2. Reading the status of all users only when needed
 * 3. Using session data to update status timestamps
 * The status information is cached locally to improve performance and reduce network operations.
 */
@Service
public class ReadFileNameStatusService {

    private final UserService userService;
    private final PathConfig pathConfig;
    private final DataAccessService dataAccessService;

    // Add local user tracking
    private volatile User localUser;
    private final AtomicBoolean initializedLocalUser = new AtomicBoolean(false);

    // Queue for status updates that need to wait for user information
    private final List<PendingStatusUpdate> pendingStatusUpdates = new ArrayList<>();

    // In-memory cache
    private volatile LocalStatusCache statusCache;
    private volatile LocalDateTime cacheLastUpdated;

    // Cache expiration time: 5 minutes in milliseconds
    private static final long CACHE_TTL = 5 * 60 * 1000;

    // Class to hold pending status updates
    private static class PendingStatusUpdate {
        final String username;
        final Integer userId;
        final String status;
        final LocalDateTime timestamp;
        final LocalDateTime createdAt;

        PendingStatusUpdate(String username, Integer userId, String status, LocalDateTime timestamp) {
            this.username = username;
            this.userId = userId;
            this.status = status;
            this.timestamp = timestamp;
            this.createdAt = LocalDateTime.now();
        }
    }

    @Autowired
    public ReadFileNameStatusService(
            UserService userService,
            PathConfig pathConfig,
            DataAccessService dataAccessService) {
        this.userService = userService;
        this.pathConfig = pathConfig;
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Loads the local user on initialization or when needed
     */
    private synchronized void loadLocalUser() {
        try {
            // Only attempt to load if not already loaded
            if (localUser == null) {
                List<User> localUsers = dataAccessService.readLocalUsers();
                if (localUsers != null && !localUsers.isEmpty()) {
                    User user = localUsers.get(0);
                    localUser = user;
                    LoggerUtil.info(this.getClass(), "Local user loaded: " + user.getUsername());

                    // Process any pending status updates
                    processPendingStatusUpdates();
                } else {
                    LoggerUtil.info(this.getClass(), "No local users found in file");
                }
            }
            // Mark that we've attempted initialization
            initializedLocalUser.set(true);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to load local user: " + e.getMessage(), e);
            initializedLocalUser.set(true); // Still mark as initialized to prevent repeated attempts
        }
    }

    /**
     * Process any pending status updates now that we have the local user
     */
    private synchronized void processPendingStatusUpdates() {
        if (localUser == null || pendingStatusUpdates.isEmpty()) {
            return;
        }

        LoggerUtil.info(this.getClass(),
                String.format("Processing %d pending status updates for user %s",
                        pendingStatusUpdates.size(), localUser.getUsername()));

        // Create a copy to avoid concurrent modification
        List<PendingStatusUpdate> updates = new ArrayList<>(pendingStatusUpdates);

        // Process each pending update if it's for the local user
        for (PendingStatusUpdate update : updates) {
            if (localUser.getUsername().equals(update.username)) {
                // Process this update now
                createNetworkStatusFlagInternal(
                        update.username,
                        update.status,
                        update.timestamp
                );
                LoggerUtil.info(this.getClass(),
                        String.format("Processed pending status update for %s: %s",
                                update.username, update.status));
            } else {
                LoggerUtil.debug(this.getClass(),
                        String.format("Skipping pending update for %s as it doesn't match local user %s",
                                update.username, localUser.getUsername()));
            }
        }

        // Clear all processed updates
        pendingStatusUpdates.clear();
    }

    /**
     * Checks if the given username matches the local user
     */
    private boolean isLocalUser(String username) {
        // If local user is not initialized yet, try to load it
        if (!initializedLocalUser.get()) {
            loadLocalUser();
        }

        // After attempting to load, check if we have a user
        if (localUser == null) {
            return false;
        }

        return username != null && username.equals(localUser.getUsername());
    }

    /**
     * Initializes the service with a one-time loading of all user information.
     * Subsequent updates will be managed individually for the current user.
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void initialize() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing ReadFileNameStatusService");

            // Initialize cache if needed
            if (this.statusCache == null) {
                this.statusCache = new LocalStatusCache();
                this.statusCache.setUserStatuses(new HashMap<>());
            }

            // Load the local status cache
            loadStatusCache();

            // Load the local user
            loadLocalUser();

            // Get user list from users.json (only done once at startup)
            updateAllUsersFromUserService();

            // Initial loading of all status flags to populate the cache
            updateAllStatusFromNetworkFlags();

            // Only update local user's status from session
            updateCurrentUserStatusFromSession();

            // Save the updated cache to local file
            saveStatusCache();

            LoggerUtil.info(this.getClass(), "ReadFileNameStatusService initialized successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing ReadFileNameStatusService: " + e.getMessage(), e);
        }
    }

    /**
     * Initializes a user's status in the local cache only, without creating a network flag.
     * This is used during startup to populate the cache without affecting other users.
     */
    public void initializeUserStatusLocally(String username, Integer userId, String status, LocalDateTime timestamp) {
        try {
            // Only update local cache, no network flag creation
            updateLocalCache(username, userId, status, timestamp);
            LoggerUtil.debug(this.getClass(), "Updated status for " + username + " to " + status + " in local cache only");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing user status locally: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the current user's status based on their session file.
     * This ensures the status and session are always synchronized.
     */
    private void updateCurrentUserStatusFromSession() {
        String username = getCurrentUsername();
        if (username == null) {
            return;
        }

        try {
            // Get current user details
            User currentUser = userService.getUserByUsername(username).orElse(null);
            if (currentUser == null) {
                return;
            }

            // Read session file to determine status
            WorkUsersSessionsStates session = dataAccessService.readLocalSessionFile(username, currentUser.getUserId());
            if (session == null) {
                return;
            }

            // Update status based on session state
            String status = session.getSessionStatus();
            if (status == null) {
                status = WorkCode.WORK_OFFLINE;
            }

            // Use last activity time from session or current time if not available
            LocalDateTime timestamp = session.getLastActivity();
            if (timestamp == null) {
                timestamp = LocalDateTime.now();
            }

            // Update the status
            updateUserStatus(username, currentUser.getUserId(), status, timestamp);

            LoggerUtil.info(this.getClass(), String.format("Updated %s's status to %s based on session", username, status));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating current user status from session: %s", e.getMessage()));
        }
    }

    /**
     * Gets the currently logged-in username from security context.
     */
    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                return auth.getName();
            }
            return null; // Return null instead of throwing NPE
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), "Could not get current username: " + e.getMessage());
            return null;
        }
    }

    /**
     * Daily update of all user information from users.json.
     * This happens at midnight to avoid concurrent access issues.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void refreshUserInformation() {
        try {
            LoggerUtil.info(this.getClass(), "Performing daily refresh of all user information at midnight");

            // Force reload ALL users from UserService
            updateAllUsersFromUserService();

            // Save the updated cache
            saveStatusCache();

            LoggerUtil.info(this.getClass(), "Midnight user information refresh complete");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error refreshing user information: " + e.getMessage(), e);
        }
    }

    /**
     * Updates all users' basic information and roles from UserService.
     * This is used for the initial load and midnight refresh.
     */
    private void updateAllUsersFromUserService() {
        try {
            // Get all non-admin users
            List<User> allUsers = userService.getAllUsers().stream().filter(user -> !user.isAdmin() && !user.getRole().equals("ROLE_ADMIN") &&
                    !user.getUsername().equalsIgnoreCase("admin")).toList();

            // Ensure status cache is initialized
            if (statusCache == null) {
                statusCache = new LocalStatusCache();
                statusCache.setUserStatuses(new HashMap<>());
            }

            // Get set of valid usernames for quick lookup
            Set<String> validUsernames = allUsers.stream().map(User::getUsername).collect(Collectors.toSet());

            // Remove users that no longer exist in UserService and the admin user
            Set<String> usernamesToRemove = new HashSet<>();
            for (String username : statusCache.getUserStatuses().keySet()) {
                if (!validUsernames.contains(username) || username.equalsIgnoreCase("admin")) {
                    usernamesToRemove.add(username);
                }
            }

            // Remove the identified users
            for (String username : usernamesToRemove) {
                statusCache.getUserStatuses().remove(username);
                LoggerUtil.info(this.getClass(), username.equalsIgnoreCase("admin")
                        ? "Removed admin user from status cache" : "Removed non-existent user from status cache: " + username);
            }

            // Update user information in cache for existing users
            for (User user : allUsers) {
                // Skip admin user
                if (user.isAdmin() || user.getUsername().equalsIgnoreCase("admin")) {
                    continue;
                }

                UserStatusInfo statusInfo = statusCache.getUserStatuses().computeIfAbsent(user.getUsername(), k -> new UserStatusInfo());

                // Update basic user info
                statusInfo.setUsername(user.getUsername());
                statusInfo.setUserId(user.getUserId());
                statusInfo.setName(user.getName());

                // Add role information to cache
                statusInfo.setRole(user.getRole());

                // Only set status to offline if not already set
                if (statusInfo.getStatus() == null) {
                    statusInfo.setStatus(WorkCode.WORK_OFFLINE);
                }
            }

            LoggerUtil.info(this.getClass(), "Updated information and roles for " + allUsers.size() + " users and removed " + usernamesToRemove.size() + " non-existent or admin users");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating all users from UserService: " + e.getMessage(), e);
        }
    }

    /**
     * Gets all user statuses for display in the UI.
     * This forces a refresh of the cache if it's expired.
     * This version includes role information directly from the cache.
     */
    public List<UserStatusDTO> getAllUserStatuses() {
        try {
            // Check if cache is still valid
            if (!isCacheValid()) {
                // Refresh cache from network flags
                updateAllStatusFromNetworkFlags();
                saveStatusCache();
            }

            // Convert cache to DTOs for display, now including roles
            return convertCacheToUserStatusDTOs();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting user statuses: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Updates a user's status by creating a flag file on the network.
     * This is used by session commands to update status during actions.
     * MODIFIED: Only create network flag for local user and check if local user is initialized
     */
    public void updateUserStatus(String username, Integer userId, String status, LocalDateTime timestamp) {
        try {
            // Always update the local cache regardless of whether network flag is created
            updateLocalCache(username, userId, status, timestamp);

            // Check if we need to create a network flag (only for local user)
            if (isLocalUser(username)) {
                createNetworkStatusFlagInternal(username, status, timestamp);
                LoggerUtil.info(this.getClass(), String.format("Updated status for local user %s to %s", username, status));
            } else if (!initializedLocalUser.get()) {
                // If local user is not initialized yet, try to load it
                loadLocalUser();

                // After loading, check again if this is the local user
                if (localUser != null && username.equals(localUser.getUsername())) {
                    createNetworkStatusFlagInternal(username, status, timestamp);
                    LoggerUtil.info(this.getClass(), String.format("Updated status for newly loaded local user %s to %s", username, status));
                } else {
                    // Queue this update for later if we couldn't load user' and it might be local user
                    // This handles the case where local_users.json doesn't exist yet but will soon
                    LoggerUtil.debug(this.getClass(), String.format("Queueing status update for user %s until local user is known", username));
                    pendingStatusUpdates.add(new PendingStatusUpdate(username, userId, status, timestamp));
                }
            } else {
                // Just log without creating network flag for non-local users
                LoggerUtil.debug(this.getClass(), String.format("Updated local cache only for non-local user %s to %s", username, status));
            }

            // Save the updated cache
            saveStatusCache();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating user status: " + e.getMessage(), e);
        }
    }

    /**
     * Internal method to create a network flag file
     */
    private void createNetworkStatusFlagInternal(String username, String status, LocalDateTime timestamp) {
        // Convert status to code for flag filename
        String statusCode = getStatusCode(status);

        // Get date and time codes for filename
        String dateCode = getDateCode(timestamp.toLocalDate());
        String timeCode = "T" + timestamp.format(DateTimeFormatter.ofPattern("HHmm"));

        // Create flag file on network
        dataAccessService.createNetworkStatusFlag(username, dateCode, timeCode, statusCode);
        LoggerUtil.debug(this.getClass(), String.format("Created network status flag for user %s with status %s", username, status));
    }

    /**
     * Gets the count of users with a specific status.
     */
    public int getStatusCount(String status) {
        if (statusCache == null || statusCache.getUserStatuses() == null) {
            return 0;
        }

        return (int) statusCache.getUserStatuses().values().stream()
                .filter(info -> status.equals(info.getStatus()))
                .count();
    }

    /**
     * Gets the count of online users.
     */
    public int getOnlineUserCount() {
        return getStatusCount(WorkCode.WORK_ONLINE);
    }

    /**
     * Gets the count of active users (online or temporary stop).
     */
    public int getActiveUserCount() {
        if (statusCache == null || statusCache.getUserStatuses() == null) {
            return 0;
        }

        return (int) statusCache.getUserStatuses().values().stream()
                .filter(info -> WorkCode.WORK_ONLINE.equals(info.getStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(info.getStatus()))
                .count();
    }

    /**
     * Invalidates the cache to force a refresh.
     * Called when the user manually refreshes the status page.
     */
    public void invalidateCache() {
        cacheLastUpdated = null;
        LoggerUtil.debug(this.getClass(), "Status cache invalidated");
    }

    /**
     * Periodically updates the current user's timestamp based on session.
     * This keeps the "last active" time current without changing status.
     */
    @Scheduled(fixedRateString = "${app.status.time.update.interval:1200000}")
    public void updateCurrentUserTimestamp() {
        String currentUsername = getCurrentUsername();
        if (currentUsername == null) {
            LoggerUtil.debug(this.getClass(), "No current user, skipping timestamp update");
            return;
        }

        try {
            // Get current user from security context
            User currentUser = userService.getUserByUsername(currentUsername).orElse(null);
            if (currentUser == null) {
                return;
            }

            // Get current session to determine status
            WorkUsersSessionsStates session;
            try {
                session = dataAccessService.readLocalSessionFile(currentUsername, currentUser.getUserId());
            } catch (Exception e) {
                LoggerUtil.debug(this.getClass(), "Could not read session file: " + e.getMessage());
                return;
            }

            if (session == null) {
                return;
            }

            // Only update if user is online or temporary stop
            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                    WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {

                // Update with same status but current time
                updateUserStatus(
                        currentUsername,
                        currentUser.getUserId(),
                        session.getSessionStatus(),
                        LocalDateTime.now()
                );

                LoggerUtil.debug(this.getClass(),
                        String.format("Updated timestamp for current user %s with status %s",
                                currentUsername, session.getSessionStatus()));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating current user timestamp: " + e.getMessage(), e);
        }
    }

    /**
     * Scheduled task to check for pending status updates that are old
     * and clean them up if they're over 24 hours old
     */
    @Scheduled(fixedRate = 3600000) // Run hourly
    public void cleanPendingStatusUpdates() {
        synchronized (pendingStatusUpdates) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

            // Create a new list with only non-expired updates
            List<PendingStatusUpdate> validUpdates = pendingStatusUpdates.stream()
                    .filter(update -> update.createdAt.isAfter(cutoff))
                    .toList();

            int removed = pendingStatusUpdates.size() - validUpdates.size();

            if (removed > 0) {
                LoggerUtil.info(this.getClass(), String.format("Cleaned up %d expired pending status updates", removed));
                pendingStatusUpdates.clear();
                pendingStatusUpdates.addAll(validUpdates);
            }
        }
    }

    /**
     * Cleans up stale status flag files on the network.
     * Prevents directory clutter by removing old offline status flags.
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupStaleFlags() {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), "Network not available, skipping flag cleanup");
                return;
            }

            LoggerUtil.info(this.getClass(), "Cleaning up stale status flags");

            // Calculate cutoff date (3 days old)
            LocalDate cutoffDate = LocalDate.now().minusDays(3);

            // Get all network flag files
            List<Path> flagFiles = dataAccessService.readNetworkStatusFlags();

            int removedCount = 0;

            for (Path flagPath : flagFiles) {
                String filename = flagPath.getFileName().toString();

                try {
                    FlagInfo flagInfo = parseFlagFilename(filename);

                    if (flagInfo != null && flagInfo.getTimestamp().toLocalDate().isBefore(cutoffDate)) {
                        // Only remove flag if old and the user is offline
                        if (flagInfo.getStatus().equals(WorkCode.WORK_OFFLINE)) {
                            // Delete the flag file
                            if (dataAccessService.deleteNetworkStatusFlag(flagPath)) {
                                removedCount++;
                            }
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Error processing flag during cleanup: " + filename);
                }
            }

            LoggerUtil.info(this.getClass(), "Removed " + removedCount + " stale flag files");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error cleaning up stale flags: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the status cache from local file.
     */
    private void loadStatusCache() {
        statusCache = dataAccessService.readLocalStatusCache();
        if (statusCache == null) {
            statusCache = new LocalStatusCache();
            statusCache.setUserStatuses(new HashMap<>());
        }
        cacheLastUpdated = LocalDateTime.now();
    }

    /**
     * Saves the status cache to local file.
     */
    public void saveStatusCache() {
        try {
            // Ensure statusCache is initialized
            if (this.statusCache == null) {
                this.statusCache = new LocalStatusCache();
                this.statusCache.setUserStatuses(new HashMap<>());
            }
            statusCache.setLastUpdated(LocalDateTime.now());
            dataAccessService.writeLocalStatusCache(statusCache);
            cacheLastUpdated = LocalDateTime.now();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving status cache: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the cache is still valid based on TTL.
     */
    private boolean isCacheValid() {
        return cacheLastUpdated != null &&
                System.currentTimeMillis() - cacheLastUpdated.toEpochSecond(java.time.ZoneOffset.UTC) * 1000 < CACHE_TTL;
    }

    /**
     * Updates all users' status information from network flags.
     * Used for the initial load and when the status page is refreshed.
     */
    private void updateAllStatusFromNetworkFlags() {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), "Network not available, cannot update from flag files");
                return;
            }

            // Ensure statusCache is initialized
            if (this.statusCache == null) {
                this.statusCache = new LocalStatusCache();
                this.statusCache.setUserStatuses(new HashMap<>());
            }

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
                        if (!latestFlags.containsKey(username) || flagInfo.getTimestamp().isAfter(latestFlags.get(username).getTimestamp())) {
                            latestFlags.put(username, flagInfo);
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Error parsing flag: " + filename);
                }
            }

            // Update cache with flag information
            for (Map.Entry<String, FlagInfo> entry : latestFlags.entrySet()) {
                String username = entry.getKey();
                FlagInfo flagInfo = entry.getValue();

                // Get user
                userService.getUserByUsername(username).ifPresent(user -> updateLocalCache(
                        username,
                        user.getUserId(),
                        flagInfo.getStatus(),
                        flagInfo.getTimestamp()
                ));
            }

            // Mark users with no flags as offline
            for (Map.Entry<String, UserStatusInfo> entry : statusCache.getUserStatuses().entrySet()) {
                String username = entry.getKey();

                if (!latestFlags.containsKey(username)) {
                    UserStatusInfo statusInfo = entry.getValue();
                    statusInfo.setStatus(WorkCode.WORK_OFFLINE);
                }
            }

            LoggerUtil.debug(this.getClass(), "Updated all statuses from " + flagFiles.size() + " network flag files");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating all status from network flags: " + e.getMessage(), e);
        }
    }

    /**
     * Updates a user's status in the local cache.
     */
    private void updateLocalCache(String username, Integer userId, String status, LocalDateTime timestamp) {
        if (statusCache == null) {
            statusCache = new LocalStatusCache();
            statusCache.setUserStatuses(new HashMap<>());
        }

        UserStatusInfo statusInfo = statusCache.getUserStatuses().computeIfAbsent(username, k -> new UserStatusInfo());

        // Update info
        statusInfo.setUsername(username);
        statusInfo.setUserId(userId);

        // Get username if not set
        if (statusInfo.getName() == null || statusInfo.getName().isEmpty()) {
            userService.getUserByUsername(username).ifPresent(user -> statusInfo.setName(user.getName()));
        }

        statusInfo.setStatus(status);
        statusInfo.setLastActive(timestamp);
    }

    /**
     * Converts the cache to UserStatusDTO objects for display in the UI.
     * Now includes role information from the cache.
     */
    private List<UserStatusDTO> convertCacheToUserStatusDTOs() {
        List<UserStatusDTO> result = new ArrayList<>();

        if (statusCache == null || statusCache.getUserStatuses() == null) {
            return result;
        }

        for (UserStatusInfo info : statusCache.getUserStatuses().values()) {
            result.add(UserStatusDTO.builder()
                    .username(info.getUsername())
                    .userId(info.getUserId())
                    .name(info.getName())
                    .status(info.getStatus())
                    .lastActive(formatDateTime(info.getLastActive()))
                    .role(info.getRole()) // Include role from cache
                    .build());
        }

        // Sort by status and name
        result.sort(Comparator.comparing((UserStatusDTO dto) -> {
                    // First level sorting - by status with custom order
                    if (WorkCode.WORK_ONLINE.equals(dto.getStatus())) return 1;
                    if (WorkCode.WORK_TEMPORARY_STOP.equals(dto.getStatus())) return 2;
                    return 3; // All other statuses
                })
                .thenComparing(UserStatusDTO::getName, String.CASE_INSENSITIVE_ORDER));

        return result;
    }

    /**
     * Formats a datetime for display.
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return WorkCode.LAST_ACTIVE_NEVER;
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Parses a flag filename to extract user, date, time, and status information.
     */
    private FlagInfo parseFlagFilename(String filename) {
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
    }

    /**
     * Converts a date to a code for flag filenames.
     */
    private String getDateCode(LocalDate date) {
        LocalDate today = LocalDate.now();

        if (date.equals(today)) {
            return "1a1";
        } else if (date.equals(today.minusDays(1))) {
            return "1b1";
        } else if (date.equals(today.minusDays(2))) {
            return "1c1";
        } else {
            // For older dates, use a different pattern
            return "X" + date.format(DateTimeFormatter.ofPattern("MMdd"));
        }
    }

    /**
     * Converts a date code from a flag filename to a LocalDate.
     */
    private LocalDate getDateFromCode(String code) {
        LocalDate today = LocalDate.now();

        switch (code) {
            case "1a1": return today;
            case "1b1": return today.minusDays(1);
            case "1c1": return today.minusDays(2);
            default:
                if (code.startsWith("X") && code.length() == 5) {
                    // Extract month and day from Xmmdd
                    try {
                        int month = Integer.parseInt(code.substring(1, 3));
                        int day = Integer.parseInt(code.substring(3, 5));
                        return LocalDate.of(today.getYear(), month, day);
                    } catch (Exception e) {
                        return today; // Default to today if parsing fails
                    }
                } else {
                    return today; // Default to today if format is unknown
                }
        }
    }

    /**
     * Converts a time code from a flag filename to a LocalTime.
     */
    private LocalTime getTimeFromCode(String code) {
        if (code.startsWith("T") && code.length() == 5) {
            try {
                int hour = Integer.parseInt(code.substring(1, 3));
                int minute = Integer.parseInt(code.substring(3, 5));
                return LocalTime.of(hour, minute);
            } catch (Exception e) {
                return LocalTime.NOON; // Default to noon if parsing fails
            }
        }
        return LocalTime.NOON;
    }

    /**
     * Converts a status to a code for flag filenames.
     */
    private String getStatusCode(String status) {
        if (status == null) return "OF";

        return switch (status) {
            case WorkCode.WORK_ONLINE -> "ON";
            case WorkCode.WORK_TEMPORARY_STOP -> "TS";
            default -> "OF";
        };
    }

    /**
     * Converts a status code from a flag filename to a full status.
     */
    private String getStatusFromCode(String code) {
        return switch (code) {
            case "ON" -> WorkCode.WORK_ONLINE;
            case "TS" -> WorkCode.WORK_TEMPORARY_STOP;
            default -> WorkCode.WORK_OFFLINE;
        };
    }
}