package com.ctgraphdep.service;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.FlagInfo;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.service.cache.StatusCacheService;
import com.ctgraphdep.session.cache.SessionCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
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

/**
 * Simplified service for managing user status information via network flag files.
 * Now delegates cache operations to StatusCacheService and session reads to SessionCacheService.
 * Responsibilities:
 * 1. Managing network flag creation for local user
 * 2. Delegating cache operations to StatusCacheService
 * 3. Coordinating with SessionCacheService for session data
 */
@Service
public class ReadFileNameStatusService {

    private final UserService userService;
    private final DataAccessService dataAccessService;
    private final TimeValidationService timeValidationService;
    private final StatusCacheService statusCacheService;
    private final SessionCacheService sessionCacheService;

    // Local user tracking - keep this for flag creation logic
    private volatile User localUser;
    private final AtomicBoolean initializedLocalUser = new AtomicBoolean(false);

    // Simplified pending updates for edge cases
    private final List<PendingStatusUpdate> pendingStatusUpdates = new ArrayList<>();

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
            DataAccessService dataAccessService,
            TimeValidationService timeValidationService,
            StatusCacheService statusCacheService,
            SessionCacheService sessionCacheService) {
        this.userService = userService;
        this.dataAccessService = dataAccessService;
        this.timeValidationService = timeValidationService;
        this.statusCacheService = statusCacheService;
        this.sessionCacheService = sessionCacheService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Simplified initialization - StatusCacheService handles most of the work
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void initialize() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing ReadFileNameStatusService");

            // Load the local user for flag creation
            loadLocalUser();

            // Update current user's status from session
            updateCurrentUserStatusFromSession();

            LoggerUtil.info(this.getClass(), "ReadFileNameStatusService initialized successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing ReadFileNameStatusService: " + e.getMessage(), e);
        }
    }

    /**
     * Loads the local user on initialization or when needed
     */
    private synchronized void loadLocalUser() {
        try {
            // Clear previous user before loading
            localUser = null;

            List<User> localUsers = dataAccessService.readLocalUser();
            if (localUsers != null && !localUsers.isEmpty()) {
                localUser = localUsers.get(0);
                LoggerUtil.info(this.getClass(), "Local user loaded: " + localUser.getUsername());
            } else {
                LoggerUtil.warn(this.getClass(), "No local users found in file");
            }

            // Always mark as initialized, even if we didn't find a user
            initializedLocalUser.set(true);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to load local user: " + e.getMessage(), e);
            initializedLocalUser.set(true); // Still mark as initialized to prevent repeated attempts
        }
    }

    /**
     * Checks if the given username matches the local user
     */
    private boolean isLocalUser(String username) {
        // Force local user load if not loaded yet
        if (localUser == null || !initializedLocalUser.get()) {
            loadLocalUser();
        }

        boolean result = localUser != null && username.equals(localUser.getUsername());
        LoggerUtil.debug(this.getClass(), String.format("isLocalUser check for %s: %b (local user is %s)",
                username, result, localUser != null ? localUser.getUsername() : "null"));

        return result;
    }

    /**
     * ENHANCED: Updates the current user's status based on their session file.
     * Now uses SessionCacheService instead of direct file access.
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

            // CHANGED: Use SessionCacheService instead of direct file read
            WorkUsersSessionsStates session = sessionCacheService.readSession(username, currentUser.getUserId());

            // ENHANCED: If session cache is null, try direct file read as fallback
            if (session == null) {
                LoggerUtil.debug(this.getClass(), "Session not found in cache, attempting direct file read as fallback");
                try {
                    session = dataAccessService.readLocalSessionFileReadOnly(username, currentUser.getUserId());
                } catch (Exception e) {
                    LoggerUtil.debug(this.getClass(), "Direct file read also failed: " + e.getMessage());
                    return;
                }
            }

            if (session == null) {
                LoggerUtil.debug(this.getClass(), "No session found for user " + username + ", skipping status update");
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
                timestamp = getStandardCurrentTime();
            }

            // Update the status using cache service
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
            return null;
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), "Could not get current username: " + e.getMessage());
            return null;
        }
    }

    /**
     * SIMPLIFIED: Delegates to StatusCacheService
     */
    public List<UserStatusDTO> getAllUserStatuses() {
        try {
            // Simple read from cache - no file operations or cache validation
            return statusCacheService.getAllUserStatuses();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting user statuses: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * SIMPLIFIED: Updates user's status by creating flag and updating cache.
     * No longer handles file I/O - delegated to StatusCacheService.
     */
    public void updateUserStatus(String username, Integer userId, String status, LocalDateTime timestamp) {
        try {
            // 1. Update cache in memory ONLY (no file write)
            statusCacheService.updateUserStatus(username, userId, status, timestamp);

            // 2. Create network flag (if local user) - keep this logic
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
                    // Queue this update for later if we couldn't load user and it might be local user
                    LoggerUtil.debug(this.getClass(), String.format("Queueing status update for user %s until local user is known", username));
                    pendingStatusUpdates.add(new PendingStatusUpdate(username, userId, status, timestamp));
                }
            } else {
                // Just log without creating network flag for non-local users
                LoggerUtil.debug(this.getClass(), String.format("Updated cache only for non-local user %s to %s", username, status));
            }

            // 3. NO FILE WRITE - removed saveStatusCache() call

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
     * SIMPLIFIED: Delegates to StatusCacheService
     */
    public int getStatusCount(String status) {
        return (int) statusCacheService.getAllUserStatuses().stream()
                .filter(dto -> status.equals(dto.getStatus()))
                .count();
    }

    /**
     * SIMPLIFIED: Delegates to StatusCacheService
     */
    public int getOnlineUserCount() {
        return getStatusCount(WorkCode.WORK_ONLINE);
    }

    /**
     * SIMPLIFIED: Delegates to StatusCacheService
     */
    public int getActiveUserCount() {
        return (int) statusCacheService.getAllUserStatuses().stream()
                .filter(dto -> WorkCode.WORK_ONLINE.equals(dto.getStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(dto.getStatus()))
                .count();
    }

    /**
     * SIMPLIFIED: Triggers immediate network flag sync
     */
    public void invalidateCache() {
        try {
            statusCacheService.syncFromNetworkFlags();
            LoggerUtil.debug(this.getClass(), "Triggered status cache refresh from network flags");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error invalidating status cache: " + e.getMessage(), e);
        }
    }

    /**
     * ENHANCED: Periodically updates the current user's timestamp based on session.
     * Now uses SessionCacheService with fallback to file read.
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

            // CHANGED: Use SessionCacheService first
            WorkUsersSessionsStates session = sessionCacheService.readSession(currentUsername, currentUser.getUserId());

            // ENHANCED: Fallback to direct file read if cache is empty
            if (session == null) {
                LoggerUtil.debug(this.getClass(), "Session not in cache, trying direct file read for timestamp update");
                try {
                    session = dataAccessService.readLocalSessionFileReadOnly(currentUsername, currentUser.getUserId());
                } catch (Exception e) {
                    LoggerUtil.debug(this.getClass(), "Could not read session file: " + e.getMessage());
                    return;
                }
            }

            if (session == null) {
                return;
            }

            // Only update if user is online or temporary stop
            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) || WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {

                // Update with same status but current time
                updateUserStatus(currentUsername, currentUser.getUserId(), session.getSessionStatus(), getStandardCurrentTime());
                LoggerUtil.debug(this.getClass(), String.format("Updated timestamp for current user %s with status %s", currentUsername, session.getSessionStatus()));
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
            LocalDateTime cutoff = getStandardCurrentTime().minusHours(24);

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
            if (!dataAccessService.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), "Network not available, skipping flag cleanup");
                return;
            }

            LoggerUtil.info(this.getClass(), "Cleaning up stale status flags");

            // Calculate cutoff date (3 days old)
            LocalDate cutoffDate = getStandardCurrentDate().minusDays(3);

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

    // ===== HELPER METHODS - Keep existing flag parsing and date/time conversion methods =====

    /**
     * Parses a flag filename to extract user, date, time, and status information.
     */
    private FlagInfo parseFlagFilename(String filename) {
        // Remove .flag extension
        filename = filename.replace(FileTypeConstants.FLAG_EXTENSION, "");

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

    private LocalDateTime getStandardCurrentTime() {
        // Get standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentTime();
    }

    private LocalDate getStandardCurrentDate() {
        // Get standardized time
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentDate();
    }
}