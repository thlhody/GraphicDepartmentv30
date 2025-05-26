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

    // ===== WORKTIME OPERATIONS (Delegate to WorktimeDataService) =====

    /**
     * Reads worktime data in read-only mode, falling back to local.
     */
    public List<WorkTimeTable> readWorktimeReadOnly(String username, int year, int month) {
        return worktimeDataService.readWorktimeReadOnly(username, year, month);
    }

    /**
     * Reads worktime data from the network in read-only mode.
     */
    public List<WorkTimeTable> readNetworkUserWorktimeReadOnly(String username, int year, int month) {
        return worktimeDataService.readNetworkUserWorktimeReadOnly(username, year, month);
    }

    /**
     * Reads worktime data from the appropriate source based on user access.
     */
    public List<WorkTimeTable> readUserWorktime(String username, int year, int month) {
        return worktimeDataService.readUserWorktime(username, year, month);
    }

    /**
     * Writes worktime data using event-driven backups.
     */
    public void writeUserWorktime(String username, List<WorkTimeTable> entries, int year, int month) {
        worktimeDataService.writeUserWorktime(username, entries, year, month);
    }

    /**
     * Reads worktime data with the operating username for validation.
     */
    public List<WorkTimeTable> readUserWorktime(String username, int year, int month, String operatingUsername) {
        // Validate username matches the operating user
        if (username.equals(operatingUsername)) {
            return worktimeDataService.readUserWorktime(username, year, month);
        } else {
            throw new SecurityException("Username mismatch with operating user");
        }
    }

    /**
     * Writes worktime data with the operating username for validation.
     */
    public void writeUserWorktime(String username, List<WorkTimeTable> entries, int year, int month, String operatingUsername) {
        worktimeDataService.writeUserWorktime(username, entries, year, month, operatingUsername);
    }

    // ===== ADMIN WORKTIME OPERATIONS (Delegate to WorktimeDataService) =====

    /**
     * Writes admin worktime data using event-driven backups.
     */
    public void writeAdminWorktime(List<WorkTimeTable> entries, int year, int month) {
        worktimeDataService.writeAdminWorktime(entries, year, month);
    }

    /**
     * Reads local admin worktime data.
     */
    public List<WorkTimeTable> readLocalAdminWorktime(int year, int month) {
        return worktimeDataService.readLocalAdminWorktime(year, month);
    }

    /**
     * Reads network admin worktime data.
     */
    public List<WorkTimeTable> readNetworkAdminWorktime(int year, int month) {
        return worktimeDataService.readNetworkAdminWorktime(year, month);
    }

    // ===== TIME OFF OPERATIONS (Delegate to TimeOffDataService) =====

    /**
     * Reads time off entries in read-only mode.
     */
    public List<WorkTimeTable> readTimeOffReadOnly(String username, int year) {
        return timeOffDataService.readTimeOffReadOnly(username, year);
    }

    /**
     * Reads the time off tracker.
     */
    public TimeOffTracker readTimeOffTracker(String username, Integer userId, int year) {
        return timeOffDataService.readTimeOffTracker(username, userId, year);
    }

    /**
     * Writes the time off tracker using event-driven backups.
     */
    public void writeTimeOffTracker(TimeOffTracker tracker, int year) {
        timeOffDataService.writeTimeOffTracker(tracker, year);
    }

    /**
     * Reads the time off tracker in read-only mode.
     */
    public TimeOffTracker readTimeOffTrackerReadOnly(String username, Integer userId, int year) {
        return timeOffDataService.readTimeOffTrackerReadOnly(username, userId, year);
    }

    // ===== REGISTER OPERATIONS (Delegate to RegisterDataService) =====

    /**
     * Reads register entries in read-only mode.
     */
    public List<RegisterEntry> readRegisterReadOnly(String username, Integer userId, int year, int month) {
        return registerDataService.readRegisterReadOnly(username, userId, year, month);
    }

    /**
     * Writes user register entries using event-driven backups.
     */
    public void writeUserRegister(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        registerDataService.writeUserRegister(username, userId, entries, year, month);
    }

    /**
     * Reads user register entries.
     */
    public List<RegisterEntry> readUserRegister(String username, Integer userId, int year, int month) {
        return registerDataService.readUserRegister(username, userId, year, month);
    }

    /**
     * Writes user check register entries using event-driven backups.
     */
    public void writeUserCheckRegister(String username, Integer userId, List<RegisterCheckEntry> entries, int year, int month) {
        registerDataService.writeUserCheckRegister(username, userId, entries, year, month);
    }

    /**
     * Reads user check register entries.
     */
    public List<RegisterCheckEntry> readUserCheckRegister(String username, Integer userId, int year, int month) {
        return registerDataService.readUserCheckRegister(username, userId, year, month);
    }

    /**
     * Reads check register in read-only mode.
     */
    public List<RegisterCheckEntry> readCheckRegisterReadOnly(String username, Integer userId, int year, int month) {
        return registerDataService.readCheckRegisterReadOnly(username, userId, year, month);
    }

    /**
     * Reads team members data.
     */
    public List<TeamMemberDTO> readTeamMembers(String teamLeadUsername, int year, int month) {
        return registerDataService.readTeamMembers(teamLeadUsername, year, month);
    }

    /**
     * Writes team members data using event-driven backups.
     */
    public void writeTeamMembers(List<TeamMemberDTO> teamMemberDTOS, String teamLeadUsername, int year, int month) {
        registerDataService.writeTeamMembers(teamMemberDTOS, teamLeadUsername, year, month);
    }

    /**
     * Writes team lead check register entries using event-driven backups.
     */
    public void writeLocalTeamCheckRegister(String username, Integer userId, List<RegisterCheckEntry> entries, int year, int month) {
        registerDataService.writeLocalTeamCheckRegister(username, userId, entries, year, month);
    }

    /**
     * Reads local lead check register.
     */
    public List<RegisterCheckEntry> readLocalLeadCheckRegister(String username, Integer userId, int year, int month) {
        return registerDataService.readLocalLeadCheckRegister(username, userId, year, month);
    }

    /**
     * Reads admin bonus entries.
     */
    public List<BonusEntry> readAdminBonus(int year, int month) {
        return registerDataService.readAdminBonus(year, month);
    }

    /**
     * Reads network user register.
     */
    public List<RegisterEntry> readNetworkUserRegister(String username, Integer userId, int year, int month) {
        return registerDataService.readNetworkUserRegister(username, userId, year, month);
    }

    /**
     * Reads local admin register.
     */
    public List<RegisterEntry> readLocalAdminRegister(String username, Integer userId, int year, int month) {
        return registerDataService.readLocalAdminRegister(username, userId, year, month);
    }

    /**
     * Writes local admin register using event-driven backups.
     */
    public void writeLocalAdminRegister(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        registerDataService.writeLocalAdminRegister(username, userId, entries, year, month);
    }

    /**
     * Syncs admin register to network explicitly.
     */
    public void syncAdminRegisterToNetwork(String username, Integer userId, int year, int month) {
        registerDataService.syncAdminRegisterToNetwork(username, userId, year, month);
    }

    /**
     * Writes admin bonus entries using event-driven backups.
     */
    public void writeAdminBonus(List<BonusEntry> entries, int year, int month) {
        registerDataService.writeAdminBonus(entries, year, month);
    }

    /**
     * Finds register files across multiple months.
     */
    public List<RegisterEntry> findRegisterFiles(String username, Integer userId) {
        return registerDataService.findRegisterFiles(username, userId);
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