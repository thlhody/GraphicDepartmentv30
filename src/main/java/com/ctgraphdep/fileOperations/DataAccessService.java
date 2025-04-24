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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        Optional<WorkUsersSessionsStates> result = fileReaderService.readLocalFile(localPath, new TypeReference<>() {
        }, true);

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
     * Reads users from the network, falling back to local if necessary
     */
    public List<User> readUsersNetwork() {
        // Create a file path object for network users
        FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.USERS, FilePathResolver.createParams());

        // First try network if available
        if (pathConfig.isNetworkAvailable()) {
            try {
                Optional<List<User>> networkUsers = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
                }, true);

                if (networkUsers.isPresent() && !networkUsers.get().isEmpty()) {
                    return networkUsers.get();
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), "Network users read failed, falling back to local: " + e.getMessage());
            }
        }

        // Fall back to local users
        try {
            LoggerUtil.info(this.getClass(), "Reading local users due to network unavailability");
            List<User> localUsers = readLocalUser();
            if (localUsers.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "No local users found");
            } else {
                LoggerUtil.info(this.getClass(), String.format("Found %d local users", localUsers.size()));
            }
            return localUsers;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to read local users: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Writes users to the network
     */
    public void writeUsersNetwork(List<User> users) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            // Create a file path object for network users
            FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.USERS, FilePathResolver.createParams());

            // Write the users to the network path
            byte[] content = objectMapper.writeValueAsBytes(users);
            transaction.addWrite(networkPath, content);

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write network users: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write network users: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d users to network", users.size()));
        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing network users: %s", e.getMessage()), e);
        }
    }

    /**
     * Reads users from local storage
     */
    public List<User> readLocalUser() {
        if (!pathConfig.isLocalAvailable()) {
            pathConfig.revalidateLocalAccess();
        }

        // Create a file path object for local users
        FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.USERS, FilePathResolver.createParams());

        try {
            // Read the file
            Optional<List<User>> users = fileReaderService.readLocalFile(localPath, new TypeReference<>() {
            }, true);

            if (users.isEmpty()) {
                LoggerUtil.info(this.getClass(), "No local users found, creating empty list");
                return new ArrayList<>();
            }
            return users.get();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading local users: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes a user to local storage
     */
    public void writeLocalUser(User user) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            // Create a file path object for local users
            FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.USERS, FilePathResolver.createParams());

            // Get current local users
            List<User> existingUsers = readLocalUser();

            // Check if user already exists
            boolean userExists = false;
            for (int i = 0; i < existingUsers.size(); i++) {
                if (existingUsers.get(i).getUsername().equals(user.getUsername())) {
                    // Replace existing user with updated one
                    existingUsers.set(i, user);
                    userExists = true;
                    break;
                }
            }

            // If user doesn't exist, add them
            if (!userExists) {
                existingUsers.add(user);
            }

            // Write the entire list back within the transaction
            byte[] content = objectMapper.writeValueAsBytes(existingUsers);
            transaction.addWrite(localPath, content);

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write local user: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write local user: " + result.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully updated local user: %s", user.getUsername()));
        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error updating local user %s: %s", user.getUsername(), e.getMessage()), e);
        }
    }

    // ===== Holiday entries operations =====

    /**
     * Reads holiday entries with network and local cache
     */
    public List<PaidHolidayEntryDTO> readHolidayEntries() {
        try {
            // Create file path objects for network and cache paths
            FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.HOLIDAY, FilePathResolver.createParams());
            FilePath cachePath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.HOLIDAY, FilePathResolver.createParams());

            // Try network first
            if (pathConfig.isNetworkAvailable()) {
                Optional<List<PaidHolidayEntryDTO>> networkEntries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {
                }, true);

                if (networkEntries.isPresent()) {
                    // Update cache with network data using transaction system
                    updateHolidayCache(networkEntries.get(), cachePath);
                    return networkEntries.get();
                }
            }

            // Fallback to cache if network unavailable or read failed
            Optional<List<PaidHolidayEntryDTO>> cacheEntries = fileReaderService.readLocalFile(cachePath, new TypeReference<>() {
            }, true);
            return cacheEntries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading holiday entries: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Helper method to update holiday cache
     */
    private void updateHolidayCache(List<PaidHolidayEntryDTO> entries, FilePath cachePath) {
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            byte[] content = objectMapper.writeValueAsBytes(entries);
            transaction.addWrite(cachePath, content);

            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.warn(this.getClass(), "Failed to update holiday cache: " + result.getErrorMessage());
            }
        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.warn(this.getClass(), "Error updating holiday cache: " + e.getMessage());
        }
    }

    /**
     * Writes holiday entries to both network and local cache
     */
    public void writeHolidayEntries(List<PaidHolidayEntryDTO> entries) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            // Create file path objects for network and cache paths
            FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.HOLIDAY, FilePathResolver.createParams());
            FilePath cachePath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.HOLIDAY, FilePathResolver.createParams());

            // Serialize content once
            byte[] content = objectMapper.writeValueAsBytes(entries);

            // Always update cache first
            transaction.addWrite(cachePath, content);

            // Then try network if available
            if (pathConfig.isNetworkAvailable()) {
                transaction.addWrite(networkPath, content);
            }

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write holiday entries: " + result.getErrorMessage());
                throw new RuntimeException("Failed to write holiday entries: " + result.getErrorMessage());
            }

            if (pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), "Successfully wrote holiday entries to network");
            } else {
                LoggerUtil.warn(this.getClass(), "Network unavailable, holiday entries stored in cache only");
            }
        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing holiday entries: %s", e.getMessage()), e);
        }
    }

    /**
     * Reads holiday entries in read-only mode
     */
    public List<PaidHolidayEntryDTO> readHolidayEntriesReadOnly() {
        try {
            // Create file path objects for network and cache paths
            FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.HOLIDAY, FilePathResolver.createParams());
            FilePath cachePath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.HOLIDAY, FilePathResolver.createParams());

            // Try network first if available
            if (pathConfig.isNetworkAvailable()) {
                Optional<List<PaidHolidayEntryDTO>> networkEntries = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {
                }, true);

                if (networkEntries.isPresent()) {
                    return networkEntries.get();
                }
            }

            // Fallback to cache if network unavailable or read failed
            Optional<List<PaidHolidayEntryDTO>> cacheEntries = fileReaderService.readFileReadOnly(cachePath, new TypeReference<>() {
            }, true);
            return cacheEntries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Error reading holiday entries in read-only mode: %s", e.getMessage()));
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

    /**
     * Writes notification tracking file using transaction system
     */
    public void writeNotificationTrackingFile(String username, String notificationType, LocalDateTime timestamp) {
        // Start a transaction
        FileTransaction transaction = transactionManager.beginTransaction();

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("notificationType", notificationType);

            FilePath filePath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.NOTIFICATION, params);

            // Create parent directory if it doesn't exist
            Files.createDirectories(filePath.getPath().getParent());

            // Convert timestamp to JSON for better readability and consistency
            Map<String, Object> trackingData = new HashMap<>();
            trackingData.put("username", username);
            trackingData.put("notificationType", notificationType);
            trackingData.put("timestamp", timestamp.toString());

            byte[] content = objectMapper.writeValueAsBytes(trackingData);

            // Add to transaction
            transaction.addWrite(filePath, content);

            // Commit the transaction
            FileTransactionResult result = transactionManager.commitTransaction();

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to write notification tracking file: " + result.getErrorMessage());
                return;
            }

            LoggerUtil.info(this.getClass(), String.format("Created notification tracking file for %s", username));

        } catch (Exception e) {
            transactionManager.rollbackTransaction();
            LoggerUtil.error(this.getClass(), String.format(
                    "Error writing notification tracking file for user %s with type %s: %s",
                    username, notificationType, e.getMessage()), e);
        }
    }

    /**
     * Reads notification tracking file
     */
    public LocalDateTime readNotificationTrackingFile(String username, String notificationType) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("notificationType", notificationType);

            FilePath filePath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.NOTIFICATION, params);

            if (!Files.exists(filePath.getPath())) {
                return null;
            }

            Optional<Map<String, Object>> content = fileReaderService.readLocalFile(filePath, new TypeReference<>() {}, true);

            if (content.isPresent()) {
                Map<String, Object> trackingData = content.get();
                String timestampStr = (String) trackingData.get("timestamp");
                if (timestampStr != null) {
                    return LocalDateTime.parse(timestampStr);
                }
            }

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading notification tracking file for user %s with type %s: %s", username, notificationType, e.getMessage()), e);
            return null;
        }
    }

    /**
     * Updates notification count file using transaction system
     */
    public int updateNotificationCountFile(String username, String notificationType, int maxCount) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("notificationType", notificationType + "_count");

            FilePath filePath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.NOTIFICATION, params);

            // Read current count if file exists
            int count = 0;
            if (Files.exists(filePath.getPath())) {
                Optional<Map<String, Object>> content = fileReaderService.readLocalFile(filePath, new TypeReference<>() {
                }, true);

                if (content.isPresent()) {
                    Map<String, Object> countData = content.get();
                    Object countObj = countData.get("count");
                    if (countObj instanceof Integer) {
                        count = (Integer) countObj;
                    } else if (countObj instanceof Number) {
                        count = ((Number) countObj).intValue();
                    }
                }
            }

            // Increment and save count if not already at max
            if (count < maxCount) {
                // Start a transaction
                FileTransaction transaction = transactionManager.beginTransaction();

                try {
                    count++;

                    // Create parent directory if it doesn't exist
                    Files.createDirectories(filePath.getPath().getParent());

                    // Prepare data
                    Map<String, Object> countData = new HashMap<>();
                    countData.put("username", username);
                    countData.put("notificationType", notificationType);
                    countData.put("count", count);
                    countData.put("lastUpdated", LocalDateTime.now().toString());

                    byte[] content = objectMapper.writeValueAsBytes(countData);

                    transaction.addWrite(filePath, content);

                    // Commit the transaction
                    FileTransactionResult result = transactionManager.commitTransaction();

                    if (!result.isSuccess()) {
                        LoggerUtil.error(this.getClass(), "Failed to update notification count: " + result.getErrorMessage());
                    }
                } catch (Exception e) {
                    transactionManager.rollbackTransaction();
                    LoggerUtil.error(this.getClass(), "Error updating notification count: " + e.getMessage());
                }
            }

            return count;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error managing notification count file for user %s with type %s: %s",
                    username, notificationType, e.getMessage()), e);
            return 0;
        }
    }

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