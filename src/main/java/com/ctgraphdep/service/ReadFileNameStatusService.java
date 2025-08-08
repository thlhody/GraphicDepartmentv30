package com.ctgraphdep.service;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.model.FlagInfo;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.service.cache.SessionCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.file.Path;

/**
 * Service for managing user status information via network flag files.
 * Now uses SessionDataService for all status and flag operations instead of DataAccessService.
 * Delegates cache operations to AllUsersCacheService and session reads to SessionCacheService.
 * Responsibilities:
 * 1. Managing network flag creation for local user
 * 2. Delegating cache operations to AllUsersCacheService
 * 3. Coordinating with SessionCacheService for session data
 */
@Service
public class ReadFileNameStatusService {

    private final TimeValidationService timeValidationService;
    private final AllUsersCacheService allUsersCacheService;
    private final SessionCacheService sessionCacheService;
    private final SessionDataService sessionDataService;
    private final DataAccessService dataAccessService;
    private final MainDefaultUserContextService mainDefaultUserContextService;

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
    public ReadFileNameStatusService(TimeValidationService timeValidationService, AllUsersCacheService allUsersCacheService, SessionCacheService sessionCacheService,
                                     SessionDataService sessionDataService, DataAccessService dataAccessService, MainDefaultUserContextService mainDefaultUserContextService) {
        this.timeValidationService = timeValidationService;
        this.allUsersCacheService = allUsersCacheService;
        this.sessionCacheService = sessionCacheService;
        this.sessionDataService = sessionDataService;      // NEW
        this.dataAccessService = dataAccessService;        // Keep for isNetworkAvailable()
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    //AllUsersCacheService handles most of the work
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void initialize() {
        try {
            LoggerUtil.info(this.getClass(), "Initializing ReadFileNameStatusService");

            // Update current user's status from session
            updateCurrentUserStatusFromSession();

            LoggerUtil.info(this.getClass(), "ReadFileNameStatusService initialized successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing ReadFileNameStatusService: " + e.getMessage(), e);
        }
    }

    // Gets the current user - always available from MainDefaultUserContextService
    private User getCurrentUser() {
        return mainDefaultUserContextService.getCurrentUser();
    }

    //Updates the current user's status based on their session file.
    private void updateCurrentUserStatusFromSession() {
        User user = getCurrentUser();
        if (user == null) {
            LoggerUtil.warn(this.getClass(), "No current user available");
            return;
        }

        String username = user.getUsername();
        Integer userId = user.getUserId();

        try {
            // Use SessionCacheService to read session
            WorkUsersSessionsStates session = sessionCacheService.readSessionWithFallback(username, userId);

            // Fallback to direct file read if needed
            if (session == null) {
                session = sessionDataService.readLocalSessionFileReadOnly(username, userId);
            }

            if (session == null) {
                LoggerUtil.debug(this.getClass(), "No session found for user " + username);
                return;
            }

            // Update status based on session
            String status = session.getSessionStatus();
            if (status == null) {
                status = WorkCode.WORK_OFFLINE;
            }

            LocalDateTime timestamp = session.getLastActivity();
            if (timestamp == null) {
                timestamp = getStandardCurrentTime();
            }

            // ALWAYS create network flag since this is THE local user
            updateUserStatus(username, userId, status, timestamp);

            LoggerUtil.info(this.getClass(),
                    String.format("Updated %s's status to %s based on session", username, status));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error updating current user status from session: " + e.getMessage());
        }
    }

    // Delegates to AllUsersCacheService
    public List<UserStatusDTO> getAllUserStatuses() {
        try {
            // Simple read from cache - no file operations or cache validation
            return allUsersCacheService.getAllUserStatuses();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting user statuses: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    //Updates user's status by creating flag and updating cache. Now uses SessionDataService for flag operations.
    public void updateUserStatus(String username, Integer userId, String status, LocalDateTime timestamp) {
        try {
            // 1. Update cache in memory
            allUsersCacheService.updateUserStatus(username, userId, status, timestamp);

            // 2. ALWAYS create network flag (this is THE local user)
            createNetworkStatusFlagInternal(username, status, timestamp);

            LoggerUtil.info(this.getClass(),
                    String.format("Updated status for user %s to %s", username, status));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating user status: " + e.getMessage(), e);
        }
    }

    //Internal method to create a network flag file using SessionDataService
    private void createNetworkStatusFlagInternal(String username, String status, LocalDateTime timestamp) {
        // Convert status to code for flag filename
        String statusCode = getStatusCode(status);

        // Get date and time codes for filename
        String dateCode = getDateCode(timestamp.toLocalDate());
        String timeCode = "T" + timestamp.format(DateTimeFormatter.ofPattern("HHmm"));

        // Create flag file on network using SessionDataService
        sessionDataService.createNetworkStatusFlag(username, dateCode, timeCode, statusCode);
        LoggerUtil.debug(this.getClass(), String.format("Created network status flag for user %s with status %s", username, status));
    }

    // Delegates to AllUsersCacheService
    public int getStatusCount(String status) {
        return (int) allUsersCacheService.getAllUserStatuses().stream()
                .filter(dto -> status.equals(dto.getStatus()))
                .count();
    }

    //Delegates to AllUsersCacheService
    public int getOnlineUserCount() {
        return getStatusCount(WorkCode.WORK_ONLINE);
    }

    // Triggers immediate network flag sync
    public void invalidateCache() {
        try {
            allUsersCacheService.syncFromNetworkFlags();
            LoggerUtil.debug(this.getClass(), "Triggered status cache refresh from network flags");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error invalidating status cache: " + e.getMessage(), e);
        }
    }

    //Periodically updates the current user's timestamp based on session. Now uses SessionCacheService with fallback to SessionDataService.
    @Scheduled(fixedRateString = "${app.status.time.update.interval:1200000}")
    public void updateCurrentUserTimestamp() {
        User user = getCurrentUser();
        if (user == null) {
            LoggerUtil.debug(this.getClass(), "No current user, skipping timestamp update");
            return;
        }

        String username = user.getUsername();
        Integer userId = user.getUserId();

        try {
            // Read session from cache
            WorkUsersSessionsStates session = sessionCacheService.readSessionWithFallback(username, userId);

            // Fallback to file read
            if (session == null) {
                session = sessionDataService.readLocalSessionFileReadOnly(username, userId);
            }

            if (session == null) {
                return;
            }

            // Only update if user is active
            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                    WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {

                updateUserStatus(username, userId, session.getSessionStatus(), getStandardCurrentTime());
                LoggerUtil.debug(this.getClass(),
                        String.format("Updated timestamp for user %s with status %s", username, session.getSessionStatus()));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating current user timestamp: " + e.getMessage(), e);
        }
    }

    // Scheduled task to check for pending status updates that are old and clean them up if they're over 24 hours old
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

    //Cleans up stale status flag files on the network using SessionDataService.
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

            // Get all network flag files using SessionDataService
            List<Path> flagFiles = sessionDataService.readNetworkStatusFlags();

            int removedCount = 0;

            for (Path flagPath : flagFiles) {
                String filename = flagPath.getFileName().toString();

                try {
                    FlagInfo flagInfo = parseFlagFilename(filename);

                    if (flagInfo != null && flagInfo.getTimestamp().toLocalDate().isBefore(cutoffDate)) {
                        // Only remove flag if old and the user is offline
                        if (flagInfo.getStatus().equals(WorkCode.WORK_OFFLINE)) {
                            // Delete the flag file using SessionDataService
                            if (sessionDataService.deleteNetworkStatusFlag(flagPath)) {
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

    // ===== HELPER METHODS =====

    // Parses a flag filename to extract user, date, time, and status information.
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

    // Converts a date to a code for flag filenames.
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

    // Converts a date code from a flag filename to a LocalDate.
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

    // Converts a time code from a flag filename to a LocalTime.
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

    // Converts a status to a code for flag filenames.
    private String getStatusCode(String status) {
        if (status == null) return "OF";

        return switch (status) {
            case WorkCode.WORK_ONLINE -> "ON";
            case WorkCode.WORK_TEMPORARY_STOP -> "TS";
            default -> "OF";
        };
    }

    // Converts a status code from a flag filename to a full status.
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