package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class DataAccessService {
    private final ObjectMapper objectMapper;
    private final PathConfig pathConfig;
    private final FileObfuscationService obfuscationService;
    private final FileSyncService fileSyncService;
    private final Map<Path, ReentrantReadWriteLock> fileLocks;

    public DataAccessService(ObjectMapper objectMapper,
                             PathConfig pathConfig,
                             FileObfuscationService obfuscationService,FileSyncService fileSyncService) {
        this.objectMapper = objectMapper;
        this.pathConfig = pathConfig;
        this.fileSyncService = fileSyncService;
        this.obfuscationService = obfuscationService;
        this.fileLocks = new ConcurrentHashMap<>();
        LoggerUtil.initialize(this.getClass(), null);
    }

    public <T> T readFile(Path path, TypeReference<T> typeRef, boolean createIfMissing) {
        String filename = path.getFileName().toString();

        // Ensure the full correct path is used
        Path readPath = filename.equals(pathConfig.getUsersFilename()) ||
                filename.equals(pathConfig.getLocalUsersFilename()) ?
                path : pathConfig.resolvePathForRead(filename);

        LoggerUtil.debug(this.getClass(),
                String.format("Reading file: %s (filename: %s)", readPath, filename));

        ReentrantReadWriteLock.ReadLock readLock = getFileLock(readPath).readLock();
        readLock.lock();

        try {
            if (!Files.exists(readPath) || Files.size(readPath) <= 3) {
                LoggerUtil.debug(this.getClass(),
                        String.format("File not found or empty: %s, createIfMissing: %s",
                                readPath, createIfMissing));

                if (!createIfMissing) {
                    throw new IOException("File not found or empty: " + readPath);
                }

                // Create empty structure
                T emptyStructure = createEmptyStructure(typeRef);

                // Ensure parent directory exists
                Files.createDirectories(readPath.getParent());

                // Write empty structure
                objectMapper.writeValue(readPath.toFile(), emptyStructure);

                return emptyStructure;
            }

            LoggerUtil.debug(this.getClass(),
                    String.format("Reading existing file: %s, size: %d bytes",
                            readPath, Files.size(readPath)));

            return objectMapper.readValue(readPath.toFile(), typeRef);

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading file %s: %s", readPath, e.getMessage()));

            if (createIfMissing) {
                try {
                    T emptyStructure = createEmptyStructure(typeRef);
                    Files.createDirectories(readPath.getParent());
                    objectMapper.writeValue(readPath.toFile(), emptyStructure);
                    return emptyStructure;
                } catch (IOException ex) {
                    LoggerUtil.error(this.getClass(),
                            "Failed to create empty file: " + ex.getMessage());
                    throw new RuntimeException("Failed to create empty file", ex);
                }
            }
            throw new RuntimeException("Failed to read file: " + readPath, e);
        } finally {
            readLock.unlock();
        }
    }

    public <T> void writeFile(Path path, T data) {
        String filename = path.getFileName().toString();
        Path writePath = pathConfig.resolvePathForWrite(filename);

        ReentrantReadWriteLock.WriteLock writeLock = getFileLock(writePath).writeLock();
        writeLock.lock();

        try {
            // Ensure directory exists
            ensureDirectoryExists(writePath);

            // Write data
            objectMapper.writeValue(writePath.toFile(), data);

            // Handle network sync if required
            if (pathConfig.requiresSync(filename)) {
                syncWithNetwork(writePath, filename);
            }

            LoggerUtil.info(this.getClass(),
                    "Successfully wrote data to file: " + writePath);

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error writing file %s: %s", writePath, e.getMessage()));
            throw new RuntimeException("Failed to write file: " + writePath, e);
        } finally {
            writeLock.unlock();
        }

        LoggerUtil.info(this.getClass(),
                String.format("Writing file: filename=%s, writePath=%s, loginPath=%s",
                        filename, writePath, pathConfig.getLoginPath()));
    }

    private void syncWithNetwork(Path localPath, String filename) {
        if (!pathConfig.isLocalOnlyFile(filename) && pathConfig.isNetworkAvailable()) {
            Path networkPath = pathConfig.getNetworkPath().resolve(filename);
            fileSyncService.syncToNetwork(localPath, networkPath);
        }
    }

    private void ensureDirectoryExists(Path path)  {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private ReentrantReadWriteLock getFileLock(Path path) {
        return fileLocks.computeIfAbsent(path, k -> new ReentrantReadWriteLock());
    }

    @SuppressWarnings("unchecked")
    private <T> T createEmptyStructure(TypeReference<T> typeRef) {
        String typeName = typeRef.getType().getTypeName();

        if (typeName.contains("List")) {
            return (T) new ArrayList<>();
        }
        if (typeName.contains("Map")) {
            return (T) new HashMap<>();
        }

        try {
            return objectMapper.readValue("{}", typeRef);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create empty structure", e);
        }
    }

    // Path resolution methods using PathConfig
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

    public Path getHolidayPath() {
        return pathConfig.getHolidayListPath();
    }
    public Path getUsersPath() {
        return pathConfig.getUsersJsonPath();
    }
    public Path getLocalUsersPath() {
        return pathConfig.getLocalUsersJsonPath();
    }

    public boolean fileExists(Path path) {
        String filename = path.getFileName().toString();
        Path resolvedPath = pathConfig.resolvePathForRead(filename);

        ReentrantReadWriteLock.ReadLock readLock = getFileLock(resolvedPath).readLock();
        readLock.lock();
        try {
            return Files.exists(resolvedPath);
        } finally {
            readLock.unlock();
        }
    }

    public boolean isFileNewer(Path file1, Path file2) {
        ReentrantReadWriteLock.ReadLock readLock1 = getFileLock(file1).readLock();
        ReentrantReadWriteLock.ReadLock readLock2 = getFileLock(file2).readLock();

        readLock1.lock();
        readLock2.lock();
        try {
            return Files.getLastModifiedTime(file1)
                    .compareTo(Files.getLastModifiedTime(file2)) > 0;
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error comparing file timestamps: %s", e.getMessage()));
            return false;
        } finally {
            readLock1.unlock();
            readLock2.unlock();
        }
    }

    /**
     * Reads user data from the most appropriate location (network or local)
     * and handles all path resolution and fallback logic.
     */
    public List<User> readUserData() {
        // Try network path first if available
        if (pathConfig.isNetworkAvailable()) {
            try {
                Path networkPath = pathConfig.getUsersJsonPath();
                if (Files.exists(networkPath) && Files.size(networkPath) > 3) {
                    List<User> users = readFile(networkPath, new TypeReference<>() {}, false);
                    LoggerUtil.info(this.getClass(),
                            "Successfully read users from network path");
                    return users;
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        "Could not read network users file: " + e.getMessage());
            }
        }

        // Fallback to local path
        try {
            Path localPath = pathConfig.getLocalUsersJsonPath();
            if (Files.exists(localPath) && Files.size(localPath) > 3) {
                List<User> users = readFile(localPath, new TypeReference<>() {}, false);
                LoggerUtil.info(this.getClass(),
                        "Successfully read users from local path");
                return users;
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Could not read local users file: " + e.getMessage());
        }

        // Return empty list if no users found
        return new ArrayList<>();
    }

    /**
     * Saves user data to appropriate location(s)
     */
    public void saveUserData(List<User> users) {
        // Always save to local path
        Path localPath = pathConfig.getLocalUsersJsonPath();
        writeFile(localPath, users);
        LoggerUtil.info(this.getClass(), "Saved users to local path");

        // If network available, save there too
        if (pathConfig.isNetworkAvailable()) {
            try {
                Path networkPath = pathConfig.getUsersJsonPath();
                writeFile(networkPath, users);
                LoggerUtil.info(this.getClass(), "Saved users to network path");
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        "Could not save to network path: " + e.getMessage());
            }
        }
    }

    /**
     * Finds a user by username
     */
    public Optional<User> findUserByUsername(String username) {
        return readUserData().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }

    /**
     * Finds a user by ID
     */
    public Optional<User> findUserById(Integer userId) {
        return readUserData().stream()
                .filter(user -> user.getUserId().equals(userId))
                .findFirst();
    }

}