package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.db.UserStatusRecord;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.security.FileAccessSecurityRules;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

@Service
public class DataAccessService {
    private final ObjectMapper objectMapper;
    private final FileObfuscationService obfuscationService;
    private final PathConfig pathConfig;
    private final FileSyncService fileSyncService;
    private final Map<Path, ReentrantReadWriteLock> fileLocks;
    private final FileBackupService backupService;
    private final FileAccessSecurityRules securityRules;

    public DataAccessService(ObjectMapper objectMapper,
                             FileObfuscationService obfuscationService,
                             PathConfig pathConfig,
                             FileSyncService fileSyncService,
                             FileBackupService backupService, FileAccessSecurityRules securityRules) {
        this.objectMapper = objectMapper;
        this.obfuscationService = obfuscationService;
        this.pathConfig = pathConfig;
        this.fileSyncService = fileSyncService;
        this.backupService = backupService;
        this.securityRules = securityRules;
        this.fileLocks = new ConcurrentHashMap<>();
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Core read/write operations
    private <T> T readFile(Path path, TypeReference<T> typeRef, boolean skipSerialization) {
        ReentrantReadWriteLock.ReadLock readLock = getFileLock(path).readLock();
        readLock.lock();
        try {
            // Try to read the main file first
            if (Files.exists(path) && Files.size(path) >= 3) {
                try {
                    byte[] content = Files.readAllBytes(path);
//                    if (!skipSerialization) {
//                        content = obfuscationService.deobfuscate(content);
//                    }
                    return objectMapper.readValue(content, typeRef);
                } catch (Exception e) {
                    // If main file read fails, try backup file
                    LoggerUtil.warn(this.getClass(), "Error reading main file: " + e.getMessage());
                }
            }

            // If main file doesn't exist or couldn't be read, try the backup
            Path backupPath = path.resolveSibling(path.getFileName() + WorkCode.BACKUP_EXTENSION);
            if (Files.exists(backupPath) && Files.size(backupPath) >= 3) {
                try {
                    LoggerUtil.info(this.getClass(), "Attempting to read from backup file: " + backupPath);
                    byte[] content = Files.readAllBytes(backupPath);
//                    if (!skipSerialization) {
//                        content = obfuscationService.deobfuscate(content);
//                    }
                    return objectMapper.readValue(content, typeRef);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error reading backup file: " + e.getMessage());
                }
            }

            // If we get here, neither main nor backup file could be read
            return null;

        } catch (IOException e) {
            LoggerUtil.logAndThrow(this.getClass(), "Error reading file: " + path, e);
            return null; // Unreachable but helps with compiler
        } finally {
            readLock.unlock();
        }
    }
    private <T> void writeFile(Path path, T data, boolean skipSerialization) {
        ReentrantReadWriteLock.WriteLock writeLock = getFileLock(path).writeLock();
        writeLock.lock();
        try {
            if (Files.exists(path)) {
                backupService.createBackup(path);
            }

            Files.createDirectories(path.getParent());
            byte[] content = objectMapper.writeValueAsBytes(data);

//            if (!skipSerialization) {
//                content = obfuscationService.obfuscate(content);
//            }

            try {
                Files.write(path, content);
                LoggerUtil.info(this.getClass(), "Successfully wrote to file: " + path);
                // Delete backup after successful write
                backupService.deleteBackup(path);
            } catch (Exception e) {
                // Restore from backup if write fails
                backupService.restoreFromBackup(path);
                LoggerUtil.logAndThrow(this.getClass(), "Successfully restored from backup ", e);
            }
        } catch (IOException e) {
            LoggerUtil.logAndThrow(this.getClass(), "Error writing file: " + path, e);
        } finally {
            writeLock.unlock();
        }
    }

    // Then the local/network operations use these base operations
    private <T> T readLocal(Path path, TypeReference<T> typeRef) {
        String filename = path.getFileName().toString();
        boolean skipSerialization = filename.equals("users.json") || filename.equals("local_users.json");
        return readFile(path, typeRef, skipSerialization);
    }
    private <T> void writeLocal(Path path, T data) {
        String filename = path.getFileName().toString();
        boolean skipSerialization = filename.equals("users.json") || filename.equals("local_users.json");
        writeFile(path, data, skipSerialization);
    }

    private <T> T readNetwork(Path path, TypeReference<T> typeRef) {
        if (!pathConfig.isNetworkAvailable()) {
            throw new RuntimeException("Network not available");
        }

        String filename = path.getFileName().toString();
        boolean skipSerialization = filename.equals("users.json") || filename.equals("local_users.json");

        try {
            // Try to read the main file first
            if (Files.exists(path) && Files.size(path) >= 3) {
                try {
                    byte[] content = Files.readAllBytes(path);
//                    if (!skipSerialization) {
//                        content = obfuscationService.deobfuscate(content);
//                    }
                    return objectMapper.readValue(content, typeRef);
                } catch (Exception e) {
                    // If main file read fails, log and continue to back up
                    LoggerUtil.warn(this.getClass(), "Error reading network file: " + e.getMessage());
                }
            }

            // If main file doesn't exist or couldn't be read, try the backup
            Path backupPath = path.resolveSibling(path.getFileName() + WorkCode.BACKUP_EXTENSION);
            if (Files.exists(backupPath) && Files.size(backupPath) >= 3) {
                LoggerUtil.info(this.getClass(), "Reading from backup network file: " + backupPath);
                byte[] content = Files.readAllBytes(backupPath);
//                if (!skipSerialization) {
//                    content = obfuscationService.deobfuscate(content);
//                }
                return objectMapper.readValue(content, typeRef);
            }

            // If we get here, neither main nor backup could be read
            return null;

        } catch (IOException e) {
            LoggerUtil.logAndThrow(this.getClass(), "Error reading network file: " + path, e);
            return null; // Unreachable but helps with compiler
        }
    }
    private <T> void writeNetwork(Path path, T data) {
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.error(this.getClass(),"Network not available");
        }
        String filename = path.getFileName().toString();
        boolean skipSerialization = filename.equals("users.json") || filename.equals("local_users.json");
        writeFile(path, data, skipSerialization);
    }

    // Session file operations - Local primary with network sync
    public void writeLocalSessionFile(WorkUsersSessionsStates session) {
        validateSession(session);

        Path localPath = pathConfig.getLocalSessionPath(session.getUsername(), session.getUserId());
        writeLocal(localPath, session);

        if (pathConfig.isNetworkAvailable()) {
            Path networkPath = pathConfig.getNetworkSessionPath(session.getUsername(), session.getUserId());
            fileSyncService.syncToNetwork(localPath, networkPath);
        }
    }
    public WorkUsersSessionsStates readLocalSessionFile(String username, Integer userId) {
        Path localPath = pathConfig.getLocalSessionPath(username, userId);
        return readLocal(localPath, new TypeReference<>() {
        });
    }

    // Session file - Network read only
    public WorkUsersSessionsStates readNetworkSessionFile(String username, Integer userId) {
        try {
            Path networkPath = pathConfig.getNetworkSessionPath(username, userId);
            return readNetwork(networkPath, new TypeReference<>() {
            });
        } catch (RuntimeException e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading network session for user status %s: %s", username, e.getMessage()));
            return null;
        }
    }
    // Network User file - Network only
    public List<User> readUsersNetwork() {
        Path networkPath = pathConfig.getNetworkUsersPath();

        // First try network if available
        if (pathConfig.isNetworkAvailable()) {
            try {
                List<User> networkUsers = readNetwork(networkPath, new TypeReference<>() {});
                if (networkUsers != null && !networkUsers.isEmpty()) {
                    // Don't cache network users automatically - only store when "Remember Me" is selected
                    return networkUsers;
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), "Network users read failed, falling back to local: " + e.getMessage());
            }
        }

        // Fall back to local users
        try {
            LoggerUtil.info(this.getClass(), "Reading local users due to network unavailability");
            List<User> localUsers = readLocalUsers();
            if (localUsers.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "No local users found");
            } else {
                LoggerUtil.info(this.getClass(),
                        String.format("Found %d local users", localUsers.size()));
            }
            return localUsers;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to read local users: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    public void writeUsersNetwork(List<User> users) {
        Path networkPath = pathConfig.getNetworkUsersPath();
        Path lockPath = pathConfig.getUsersLockPath();

        try {
            acquireLock(lockPath);
            writeNetwork(networkPath, users);
            LoggerUtil.debug(this.getClass(), "Successfully wrote users to network");
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing network users: %s", e.getMessage()), e);
        } finally {
            releaseLock(lockPath);
        }
    }

    // Local users file - Local only
    public List<User> readLocalUsers() {
        if (!pathConfig.isLocalAvailable()) {
            pathConfig.revalidateLocalAccess();
        }

        Path localPath = pathConfig.getLocalUsersPath();
        try {
            // Read doesn't need lock since we have ReentrantReadWriteLock in readFile
            List<User> users = readLocal(localPath, new TypeReference<>() {
            });
            if (users == null) {
                LoggerUtil.info(this.getClass(), "No local users found, creating empty list");
                return new ArrayList<>();
            }
            return users;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading local users: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }
    public void writeLocalUsers(List<User> users) {
        Path localPath = pathConfig.getLocalUsersPath();
        Path lockPath = pathConfig.getUsersLockPath();

        try {
            acquireLock(lockPath);
            writeLocal(localPath, users);
            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d local users", users.size()));
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), "Error writing local users: " + e.getMessage(), e);
        } finally {
            releaseLock(lockPath);
        }
    }
    // Add this helper method for single user write
    public void writeLocalUsers(User user) {
        try {
            // Get current local users
            List<User> existingUsers = readLocalUsers();

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

            // Write the updated list
            writeLocalUsers(existingUsers);
            LoggerUtil.info(this.getClass(), String.format("Updated local user: %s", user.getUsername()));
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error updating local user %s", user.getUsername()), e);
        }
    }

    // Single read method for holiday entries
    public List<PaidHolidayEntryDTO> readHolidayEntries() {
        Path networkPath = pathConfig.getNetworkHolidayPath();
        Path localCachePath = pathConfig.getHolidayCachePath();

        try {
            // Try network first
            if (pathConfig.isNetworkAvailable()) {
                List<PaidHolidayEntryDTO> networkEntries = readNetwork(networkPath, new TypeReference<>() {

                });
                if (networkEntries != null) {
                    // Update cache with network data
                    writeLocal(localCachePath, networkEntries);
                    return networkEntries;
                }
            }

            // Fallback to cache if network unavailable or read failed
            List<PaidHolidayEntryDTO> cacheEntries = readLocal(localCachePath, new TypeReference<>() {
            });
            return cacheEntries != null ? cacheEntries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading holiday entries: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }
    // Single write method for holiday entries
    public void writeHolidayEntries(List<PaidHolidayEntryDTO> entries) {
        Path networkPath = pathConfig.getNetworkHolidayPath();
        Path localCachePath = pathConfig.getHolidayCachePath();
        Path lockPath = pathConfig.getHolidayLockPath();

        try {
            acquireLock(lockPath);
            // Always update cache first
            writeLocal(localCachePath, entries);
            // Then try network if available
            if (pathConfig.isNetworkAvailable()) {
                writeNetwork(networkPath, entries);
                LoggerUtil.info(this.getClass(), "Successfully wrote holiday entries to network");
            } else {
                LoggerUtil.warn(this.getClass(), "Network unavailable, holiday entries stored in cache only");
            }

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing holiday entries: %s", e.getMessage()), e);
        } finally {
            releaseLock(lockPath);
        }
    }

    public List<WorkTimeTable> readNetworkUserWorktime(String username, int year, int month) {
        try {
            Path networkPath = pathConfig.getNetworkWorktimePath(username, year, month);
            List<WorkTimeTable> entries = readNetwork(networkPath, new TypeReference<>() {
            });
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading network worktime for user %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
    // Worktime file operations - Bidirectional sync - UserTimeOffService,
    public List<WorkTimeTable> readUserWorktime(String username, int year, int month) {
        try {
            // Get current user
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

            // If user is accessing their own data (including team leaders), use local path
            if (currentUsername.equals(username)) {
                LoggerUtil.info(this.getClass(), String.format("Reading local worktime for user %s", username));
                Path localPath = pathConfig.getLocalWorktimePath(username, year, month);
                List<WorkTimeTable> entries = readLocal(localPath, new TypeReference<>() {
                });
                return entries != null ? entries : new ArrayList<>();
            }

            // If accessing other user's data, use network path
            if (pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), String.format("Reading network worktime for user %s by %s", username, currentUsername));
                return readNetworkUserWorktime(username, year, month);
            }
            throw new RuntimeException("Network access required but not available");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading worktime for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }
    public List<WorkTimeTable> readUserWorktime(String username, int year, int month, String operatingUsername) {
        try {
            // Verify username matches the operating user
            if (username.equals(operatingUsername)) {
                Path localPath = pathConfig.getLocalWorktimePath(username, year, month);
                return readLocal(localPath, new TypeReference<>() {
                });
            } else {
                throw new SecurityException("Username mismatch with operating user");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading worktime for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }
    public void writeUserWorktime(String username, List<WorkTimeTable> entries, int year, int month) {
        // Get current user
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Only validate write access if user is writing their own data
        if (currentUsername.equals(username)) {
            securityRules.validateFileAccess(username, true);
        }

        // First write to local file
        Path localPath = pathConfig.getLocalWorktimePath(username, year, month);
        writeLocal(localPath, entries);

        if (pathConfig.isNetworkAvailable()) {
            Path networkPath = pathConfig.getNetworkWorktimePath(username, year, month);
            fileSyncService.syncToNetwork(localPath, networkPath);
        }
    }
    public void writeUserWorktime(String username, List<WorkTimeTable> entries, int year, int month, String operatingUsername) {
        try {
            // Verify the username matches the session file
            if (username.equals(operatingUsername)) {
                // Write to local file
                Path localPath = pathConfig.getLocalWorktimePath(username, year, month);
                writeLocal(localPath, entries);

                if (pathConfig.isNetworkAvailable()) {
                    Path networkPath = pathConfig.getNetworkWorktimePath(username, year, month);
                    fileSyncService.syncToNetwork(localPath, networkPath);
                }
            } else {
                throw new SecurityException("Username mismatch with session file");
            }
        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing worktime for user %s", username), e);
        }
    }

    // Register file operations - Bidirectional sync UserRegisterService
    public void writeUserRegister(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        securityRules.validateFileAccess(username, true);
        Path localPath = pathConfig.getLocalRegisterPath(username, userId, year, month);
        writeLocal(localPath, entries);

        if (pathConfig.isNetworkAvailable()) {
            Path networkPath = pathConfig.getNetworkRegisterPath(username, userId, year, month);
            fileSyncService.syncToNetwork(localPath, networkPath);
        }
    }
    public List<RegisterEntry> readUserRegister(String username, Integer userId, int year, int month) {
        try {
            // Get current user from security context
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

            // If accessing own data, use local path
            if (currentUsername.equals(username)) {
                LoggerUtil.info(this.getClass(), String.format("Reading local register for user %s", username));
                Path localPath = pathConfig.getLocalRegisterPath(username, userId, year, month);
                List<RegisterEntry> entries = readLocal(localPath, new TypeReference<>() {
                });
                return entries != null ? entries : new ArrayList<>();
            }

            // If accessing other user's data, try network path (removing security validation)
            if (pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), String.format("Reading network register for user %s by %s", username, currentUsername));
                Path networkPath = pathConfig.getNetworkRegisterPath(username, userId, year, month);
                List<RegisterEntry> entries = readNetwork(networkPath, new TypeReference<>() {
                });
                return entries != null ? entries : new ArrayList<>();
            }

            throw new RuntimeException("Network access required but not available");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading register for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }
    // Admin worktime operations - Bidirectional sync - for WorkTimeConsolidationService
    public void writeAdminWorktime(List<WorkTimeTable> entries, int year, int month) {
        Path localPath = pathConfig.getLocalAdminWorktimePath(year, month);
        writeLocal(localPath, entries);

        if (pathConfig.isNetworkAvailable()) {
            Path networkPath = pathConfig.getNetworkAdminWorktimePath(year, month);
            fileSyncService.syncToNetwork(localPath, networkPath);
        }
    }
    public List<WorkTimeTable> readLocalAdminWorktime(int year, int month) {
        Path localPath = pathConfig.getLocalAdminWorktimePath(year, month);
        try {
            if (!Files.exists(localPath)) {
                LoggerUtil.debug(this.getClass(), String.format("No local admin worktime file exists for %d/%d, returning empty list", year, month));
                return new ArrayList<>();
            }

            List<WorkTimeTable> entries = readLocal(localPath, new TypeReference<>() {
            });
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading local admin worktime for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
    // Admin worktime operations - Network only
    public List<WorkTimeTable> readNetworkAdminWorktime(int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format("Network not available for admin worktime %d/%d", year, month));
                return new ArrayList<>();
            }

            Path networkPath = pathConfig.getNetworkAdminWorktimePath(year, month);
            if (!Files.exists(networkPath)) {
                LoggerUtil.debug(this.getClass(), String.format("No network admin worktime file exists for %d/%d", year, month));
                return new ArrayList<>();
            }

            List<WorkTimeTable> entries = readNetwork(networkPath, new TypeReference<>() {
            });
            if (entries == null) {
                LoggerUtil.debug(this.getClass(), String.format("Empty or invalid network admin worktime for %d/%d", year, month));
                return new ArrayList<>();
            }
            return entries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading network admin worktime for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
    // Admin bonus operations - Local only for AdminBonusService
    public void writeAdminBonus(List<BonusEntry> entries, int year, int month) {
        Path localPath = pathConfig.getLocalBonusPath(year, month);
        writeLocal(localPath, entries);
    }
    public List<BonusEntry> readAdminBonus(int year, int month) {
        try {
            Path localPath = pathConfig.getLocalBonusPath(year, month);
            List<BonusEntry> entries = readLocal(localPath, new TypeReference<>() {
            });
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading admin bonus data for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Specific methods - Bidirectional sync for AdminRegisterService
    public List<RegisterEntry> readNetworkUserRegister(String username, Integer userId, int year, int month) {
        Path networkPath = pathConfig.getNetworkRegisterPath(username, userId, year, month);
        return readNetwork(networkPath, new TypeReference<>() {
        });
    }
    public List<RegisterEntry> findRegisterFiles(String username, Integer userId) {
        List<RegisterEntry> allEntries = new ArrayList<>();

        try {
            // Determine the years and months to search
            int currentYear = LocalDate.now().getYear();
            int currentMonth = LocalDate.now().getMonthValue();

            // Search previous 24 months (2 years)
            for (int year = currentYear; year >= currentYear - 1; year--) {
                for (int month = (year == currentYear ? currentMonth : 12); month >= 1; month--) {
                    // Try network path first if available
                    if (pathConfig.isNetworkAvailable()) {
                        try {
                            Path networkPath = pathConfig.getNetworkRegisterPath(username, userId, year, month);
                            List<RegisterEntry> networkEntries = readNetwork(networkPath, new TypeReference<>() {});

                            if (networkEntries != null) {
                                allEntries.addAll(networkEntries);
                            }
                        } catch (Exception e) {
                            LoggerUtil.warn(this.getClass(),
                                    String.format("Error reading network register for %s (%d/%d): %s",
                                            username, year, month, e.getMessage()));
                        }
                    }

                    // Fallback to local path
                    try {
                        Path localPath = pathConfig.getLocalRegisterPath(username, userId, year, month);
                        List<RegisterEntry> localEntries = readLocal(localPath, new TypeReference<>() {});

                        if (localEntries != null) {
                            allEntries.addAll(localEntries);
                        }
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(),
                                String.format("Error reading local register for %s (%d/%d): %s",
                                        username, year, month, e.getMessage()));
                    }
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error searching register files for %s: %s", username, e.getMessage()));
        }

        return allEntries;
    }

    public void writeLocalAdminRegister(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        Path localAdminPath = pathConfig.getLocalAdminRegisterPath(username, userId, year, month);
        writeLocal(localAdminPath, entries);
    }

    public List<RegisterEntry> readLocalAdminRegister(String username, Integer userId, int year, int month) {
        Path localAdminPath = pathConfig.getLocalAdminRegisterPath(username, userId, year, month);
        List<RegisterEntry> entries = readLocal(localAdminPath, new TypeReference<>() {
        });
        return entries != null ? entries : new ArrayList<>(); // Return empty list instead of null
    }

    public void syncAdminRegisterToNetwork(String username, Integer userId, int year, int month) {
        if (!pathConfig.isNetworkAvailable()) {
            return;
        }

        Path localAdminPath = pathConfig.getLocalAdminRegisterPath(username, userId, year, month);
        Path networkAdminPath = pathConfig.getNetworkAdminRegisterPath(username, userId, year, month);
        fileSyncService.syncToNetwork(localAdminPath, networkAdminPath);
    }

    // Add new methods for team members operations
    public List<TeamMemberDTO> readTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            Path teamPath = pathConfig.getTeamJsonPath(teamLeadUsername, year, month);
            List<TeamMemberDTO> members = readLocal(teamPath, new TypeReference<>() {});
            return members != null ? members : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading team members for %s (%d/%d): %s", teamLeadUsername, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
    public void writeTeamMembers(List<TeamMemberDTO> teamMemberDTOS, String teamLeadUsername, int year, int month) {
        try {
            Path teamPath = pathConfig.getTeamJsonPath(teamLeadUsername, year, month);
            writeLocal(teamPath, teamMemberDTOS);
            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d team members for %s (%d/%d)", teamMemberDTOS.size(), teamLeadUsername, year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error writing team members for %s (%d/%d): %s", teamLeadUsername, year, month, e.getMessage()));
            throw new RuntimeException("Failed to write team members", e);
        }
    }

    // Utility methods
    private void validateSession(WorkUsersSessionsStates session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        if (session.getUsername() == null || session.getUserId() == null) {
            throw new IllegalArgumentException("Session must have both username and userId");
        }
    }

    private ReentrantReadWriteLock getFileLock(Path path) {
        return fileLocks.computeIfAbsent(path, k -> new ReentrantReadWriteLock());
    }

    // Generic lock handling methods
    private void acquireLock(Path lockFile){
        while (Files.exists(lockFile)) {
            LoggerUtil.info(this.getClass(), String.format("Waiting for lock to be released: %s", lockFile.getFileName()));

        }

        try {
            Files.createFile(lockFile);
        } catch (IOException e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error creating lock file %s", lockFile.getFileName()), e);
        }
    }

    private void releaseLock(Path lockFile) {
        try {
            Files.deleteIfExists(lockFile);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), String.format("Error removing lock file %s: %s", lockFile.getFileName(), e.getMessage()));
        }
    }

    // Reads a file without creating backups or locks (read-only operation)
    private <T> T readFileReadOnly(Path path, TypeReference<T> typeRef) {
        try {
            // Check if file exists and has content
            if (Files.exists(path) && Files.size(path) >= 3) {
                byte[] content = Files.readAllBytes(path);
                return objectMapper.readValue(content, typeRef);
            }

            // Try backup if main file doesn't exist or is corrupted
            Path backupPath = path.resolveSibling(path.getFileName() + WorkCode.BACKUP_EXTENSION);
            if (Files.exists(backupPath) && Files.size(backupPath) >= 3) {
                byte[] content = Files.readAllBytes(backupPath);
                return objectMapper.readValue(content, typeRef);
            }

            return null;
        } catch (IOException e) {
            LoggerUtil.debug(this.getClass(), "Error in read-only file access: " + e.getMessage());
            return null;
        }
    }
    // Read user worktime data in read-only mode (no locks, no backups)
    public List<WorkTimeTable> readWorktimeReadOnly(String username, int year, int month) {
        try {
            // First try network if available
            if (pathConfig.isNetworkAvailable()) {
                Path networkPath = pathConfig.getNetworkWorktimePath(username, year, month);
                List<WorkTimeTable> entries = readFileReadOnly(networkPath, new TypeReference<>() {});
                if (entries != null) {
                    return entries;
                }
            }

            // Fall back to local file
            Path localPath = pathConfig.getLocalWorktimePath(username, year, month);
            List<WorkTimeTable> entries = readFileReadOnly(localPath, new TypeReference<>() {});
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Read-only worktime access failed for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
    // Read register entries in read-only mode (no locks, no backups)
    public List<RegisterEntry> readRegisterReadOnly(String username, Integer userId, int year, int month) {
        try {
            // Try network first if available
            if (pathConfig.isNetworkAvailable()) {
                Path networkPath = pathConfig.getNetworkRegisterPath(username, userId, year, month);
                List<RegisterEntry> entries = readFileReadOnly(networkPath, new TypeReference<>() {});
                if (entries != null) {
                    return entries;
                }
            }

            // Fall back to local file
            Path localPath = pathConfig.getLocalRegisterPath(username, userId, year, month);
            List<RegisterEntry> entries = readFileReadOnly(localPath, new TypeReference<>() {});
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Read-only register access failed for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
    // Read time off entries in read-only mode
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
                if (monthEntries != null) {
                    // Filter for time off entries only
                    List<WorkTimeTable> timeOffEntries = monthEntries.stream().filter(entry -> entry.getTimeOffType() != null).toList();
                    allEntries.addAll(timeOffEntries);
                }
            } catch (Exception e) {
                LoggerUtil.debug(this.getClass(), String.format("Read-only time-off access failed for %s - %d/%d: %s", username, year, month, e.getMessage()));
            }
        }

        return allEntries;
    }

    /**
     * Read a time off tracker file for a user for a specific year
     */
    public TimeOffTracker readTimeOffTracker(String username, Integer userId, int year) {
        try {
            Path localPath = pathConfig.getLocalTimeOffTrackerPath(username, userId, year);

            // First try to read from local file
            if (Files.exists(localPath)) {
                try {
                    return readLocal(localPath, new TypeReference<>() {});
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            String.format("Error reading local tracker file for %s (%d): %s",
                                    username, year, e.getMessage()));
                }
            }

            // If network is available, try to read from network
            if (pathConfig.isNetworkAvailable()) {
                Path networkPath = pathConfig.getNetworkTimeOffTrackerPath(username, userId, year);
                if (Files.exists(networkPath)) {
                    try {
                        TimeOffTracker tracker = readNetwork(networkPath, new TypeReference<>() {});
                        if (tracker != null) {
                            // Save to local for future use
                            writeTimeOffTracker(tracker, year);
                            return tracker;
                        }
                    } catch (Exception e) {
                        LoggerUtil.error(this.getClass(),
                                String.format("Error reading network tracker file for %s (%d): %s",
                                        username, year, e.getMessage()));
                    }
                }
            }

            // Return null if no file exists
            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading time off tracker for %s (%d): %s",
                            username, year, e.getMessage()));
            return null;
        }
    }

    /**
     * Write a time off tracker file for a user for a specific year
     */
    public void writeTimeOffTracker(TimeOffTracker tracker, int year) {
        if (tracker == null || tracker.getUsername() == null) {
            LoggerUtil.error(this.getClass(), "Cannot save null tracker or tracker without username");
            return;
        }

        try {
            // First save locally
            Path localPath = pathConfig.getLocalTimeOffTrackerPath(tracker.getUsername(), tracker.getUserId(), year);
            Files.createDirectories(localPath.getParent());
            writeLocal(localPath, tracker);

            // Then save to network if available
            if (pathConfig.isNetworkAvailable()) {
                Path networkPath = pathConfig.getNetworkTimeOffTrackerPath(tracker.getUsername(), tracker.getUserId(), year);
                Files.createDirectories(networkPath.getParent());
                fileSyncService.syncToNetwork(localPath, networkPath);
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Saved time off tracker for %s (%d) with %d requests",
                            tracker.getUsername(), year, tracker.getRequests().size()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error saving time off tracker for %s (%d): %s",
                            tracker.getUsername(), year, e.getMessage()));
        }
    }

    /**
     * Read a time off tracker in read-only mode
     */
    public TimeOffTracker readTimeOffTrackerReadOnly(String username, Integer userId, int year) {
        try {
            // Try network first if available
            if (pathConfig.isNetworkAvailable()) {
                Path networkPath = pathConfig.getNetworkTimeOffTrackerPath(username, userId, year);
                TimeOffTracker tracker = readFileReadOnly(networkPath, new TypeReference<TimeOffTracker>() {});
                if (tracker != null) {
                    return tracker;
                }
            }

            // Fall back to local file
            Path localPath = pathConfig.getLocalTimeOffTrackerPath(username, userId, year);
            return readFileReadOnly(localPath, new TypeReference<TimeOffTracker>() {});
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(),
                    String.format("Read-only time-off tracker access failed for %s (%d): %s",
                            username, year, e.getMessage()));
            return null;
        }
    }

    // Writes a user status record to its dedicated status file.
    public void writeUserStatus(String username, Integer userId, UserStatusRecord statusRecord) {
        try {
            // Get the path to the user's status file
            Path statusFilePath = pathConfig.getLocalStatusFilePath(username, userId);

            // Make sure parent directory exists
            Files.createDirectories(statusFilePath.getParent());

            // Write to temp file first (atomic write)
            Path tempFile = statusFilePath.resolveSibling(statusFilePath.getFileName() + ".tmp");
            byte[] content = objectMapper.writeValueAsBytes(statusRecord);
            Files.write(tempFile, content);

            // Atomically replace the actual file
            Files.move(tempFile, statusFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            // If network is available, sync to network
            if (pathConfig.isNetworkAvailable()) {
                Path networkPath = pathConfig.getNetworkStatusFilePath(username, userId);
                fileSyncService.syncToNetwork(statusFilePath, networkPath);
            }

            LoggerUtil.debug(this.getClass(), String.format("Successfully wrote status file for %s", username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error writing status file for %s: %s", username, e.getMessage()), e);
        }
    }

    // Reads all user status records from the status directory.
    public Map<String, UserStatusRecord> readAllUserStatuses() {
        Map<String, UserStatusRecord> statusMap = new ConcurrentHashMap<>();
        Path statusDir = pathConfig.getLocalStatusDbDirectory();

        try {
            if (!Files.exists(statusDir)) {
                Files.createDirectories(statusDir);
            }

            // Read all status files from the local directory
            try (Stream<Path> files = Files.list(statusDir)) {
                List<Path> statusFiles = files.filter(path -> path.toString().endsWith(".json") && !path.toString().endsWith(".tmp")).toList();

                for (Path file : statusFiles) {
                    try {
                        if (Files.size(file) > 0) {
                            UserStatusRecord record = objectMapper.readValue(Files.readAllBytes(file), UserStatusRecord.class);
                            if (record != null && record.getUsername() != null) {
                                statusMap.put(record.getUsername(), record);
                            }
                        }
                    } catch (Exception e) {
                        LoggerUtil.debug(this.getClass(), String.format("Skipping invalid status file %s: %s", file.getFileName(), e.getMessage()));
                    }
                }
            }

            // If network is available, also check for network status files that might be newer
            if (pathConfig.isNetworkAvailable()) {
                Path networkStatusDir = pathConfig.getNetworkStatusDbDirectory();
                if (Files.exists(networkStatusDir)) {
                    try (Stream<Path> files = Files.list(networkStatusDir)) {
                        List<Path> networkStatusFiles = files.filter(path -> path.toString().endsWith(".json") && !path.toString().endsWith(".tmp")).toList();

                        for (Path file : networkStatusFiles) {
                            try {
                                if (Files.size(file) > 0) {
                                    UserStatusRecord record = objectMapper.readValue(Files.readAllBytes(file), UserStatusRecord.class);
                                    if (record != null && record.getUsername() != null) {
                                        // Check if we already have a record for this user
                                        UserStatusRecord localRecord = statusMap.get(record.getUsername());

                                        // If no local record or network record is newer, use network record
                                        if (localRecord == null || (record.getLastUpdated() != null && localRecord.getLastUpdated() != null && record.getLastUpdated().isAfter(localRecord.getLastUpdated()))) {
                                            statusMap.put(record.getUsername(), record);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                LoggerUtil.debug(this.getClass(), String.format("Skipping invalid network status file %s: %s", file.getFileName(), e.getMessage()));
                            }
                        }
                    }
                }
            }

            return statusMap;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading status records: " + e.getMessage(), e);
            return statusMap;
        }
    }

    public void writeNotificationTrackingFile(String username, String notificationType, LocalDateTime timestamp) {
        try {
            Path notificationsDir = pathConfig.getNotificationsPath();
            Files.createDirectories(notificationsDir);

            Path trackingFile = pathConfig.getNotificationTrackingFilePath(username, notificationType);

            // Write timestamp to file
            Files.write(trackingFile, timestamp.toString().getBytes());

            LoggerUtil.info(this.getClass(), String.format("Created notification tracking file for %s: %s", username, trackingFile));
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error writing notification tracking file for user %s with type %s: %s",
                            username, notificationType, e.getMessage()), e);
        }
    }
}