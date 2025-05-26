package com.ctgraphdep.fileOperations.data;

import com.ctgraphdep.fileOperations.config.PathConfig;
import com.ctgraphdep.fileOperations.core.FilePath;
import com.ctgraphdep.fileOperations.core.FileOperationResult;
import com.ctgraphdep.fileOperations.service.FilePathResolver;
import com.ctgraphdep.fileOperations.service.FileReaderService;
import com.ctgraphdep.fileOperations.service.FileWriterService;
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

/**
 * Domain service for all time-off related data operations.
 * Handles time off trackers and time off entries with event-driven backups.
 */
@Service
public class TimeOffDataService {
    private final FileWriterService fileWriterService;
    private final FileReaderService fileReaderService;
    private final FilePathResolver pathResolver;
    private final PathConfig pathConfig;

    public TimeOffDataService(
            FileWriterService fileWriterService,
            FileReaderService fileReaderService,
            FilePathResolver pathResolver,
            PathConfig pathConfig) {
        this.fileWriterService = fileWriterService;
        this.fileReaderService = fileReaderService;
        this.pathResolver = pathResolver;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ===== TIME OFF TRACKER OPERATIONS =====

    /**
     * Reads the time off tracker.
     */
    public TimeOffTracker readTimeOffTracker(String username, Integer userId, int year) {
        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);

            // Create file paths
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);

            // First try to read from local file
            Optional<TimeOffTracker> localResult = fileReaderService.readLocalFile(localPath, new TypeReference<>() {}, true);

            if (localResult.isPresent()) {
                return localResult.get();
            }

            // If network is available, try to read from network
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);
                Optional<TimeOffTracker> networkResult = fileReaderService.readNetworkFile(networkPath, new TypeReference<>() {}, true);

                if (networkResult.isPresent()) {
                    // Save to local for future use
                    TimeOffTracker tracker = networkResult.get();
                    writeTimeOffTracker(tracker, year);
                    return tracker;
                }
            }

            return null;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading time off tracker for %s (%d): %s", username, year, e.getMessage()));
            return null;
        }
    }

    /**
     * Writes the time off tracker using event-driven backups.
     */
    public void writeTimeOffTracker(TimeOffTracker tracker, int year) {
        if (tracker == null || tracker.getUsername() == null) {
            LoggerUtil.error(this.getClass(), "Cannot save null tracker or tracker without username");
            return;
        }

        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);
            FilePath localPath = pathResolver.getLocalPath(
                    tracker.getUsername(),
                    tracker.getUserId(),
                    FilePathResolver.FileType.TIMEOFF_TRACKER,
                    params
            );

            // Use FileWriterService with network sync - triggers events and backups
            FileOperationResult result = fileWriterService.writeWithNetworkSync(localPath, tracker, true);

            if (!result.isSuccess()) {
                LoggerUtil.error(this.getClass(), "Failed to save time off tracker: " + result.getErrorMessage().orElse("Unknown error"));
                return;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Saved time off tracker for %s (%d) with %d requests",
                    tracker.getUsername(), year, tracker.getRequests().size()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error saving time off tracker for %s (%d): %s",
                    tracker.getUsername(), year, e.getMessage()));
        }
    }

    /**
     * Reads the time off tracker in read-only mode.
     */
    public TimeOffTracker readTimeOffTrackerReadOnly(String username, Integer userId, int year) {
        try {
            Map<String, Object> params = FilePathResolver.createYearParams(year);

            // Try network first if available
            if (pathConfig.isNetworkAvailable()) {
                FilePath networkPath = pathResolver.getNetworkPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);
                Optional<TimeOffTracker> result = fileReaderService.readFileReadOnly(networkPath, new TypeReference<>() {}, true);

                if (result.isPresent()) {
                    return result.get();
                }
            }

            // Fall back to local file
            FilePath localPath = pathResolver.getLocalPath(username, userId, FilePathResolver.FileType.TIMEOFF_TRACKER, params);
            Optional<TimeOffTracker> result = fileReaderService.readFileReadOnly(localPath, new TypeReference<>() {}, true);

            return result.orElse(null);
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Read-only time-off tracker access failed for %s (%d): %s", username, year, e.getMessage()));
            return null;
        }
    }

    // ===== TIME OFF ENTRIES OPERATIONS =====

    /**
     * Reads time off entries in read-only mode by analyzing worktime data.
     * This method extracts time off entries from worktime files.
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

    /**
     * Helper method to read worktime data in read-only mode.
     * This delegates to the worktime reading logic but is duplicated here to avoid circular dependencies.
     */
    private List<WorkTimeTable> readWorktimeReadOnly(String username, int year, int month) {
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
            LoggerUtil.debug(this.getClass(), String.format(
                    "Read-only worktime access failed for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ===== TIME OFF ANALYSIS METHODS =====

    /**
     * Gets all time off entries for a specific user and year.
     * This is a convenience method that combines tracker and worktime data.
     */
    public List<WorkTimeTable> getAllTimeOffEntries(String username, Integer userId, int year) {
        // Get time off entries from worktime data
        List<WorkTimeTable> timeOffEntries = readTimeOffReadOnly(username, year);

        // You could also cross-reference with the tracker data here if needed
        TimeOffTracker tracker = readTimeOffTrackerReadOnly(username, userId, year);
        if (tracker != null) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Found %d time off entries and %d tracker requests for %s (%d)",
                    timeOffEntries.size(), tracker.getRequests().size(), username, year));
        }

        return timeOffEntries;
    }

    /**
     * Gets summary statistics for time off usage.
     */
    public TimeOffSummary getTimeOffSummary(String username, Integer userId, int year) {
        List<WorkTimeTable> timeOffEntries = readTimeOffReadOnly(username, year);
        TimeOffTracker tracker = readTimeOffTrackerReadOnly(username, userId, year);

        TimeOffSummary summary = new TimeOffSummary();
        summary.setUsername(username);
        summary.setYear(year);
        summary.setTotalTimeOffDays(timeOffEntries.size());

        if (tracker != null) {
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
                .collect(java.util.stream.Collectors.groupingBy(
                        entry -> entry.getTimeOffType() != null ? entry.getTimeOffType() : "UNKNOWN",
                        java.util.stream.Collectors.counting()));

        summary.setTimeOffTypeBreakdown(typeBreakdown);

        return summary;
    }

    /**
     * Inner class for time off summary data.
     */
    @Setter
    @Getter
    public static class TimeOffSummary {
        // Getters and setters
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