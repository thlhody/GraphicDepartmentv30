package com.ctgraphdep.controller.utility;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.*;
import com.ctgraphdep.session.service.SessionMidnightHandler;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for cache management utilities.
 * Handles cache status, validation, refresh, and emergency reset operations.
 */
@RestController
@RequestMapping("/utility/cache")
public class CacheUtilityController extends BaseController {

    private final AllUsersCacheService allUsersCacheService;
    private final SessionMidnightHandler sessionMidnightHandler;
    private final MetricsCacheService metricsCacheService;
    private final SessionCacheService sessionCacheService;
    private final TimeOffCacheService timeOffCacheService;
    private final WorktimeCacheService worktimeCacheService;
    private final RegisterCheckCacheService registerCheckCacheService;

    public CacheUtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            AllUsersCacheService allUsersCacheService,
            SessionMidnightHandler sessionMidnightHandler,
            MetricsCacheService metricsCacheService,
            SessionCacheService sessionCacheService,
            TimeOffCacheService timeOffCacheService,
            WorktimeCacheService worktimeCacheService,
            RegisterCheckCacheService registerCheckCacheService) {

        super(userService, folderStatus, timeValidationService);
        this.allUsersCacheService = allUsersCacheService;
        this.sessionMidnightHandler = sessionMidnightHandler;
        this.metricsCacheService = metricsCacheService;
        this.sessionCacheService = sessionCacheService;
        this.timeOffCacheService = timeOffCacheService;
        this.worktimeCacheService = worktimeCacheService;
        this.registerCheckCacheService = registerCheckCacheService;
    }

    // ========================================================================
    // CACHE MANAGEMENT ENDPOINTS
    // ========================================================================

    /**
     * Get status cache health information
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            String cacheStatus = allUsersCacheService.getCacheStatus();

            response.put("success", true);
            response.put("cacheStatus", cacheStatus);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting cache status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting cache status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Perform manual cache validation
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateCache() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Get current cache status for validation
            String cacheStatus = allUsersCacheService.getCacheStatus();
            boolean hasUserData = allUsersCacheService.hasUserData();
            int cachedUserCount = allUsersCacheService.getCachedUserCount();

            response.put("success", true);
            response.put("message", "Cache validation completed");
            response.put("hasUserData", hasUserData);
            response.put("cachedUserCount", cachedUserCount);
            response.put("cacheStatus", cacheStatus);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Manual cache validation performed - Users: " + cachedUserCount);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error validating cache: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error validating cache: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Check if cache has user data
     */
    @GetMapping("/user-data-check")
    public ResponseEntity<Map<String, Object>> checkUserData() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean hasUserData = allUsersCacheService.hasUserData();
            int cachedUserCount = allUsersCacheService.getCachedUserCount();

            response.put("success", true);
            response.put("hasUserData", hasUserData);
            response.put("cachedUserCount", cachedUserCount);
            response.put("message", hasUserData ? "Cache contains user data" : "Cache is empty");
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking user data: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error checking user data: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get cached user count
     */
    @GetMapping("/user-count")
    public ResponseEntity<Map<String, Object>> getCachedUserCount() {
        Map<String, Object> response = new HashMap<>();

        try {
            int cachedUserCount = allUsersCacheService.getCachedUserCount();

            response.put("success", true);
            response.put("cachedUserCount", cachedUserCount);
            response.put("message", "Found " + cachedUserCount + " cached users");
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting cached user count: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting cached user count: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Refresh cache from UserService data
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshCache() {
        Map<String, Object> response = new HashMap<>();

        try {
            int beforeCount = allUsersCacheService.getCachedUserCount();

            // Refresh all users from UserDataService
            allUsersCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();
            allUsersCacheService.writeToFile();

            int afterCount = allUsersCacheService.getCachedUserCount();

            response.put("success", true);
            response.put("message", "Cache refreshed successfully");
            response.put("beforeCount", beforeCount);
            response.put("afterCount", afterCount);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), String.format("Cache refreshed: %d → %d users", beforeCount, afterCount));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error refreshing cache: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error refreshing cache: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Emergency cache reset
     */
    @PostMapping("/emergency-reset")
    public ResponseEntity<Map<String, Object>> emergencyCacheReset(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Perform emergency cache reset using SessionMidnightHandler
            sessionMidnightHandler.performEmergencyCacheReset();

            response.put("success", true);
            response.put("message", "Emergency cache reset completed successfully");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.warn(this.getClass(), "Emergency cache reset performed by user: " + currentUser.getUsername());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error performing emergency cache reset: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error performing emergency cache reset: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // METRICS CACHE SERVICE ENDPOINTS
    // ========================================================================

    /**
     * Get metrics cache statistics
     */
    @GetMapping("/metrics/status")
    public ResponseEntity<Map<String, Object>> getMetricsCacheStats() {
        Map<String, Object> response = new HashMap<>();

        try {
            String stats = metricsCacheService.getCacheStats();

            response.put("success", true);
            response.put("stats", stats);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting metrics cache stats: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting metrics cache stats: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Check if standard/live hours are cached for a user
     */
    @GetMapping("/metrics/check/{year}/{month}")
    public ResponseEntity<Map<String, Object>> checkHoursCached(
            @RequestParam String username,
            @PathVariable int year,
            @PathVariable int month) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean hasStandardHours = metricsCacheService.hasStandardHoursCached(username, year, month);
            boolean hasLiveHours = metricsCacheService.hasLiveWorkHoursCached(username, year, month);

            response.put("success", true);
            response.put("username", username);
            response.put("year", year);
            response.put("month", month);
            response.put("hasStandardHours", hasStandardHours);
            response.put("hasLiveHours", hasLiveHours);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking cached hours: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error checking cached hours: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Invalidate metrics cache for a specific user
     */
    @PostMapping("/metrics/invalidate/user")
    public ResponseEntity<Map<String, Object>> invalidateMetricsUser(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();

        try {
            metricsCacheService.invalidateUser(username);

            response.put("success", true);
            response.put("message", "Metrics cache invalidated for user: " + username);
            response.put("username", username);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Metrics cache invalidated for user: " + username);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error invalidating metrics cache for user: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error invalidating metrics cache: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Clear all metrics cache
     */
    @PostMapping("/metrics/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllMetricsCache() {
        Map<String, Object> response = new HashMap<>();

        try {
            metricsCacheService.clearAll();

            response.put("success", true);
            response.put("message", "All metrics cache cleared successfully");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.warn(this.getClass(), "All metrics cache cleared");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error clearing all metrics cache: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error clearing all metrics cache: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // SESSION CACHE SERVICE ENDPOINTS
    // ========================================================================

    /**
     * Check session cache health for a user
     */
    @GetMapping("/session/health")
    public ResponseEntity<Map<String, Object>> checkSessionCacheHealth(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isHealthy = sessionCacheService.isSessionCacheHealthy(username);

            response.put("success", true);
            response.put("username", username);
            response.put("isHealthy", isHealthy);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking session cache health: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error checking session cache health: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // TIME OFF CACHE SERVICE ENDPOINTS
    // ========================================================================

    /**
     * Cleanup expired time off sessions
     */
    @PostMapping("/timeoff/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupExpiredTimeOffSessions() {
        Map<String, Object> response = new HashMap<>();

        try {
            timeOffCacheService.cleanupExpiredSessions();

            response.put("success", true);
            response.put("message", "Expired time off sessions cleaned up successfully");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Expired time off sessions cleaned up");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error cleaning up expired time off sessions: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error cleaning up expired time off sessions: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get time off summary for a user
     */
    @GetMapping("/timeoff/summary")
    public ResponseEntity<Map<String, Object>> getTimeOffSummary(
            @RequestParam String username,
            @RequestParam int year) {
        Map<String, Object> response = new HashMap<>();

        try {
            TimeOffSummaryDTO summary = timeOffCacheService.getSummary(username, year);

            response.put("success", true);
            response.put("username", username);
            response.put("year", year);
            response.put("summary", summary);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting time off summary: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting time off summary: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Check if user has active time off session
     */
    @GetMapping("/timeoff/active-check")
    public ResponseEntity<Map<String, Object>> hasActiveTimeOffSession(
            @RequestParam String username,
            @RequestParam int year) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean hasActive = timeOffCacheService.hasActiveSession(username, year);

            response.put("success", true);
            response.put("username", username);
            response.put("year", year);
            response.put("hasActiveSession", hasActive);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking active time off session: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error checking active time off session: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get time off cache statistics
     */
    @GetMapping("/timeoff/statistics")
    public ResponseEntity<Map<String, Object>> getTimeOffCacheStatistics() {
        Map<String, Object> response = new HashMap<>();

        try {
            String statistics = timeOffCacheService.getCacheStatistics();

            response.put("success", true);
            response.put("statistics", statistics);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting time off cache statistics: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting time off cache statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // WORKTIME CACHE SERVICE ENDPOINTS
    // ========================================================================

    /**
     * Switch user to a different month
     */
    @PostMapping("/worktime/switch-month")
    public ResponseEntity<Map<String, Object>> switchUserToMonth(
            @RequestParam String username,
            @RequestParam Integer userId,
            @RequestParam int newYear,
            @RequestParam int newMonth) {
        Map<String, Object> response = new HashMap<>();

        try {
            worktimeCacheService.switchUserToMonth(username, userId, newYear, newMonth);

            response.put("success", true);
            response.put("message", "User switched to " + newYear + "/" + newMonth);
            response.put("username", username);
            response.put("newYear", newYear);
            response.put("newMonth", newMonth);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), String.format("User %s switched to %d/%d", username, newYear, newMonth));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error switching user to month: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error switching user to month: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Invalidate all worktime sessions for a user
     */
    @PostMapping("/worktime/invalidate-all")
    public ResponseEntity<Map<String, Object>> invalidateAllWorktimeUserSessions(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();

        try {
            worktimeCacheService.invalidateAllUserSessions(username);

            response.put("success", true);
            response.put("message", "All worktime sessions invalidated for user: " + username);
            response.put("username", username);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "All worktime sessions invalidated for user: " + username);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error invalidating all worktime sessions: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error invalidating all worktime sessions: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Cleanup expired worktime sessions
     */
    @PostMapping("/worktime/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupExpiredWorktimeSessions() {
        Map<String, Object> response = new HashMap<>();

        try {
            worktimeCacheService.cleanupExpiredSessions();

            response.put("success", true);
            response.put("message", "Expired worktime sessions cleaned up successfully");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Expired worktime sessions cleaned up");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error cleaning up expired worktime sessions: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error cleaning up expired worktime sessions: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Check if user has active month session
     */
    @GetMapping("/worktime/active-check")
    public ResponseEntity<Map<String, Object>> hasActiveWorktimeMonthSession(
            @RequestParam String username,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean hasActive = worktimeCacheService.hasActiveMonthSession(username, year, month);

            response.put("success", true);
            response.put("username", username);
            response.put("year", year);
            response.put("month", month);
            response.put("hasActiveMonthSession", hasActive);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking active month session: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error checking active month session: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get worktime cache statistics
     */
    @GetMapping("/worktime/statistics")
    public ResponseEntity<Map<String, Object>> getWorktimeCacheStatistics() {
        Map<String, Object> response = new HashMap<>();

        try {
            String statistics = worktimeCacheService.getCacheStatistics();

            response.put("success", true);
            response.put("statistics", statistics);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting worktime cache statistics: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting worktime cache statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get user cache status
     */
    @GetMapping("/worktime/user-status")
    public ResponseEntity<Map<String, Object>> getWorktimeUserCacheStatus(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();

        try {
            String status = worktimeCacheService.getUserCacheStatus(username);

            response.put("success", true);
            response.put("username", username);
            response.put("status", status);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting user cache status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting user cache status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Force refresh month from file
     */
    @PostMapping("/worktime/force-refresh")
    public ResponseEntity<Map<String, Object>> forceRefreshWorktimeMonthFromFile(
            @RequestParam String username,
            @RequestParam Integer userId,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();

        try {
            worktimeCacheService.forceRefreshMonthFromFile(username, userId, year, month);

            response.put("success", true);
            response.put("message", "Worktime month refreshed from file");
            response.put("username", username);
            response.put("year", year);
            response.put("month", month);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), String.format("Force refreshed worktime for %s: %d/%d", username, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error force refreshing worktime month: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error force refreshing worktime month: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // REGISTER CHECK CACHE SERVICE ENDPOINTS
    // ========================================================================

    /**
     * Set current user in register check cache
     */
    @PostMapping("/register-check/set-user")
    public ResponseEntity<Map<String, Object>> setRegisterCheckCurrentUser(
            @RequestParam String username,
            @RequestParam Integer userId) {
        Map<String, Object> response = new HashMap<>();

        try {
            registerCheckCacheService.setCurrentUser(username, userId);

            response.put("success", true);
            response.put("message", "Current user set in register check cache");
            response.put("username", username);
            response.put("userId", userId);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), String.format("Register check cache current user set to: %s (%d)", username, userId));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error setting current user in register check cache: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error setting current user: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Delete entry from register check cache
     */
    @DeleteMapping("/register-check/entry")
    public ResponseEntity<Map<String, Object>> deleteRegisterCheckEntry(
            @RequestParam String username,
            @RequestParam Integer userId,
            @RequestParam Integer entryId,
            @RequestParam int year,
            @RequestParam int month) {
        Map<String, Object> response = new HashMap<>();

        try {
            registerCheckCacheService.deleteEntry(username, userId, entryId, year, month);

            response.put("success", true);
            response.put("message", "Entry deleted from register check cache");
            response.put("username", username);
            response.put("entryId", entryId);
            response.put("year", year);
            response.put("month", month);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), String.format("Deleted register check entry %d for %s: %d/%d", entryId, username, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error deleting register check entry: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error deleting register check entry: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Clear all register check cache
     */
    @PostMapping("/register-check/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllRegisterCheckCache() {
        Map<String, Object> response = new HashMap<>();

        try {
            registerCheckCacheService.clearAllCache();

            response.put("success", true);
            response.put("message", "All register check cache cleared successfully");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.warn(this.getClass(), "All register check cache cleared");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error clearing all register check cache: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error clearing all register check cache: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get register check cache status
     */
    @GetMapping("/register-check/status")
    public ResponseEntity<Map<String, Object>> getRegisterCheckCacheStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            String status = registerCheckCacheService.getCacheStatus();

            response.put("success", true);
            response.put("status", status);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting register check cache status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting register check cache status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
