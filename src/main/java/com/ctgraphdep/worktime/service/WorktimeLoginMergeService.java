package com.ctgraphdep.worktime.service;

import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.fileOperations.data.TimeOffDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.TimeOffRequest;
import com.ctgraphdep.service.cache.MainDefaultUserContextCache;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ENHANCED WorktimeLoginMergeService - Now includes Time Off Tracker synchronization.
 * Key Enhancement:
 * - After worktime merge, automatically updates time off tracker with missing entries
 * - Processes calendar year only (Jan-Dec) + December transition handling
 * - One-way sync: Admin worktime entries → Time off tracker
 */
@Service
public class WorktimeLoginMergeService {

    private final WorktimeDataService worktimeDataService;
    private final TimeOffDataService timeOffDataService; // NEW: For tracker operations
    private final WorktimeMergeService worktimeMergeService;
    private final MainDefaultUserContextCache mainDefaultUserContextCache;

    public WorktimeLoginMergeService(
            WorktimeDataService worktimeDataService,
            TimeOffDataService timeOffDataService, // NEW
            WorktimeMergeService worktimeMergeService,
            MainDefaultUserContextCache mainDefaultUserContextCache) {
        this.worktimeDataService = worktimeDataService;
        this.timeOffDataService = timeOffDataService; // NEW
        this.worktimeMergeService = worktimeMergeService;
        this.mainDefaultUserContextCache = mainDefaultUserContextCache;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // LOGIN MERGE OPERATIONS (ENHANCED)
    // ========================================================================

    /**
     * Perform worktime merge at user login.
     * ENHANCED: Now also synchronizes time off tracker with worktime entries.
     */
    public void performUserWorktimeLoginMerge(String username) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Starting enhanced worktime login merge for user: %s", username));

            // Get current date to determine relevant months to merge
            LocalDate now = LocalDate.now();
            int currentYear = now.getYear();

            // ENHANCED: Get calendar year months + transition handling
            List<YearMonth> monthsToMerge = getRelevantMonthsForMerge(currentYear, now);

            int totalMergedMonths = 0;
            int totalEntriesProcessed = 0;
            int totalTrackerUpdates = 0; // NEW: Track tracker updates

            // Process each month
            for (YearMonth yearMonth : monthsToMerge) {
                try {
                    MergeResult result = mergeMonthWorktime(username, yearMonth);
                    if (result.wasModified()) {
                        totalMergedMonths++;
                        totalEntriesProcessed += result.totalEntries();

                        // NEW: Track tracker updates
                        if (result.trackerUpdated()) {
                            totalTrackerUpdates++;
                        }

                        LoggerUtil.info(this.getClass(), String.format(
                                "Merged worktime for %s - %d/%d: %d entries, tracker updated: %s",
                                username, yearMonth.getYear(), yearMonth.getMonthValue(),
                                result.totalEntries(), result.trackerUpdated()));
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Failed to merge worktime for %s - %d/%d: %s",
                            username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()));
                    // Continue with other months
                }
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Enhanced worktime login merge completed for %s: %d months merged, %d total entries, %d tracker updates",
                    username, totalMergedMonths, totalEntriesProcessed, totalTrackerUpdates));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during enhanced worktime login merge for %s: %s", username, e.getMessage()), e);
            // Don't throw - login should continue even if merge fails
        }
    }

    /**
     * ENHANCED: Merge worktime for a specific month + update time off tracker.
     */
    private MergeResult mergeMonthWorktime(String username, YearMonth yearMonth) {
        try {
            int year = yearMonth.getYear();
            int month = yearMonth.getMonthValue();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Enhanced merging worktime for %s - %d/%d", username, year, month));

            // Get user ID
            Integer userId = getUserIdFromUsername(username);
            if (userId == null) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Could not find user ID for %s, skipping merge", username));
                return new MergeResult(false, 0, false);
            }

            // Load user local worktime entries
            List<WorkTimeTable> userEntries = worktimeDataService.readUserLocalReadOnly(
                    username, year, month, username);
            if (userEntries == null) {
                userEntries = new ArrayList<>();
            }

            // Load admin network worktime entries for this user
            List<WorkTimeTable> allAdminEntries = worktimeDataService.readAdminByUserNetworkReadOnly(year, month);
            if (allAdminEntries == null) {
                allAdminEntries = new ArrayList<>();
            }

            // Filter admin entries for this specific user
            List<WorkTimeTable> userAdminEntries = filterAdminEntriesForUser(allAdminEntries, userId);

            // Check if merge is needed
            if (userEntries.isEmpty() && userAdminEntries.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format("No entries to merge for %s - %d/%d", username, year, month));
                return new MergeResult(false, 0, false);
            }

            // Perform merge using existing merge logic
            List<WorkTimeTable> mergedEntries = worktimeMergeService.mergeEntries(userEntries, userAdminEntries, userId);

            // Check if result is different from user entries (i.e., admin had changes)
            boolean wasModified = !mergedEntries.equals(userEntries) || !userAdminEntries.isEmpty();

            if (wasModified) {
                // Save merged result as final local worktime
                worktimeDataService.writeUserLocalWithSyncAndBackup(username, mergedEntries, year, month);

                // NEW: Update time off tracker with entries from merged worktime
                boolean trackerUpdated = updateTimeOffTrackerFromWorktime(username, userId, year, mergedEntries);

                LoggerUtil.debug(this.getClass(), String.format("Saved merged worktime for %s - %d/%d: %d entries, tracker updated: %s", username, year, month, mergedEntries.size(), trackerUpdated));

                return new MergeResult(true, mergedEntries.size(), trackerUpdated);
            } else {
                LoggerUtil.debug(this.getClass(), String.format("No changes to save for %s - %d/%d", username, year, month));
                return new MergeResult(false, userEntries.size(), false);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error merging month worktime for %s - %d/%d: %s", username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()), e);
            throw new RuntimeException("Failed to merge month worktime", e);
        }
    }

    // ========================================================================
    // NEW: TIME OFF TRACKER SYNCHRONIZATION
    // ========================================================================

    /**
     * NEW: Update time off tracker with missing entries from merged worktime.
     * This ensures tracker stays in sync with admin-created time off entries.
     */
    private boolean updateTimeOffTrackerFromWorktime(String username, Integer userId, int year, List<WorkTimeTable> mergedEntries) {
        try {
            // Extract time off entries from merged worktime
            Map<LocalDate, String> timeOffEntries = extractTimeOffEntries(mergedEntries);

            if (timeOffEntries.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format("No time off entries found in worktime for %s - %d", username, year));
                return false;
            }

            LoggerUtil.debug(this.getClass(), String.format("Found %d time off entries in worktime for %s - %d: %s", timeOffEntries.size(), username, year, timeOffEntries.keySet()));

            // Load existing time off tracker
            TimeOffTracker tracker = timeOffDataService.readUserLocalTrackerReadOnly(username, userId, username, year);
            if (tracker == null) {
                // Create new tracker if none exists
                tracker = createEmptyTracker(username, userId, year);
                LoggerUtil.debug(this.getClass(), String.format("Created new time off tracker for %s - %d", username, year));
            }

            try {
                // Get current user's holiday balance from MainDefaultUserContextCache (authoritative source)
                Integer currentHolidayDays = mainDefaultUserContextCache.getCurrentPaidHolidayDays();
                if (currentHolidayDays != null) {
                    // Sync tracker's available days with user's current balance
                    tracker.setAvailableHolidayDays(currentHolidayDays);

                    LoggerUtil.debug(this.getClass(), String.format("Synced holiday balance for %s - %d: tracker.availableHolidayDays = %d", username, year, currentHolidayDays));
                } else {
                    LoggerUtil.warn(this.getClass(), String.format("Could not get current holiday days from MainDefaultUserContextCache for %s", username));
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Error syncing holiday balance for %s: %s", username, e.getMessage()));
                // Continue without failing the entire operation
            }



            // Compare and add missing entries
            List<TimeOffRequest> newRequests = findMissingTimeOffRequests(timeOffEntries, tracker);

            if (newRequests.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format("No missing time off entries to add to tracker for %s - %d", username, year));
                return false;
            }

            // Add new requests to tracker
            if (tracker.getRequests() == null) {
                tracker.setRequests(new ArrayList<>());
            }
            tracker.getRequests().addAll(newRequests);

            // Update tracker metadata
            tracker.setLastSyncTime(LocalDateTime.now());

            // Save updated tracker
            timeOffDataService.writeUserLocalTrackerWithSyncAndBackup(username, userId, tracker, year);

            LoggerUtil.info(this.getClass(), String.format("Updated time off tracker for %s - %d: added %d new entries", username, year, newRequests.size()));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating time off tracker for %s - %d: %s", username, year, e.getMessage()), e);
            // Don't throw - tracker sync failure shouldn't break worktime merge
            return false;
        }
    }

    /**
     * NEW: Extract time off entries (SN/CO/CM) from worktime entries.
     */
    private Map<LocalDate, String> extractTimeOffEntries(List<WorkTimeTable> entries) {
        Map<LocalDate, String> timeOffEntries = new HashMap<>();

        for (WorkTimeTable entry : entries) {
            if (entry.getTimeOffType() != null && !entry.getTimeOffType().trim().isEmpty()) {
                String timeOffType = entry.getTimeOffType().trim().toUpperCase();

                // Only include SN/CO/CM entries
                if (timeOffType.matches("^(SN|CO|CM)$")) {
                    timeOffEntries.put(entry.getWorkDate(), timeOffType);
                }
            }
        }

        return timeOffEntries;
    }

    /**
     * NEW: Find time off entries that are missing from the tracker.
     */
    private List<TimeOffRequest> findMissingTimeOffRequests(Map<LocalDate, String> timeOffEntries,
                                                            TimeOffTracker tracker) {
        List<TimeOffRequest> newRequests = new ArrayList<>();

        // Create lookup map of existing tracker requests
        Map<LocalDate, String> existingRequests = new HashMap<>();
        if (tracker.getRequests() != null) {
            for (TimeOffRequest request : tracker.getRequests()) {
                existingRequests.put(request.getDate(), request.getTimeOffType());
            }
        }

        // Find missing entries
        for (Map.Entry<LocalDate, String> entry : timeOffEntries.entrySet()) {
            LocalDate date = entry.getKey();
            String timeOffType = entry.getValue();

            // Check if tracker already has same date + same timeOffType
            String existingType = existingRequests.get(date);
            if (!timeOffType.equals(existingType)) {
                // Missing or different - create new request
                TimeOffRequest newRequest = createTimeOffRequest(date, timeOffType);
                newRequests.add(newRequest);

                LoggerUtil.debug(this.getClass(), String.format("Creating tracker entry: %s -> %s (existing: %s)", date, timeOffType, existingType));
            }
        }

        return newRequests;
    }

    /**
     * NEW: Create a TimeOffRequest for tracker synchronization.
     */
    private TimeOffRequest createTimeOffRequest(LocalDate date, String timeOffType) {
        TimeOffRequest request = new TimeOffRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setDate(date);
        request.setTimeOffType(timeOffType);
        request.setStatus("APPROVED");
        request.setEligibleDays(0);
        request.setCreatedAt(LocalDateTime.now());
        request.setLastUpdated(LocalDateTime.now());
        request.setNotes("Auto-created from worktime files during sync");
        return request;
    }

    /**
     * NEW: Create empty time off tracker.
     */
    private TimeOffTracker createEmptyTracker(String username, Integer userId, int year) {
        TimeOffTracker tracker = new TimeOffTracker();
        tracker.setUsername(username);
        tracker.setUserId(userId);
        tracker.setYear(year);
        tracker.setRequests(new ArrayList<>());
        tracker.setLastSyncTime(LocalDateTime.now());
        tracker.setAvailableHolidayDays(0); // Will be updated by other processes
        tracker.setUsedHolidayDays(0);
        return tracker;
    }

    // ========================================================================
    // ENHANCED HELPER METHODS
    // ========================================================================

    /**
     * FIXED: Get focused months for merge - Current month ± range instead of ALL 12 months
     * Current month = June → merge May, June, July, August (current + previous + next 2)
     */
    private List<YearMonth> getRelevantMonthsForMerge(int currentYear, LocalDate now) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth currentMonth = YearMonth.of(currentYear, now.getMonthValue());

        // FOCUSED APPROACH: Current month + previous month + next 2 months
        months.add(currentMonth.minusMonths(1)); // Previous month (May if current is June)
        months.add(currentMonth);                // Current month (June)
        months.add(currentMonth.plusMonths(1));  // Next month (July)
        months.add(currentMonth.plusMonths(2));  // Next month (August)

        // Special handling for year transitions
        if (now.getMonthValue() == 1) { // January - also include December of previous year
            months.add(YearMonth.of(currentYear - 1, 12));
            LoggerUtil.info(this.getClass(), String.format("Year transition detected: added December %d to merge scope", currentYear - 1));
        }

        LoggerUtil.info(this.getClass(), String.format("Focused merge scope: %d months around %s", months.size(), currentMonth));

        return months;
    }

    private Integer getUserIdFromUsername(String username) {
        try {
            User currentUser = mainDefaultUserContextCache.getCurrentUser();

            if (currentUser != null && username.equals(currentUser.getUsername())) {
                return currentUser.getUserId();
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Username mismatch during login merge: expected %s, cache has %s", username, currentUser != null ? currentUser.getUsername() : "null"));
                return null;
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Could not get user ID from MainDefaultUserContextCache for %s: %s", username, e.getMessage()));
            return null;
        }
    }
    /**
     * Filter admin entries for specific user by userId
     */
    private List<WorkTimeTable> filterAdminEntriesForUser(List<WorkTimeTable> adminEntries, Integer userId) {
        if (userId == null) {
            return new ArrayList<>();
        }

        return adminEntries.stream().filter(entry -> userId.equals(entry.getUserId())).collect(Collectors.toList());
    }

    // ========================================================================
    // ENHANCED RESULT CLASS
    // ========================================================================

    /**
     * ENHANCED: Result class for merge operations - now includes tracker update status.
     */
    private record MergeResult(boolean modified, @Getter int totalEntries, @Getter boolean trackerUpdated) {

        public boolean wasModified() {
            return modified;
        }
    }
}