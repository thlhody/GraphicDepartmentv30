package com.ctgraphdep.fileOperations;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.core.FileTransaction;
import com.ctgraphdep.fileOperations.model.FileTransactionResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.SyncFilesService;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileTransactionManager;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.security.FileAccessSecurityRules;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for accessing data files using the file operations framework.
 * Acts as a facade for the underlying file services.
 * Consistently uses the transaction system for all write operations.
 */
@Getter
@Service
public class DataAccessService {

    private final ObjectMapper objectMapper;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;
    private final FileReaderService fileReaderService;
    private final FileWriterService fileWriterService;
    private final FileTransactionManager transactionManager;
    private final SyncFilesService syncFileService;
    private final FileAccessSecurityRules securityRules;

    public DataAccessService(ObjectMapper objectMapper,
                             PathConfig pathConfig,
                             FilePathResolver pathResolver,
                             FileReaderService fileReaderService,
                             FileWriterService fileWriterService,
                             FileTransactionManager transactionManager,
                             SyncFilesService syncFileService,
                             FileAccessSecurityRules securityRules) {
        this.objectMapper = objectMapper;
        this.pathConfig = pathConfig;
        this.pathResolver = pathResolver;
        this.fileReaderService = fileReaderService;
        this.fileWriterService = fileWriterService;
        this.transactionManager = transactionManager;
        this.syncFileService = syncFileService;
        this.securityRules = securityRules;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ===== Session file operations =====

    /**
     * Gets the log path for a specific user
     * @param username The username
     * @return Path to the user's log file
     */

    public Path getLocalSessionPath(String username, Integer userId) {
        return pathConfig.getLocalSessionPath(username,userId);

    }

    /**
     * Writes a session file to local storage and syncs to network
     */
    public void writeLocalSessionFile(WorkUsersSessionsStates session) {
        validateSession(session);

        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            // Create a file path object
            FilePath localPath = pathResolver.getLocalPath(session.getUsername(), session.getUserId(), FilePathResolver.FileType.SESSION, FilePathResolver.createParams());

            // Serialize the data
            byte[] content = objectMapper.writeValueAsBytes(session);

            // Add to transaction
            transaction.addWrite(localPath, content);

            // Add network sync operation if network is available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.toNetworkPath(localPath);
                transaction.addSync(localPath, networkPath);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write session file: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write session file: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format("Saved session for user %s with status %s",
                    session.getUsername(), session.getSessionStatus()));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), "Failed to write session file: " + e.getMessage(), e);
        }
    }
    /**
     * Reads a session file from local storage
     */
    public WorkUsersSessionsStates readLocalSessionFile(String username, Integer userId) {
        // Create a file path object
        FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.SESSION, FilePathResolver.createParams());

        // Read the file
        Optional<WorkUsersSessionsStates> result = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

        return result.orElse(null);
    }
    /**
     * Reads a session file from the network in read-only mode
     */
    public WorkUsersSessionsStates readNetworkSessionFileReadOnly(String username, Integer userId) {
        try {
            // Create a file path object
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.SESSION, FilePathResolver.createParams());

            // Read the file in read-only mode
            Optional<WorkUsersSessionsStates> result = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {
            }, true);

            return result.orElse(null);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading network session for user %s: %s", username, e.getMessage()));
            return null;
        }
    }
    /**
     * Reads a session file from local storage in read-only mode (no locking)
     */
    public WorkUsersSessionsStates readLocalSessionFileReadOnly(String username, Integer userId) {
        try {
            // Create a file path object
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.SESSION, FilePathResolver.createParams());

            // Read the file in read-only mode
            Optional<WorkUsersSessionsStates> result = fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {
            }, true);

            return result.orElse(null);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading local session for user %s: %s", username, e.getMessage()));
            return null;
        }
    }

    // ===== User file operations =====

    /**
     * Reads a specific user by username and userId
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
     * Gets a user by their user ID
     * @param userId The user ID to look for
     * @return The user if found, or empty
     */
    public Optional<User> getUserById(Integer userId) {
        // First try to find in network if available
        if (pathConfig.isNetworkAvailable()) {
            try {
                List<User> users = readUsersNetwork();
                Optional<User> networkUser = users.stream()
                        .filter(user -> user.getUserId().equals(userId))
                        .findFirst();

                if (networkUser.isPresent()) {
                    return networkUser;
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Error finding network user with ID %d: %s",
                                userId, e.getMessage()));
            }
        }

        // Fall back to local users
        try {
            List<User> localUsers = readLocalUser();
            return localUsers.stream()
                    .filter(user -> user.getUserId().equals(userId))
                    .findFirst();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error finding local user with ID %d: %s",
                            userId, e.getMessage()));
            return Optional.empty();
        }
    }

    /**
     * Writes a user to their individual file with transaction system
     */
    public void writeUser(User user) {
        if (user == null || user.getUsername() == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User must have username and userId for individual file storage");
        }

        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            // First update local file
            FilePath localPath = FilePath.local(pathConfig.getLocalUsersPath(user.getUsername(), user.getUserId()));

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(user);

            // Add to transaction
            transaction.addWrite(localPath, content);

            // Add network write if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(user.getUsername(), user.getUserId()));
                transaction.addWrite(networkPath, content);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write user: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write user: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote user: %s (ID: %d)",
                    user.getUsername(), user.getUserId()));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing user %s: %s",
                    user.getUsername(), e.getMessage()), e);
        }
    }

    /**
     * Gets all users by scanning for individual files
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
                                        path.getFileName().toString().endsWith(".json"))
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
            Path localDir = pathConfig.getLocalPath().resolve(pathConfig.getUsersPath());

            if (Files.exists(localDir) && Files.isDirectory(localDir)) {
                try (Stream<Path> files = Files.list(localDir)) {
                    files.filter(path -> path.getFileName().toString().startsWith("local_user_") &&
                                    path.getFileName().toString().endsWith(".json"))
                            .forEach(path -> {
                                try {
                                    FilePath filePath = FilePath.local(path);
                                    Optional<User> user = fileReaderService.readFileReadOnly(filePath, new TypeReference<>() {}, true);
                                    user.ifPresent(allUsers::add);
                                } catch (Exception e) {
                                    LoggerUtil.warn(this.getClass(), "Error reading local user file " + path + ": " + e.getMessage());
                                }
                            });
                }
            }

            // Remove duplicates if any
            return new ArrayList<>(allUsers.stream()
                    .collect(Collectors.toMap(
                            User::getUsername,
                            user -> user,
                            (existing, replacement) -> existing // Keep first one in case of duplicates
                    ))
                    .values());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error scanning for user files: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Reads users from the network and local files, maintaining backward compatibility
     * Now uses the new individual file approach internally
     */
    public List<User> readUsersNetwork() {
        // Use the new getAllUsers method internally
        List<User> allUsers = getAllUsers();

        // Log info for monitoring
        LoggerUtil.info(this.getClass(), String.format("Read %d users with the new individual file system",
                allUsers.size()));

        return allUsers;
    }

    /**
     * Writes users to the network, maintaining backward compatibility
     * Now uses the new individual file approach internally
     */
    public void writeUsersNetwork(List<User> users) {
        // Start a transaction for batch processing
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            // For each user, write their individual file
            for (User user : users) {
                if (user.getUsername() == null || user.getUserId() == null) {
                    LoggerUtil.warn(this.getClass(), "Skipping user with missing username or userId");
                    continue;
                }

                // Get network path for this user
                FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(user.getUsername(), user.getUserId()));

                // Serialize user data
                byte[] content = objectMapper.writeValueAsBytes(user);

                // Add to transaction
                transaction.addWrite(networkPath, content);
            }

            // Commit all changes in one transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write users to network: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write users to network: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d users to network using individual file system",
                    users.size()));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing users to network: %s", e.getMessage()), e);
        }
    }

    /**
     * Reads users from local storage, maintaining backward compatibility
     * Now uses the new individual file approach internally
     */
    public List<User> readLocalUser() {
        if (!pathConfig.isLocalAvailable()) {
            pathConfig.revalidateLocalAccess();
        }

        try {
            // Filter to get only local users
            List<User> localUsers = getAllUsers().stream()
                    .filter(user -> {
                        try {
                            // Check if a local version exists
                            return Files.exists(pathConfig.getLocalUsersPath(user.getUsername(), user.getUserId()));
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            if (localUsers.isEmpty()) {
                LoggerUtil.info(this.getClass(), "No local users found, returning empty list");
                return new ArrayList<>();
            }

            LoggerUtil.info(this.getClass(), String.format("Read %d local users with individual file system",
                    localUsers.size()));

            return localUsers;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading local users: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes a user to local storage, maintaining backward compatibility
     * Now uses the new individual file approach internally
     */
    public void writeLocalUser(User user) {
        if (user == null || user.getUsername() == null || user.getUserId() == null) {
            throw new IllegalArgumentException("User must have username and userId");
        }

        // Use the new method internally
        try {
            // Call our new method which handles writing to local storage
            writeUser(user);
            LoggerUtil.info(this.getClass(), String.format("Successfully updated local user: %s", user.getUsername()));
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error updating local user %s: %s",
                    user.getUsername(), e.getMessage()), e);
        }
    }

    // ===== Holiday entries operations =====


    /**
     * Updates holiday days for a user in their user file
     * This includes local update and network sync
     */
    public void updateUserHolidayDays(String username, Integer userId, Integer holidayDays) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            // First get the user from network if available
            Optional<User> networkUser = Optional.empty();
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(username, userId));
                networkUser = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {}, true);
            }

            // If not found on network, try local file
            Optional<User> userToUpdate = networkUser.isPresent() ?
                    networkUser :
                    fileReaderService.readLocalFile(FilePath.local(pathConfig.getLocalUsersPath(username, userId)),
                            new TypeReference<>() {}, true);

            if (userToUpdate.isEmpty()) {
                LoggerUtil.error(this.getClass(), "User not found for holiday update: " + username);
                throw new RuntimeException("User not found for holiday update: " + username);
            }

            // Update the holiday days
            User user = userToUpdate.get();
            user.setPaidHolidayDays(holidayDays);

            // Write to local file
            FilePath localPath = FilePath.local(pathConfig.getLocalUsersPath(username, userId));
            byte[] content = objectMapper.writeValueAsBytes(user);
            transaction.addWrite(localPath, content);

            // Write to network if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(username, userId));
                transaction.addWrite(networkPath, content);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to update holiday days: " + result.getErrorMessage());
                throw new RuntimeException("Failed to update holiday days: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Updated holiday days for user %s to %d days", username, holidayDays));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(),
                    String.format("Error updating holiday days for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Gets a list of holiday entries by extracting information from user files
     * This replaces the previous readHolidayEntries method
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
                        .paidHolidayDays(user.getPaidHolidayDays())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Gets holiday days for a specific user
     */
    public int getUserHolidayDays(String username, Integer userId) {
        // Try network first if available
        if (pathConfig.isNetworkAvailable()) {
            try {
                FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(username, userId));
                Optional<User> networkUser = fileReaderService.readFileReadOnly(
                        networkPath, new TypeReference<>() {}, true);

                if (networkUser.isPresent() && networkUser.get().getPaidHolidayDays() != null) {
                    return networkUser.get().getPaidHolidayDays();
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Error reading network user holiday days for %s: %s",
                                username, e.getMessage()));
            }
        }

        // Fall back to local file
        try {
            FilePath localPath = FilePath.local(pathConfig.getLocalUsersPath(username, userId));
            Optional<User> localUser = fileReaderService.readFileReadOnly(
                    localPath, new TypeReference<>() {}, true);

            if (localUser.isPresent() && localUser.get().getPaidHolidayDays() != null) {
                return localUser.get().getPaidHolidayDays();
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading local user holiday days for %s: %s",
                            username, e.getMessage()));
        }

        // Default to 0 if no information found
        return 0;
    }

    /**
     * Gets a list of holiday entries for read-only purposes
     * This replaces the previous readHolidayEntriesReadOnly method
     */
    public List<PaidHolidayEntryDTO> getUserHolidayEntriesReadOnly() {
        try {
            // Use existing method to get all users
            List<User> users = readUsersNetwork();

            // Convert users to holiday entries
            return users.stream()
                    .map(user -> PaidHolidayEntryDTO.builder()
                            .userId(user.getUserId())
                            .username(user.getUsername())
                            .name(user.getName())
                            .employeeId(user.getEmployeeId())
                            .schedule(user.getSchedule())
                            .paidHolidayDays(user.getPaidHolidayDays() != null ?
                                    user.getPaidHolidayDays() : 0)
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error reading user holiday entries in read-only mode: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Updates holiday days for a user in their user file - admin version
     * This writes directly to network without local update
     */
    public void updateUserHolidayDaysAdmin(String username, Integer userId, Integer holidayDays) {
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.error(this.getClass(), "Network not available for admin holiday update");
            throw new RuntimeException("Network not available for admin holiday update");
        }

        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            // Get the user from network
            FilePath networkPath = FilePath.network(pathConfig.getNetworkUsersPath(username, userId));
            Optional<User> networkUser = fileReaderService.readFileReadOnly(
                    networkPath, new TypeReference<>() {}, true);

            if (networkUser.isEmpty()) {
                LoggerUtil.error(this.getClass(), "User not found on network: " + username);
                throw new RuntimeException("User not found on network: " + username);
            }

            // Update the holiday days
            User user = networkUser.get();
            user.setPaidHolidayDays(holidayDays);

            // Write directly to network
            byte[] content = objectMapper.writeValueAsBytes(user);
            transaction.addWrite(networkPath, content);

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(),
                        "Failed to update holiday days (admin): " + result.getErrorMessage());
                throw new RuntimeException(
                        "Failed to update holiday days (admin): " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Admin updated holiday days for user %s to %d days",
                            username, holidayDays));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(),
                    String.format("Error in admin holiday update for user %s: %s",
                            username, e.getMessage()), e);
        }
    }

    /**
     * Reads check values for a specific user
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
     * Writes check values for a specific user
     */
    public void writeUserCheckValues(UsersCheckValueEntry entry, String username, Integer userId) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            // Update local file first
            FilePath localPath = FilePath.local(pathConfig.getLocalCheckValuesPath(username, userId));

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(entry);

            // Add to transaction
            transaction.addWrite(localPath, content);

            // Update network if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = FilePath.network(pathConfig.getNetworkCheckValuesPath(username, userId));
                transaction.addWrite(networkPath, content);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write check values: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write check values: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote check values for user %s", username));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing check values for user %s: %s",
                    username, e.getMessage()), e);
        }
    }

    /**
     * Gets all check values entries by scanning for individual files
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
                                        path.getFileName().toString().endsWith(".json"))
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
                                    path.getFileName().toString().endsWith(".json"))
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

    // ===== Worktime operations =====

    /**
     * Reads worktime data in read-only mode, falling back to local
     */
    public List<WorkTimeTable> readWorktimeReadOnly(String username, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // First try network if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, null, FilePathResolver.FileType.WORKTIME, params);
                Optional<List<WorkTimeTable>> entries = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {
                }, true);

                if (entries.isPresent()) {
                    return entries.get();
                }
            }

            // Fall back to local file
            FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);
            Optional<List<WorkTimeTable>> entries = fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {
            }, true);

            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Read-only worktime access failed for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads worktime data from the network in read-only mode
     */
    public List<WorkTimeTable> readNetworkUserWorktimeReadOnly(String username, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(username, null, FilePathResolver.FileType.WORKTIME, params);
            Optional<List<WorkTimeTable>> entries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
            }, true);

            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading network worktime for user %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads worktime data from the appropriate source based on user access
     */
    public List<WorkTimeTable> readUserWorktime(String username, int year, int month) {
        try {
            // Get current user
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // If user is accessing their own data, use local path
            if (currentUsername.equals(username)) {
                LoggerUtil.info(this.getClass(), String.format("Reading local worktime for user %s", username));
                FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);
                Optional<List<WorkTimeTable>> entries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {
                }, true);
                return entries.orElse(new ArrayList<>());
            }

            // If accessing other user's data, use network path
            if (pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), String.format("Reading network worktime for user %s by %s",
                        username, currentUsername));
                return readNetworkUserWorktimeReadOnly(username, year, month);
            }

            throw new RuntimeException("Network access required but not available");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading worktime for user %s: %s",
                    username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes worktime data using the transaction system
     */
    public void writeUserWorktime(String username, List<WorkTimeTable> entries, int year, int month) {
        // Get current user for validation
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Only validate write access if user is writing their own data
        if (currentUsername.equals(username)) {
            securityRules.validateFileAccess(username, true);
        }

        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);

            // Serialize the data
            byte[] content = objectMapper.writeValueAsBytes(entries);

            // Add local write operation
            transaction.addWrite(localPath, content);

            // Add network sync if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.toNetworkPath(localPath);
                transaction.addSync(localPath, networkPath);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write worktime: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write worktime: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d worktime entries for user %s - %d/%d",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing worktime for user %s: %s",
                    username, e.getMessage()), e);
        }
    }

    /**
     * Reads worktime data with the operating username for validation
     */
    public List<WorkTimeTable> readUserWorktime(String username, int year, int month, String operatingUsername) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // Verify username matches the operating user
            if (username.equals(operatingUsername)) {
                FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);
                Optional<List<WorkTimeTable>> entries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {
                }, true);
                return entries.orElse(new ArrayList<>());
            } else {
                throw new SecurityException("Username mismatch with operating user");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading worktime for user %s: %s",
                    username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes worktime data with the operating username for validation
     * Uses explicit transaction management
     */
    public void writeUserWorktime(String username, List<WorkTimeTable> entries, int year, int month, String operatingUsername) {
        // Verify the username matches the operating username
        if (!username.equals(operatingUsername)) {
            throw new SecurityException("Username mismatch with operating username");
        }

        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);

            // Serialize the data
            byte[] content = objectMapper.writeValueAsBytes(entries);

            // Add to transaction
            transaction.addWrite(localPath, content);

            // Add network sync if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.toNetworkPath(localPath);
                transaction.addSync(localPath, networkPath);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write worktime: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write worktime: " + result.getErrorMessage());
            }

            // Add more detailed logging including entry information
            if (!entries.isEmpty()) {
                WorkTimeTable latestEntry = entries.get(entries.size() - 1);
                LoggerUtil.info(this.getClass(), String.format(
                        "Saved worktime entry for user %s - %d/%d using file-based auth. Latest entry date: %s, Status: %s",
                        username, year, month,
                        latestEntry.getWorkDate(),
                        latestEntry.getAdminSync()));
            } else {
                LoggerUtil.info(this.getClass(), String.format(
                        "Saved empty worktime entry list for user %s - %d/%d using file-based auth",
                        username, year, month));
            }

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing worktime for user %s: %s",
                    username, e.getMessage()), e);
        }
    }

    // ===== Time Off Tracker methods =====

    /**
     * Reads time off entries in read-only mode
     */
    public List<WorkTimeTable> readTimeOffReadOnly(String username, int year) {
        List<WorkTimeTable> allEntries = new ArrayList<>();

        // Only load last 12 months to improve performance
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        // Only process months for the requested year
        for (int month = 1; month <= 12; month++) {
            // Skip future months
            if (year > currentYear || (year == currentYear && month > currentMonth)) {
                continue;
            }

            try {
                List<WorkTimeTable> monthEntries = readWorktimeReadOnly(username, year, month);
                if (monthEntries != null && !monthEntries.isEmpty()) {
                    // Filter for time off entries only
                    List<WorkTimeTable> timeOffEntries = monthEntries.stream()
                            .filter(entry -> entry.getTimeOffType() != null)
                            .toList();
                    allEntries.addAll(timeOffEntries);
                }
            } catch (Exception e) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Read-only time-off access failed for %s - %d/%d: %s",
                        username, year, month, e.getMessage()));
            }
        }

        return allEntries;
    }

    /**
     * Reads the time off tracker
     */
    public TimeOffTracker readTimeOffTracker(String username, Integer userId, int year) {
        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);

            // Create file paths
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

            // First try to read from local file
            Optional<TimeOffTracker> localResult = fileReaderService.readLocalFile(localPath, new TypeReference<>() {
            }, true);

            if (localResult.isPresent()) {
                return localResult.get();
            }

            // If network is available, try to read from network
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);
                Optional<TimeOffTracker> networkResult = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
                }, true);

                if (networkResult.isPresent()) {
                    // Save to local for future use
                    TimeOffTracker tracker = networkResult.get();
                    writeTimeOffTracker(tracker, year);
                    return tracker;
                }
            }

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading time off tracker for %s (%d): %s",
                    username, year, e.getMessage()));
            return null;
        }
    }

    /**
     * Writes the time off tracker using the transaction system
     */
    public void writeTimeOffTracker(TimeOffTracker tracker, int year) {
        if (tracker == null || tracker.getUsername() == null) {
            LoggerUtil.error(this.getClass(), "Cannot save null tracker or tracker without username");
            return;
        }

        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);
            FilePath localPath = pathResolver.getLocalPath(tracker.getUsername(), tracker.getUserId(),
                    FilePathResolver.FileType.TIMEOFF_TRACKER, params);

            // Serialize the data
            byte[] content = objectMapper.writeValueAsBytes(tracker);

            // Add to transaction
            transaction.addWrite(localPath, content);

            // Add network sync if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.toNetworkPath(localPath);
                transaction.addSync(localPath, networkPath);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to save time off tracker: " + result.getErrorMessage());
                return;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Saved time off tracker for %s (%d) with %d requests",
                    tracker.getUsername(), year, tracker.getRequests().size()));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.error(this.getClass(), String.format(
                    "Error saving time off tracker for %s (%d): %s",
                    tracker.getUsername(), year, e.getMessage()));
        }
    }

    /**
     * Reads the time off tracker in read-only mode
     */
    public TimeOffTracker readTimeOffTrackerReadOnly(String username, Integer userId, int year) {
        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);

            // Try network first if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);
                Optional<TimeOffTracker> result = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {
                }, true);

                if (result.isPresent()) {
                    return result.get();
                }
            }

            // Fall back to local file
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);
            Optional<TimeOffTracker> result = fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {
            }, true);

            return result.orElse(null);
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Read-only time-off tracker access failed for %s (%d): %s",
                    username, year, e.getMessage()));
            return null;
        }
    }

    // ===== Register methods =====

    /**
     * Reads register entries in read-only mode
     */
    public List<RegisterEntry> readRegisterReadOnly(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // First try network if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                Optional<List<RegisterEntry>> entries = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {
                }, true);

                if (entries.isPresent()) {
                    return entries.get();
                }
            }

            // Fall back to local file
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);
            Optional<List<RegisterEntry>> entries = fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {
            }, true);

            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Read-only register access failed for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes user register entries using transaction system
     */
    public void writeUserRegister(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        securityRules.validateFileAccess(username, true);

        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(entries);

            // Add to transaction
            transaction.addWrite(localPath, content);

            // Add network sync if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.toNetworkPath(localPath);
                transaction.addSync(localPath, networkPath);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write register: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write register: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d register entries for user %s - %d/%d",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing register for user %s: %s",
                    username, e.getMessage()), e);
        }
    }

    /**
     * Reads user register entries
     */
    public List<RegisterEntry> readUserRegister(String username, Integer userId, int year, int month) {
        try {
            // Get current user from security context
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // If accessing own data, use local path
            if (currentUsername.equals(username)) {
                LoggerUtil.info(this.getClass(), String.format("Reading local register for user %s", username));
                FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                Optional<List<RegisterEntry>> entries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {
                }, true);
                return entries.orElse(new ArrayList<>());
            }

            // If accessing other user's data, try network path
            if (pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Reading network register for user %s by %s",
                        username, currentUsername));

                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                Optional<List<RegisterEntry>> entries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
                }, true);
                return entries.orElse(new ArrayList<>());
            }

            throw new RuntimeException("Network access required but not available");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading register for user %s: %s",
                    username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ===== Register Check methods =====

    /**
     * Writes check register entries using transaction system
     */
    public void writeUserCheckRegister(String username, Integer userId, List<RegisterCheckEntry> entries, int year, int month) {
        securityRules.validateFileAccess(username, true);

        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(entries);

            // Add to transaction
            transaction.addWrite(localPath, content);

            // Add network sync if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.toNetworkPath(localPath);
                transaction.addSync(localPath, networkPath);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write check register: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write check register: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d check register entries for user %s - %d/%d",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing check register for user %s: %s",
                    username, e.getMessage()), e);
        }
    }

    /**
     * Reads user check register entries
     */
    public List<RegisterCheckEntry> readUserCheckRegister(String username, Integer userId, int year, int month) {
        try {
            // Get current user from security context
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // If accessing own data, use local path
            if (currentUsername.equals(username)) {
                LoggerUtil.info(this.getClass(), String.format("Reading local check register for user %s", username));
                FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);
                Optional<List<RegisterCheckEntry>> entries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {
                }, true);
                return entries.orElse(new ArrayList<>());
            }

            // If accessing other user's data, try network path
            if (pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Reading network check register for user %s by %s",
                        username, currentUsername));

                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);
                Optional<List<RegisterCheckEntry>> entries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
                }, true);
                return entries.orElse(new ArrayList<>());
            }

            throw new RuntimeException("Network access required but not available");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading check register for user %s: %s",
                    username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ===== Team Members methods =====

    /**
     * Reads team members data
     */
    public List<TeamMemberDTO> readTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath teamPath = pathResolver.getLocalPath(teamLeadUsername, null, FilePathResolver.FileType.TEAM, params);
            Optional<List<TeamMemberDTO>> members = fileReaderService.readLocalFile(teamPath, new TypeReference<>() {
            }, true);
            return members.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading team members for %s (%d/%d): %s",
                    teamLeadUsername, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes team members data using transaction system
     */
    public void writeTeamMembers(List<TeamMemberDTO> teamMemberDTOS, String teamLeadUsername, int year, int month) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath teamPath = pathResolver.getLocalPath(teamLeadUsername, null, FilePathResolver.FileType.TEAM, params);

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(teamMemberDTOS);

            // Add to transaction
            transaction.addWrite(teamPath, content);

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write team members: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write team members: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d team members for %s (%d/%d)",
                    teamMemberDTOS.size(), teamLeadUsername, year, month));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing team members for %s (%d/%d): %s",
                    teamLeadUsername, year, month, e.getMessage()), e);
        }
    }

    // ===== Notification methods =====

//    /**
//     * Writes notification tracking file using transaction system
//     */
//    public void writeNotificationTrackingFile(String username, String notificationType, LocalDateTime timestamp) {
//        // Start a transaction
//        FileTransaction transaction = transactionManager.beginTransaction();
//
//        try {
//            Map<String, Object> params = new HashMap<>();
//            params.put("notificationType", notificationType);
//
//            FilePath filePath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.NOTIFICATION, params);
//
//            // Create parent directory if it doesn't exist
//            Files.createDirectories(filePath.getPath().getParent());
//
//            // Convert timestamp to JSON for better readability and consistency
//            Map<String, Object> trackingData = new HashMap<>();
//            trackingData.put("username", username);
//            trackingData.put("notificationType", notificationType);
//            trackingData.put("timestamp", timestamp.toString());
//
//            byte[] content = objectMapper.writeValueAsBytes(trackingData);
//
//            // Add to transaction
//            transaction.addWrite(filePath, content);
//
//            // Commit the transaction
//            FileTransactionResult result = transactionManager.commitTransaction();
//
//            if (!result.isSuccess()) {
//                LoggerUtil.error(this.getClass(), "Failed to write notification tracking file: " + result.getErrorMessage());
//                return;
//            }
//
//            LoggerUtil.info(this.getClass(), String.format("Created notification tracking file for %s", username));
//
//        } catch (Exception e) {
//            transactionManager.rollbackTransaction();
//            LoggerUtil.error(this.getClass(), String.format(
//                    "Error writing notification tracking file for user %s with type %s: %s",
//                    username, notificationType, e.getMessage()), e);
//        }
//    }
//
//    /**
//     * Reads notification tracking file
//     */
//    public LocalDateTime readNotificationTrackingFile(String username, String notificationType) {
//        try {
//            Map<String, Object> params = new HashMap<>();
//            params.put("notificationType", notificationType);
//
//            FilePath filePath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.NOTIFICATION, params);
//
//            if (!Files.exists(filePath.getPath())) {
//                return null;
//            }
//
//            Optional<Map<String, Object>> content = fileReaderService.readLocalFile(filePath, new TypeReference<>() {}, true);
//
//            if (content.isPresent()) {
//                Map<String, Object> trackingData = content.get();
//                String timestampStr = (String) trackingData.get("timestamp");
//                if (timestampStr != null) {
//                    return LocalDateTime.parse(timestampStr);
//                }
//            }
//
//            return null;
//        } catch (Exception e) {
//            LoggerUtil.error(this.getClass(), String.format("Error reading notification tracking file for user %s with type %s: %s", username, notificationType, e.getMessage()), e);
//            return null;
//        }
//    }
//
//    /**
//     * Updates notification count file using transaction system
//     */
//    public int updateNotificationCountFile(String username, String notificationType, int maxCount) {
//        try {
//            Map<String, Object> params = new HashMap<>();
//            params.put("notificationType", notificationType + "_count");
//
//            FilePath filePath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.NOTIFICATION, params);
//
//            // Read current count if file exists
//            int count = 0;
//            if (Files.exists(filePath.getPath())) {
//                Optional<Map<String, Object>> content = fileReaderService.readLocalFile(filePath, new TypeReference<>() {
//                }, true);
//
//                if (content.isPresent()) {
//                    Map<String, Object> countData = content.get();
//                    Object countObj = countData.get("count");
//                    if (countObj instanceof Integer) {
//                        count = (Integer) countObj;
//                    } else if (countObj instanceof Number) {
//                        count = ((Number) countObj).intValue();
//                    }
//                }
//            }
//
//            // Increment and save count if not already at max
//            if (count < maxCount) {
//                // Start a transaction
//                FileTransaction transaction = transactionManager.beginTransaction();
//
//                try {
//                    count++;
//
//                    // Create parent directory if it doesn't exist
//                    Files.createDirectories(filePath.getPath().getParent());
//
//                    // Prepare data
//                    Map<String, Object> countData = new HashMap<>();
//                    countData.put("username", username);
//                    countData.put("notificationType", notificationType);
//                    countData.put("count", count);
//                    countData.put("lastUpdated", LocalDateTime.now().toString());
//
//                    byte[] content = objectMapper.writeValueAsBytes(countData);
//
//                    transaction.addWrite(filePath, content);
//
//                    // Commit the transaction
//                    FileTransactionResult result = transactionManager.commitTransaction();
//
//                    if (!result.isSuccess()) {
//                        LoggerUtil.error(this.getClass(), "Failed to update notification count: " + result.getErrorMessage());
//                    }
//                } catch (Exception e) {
//                    transactionManager.rollbackTransaction();
//                    LoggerUtil.error(this.getClass(), "Error updating notification count: " + e.getMessage());
//                }
//            }
//
//            return count;
//        } catch (Exception e) {
//            LoggerUtil.error(this.getClass(), String.format(
//                    "Error managing notification count file for user %s with type %s: %s",
//                    username, notificationType, e.getMessage()), e);
//            return 0;
//        }
//    }

    // ===== Status Cache methods =====

    /**
     * Reads local status cache
     */
    public LocalStatusCache readLocalStatusCache() {
        try {
            FilePath cachePath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.STATUS, new HashMap<>());
            Optional<LocalStatusCache> cache = fileReaderService.readLocalFile(cachePath, new TypeReference<>() {
            }, true);
            return cache.orElse(new LocalStatusCache());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading local status cache: " + e.getMessage(), e);
            return new LocalStatusCache();
        }
    }

    /**
     * Writes local status cache using transaction system
     */
    public void writeLocalStatusCache(LocalStatusCache cache) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            FilePath cachePath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.STATUS, new HashMap<>());

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(cache);

            // Add to transaction
            transaction.addWrite(cachePath, content);

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write local status cache: " + result.getErrorMessage());
                return;
            }

            LoggerUtil.debug(this.getClass(), "Successfully wrote local status cache");

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.error(this.getClass(), "Error writing local status cache: " + e.getMessage(), e);
        }
    }

    // ===== Network Status Flag methods =====

    /**
     * Creates network status flag
     */
    public void createNetworkStatusFlag(String username, String dateCode, String timeCode, String statusCode) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.warn(this.getClass(), "Network unavailable, cannot create status flag");
                return;
            }

            // We need to use the raw Path API here because we're creating a file with a dynamic name
            Path networkFlagsDir = pathConfig.getNetworkStatusFlagsDirectory();
            Files.createDirectories(networkFlagsDir);

            // Delete any existing status flags for this user
            try (Stream<Path> files = Files.list(networkFlagsDir)) {
                files.filter(path -> path.getFileName().toString().startsWith("status_" + username + "_")).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        LoggerUtil.error(this.getClass(), "Error deleting old network status flag: " + e.getMessage());
                    }
                });
            }

            // Create the new flag file on network
            String flagFilename = String.format(pathConfig.getStatusFlagFormat(), username, dateCode, timeCode, statusCode);
            Path networkFlagPath = networkFlagsDir.resolve(flagFilename);
            Files.createFile(networkFlagPath);

            LoggerUtil.debug(this.getClass(), "Created network status flag: " + flagFilename);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating network status flag for " + username + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads network status flags
     */
    public List<Path> readNetworkStatusFlags() {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.warn(this.getClass(), "Network unavailable, cannot read status flags");
                return new ArrayList<>();
            }

            Path networkFlagsDir = pathConfig.getNetworkStatusFlagsDirectory();
            if (!Files.exists(networkFlagsDir)) {
                return new ArrayList<>();
            }

            try (Stream<Path> files = Files.list(networkFlagsDir)) {
                return files.filter(path -> path.getFileName().toString().matches("status_.*\\.flag")).collect(Collectors.toList());
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading network status flags: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Deletes network status flag
     */
    public boolean deleteNetworkStatusFlag(Path flagPath) {
        try {
            return Files.deleteIfExists(flagPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error deleting network status flag: " + e.getMessage(), e);
            return false;
        }
    }

    // ===== Admin Worktime methods =====

    /**
     * Writes admin worktime data using transaction system
     */
    public void writeAdminWorktime(List<WorkTimeTable> entries, int year, int month) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(entries);

            // Add to transaction
            transaction.addWrite(localPath, content);

            // Add network sync if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.toNetworkPath(localPath);
                transaction.addSync(localPath, networkPath);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write admin worktime: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write admin worktime: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d admin worktime entries for %d/%d",
                    entries.size(), year, month));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing admin worktime for %d/%d: %s",
                    year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads local admin worktime data
     */
    public List<WorkTimeTable> readLocalAdminWorktime(int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);
            Optional<List<WorkTimeTable>> entries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {
            }, true);
            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading local admin worktime for %d/%d: %s",
                    year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads network admin worktime data
     */
    public List<WorkTimeTable> readNetworkAdminWorktime(int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format("Network not available for admin worktime %d/%d", year, month));
                return new ArrayList<>();
            }

            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);
            Optional<List<WorkTimeTable>> entries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
            }, true);
            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading network admin worktime for %d/%d: %s",
                    year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ===== Register Files Search method =====

    /**
     * Finds register files across multiple months
     */
    public List<RegisterEntry> findRegisterFiles(String username, Integer userId) {
        List<RegisterEntry> allEntries = new ArrayList<>();

        try {
            // Determine the years and months to search
            int currentYear = LocalDate.now().getYear();
            int currentMonth = LocalDate.now().getMonthValue();

            // Search previous 24 months (2 years)
            for (int year = currentYear; year >= currentYear - 1; year--) {
                for (int month = (year == currentYear ? currentMonth : 12); month >= 1; month--) {
                    Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

                    // Try network path first if available
                    if (pathConfig.isNetworkAvailable()) {
                        try {
                            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                            Optional<List<RegisterEntry>> networkEntries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
                            }, true);
                            networkEntries.ifPresent(allEntries::addAll);
                        } catch (Exception e) {
                            LoggerUtil.warn(this.getClass(), String.format(
                                    "Error reading network register for %s (%d/%d): %s",
                                    username, year, month, e.getMessage()));
                        }
                    }

                    // Fallback to local path
                    try {
                        FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                        Optional<List<RegisterEntry>> localEntries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {
                        }, true);
                        localEntries.ifPresent(allEntries::addAll);
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Error reading local register for %s (%d/%d): %s",
                                username, year, month, e.getMessage()));
                    }
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error searching register files for %s: %s",
                    username, e.getMessage()));
        }

        return allEntries;
    }

    // ===== Additional methods for CheckRegisterService, AdminRegisterService, etc. =====

    /**
     * Reads local lead check register
     */
    public List<RegisterCheckEntry> readLocalLeadCheckRegister(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath leadCheckPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.LEAD_CHECK_REGISTER, params);
            Optional<List<RegisterCheckEntry>> teamLeadEntriesOpt = fileReaderService.readFileReadOnly(leadCheckPath, new TypeReference<>() {
            }, true);
            return teamLeadEntriesOpt.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading local lead check register for %s (%d/%d): %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads admin bonus entries
     */
    public List<BonusEntry> readAdminBonus(int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath bonusPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_BONUS, params);
            Optional<List<BonusEntry>> bonusEntriesOpt = fileReaderService.readLocalFile(bonusPath, new TypeReference<>() {
            }, true);
            return bonusEntriesOpt.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading admin bonus for %d/%d: %s",
                    year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads network user register
     */
    public List<RegisterEntry> readNetworkUserRegister(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);
            Optional<List<RegisterEntry>> userEntriesOpt = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
            }, true);
            return userEntriesOpt.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading network user register for %s (%d/%d): %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads local admin register
     */
    public List<RegisterEntry> readLocalAdminRegister(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath adminPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);
            Optional<List<RegisterEntry>> adminEntriesOpt = fileReaderService.readLocalFile(adminPath, new TypeReference<>() {
            }, true);
            return adminEntriesOpt.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading local admin register for %s (%d/%d): %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes local admin register using transaction system
     */
    public void writeLocalAdminRegister(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath adminPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(entries);

            // Add to transaction
            transaction.addWrite(adminPath, content);

            // Add network sync if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.toNetworkPath(adminPath);
                transaction.addSync(adminPath, networkPath);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write admin register: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write admin register: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d admin register entries for user %s - %d/%d",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing admin register for user %s: %s",
                    username, e.getMessage()), e);
        }
    }

    /**
     * Syncs admin register to network explicitly using SyncFilesService
     */
    public void syncAdminRegisterToNetwork(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);

            // Using CompletableFuture with explicit wait for completion
            LoggerUtil.info(this.getClass(), String.format(
                    "Starting explicit sync of admin register for user %s - %d/%d",
                    username, year, month));

            CompletableFuture<FileOperationResult> future = syncFileService.syncToNetwork(localPath, networkPath);

            // Wait for the sync to complete
            FileOperationResult result = future.get();  // Using .get() to wait for completion

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to sync admin register to network: " +
                        result.getErrorMessage().orElse("Unknown error"));
                throw new RuntimeException("Failed to sync admin register to network: " +
                        result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully synced admin register for user %s - %d/%d to network",
                    username, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to sync admin register to network: " + e.getMessage());
            throw new RuntimeException("Failed to sync admin register to network", e);
        }
    }

    /**
     * Writes admin bonus entries using transaction system
     */
    public void writeAdminBonus(List<BonusEntry> entries, int year, int month) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath bonusPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_BONUS, params);

            // Serialize data
            byte[] content = objectMapper.writeValueAsBytes(entries);

            // Add to transaction
            transaction.addWrite(bonusPath, content);

            // Add network sync if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.toNetworkPath(bonusPath);
                transaction.addSync(bonusPath, networkPath);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write bonus entries: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write bonus entries: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d bonus entries for %d/%d",
                    entries.size(), year, month));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing bonus entries for %d/%d: %s",
                    year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads check register in read-only mode
     */
    public List<RegisterCheckEntry> readCheckRegisterReadOnly(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            // Get network path for check register
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);

            // Log exact path being accessed
            LoggerUtil.debug(this.getClass(), "Attempting to read check register from: " + networkPath.getPath().toString());

            // Check if file exists
            if (Files.exists(networkPath.getPath())) {
                LoggerUtil.debug(this.getClass(), "Check register file exists, size: " + Files.size(networkPath.getPath()));
            } else {
                LoggerUtil.debug(this.getClass(), "Check register file does not exist at path: " + networkPath.getPath().toString());
            }

            // Read file
            Optional<List<RegisterCheckEntry>> entriesOpt = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
            }, true);

            if (entriesOpt.isPresent()) {
                List<RegisterCheckEntry> entries = entriesOpt.get();
                LoggerUtil.debug(this.getClass(), String.format(
                        "Successfully read network check register for user %s (%d/%d) with %d entries",
                        username, month, year, entries.size()));
                return entries;
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No network check register found for user %s (%d/%d)",
                        username, month, year));
                return new ArrayList<>();
            }
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading network check register for user %s (%d/%d): %s",
                    username, month, year, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ===== Login Controller =====

    /**
     * Checks if network is available
     * @return True if network is available
     */
    public boolean isNetworkAvailable() {
        return pathConfig.isNetworkAvailable();
    }

    /**
     * Checks if offline mode is available by verifying the existence of local user files
     * @return True if there are any local user files
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
                boolean available = pathStream.anyMatch(path -> path.getFileName().toString().startsWith("local_user_") && path.getFileName().toString().endsWith(".json"));
                LoggerUtil.debug(this.getClass(), String.format("Offline mode availability: %s", available));
                return available;
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking offline mode availability: " + e.getMessage(), e);
            return false;
        }
    }

    // ===== Log Controller/Service ======

    /**
     * Checks if local log file exists
     * @return true if the local log file exists
     */
    public boolean localLogExists() {
        try {
            Path localLogPath = pathConfig.getLocalLogPath();
            return Files.exists(localLogPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking if local log exists: " + e.getMessage());
            return false;
        }
    }

    /**
     * Syncs the local log file to the network for a specific user
     * @param username The username
     * @throws IOException if the file cannot be synced
     */
    public void syncLogToNetwork(String username) throws IOException {
        Path sourceLogPath = pathConfig.getLocalLogPath();
        Path targetLogPath = pathConfig.getNetworkLogPath(username);

        // Ensure target directory exists (handled by PathConfig)
        Path networkLogsDir = targetLogPath.getParent();
        if (!Files.exists(networkLogsDir)) {
            Files.createDirectories(networkLogsDir);
        }

        // Copy log file
        Files.copy(sourceLogPath, targetLogPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Gets the list of usernames with available logs
     * @return List of usernames
     */
    public List<String> getUserLogsList() {
        try {
            Path logDir = pathConfig.getNetworkLogDirectory();
            if (!Files.exists(logDir)) {
                return new ArrayList<>();
            }

            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
                LoggerUtil.info(this.getClass(), "Created network logs directory: " + logDir);
            }

            try (Stream<Path> files = Files.list(logDir)) {
                return files
                        .filter(path -> path.getFileName().toString().startsWith("ctgraphdep-logger_") &&
                                path.getFileName().toString().endsWith(".log"))
                        .map(path -> {
                            String filename = path.getFileName().toString();
                            // Extract username from filename format: ctgraphdep-logger_username.log
                            return filename.substring(18, filename.length() - 4);
                        })
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error listing log files: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Reads log content for a specific user
     * @param username The username
     * @return The log content or empty if not found
     */
    public Optional<String> getUserLogContent(String username) {
        try {
            Path logPath = pathConfig.getNetworkLogPath(username);;

            if (!Files.exists(logPath)) {
                return Optional.empty();
            }

            String content = Files.readString(logPath);
            return Optional.of(content);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error reading log for " + username + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    // ===== Authentication checks =======

    /**
     * Ensures that all required local directories exist
     * Creates them if they don't exist
     * @param isAdmin whether to also ensure admin directories
     * @return true if all required directories exist or were created
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
     * Revalidates all local directories, attempting to recreate any that are missing
     * This can be called when there are file access errors to recover the directory structure
     * @param isAdmin whether to also validate admin directories
     * @return true if revalidation was successful
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

    // ===== Utility methods =====

    /**
     * Validates a session object has required fields
     */
    private void validateSession(WorkUsersSessionsStates session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        if (session.getUsername() == null || session.getUserId() == null) {
            throw new IllegalArgumentException("Session must have both username and userId");
        }
    }

}