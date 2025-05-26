package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.security.FileAccessSecurityRules;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Domain service for all worktime-related data operations.
 * Handles user worktime, admin worktime operations with event-driven backups.
 */
@Service
public class WorktimeDataService {
    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;
    private final FileAccessSecurityRules securityRules;

    public WorktimeDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig,
            FileAccessSecurityRules securityRules) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathResolver = pathResolver;
        this.pathConfig = pathConfig;
        this.securityRules = securityRules;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ===== USER WORKTIME OPERATIONS =====

    /**
     * Reads worktime data from the appropriate source based on user access.
     */
    public List<WorkTimeTable> readUserWorktime(String username, int year, int month) {
        try {
            // Get current user
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // If user is accessing their own data, use local path
            if (currentUsername.equals(username)) {
                LoggerUtil.info(this.getClass(), String.format("Reading local worktime for user %s", username));
                FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);
                Optional<List<WorkTimeTable>> entries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);
                return entries.orElse(new ArrayList<>());
            }

            // If accessing other user's data, use network path
            if (pathConfig.isNetworkAvailable()) {
                LoggerUtil.info(this.getClass(), String.format("Reading network worktime for user %s by %s", username, currentUsername));
                return readNetworkUserWorktimeReadOnly(username, year, month);
            }

            throw new RuntimeException("Network access required but not available");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading worktime for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Writes worktime data using event-driven backups.
     */
    public void writeUserWorktime(String username, List<WorkTimeTable> entries, int year, int month) {

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write worktime: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d worktime entries for user %s - %d/%d",
                    entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing worktime for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Writes worktime data with operating username validation using event-driven backups.
     */
    public void writeUserWorktime(String username, List<WorkTimeTable> entries, int year, int month, String operatingUsername) {
        if (!username.equals(operatingUsername)) {
            throw new SecurityException("Username mismatch with operating username");
        }

        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write worktime: " + result.getErrorMessage().orElse("Unknown error"));
            }

            // Enhanced logging with entry details
            if (!entries.isEmpty()) {
                WorkTimeTable latestEntry = entries.get(entries.size() - 1);
                LoggerUtil.info(this.getClass(), String.format(
                        "Saved worktime for user %s - %d/%d using event system. Latest entry: %s, Status: %s",
                        username, year, month, latestEntry.getWorkDate(), latestEntry.getAdminSync()));
            } else {
                LoggerUtil.info(this.getClass(), String.format(
                        "Saved empty worktime for user %s - %d/%d using event system", username, year, month));
            }

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format("Error writing worktime for user %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Reads worktime data in read-only mode, falling back to local.
     */
    public List<WorkTimeTable> readWorktimeReadOnly(String username, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);

            // First try network if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, null, FilePathResolver.FileType.WORKTIME, params);
                Optional<List<WorkTimeTable>> entries = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {}, true);

                if (entries.isPresent()) {
                    return entries.get();
                }
            }

            // Fall back to local file
            FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);
            Optional<List<WorkTimeTable>> entries = fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {}, true);

            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format("Read-only worktime access failed for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads worktime data from the network in read-only mode.
     */
    public List<WorkTimeTable> readNetworkUserWorktimeReadOnly(String username, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(username, null, FilePathResolver.FileType.WORKTIME, params);
            Optional<List<WorkTimeTable>> entries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading network worktime for user %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ===== ADMIN WORKTIME OPERATIONS =====

    /**
     * Writes admin worktime data using event-driven backups.
     */
    public void writeAdminWorktime(List<WorkTimeTable> entries, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, entries, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write admin worktime: " + result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d admin worktime entries for %d/%d",
                    entries.size(), year, month));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing admin worktime for %d/%d: %s", year, month, e.getMessage()), e);
        }
    }

    /**
     * Reads local admin worktime data.
     */
    public List<WorkTimeTable> readLocalAdminWorktime(int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);
            Optional<List<WorkTimeTable>> entries = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);
            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading local admin worktime for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads network admin worktime data.
     */
    public List<WorkTimeTable> readNetworkAdminWorktime(int year, int month) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format("Network not available for admin worktime %d/%d", year, month));
                return new ArrayList<>();
            }

            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath networkPath = pathResolver.getNetworkPath(null, null, FilePathResolver.FileType.ADMIN_WORKTIME, params);
            Optional<List<WorkTimeTable>> entries = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);
            return entries.orElse(new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading network admin worktime for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ===== TIME OFF OPERATIONS =====

    /**
     * Reads time off entries in read-only mode.
     */
    public List<WorkTimeTable> readTimeOffReadOnly(String username, int year) {
        List<WorkTimeTable> allEntries = new ArrayList<>();

        // Only load last 12 months to improve performance
        int currentMonth = LocalDate.now().getMonthValue();
        int currentYear = LocalDate.now().getYear();

        // Only process months for the requested year
        for (int month = 1; month <= 12; month++) {
            // Skip future months
            if (year > currentYear || (year == currentYear && month > currentMonth)) {
                continue;
            }

            try {
                List<WorkTimeTable> monthEntries = readWorktimeReadOnly(username, year, month);
                if (monthEntries != null && !monthEntries.isEmpty()) {
                    // Filter for time off entries only
                    List<WorkTimeTable> timeOffEntries = monthEntries.stream()
                            .filter(entry -> entry.getTimeOffType() != null)
                            .toList();
                    allEntries.addAll(timeOffEntries);
                }
            } catch (Exception e) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Read-only time-off access failed for %s - %d/%d: %s",
                        username, year, month, e.getMessage()));
            }
        }

        return allEntries;
    }
}