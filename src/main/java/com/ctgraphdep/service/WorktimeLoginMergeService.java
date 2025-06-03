package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WorktimeLoginMergeService - Handles worktime merging at login.
 * Key Responsibility:
 * - Merge admin network worktime files + user local worktime files → final local worktime files
 * - This happens ONCE at login, then worktime files are final until next login
 * Merge Logic:
 * 1. Admin works offline → saves to network worktime files
 * 2. User login → merge admin network + user local → final local worktime
 * 3. User session uses final local worktime files (no more merging)
 */
@Service
public class WorktimeLoginMergeService {

    private final WorktimeDataService worktimeDataService;
    private final WorktimeMergeService worktimeMergeService;
    private final UserService userService;

    public WorktimeLoginMergeService(
            WorktimeDataService worktimeDataService,
            WorktimeMergeService worktimeMergeService,
            UserService userService) {
        this.worktimeDataService = worktimeDataService;
        this.worktimeMergeService = worktimeMergeService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // LOGIN MERGE OPERATIONS
    // ========================================================================

    /**
     * Perform worktime merge at user login.
     * This merges admin network files with user local files to create final worktime.
     */
    public void performUserWorktimeLoginMerge(String username) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Starting worktime login merge for user: %s", username));

            // Get current date to determine relevant months to merge
            LocalDate now = LocalDate.now();
            int currentYear = now.getYear();

            // Merge current year and previous year (to handle year transitions)
            List<YearMonth> monthsToMerge = getRelevantMonthsForMerge(currentYear);

            int totalMergedMonths = 0;
            int totalEntriesProcessed = 0;

            // Process each month
            for (YearMonth yearMonth : monthsToMerge) {
                try {
                    MergeResult result = mergeMonthWorktime(username, yearMonth);
                    if (result.wasModified()) {
                        totalMergedMonths++;
                        totalEntriesProcessed += result.totalEntries();

                        LoggerUtil.info(this.getClass(), String.format(
                                "Merged worktime for %s - %d/%d: %d entries",
                                username, yearMonth.getYear(), yearMonth.getMonthValue(),
                                result.totalEntries()));
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Failed to merge worktime for %s - %d/%d: %s",
                            username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()));
                    // Continue with other months
                }
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Worktime login merge completed for %s: %d months merged, %d total entries",
                    username, totalMergedMonths, totalEntriesProcessed));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during worktime login merge for %s: %s", username, e.getMessage()), e);
            // Don't throw - login should continue even if merge fails
        }
    }

    /**
     * Merge worktime for a specific month.
     */
    private MergeResult mergeMonthWorktime(String username, YearMonth yearMonth) {
        try {
            int year = yearMonth.getYear();
            int month = yearMonth.getMonthValue();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Merging worktime for %s - %d/%d", username, year, month));

            // Get user ID
            Integer userId = getUserIdFromUsername(username);
            if (userId == null) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Could not find user ID for %s, skipping merge", username));
                return new MergeResult(false, 0);
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
                LoggerUtil.debug(this.getClass(), String.format(
                        "No entries to merge for %s - %d/%d", username, year, month));
                return new MergeResult(false, 0);
            }

            // Perform merge using existing merge logic
            List<WorkTimeTable> mergedEntries = worktimeMergeService.mergeEntries(
                    userEntries, userAdminEntries, userId);

            // Check if result is different from user entries (i.e., admin had changes)
            boolean wasModified = !mergedEntries.equals(userEntries) || !userAdminEntries.isEmpty();

            if (wasModified) {
                // Save merged result as final local worktime
                worktimeDataService.writeUserLocalWithSyncAndBackup(username, mergedEntries, year, month);

                LoggerUtil.debug(this.getClass(), String.format(
                        "Saved merged worktime for %s - %d/%d: %d entries",
                        username, year, month, mergedEntries.size()));

                return new MergeResult(true, mergedEntries.size());
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No changes to save for %s - %d/%d", username, year, month));
                return new MergeResult(false, userEntries.size());
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error merging month worktime for %s - %d/%d: %s",
                    username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()), e);
            throw new RuntimeException("Failed to merge month worktime", e);
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get relevant months to merge (current year + previous year for transitions)
     */
    private List<YearMonth> getRelevantMonthsForMerge(int currentYear) {
        List<YearMonth> months = new ArrayList<>();

        // Add previous year (last 6 months only)
        for (int month = 7; month <= 12; month++) {
            months.add(YearMonth.of(currentYear - 1, month));
        }

        // Add current year (all 12 months)
        for (int month = 1; month <= 12; month++) {
            months.add(YearMonth.of(currentYear, month));
        }

        return months;
    }

    /**
     * Get user ID from username using user service
     */
    private Integer getUserIdFromUsername(String username) {
        try {
            return userService.getUserByUsername(username)
                    .map(User::getUserId)
                    .orElse(null);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Could not get user ID for username %s: %s", username, e.getMessage()));
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

        return adminEntries.stream()
                .filter(entry -> userId.equals(entry.getUserId()))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // RESULT CLASS
    // ========================================================================

    /**
         * Simple result class for merge operations
         */
        private record MergeResult(boolean modified, @Getter int totalEntries) {

        public boolean wasModified() {
                return modified;
            }

        }
}