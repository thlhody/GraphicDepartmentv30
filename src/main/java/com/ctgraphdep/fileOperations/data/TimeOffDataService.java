package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.fileOperations.service.SyncFilesService;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * REFACTORED TimeOffDataService following RegisterDataService pattern.
 * Key Principles:
 * - Pure file I/O operations, no business logic
 * - Explicit backup and sync control
 * - Clear user operation patterns (yearly tracker files)
 * - Smart fallback with sync-to-local when needed (using SyncFilesService)
 * - Same pattern as RegisterDataService but for yearly files
 * File Pattern: timeoff_tracker_{username}_{userid}_{year}.json
 * Sync Logic:
 * - Normal flow: Local → Network (local is source of truth)
 * - Missing local: Network → Local (bootstrap local from network)
 * - After bootstrap: Resume normal Local → Network flow
 */
@Service
public class TimeOffDataService {

    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;
    private final SyncFilesService syncFilesService;

    public TimeOffDataService(FileWriterService fileWriterService, FileReaderService fileReaderService,
                              FilePathResolver pathResolver, PathConfig pathConfig, SyncFilesService syncFilesService) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathResolver = pathResolver;
        this.pathConfig = pathConfig;
        this.syncFilesService = syncFilesService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // TIME OFF TRACKER OPERATIONS (YEARLY FILES)
    // ========================================================================

    /**
     * Writes time off tracker with explicit backup and sync control.
     * Pattern: Local First -> Backup -> Network Overwrite
     * Same as RegisterDataService.writeUserLocalWithSyncAndBackup but for yearly tracker
     *
     * @param username Username
     * @param userId User ID
     * @param tracker Time off tracker
     * @param year Year
     */
    public void writeUserLocalTrackerWithSyncAndBackup(String username, Integer userId, TimeOffTracker tracker, int year) {
        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

            // Step 1: Write to local with backup enabled
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, tracker, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write time off tracker: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote time off tracker for %s - %d with %d requests (with backup and sync)",
                    username, year, tracker.getRequests() != null ? tracker.getRequests().size() : 0));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing time off tracker for %s - %d: %s", username, year, e.getMessage()), e);
        }
    }

    /**
     * Reads time off tracker with smart fallback logic.
     * Pattern: Local for own data, Network for others, Smart sync when missing
     * Same as RegisterDataService.readUserLocalReadOnly but for yearly tracker
     *
     * @param username Username to read
     * @param userId User ID
     * @param currentUsername Current authenticated user
     * @param year Year
     * @return Time off tracker
     */
    public TimeOffTracker readUserLocalTrackerReadOnly(String username, Integer userId, String currentUsername, int year) {
        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);
            boolean isOwnData = username.equals(currentUsername);

            if (isOwnData) {
                // Reading own data - local first with smart fallback
                return readOwnTrackerWithSmartFallback(username, userId, year, params);
            } else {
                // Reading other user's data - network first
                return readOtherUserTrackerFromNetwork(username, userId, year, params);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading time off tracker for %s - %d: %s", username, year, e.getMessage()));
            return null;
        }
    }

    /**
     * Reads time off tracker from network ONLY without any sync or backup operations.
     * Pattern: Network-only, no fallback, no sync, no local operations
     * Same as RegisterDataService.readUserFromNetworkOnly but for yearly tracker
     * This is for when you specifically want to see what's on the network
     * without affecting local files in any way.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return Time off tracker from network, or null if not found
     */
    public TimeOffTracker readTrackerFromNetworkReadOnly(String username, Integer userId, int year) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format("Network not available for tracker network-only read %s - %d", username, year));
                return null;
            }

            Map<String, Object> params = FilePathResolver.createYearParams(year);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

            Optional<TimeOffTracker> networkResult = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

            if (networkResult.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format("Read tracker network-only data for %s - %d", username, year));
                return networkResult.get();
            } else {
                LoggerUtil.debug(this.getClass(), String.format("No tracker network data found for %s - %d", username, year));
                return null;
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Error reading tracker network-only data for %s - %d: %s", username, year, e.getMessage()));
            return null;
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Read own tracker data with smart fallback (same pattern as RegisterDataService)
     */
    private TimeOffTracker readOwnTrackerWithSmartFallback(String username, Integer userId, int year, Map<String, Object> params) {
        FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

        // Try local first
        Optional<TimeOffTracker> localResult = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

        if (localResult.isPresent()) {
            LoggerUtil.debug(this.getClass(), String.format("Found local tracker data for %s - %d", username, year));
            return localResult.get();
        }

        // Local is missing - try network and sync to local if found
        if (pathConfig.isNetworkAvailable()) {
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

            Optional<TimeOffTracker> networkResult = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

            if (networkResult.isPresent()) {
                LoggerUtil.info(this.getClass(), String.format("Found network tracker data for %s - %d, syncing from network to local", username, year));

                // Use SyncFilesService to sync from network to local
                try {
                    syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                    LoggerUtil.info(this.getClass(), String.format("Successfully synced tracker network → local for %s - %d", username, year));
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Failed to sync tracker network → local for %s - %d: %s", username, year, e.getMessage()));
                    // Continue anyway - return the network data
                }

                return networkResult.get();
            }
        }

        // Both local and network are missing - return null
        LoggerUtil.debug(this.getClass(), String.format("No tracker data found for %s - %d, returning null", username, year));
        return null;
    }

    /**
     * Read other user's tracker data from network (same pattern as RegisterDataService)
     */
    private TimeOffTracker readOtherUserTrackerFromNetwork(String username, Integer userId, int year, Map<String, Object> params) {
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network not available for reading other user tracker data");
            return null;
        }

        FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

        Optional<TimeOffTracker> networkResult = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

        if (networkResult.isPresent()) {
            LoggerUtil.debug(this.getClass(), String.format("Read other user tracker data from network for %s - %d", username, year));
            return networkResult.get();
        }

        return null;
    }
}