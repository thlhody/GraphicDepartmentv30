package com.ctgraphdep.service.cache;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache service for storing calculated metrics (Standard Hours, Live Work Hours)
 * to avoid repeated calculations and improve performance.
 *
 * <p>This cache is populated at login and invalidated when:
 * <ul>
 *   <li>User updates worktime entries</li>
 *   <li>User adds/removes timeoff</li>
 *   <li>Month changes</li>
 *   <li>Manual cache invalidation</li>
 * </ul>
 */
@Service
public class MetricsCacheService {

    private static final String CACHE_KEY_FORMAT = "%s-%d-%d"; // username-year-month

    // Cache for standard work hours per user-month
    private final ConcurrentHashMap<String, Double> standardHoursCache = new ConcurrentHashMap<>();

    // Cache for live work hours per user-month
    private final ConcurrentHashMap<String, Double> liveWorkHoursCache = new ConcurrentHashMap<>();

    /**
     * Generates cache key for a specific user-month
     */
    private String getCacheKey(String username, int year, int month) {
        return String.format(CACHE_KEY_FORMAT, username, year, month);
    }

    // ======================== STANDARD HOURS CACHE ========================

    /**
     * Caches standard work hours for a user-month
     *
     * @param username The username
     * @param year The year
     * @param month The month
     * @param standardHours The calculated standard hours
     */
    public void cacheStandardHours(String username, int year, int month, double standardHours) {
        String key = getCacheKey(username, year, month);
        standardHoursCache.put(key, standardHours);
        LoggerUtil.debug(this.getClass(),
            String.format("Cached standard hours for %s (%d-%02d): %.2f hours",
                username, year, month, standardHours));
    }

    /**
     * Retrieves cached standard hours
     *
     * @param username The username
     * @param year The year
     * @param month The month
     * @return Cached standard hours, or null if not cached
     */
    public Double getCachedStandardHours(String username, int year, int month) {
        String key = getCacheKey(username, year, month);
        return standardHoursCache.get(key);
    }

    /**
     * Checks if standard hours are cached
     */
    public boolean hasStandardHoursCached(String username, int year, int month) {
        String key = getCacheKey(username, year, month);
        return standardHoursCache.containsKey(key);
    }

    // ======================== LIVE WORK HOURS CACHE ========================

    /**
     * Caches live work hours for a user-month
     *
     * @param username The username
     * @param year The year
     * @param month The month
     * @param liveWorkHours The calculated live work hours
     */
    public void cacheLiveWorkHours(String username, int year, int month, double liveWorkHours) {
        String key = getCacheKey(username, year, month);
        liveWorkHoursCache.put(key, liveWorkHours);
        LoggerUtil.debug(this.getClass(),
            String.format("Cached live work hours for %s (%d-%02d): %.2f hours",
                username, year, month, liveWorkHours));
    }

    /**
     * Retrieves cached live work hours
     *
     * @param username The username
     * @param year The year
     * @param month The month
     * @return Cached live work hours, or null if not cached
     */
    public Double getCachedLiveWorkHours(String username, int year, int month) {
        String key = getCacheKey(username, year, month);
        return liveWorkHoursCache.get(key);
    }

    /**
     * Checks if live work hours are cached
     */
    public boolean hasLiveWorkHoursCached(String username, int year, int month) {
        String key = getCacheKey(username, year, month);
        return liveWorkHoursCache.containsKey(key);
    }

    // ======================== CACHE MANAGEMENT ========================

    /**
     * Invalidates all cached metrics for a specific user-month
     *
     * @param username The username
     * @param year The year
     * @param month The month
     */
    public void invalidateMonth(String username, int year, int month) {
        String key = getCacheKey(username, year, month);
        standardHoursCache.remove(key);
        liveWorkHoursCache.remove(key);
        LoggerUtil.info(this.getClass(),
            String.format("Invalidated metrics cache for %s (%d-%02d)", username, year, month));
    }

    /**
     * Invalidates all cached metrics for a specific user (all months)
     *
     * @param username The username
     */
    public void invalidateUser(String username) {
        standardHoursCache.keySet().removeIf(key -> key.startsWith(username + "-"));
        liveWorkHoursCache.keySet().removeIf(key -> key.startsWith(username + "-"));
        LoggerUtil.info(this.getClass(),
            String.format("Invalidated all metrics cache for user: %s", username));
    }

    /**
     * Clears all cached metrics (for all users)
     */
    public void clearAll() {
        int standardCount = standardHoursCache.size();
        int liveCount = liveWorkHoursCache.size();
        standardHoursCache.clear();
        liveWorkHoursCache.clear();
        LoggerUtil.info(this.getClass(),
            String.format("Cleared all metrics cache (%d standard hours, %d live hours)",
                standardCount, liveCount));
    }

    /**
     * Gets cache statistics for monitoring
     */
    public String getCacheStats() {
        return String.format("MetricsCache: %d standard hours entries, %d live hours entries",
            standardHoursCache.size(), liveWorkHoursCache.size());
    }
}