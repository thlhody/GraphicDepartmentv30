package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UsersCheckValueEntry;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Domain service for all user-related data operations.
 * Handles user files, check values, and holiday management with event-driven backups.
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

    // ===== INDIVIDUAL USER OPERATIONS =====

    /**
     * Writes a user to their individual file with event-driven backup.
     */
    public void writeUser(User user) {
        if (user == null || user.getUsername() == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User must have username and userId for individual file storage");
        }

        try {
            // Write to local with network sync - triggers events and backups automatically
            FilePath localPath = FilePath.local(pathConfig.getLocalUsersPath(user.getUsername(), user.getUserId()));
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, user, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write user: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote user: %s (ID: %d)",
                    user.getUsername(), user.getUserId()));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing user %s: %s",
                    user.getUsername(), e.getMessage()), e);
        }
    }

    /**
     * Reads a specific user by username and userId.
     */
    public Optional<User> readUserByUsernameAndId(String username, Integer userId) {
        // First try network if available
        if (pathConfig.isNetworkAvailable()) {
            try {
                FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(username, userId));
                Optional<User> networkUser = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {}, true);
                if (networkUser.isPresent()) {
                    return networkUser;
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Error reading network user for %s: %s", username, e.getMessage()));
            }
        }

        // Fall back to local file
        try {
            FilePath localPath = FilePath.local(pathConfig.getLocalUsersPath(username, userId));
            return fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {}, true);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading local user for %s: %s", username, e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Gets a user by their user ID.
     */
    public Optional<User> getUserById(Integer userId) {
        // First try to find in network if available
        if (pathConfig.isNetworkAvailable()) {
            try {
                List<User> users = getAllUsers();
                Optional<User> networkUser = users.stream()
                        .filter(user -> user.getUserId().equals(userId))
                        .findFirst();

                if (networkUser.isPresent()) {
                    return networkUser;
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Error finding network user with ID %d: %s", userId, e.getMessage()));
            }
        }

        // Fall back to local users
        try {
            List<User> localUsers = getLocalUsers();
            return localUsers.stream()
                    .filter(user -> user.getUserId().equals(userId))
                    .findFirst();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error finding local user with ID %d: %s", userId, e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Gets all users by scanning for individual files.
     */
    public List<User> getAllUsers() {
        List<User> allUsers = new ArrayList<>();

        try {
            // First scan network directory if available
            if (pathConfig.isNetworkAvailable()) {
                Path networkDir = pathConfig.getNetworkPath().resolve(pathConfig.getUsersPath());

                if (Files.exists(networkDir) && Files.isDirectory(networkDir)) {
                    try (Stream<Path> files = Files.list(networkDir)) {
                        files.filter(path -> path.getFileName().toString().startsWith("user_") &&
                                        path.getFileName().toString().endsWith(FileTypeConstants.JSON_EXTENSION))
                                .forEach(path -> {
                                    try {
                                        FilePath filePath = FilePath.network(path);
                                        Optional<User> user = fileReaderService.readFileReadOnly(filePath, new TypeReference<>() {}, true);
                                        user.ifPresent(allUsers::add);
                                    } catch (Exception e) {
                                        LoggerUtil.warn(this.getClass(), "Error reading user file " + path + ": " + e.getMessage());
                                    }
                                });
                    }

                    if (!allUsers.isEmpty()) {
                        // Remove duplicates if any
                        return new ArrayList<>(allUsers.stream()
                                .collect(Collectors.toMap(
                                        User::getUsername,
                                        user -> user,
                                        (existing, replacement) -> existing // Keep first one in case of duplicates
                                ))
                                .values());
                    }
                }
            }

            // If network unavailable or no users found, scan local directory
            allUsers.addAll(getLocalUsers());

            return allUsers;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error scanning for user files: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets local users only.
     */
    public List<User> getLocalUsers() {
        List<User> localUsers = new ArrayList<>();

        try {
            Path localDir = pathConfig.getLocalPath().resolve(pathConfig.getUsersPath());

            if (Files.exists(localDir) && Files.isDirectory(localDir)) {
                try (Stream<Path> files = Files.list(localDir)) {
                    files.filter(path -> path.getFileName().toString().startsWith("local_user_") &&
                                    path.getFileName().toString().endsWith(FileTypeConstants.JSON_EXTENSION))
                            .forEach(path -> {
                                try {
                                    FilePath filePath = FilePath.local(path);
                                    Optional<User> user = fileReaderService.readFileReadOnly(filePath, new TypeReference<>() {}, true);
                                    user.ifPresent(localUsers::add);
                                } catch (Exception e) {
                                    LoggerUtil.warn(this.getClass(), "Error reading local user file " + path + ": " + e.getMessage());
                                }
                            });
                }
            }

            // Remove duplicates if any
            return new ArrayList<>(localUsers.stream()
                    .collect(Collectors.toMap(
                            User::getUsername,
                            user -> user,
                            (existing, replacement) -> existing // Keep first one in case of duplicates
                    ))
                    .values());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading local users: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    // ===== BATCH USER OPERATIONS =====

    /**
     * Writes multiple users to network using event-driven backups.
     * Replaces the old manual transaction approach.
     */
    public void writeUsersNetwork(List<User> users) {
        LoggerUtil.info(this.getClass(), String.format("Writing %d users to network using event system", users.size()));

        int successCount = 0;
        List<String> failures = new ArrayList<>();

        for (User user : users) {
            if (user.getUsername() == null || user.getUserId() == null) {
                LoggerUtil.warn(this.getClass(), "Skipping user with missing username or userId");
                continue;
            }

            try {
                // Use FileWriterService directly - this triggers events and backups
                FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(user.getUsername(), user.getUserId()));
                FileOperationResult result = fileWriterService.writeFile(networkPath, user, true);

                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failures.add(user.getUsername() + ": " + result.getErrorMessage().orElse("Unknown error"));
                }
            } catch (Exception e) {
                failures.add(user.getUsername() + ": " + e.getMessage());
                LoggerUtil.error(this.getClass(), "Error writing user " + user.getUsername() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            String errorMsg = String.format("Failed to write %d/%d users: %s",
                    failures.size(), users.size(), String.join(", ", failures));
            LoggerUtil.error(this.getClass(), errorMsg);
            throw new RuntimeException(errorMsg);
        }

        LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d users to network with event-driven backups", successCount));
    }

    // ===== HOLIDAY MANAGEMENT =====

    /**
     * Updates holiday days for a user using event-driven backups.
     */
    public void updateUserHolidayDays(String username, Integer userId, Integer holidayDays) {
        try {
            // Read current user data
            Optional<User> userToUpdate = readUserByUsernameAndId(username, userId);

            if (userToUpdate.isEmpty()) {
                throw new RuntimeException("User not found for holiday update: " + username);
            }

            // Update holiday days
            User user = userToUpdate.get();
            user.setPaidHolidayDays(holidayDays);

            // Write using event system
            writeUser(user);

            LoggerUtil.info(this.getClass(), String.format("Updated holiday days for user %s to %d days", username, holidayDays));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error updating holiday days for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Updates holiday days for a user (admin version) using event-driven backups.
     */
    public void updateUserHolidayDaysAdmin(String username, Integer userId, Integer holidayDays) {
        if (!pathConfig.isNetworkAvailable()) {
            throw new RuntimeException("Network not available for admin holiday update");
        }

        try {
            // Read user from network
            FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(username, userId));
            Optional<User> networkUser = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {}, true);

            if (networkUser.isEmpty()) {
                throw new RuntimeException("User not found on network: " + username);
            }

            // Update holiday days
            User user = networkUser.get();
            user.setPaidHolidayDays(holidayDays);

            // Write directly to network - this triggers events and backups
            FileOperationResult result = fileWriterService.writeFile(networkPath, user, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to update holiday days (admin): " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Admin updated holiday days for user %s to %d days", username, holidayDays));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error in admin holiday update for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Gets holiday days for a specific user.
     */
    public int getUserHolidayDays(String username, Integer userId) {
        Optional<User> user = readUserByUsernameAndId(username, userId);
        return user.map(u -> u.getPaidHolidayDays() != null ? u.getPaidHolidayDays() : 0).orElse(0);
    }

    /**
     * Gets a list of holiday entries by extracting information from user files.
     */
    public List<PaidHolidayEntryDTO> getUserHolidayEntries() {
        List<User> users = getAllUsers();
        return users.stream()
                .map(user -> PaidHolidayEntryDTO.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .name(user.getName())
                        .employeeId(user.getEmployeeId())
                        .schedule(user.getSchedule())
                        .paidHolidayDays(user.getPaidHolidayDays() != null ? user.getPaidHolidayDays() : 0)
                        .build())
                .collect(Collectors.toList());
    }

    // ===== CHECK VALUES =====

    /**
     * Reads check values for a specific user.
     */
    public Optional<UsersCheckValueEntry> readUserCheckValues(String username, Integer userId) {
        // First try network if available
        if (pathConfig.isNetworkAvailable()) {
            try {
                FilePath networkPath = FilePath.network(pathConfig.getNetworkCheckValuesPath(username, userId));
                Optional<UsersCheckValueEntry> networkEntry = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {}, true);
                if (networkEntry.isPresent()) {
                    return networkEntry;
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Error reading network check values for %s: %s", username, e.getMessage()));
            }
        }

        // Fall back to local file
        try {
            FilePath localPath = FilePath.local(pathConfig.getLocalCheckValuesPath(username, userId));
            return fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {}, true);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading local check values for %s: %s", username, e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Writes check values for a specific user with event-driven backups.
     */
    public void writeUserCheckValues(UsersCheckValueEntry entry, String username, Integer userId) {
        try {
            // Write to local with network sync - triggers events and backups
            FilePath localPath = FilePath.local(pathConfig.getLocalCheckValuesPath(username, userId));
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entry, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write check values: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote check values for user %s", username));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing check values for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Gets all check values entries by scanning for individual files.
     */
    public List<UsersCheckValueEntry> getAllCheckValues() {
        List<UsersCheckValueEntry> allEntries = new ArrayList<>();

        try {
            // First scan network directory if available
            if (pathConfig.isNetworkAvailable()) {
                Path networkDir = pathConfig.getNetworkPath().resolve(pathConfig.getUsersPath());

                if (Files.exists(networkDir) && Files.isDirectory(networkDir)) {
                    try (Stream<Path> files = Files.list(networkDir)) {
                        files.filter(path -> path.getFileName().toString().startsWith("users_check_value_") &&
                                        path.getFileName().toString().endsWith(FileTypeConstants.JSON_EXTENSION))
                                .forEach(path -> {
                                    try {
                                        FilePath filePath = FilePath.network(path);
                                        Optional<UsersCheckValueEntry> entry = fileReaderService.readFileReadOnly(
                                                filePath, new TypeReference<>() {}, true);
                                        entry.ifPresent(allEntries::add);
                                    } catch (Exception e) {
                                        LoggerUtil.warn(this.getClass(), "Error reading check values file " + path + ": " + e.getMessage());
                                    }
                                });
                    }

                    if (!allEntries.isEmpty()) {
                        return allEntries;
                    }
                }
            }

            // If network unavailable or no entries found, scan local directory
            Path localDir = pathConfig.getLocalPath().resolve(pathConfig.getUsersPath());

            if (Files.exists(localDir) && Files.isDirectory(localDir)) {
                try (Stream<Path> files = Files.list(localDir)) {
                    files.filter(path -> path.getFileName().toString().startsWith("local_users_check_value_") &&
                                    path.getFileName().toString().endsWith(FileTypeConstants.JSON_EXTENSION))
                            .forEach(path -> {
                                try {
                                    FilePath filePath = FilePath.local(path);
                                    Optional<UsersCheckValueEntry> entry = fileReaderService.readFileReadOnly(
                                            filePath, new TypeReference<>() {}, true);
                                    entry.ifPresent(allEntries::add);
                                } catch (Exception e) {
                                    LoggerUtil.warn(this.getClass(), "Error reading local check values file " + path + ": " + e.getMessage());
                                }
                            });
                }
            }

            return allEntries;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error scanning for check values files: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}