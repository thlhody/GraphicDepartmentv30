package com.ctgraphdep.fileOperations.service;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * SystemAvailabilityService - System utility facade for network/offline status checks.
 * This lightweight service provides centralized access to system-level utilities:
 * - Network availability checking
 * - Offline mode capability checking (local user files existence)
 * Note: File I/O operations have been moved to specialized *DataService classes
 * (UserDataService, SessionDataService, RegisterDataService, etc.)
 */
@Getter
@Service
public class SystemAvailabilityService {

    private final PathConfig pathConfig;

    public SystemAvailabilityService(PathConfig pathConfig) {
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ===== SYSTEM UTILITY METHODS =====

    /**
     * Checks if network is available.
     * Delegates to PathConfig which monitors network status.
     *
     * @return true if network storage is accessible
     */
    public boolean isNetworkAvailable() {
        return pathConfig.isNetworkAvailable();
    }

    /**
     * Checks if offline mode is available by verifying the existence of local user files.
     * Used by login system to determine if offline authentication is possible.
     *
     * @return true if local user files exist for offline authentication
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
                return pathStream.anyMatch(path -> path.getFileName().toString().startsWith("local_user_") &&
                        path.getFileName().toString().endsWith(FileTypeConstants.JSON_EXTENSION));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking offline mode availability: " + e.getMessage(), e);
            return false;
        }
    }
}