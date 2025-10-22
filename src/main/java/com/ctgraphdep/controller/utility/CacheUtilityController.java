package com.ctgraphdep.controller.utility;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
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

    public CacheUtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            AllUsersCacheService allUsersCacheService,
            SessionMidnightHandler sessionMidnightHandler) {

        super(userService, folderStatus, timeValidationService);
        this.allUsersCacheService = allUsersCacheService;
        this.sessionMidnightHandler = sessionMidnightHandler;
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
}
