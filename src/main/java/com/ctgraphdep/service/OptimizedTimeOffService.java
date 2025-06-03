package com.ctgraphdep.service;

import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * SIMPLIFIED OptimizedTimeOffService - Clean Architecture Adapter.
 * This service now acts as a facade over the clean architecture components.
 * The "optimization" is now handled by:
 * 1. Login merge (worktime files already final)
 * 2. Cache service (fast tracker access)
 * 3. Write-through operations (consistent updates)
 * This maintains the interface for backward compatibility while leveraging
 * the new clean architecture underneath.
 */
@Service
public class OptimizedTimeOffService {

    private final TimeOffCacheService timeOffCacheService;
    private final TimeOffManagementService timeOffManagementService;

    public OptimizedTimeOffService(
            TimeOffCacheService timeOffCacheService,
            TimeOffManagementService timeOffManagementService) {
        this.timeOffCacheService = timeOffCacheService;
        this.timeOffManagementService = timeOffManagementService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // OPTIMIZED OPERATIONS - DELEGATES TO CLEAN ARCHITECTURE
    // ========================================================================

    /**
     * Get year tracker (optimized via cache and final worktime files)
     */
    public TimeOffTracker getYearTrackerOptimized(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Getting optimized year tracker for %s - %d (via cache)", username, year));

            // Delegate to cache service (builds from final worktime files if needed)
            TimeOffTracker tracker = timeOffCacheService.getYearTracker(username, userId, year);

            if (tracker != null) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully retrieved optimized tracker for %s - %d with %d requests",
                        username, year, tracker.getRequests().size()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No tracker found for %s - %d", username, year));
            }

            return tracker;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error in optimized tracker retrieval for %s - %d: %s", username, year, e.getMessage()), e);

            // Fallback to management service
            LoggerUtil.info(this.getClass(), "Falling back to management service");
            return timeOffManagementService.getYearTracker(username, userId, year);
        }
    }

    /**
     * Add time off request (optimized via write-through cache)
     */
    public boolean addTimeOffRequestOptimized(String username, Integer userId, List<LocalDate> dates, String timeOffType) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing optimized time off request for %s - %d dates (%s)",
                    username, dates.size(), timeOffType));

            // Delegate to cache service (write-through to all layers)
            boolean success = timeOffCacheService.addTimeOffRequest(username, userId, dates, timeOffType);

            if (success) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Optimized time off request processed successfully for %s: %d days",
                        username, dates.size()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Optimized time off request failed for %s: %d days", username, dates.size()));
            }

            return success;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error in optimized time off request for %s: %s", username, e.getMessage()), e);

            // Fallback to management service
            LoggerUtil.info(this.getClass(), "Falling back to management service");
            return timeOffManagementService.addTimeOffRequest(username, userId, dates, timeOffType);
        }
    }

    /**
     * Calculate time off summary (optimized via cache)
     */
    public TimeOffSummaryDTO calculateTimeOffSummaryOptimized(String username, Integer userId, int year) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Calculating optimized summary for %s - %d", username, year));

            // Delegate to cache service (fast tracker-based calculation)
            TimeOffSummaryDTO summary = timeOffCacheService.getTimeOffSummary(username, userId, year);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Optimized summary calculated for %s - %d: %d CO days, %d available",
                    username, year, summary.getCoDays(), summary.getAvailablePaidDays()));

            return summary;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error calculating optimized summary for %s - %d: %s", username, year, e.getMessage()));

            // Fallback to management service
            return timeOffManagementService.calculateTimeOffSummary(username, userId, year);
        }
    }

    /**
     * Refresh tracker from worktime files (for manual refresh)
     */
    public boolean refreshYearTrackerCache(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Refreshing year tracker cache for %s - %d", username, year));

            // Use cache service refresh method
            boolean success = timeOffCacheService.refreshTrackerFromWorktime(username, userId, year);

            if (success) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully refreshed tracker cache for %s - %d", username, year));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to refresh tracker cache for %s - %d", username, year));
            }

            return success;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error refreshing tracker cache for %s - %d: %s", username, year, e.getMessage()));
            return false;
        }
    }

    /**
     * Clear optimization cache (for maintenance)
     */
    public void clearOptimizationCache(String username, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Clearing optimization cache for %s - %d", username, year));

            timeOffCacheService.clearYear(username, year);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully cleared cache for %s - %d", username, year));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error clearing cache for %s - %d: %s", username, year, e.getMessage()));
        }
    }

    /**
     * Get optimization statistics
     */
    public String getOptimizationStatistics() {
        try {
            return timeOffCacheService.getCacheStatistics();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting optimization statistics: " + e.getMessage());
            return "Error retrieving statistics";
        }
    }

    // ========================================================================
    // DIAGNOSTIC METHODS
    // ========================================================================

    /**
     * Check if optimization is working properly
     */
    public boolean isOptimizationHealthy() {
        try {
            // Simple health check - try to get cache statistics
            String stats = getOptimizationStatistics();
            return stats != null && !stats.contains("Error");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Optimization health check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Force optimization refresh (for troubleshooting)
     */
    public boolean forceOptimizationRefresh(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Forcing optimization refresh for %s - %d", username, year));

            // Clear and rebuild
            clearOptimizationCache(username, year);
            TimeOffTracker tracker = getYearTrackerOptimized(username, userId, year);

            boolean success = tracker != null;

            LoggerUtil.info(this.getClass(), String.format(
                    "Force refresh %s for %s - %d", success ? "succeeded" : "failed", username, year));

            return success;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error in force refresh for %s - %d: %s", username, year, e.getMessage()));
            return false;
        }
    }
}