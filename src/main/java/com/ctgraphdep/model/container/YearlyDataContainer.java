package com.ctgraphdep.model.container;

import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for holding a full year's worth of worktime and time-off data in memory.
 * Designed for batch processing to eliminate file I/O bottlenecks.
 *
 * Memory Usage: Max 372 entries (31 days × 12 months) - very manageable.
 */
@Getter
@Setter
public class YearlyDataContainer {

    private final String username;
    private final Integer userId;
    private final int year;

    // User worktime data: month -> list of entries (max 31 per month)
    private Map<Integer, List<WorkTimeTable>> userWorktime;

    // Admin worktime data: month -> list of entries (max 31 per month)
    private Map<Integer, List<WorkTimeTable>> adminWorktime;

    // Existing time off tracker
    private TimeOffTracker existingTracker;

    // Metadata
    private long loadStartTime;
    private int totalEntriesLoaded;

    public YearlyDataContainer(String username, Integer userId, int year) {
        this.username = username;
        this.userId = userId;
        this.year = year;
        this.userWorktime = new HashMap<>();
        this.adminWorktime = new HashMap<>();
        this.loadStartTime = System.currentTimeMillis();
        this.totalEntriesLoaded = 0;

        LoggerUtil.debug(this.getClass(), String.format(
                "Created yearly data container for %s - %d", username, year));
    }

    /**
     * Add user worktime data for a specific month
     */
    public void addUserWorktime(int month, List<WorkTimeTable> entries) {
        if (entries != null && !entries.isEmpty()) {
            userWorktime.put(month, entries);
            totalEntriesLoaded += entries.size();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Added %d user worktime entries for %s - %d/%d",
                    entries.size(), username, year, month));
        }
    }

    /**
     * Add admin worktime data for a specific month
     */
    public void addAdminWorktime(int month, List<WorkTimeTable> entries) {
        if (entries != null && !entries.isEmpty()) {
            adminWorktime.put(month, entries);
            totalEntriesLoaded += entries.size();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Added %d admin worktime entries for %s - %d/%d",
                    entries.size(), username, year, month));
        }
    }

    /**
     * Get user worktime for a specific month (null-safe)
     */
    public List<WorkTimeTable> getUserWorktimeForMonth(int month) {
        return userWorktime.getOrDefault(month, List.of());
    }

    /**
     * Get admin worktime for a specific month (null-safe)
     */
    public List<WorkTimeTable> getAdminWorktimeForMonth(int month) {
        return adminWorktime.getOrDefault(month, List.of());
    }

    /**
     * Check if any data was loaded for the year
     */
    public boolean hasAnyData() {
        return totalEntriesLoaded > 0 || existingTracker != null;
    }

    /**
     * Check if worktime data exists for a specific month
     */
    public boolean hasWorktimeForMonth(int month) {
        return (!getUserWorktimeForMonth(month).isEmpty()) ||
                (!getAdminWorktimeForMonth(month).isEmpty());
    }

    /**
     * Get loading duration in milliseconds
     */
    public long getLoadingDuration() {
        return System.currentTimeMillis() - loadStartTime;
    }

    /**
     * Get summary of loaded data
     */
    public String getLoadingSummary() {
        return String.format("Loaded %d total entries for %s - %d in %dms",
                totalEntriesLoaded, username, year, getLoadingDuration());
    }

    /**
     * Clear all data from memory to free up resources
     */
    public void clear() {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Clearing yearly data container for %s - %d (%s)",
                    username, year, getLoadingSummary()));

            if (userWorktime != null) {
                userWorktime.clear();
            }

            if (adminWorktime != null) {
                adminWorktime.clear();
            }

            existingTracker = null;
            totalEntriesLoaded = 0;

            LoggerUtil.debug(this.getClass(), String.format(
                    "Successfully cleared memory for %s - %d", username, year));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error clearing yearly data container for %s - %d: %s",
                    username, year, e.getMessage()), e);
        }
    }

    /**
     * Validate container state
     */
    public boolean isValid() {
        return username != null && userId != null && year > 0;
    }
}