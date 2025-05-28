package com.ctgraphdep.fileOperations;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.data.*;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Simplified DataAccessService acting as a pure facade over domain-specific services.
 * This service maintains backward compatibility while delegating all operations to specialized services.
 * All methods now use the event-driven backup system automatically.
 */
@Getter
@Service
public class DataAccessService {

    // Domain-specific services
    private final UserDataService userDataService;
    private final WorktimeDataService worktimeDataService;
    private final RegisterDataService registerDataService;
    private final SessionDataService sessionDataService;
    private final TimeOffDataService timeOffDataService;
    private final PathConfig pathConfig;

    public DataAccessService(
            UserDataService userDataService,
            WorktimeDataService worktimeDataService,
            RegisterDataService registerDataService,
            SessionDataService sessionDataService,
            TimeOffDataService timeOffDataService,
            PathConfig pathConfig) {
        this.userDataService = userDataService;
        this.worktimeDataService = worktimeDataService;
        this.registerDataService = registerDataService;
        this.sessionDataService = sessionDataService;
        this.timeOffDataService = timeOffDataService;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ===== SESSION OPERATIONS (Delegate to SessionDataService) =====

    /**
     * Writes a session file to local storage and syncs to network.
     */
    public void writeLocalSessionFile(WorkUsersSessionsStates session) {
        sessionDataService.writeLocalSessionFile(session);
    }

    /**
     * Reads a session file from local storage.
     */
    public WorkUsersSessionsStates readLocalSessionFile(String username, Integer userId) {
        return sessionDataService.readLocalSessionFile(username, userId);
    }

    /**
     * Reads a session file from the network in read-only mode.
     */
    public WorkUsersSessionsStates readNetworkSessionFileReadOnly(String username, Integer userId) {
        return sessionDataService.readNetworkSessionFileReadOnly(username, userId);
    }

    /**
     * Reads a session file from local storage in read-only mode (no locking).
     */
    public WorkUsersSessionsStates readLocalSessionFileReadOnly(String username, Integer userId) {
        return sessionDataService.readLocalSessionFileReadOnly(username, userId);
    }

    // ===== USER OPERATIONS (Delegate to UserDataService) =====

    /**
     * Reads a specific user by username and userId.
     */
    public Optional<User> readUserByUsernameAndId(String username, Integer userId) {
        return userDataService.readUserByUsernameAndId(username, userId);
    }

    /**
     * Gets a user by their user ID.
     */
    public Optional<User> getUserById(Integer userId) {
        return userDataService.getUserById(userId);
    }

    /**
     * Writes a user to their individual file with event-driven backup.
     */
    public void writeUser(User user) {
        userDataService.writeUser(user);
    }

    /**
     * Gets all users by scanning for individual files.
     */
    public List<User> getAllUsers() {
        return userDataService.getAllUsers();
    }

    /**
     * Reads users from the network and local files, maintaining backward compatibility.
     */
    @Deprecated
    public List<User> readUsersNetwork() {
        return userDataService.getAllUsers();
    }

    /**
     * Writes users to the network, maintaining backward compatibility.
     */
    @Deprecated
    public void writeUsersNetwork(List<User> users) {
        userDataService.writeUsersNetwork(users);
    }

    /**
     * Reads users from local storage, maintaining backward compatibility.
     */
    @Deprecated
    public List<User> readLocalUser() {
        return userDataService.getLocalUsers();
    }

    /**
     * Writes a user to local storage, maintaining backward compatibility.
     */
    @Deprecated
    public void writeLocalUser(User user) {
        userDataService.writeUser(user);
    }

    // ===== HOLIDAY OPERATIONS (Delegate to UserDataService) =====

    /**
     * Updates holiday days for a user in their user file.
     */
    public void updateUserHolidayDays(String username, Integer userId, Integer holidayDays) {
        userDataService.updateUserHolidayDays(username, userId, holidayDays);
    }

    /**
     * Gets a list of holiday entries by extracting information from user files.
     */
    public List<PaidHolidayEntryDTO> getUserHolidayEntries() {
        return userDataService.getUserHolidayEntries();
    }

    /**
     * Gets holiday days for a specific user.
     */
    public int getUserHolidayDays(String username, Integer userId) {
        return userDataService.getUserHolidayDays(username, userId);
    }

    /**
     * Gets a list of holiday entries for read-only purposes.
     */
    public List<PaidHolidayEntryDTO> getUserHolidayEntriesReadOnly() {
        return userDataService.getUserHolidayEntries();
    }

    /**
     * Updates holiday days for a user in their user file - admin version.
     */
    public void updateUserHolidayDaysAdmin(String username, Integer userId, Integer holidayDays) {
        userDataService.updateUserHolidayDaysAdmin(username, userId, holidayDays);
    }

    /**
     * Reads check values for a specific user.
     */
    public Optional<UsersCheckValueEntry> readUserCheckValues(String username, Integer userId) {
        return userDataService.readUserCheckValues(username, userId);
    }

    /**
     * Writes check values for a specific user.
     */
    public void writeUserCheckValues(UsersCheckValueEntry entry, String username, Integer userId) {
        userDataService.writeUserCheckValues(entry, username, userId);
    }

    /**
     * Gets all check values entries by scanning for individual files.
     */
    public List<UsersCheckValueEntry> getAllCheckValues() {
        return userDataService.getAllCheckValues();
    }


    // ===== STATUS AND CACHE OPERATIONS (Delegate to SessionDataService) =====

    /**
     * Reads local status cache.
     */
    public LocalStatusCache readLocalStatusCache() {
        return sessionDataService.readLocalStatusCache();
    }

    /**
     * Writes local status cache using event-driven backups.
     */
    public void writeLocalStatusCache(LocalStatusCache cache) {
        sessionDataService.writeLocalStatusCache(cache);
    }

    /**
     * Creates network status flag.
     */
    public void createNetworkStatusFlag(String username, String dateCode, String timeCode, String statusCode) {
        sessionDataService.createNetworkStatusFlag(username, dateCode, timeCode, statusCode);
    }

    /**
     * Reads network status flags.
     */
    public List<Path> readNetworkStatusFlags() {
        return sessionDataService.readNetworkStatusFlags();
    }

    /**
     * Deletes network status flag.
     */
    public boolean deleteNetworkStatusFlag(Path flagPath) {
        return sessionDataService.deleteNetworkStatusFlag(flagPath);
    }

    // ===== LOG OPERATIONS (Delegate to SessionDataService) =====

    /**
     * Checks if local log file exists.
     */
    public boolean localLogExists() {
        return sessionDataService.localLogExists();
    }

    /**
     * Gets the list of usernames with available logs.
     */
    public List<String> getUserLogsList() {
        return sessionDataService.getUserLogsList();
    }

    /**
     * Reads log content for a specific user.
     */
    public Optional<String> getUserLogContent(String username) {
        return sessionDataService.getUserLogContent(username);
    }

    /**
     * Gets the log filename for a specific user.
     */
    public String getLogFilename(String username) {
        return sessionDataService.getLogFilename(username);
    }

    /**
     * Extract version from log filename.
     */
    public String extractVersionFromLogFilename(String filename) {
        return sessionDataService.extractVersionFromLogFilename(filename);
    }

    /**
     * Syncs the local log file to the network.
     */
    public void syncLogToNetwork(String username, String version) throws IOException {
        sessionDataService.syncLogToNetwork(username, version);
    }

    // ===== SYSTEM UTILITY METHODS =====

    /**
     * Checks if network is available.
     */
    public boolean isNetworkAvailable() {
        return pathConfig.isNetworkAvailable();
    }

    /**
     * Checks if offline mode is available by verifying the existence of local user files.
     */
    public boolean isOfflineModeAvailable() {
        try {
            // Check for any local user files in the users directory
            Path localUsersDir = pathConfig.getLocalPath().resolve(pathConfig.getUsersPath());

            if (!Files.exists(localUsersDir)) {
                return false;
            }

            // Use try-with-resources to ensure the stream is closed properly
            try (Stream<Path> pathStream = Files.list(localUsersDir)) {
                boolean available = pathStream.anyMatch(path -> path.getFileName().toString().startsWith("local_user_") &&
                        path.getFileName().toString().endsWith(FileTypeConstants.JSON_EXTENSION));
                LoggerUtil.debug(this.getClass(), String.format("Offline mode availability: %s", available));
                return available;
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking offline mode availability: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Ensures that all required local directories exist.
     */
    public boolean ensureLocalDirectories(boolean isAdmin) {
        boolean success = true;

        try {
            // First verify/create basic user directories
            boolean userDirsOk = pathConfig.verifyUserDirectories();
            if (!userDirsOk) {
                LoggerUtil.warn(this.getClass(), "Failed to verify/create user directories");
                success = false;
            }

            // If admin, also verify/create admin directories
            if (isAdmin) {
                boolean adminDirsOk = pathConfig.verifyAdminDirectories();
                if (!adminDirsOk) {
                    LoggerUtil.warn(this.getClass(), "Failed to verify/create admin directories");
                    success = false;
                }
            }

            if (success) {
                LoggerUtil.info(this.getClass(), "Successfully ensured all required local directories exist");
            } else {
                LoggerUtil.error(this.getClass(), "One or more local directories could not be verified/created");
            }

            return success;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error ensuring local directories: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Revalidates all local directories.
     */
    public boolean revalidateLocalDirectories(boolean isAdmin) {
        try {
            // Check if local path exists, create if necessary
            if (!Files.exists(pathConfig.getLocalPath())) {
                Files.createDirectories(pathConfig.getLocalPath());
                LoggerUtil.info(this.getClass(), "Created local path: " + pathConfig.getLocalPath());
            }

            // Recreate all standard directories
            pathConfig.revalidateLocalAccess();

            // Verify/create specific directories
            return ensureLocalDirectories(isAdmin);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to revalidate local directories: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets the log path for a specific user.
     */
    public Path getLocalSessionPath(String username, Integer userId) {
        return pathConfig.getLocalSessionPath(username, userId);
    }
}