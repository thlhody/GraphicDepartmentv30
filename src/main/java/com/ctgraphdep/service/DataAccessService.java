package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.model.BonusEntry;
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
    private final Map<Path, ReentrantReadWriteLock> fileLocks;

    public DataAccessService(ObjectMapper objectMapper, PathConfig pathConfig, FileObfuscationService obfuscationService) {
        this.objectMapper = objectMapper;
        this.pathConfig = pathConfig;
        this.obfuscationService = obfuscationService;
        this.fileLocks = new ConcurrentHashMap<>();
        LoggerUtil.initialize(this.getClass(), "Initializing Data Access Service");
    }
//
//    public <T> T readFile(Path path, TypeReference<T> typeRef, boolean createIfMissing) {
//        ReentrantReadWriteLock.ReadLock readLock = getFileLock(path).readLock();
//        readLock.lock();
//
//        try {
//            if (!Files.exists(path)) {
//                LoggerUtil.info(this.getClass(), "File not found: " + path);
//                if (!createIfMissing) {
//                    throw new IOException("File not found: " + path);
//                }
//                return createEmptyStructure(typeRef);
//            }
//
//            byte[] fileData = Files.readAllBytes(path);
//
//            // Deobfuscate if necessary
//            if (obfuscationService.shouldObfuscate(path.getFileName().toString())) {
//                fileData = obfuscationService.deobfuscate(fileData);
//            }
//
//            return objectMapper.readValue(fileData, typeRef);
//
//        } catch (IOException e) {
//            LoggerUtil.error(this.getClass(),
//                    String.format("Error reading file %s: %s", path, e.getMessage()));
//            if (createIfMissing) {
//                return createEmptyStructure(typeRef);
//            }
//            throw new RuntimeException("Failed to read file: " + path, e);
//        } finally {
//            readLock.unlock();
//        }
//    }
//
//    public <T> void writeFile(Path path, T data) {
//        ReentrantReadWriteLock.WriteLock writeLock = getFileLock(path).writeLock();
//        writeLock.lock();
//
//        try {
//            ensureDirectoryExists(path);
//            byte[] jsonData = objectMapper.writeValueAsBytes(data);
//
//            // Obfuscate if necessary
//            if (obfuscationService.shouldObfuscate(path.getFileName().toString())) {
//                jsonData = obfuscationService.obfuscate(jsonData);
//            }
//
//            Files.write(path, jsonData);
//
//            LoggerUtil.info(this.getClass(), "Successfully wrote data to file: " + path);
//        } catch (Exception e) {
//            LoggerUtil.error(this.getClass(),
//                    String.format("Error writing file %s: %s", path, e.getMessage()));
//            throw new RuntimeException("Failed to write file: " + path, e);
//        } finally {
//            writeLock.unlock();
//        }
//    }

    public <T> T readFile(Path path, TypeReference<T> typeRef, boolean createIfMissing) {
        ReentrantReadWriteLock.ReadLock readLock = getFileLock(path).readLock();
        readLock.lock();

        try {
            if (!Files.exists(path)) {
                LoggerUtil.info(this.getClass(), "File not found: " + path);
                if (!createIfMissing) {
                    throw new IOException("File not found: " + path);
                }
                return createEmptyStructure(typeRef);
            }
            return objectMapper.readValue(path.toFile(), typeRef);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading file %s: %s", path, e.getMessage()));
            if (createIfMissing) {
                return createEmptyStructure(typeRef);
            }
            throw new RuntimeException("Failed to read file: " + path, e);
        } finally {
            readLock.unlock();
        }
    }
    //no encryption version
    public <T> void writeFile(Path path, T data) {
        ReentrantReadWriteLock.WriteLock writeLock = getFileLock(path).writeLock();
        writeLock.lock();

        try {
            ensureDirectoryExists(path);
            objectMapper.writeValue(path.toFile(), data);
            LoggerUtil.info(this.getClass(), "Successfully wrote data to file: " + path);
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error writing file %s: %s", path, e.getMessage()));
            throw new RuntimeException("Failed to write file: " + path, e);
        } finally {
            writeLock.unlock();
        }
    }

    // Ensure the directory structure exists for a given path
    private void ensureDirectoryExists(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    //Get file lock for a specific path
    private ReentrantReadWriteLock getFileLock(Path path) {
        return fileLocks.computeIfAbsent(path, k -> new ReentrantReadWriteLock());
    }

    // Create empty data structure based on type
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

    // Path helper methods using PathConfig's resolution
    public Path getAdminWorktimePath(int year, int month) {
        return pathConfig.getAdminWorktimePath(year, month);
    }

    public Path getUserWorktimePath(String username, int year, int month) {
        return pathConfig.getUserWorktimeFilePath(username, year, month);
    }

    public Path getHolidayPath() {
        return pathConfig.getHolidayListPath();
    }

    public Path getUsersPath() {
        return pathConfig.getUsersJsonPath();
    }

    public Path getSessionPath(String username, Integer userId) {
        return pathConfig.getSessionFilePath(username, userId);
    }

    public Path getUserRegisterPath(String username, Integer userId, int year, int month) {
        return pathConfig.getUserRegisterPath(username, userId, year, month);
    }

    public Path getAdminRegisterPath(String username, Integer userId, int year, int month) {
        return pathConfig.getAdminRegisterPath(username, userId, year, month);
    }

    public Path getAdminBonusPath(int year, int month) {
        return pathConfig.getAdminBonusPath(year, month);
    }

    // Check if a file exists at the given path
    public boolean fileExists(Path path) {
        ReentrantReadWriteLock.ReadLock readLock = getFileLock(path).readLock();
        readLock.lock();
        try {
            return Files.exists(path);
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

    public Path getNetworkPath() {
        return pathConfig.getNetworkPath();
    }
}