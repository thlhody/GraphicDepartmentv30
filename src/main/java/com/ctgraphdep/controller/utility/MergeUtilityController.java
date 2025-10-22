package com.ctgraphdep.controller.utility;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.merge.login.LoginMergeStrategy;
import com.ctgraphdep.merge.login.interfaces.LoginMergeService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for merge operation utilities.
 * Handles pending merge status, merge strategy, and merge operation management.
 */
@RestController
@RequestMapping("/utility/merge")
public class MergeUtilityController extends BaseController {

    private final LoginMergeService loginMergeService;
    private final LoginMergeStrategy loginMergeStrategy;

    public MergeUtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            LoginMergeService loginMergeService,
            LoginMergeStrategy loginMergeStrategy) {

        super(userService, folderStatus, timeValidationService);
        this.loginMergeService = loginMergeService;
        this.loginMergeStrategy = loginMergeStrategy;
    }

    // ========================================================================
    // MERGE MANAGEMENT ENDPOINTS
    // ========================================================================

    /**
     * Get pending merge status for current user
     * Checks if there are pending merge operations stuck in the queue
     */
    @GetMapping("/pending-status")
    public ResponseEntity<Map<String, Object>> getPendingMergeStatus(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            String username = currentUser.getUsername();
            boolean hasPendingMerges = loginMergeService.hasPendingMerges(username);
            int pendingCount = loginMergeService.getPendingMergeCount();

            response.put("success", true);
            response.put("username", username);
            response.put("hasPendingMerges", hasPendingMerges);
            response.put("pendingCount", pendingCount);
            response.put("timestamp", getStandardCurrentDateTime());

            // Warning message if user has pending merges
            if (hasPendingMerges) {
                response.put("warning", "You have pending merge operations that may need clearing if stuck");
                response.put("action", "Consider using the Clear Pending Merges utility if these operations are not completing");
            } else {
                response.put("message", "No pending merge operations");
            }

            LoggerUtil.info(this.getClass(), String.format("Pending merge status checked for user %s: %d pending",
                    username, pendingCount));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting pending merge status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting pending merge status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get total count of pending merge operations
     * Useful for dashboard or status indicators
     */
    @GetMapping("/pending-count")
    public ResponseEntity<Map<String, Object>> getPendingMergeCount() {
        Map<String, Object> response = new HashMap<>();

        try {
            int pendingCount = loginMergeService.getPendingMergeCount();

            response.put("success", true);
            response.put("pendingCount", pendingCount);
            response.put("message", pendingCount > 0
                ? String.format("Found %d pending merge operation(s)", pendingCount)
                : "No pending merge operations");
            response.put("timestamp", getStandardCurrentDateTime());

            // Add severity level based on count
            if (pendingCount > 10) {
                response.put("severity", "high");
                response.put("recommendation", "High number of pending merges - consider clearing queue");
            } else if (pendingCount > 5) {
                response.put("severity", "medium");
                response.put("recommendation", "Moderate pending merges - monitor if they complete");
            } else if (pendingCount > 0) {
                response.put("severity", "low");
                response.put("recommendation", "Low pending merges - should complete automatically");
            } else {
                response.put("severity", "none");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting pending merge count: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting pending merge count: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Clear all pending merge operations
     * Use this when merge operations are stuck and not completing
     * WARNING: Only use if merges are confirmed stuck/failed
     */
    @PostMapping("/clear-pending")
    public ResponseEntity<Map<String, Object>> clearPendingMerges(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            int countBeforeClear = loginMergeService.getPendingMergeCount();

            // Clear all pending merges
            loginMergeService.clearPendingMerges();

            response.put("success", true);
            response.put("message", "All pending merge operations have been cleared");
            response.put("clearedCount", countBeforeClear);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.warn(this.getClass(), String.format("User %s cleared %d pending merge operations",
                    currentUser.getUsername(), countBeforeClear));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error clearing pending merges: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error clearing pending merges: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get login merge strategy status
     * Shows current login count and next merge strategy
     */
    @GetMapping("/strategy-status")
    public ResponseEntity<Map<String, Object>> getMergeStrategyStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            int loginCount = loginMergeStrategy.getCurrentLoginCount();
            boolean shouldPerformFullMerge = loginMergeStrategy.shouldPerformFullMerge();
            boolean shouldPerformFastRefresh = loginMergeStrategy.shouldPerformFastCacheRefresh();
            boolean isFirstLogin = loginMergeStrategy.isFirstLoginOfDay();
            String statusDescription = loginMergeStrategy.getStatus();
            String performanceBenefit = loginMergeStrategy.getPerformanceBenefit();

            response.put("success", true);
            response.put("loginCount", loginCount);
            response.put("shouldPerformFullMerge", shouldPerformFullMerge);
            response.put("shouldPerformFastRefresh", shouldPerformFastRefresh);
            response.put("isFirstLogin", isFirstLogin);
            response.put("statusDescription", statusDescription);
            response.put("performanceBenefit", performanceBenefit);
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Merge strategy status retrieved: " + statusDescription);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting merge strategy status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting merge strategy status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Force full merge on next login
     * Resets login count to 0, causing next login to perform full merge
     */
    @PostMapping("/force-full-merge")
    public ResponseEntity<Map<String, Object>> forceFullMergeOnNextLogin(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            int beforeCount = loginMergeStrategy.getCurrentLoginCount();

            // Force full merge on next login
            loginMergeStrategy.forceFullMergeOnNextLogin();

            response.put("success", true);
            response.put("message", "Next login will perform full merge (data refresh)");
            response.put("previousLoginCount", beforeCount);
            response.put("newLoginCount", loginMergeStrategy.getCurrentLoginCount());
            response.put("nextStrategy", "Full Merge (~7 seconds)");
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.warn(this.getClass(), String.format("User %s forced full merge on next login (reset from count %d to 0)",
                    currentUser.getUsername(), beforeCount));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error forcing full merge: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error forcing full merge: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Trigger full merge immediately
     * Sets login count to 1, simulating first login
     * WARNING: This sets the counter to 1, not 0!
     */
    @PostMapping("/trigger-merge-now")
    public ResponseEntity<Map<String, Object>> triggerFullMergeNow(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Trigger full merge now
            loginMergeStrategy.triggerFullMergeNow();

            response.put("success", true);
            response.put("message", "Merge strategy set to trigger full merge");
            response.put("currentLoginCount", loginMergeStrategy.getCurrentLoginCount());
            response.put("willPerformFullMerge", loginMergeStrategy.shouldPerformFullMerge());
            response.put("status", loginMergeStrategy.getStatus());
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "User " + currentUser.getUsername() + " triggered full merge strategy");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error triggering full merge: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error triggering full merge: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
