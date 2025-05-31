package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.fileOperations.service.SyncFilesService;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
public class RegisterDataService {

    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;
    private final SyncFilesService syncFilesService;

    public RegisterDataService(FileWriterService fileWriterService, FileReaderService fileReaderService, FilePathResolver pathResolver,
                               PathConfig pathConfig, SyncFilesService syncFilesService) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathResolver = pathResolver;
        this.pathConfig = pathConfig;
        this.syncFilesService = syncFilesService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // USER REGISTER OPERATIONS
    // ========================================================================

    /**
     * Writes user register with explicit backup and sync control.
     * Pattern: Local First -> Backup -> Network Overwrite
     *
     * @param username Username
     * @param userId User ID
     * @param entries Register entries
     * @param year Year
     * @param month Month
     */
    public void writeUserLocalWithSyncAndBackup(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);

            // Step 1: Write to local with backup enabled
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write user register: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d user register entries for %s - %d/%d (with backup and sync)", entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing user register for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads user register with smart fallback logic.
     * Pattern: Local for own data, Network for others, Smart sync when missing
     *
     * @param username Username to read
     * @param userId User ID
     * @param currentUsername Current authenticated user
     * @param year Year
     * @param month Month
     * @return Register entries
     */
    public List<RegisterEntry> readUserLocalReadOnly(String username, Integer userId, String currentUsername, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            boolean isOwnData = username.equals(currentUsername);

            if (isOwnData) {
                // Reading own data - local first with smart fallback
                return readOwnDataWithSmartFallback(username, userId, year, month, params);
            } else {
                // Reading other user's data - network first
                return readOtherUserDataFromNetwork(username, userId, year, month, params);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading user register for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private List<RegisterEntry> readOwnDataWithSmartFallback(String username, Integer userId, int year, int month, Map<String, Object> params) {
        FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);

        // Try local first
        Optional<List<RegisterEntry>> localEntries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

        if (localEntries.isPresent() && !localEntries.get().isEmpty()) {
            LoggerUtil.debug(this.getClass(), String.format("Found local data for %s - %d/%d (%d entries)", username, year, month, localEntries.get().size()));
            return localEntries.get();
        }

        // Local is missing/empty - try network and sync to local if found
        if (pathConfig.isNetworkAvailable()) {
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);

            Optional<List<RegisterEntry>> networkEntries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

            if (networkEntries.isPresent() && !networkEntries.get().isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("Found network data for %s - %d/%d, syncing from network to local", username, year, month));

                // Use SyncFilesService to sync from network to local
                try {
                    syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                    LoggerUtil.info(this.getClass(), String.format("Successfully synced network → local for %s - %d/%d", username, year, month));
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Failed to sync network → local for %s - %d/%d: %s", username, year, month, e.getMessage()));
                    // Continue anyway - return the network data
                }

                return networkEntries.get();
            }
        }

        // Both local and network are missing/empty - return empty list
        LoggerUtil.debug(this.getClass(), String.format("No data found for %s - %d/%d, returning empty list", username, year, month));
        return new ArrayList<>();
    }

    private List<RegisterEntry> readOtherUserDataFromNetwork(String username, Integer userId, int year, int month, Map<String, Object> params) {
        if (!pathConfig.isNetworkAvailable()) {
            LoggerUtil.warn(this.getClass(), "Network not available for reading other user data");
            return new ArrayList<>();
        }

        FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);

        Optional<List<RegisterEntry>> networkEntries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

        if (networkEntries.isPresent()) {
            LoggerUtil.debug(this.getClass(), String.format("Read other user data from network for %s - %d/%d (%d entries)", username, year, month, networkEntries.get().size()));
            return networkEntries.get();
        }

        return new ArrayList<>();
    }

    /**
     * Reads user register from network ONLY without any sync or backup operations.
     * Pattern: Network-only, no fallback, no sync, no local operations
     * This is for when you specifically want to see what's on the network
     * without affecting local files in any way.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return User register entries from network, or empty if not found
     */
    public List<RegisterEntry> readUserFromNetworkOnly(String username, Integer userId, int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format("Network not available for user network-only read %s - %d/%d", username, year, month));
                return new ArrayList<>();
            }

            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);

            Optional<List<RegisterEntry>> networkEntries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

            if (networkEntries.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format("Read user network-only data for %s - %d/%d (%d entries)", username, year, month, networkEntries.get().size()));
                return networkEntries.get();
            } else {
                LoggerUtil.debug(this.getClass(), String.format("No user network data found for %s - %d/%d", username, year, month));
                return new ArrayList<>();
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Error reading user network-only data for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Finds register files across multiple months.
     */
    public List<RegisterEntry> findRegisterFiles(String username, Integer userId) {
        List<RegisterEntry> allEntries = new ArrayList<>();

        try {
            // Determine the years and months to search
            int currentYear = LocalDate.now().getYear();
            int currentMonth = LocalDate.now().getMonthValue();

            // Search previous 24 months (2 years)
            for (int year = currentYear; year >= currentYear - 1; year--) {
                for (int month = (year == currentYear ? currentMonth : 12); month >= 1; month--) {
                    Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

                    // Try network path first if available
                    if (pathConfig.isNetworkAvailable()) {
                        try {
                            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                            Optional<List<RegisterEntry>> networkEntries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);
                            networkEntries.ifPresent(allEntries::addAll);
                        } catch (Exception e) {
                            LoggerUtil.warn(this.getClass(), String.format("Error reading network register for %s (%d/%d): %s", username, year, month, e.getMessage()));
                        }
                    }

                    // Fallback to local path
                    try {
                        FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                        Optional<List<RegisterEntry>> localEntries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);
                        localEntries.ifPresent(allEntries::addAll);
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format("Error reading local register for %s (%d/%d): %s", username, year, month, e.getMessage()));
                    }
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error searching register files for %s: %s", username, e.getMessage()));
        }

        return allEntries;
    }

    // ========================================================================
    // ADMIN REGISTER OPERATIONS
    // ========================================================================

    /**
     * Writes admin register with explicit backup and sync control.
     * Pattern: Local First -> Backup -> Network Overwrite
     *
     * @param username Username (target user)
     * @param userId User ID
     * @param entries Admin register entries
     * @param year Year
     * @param month Month
     */
    public void writeAdminLocalWithSyncAndBackup(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);

            // Step 1: Write to local with backup enabled
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write admin register: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d admin register entries for %s - %d/%d (with backup and sync)", entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing admin register for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads admin register with smart fallback logic.
     * Pattern: Local first with smart sync when missing
     *
     * @param username Username (target user)
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return Admin register entries
     */
    public List<RegisterEntry> readAdminLocalReadOnly(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);

            // Try local first
            Optional<List<RegisterEntry>> localEntries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

            if (localEntries.isPresent() && !localEntries.get().isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format("Found local admin data for %s - %d/%d (%d entries)", username, year, month, localEntries.get().size()));
                return localEntries.get();
            }

            // Local is missing/empty - try network and sync to local if found
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);

                Optional<List<RegisterEntry>> networkEntries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

                if (networkEntries.isPresent() && !networkEntries.get().isEmpty()) {
                    LoggerUtil.info(this.getClass(), String.format("Found network admin data for %s - %d/%d, syncing from network to local", username, year, month));

                    // Use SyncFilesService to sync from network to local
                    try {
                        syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                        LoggerUtil.info(this.getClass(), String.format("Successfully synced admin network → local for %s - %d/%d", username, year, month));
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format("Failed to sync admin network → local for %s - %d/%d: %s", username, year, month, e.getMessage()));
                        // Continue anyway - return the network data
                    }

                    return networkEntries.get();
                }
            }

            // Both local and network are missing/empty - return empty list
            LoggerUtil.debug(this.getClass(), String.format("No admin data found for %s - %d/%d, returning empty list", username, year, month));
            return new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading admin register for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads admin register from network ONLY for merge operations.
     * Pattern: Network-only, no fallback, no sync, no local operations
     * This is specifically for admin merge at login - we only want to CHECK
     * if admin register exists on network, without affecting local files.
     * @param username Username (target user)
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return Admin register entries from network, or empty if not found
     */
    public List<RegisterEntry> readAdminByUserNetworkReadOnly(String username, Integer userId, int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format("Network not available for admin merge read %s - %d/%d", username, year, month));
                return new ArrayList<>();
            }

            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);

            Optional<List<RegisterEntry>> networkEntries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

            if (networkEntries.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format("Read admin network data for merge %s - %d/%d (%d entries)", username, year, month, networkEntries.get().size()));
                return networkEntries.get();
            } else {
                LoggerUtil.debug(this.getClass(), String.format("No admin network data found for merge %s - %d/%d", username, year, month));
                return new ArrayList<>();
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Error reading admin network data for merge %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }


    // ========================================================================
    // TEAM LEAD STATISTICS LOCAL ONLY
    // ========================================================================

    /**
     * Reads team members data from local storage only.
     * Pattern: Local only, no network sync
     * This is for team lead statistics that stay local.
     *
     * @param teamLeadUsername Team lead username
     * @param year Year
     * @param month Month
     * @return Team member data
     */
    public List<TeamMemberDTO> readTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath teamPath = pathResolver.getLocalPath(teamLeadUsername, null, FilePathResolver.FileType.TEAM, params);
            Optional<List<TeamMemberDTO>> members = fileReaderService.readLocalFile(teamPath, new TypeReference<>() {}, true);
            return members.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading team members for %s (%d/%d): %s", teamLeadUsername, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes team members data to local storage only with backup.
     * Pattern: Local only with backup, no network sync
     * This keeps team lead statistics local but backed up.
     *
     * @param teamMemberDTOS Team member data
     * @param teamLeadUsername Team lead username
     * @param year Year
     * @param month Month
     */
    public void writeTeamMembers(List<TeamMemberDTO> teamMemberDTOS, String teamLeadUsername, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath teamPath = pathResolver.getLocalPath(teamLeadUsername, null, FilePathResolver.FileType.TEAM, params);

            // Use writeFile (local only) with backup enabled, no network sync
            FileOperationResult result = fileWriterService.writeFile(teamPath, teamMemberDTOS, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write team members: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d team members for %s (%d/%d) - local only with backup", teamMemberDTOS.size(), teamLeadUsername, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing team members for %s (%d/%d): %s", teamLeadUsername, year, month, e.getMessage()), e);
        }
    }

    // ========================================================================
    // ADMIN BONUS LOCAL ONLY
    // ========================================================================

    /**
     * Reads admin bonus entries from local storage only.
     * Pattern: Local only, no network sync
     * This is for admin bonus data that stays local.
     *
     * @param year Year
     * @param month Month
     * @return Admin bonus entries
     */
    public List<BonusEntry> readAdminBonus(int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath bonusPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_BONUS, params);
            Optional<List<BonusEntry>> bonusEntriesOpt = fileReaderService.readLocalFile(bonusPath, new TypeReference<>() {}, true);
            return bonusEntriesOpt.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Error reading admin bonus for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes admin bonus entries to local storage only with backup.
     * Pattern: Local only with backup, no network sync
     * This keeps admin bonus data local but backed up.
     *
     * @param entries Bonus entries
     * @param year Year
     * @param month Month
     */
    public void writeAdminBonus(List<BonusEntry> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath bonusPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_BONUS, params);

            // Use writeFileWithBackupControl (local only) with backup enabled, no network sync
            FileOperationResult result = fileWriterService.writeFileWithBackupControl(bonusPath, entries, true, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write bonus entries: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d bonus entries for %d/%d - local only with backup", entries.size(), year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing bonus entries for %d/%d: %s", year, month, e.getMessage()), e);
        }
    }
}