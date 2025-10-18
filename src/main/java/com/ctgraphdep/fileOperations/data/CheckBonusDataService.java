package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.fileOperations.service.SyncFilesService;
import com.ctgraphdep.model.CheckBonusEntry;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data service for Check Bonus operations.
 * Handles file operations for team lead check bonus data.
 * Key Principles:
 * - Team lead writes locally with network sync
 * - Admin reads from network (to be implemented later)
 * - Smart fallback with sync-to-local when needed
 */
@Service
public class CheckBonusDataService {

    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;
    private final SyncFilesService syncFilesService;

    public CheckBonusDataService(FileWriterService fileWriterService, FileReaderService fileReaderService,
                                  FilePathResolver pathResolver, PathConfig pathConfig, SyncFilesService syncFilesService) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathResolver = pathResolver;
        this.pathConfig = pathConfig;
        this.syncFilesService = syncFilesService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // TEAM LEAD CHECK BONUS OPERATIONS
    // ========================================================================

    /**
     * Writes team lead check bonus with explicit backup and sync control.
     * Pattern: Local First -> Backup -> Network Overwrite
     * This is used by team leads to save check bonus data.
     *
     * @param bonusList List of bonus entries
     * @param year Year
     * @param month Month
     */
    public void writeTeamLeadCheckBonusWithSyncAndBackup(List<CheckBonusEntry> bonusList, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.LEAD_CHECK_BONUS, params);

            // Step 1: Write to local with backup enabled and network sync
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, bonusList, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write team lead check bonus: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d team lead check bonus entries for %d/%d (with backup and sync)",
                    bonusList.size(), year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing team lead check bonus for %d/%d: %s", year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads team lead check bonus with smart fallback logic.
     * Pattern: Local first with smart sync when missing
     * This is used by team leads to read their own bonus data.
     *
     * @param year Year
     * @param month Month
     * @return Team lead check bonus entries
     */
    public List<CheckBonusEntry> readTeamLeadCheckBonusLocalReadOnly(int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.LEAD_CHECK_BONUS, params);

            // Try local first
            Optional<List<CheckBonusEntry>> localEntries = fileReaderService.readLocalFile(
                    localPath, new TypeReference<>() {}, true);

            if (localEntries.isPresent() && !localEntries.get().isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Found local team lead check bonus for %d/%d (%d entries)",
                        year, month, localEntries.get().size()));
                return localEntries.get();
            }

            // Local is missing/empty - try network and sync to local if found
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.LEAD_CHECK_BONUS, params);

                Optional<List<CheckBonusEntry>> networkEntries = fileReaderService.readNetworkFile(
                        networkPath, new TypeReference<>() {}, true);

                if (networkEntries.isPresent() && !networkEntries.get().isEmpty()) {
                    LoggerUtil.info(this.getClass(), String.format(
                            "Found network team lead check bonus for %d/%d, syncing from network to local",
                            year, month));

                    // Use SyncFilesService to sync from network to local
                    try {
                        syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                        LoggerUtil.info(this.getClass(), String.format(
                                "Successfully synced team lead check bonus network → local for %d/%d",
                                year, month));
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Failed to sync team lead check bonus network → local for %d/%d: %s",
                                year, month, e.getMessage()));
                        // Continue anyway - return the network data
                    }

                    return networkEntries.get();
                }
            }

            // Both local and network are missing/empty - return empty list
            LoggerUtil.debug(this.getClass(), String.format(
                    "No team lead check bonus found for %d/%d, returning empty list",
                    year, month));
            return new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading team lead check bonus for %d/%d: %s",
                    year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads team lead check bonus from network ONLY without any sync or backup operations.
     * Pattern: Network-only, no fallback, no sync, no local operations
     * This will be used by admin to read team lead bonus entries from network.
     * (To be used in future admin implementation)
     *
     * @param year Year
     * @param month Month
     * @return Team lead check bonus entries from network, or empty if not found
     */
    public List<CheckBonusEntry> readTeamLeadCheckBonusFromNetworkOnly(int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Network not available for team lead check bonus network-only read %d/%d", year, month));
                return new ArrayList<>();
            }

            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.LEAD_CHECK_BONUS, params);

            Optional<List<CheckBonusEntry>> networkEntries = fileReaderService.readNetworkFile(
                    networkPath, new TypeReference<>() {}, true);

            if (networkEntries.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Read team lead check bonus network-only data for %d/%d (%d entries)",
                        year, month, networkEntries.get().size()));
                return networkEntries.get();
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No team lead check bonus network data found for %d/%d",
                        year, month));
                return new ArrayList<>();
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading team lead check bonus network-only data for %d/%d: %s",
                    year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
}