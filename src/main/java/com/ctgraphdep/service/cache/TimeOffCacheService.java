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
 * REFACTORED TimeOffCacheService - Pure Caching Layer.
 * Key Principles (following RegisterCacheService pattern):
 * - Pure caching, no business logic
 * - Write-through: cache updates immediately trigger service operations
 * - Year-based memory management (yearly tracker files)
 * - Thread-safe operations using TimeOffCacheEntry
 * - Delegates ALL business logic to TimeOffManagementService
 * Cache Key Pattern: "username-year" (similar to RegisterCache's "username-year-month")
 * Responsibilities:
 * 1. Cache yearly tracker data for fast access
 * 2. Write-through operations for updates
 * 3. Cache invalidation and management
 * 4. Thread-safe concurrent access
 * What this service does NOT do:
 * - No worktime scanning or merging (TimeOffManagementService handles this)
 * - No holiday balance calculations (TimeOffManagementService handles this)
 * - No file operations (TimeOffManagementService → TimeOffDataService handles this)
 * - No business validation (TimeOffManagementService handles this)
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
    // MAIN CACHE OPERATIONS
    // ========================================================================

    /**
     * Get time off tracker for a specific year (loads via service if not cached).
     * Main entry point - follows the exact same pattern as RegisterCacheService.getMonthEntries()
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return Time off tracker for the year
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

            // Cache miss or expired - load via service (which handles all business logic)
            LoggerUtil.info(this.getClass(), String.format("Loading time off tracker via service for %s - %d", username, year));
            return loadYearFromService(username, userId, year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting year tracker for %s - %d: %s", username, year, e.getMessage()), e);
            return null;
        }
    }

    /**
     * Add time off request with write-through.
     * Follows same pattern as RegisterCacheService.addEntry()
     * @param username Username
     * @param userId User ID
     * @param dates List of dates for time off
     * @param timeOffType Time off type (CO, SN, CM)
     * @return true if request was added successfully
     */
    public boolean addTimeOffRequest(String username, Integer userId, List<LocalDate> dates, String timeOffType) {
        try {
            if (dates == null || dates.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Cannot add time off request with empty dates");
                return false;
            }

            int year = dates.get(0).getYear();
            String yearKey = createYearKey(username, year);

            LoggerUtil.info(this.getClass(), String.format("Adding time off request for %s - %d dates, type %s", username, dates.size(), timeOffType));

            // Write-through: delegate to service for business logic and persistence
            boolean success = timeOffManagementService.addTimeOffRequest(username, userId, dates, timeOffType);

            if (!success) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to add time off request via service for %s", username));
                return false;
            }

            // Invalidate cache to force reload with fresh data on next access
            clearYear(username, year);

            LoggerUtil.info(this.getClass(), String.format("Successfully added %d time off requests for %s - %d (%s)", dates.size(), username, year, timeOffType));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error adding time off request for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Get time off summary from cache (delegates to service if cache miss).
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return Time off summary
     */
    public TimeOffSummaryDTO getTimeOffSummary(String username, Integer userId, int year) {
        try {
            // Get tracker from cache (or load via service)
            TimeOffTracker tracker = getYearTracker(username, userId, year);

            if (tracker == null) {
                LoggerUtil.debug(this.getClass(), String.format("No tracker found for summary calculation %s - %d", username, year));
                return createEmptySummary();
            }

            // Delegate summary calculation to service
            return timeOffManagementService.calculateTimeOffSummary(username, userId, year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting time off summary for %s - %d: %s", username, year, e.getMessage()));
            return createEmptySummary();
        }
    }

    /**
     * Get upcoming time off from cache (delegates to service if cache miss).
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return List of upcoming time off entries
     */
    public List<WorkTimeTable> getUpcomingTimeOff(String username, Integer userId, int year) {
        try {
            // Get tracker from cache (or load via service)
            TimeOffTracker tracker = getYearTracker(username, userId, year);

            if (tracker == null) {
                LoggerUtil.debug(this.getClass(), String.format("No tracker found for upcoming time off %s - %d", username, year));
                return new ArrayList<>();
            }

            // Delegate to service for business logic
            return timeOffManagementService.getUpcomingTimeOff(username, userId, year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting upcoming time off for %s - %d: %s", username, year, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // CACHE MANAGEMENT
    // ========================================================================

    /**
     * Clear specific year from cache.
     * Same pattern as RegisterCacheService.clearMonth()
     *
     * @param username Username
     * @param year Year
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
     * Clear entire cache.
     * Same pattern as RegisterCacheService.clearAllCache()
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
     * Get cache statistics for monitoring.
     *
     * @return Cache statistics
     */
    public String getCacheStatistics() {
        globalLock.readLock().lock();
        try {
            int totalEntries = timeOffCache.size();
            long validEntries = timeOffCache.values().stream().filter(TimeOffCacheEntry::isValid).count();
            long expiredEntries = timeOffCache.values().stream().filter(TimeOffCacheEntry::isExpired).count();

            return String.format("TimeOffCache: %d total, %d valid, %d expired", totalEntries, validEntries, expiredEntries);
        } finally {
            globalLock.readLock().unlock();
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Load year data from service into cache.
     * Same pattern as RegisterCacheService.loadMonthFromFile()
     */
    private TimeOffTracker loadYearFromService(String username, Integer userId, int year) {
        try {
            // Delegate to service for all business logic (worktime merge, etc.)
            TimeOffTracker tracker = timeOffManagementService.getYearTracker(username, userId, year);

            if (tracker == null) {
                LoggerUtil.debug(this.getClass(), String.format("Service returned null tracker for %s - %d", username, year));
                return null;
            }

            // Create and populate cache entry
            String yearKey = createYearKey(username, year);
            TimeOffCacheEntry cacheEntry = new TimeOffCacheEntry();
            cacheEntry.initializeFromService(username, userId, year, tracker);

            // Store in cache
            timeOffCache.put(yearKey, cacheEntry);

            LoggerUtil.info(this.getClass(), String.format("Successfully loaded and cached tracker for %s - %d with %d requests",
                    username, year, tracker.getRequests() != null ? tracker.getRequests().size() : 0));

            return tracker;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading year from service for %s - %d: %s", username, year, e.getMessage()), e);
            return null;
        }
    }

    /**
     * Create year key for cache indexing.
     * Same pattern as RegisterCacheService.createMonthKey()
     */
    private String createYearKey(String username, int year) {
        return String.format("%s-%d", username, year);
    }

    /**
     * Create empty time off summary.
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