package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service for reading files with proper locking and error handling.
 */
@Service
public class FileReaderService {
    private final ObjectMapper objectMapper;
    private final FilePathResolver pathResolver;
    private final BackupService backupService;
    private final PathConfig pathConfig;
    private final FileObfuscationService obfuscationService;

    public FileReaderService(
            ObjectMapper objectMapper,
            FilePathResolver pathResolver,
            BackupService backupService,
            PathConfig pathConfig,
            FileObfuscationService obfuscationService) {
        this.objectMapper = objectMapper;
        this.pathResolver = pathResolver;
        this.backupService = backupService;
        this.pathConfig = pathConfig;
        this.obfuscationService = obfuscationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Reads a file with proper locking
     * @param filePath The file path to read
     * @param typeRef The type reference for deserialization
     * @param skipDeobfuscation Whether to skip deobfuscation (for user files)
     * @return The deserialized object, or empty if the file can't be read
     */
    public <T> Optional<T> readFile(FilePath filePath, TypeReference<T> typeRef, boolean skipDeobfuscation) {
        Path path = filePath.getPath();

        // Acquire read lock
        ReentrantReadWriteLock.ReadLock readLock = pathResolver.getLock(filePath).readLock();
        readLock.lock();

        try {
            // Try to read the main file first
            if (Files.exists(path) && Files.size(path) >= 3) {
                try {
                    byte[] content = Files.readAllBytes(path);

                    // Apply deobfuscation if needed
                    if (!skipDeobfuscation) {
                        content = obfuscationService.deobfuscate(content);
                    }

                    T result = objectMapper.readValue(content, typeRef);
                    return Optional.of(result);
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Error reading file " + path + ": " + e.getMessage());
                    // Continue to back up file
                }
            }

            // If main file doesn't exist or had errors, try the backup
            Path backupPath = backupService.getBackupPath(path);
            if (Files.exists(backupPath) && Files.size(backupPath) >= 3) {
                try {
                    LoggerUtil.info(this.getClass(), "Attempting to read from backup file: " + backupPath);
                    byte[] content = Files.readAllBytes(backupPath);

                    // Apply deobfuscation if needed
                    if (!skipDeobfuscation) {
                        content = obfuscationService.deobfuscate(content);
                    }

                    T result = objectMapper.readValue(content, typeRef);
                    return Optional.of(result);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error reading backup file: " + e.getMessage());
                }
            }

            // If we get here, neither file could be read
            return Optional.empty();
        } catch (IOException e) {
            LoggerUtil.error(this.getClass(), "Error reading file: " + path, e);
            return Optional.empty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Reads a file in read-only mode (no locking)
     * @param filePath The file path to read
     * @param typeRef The type reference for deserialization
     * @param skipDeobfuscation Whether to skip deobfuscation (for user files)
     * @return The deserialized object, or empty if the file can't be read
     */
    public <T> Optional<T> readFileReadOnly(FilePath filePath, TypeReference<T> typeRef, boolean skipDeobfuscation) {
        Path path = filePath.getPath();

        try {
            // Check if file exists and has content
            if (Files.exists(path) && Files.size(path) >= 3) {
                byte[] content = Files.readAllBytes(path);

                // Apply deobfuscation if needed
                if (!skipDeobfuscation) {
                    content = obfuscationService.deobfuscate(content);
                }

                T result = objectMapper.readValue(content, typeRef);
                return Optional.of(result);
            }

            // Try backup if main file doesn't exist or is corrupted
            Path backupPath = backupService.getBackupPath(path);
            if (Files.exists(backupPath) && Files.size(backupPath) >= 3) {
                byte[] content = Files.readAllBytes(backupPath);

                // Apply deobfuscation if needed
                if (!skipDeobfuscation) {
                    content = obfuscationService.deobfuscate(content);
                }

                T result = objectMapper.readValue(content, typeRef);
                return Optional.of(result);
            }

            return Optional.empty();
        } catch (IOException e) {
            LoggerUtil.debug(this.getClass(), "Error in read-only file access: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Reads a network file
     * @param networkPath The network file path to read
     * @param typeRef The type reference for deserialization
     * @param skipDeobfuscation Whether to skip deobfuscation (for user files)
     * @return The deserialized object, or empty if the file can't be read
     */
    public <T> Optional<T> readNetworkFile(FilePath networkPath, TypeReference<T> typeRef, boolean skipDeobfuscation) {
        if (!networkPath.isNetwork()) {
            LoggerUtil.warn(this.getClass(), "Not a network path: " + networkPath.getPath());
            return Optional.empty();
        }

        // Check if network is available
        if (!isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network not available");
            return Optional.empty();
        }

        return readFile(networkPath, typeRef, skipDeobfuscation);
    }

    /**
     * Reads a local file
     * @param localPath The local file path to read
     * @param typeRef The type reference for deserialization
     * @param skipDeobfuscation Whether to skip deobfuscation (for user files)
     * @return The deserialized object, or empty if the file can't be read
     */
    public <T> Optional<T> readLocalFile(FilePath localPath, TypeReference<T> typeRef, boolean skipDeobfuscation) {
        if (!localPath.isLocal()) {
            LoggerUtil.warn(this.getClass(), "Not a local path: " + localPath.getPath());
            return Optional.empty();
        }

        return readFile(localPath, typeRef, skipDeobfuscation);
    }

    /**
     * Checks if the network is available
     * @return True if the network is available
     */
    private boolean isNetworkAvailable() {
        return pathConfig.isNetworkAvailable();
    }
}