package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.service.TimeOffManagementService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * REFACTORED TimeOffCacheService - Clean Architecture Implementation.
 * Key Principles:
 * 1. Cache yearly trackers (built from final worktime files)
 * 2. Write-through for time off requests
 * 3. Tracker is display/summary layer (yearly-based)
 * 4. All display operations use cache (fast!)
 * Cache Pattern:
 * - Key: "username-year"
 * - Value: TimeOffTracker (yearly summary)
 * - Source: Final worktime files (merged at login)
 * - Write-through: Updates tracker + worktime + user balance
 */
@Service
public class TimeOffCacheService {

    private final TimeOffManagementService timeOffManagementService;

    // Thread-safe cache - yearKey as key (format: "username-year")
    private final ConcurrentHashMap<String, TimeOffCacheEntry> timeOffCache = new ConcurrentHashMap<>();

    // Global cache lock for operations that affect multiple entries
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    @Autowired
    public TimeOffCacheService(TimeOffManagementService timeOffManagementService) {
        this.timeOffManagementService = timeOffManagementService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // MAIN CACHE OPERATIONS - CLEAN ARCHITECTURE
    // ========================================================================

    /**
     * Get time off tracker for display (builds from final worktime files if not cached)
     * This is the main entry point for timeoff page display
     */
    public TimeOffTracker getYearTracker(String username, Integer userId, int year) {
        try {
            String yearKey = createYearKey(username, year);

            // Try cache first
            TimeOffCacheEntry cacheEntry = timeOffCache.get(yearKey);

            if (cacheEntry != null && cacheEntry.isValid() && !cacheEntry.isExpired()) {
                LoggerUtil.debug(this.getClass(), String.format("Cache hit for %s - %d", username, year));
                return cacheEntry.getTracker();
            }

            // Cache miss or expired - build from final worktime files
            LoggerUtil.info(this.getClass(), String.format(
                    "Building tracker from final worktime files for %s - %d", username, year));
            return loadTrackerFromWorktimeFiles(username, userId, year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting year tracker for %s - %d: %s", username, year, e.getMessage()), e);
            return null;
        }
    }

    /**
     * Add time off request with write-through to all layers
     * CLEAN FLOW: worktime → balance → tracker → cache
     */
    public boolean addTimeOffRequest(String username, Integer userId, List<LocalDate> dates, String timeOffType) {
        try {
            if (dates == null || dates.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Cannot add time off request with empty dates");
                return false;
            }

            int year = dates.get(0).getYear();
            String yearKey = createYearKey(username, year);

            LoggerUtil.info(this.getClass(), String.format(
                    "Processing time off request for %s - %d dates, type %s", username, dates.size(), timeOffType));

            // Write-through: Update all layers via TimeOffManagementService
            // This updates: worktime files → user balance → tracker
            boolean success = timeOffManagementService.addTimeOffRequest(username, userId, dates, timeOffType);

            if (!success) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to add time off request via service for %s", username));
                return false;
            }

            // Invalidate cache to force reload with fresh data on next access
            clearYear(username, year);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully added %d time off requests for %s - %d (%s)",
                    dates.size(), username, year, timeOffType));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error adding time off request for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Get time off summary from cached tracker (fast display)
     */
    public TimeOffSummaryDTO getTimeOffSummary(String username, Integer userId, int year) {
        try {
            // Get tracker from cache (built from final worktime files)
            TimeOffTracker tracker = getYearTracker(username, userId, year);

            if (tracker == null) {
                LoggerUtil.debug(this.getClass(), String.format("No tracker found for summary calculation %s - %d", username, year));
                return createEmptySummary();
            }

            // Calculate summary from tracker (fast!)
            return timeOffManagementService.calculateTimeOffSummary(username, userId, year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting time off summary for %s - %d: %s", username, year, e.getMessage()));
            return createEmptySummary();
        }
    }

    /**
     * Get upcoming time off from cached tracker (fast display)
     */
    public List<WorkTimeTable> getUpcomingTimeOff(String username, Integer userId, int year) {
        try {
            // Get tracker from cache (built from final worktime files)
            TimeOffTracker tracker = getYearTracker(username, userId, year);

            if (tracker == null) {
                LoggerUtil.debug(this.getClass(), String.format("No tracker found for upcoming time off %s - %d", username, year));
                return new ArrayList<>();
            }

            // Get upcoming entries from service (uses tracker)
            return timeOffManagementService.getUpcomingTimeOff(username, userId, year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting upcoming time off for %s - %d: %s", username, year, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Refresh tracker from worktime files (after external changes)
     * Use this when worktime files are updated outside of this service
     */
    public boolean refreshTrackerFromWorktime(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Refreshing tracker from worktime files for %s - %d", username, year));

            // Clear cache to force rebuild
            clearYear(username, year);

            // Rebuild from worktime files
            TimeOffTracker refreshedTracker = loadTrackerFromWorktimeFiles(username, userId, year);

            return refreshedTracker != null;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error refreshing tracker for %s - %d: %s", username, year, e.getMessage()));
            return false;
        }
    }

    // ========================================================================
    // CACHE MANAGEMENT
    // ========================================================================

    /**
     * Clear specific year from cache
     */
    public void clearYear(String username, int year) {
        globalLock.writeLock().lock();
        try {
            String yearKey = createYearKey(username, year);
            TimeOffCacheEntry removed = timeOffCache.remove(yearKey);
            if (removed != null) {
                removed.clear();
                LoggerUtil.info(this.getClass(), String.format("Cleared cache for %s - %d", username, year));
            }
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Clear entire cache
     */
    public void clearAllCache() {
        globalLock.writeLock().lock();
        try {
            for (TimeOffCacheEntry entry : timeOffCache.values()) {
                entry.clear();
            }
            timeOffCache.clear();
            LoggerUtil.info(this.getClass(), "Cleared entire time off cache");
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Get cache statistics for monitoring
     */
    public String getCacheStatistics() {
        globalLock.readLock().lock();
        try {
            int totalEntries = timeOffCache.size();
            long validEntries = timeOffCache.values().stream().filter(TimeOffCacheEntry::isValid).count();
            long expiredEntries = timeOffCache.values().stream().filter(TimeOffCacheEntry::isExpired).count();

            return String.format("TimeOffCache: %d total, %d valid, %d expired",
                    totalEntries, validEntries, expiredEntries);
        } finally {
            globalLock.readLock().unlock();
        }
    }

    // ========================================================================
    // PRIVATE IMPLEMENTATION METHODS
    // ========================================================================

    /**
     * Load tracker from final worktime files (source of truth)
     * This builds the yearly display layer from monthly worktime data
     */
    private TimeOffTracker loadTrackerFromWorktimeFiles(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Building tracker from final worktime files for %s - %d", username, year));

            // Use TimeOffManagementService to build tracker from worktime files
            // This reads the final, merged worktime files (already processed at login)
            TimeOffTracker tracker = timeOffManagementService.getYearTracker(username, userId, year);

            if (tracker == null) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Service returned null tracker for %s - %d", username, year));
                return null;
            }

            // Create and populate cache entry
            String yearKey = createYearKey(username, year);
            TimeOffCacheEntry cacheEntry = new TimeOffCacheEntry();
            cacheEntry.initializeFromService(username, userId, year, tracker);

            // Store in cache
            timeOffCache.put(yearKey, cacheEntry);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully built and cached tracker for %s - %d with %d requests",
                    username, year, tracker.getRequests() != null ? tracker.getRequests().size() : 0));

            return tracker;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error building tracker from worktime for %s - %d: %s", username, year, e.getMessage()), e);
            return null;
        }
    }

    /**
     * Create year key for cache indexing
     */
    private String createYearKey(String username, int year) {
        return String.format("%s-%d", username, year);
    }

    /**
     * Create empty time off summary
     */
    private TimeOffSummaryDTO createEmptySummary() {
        return TimeOffSummaryDTO.builder()
                .coDays(0)
                .snDays(0)
                .cmDays(0)
                .paidDaysTaken(0)
                .remainingPaidDays(0)
                .availablePaidDays(0)
                .build();
    }
}