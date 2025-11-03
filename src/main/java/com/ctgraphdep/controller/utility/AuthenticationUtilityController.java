package com.ctgraphdep.controller.utility;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.security.AuthenticationService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for authentication diagnostics and management utilities.
 * Provides admin-only access to log in optimization status, pending merges, and cache management.
 * All endpoints in this controller are restricted to ADMIN role only.
 */
@RestController
@RequestMapping("/utility/auth")
@PreAuthorize("hasRole('ADMIN')")
public class AuthenticationUtilityController extends BaseController {

    private final AuthenticationService authenticationService;

    public AuthenticationUtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            AuthenticationService authenticationService) {

        super(userService, folderStatus, timeValidationService);
        this.authenticationService = authenticationService;
    }

    // ========================================================================
    // AUTHENTICATION DIAGNOSTICS & MANAGEMENT ENDPOINTS
    // ========================================================================

    /**
     * Get current login optimization status
     */
    @GetMapping("/login-optimization-status")
    public ResponseEntity<Map<String, Object>> getLoginOptimizationStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            String status = authenticationService.getLoginOptimizationStatus();

            response.put("success", true);
            response.put("status", status);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting login optimization status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting login optimization status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Check if there are pending merge operations for a specific user
     */
    @GetMapping("/pending-merges/{username}")
    public ResponseEntity<Map<String, Object>> hasPendingMerges(@PathVariable String username) {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean hasPending = authenticationService.hasPendingMerges(username);

            response.put("success", true);
            response.put("username", username);
            response.put("hasPendingMerges", hasPending);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking pending merges for " + username + ": " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error checking pending merges: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get the total number of pending merge operations
     */
    @GetMapping("/pending-merge-count")
    public ResponseEntity<Map<String, Object>> getPendingMergeCount() {
        Map<String, Object> response = new HashMap<>();

        try {
            int count = authenticationService.getPendingMergeCount();

            response.put("success", true);
            response.put("pendingMergeCount", count);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting pending merge count: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting pending merge count: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Force retry of pending merges (for manual intervention)
     */
    @PostMapping("/retry-pending-merges")
    public ResponseEntity<Map<String, Object>> retryPendingMerges() {
        Map<String, Object> response = new HashMap<>();

        try {
            authenticationService.retryPendingMerges();

            response.put("success", true);
            response.put("message", "Pending merges retry initiated successfully");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Admin initiated retry of pending merges");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error retrying pending merges: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error retrying pending merges: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Force full cache refresh for a user (for manual intervention)
     */
    @PostMapping("/force-cache-refresh")
    public ResponseEntity<Map<String, Object>> forceFullCacheRefresh(@RequestParam String username) {
        Map<String, Object> response = new HashMap<>();

        try {
            authenticationService.forceFullCacheRefresh(username);

            response.put("success", true);
            response.put("message", "Full cache refresh initiated successfully for user: " + username);
            response.put("username", username);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Admin initiated full cache refresh for user: " + username);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error forcing cache refresh for " + username + ": " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error forcing cache refresh: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
