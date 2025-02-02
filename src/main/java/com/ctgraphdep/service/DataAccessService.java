package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.security.CustomUserDetails;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class DataAccessService {
    private final ObjectMapper objectMapper;
    private final PathConfig pathConfig;
    private final FileObfuscationService obfuscationService;
    private final FileSyncService fileSyncService;
    private final Map<Path, ReentrantReadWriteLock> fileLocks;

    public DataAccessService(ObjectMapper objectMapper,
                             PathConfig pathConfig,
                             FileObfuscationService obfuscationService,
                             FileSyncService fileSyncService) {
        this.objectMapper = objectMapper;
        this.pathConfig = pathConfig;
        this.fileSyncService = fileSyncService;
        this.obfuscationService = obfuscationService;
        this.fileLocks = new ConcurrentHashMap<>();
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Core read operation that tries network first, then local
    private <T> T readOperation(Path networkPath, Path localPath, TypeReference<T> typeRef, boolean createIfMissing) {
        // Always check local path first
        try {
            if (Files.exists(localPath) && Files.size(localPath) > 3) {
                return deserializeFromPath(localPath, typeRef);
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    "Could not read from local path: " + e.getMessage());
        }

        // Only try network if local doesn't exist
        if (pathConfig.isNetworkAvailable()) {
            try {
                if (Files.exists(networkPath) && Files.size(networkPath) > 3) {
                    T data = deserializeFromPath(networkPath, typeRef);
                    // Copy network data to local for future use
                    ensureDirectoryExists(localPath.getParent());
                    serializeToPath(localPath, data);
                    return data;
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        "Could not read from network path: " + e.getMessage());
            }
        }

        // If nothing exists and createIfMissing is true
        if (createIfMissing) {
            try {
                return createAndSaveEmptyStructure(localPath, typeRef);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        throw new RuntimeException("File not found and creation not requested");
    }
    // Core write operation that always writes to local first, then syncs
    private <T> void writeOperation(Path localPath, T data, boolean requiresSync) {
        try {
            // Force local path to be under installation directory
            Path forcedLocalPath = ensureInstallationPath(localPath);

            // Always ensure we're writing to local directory
            ensureDirectoryExists(forcedLocalPath.getParent());
            serializeToPath(forcedLocalPath, data);
            LoggerUtil.info(this.getClass(), "Successfully wrote to local path: " + forcedLocalPath);

            // Only sync to network if required and available
            if (requiresSync && pathConfig.isNetworkAvailable()) {
                // For network path, maintain the original structure under network root
                Path networkPath = pathConfig.getNetworkPath().resolve(
                        forcedLocalPath.subpath(pathConfig.getLocalPath().getNameCount(), forcedLocalPath.getNameCount())
                );
                syncToNetwork(forcedLocalPath, networkPath);
            }
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Failed to write file: " + e.getMessage());
            throw new RuntimeException("Failed to write file", e);
        }
    }

    private Path ensureInstallationPath(Path path) {
        // Get the relative path from either network or local root
        Path relativePath;
        if (path.startsWith(pathConfig.getNetworkPath())) {
            relativePath = pathConfig.getNetworkPath().relativize(path);
        } else if (path.startsWith(pathConfig.getLocalPath())) {
            relativePath = pathConfig.getLocalPath().relativize(path);
        } else {
            // If path doesn't start with either, assume it's already relative
            relativePath = path;
        }

        // Force the path to be under installation directory
        return pathConfig.getLocalPath().resolve(relativePath);
    }

    // File serialization/deserialization
    private <T> T deserializeFromPath(Path path, TypeReference<T> typeRef) throws IOException {
        byte[] content = Files.readAllBytes(path);
//        if (obfuscationService.shouldObfuscate(path.getFileName().toString())) {
//            content = obfuscationService.deobfuscate(content);
//        }
        return objectMapper.readValue(content, typeRef);
    }

    private <T> void serializeToPath(Path path, T data) throws IOException {
        byte[] content = objectMapper.writeValueAsBytes(data);
//        if (obfuscationService.shouldObfuscate(path.getFileName().toString())) {
//            content = obfuscationService.obfuscate(content);
//        }
        Files.write(path, content);
    }

    private void syncToNetwork(Path localPath, Path networkPath) {
        try {
            if (!Files.exists(localPath)) {
                LoggerUtil.warn(this.getClass(), "Cannot sync - local file does not exist: " + localPath);
                return;
            }

            // Use FileSyncService instead of direct Files.copy
            fileSyncService.syncToNetwork(localPath, networkPath);
            LoggerUtil.info(this.getClass(),
                    String.format("Initiated sync from local to network:\nFrom: %s\nTo: %s",
                            localPath, networkPath));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Failed to sync to network: " + e.getMessage());
        }
    }

    // Utility methods
    @SuppressWarnings("unchecked")
    private <T> T createEmptyStructure(TypeReference<T> typeRef) {
        String typeName = typeRef.getType().getTypeName();
        if (typeName.contains("List")) return (T) new ArrayList<>();
        if (typeName.contains("Map")) return (T) new HashMap<>();
        try {
            return objectMapper.readValue("{}", typeRef);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create empty structure", e);
        }
    }

    private <T> T createAndSaveEmptyStructure(Path path, TypeReference<T> typeRef) throws IOException {
        T emptyStructure = createEmptyStructure(typeRef);
        Files.createDirectories(path.getParent());
        objectMapper.writeValue(path.toFile(), emptyStructure);
        return emptyStructure;
    }

    private void ensureDirectoryExists(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private ReentrantReadWriteLock getFileLock(Path path) {
        return fileLocks.computeIfAbsent(path, k -> new ReentrantReadWriteLock());
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) auth.getPrincipal()).getUser();
        }
        throw new SecurityException("No authenticated user found");
    }

    // Public API methods
    public <T> T readFile(Path path, TypeReference<T> typeRef, boolean createIfMissing) {
        String filename = path.getFileName().toString();
        boolean isUserFile = filename.equals(pathConfig.getUsersFilename()) ||
                filename.equals(pathConfig.getLocalUsersFilename());

        Path networkPath = isUserFile ? path : pathConfig.resolvePathForRead(filename);
        Path localPath = isUserFile ? path : pathConfig.resolvePathForWrite(filename);

        return readOperation(networkPath, localPath, typeRef, createIfMissing);
    }

    public <T> void writeFile(Path path, T data) {
        String filename = path.getFileName().toString();
        if (filename.equals(pathConfig.getLocalUsersFilename())) {
            writeOperation(path, data, false); // Never sync local users file
            return;
        }
        User currentUser = getCurrentUser();
        Path localPath = pathConfig.resolvePathForWrite(filename);

        boolean shouldSync = filename.startsWith("worktime_") ||
                pathConfig.shouldSync(filename, currentUser.isAdmin(), currentUser.getUsername());

        writeOperation(localPath, data, shouldSync);
    }

    // Session-specific operations
    public WorkUsersSessionsStates readLocalSessionFile(String username, Integer userId) {
        Path localPath = pathConfig.getLocalSessionFilePath(username, userId);
        Path networkPath = pathConfig.getSessionFilePath(username, userId);
        TypeReference<WorkUsersSessionsStates> sessionType = new TypeReference<>() {};

        try {
            // Use readOperation for network-first, local-fallback behavior
            WorkUsersSessionsStates session = readOperation(networkPath, localPath, sessionType, false);

            // Verify session ownership
            if (session != null &&
                    username.equals(session.getUsername()) &&
                    userId.equals(session.getUserId())) {
                return session;
            }

            LoggerUtil.warn(this.getClass(), "Session ownership verification failed");
            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading session file for user %s: %s", username, e.getMessage()));
            return null;
        }
    }

    public void writeLocalSessionFile(WorkUsersSessionsStates session) {
        validateSession(session);

        try {
            Path localPath = pathConfig.getLocalSessionFilePath(session.getUsername(), session.getUserId());

            // Use writeOperation to handle local write and network sync
            writeOperation(localPath, session, true);

            LoggerUtil.info(this.getClass(),
                    String.format("Successfully wrote session file for user %s", session.getUsername()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to write session file for user %s: %s",
                            session.getUsername(), e.getMessage()));
            throw new RuntimeException("Failed to write session file", e);
        }
    }

    private void validateSession(WorkUsersSessionsStates session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        if (session.getUsername() == null || session.getUserId() == null) {
            throw new IllegalArgumentException("Session must have both username and userId");
        }
    }

    // WorkTime operations
    public void writeLocalWorkTimeEntry(String username, List<WorkTimeTable> entries, int year, int month) {
        // Group entries by date and get only the most recent entry for each date
        Map<LocalDate, WorkTimeTable> uniqueEntries = entries.stream()
                .collect(Collectors.groupingBy(
                        WorkTimeTable::getWorkDate,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .max(Comparator.comparing(WorkTimeTable::getDayStartTime)
                                                .thenComparing(e -> {
                                                    if (e.getAdminSync() == SyncStatus.USER_IN_PROCESS) return 0;
                                                    if (e.getAdminSync() == SyncStatus.USER_INPUT) return 1;
                                                    return -1;
                                                }))
                                        .orElse(null)
                        )
                ));

        List<WorkTimeTable> dedupedEntries = new ArrayList<>(uniqueEntries.values());
        dedupedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));

        // Get paths but ensure local path is under installation directory
        Path basePath = pathConfig.getUserWorktimeFilePath(username, year, month);
        Path localPath = ensureInstallationPath(basePath);
        Path networkPath = pathConfig.getUserWorktimeFilePath(username, year, month);

        try {
            // Write to local
            writeOperation(localPath, dedupedEntries, false);
            LoggerUtil.info(this.getClass(),
                    String.format("Wrote %d unique entries to local storage for %s",
                            dedupedEntries.size(), username));

            // Sync to network if available
            if (pathConfig.isNetworkAvailable()) {
                fileSyncService.syncToNetwork(localPath, networkPath);
                LoggerUtil.info(this.getClass(), "Synced worktime entries to network");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Failed to write worktime entries: %s", e.getMessage()));
            throw new RuntimeException("Failed to write worktime entries", e);
        }
    }

    // File existence and comparison operations
    public boolean fileExists(Path path) {
        String filename = path.getFileName().toString();
        Path resolvedPath = pathConfig.resolvePathForRead(filename);

        ReentrantReadWriteLock.ReadLock readLock = getFileLock(resolvedPath).readLock();
        readLock.lock();
        try {
            return Files.exists(resolvedPath);
        } catch (Exception e) {
            return false;
        } finally {
            readLock.unlock();
        }
    }

    // User data operations
    public List<User> readUserData() {
        TypeReference<List<User>> userListType = new TypeReference<>() {};
        Path networkPath = pathConfig.getUsersJsonPath();
        Path localPath = pathConfig.getLocalUsersJsonPath();

        try {
            // Use readOperation for network-first, local-fallback behavior
            return readOperation(networkPath, localPath, userListType, false);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Failed to read user data, returning empty list: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public void saveUserData(List<User> users) {
        Path localPath = pathConfig.getLocalUsersJsonPath();
        Path networkPath = pathConfig.getUsersJsonPath();

        try {
            // Always write to local path first
            writeOperation(localPath, users, false);
            LoggerUtil.info(this.getClass(), "Saved users to local path");

            // Write to network if available using FileSyncService
            if (pathConfig.isNetworkAvailable()) {
                try {
                    fileSyncService.syncToNetwork(localPath, networkPath);
                    LoggerUtil.info(this.getClass(), "Synced users to network path");
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            "Could not sync to network path: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to save user data: " + e.getMessage());
            throw new RuntimeException("Failed to save user data", e);
        }
    }

    public Optional<User> findUserByUsername(String username) {
        return readUserData().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }

    public Optional<User> findUserById(Integer userId) {
        return readUserData().stream()
                .filter(user -> user.getUserId().equals(userId))
                .findFirst();
    }

    // Path resolution methods
    public Path getAdminWorktimePath(Integer year, Integer month) {
        return pathConfig.getAdminWorktimePath(year, month);
    }

    public Path getAdminRegisterPath(String username, Integer userId, Integer year, Integer month) {
        return pathConfig.getAdminRegisterPath(username, userId, year, month);
    }

    public Path getAdminBonusPath(Integer year, Integer month) {
        return pathConfig.getAdminBonusPath(year, month);
    }

    public Path getUserWorktimePath(String username, Integer year, Integer month) {
        return pathConfig.getUserWorktimeFilePath(username, year, month);
    }

    public Path getUserRegisterPath(String username, Integer userId, Integer year, Integer month) {
        return pathConfig.getUserRegisterPath(username, userId, year, month);
    }

    public Path getSessionPath(String username, Integer userId) {
        return pathConfig.getSessionFilePath(username, userId);
    }

    public Path getLocalSessionPath(String username, Integer userId) {
        return pathConfig.getLocalSessionFilePath(username, userId);
    }

    public Path getHolidayPath() {
        return pathConfig.getHolidayListPath();
    }

    public Path getUsersPath() {
        return pathConfig.getUsersJsonPath();
    }

    public Path getLocalUsersPath() {
        return pathConfig.getLocalUsersJsonPath();
    }
}