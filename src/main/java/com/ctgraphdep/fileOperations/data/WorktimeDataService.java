package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.fileOperations.service.SyncFilesService;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REDESIGNED WorktimeDataService with clear separation of concerns.
 * Key Principles:
 * - No security validation (handled at controller/service layer)
 * - Explicit backup and sync control
 * - Clear user vs admin operation patterns
 * - Smart fallback with sync-to-local when needed (using SyncFilesService)
 * - Merge-specific methods for admin operations
 * Sync Logic:
 * - Normal flow: Local → Network (local is source of truth)
 * - Missing local: Network → Local (bootstrap local from network)
 * - After bootstrap: Resume normal Local → Network flow
 */
@Service
public class WorktimeDataService {

    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;
    private final SyncFilesService syncFilesService;

    public WorktimeDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig,
            SyncFilesService syncFilesService) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathResolver = pathResolver;
        this.pathConfig = pathConfig;
        this.syncFilesService = syncFilesService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // USER WORKTIME OPERATIONS
    // ========================================================================

    /**
     * Writes user worktime with explicit backup and sync control.
     * Pattern: Local First -> Backup -> Network Overwrite
     *
     * @param username Username
     * @param entries Worktime entries
     * @param year Year
     * @param month Month
     */
    public void writeUserLocalWithSyncAndBackup(String username, List<WorkTimeTable> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);

            // Step 1: Write to local with backup enabled
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write user worktime: " +
                        result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d user worktime entries for %s - %d/%d (with backup and sync)",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing user worktime for %s - %d/%d: %s",
                    username, year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads user worktime with smart fallback logic.
     * Pattern: Local for own data, Network for others, Smart sync when missing
     *
     * @param username Username to read
     * @param year Year
     * @param month Month
     * @param currentUsername Current authenticated user
     * @return Worktime entries
     */
    public List<WorkTimeTable> readUserLocalReadOnly(String username, int year, int month, String currentUsername) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            boolean isOwnData = username.equals(currentUsername);

            if (isOwnData) {
                // Reading own data - local first with smart fallback
                return readOwnDataWithSmartFallback(username, year, month, params);
            } else {
                // Reading other user's data - network first
                return readOtherUserDataFromNetwork(username, year, month, params);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading user worktime for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private List<WorkTimeTable> readOwnDataWithSmartFallback(String username, int year, int month, Map<String, Object> params) {
        FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);

        // Try local first
        Optional<List<WorkTimeTable>> localEntries = fileReaderService.readLocalFile(
                localPath, new TypeReference<>() {}, true);

        if (localEntries.isPresent() && !localEntries.get().isEmpty()) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Found local worktime data for %s - %d/%d (%d entries)",
                    username, year, month, localEntries.get().size()));
            return localEntries.get();
        }

        // Local is missing/empty - try network and sync to local if found
        if (pathConfig.isNetworkAvailable()) {
            FilePath networkPath = pathResolver.getNetworkPath(username, null, FilePathResolver.FileType.WORKTIME, params);

            Optional<List<WorkTimeTable>> networkEntries = fileReaderService.readNetworkFile(
                    networkPath, new TypeReference<>() {}, true);

            if (networkEntries.isPresent() && !networkEntries.get().isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Found network worktime data for %s - %d/%d, syncing from network to local",
                        username, year, month));

                // Use SyncFilesService to sync from network to local
                try {
                    syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                    LoggerUtil.info(this.getClass(), String.format(
                            "Successfully synced worktime network → local for %s - %d/%d",
                            username, year, month));
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Failed to sync worktime network → local for %s - %d/%d: %s",
                            username, year, month, e.getMessage()));
                    // Continue anyway - return the network data
                }

                return networkEntries.get();
            }
        }

        // Both local and network are missing/empty - return empty list
        LoggerUtil.debug(this.getClass(), String.format(
                "No worktime data found for %s - %d/%d, returning empty list",
                username, year, month));
        return new ArrayList<>();
    }

    private List<WorkTimeTable> readOtherUserDataFromNetwork(String username, int year, int month, Map<String, Object> params) {
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network not available for reading other user worktime data");
            return new ArrayList<>();
        }

        FilePath networkPath = pathResolver.getNetworkPath(username, null, FilePathResolver.FileType.WORKTIME, params);

        Optional<List<WorkTimeTable>> networkEntries = fileReaderService.readNetworkFile(
                networkPath, new TypeReference<>() {}, true);

        if (networkEntries.isPresent()) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Read other user worktime data from network for %s - %d/%d (%d entries)",
                    username, year, month, networkEntries.get().size()));
            return networkEntries.get();
        }

        return new ArrayList<>();
    }

    /**
     * Reads user worktime from network ONLY without any sync or backup operations.
     * Pattern: Network-only, no fallback, no sync, no local operations
     * This is for when you specifically want to see what's on the network
     * without affecting local files in any way.
     *
     * @param username Username
     * @param year Year
     * @param month Month
     * @return User worktime entries from network, or empty if not found
     */
    public List<WorkTimeTable> readUserFromNetworkOnly(String username, int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format("Network not available for user worktime network-only read %s - %d/%d", username, year, month));
                return new ArrayList<>();
            }

            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(username, null, FilePathResolver.FileType.WORKTIME, params);

            Optional<List<WorkTimeTable>> networkEntries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

            if (networkEntries.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format("Read user worktime network-only data for %s - %d/%d (%d entries)", username, year, month, networkEntries.get().size()));
                return networkEntries.get();
            } else {
                LoggerUtil.debug(this.getClass(), String.format("No user worktime network data found for %s - %d/%d", username, year, month));
                return new ArrayList<>();
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Error reading user worktime network-only data for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // ADMIN WORKTIME OPERATIONS
    // ========================================================================

    /**
     * Writes admin worktime with explicit backup and sync control.
     * Pattern: Local First -> Backup -> Network Overwrite
     *
     * @param entries Admin worktime entries
     * @param year Year
     * @param month Month
     */
    public void writeAdminLocalWithSyncAndBackup(List<WorkTimeTable> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);

            // Step 1: Write to local with backup enabled
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write admin worktime: " +
                        result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d admin worktime entries for %d/%d (with backup and sync)",
                    entries.size(), year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing admin worktime for %d/%d: %s",
                    year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads admin worktime with smart fallback logic.
     * Pattern: Local first with smart sync when missing
     *
     * @param year Year
     * @param month Month
     * @return Admin worktime entries
     */
    public List<WorkTimeTable> readAdminLocalReadOnly(int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);

            // Try local first
            Optional<List<WorkTimeTable>> localEntries = fileReaderService.readLocalFile(
                    localPath, new TypeReference<>() {}, true);

            if (localEntries.isPresent() && !localEntries.get().isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Found local admin worktime data for %d/%d (%d entries)",
                        year, month, localEntries.get().size()));
                return localEntries.get();
            }

            // Local is missing/empty - try network and sync to local if found
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);

                Optional<List<WorkTimeTable>> networkEntries = fileReaderService.readNetworkFile(
                        networkPath, new TypeReference<>() {}, true);

                if (networkEntries.isPresent() && !networkEntries.get().isEmpty()) {
                    LoggerUtil.info(this.getClass(), String.format(
                            "Found network admin worktime data for %d/%d, syncing from network to local",
                            year, month));

                    // Use SyncFilesService to sync from network to local
                    try {
                        syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                        LoggerUtil.info(this.getClass(), String.format(
                                "Successfully synced admin worktime network → local for %d/%d",
                                year, month));
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Failed to sync admin worktime network → local for %d/%d: %s",
                                year, month, e.getMessage()));
                        // Continue anyway - return the network data
                    }

                    return networkEntries.get();
                }
            }

            // Both local and network are missing/empty - return empty list
            LoggerUtil.debug(this.getClass(), String.format(
                    "No admin worktime data found for %d/%d, returning empty list",
                    year, month));
            return new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading admin worktime for %d/%d: %s",
                    year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads admin worktime from network ONLY for merge operations.
     * Pattern: Network-only, no fallback, no sync, no local operations
     * This is specifically for admin merge/consolidation operations - we only want to CHECK
     * if admin worktime exists on network, without affecting local files.
     *
     * @param year Year
     * @param month Month
     * @return Admin worktime entries from network, or empty if not found
     */
    public List<WorkTimeTable> readAdminByUserNetworkReadOnly(int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Network not available for admin worktime merge read %d/%d",
                        year, month));
                return new ArrayList<>();
            }

            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);

            Optional<List<WorkTimeTable>> networkEntries = fileReaderService.readNetworkFile(
                    networkPath, new TypeReference<>() {}, true);

            if (networkEntries.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Read admin worktime network data for merge %d/%d (%d entries)",
                        year, month, networkEntries.get().size()));
                return networkEntries.get();
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No admin worktime network data found for merge %d/%d",
                        year, month));
                return new ArrayList<>();
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading admin worktime network data for merge %d/%d: %s",
                    year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
}