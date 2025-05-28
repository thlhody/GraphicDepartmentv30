package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
import com.ctgraphdep.fileOperations.service.SyncFilesService;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REDESIGNED TimeOffDataService with clear separation of concerns.
 * Key Principles:
 * - No security validation (handled at controller/service layer)
 * - Explicit backup and sync control
 * - Clear user operation patterns (no admin logic for time off)
 * - Smart fallback with sync-to-local when needed (using SyncFilesService)
 * - Independent worktime reading (dedicated method, not dependent on WorktimeDataService)
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

    public TimeOffDataService(
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
    // TIME OFF TRACKER OPERATIONS
    // ========================================================================

    /**
     * Writes time off tracker with explicit backup and sync control.
     * Pattern: Local First -> Backup -> Network Overwrite
     *
     * @param tracker Time off tracker
     * @param year Year
     */
    public void writeUserLocalTrackerWithSyncAndBackup(TimeOffTracker tracker, int year) {
        if (tracker == null || tracker.getUsername() == null) {
            throw new IllegalArgumentException("Cannot save null tracker or tracker without username");
        }

        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);
            FilePath localPath = pathResolver.getLocalPath(
                    tracker.getUsername(),
                    tracker.getUserId(),
                    FilePathResolver.FileType.TIMEOFF_TRACKER,
                    params
            );

            // Step 1: Write to local with backup enabled
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, tracker, true);

            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to write time off tracker: " +
                        result.getErrorMessage().orElse("Unknown error"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote time off tracker for %s (%d) with %d requests (with backup and sync)",
                    tracker.getUsername(), year, tracker.getRequests() != null ? tracker.getRequests().size() : 0));

        } catch (Exception e) {
            LoggerUtil.logAndThrow(this.getClass(), String.format(
                    "Error writing time off tracker for %s (%d): %s",
                    tracker.getUsername(), year, e.getMessage()), e);
        }
    }

    /**
     * Reads time off tracker with smart fallback logic.
     * Pattern: Local first with smart sync when missing
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return Time off tracker
     */
    public TimeOffTracker readUserLocalTrackerReadOnly(String username, Integer userId, int year) {
        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

            // Try local first
            Optional<TimeOffTracker> localResult = fileReaderService.readLocalFile(
                    localPath, new TypeReference<>() {}, true);

            if (localResult.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Found local time off tracker for %s (%d)",
                        username, year));
                return localResult.get();
            }

            // Local is missing - try network and sync to local if found
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

                Optional<TimeOffTracker> networkResult = fileReaderService.readNetworkFile(
                        networkPath, new TypeReference<>() {}, true);

                if (networkResult.isPresent()) {
                    LoggerUtil.info(this.getClass(), String.format(
                            "Found network time off tracker for %s (%d), syncing from network to local",
                            username, year));

                    // Use SyncFilesService to sync from network to local
                    try {
                        syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                        LoggerUtil.info(this.getClass(), String.format(
                                "Successfully synced time off tracker network → local for %s (%d)",
                                username, year));
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Failed to sync time off tracker network → local for %s (%d): %s",
                                username, year, e.getMessage()));
                        // Continue anyway - return the network data
                    }

                    return networkResult.get();
                }
            }

            // Both local and network are missing - return null
            LoggerUtil.debug(this.getClass(), String.format(
                    "No time off tracker found for %s (%d), returning null",
                    username, year));
            return null;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading time off tracker for %s (%d): %s",
                    username, year, e.getMessage()));
            return null;
        }
    }

    /**
     * Reads time off tracker from network ONLY without any sync or backup operations.
     * Pattern: Network-only, no fallback, no sync, no local operations
     * This is for when you specifically want to see what's on the network
     * without affecting local files in any way.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return Time off tracker from network, or null if not found
     */
    public TimeOffTracker readTrackerFromNetworkOnly(String username, Integer userId, int year) {
        try {
            if (!pathConfig.isNetworkAvailable()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Network not available for time off tracker network-only read %s (%d)",
                        username, year));
                return null;
            }

            Map<String, Object> params = FilePathResolver.createYearParams(year);
            FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

            Optional<TimeOffTracker> networkResult = fileReaderService.readNetworkFile(
                    networkPath, new TypeReference<>() {}, true);

            if (networkResult.isPresent()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Read time off tracker network-only data for %s (%d)",
                        username, year));
                return networkResult.get();
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No time off tracker network data found for %s (%d)",
                        username, year));
                return null;
            }

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Error reading time off tracker network-only data for %s (%d): %s",
                    username, year, e.getMessage()));
            return null;
        }
    }

    // ========================================================================
    // TIME OFF ENTRIES OPERATIONS (WORKTIME-BASED)
    // ========================================================================

    /**
     * Reads worktime data for time off analysis - dedicated local method.
     * This method is independent and doesn't rely on WorktimeDataService.
     * Pattern: Local first with smart sync when missing
     *
     * @param username Username
     * @param year Year
     * @param month Month
     * @return Worktime entries from local (with smart fallback)
     */
    public List<WorkTimeTable> readWorktimeForTimeOffReadOnly(String username, int year, int month) {
        try {
            Map<String, Object> params = FilePathResolver.createYearMonthParams(year, month);
            FilePath localPath = pathResolver.getLocalPath(username, null, FilePathResolver.FileType.WORKTIME, params);

            // Try local first
            Optional<List<WorkTimeTable>> localEntries = fileReaderService.readLocalFile(
                    localPath, new TypeReference<>() {}, true);

            if (localEntries.isPresent() && !localEntries.get().isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Found local worktime data for time off analysis %s - %d/%d (%d entries)",
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
                            "Found network worktime data for time off analysis %s - %d/%d, syncing from network to local",
                            username, year, month));

                    // Use SyncFilesService to sync from network to local
                    try {
                        syncFilesService.syncToLocal(networkPath, localPath).get(); // Wait for completion
                        LoggerUtil.info(this.getClass(), String.format(
                                "Successfully synced worktime network → local for time off analysis %s - %d/%d",
                                username, year, month));
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Failed to sync worktime network → local for time off analysis %s - %d/%d: %s",
                                username, year, month, e.getMessage()));
                        // Continue anyway - return the network data
                    }

                    return networkEntries.get();
                }
            }

            // Both local and network are missing/empty - return empty list
            LoggerUtil.debug(this.getClass(), String.format(
                    "No worktime data found for time off analysis %s - %d/%d, returning empty list",
                    username, year, month));
            return new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Read-only worktime access failed for time off analysis %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Reads time off entries in read-only mode by analyzing worktime data.
     * This method extracts time off entries from worktime files using the dedicated method.
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
                List<WorkTimeTable> monthEntries = readWorktimeForTimeOffReadOnly(username, year, month);
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

    // ========================================================================
    // TIME OFF ANALYSIS METHODS (Keep as is for now)
    // ========================================================================

    /**
     * Gets all time off entries for a specific user and year.
     * This is a convenience method that combines tracker and worktime data.
     */
    public List<WorkTimeTable> getAllTimeOffEntries(String username, Integer userId, int year) {
        // Get time off entries from worktime data using the new method
        List<WorkTimeTable> timeOffEntries = readTimeOffReadOnly(username, year);

        // You could also cross-reference with the tracker data here if needed
        TimeOffTracker tracker = readUserLocalTrackerReadOnly(username, userId, year);
        if (tracker != null) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Found %d time off entries and %d tracker requests for %s (%d)",
                    timeOffEntries.size(),
                    tracker.getRequests() != null ? tracker.getRequests().size() : 0,
                    username, year));
        }

        return timeOffEntries;
    }

    /**
     * Gets summary statistics for time off usage.
     */
    public TimeOffSummary getTimeOffSummary(String username, Integer userId, int year) {
        List<WorkTimeTable> timeOffEntries = readTimeOffReadOnly(username, year);
        TimeOffTracker tracker = readUserLocalTrackerReadOnly(username, userId, year);

        TimeOffSummary summary = new TimeOffSummary();
        summary.setUsername(username);
        summary.setYear(year);
        summary.setTotalTimeOffDays(timeOffEntries.size());

        if (tracker != null && tracker.getRequests() != null) {
            summary.setTotalRequests(tracker.getRequests().size());
            summary.setApprovedRequests((int) tracker.getRequests().stream()
                    .filter(request -> "APPROVED".equals(request.getStatus()))
                    .count());
            summary.setPendingRequests((int) tracker.getRequests().stream()
                    .filter(request -> "PENDING".equals(request.getStatus()))
                    .count());
        }

        // Group by time off type
        Map<String, Long> typeBreakdown = timeOffEntries.stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getTimeOffType() != null ? entry.getTimeOffType() : "UNKNOWN",
                        Collectors.counting()));

        summary.setTimeOffTypeBreakdown(typeBreakdown);

        return summary;
    }

    /**
     * Inner class for time off summary data.
     */
    @Setter
    @Getter
    public static class TimeOffSummary {
        private String username;
        private int year;
        private int totalTimeOffDays;
        private int totalRequests;
        private int approvedRequests;
        private int pendingRequests;
        private Map<String, Long> timeOffTypeBreakdown;

        @Override
        public String toString() {
            return String.format("TimeOffSummary{username='%s', year=%d, totalDays=%d, requests=%d, approved=%d, pending=%d}",
                    username, year, totalTimeOffDays, totalRequests, approvedRequests, pendingRequests);
        }
    }
}