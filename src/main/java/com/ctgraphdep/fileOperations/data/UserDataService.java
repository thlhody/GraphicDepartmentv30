package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UsersCheckValueEntry;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * REFACTORED UserDataService following RegisterDataService pattern.
 * Key Principles:
 * - Pure file I/O operations, no business logic
 * - Explicit backup and sync control
 * - Clear admin vs user operation patterns
 * - Smart fallback with sync-to-local when needed (using SyncFilesService)
 * - Individual user file focus (no batch operations)
 * File Patterns:
 * - Admin domain: user_[username]_[userId].json (network login files)
 * - User domain: local_user_[username]_[userId].json (working copies)
 * - Check values: users_check_value_[username]_[userId].json (both domains)
 * Sync Logic:
 * - Normal flow: Local → Network (local is source of truth for user changes)
 * - Missing local: Network → Local (bootstrap local from network)
 * - After bootstrap: Resume normal Local → Network flow
 */
@Service
public class UserDataService {

    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final PathConfig pathConfig;

    public UserDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            PathConfig pathConfig) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Gets all users from network by scanning individual files (for StatusCache).
     * Pattern: Network-only scan for cache population
     * Files: user_[username]_[userId].json
     *
     * @return List of all users from network files
     */
    public List<User> readAllUsersForCachePopulation() {
        return adminReadAllUsersNetworkOnly();
    }

    /**
     * Gets user count from network (for cache validation).
     * Pattern: Network-only count
     *
     * @return Number of users in network
     */
    public int getUserCountFromNetwork() {
        try {
            return adminReadAllUsersNetworkOnly().size();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting user count from network: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Scans for any local user files (for MainDefaultUserContextCache single-user pattern).
     * Pattern: Local scan for any local_user_*.json
     * Files: local_user_[username]_[userId].json
     *
     * @return First non-admin user found, or empty if none found
     */
    public Optional<User> scanForAnyLocalUser() {
        try {
            Path localUsersDir = pathConfig.getLocalPath().resolve(pathConfig.getUsersPath());

            if (!Files.exists(localUsersDir) || !Files.isDirectory(localUsersDir)) {
                LoggerUtil.debug(this.getClass(), "Local users directory does not exist: " + localUsersDir);
                return Optional.empty();
            }

            try (Stream<Path> files = Files.list(localUsersDir)) {
                Optional<Path> userFile = files
                        .filter(path -> {
                            String fileName = path.getFileName().toString();
                            return fileName.startsWith("local_user_") &&
                                    fileName.endsWith(FileTypeConstants.JSON_EXTENSION);
                        })
                        .findFirst();

                if (userFile.isPresent()) {
                    Path filePath = userFile.get();
                    LoggerUtil.debug(this.getClass(), "Scanning local user file: " + filePath);

                    FilePath localPath = FilePath.local(filePath);
                    Optional<User> user = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

                    if (user.isPresent() && !user.get().isAdmin()) {
                        LoggerUtil.info(this.getClass(), String.format(
                                "Found local user via scan: %s (ID: %d, Role: %s)",
                                user.get().getUsername(), user.get().getUserId(), user.get().getRole()));
                        return user;
                    } else user.ifPresent(value -> LoggerUtil.debug(this.getClass(), String.format(
                            "Skipping admin user from local scan: %s", value.getUsername())));
                } else {
                    LoggerUtil.debug(this.getClass(), "No local_user_*.json files found in: " + localUsersDir);
                }
            }

            return Optional.empty();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error scanning for local users: " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    // ========================================================================
    // ADMIN USER OPERATIONS (NETWORK FILES - LOGIN AUTHORITY)
    // ========================================================================

    /**
     * Writes user to network with explicit backup control (admin only).
     * Pattern: Network direct write with one backup, no sync
     * File: user_[username]_[userId].json
     *
     * @param user User to write
     */
    public void adminWriteUserNetworkOnly(User user) {
        if (user == null || user.getUsername() == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User must have username and userId for network file storage");
        }

        try {
            FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(user.getUsername(), user.getUserId()));

            // Direct network write with one backup, no sync
            FileOperationResult result = fileWriterService.writeFile(networkPath, user, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write user to network: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Admin wrote user to network: %s (ID: %d)", user.getUsername(), user.getUserId()));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error in admin network write for user %s: %s", user.getUsername(), e.getMessage()), e);
        }
    }

    /**
     * Reads user from network only (admin operations).
     * Pattern: Network-only, no fallback, no sync, no local operations
     * File: user_[username]_[userId].json
     *
     * @param username Username
     * @param userId User ID
     * @return User from network, or empty if not found
     */
    public Optional<User> adminReadUserNetworkOnly(String username, Integer userId) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format("Network not available for admin user read %s-%d", username, userId));
                return Optional.empty();
            }

            FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(username, userId));
            Optional<User> networkUser = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

            if (networkUser.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format("Admin read user from network: %s (ID: %d)", username, userId));
                return networkUser;
            } else {
                LoggerUtil.debug(this.getClass(), String.format("Admin network read: user not found %s-%d", username, userId));
                return Optional.empty();
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Error in admin network read for user %s-%d: %s", username, userId, e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Reads all users from network by scanning individual files (admin operations).
     * Pattern: Network-only scan, no local involvement
     * Files: user_[username]_[userId].json
     *
     * @return List of all users from network files
     */
    public List<User> adminReadAllUsersNetworkOnly() {
        List<User> allUsers = new ArrayList<>();

        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), "Network not available for admin user scan");
                return allUsers;
            }

            Path networkUsersDir = pathConfig.getNetworkPath().resolve(pathConfig.getUsersPath());

            if (Files.exists(networkUsersDir) && Files.isDirectory(networkUsersDir)) {
                try (Stream<Path> files = Files.list(networkUsersDir)) {
                    files.filter(path -> {
                                String fileName = path.getFileName().toString();
                                return fileName.startsWith("user_") &&
                                        fileName.endsWith(FileTypeConstants.JSON_EXTENSION);
                            })
                            .forEach(path -> {
                                try {
                                    FilePath filePath = FilePath.network(path);
                                    Optional<User> user = fileReaderService.readNetworkFile(filePath, new TypeReference<>() {}, true);
                                    user.ifPresent(allUsers::add);
                                } catch (Exception e) {
                                    LoggerUtil.warn(this.getClass(), "Error reading network user file " + path + ": " + e.getMessage());
                                }
                            });
                }
            }

            LoggerUtil.info(this.getClass(), String.format("Admin scanned network users: found %d users", allUsers.size()));
            return allUsers;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error scanning network user files: " + e.getMessage(), e);
            return allUsers;
        }
    }

    /**
     * Deletes user from network (admin only).
     * Pattern: Network direct delete with backup
     * File: user_[username]_[userId].json
     *
     * @param username Username
     * @param userId User ID
     * @return true if deleted successfully
     */
    public boolean adminDeleteUserNetworkOnly(String username, Integer userId) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), "Network not available for admin user deletion");
                return false;
            }

            FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(username, userId));

            // Check if file exists first
            if (!Files.exists(networkPath.getPath())) {
                LoggerUtil.debug(this.getClass(), String.format("Admin delete: user file not found %s-%d", username, userId));
                return false;
            }

            // Delete with backup
            boolean deleted = Files.deleteIfExists(networkPath.getPath());

            if (deleted) {
                LoggerUtil.info(this.getClass(), String.format("Admin deleted user from network: %s (ID: %d)", username, userId));
            }

            return deleted;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in admin delete for user %s-%d: %s", username, userId, e.getMessage()));
            return false;
        }
    }

    // ========================================================================
    // USER OPERATIONS (LOCAL FILES WITH SYNC)
    // ========================================================================

    /**
     * Writes user to local with sync and backup (user operations).
     * Pattern: Local First -> Backup -> Network Sync
     * File: local_user_[username]_[userId].json
     *
     * @param user User to write
     */
    public void userWriteLocalWithSyncAndBackup(User user) {
        if (user == null || user.getUsername() == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User must have username and userId for local file storage");
        }

        try {
            String username = user.getUsername();
            Integer userId = user.getUserId();

            // Step 1: Write to local file (local_user_*)
            FilePath localPath = FilePath.local(pathConfig.getLocalUsersPath(username, userId));
            FileOperationResult localResult = fileWriterService.writeFile(localPath, user, true);

            if (!localResult.isSuccess()) {
                throw new RuntimeException("Failed to write user locally: " + localResult.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "Successfully wrote user to local: %s (ID: %d)", username, userId));

            // Step 2: Sync to network with filename transformation (user_*)
            if (pathConfig.isNetworkAvailable()) {
                try {
                    FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(username, userId));
                    FileOperationResult networkResult = fileWriterService.writeFile(networkPath, user, true);

                    if (networkResult.isSuccess()) {
                        LoggerUtil.info(this.getClass(), String.format(
                                "Successfully synced user local → network: %s (ID: %d)", username, userId));
                    } else {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Failed to sync user to network: %s",
                                networkResult.getErrorMessage().orElse("Unknown error")));
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Error syncing user to network for %s-%d: %s", username, userId, e.getMessage()));
                    // Don't throw - local write succeeded
                }
            } else {
                LoggerUtil.debug(this.getClass(), "Network not available for user sync");
            }

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error in user local write for %s: %s", user.getUsername(), e.getMessage()), e);
        }
    }

    /**
     * Reads user with smart fallback logic.
     * Pattern: Local for own data, Network for others, Smart sync when missing
     * Files: local_user_[username]_[userId].json / user_[username]_[userId].json
     *
     * @param username Username to read
     * @param userId User ID
     * @param currentUsername Current authenticated user
     * @return User data
     */
    public Optional<User> userReadLocalReadOnly(String username, Integer userId, String currentUsername) {
        try {
            boolean isOwnData = username.equals(currentUsername);

            if (isOwnData) {
                // Reading own data - local first with smart fallback
                return readOwnUserWithSmartFallback(username, userId);
            } else {
                // Reading other user's data - network first
                return readOtherUserFromNetwork(username, userId);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading user %s-%d: %s", username, userId, e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Creates local user file from network data (first login).
     * Pattern: Network → Local bootstrap
     * Files: user_[username]_[userId].json → local_user_[username]_[userId].json
     *
     * @param username Username
     * @param userId User ID
     * @return true if created successfully
     */
    public boolean userCreateLocalFromNetwork(String username, Integer userId) {
        try {
            // Check if local file already exists
            FilePath localPath = FilePath.local(pathConfig.getLocalUsersPath(username, userId));
            if (Files.exists(localPath.getPath())) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Local user file already exists for %s-%d", username, userId));
                return true;
            }

            // Read from network
            Optional<User> networkUser = adminReadUserNetworkOnly(username, userId);
            if (networkUser.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Cannot create local file: network user not found %s-%d", username, userId));
                return false;
            }

            // Write to local (no sync - this is the bootstrap)
            FileOperationResult result = fileWriterService.writeFile(localPath, networkUser.get(), true);

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), String.format(
                        "Failed to create local user file for %s-%d: %s",
                        username, userId, result.getErrorMessage().orElse("Unknown error")));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Created local user file from network for %s (ID: %d)", username, userId));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error creating local user file for %s-%d: %s", username, userId, e.getMessage()));
            return false;
        }
    }

    /**
     * Creates local user file from network data (for "remember me" login feature).
     * Pattern: Network (user_*) → Local (local_user_*) bootstrap
     * Files: user_[username]_[userId].json → local_user_[username]_[userId].json
     * NOTE: This should ONLY be called from AuthenticationService when user
     * selects "remember me" during login. Otherwise, no local file is created.
     *
     * @param username Username
     * @param userId User ID
     * @return true if created successfully
     */
    public boolean userCreateLocalFromNetworkForRememberMe(String username, Integer userId) {
        try {
            // Check if local file already exists
            FilePath localPath = FilePath.local(pathConfig.getLocalUsersPath(username, userId));
            if (Files.exists(localPath.getPath())) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Local user file already exists for remember me: %s-%d", username, userId));
                return true;
            }

            // Read from network (user_* file)
            Optional<User> networkUser = adminReadUserNetworkOnly(username, userId);
            if (networkUser.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Cannot create local file for remember me: network user not found %s-%d", username, userId));
                return false;
            }

            // Write to local (local_user_* file) - no sync needed, this is the bootstrap
            FileOperationResult result = fileWriterService.writeFile(localPath, networkUser.get(), true);

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), String.format(
                        "Failed to create local user file for remember me %s-%d: %s",
                        username, userId, result.getErrorMessage().orElse("Unknown error")));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Created local user file for remember me: %s (ID: %d)", username, userId));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error creating local user file for remember me %s-%d: %s", username, userId, e.getMessage()));
            return false;
        }
    }

    // ========================================================================
    // LOGIN OPERATIONS (NETWORK FIRST, LOCAL FALLBACK)
    // ========================================================================

    /**
     * Find user by username for authentication (network first, local fallback).
     * Pattern: Network → Local, returns complete User with password
     * Used by CustomUserDetailsService.loadUserByUsername()
     *
     * @param username Username to find
     * @return Complete User object with password for authentication
     */
    public Optional<User> findUserByUsernameForAuthentication(String username) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Finding user for authentication: %s", username));

            // Strategy 1: Try network first (login authority)
            if (pathConfig.isNetworkAvailable()) {
                Optional<User> networkUser = findUserByUsernameFromNetwork(username);
                if (networkUser.isPresent()) {
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Found user for authentication from network: %s", username));
                    return networkUser;
                }
            }

            // Strategy 2: Fallback to local files
            Optional<User> localUser = findUserByUsernameFromLocal(username);
            if (localUser.isPresent()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Found user for authentication from local fallback: %s", username));
                return localUser;
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "User not found for authentication: %s", username));
            return Optional.empty();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error finding user for authentication %s: %s", username, e.getMessage()), e);
            return Optional.empty();
        }
    }

    /**
     * Get all local users for offline authentication.
     * Pattern: Local scan only, returns complete User objects with passwords
     * Used by CustomUserDetailsService.loadUserByUsernameOffline()
     *
     * @return List of complete User objects with passwords from local storage
     */
    public List<User> getAllLocalUsersForAuthentication() {
        List<User> localUsers = new ArrayList<>();

        try {
            Path localUsersDir = pathConfig.getLocalPath().resolve(pathConfig.getUsersPath());

            if (!Files.exists(localUsersDir) || !Files.isDirectory(localUsersDir)) {
                LoggerUtil.debug(this.getClass(), "Local users directory does not exist: " + localUsersDir);
                return localUsers;
            }

            try (Stream<Path> files = Files.list(localUsersDir)) {
                files.filter(path -> {
                            String fileName = path.getFileName().toString();
                            return fileName.startsWith("local_user_") &&
                                    fileName.endsWith(FileTypeConstants.JSON_EXTENSION);
                        })
                        .forEach(path -> {
                            try {
                                FilePath localPath = FilePath.local(path);
                                Optional<User> user = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);
                                user.ifPresent(localUsers::add);
                            } catch (Exception e) {
                                LoggerUtil.warn(this.getClass(),
                                        "Error reading local user file for authentication " + path + ": " + e.getMessage());
                            }
                        });
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Found %d local users for authentication", localUsers.size()));
            return localUsers;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error scanning local users for authentication: " + e.getMessage(), e);
            return localUsers;
        }
    }

    /**
     * Check if local users exist for authentication status.
     * Pattern: Quick local directory scan
     * Used by AuthenticationService.getAuthenticationStatus()
     *
     * @return true if local user files exist for offline authentication
     */
    public boolean hasLocalUsersForAuthenticationStatus() {
        try {
            Path localUsersDir = pathConfig.getLocalPath().resolve(pathConfig.getUsersPath());

            if (!Files.exists(localUsersDir) || !Files.isDirectory(localUsersDir)) {
                return false;
            }

            try (Stream<Path> files = Files.list(localUsersDir)) {
                boolean hasLocalUsers = files.anyMatch(path -> {
                    String fileName = path.getFileName().toString();
                    return fileName.startsWith("local_user_") &&
                            fileName.endsWith(FileTypeConstants.JSON_EXTENSION);
                });

                LoggerUtil.debug(this.getClass(), String.format(
                        "Local users available for authentication: %s", hasLocalUsers));
                return hasLocalUsers;
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking local users for authentication status: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Store user data locally for remember me functionality.
     * Pattern: Network → Local bootstrap for persistent login
     * Used by AuthenticationService.storeUserDataLocally()
     *
     * @param user Complete user object to store locally
     * @return true if stored successfully
     */
    public boolean storeUserDataForRememberMe(User user) {
        if (user == null || user.getUsername() == null || user.getUserId() == null) {
            LoggerUtil.error(this.getClass(), "Cannot store user for remember me: invalid user object");
            return false;
        }

        try {
            String username = user.getUsername();
            Integer userId = user.getUserId();

            // Use existing method for remember me storage
            boolean created = userCreateLocalFromNetworkForRememberMe(username, userId);

            if (created) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Stored user data for remember me: %s (ID: %d)", username, userId));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to store user data for remember me: %s (ID: %d)", username, userId));
            }

            return created;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error storing user data for remember me %s: %s", user.getUsername(), e.getMessage()), e);
            return false;
        }
    }

    // ========================================================================
    // CHECK VALUES OPERATIONS
    // ========================================================================

    /**
     * Writes user check values with sync and backup.
     * Pattern: Local First -> Backup -> Network Sync
     * File: users_check_value_[username]_[userId].json
     *
     * @param entry Check values entry
     * @param username Username
     * @param userId User ID
     */
    public void writeUserCheckValuesWithSyncAndBackup(UsersCheckValueEntry entry, String username, Integer userId) {
        try {
            FilePath localPath = FilePath.local(pathConfig.getLocalCheckValuesPath(username, userId));

            // Step 1: Write to local with backup and network sync enabled
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entry, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write check values: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote check values for user %s-%d", username, userId));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing check values for user %s-%d: %s", username, userId, e.getMessage()), e);
        }
    }

    /**
     * Reads user check values with smart fallback.
     * Pattern: Network first, local fallback
     * File: users_check_value_[username]_[userId].json
     *
     * @param username Username
     * @param userId User ID
     * @return Check values entry
     */
    public Optional<UsersCheckValueEntry> readUserCheckValuesReadOnly(String username, Integer userId) {
        try {
            // Try network first if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = FilePath.network(pathConfig.getNetworkCheckValuesPath(username, userId));
                Optional<UsersCheckValueEntry> networkEntry = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);
                if (networkEntry.isPresent()) {
                    LoggerUtil.debug(this.getClass(), String.format("Read check values from network for %s-%d", username, userId));
                    return networkEntry;
                }
            }

            // Fallback to local file
            FilePath localPath = FilePath.local(pathConfig.getLocalCheckValuesPath(username, userId));
            Optional<UsersCheckValueEntry> localEntry = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

            if (localEntry.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format("Read check values from local for %s-%d", username, userId));
            }

            return localEntry;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading check values for %s-%d: %s", username, userId, e.getMessage()));
            return Optional.empty();
        }
    }

    // ========================================================================
    // HOLIDAY OPERATIONS (INTEGRATED WITH USER DATA)
    // ========================================================================

    /**
     * Updates holiday days for user via admin (network only).
     * Pattern: Admin network direct update
     * File: user_[username]_[userId].json
     *
     * @param username Username
     * @param userId User ID
     * @param holidayDays New holiday days value
     */
    public void updateUserHolidayDaysAdmin(String username, Integer userId, Integer holidayDays) {
        try {
            // Read current user from network
            Optional<User> userOpt = adminReadUserNetworkOnly(username, userId);
            if (userOpt.isEmpty()) {
                throw new RuntimeException("User not found on network for holiday update: " + username);
            }

            // Update holiday days
            User user = userOpt.get();
            user.setPaidHolidayDays(holidayDays);

            // Write back to network
            adminWriteUserNetworkOnly(user);

            LoggerUtil.info(this.getClass(), String.format(
                    "Admin updated holiday days for %s-%d to %d days", username, userId, holidayDays));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error in admin holiday update for %s-%d: %s", username, userId, e.getMessage()), e);
        }
    }

    /**
     * Updates holiday days for user via user (local with sync).
     * Pattern: User local update with network sync
     * File: local_user_[username]_[userId].json
     *
     * @param username Username
     * @param userId User ID
     * @param holidayDays New holiday days value
     */
    public void updateUserHolidayDaysUser(String username, Integer userId, Integer holidayDays) {
        try {
            // Read current user (smart fallback)
            Optional<User> userOpt = userReadLocalReadOnly(username, userId, username);
            if (userOpt.isEmpty()) {
                throw new RuntimeException("User not found for holiday update: " + username);
            }

            // Update holiday days
            User user = userOpt.get();
            user.setPaidHolidayDays(holidayDays);

            // Write to local with sync
            userWriteLocalWithSyncAndBackup(user);

            LoggerUtil.info(this.getClass(), String.format(
                    "User updated holiday days for %s-%d to %d days", username, userId, holidayDays));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error in user holiday update for %s-%d: %s", username, userId, e.getMessage()), e);
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Read own user data with smart fallback (same pattern as RegisterDataService)
     */
    private Optional<User> readOwnUserWithSmartFallback(String username, Integer userId) {
        FilePath localPath = FilePath.local(pathConfig.getLocalUsersPath(username, userId));

        // Try local first
        Optional<User> localUser = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

        if (localUser.isPresent()) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Found local user data for %s-%d", username, userId));
            return localUser;
        }

        // Local is missing - try network and sync to local if found
        if (pathConfig.isNetworkAvailable()) {
            Optional<User> networkUser = adminReadUserNetworkOnly(username, userId);

            if (networkUser.isPresent()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Found network user data for %s-%d, syncing to local", username, userId));

                // Create local file from network data
                boolean created = userCreateLocalFromNetwork(username, userId);
                if (!created) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Failed to sync user network → local for %s-%d", username, userId));
                }

                return networkUser;
            }
        }

        // Both local and network are missing
        LoggerUtil.debug(this.getClass(), String.format(
                "No user data found for %s-%d", username, userId));
        return Optional.empty();
    }

    /**
     * Read other user's data from network (same pattern as RegisterDataService)
     */
    private Optional<User> readOtherUserFromNetwork(String username, Integer userId) {
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network not available for reading other user data");
            return Optional.empty();
        }

        Optional<User> networkUser = adminReadUserNetworkOnly(username, userId);

        if (networkUser.isPresent()) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Read other user data from network for %s-%d", username, userId));
        }

        return networkUser;
    }

    /**
     * Find user by username from network files (for authentication)
     */
    private Optional<User> findUserByUsernameFromNetwork(String username) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), "Network not available for user authentication lookup");
                return Optional.empty();
            }

            // Scan all network user files to find matching username
            Path networkUsersDir = pathConfig.getNetworkPath().resolve(pathConfig.getUsersPath());

            if (!Files.exists(networkUsersDir) || !Files.isDirectory(networkUsersDir)) {
                LoggerUtil.debug(this.getClass(), "Network users directory does not exist: " + networkUsersDir);
                return Optional.empty();
            }

            try (Stream<Path> files = Files.list(networkUsersDir)) {
                Optional<Path> userFile = files
                        .filter(path -> {
                            String fileName = path.getFileName().toString();
                            return fileName.startsWith("user_") &&
                                    fileName.endsWith(FileTypeConstants.JSON_EXTENSION);
                        })
                        .filter(path -> {
                            // Quick check if filename contains the username
                            String fileName = path.getFileName().toString();
                            return fileName.contains("_" + username + "_");
                        })
                        .findFirst();

                if (userFile.isPresent()) {
                    FilePath networkPath = FilePath.network(userFile.get());
                    Optional<User> user = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

                    // Verify username matches (double-check)
                    if (user.isPresent() && username.equals(user.get().getUsername())) {
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Found user by username from network: %s", username));
                        return user;
                    }
                }
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "User not found by username in network: %s", username));
            return Optional.empty();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error finding user by username from network %s: %s", username, e.getMessage()), e);
            return Optional.empty();
        }
    }

    /**
     * Find user by username from local files (for authentication)
     */
    private Optional<User> findUserByUsernameFromLocal(String username) {
        try {
            Path localUsersDir = pathConfig.getLocalPath().resolve(pathConfig.getUsersPath());

            if (!Files.exists(localUsersDir) || !Files.isDirectory(localUsersDir)) {
                LoggerUtil.debug(this.getClass(), "Local users directory does not exist: " + localUsersDir);
                return Optional.empty();
            }

            try (Stream<Path> files = Files.list(localUsersDir)) {
                Optional<Path> userFile = files
                        .filter(path -> {
                            String fileName = path.getFileName().toString();
                            return fileName.startsWith("local_user_") &&
                                    fileName.endsWith(FileTypeConstants.JSON_EXTENSION);
                        })
                        .filter(path -> {
                            // Quick check if filename contains the username
                            String fileName = path.getFileName().toString();
                            return fileName.contains("_" + username + "_");
                        })
                        .findFirst();

                if (userFile.isPresent()) {
                    FilePath localPath = FilePath.local(userFile.get());
                    Optional<User> user = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

                    // Verify username matches (double-check)
                    if (user.isPresent() && username.equals(user.get().getUsername())) {
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Found user by username from local: %s", username));
                        return user;
                    }
                }
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "User not found by username in local: %s", username));
            return Optional.empty();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error finding user by username from local %s: %s", username, e.getMessage()), e);
            return Optional.empty();
        }
    }
}