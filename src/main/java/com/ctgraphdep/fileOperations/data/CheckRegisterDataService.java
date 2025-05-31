package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.fileOperations.service.SyncFilesService;
import com.ctgraphdep.model.*;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REDESIGNED RegisterDataService with clear separation of concerns.
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
public class CheckRegisterDataService {

    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;
    private final SyncFilesService syncFilesService;

    public CheckRegisterDataService(FileWriterService fileWriterService, FileReaderService fileReaderService, FilePathResolver pathResolver,
                                    PathConfig pathConfig, SyncFilesService syncFilesService) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathResolver = pathResolver;
        this.pathConfig = pathConfig;
        this.syncFilesService = syncFilesService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // CHECK REGISTER USER<->TEAM LEADER
    // ========================================================================

    /**
     * Writes user check register with explicit backup and sync control.
     * Pattern: Local First -> Backup -> Network Overwrite
     * This is used by users to save their own check register entries.
     *
     * @param username Username
     * @param userId User ID
     * @param entries User check register entries
     * @param year Year
     * @param month Month
     */
    public void writeUserCheckRegisterWithSyncAndBackup(String username, Integer userId, List<RegisterCheckEntry> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);

            // Step 1: Write to local with backup enabled
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write user check register: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d user check register entries for %s - %d/%d (with backup and sync)",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing user check register for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads user check register with smart fallback logic.
     * Pattern: Local first with smart sync when missing
     * This is used by users to read their own check register entries.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return User check register entries
     */
    public List<RegisterCheckEntry> readUserCheckRegisterLocalReadOnly(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);

            // Try local first
            Optional<List<RegisterCheckEntry>> localEntries = fileReaderService.readLocalFile(
                    localPath, new TypeReference<>() {}, true);

            if (localEntries.isPresent() && !localEntries.get().isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Found local user check register for %s - %d/%d (%d entries)",
                        username, year, month, localEntries.get().size()));
                return localEntries.get();
            }

            // Local is missing/empty - try network and sync to local if found
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);

                Optional<List<RegisterCheckEntry>> networkEntries = fileReaderService.readNetworkFile(
                        networkPath, new TypeReference<>() {}, true);

                if (networkEntries.isPresent() && !networkEntries.get().isEmpty()) {
                    LoggerUtil.info(this.getClass(), String.format(
                            "Found network user check register for %s - %d/%d, syncing from network to local",
                            username, year, month));

                    // Use SyncFilesService to sync from network to local
                    try {
                        syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                        LoggerUtil.info(this.getClass(), String.format(
                                "Successfully synced user check register network → local for %s - %d/%d",
                                username, year, month));
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Failed to sync user check register network → local for %s - %d/%d: %s",
                                username, year, month, e.getMessage()));
                        // Continue anyway - return the network data
                    }

                    return networkEntries.get();
                }
            }

            // Both local and network are missing/empty - return empty list
            LoggerUtil.debug(this.getClass(), String.format(
                    "No user check register found for %s - %d/%d, returning empty list",
                    username, year, month));
            return new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading user check register for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads user check register from network ONLY without any sync or backup operations.
     * Pattern: Network-only, no fallback, no sync, no local operations
     * This is used by team leads to read user check register entries from network.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return User check register entries from network, or empty if not found
     */
    public List<RegisterCheckEntry> readUserCheckRegisterFromNetworkOnly(String username, Integer userId, int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Network not available for user check register network-only read %s - %d/%d", username, year, month));
                return new ArrayList<>();
            }

            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);

            Optional<List<RegisterCheckEntry>> networkEntries = fileReaderService.readNetworkFile(
                    networkPath, new TypeReference<>() {}, true);

            if (networkEntries.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Read user check register network-only data for %s - %d/%d (%d entries)",
                        username, year, month, networkEntries.get().size()));
                return networkEntries.get();
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No user check register network data found for %s - %d/%d",
                        username, year, month));
                return new ArrayList<>();
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading user check register network-only data for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes team lead check register with explicit backup and sync control.
     * Pattern: Local First -> Backup -> Network Overwrite
     * This is used by team leads to save their review of user check register entries.
     *
     * @param username Username (target user being reviewed)
     * @param userId User ID
     * @param entries Team lead check register entries
     * @param year Year
     * @param month Month
     */
    public void writeTeamLeadCheckRegisterWithSyncAndBackup(String username, Integer userId, List<RegisterCheckEntry> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.LEAD_CHECK_REGISTER, params);

            // Step 1: Write to local with backup enabled
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write team lead check register: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d team lead check register entries for user %s - %d/%d (with backup and sync)",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing team lead check register for user %s - %d/%d: %s", username, year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads team lead check register with smart fallback logic.
     * Pattern: Local first with smart sync when missing
     * This is used by team leads to read their own review entries for a user.
     *
     * @param username Username (target user being reviewed)
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return Team lead check register entries
     */
    public List<RegisterCheckEntry> readTeamLeadCheckRegisterLocalReadOnly(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.LEAD_CHECK_REGISTER, params);

            // Try local first
            Optional<List<RegisterCheckEntry>> localEntries = fileReaderService.readLocalFile(
                    localPath, new TypeReference<>() {}, true);

            if (localEntries.isPresent() && !localEntries.get().isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Found local team lead check register for user %s - %d/%d (%d entries)",
                        username, year, month, localEntries.get().size()));
                return localEntries.get();
            }

            // Local is missing/empty - try network and sync to local if found
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.LEAD_CHECK_REGISTER, params);

                Optional<List<RegisterCheckEntry>> networkEntries = fileReaderService.readNetworkFile(
                        networkPath, new TypeReference<>() {}, true);

                if (networkEntries.isPresent() && !networkEntries.get().isEmpty()) {
                    LoggerUtil.info(this.getClass(), String.format(
                            "Found network team lead check register for user %s - %d/%d, syncing from network to local",
                            username, year, month));

                    // Use SyncFilesService to sync from network to local
                    try {
                        syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                        LoggerUtil.info(this.getClass(), String.format(
                                "Successfully synced team lead check register network → local for user %s - %d/%d",
                                username, year, month));
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Failed to sync team lead check register network → local for user %s - %d/%d: %s",
                                username, year, month, e.getMessage()));
                        // Continue anyway - return the network data
                    }

                    return networkEntries.get();
                }
            }

            // Both local and network are missing/empty - return empty list
            LoggerUtil.debug(this.getClass(), String.format(
                    "No team lead check register found for user %s - %d/%d, returning empty list",
                    username, year, month));
            return new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading team lead check register for user %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads team lead check register from network ONLY without any sync or backup operations.
     * Pattern: Network-only, no fallback, no sync, no local operations
     * This is used by users to read team lead review entries from network for merging.
     *
     * @param username Username (target user being reviewed)
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return Team lead check register entries from network, or empty if not found
     */
    public List<RegisterCheckEntry> readTeamLeadCheckRegisterFromNetworkOnly(String username, Integer userId, int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Network not available for team lead check register network-only read for user %s - %d/%d", username, year, month));
                return new ArrayList<>();
            }

            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.LEAD_CHECK_REGISTER, params);

            Optional<List<RegisterCheckEntry>> networkEntries = fileReaderService.readNetworkFile(
                    networkPath, new TypeReference<>() {}, true);

            if (networkEntries.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Read team lead check register network-only data for user %s - %d/%d (%d entries)",
                        username, year, month, networkEntries.get().size()));
                return networkEntries.get();
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No team lead check register network data found for user %s - %d/%d",
                        username, year, month));
                return new ArrayList<>();
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading team lead check register network-only data for user %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
}