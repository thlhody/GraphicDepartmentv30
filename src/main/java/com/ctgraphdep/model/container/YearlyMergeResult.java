package com.ctgraphdep.model.container;

import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Container for holding the results of yearly worktime merge operations.
 * Contains merged worktime data and updated time-off tracker.
 * Designed for batch file transaction operations.
 */
@Getter
@Setter
public class YearlyMergeResult {

    private final String username;
    private final Integer userId;
    private final int year;

    // Merged worktime data: month -> merged entries
    private Map<Integer, List<WorkTimeTable>> mergedWorktime;

    // Only months that were actually modified and need saving
    private Map<Integer, List<WorkTimeTable>> modifiedMonths;

    // Updated time off tracker
    private TimeOffTracker updatedTracker;

    // Merge statistics
    private int totalEntriesMerged;
    private int monthsModified;
    private int timeOffEntriesFound;
    private long mergeStartTime;

    public YearlyMergeResult(String username, Integer userId, int year) {
        this.username = username;
        this.userId = userId;
        this.year = year;
        this.mergedWorktime = new HashMap<>();
        this.modifiedMonths = new HashMap<>();
        this.mergeStartTime = System.currentTimeMillis();
        this.totalEntriesMerged = 0;
        this.monthsModified = 0;
        this.timeOffEntriesFound = 0;

        LoggerUtil.debug(this.getClass(), String.format(
                "Created yearly merge result for %s - %d", username, year));
    }

    /**
     * Add merged worktime data for a specific month
     */
    public void addMergedWorktime(int month, List<WorkTimeTable> mergedEntries, boolean wasModified) {
        if (mergedEntries != null) {
            mergedWorktime.put(month, mergedEntries);
            totalEntriesMerged += mergedEntries.size();

            // Only track months that were actually modified
            if (wasModified && !mergedEntries.isEmpty()) {
                modifiedMonths.put(month, mergedEntries);
                monthsModified++;

                LoggerUtil.debug(this.getClass(), String.format(
                        "Added modified worktime for %s - %d/%d (%d entries)",
                        username, year, month, mergedEntries.size()));
            }

            // Count time-off entries
            long timeOffCount = mergedEntries.stream()
                    .filter(entry -> entry.getTimeOffType() != null)
                    .count();
            timeOffEntriesFound += (int) timeOffCount;
        }
    }

    /**
     * Get merged worktime for a specific month
     */
    public List<WorkTimeTable> getMergedWorktimeForMonth(int month) {
        return mergedWorktime.getOrDefault(month, List.of());
    }

    /**
     * Check if a month was modified and needs saving
     */
    public boolean wasMonthModified(int month) {
        return modifiedMonths.containsKey(month);
    }

    /**
     * Get all modified month numbers
     */
    public Set<Integer> getModifiedMonthNumbers() {
        return modifiedMonths.keySet();
    }

    /**
     * Check if any months were modified
     */
    public boolean hasModifications() {
        return monthsModified > 0 || trackerWasModified();
    }

    /**
     * Check if tracker was modified (simplified check)
     */
    public boolean trackerWasModified() {
        return updatedTracker != null;
    }

    /**
     * Get merge duration in milliseconds
     */
    public long getMergeDuration() {
        return System.currentTimeMillis() - mergeStartTime;
    }

    /**
     * Get summary of merge operations
     */
    public String getMergeSummary() {
        return String.format(
                "Merged %d entries across %d modified months for %s - %d (found %d time-off entries) in %dms",
                totalEntriesMerged, monthsModified, username, year, timeOffEntriesFound, getMergeDuration());
    }

    /**
     * Get detailed statistics for logging
     */
    public MergeStatistics getStatistics() {
        return new MergeStatistics(
                totalEntriesMerged,
                monthsModified,
                timeOffEntriesFound,
                getMergeDuration(),
                hasModifications(),
                trackerWasModified()
        );
    }

    /**
     * Clear all data from memory to free up resources
     */
    public void clear() {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Clearing yearly merge result for %s - %d (%s)",
                    username, year, getMergeSummary()));

            if (mergedWorktime != null) {
                mergedWorktime.clear();
            }

            if (modifiedMonths != null) {
                modifiedMonths.clear();
            }

            updatedTracker = null;
            totalEntriesMerged = 0;
            monthsModified = 0;
            timeOffEntriesFound = 0;

            LoggerUtil.debug(this.getClass(), String.format(
                    "Successfully cleared merge result memory for %s - %d", username, year));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error clearing yearly merge result for %s - %d: %s",
                    username, year, e.getMessage()), e);
        }
    }

    /**
     * Validate merge result state
     */
    public boolean isValid() {
        return username != null && userId != null && year > 0;
    }

    /**
     * Statistics inner class for detailed reporting
     */
    @Getter
    public static class MergeStatistics {
        private final int totalEntriesMerged;
        private final int monthsModified;
        private final int timeOffEntriesFound;
        private final long mergeDurationMs;
        private final boolean hasModifications;
        private final boolean trackerModified;

        public MergeStatistics(int totalEntriesMerged, int monthsModified, int timeOffEntriesFound,
                               long mergeDurationMs, boolean hasModifications, boolean trackerModified) {
            this.totalEntriesMerged = totalEntriesMerged;
            this.monthsModified = monthsModified;
            this.timeOffEntriesFound = timeOffEntriesFound;
            this.mergeDurationMs = mergeDurationMs;
            this.hasModifications = hasModifications;
            this.trackerModified = trackerModified;
        }

        @Override
        public String toString() {
            return String.format(
                    "MergeStats{entries=%d, months=%d, timeOff=%d, duration=%dms, modified=%s, tracker=%s}",
                    totalEntriesMerged, monthsModified, timeOffEntriesFound, mergeDurationMs,
                    hasModifications, trackerModified);
        }
    }
}