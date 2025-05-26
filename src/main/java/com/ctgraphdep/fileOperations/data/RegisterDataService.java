package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.fileOperations.service.SyncFilesService;
import com.ctgraphdep.model.BonusEntry;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.security.FileAccessSecurityRules;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Domain service for all register-related data operations.
 * Handles user registers, check registers, admin registers, team operations, and bonus management with event-driven backups.
 */
@Service
public class RegisterDataService {
    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;
    private final FileAccessSecurityRules securityRules;
    private final SyncFilesService syncFilesService;

    public RegisterDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig,
            FileAccessSecurityRules securityRules,
            SyncFilesService syncFilesService) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathResolver = pathResolver;
        this.pathConfig = pathConfig;
        this.securityRules = securityRules;
        this.syncFilesService = syncFilesService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ===== USER REGISTER OPERATIONS =====

    /**
     * Writes user register entries using event-driven backups.
     */
    public void writeUserRegister(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        securityRules.validateFileAccess(username, true);

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write register: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d register entries for user %s - %d/%d",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing register for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Reads user register entries.
     */
    public List<RegisterEntry> readUserRegister(String username, Integer userId, int year, int month) {
        try {
            // Get current user from security context
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // If accessing own data, use local path
            if (currentUsername.equals(username)) {
                LoggerUtil.info(this.getClass(), String.format("Reading local register for user %s", username));
                FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                Optional<List<RegisterEntry>> entries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);
                return entries.orElse(new ArrayList<>());
            }

            // If accessing other user's data, try network path
            if (pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Reading network register for user %s by %s", username, currentUsername));

                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                Optional<List<RegisterEntry>> entries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);
                return entries.orElse(new ArrayList<>());
            }

            throw new RuntimeException("Network access required but not available");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading register for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads register entries in read-only mode.
     */
    public List<RegisterEntry> readRegisterReadOnly(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // First try network if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                Optional<List<RegisterEntry>> entries = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {}, true);

                if (entries.isPresent()) {
                    return entries.get();
                }
            }

            // Fall back to local file
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);
            Optional<List<RegisterEntry>> entries = fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {}, true);

            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Read-only register access failed for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads network user register.
     */
    public List<RegisterEntry> readNetworkUserRegister(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.REGISTER, params);
            Optional<List<RegisterEntry>> userEntriesOpt = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);
            return userEntriesOpt.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading network user register for %s (%d/%d): %s", username, year, month, e.getMessage()));
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
                            LoggerUtil.warn(this.getClass(), String.format(
                                    "Error reading network register for %s (%d/%d): %s", username, year, month, e.getMessage()));
                        }
                    }

                    // Fallback to local path
                    try {
                        FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.REGISTER, params);
                        Optional<List<RegisterEntry>> localEntries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);
                        localEntries.ifPresent(allEntries::addAll);
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Error reading local register for %s (%d/%d): %s", username, year, month, e.getMessage()));
                    }
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error searching register files for %s: %s", username, e.getMessage()));
        }

        return allEntries;
    }

    // ===== CHECK REGISTER OPERATIONS =====

    /**
     * Writes user check register entries using event-driven backups.
     */
    public void writeUserCheckRegister(String username, Integer userId, List<RegisterCheckEntry> entries, int year, int month) {
        securityRules.validateFileAccess(username, true);

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write check register: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d check register entries for user %s - %d/%d",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing check register for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Reads user check register entries.
     */
    public List<RegisterCheckEntry> readUserCheckRegister(String username, Integer userId, int year, int month) {
        try {
            // Get current user from security context
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // If accessing own data, use local path
            if (currentUsername.equals(username)) {
                LoggerUtil.info(this.getClass(), String.format("Reading local check register for user %s", username));
                FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);
                Optional<List<RegisterCheckEntry>> entries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);
                return entries.orElse(new ArrayList<>());
            }

            // If accessing other user's data, try network path
            if (pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Reading network check register for user %s by %s", username, currentUsername));

                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);
                Optional<List<RegisterCheckEntry>> entries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);
                return entries.orElse(new ArrayList<>());
            }

            throw new RuntimeException("Network access required but not available");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading check register for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads check register in read-only mode.
     */
    public List<RegisterCheckEntry> readCheckRegisterReadOnly(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            // Get network path for check register
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.CHECK_REGISTER, params);

            // Log exact path being accessed
            LoggerUtil.debug(this.getClass(), "Attempting to read check register from: " + networkPath.getPath().toString());

            // Check if file exists
            if (Files.exists(networkPath.getPath())) {
                LoggerUtil.debug(this.getClass(), "Check register file exists, size: " + Files.size(networkPath.getPath()));
            } else {
                LoggerUtil.debug(this.getClass(), "Check register file does not exist at path: " + networkPath.getPath().toString());
            }

            // Read file
            Optional<List<RegisterCheckEntry>> entriesOpt = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

            if (entriesOpt.isPresent()) {
                List<RegisterCheckEntry> entries = entriesOpt.get();
                LoggerUtil.debug(this.getClass(), String.format(
                        "Successfully read network check register for user %s (%d/%d) with %d entries",
                        username, month, year, entries.size()));
                return entries;
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No network check register found for user %s (%d/%d)", username, month, year));
                return new ArrayList<>();
            }
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading network check register for user %s (%d/%d): %s", username, month, year, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ===== TEAM OPERATIONS =====

    /**
     * Reads team members data.
     */
    public List<TeamMemberDTO> readTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath teamPath = pathResolver.getLocalPath(teamLeadUsername, null, FilePathResolver.FileType.TEAM, params);
            Optional<List<TeamMemberDTO>> members = fileReaderService.readLocalFile(teamPath, new TypeReference<>() {}, true);
            return members.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading team members for %s (%d/%d): %s", teamLeadUsername, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes team members data using event-driven backups.
     */
    public void writeTeamMembers(List<TeamMemberDTO> teamMemberDTOS, String teamLeadUsername, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath teamPath = pathResolver.getLocalPath(teamLeadUsername, null, FilePathResolver.FileType.TEAM, params);

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(teamPath, teamMemberDTOS, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write team members: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d team members for %s (%d/%d)",
                    teamMemberDTOS.size(), teamLeadUsername, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing team members for %s (%d/%d): %s", teamLeadUsername, year, month, e.getMessage()), e);
        }
    }

    /**
     * Writes team lead check register entries using event-driven backups.
     */
    public void writeLocalTeamCheckRegister(String username, Integer userId, List<RegisterCheckEntry> entries, int year, int month) {
        // Get current authenticated user (the team lead)
        String teamLeadUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // Validate the team lead's permissions, not the target user's
        securityRules.validateFileAccess(teamLeadUsername, true);

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            // Get path for the team lead's version of the user's check register
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.LEAD_CHECK_REGISTER, params);

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write team check register: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d team check register entries for user %s - %d/%d",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing team check register for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Reads local lead check register.
     */
    public List<RegisterCheckEntry> readLocalLeadCheckRegister(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath leadCheckPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.LEAD_CHECK_REGISTER, params);
            Optional<List<RegisterCheckEntry>> teamLeadEntriesOpt = fileReaderService.readFileReadOnly(leadCheckPath, new TypeReference<>() {}, true);
            return teamLeadEntriesOpt.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading local lead check register for %s (%d/%d): %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ===== ADMIN REGISTER OPERATIONS =====

    /**
     * Reads local admin register.
     */
    public List<RegisterEntry> readLocalAdminRegister(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath adminPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);
            Optional<List<RegisterEntry>> adminEntriesOpt = fileReaderService.readLocalFile(adminPath, new TypeReference<>() {}, true);
            return adminEntriesOpt.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading local admin register for %s (%d/%d): %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes local admin register using event-driven backups.
     */
    public void writeLocalAdminRegister(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath adminPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(adminPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write admin register: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d admin register entries for user %s - %d/%d",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing admin register for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Syncs admin register to network explicitly using SyncFilesService.
     */
    public void syncAdminRegisterToNetwork(String username, Integer userId, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.ADMIN_REGISTER, params);

            // Using CompletableFuture with explicit wait for completion
            LoggerUtil.info(this.getClass(), String.format(
                    "Starting explicit sync of admin register for user %s - %d/%d", username, year, month));

            CompletableFuture<FileOperationResult> future = syncFilesService.syncToNetwork(localPath, networkPath);

            // Wait for the sync to complete
            FileOperationResult result = future.get();  // Using .get() to wait for completion

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to sync admin register to network: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully synced admin register for user %s - %d/%d to network", username, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to sync admin register to network: " + e.getMessage());
            throw new RuntimeException("Failed to sync admin register to network", e);
        }
    }

    // ===== BONUS OPERATIONS =====

    /**
     * Reads admin bonus entries.
     */
    public List<BonusEntry> readAdminBonus(int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath bonusPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_BONUS, params);
            Optional<List<BonusEntry>> bonusEntriesOpt = fileReaderService.readLocalFile(bonusPath, new TypeReference<>() {}, true);
            return bonusEntriesOpt.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading admin bonus for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes admin bonus entries using event-driven backups.
     */
    public void writeAdminBonus(List<BonusEntry> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath bonusPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_BONUS, params);

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(bonusPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write bonus entries: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d bonus entries for %d/%d", entries.size(), year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing bonus entries for %d/%d: %s", year, month, e.getMessage()), e);
        }
    }
}