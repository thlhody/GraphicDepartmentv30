package com.ctgraphdep.merge.login;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.fileOperations.data.TimeOffDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.TimeOffRequest;
import com.ctgraphdep.service.cache.MainDefaultUserContextCache;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.worktime.service.WorktimeMergeService;
import com.ctgraphdep.worktime.util.StatusCleanupUtil;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class WorktimeLoginMerge {

    private final WorktimeDataService worktimeDataService;
    private final TimeOffDataService timeOffDataService;
    private final WorktimeMergeService worktimeMergeService;
    private final MainDefaultUserContextCache mainDefaultUserContextCache;

    public WorktimeLoginMerge(
            WorktimeDataService worktimeDataService,
            TimeOffDataService timeOffDataService,
            WorktimeMergeService worktimeMergeService,
            MainDefaultUserContextCache mainDefaultUserContextCache) {
        this.worktimeDataService = worktimeDataService;
        this.timeOffDataService = timeOffDataService;
        this.worktimeMergeService = worktimeMergeService;
        this.mainDefaultUserContextCache = mainDefaultUserContextCache;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // MAIN LOGIN MERGE OPERATION (FULLY OPTIMIZED)
    // ========================================================================

    public void performUserWorktimeLoginMerge(String username) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting OPTIMIZED worktime login merge for user: %s", username));

            // Get current date to determine relevant months to merge
            LocalDate now = LocalDate.now();
            int currentYear = now.getYear();

            // OPTIMIZATION 1: Reduced scope to 2 months
            List<YearMonth> monthsToMerge = getOptimizedMonthsForMerge(currentYear, now);

            // OPTIMIZATION 2: Try parallel processing first, fallback to sequential
            List<MergeResult> results = attemptParallelMerge(username, monthsToMerge);

            // Process results
            int totalMergedMonths = 0;
            int totalEntriesProcessed = 0;
            int totalTrackerUpdates = 0;
            int totalCleanupOperations = 0;

            for (MergeResult result : results) {
                if (result.wasModified()) {
                    totalMergedMonths++;
                    totalEntriesProcessed += result.totalEntries();

                    if (result.trackerUpdated()) {
                        totalTrackerUpdates++;
                    }

                    if (result.hadStatusCleanup()) {
                        totalCleanupOperations++;
                    }
                }
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "OPTIMIZED worktime login merge completed for %s: %d months merged, %d total entries, %d tracker updates, %d cleanup operations",
                    username, totalMergedMonths, totalEntriesProcessed, totalTrackerUpdates, totalCleanupOperations));

            // CRITICAL: Update tracker ONCE after all months are processed
            // This prevents data loss from parallel writes and ensures balance is always synced
            Integer userId = getUserIdFromUsername(username);
            if (userId != null) {
                updateTimeOffTrackerAfterAllMonthsMerged(username, userId, currentYear);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during optimized worktime login merge for %s: %s", username, e.getMessage()), e);
            // Don't throw - login should continue even if merge fails
        }
    }

    // ========================================================================
    // PARALLEL PROCESSING WITH FALLBACK (NEW)
    // ========================================================================

    // Attempt parallel processing with fallback to sequential.
    private List<MergeResult> attemptParallelMerge(String username, List<YearMonth> monthsToMerge) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Attempting PARALLEL merge for %s with %d months", username, monthsToMerge.size()));

            // Try parallel processing
            List<CompletableFuture<MergeResult>> mergeFutures = monthsToMerge.stream()
                    .map(yearMonth -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return mergeMonthWorktimeOptimized(username, yearMonth);
                        } catch (Exception e) {
                            LoggerUtil.warn(this.getClass(), String.format("Parallel merge failed for %s - %d/%d: %s",
                                    username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()));
                            throw new RuntimeException(e);
                        }
                    }))
                    .toList();

            // Wait for all futures to complete with timeout
            List<MergeResult> results = mergeFutures.stream()
                    .map(future -> {
                        try {
                            return future.get(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new RuntimeException("Parallel merge timeout or failure", e);
                        }
                    })
                    .toList();

            LoggerUtil.info(this.getClass(), String.format("PARALLEL merge successful for %s", username));
            return results;

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Parallel merge failed for %s, falling back to SEQUENTIAL: %s", username, e.getMessage()));

            // Fallback to sequential processing
            return performSequentialMerge(username, monthsToMerge);
        }
    }

    // Sequential merge processing (reliable fallback).
    private List<MergeResult> performSequentialMerge(String username, List<YearMonth> monthsToMerge) {
        LoggerUtil.info(this.getClass(), String.format("Performing SEQUENTIAL merge for %s", username));

        List<MergeResult> results = new ArrayList<>();

        for (YearMonth yearMonth : monthsToMerge) {
            try {
                MergeResult result = mergeMonthWorktimeOptimized(username, yearMonth);
                results.add(result);

                if (result.wasModified()) {
                    LoggerUtil.info(this.getClass(), String.format("Sequential merge: %s - %d/%d: %d entries, tracker updated: %s, cleanup: %s",
                            username, yearMonth.getYear(), yearMonth.getMonthValue(), result.totalEntries(), result.trackerUpdated(), result.hadStatusCleanup()));
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to merge worktime for %s - %d/%d: %s", username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()));
                // Add failed result and continue
                results.add(new MergeResult(false, 0, false, false));
            }
        }

        return results;
    }

    // ========================================================================
    // OPTIMIZED MONTH MERGE WITH COMPLETE STATUS CLEANUP
    // ========================================================================

    // Merge worktime for a specific month with complete status cleanup + tracker sync.
    private MergeResult mergeMonthWorktimeOptimized(String username, YearMonth yearMonth) {
        try {
            int year = yearMonth.getYear();
            int month = yearMonth.getMonthValue();

            LoggerUtil.debug(this.getClass(), String.format("OPTIMIZED merging worktime for %s - %d/%d", username, year, month));

            // Get user ID
            Integer userId = getUserIdFromUsername(username);
            if (userId == null) {
                LoggerUtil.warn(this.getClass(), String.format("Could not find user ID for %s, skipping merge", username));
                return new MergeResult(false, 0, false, false);
            }

            // Load user local worktime entries
            List<WorkTimeTable> userEntries = worktimeDataService.readUserLocalReadOnly(username, year, month, username);
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

            // OPTIMIZATION 3: Complete status cleanup BEFORE merge (perpetuates changes to files)
            boolean userCleanupNeeded = StatusCleanupUtil.cleanupStatuses(
                    userEntries, String.format("user file: %s-%d/%d (login-merge)", username, year, month));
            boolean adminCleanupNeeded = StatusCleanupUtil.cleanupStatuses(
                    userAdminEntries, String.format("admin file: %s-%d/%d (login-merge)", username, year, month));

            boolean anyCleanupNeeded = userCleanupNeeded || adminCleanupNeeded;

            // Check if merge is needed
            if (userEntries.isEmpty() && userAdminEntries.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format("No entries to merge for %s - %d/%d", username, year, month));
                return new MergeResult(false, 0, false, anyCleanupNeeded);
            }

            // Perform merge using existing merge logic
            List<WorkTimeTable> mergedEntries = worktimeMergeService.mergeEntries(userEntries, userAdminEntries, userId);

            // POST-MERGE ZS VALIDATION: Apply ZS logic to merged entries
            boolean zsUpdated = validateAndApplyShortDayLogic(mergedEntries, username);

            // Check if result is different from user entries OR cleanup was needed OR ZS was updated
            boolean wasModified = !mergedEntries.equals(userEntries) || !userAdminEntries.isEmpty() || anyCleanupNeeded || zsUpdated;

            if (wasModified) {
                // Save merged result as final local worktime (includes cleaned statuses)
                worktimeDataService.writeUserLocalWithSyncAndBackup(username, mergedEntries, year, month);

                LoggerUtil.debug(this.getClass(), String.format(
                        "Saved OPTIMIZED merged worktime for %s - %d/%d: %d entries, cleanup: user=%s admin=%s",
                        username, year, month, mergedEntries.size(), userCleanupNeeded, adminCleanupNeeded));

                return new MergeResult(true, mergedEntries.size(), false, anyCleanupNeeded);
            } else {
                LoggerUtil.debug(this.getClass(), String.format("No changes to save for %s - %d/%d", username, year, month));
                return new MergeResult(false, userEntries.size(), false, false);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in optimized month worktime merge for %s - %d/%d: %s", username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()), e);
            throw new RuntimeException("Failed to merge month worktime", e);
        }
    }

    // ========================================================================
    // OPTIMIZED SCOPE (50% REDUCTION)
    // ========================================================================

    // Get optimized months for merge - Only 2 months.
    private List<YearMonth> getOptimizedMonthsForMerge(int currentYear, LocalDate now) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth currentMonth = YearMonth.of(currentYear, now.getMonthValue());

        // OPTIMIZED: Only current + previous month (was current + previous + next 2)
        months.add(currentMonth.minusMonths(1)); // Previous month (for late entries)
        months.add(currentMonth);                // Current month

        // Special handling for year transitions
        if (now.getMonthValue() == 1) { // January - also include December of previous year
            months.add(YearMonth.of(currentYear - 1, 12));
            LoggerUtil.info(this.getClass(), String.format("Year transition detected: added December %d to merge scope", currentYear - 1));
        }

        LoggerUtil.info(this.getClass(), String.format("OPTIMIZED merge scope: %d months around %s (reduced from 4+ months, 50%% performance gain)", months.size(), currentMonth));

        return months;
    }

    // ========================================================================
    // TIME OFF TRACKER SYNCHRONIZATION (OPTIMIZED - SINGLE WRITE)
    // ========================================================================

    /**
     * Update time off tracker ONCE after ALL months have been merged.
     * CRITICAL FIX: This method loads ALL user worktime files for the entire year to ensure
     * we don't lose time-off entries from months that weren't part of the optimized merge scope.
     *
     * This method ALWAYS syncs holiday balance, even if no new time-off entries exist.
     * This ensures tracker stays in sync with admin-created time off entries AND admin holiday balance changes.
     */
    private void updateTimeOffTrackerAfterAllMonthsMerged(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Syncing time off tracker for %s - %d (after all months merged)", username, year));

            // Load existing time off tracker
            TimeOffTracker tracker = timeOffDataService.readUserLocalTrackerReadOnly(username, userId, username, year);
            if (tracker == null) {
                // Create new tracker if none exists
                tracker = createEmptyTracker(username, userId, year);
                LoggerUtil.debug(this.getClass(), String.format("Created new time off tracker for %s - %d", username, year));
            }

            boolean trackerModified = false;

            // STEP 1: ALWAYS sync holiday balance with user file (source of truth)
            try {
                Integer currentHolidayDays = mainDefaultUserContextCache.getCurrentPaidHolidayDays();
                Integer trackerHolidayDays = tracker.getAvailableHolidayDays();

                if (currentHolidayDays != null) {
                    // Check if balance sync is needed
                    if (trackerHolidayDays == null || !trackerHolidayDays.equals(currentHolidayDays)) {
                        tracker.setAvailableHolidayDays(currentHolidayDays);
                        trackerModified = true;

                        LoggerUtil.info(this.getClass(), String.format(
                            "Synced holiday balance for %s - %d: tracker %d → %d days (from user file)",
                            username, year, trackerHolidayDays != null ? trackerHolidayDays : 0, currentHolidayDays));
                    } else {
                        LoggerUtil.debug(this.getClass(), String.format(
                            "Holiday balance already in sync for %s - %d: %d days", username, year, currentHolidayDays));
                    }
                } else {
                    LoggerUtil.warn(this.getClass(), String.format(
                        "Could not get current holiday days from cache for %s", username));
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                    "Error syncing holiday balance for %s: %s", username, e.getMessage()));
                // Continue without failing - balance sync is not critical if requests are updated
            }

            // STEP 2: Load ALL user worktime files for the entire year to extract time-off entries
            Map<LocalDate, String> allTimeOffEntries = new HashMap<>();

            for (int month = 1; month <= 12; month++) {
                try {
                    List<WorkTimeTable> monthEntries = worktimeDataService.readUserLocalReadOnly(username, year, month, username);
                    if (monthEntries != null && !monthEntries.isEmpty()) {
                        Map<LocalDate, String> monthTimeOffEntries = extractTimeOffEntries(monthEntries);
                        allTimeOffEntries.putAll(monthTimeOffEntries);

                        if (!monthTimeOffEntries.isEmpty()) {
                            LoggerUtil.debug(this.getClass(), String.format(
                                "Found %d time-off entries in %s - %d/%d",
                                monthTimeOffEntries.size(), username, year, month));
                        }
                    }
                } catch (Exception e) {
                    // Skip month if read fails - file might not exist yet
                    LoggerUtil.debug(this.getClass(), String.format(
                        "Skipping month %d for %s - %d: %s", month, username, year, e.getMessage()));
                }
            }

            LoggerUtil.info(this.getClass(), String.format(
                "Loaded %d total time-off entries from all months for %s - %d",
                allTimeOffEntries.size(), username, year));

            // STEP 3: Compare and add missing time-off requests
            if (!allTimeOffEntries.isEmpty()) {
                List<TimeOffRequest> newRequests = findMissingTimeOffRequests(allTimeOffEntries, tracker);

                if (!newRequests.isEmpty()) {
                    // Add new requests to tracker
                    if (tracker.getRequests() == null) {
                        tracker.setRequests(new ArrayList<>());
                    }
                    tracker.getRequests().addAll(newRequests);
                    trackerModified = true;

                    LoggerUtil.info(this.getClass(), String.format(
                        "Added %d missing time off requests to tracker for %s - %d", newRequests.size(), username, year));
                } else {
                    LoggerUtil.debug(this.getClass(), String.format(
                        "No missing time off entries to add to tracker for %s - %d", username, year));
                }
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                    "No time off entries found in any worktime files for %s - %d", username, year));
            }

            // STEP 4: Save tracker if any modifications were made
            if (trackerModified) {
                // Update tracker metadata
                tracker.setLastSyncTime(LocalDateTime.now());

                // Save updated tracker (SINGLE WRITE per login)
                timeOffDataService.writeUserLocalTrackerWithSyncAndBackup(username, userId, tracker, year);

                LoggerUtil.info(this.getClass(), String.format(
                    "Updated and saved time off tracker for %s - %d (balance synced: yes, requests synced from all months)",
                    username, year));
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                    "No modifications needed for tracker %s - %d", username, year));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                "Error updating time off tracker for %s - %d: %s", username, year, e.getMessage()), e);
            // Don't throw - tracker sync failure shouldn't break login merge
        }
    }

    // Extract time off entries (SN/CO/CM) from worktime entries.
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

    // Find time off entries that are missing from the tracker.
    private List<TimeOffRequest> findMissingTimeOffRequests(Map<LocalDate, String> timeOffEntries, TimeOffTracker tracker) {
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

    // Create a TimeOffRequest for tracker synchronization.
    private TimeOffRequest createTimeOffRequest(LocalDate date, String timeOffType) {
        TimeOffRequest request = new TimeOffRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setDate(date);
        request.setTimeOffType(timeOffType);
        request.setStatus(WorkCode.APPROVED);
        request.setEligibleDays(0);
        request.setCreatedAt(LocalDateTime.now());
        request.setLastUpdated(LocalDateTime.now());
        request.setNotes("Auto-created from worktime files during sync");
        return request;
    }

    // Create empty time off tracker.
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
    // HELPER METHODS (UNCHANGED)
    // ========================================================================

    private Integer getUserIdFromUsername(String username) {
        try {
            User currentUser = mainDefaultUserContextCache.getOriginalUser();

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

    // Filter admin entries for specific user by userId
    private List<WorkTimeTable> filterAdminEntriesForUser(List<WorkTimeTable> adminEntries, Integer userId) {
        if (userId == null) {
            return new ArrayList<>();
        }

        return adminEntries.stream().filter(entry -> userId.equals(entry.getUserId())).collect(Collectors.toList());
    }

    // ========================================================================
    // POST-MERGE ZS (SHORT DAY) VALIDATION
    // ========================================================================

    /**
     * Validate and apply ZS (Short Day) logic to merged worktime entries.
     * This ensures that after merging admin changes, ZS markers are correctly applied.
     * Logic for each entry:
     * 1. Skip if entry has no start/end time (incomplete day, in-process)
     * 2. Get user schedule
     * 3. Calculate worked minutes vs schedule
     * 4. If complete AND has ZS → Remove ZS
     * 5. If incomplete AND has no other time-off → Create/Update ZS
     * 6. If it has other time-off (CO, CM, SN, etc.) → Don't touch it
     *
     * @param mergedEntries List of merged worktime entries to validate
     * @param username Username for logging and user lookup
     * @return true if any ZS was added/updated/removed, false otherwise
     */
    private boolean validateAndApplyShortDayLogic(List<WorkTimeTable> mergedEntries, String username) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Starting post-merge ZS validation for %s (%d entries)", username, mergedEntries.size()));

            // Get user schedule
            User user = mainDefaultUserContextCache.getOriginalUser();
            if (user == null || !username.equals(user.getUsername())) {
                LoggerUtil.warn(this.getClass(), String.format("Could not get user schedule for %s during ZS validation", username));
                return false;
            }

            int userScheduleHours = user.getSchedule();
            int scheduleMinutes = userScheduleHours * 60;
            boolean anyUpdated = false;

            LoggerUtil.debug(this.getClass(), String.format("User %s schedule: %d hours (%d minutes)", username, userScheduleHours, scheduleMinutes));

            // Process each entry
            for (WorkTimeTable entry : mergedEntries) {
                // Skip entries without both start and end time (in-process or incomplete)
                if (entry.getDayStartTime() == null || entry.getDayEndTime() == null) {
                    continue;
                }

                String originalTimeOffType = entry.getTimeOffType();
                int rawWorkedMinutes = entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0;

                // IMPORTANT: Use ADJUSTED minutes (after lunch deduction) for ZS calculation
                int adjustedWorkedMinutes = CalculateWorkHoursUtil.calculateAdjustedMinutes(rawWorkedMinutes, userScheduleHours);

                boolean isDayComplete = adjustedWorkedMinutes >= scheduleMinutes;
                boolean hasZS = originalTimeOffType != null && originalTimeOffType.startsWith("ZS-");

                if (isDayComplete) {
                    // Day is complete - remove ZS if it exists
                    if (hasZS) {
                        LoggerUtil.info(this.getClass(), String.format(
                                "POST-MERGE ZS: Day complete for %s on %s (raw: %d min, adjusted: %d min, schedule: %d min). Removing %s",
                                username, entry.getWorkDate(), rawWorkedMinutes, adjustedWorkedMinutes, scheduleMinutes, originalTimeOffType));
                        entry.setTimeOffType(null);
                        anyUpdated = true;
                    }
                } else {
                    // Day is incomplete - create/update ZS if no other time-off type
                    boolean hasOtherTimeOff = originalTimeOffType != null && !originalTimeOffType.trim().isEmpty() && !hasZS;

                    if (!hasOtherTimeOff) {
                        // Calculate missing hours using ADJUSTED minutes
                        int missingMinutes = scheduleMinutes - adjustedWorkedMinutes;
                        int missingHours = (int) Math.ceil(missingMinutes / 60.0);
                        String newZS = "ZS-" + missingHours;

                        if (!newZS.equals(originalTimeOffType)) {
                            LoggerUtil.info(this.getClass(), String.format(
                                    "POST-MERGE ZS: Day incomplete for %s on %s (raw: %d min, adjusted: %d min, schedule: %d min). Updating ZS: %s → %s",
                                    username, entry.getWorkDate(), rawWorkedMinutes, adjustedWorkedMinutes, scheduleMinutes,
                                    originalTimeOffType != null ? originalTimeOffType : "none", newZS));
                            entry.setTimeOffType(newZS);
                            anyUpdated = true;
                        }
                    }
                }
            }

            if (anyUpdated) {
                LoggerUtil.info(this.getClass(), String.format("POST-MERGE ZS validation completed for %s: entries were updated", username));
            } else {
                LoggerUtil.debug(this.getClass(), String.format("POST-MERGE ZS validation completed for %s: no updates needed", username));
            }

            return anyUpdated;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during post-merge ZS validation for %s: %s", username, e.getMessage()), e);
            // Don't throw - ZS validation failure shouldn't break the merge
            return false;
        }
    }

    // ========================================================================
    // ENHANCED RESULT CLASS (WITH CLEANUP TRACKING)
    // ========================================================================

    // Result class for merge operations - now includes cleanup tracking.
    private record MergeResult(
            boolean modified,
            @Getter int totalEntries,
            @Getter boolean trackerUpdated,
            @Getter boolean hadStatusCleanup
    ) {
        public boolean wasModified() {
            return modified;
        }
    }
}