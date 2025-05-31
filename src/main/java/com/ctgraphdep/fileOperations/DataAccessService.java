package com.ctgraphdep.fileOperations;

import com.ctgraphdep.config.FileTypeConstants;
import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Simplified DataAccessService acting as a pure facade over domain-specific services.
 * This service maintains backward compatibility while delegating all operations to specialized services.
 * All methods now use the event-driven backup system automatically.
 */
@Getter
@Service
public class DataAccessService {

    private final PathConfig pathConfig;

    public DataAccessService(PathConfig pathConfig) {

        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ===== SYSTEM UTILITY METHODS =====

    /**
     * Checks if network is available.
     */
    public boolean isNetworkAvailable() {
        return pathConfig.isNetworkAvailable();
    }

    /**
     * Checks if offline mode is available by verifying the existence of local user files.
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

    /**
     * Ensures that all required local directories exist.
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
     * Revalidates all local directories.
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
}