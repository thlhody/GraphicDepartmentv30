package com.ctgraphdep.controller;

import com.ctgraphdep.security.LoginMergeCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API for checking background merge status.
 * Provides endpoints for frontend to monitor background merge operations.
 */
@RestController
@RequestMapping("/api/auth")
public class MergeStatusController {

    private final LoginMergeCacheService loginMergeCacheService;

    // Simple in-memory tracking of background merges
    // In production, you might want to use Redis or database
    private final ConcurrentHashMap<String, MergeStatus> userMergeStatus = new ConcurrentHashMap<>();

    public MergeStatusController(LoginMergeCacheService loginMergeCacheService) {
        this.loginMergeCacheService = loginMergeCacheService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Check if background merge is complete for current user
     */
    @GetMapping("/merge-status")
    public ResponseEntity<Map<String, Object>> getMergeStatus() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String username = auth.getName();
            Map<String, Object> response = new HashMap<>();

            // Check if this is first login (would trigger background merge)
            boolean isFirstLogin = loginMergeCacheService.getCurrentLoginCount() == 1;

            if (!isFirstLogin) {
                // Not first login, no background merge needed
                response.put("mergeComplete", true);
                response.put("mergeNeeded", false);
                response.put("message", "No background merge needed");
                return ResponseEntity.ok(response);
            }

            // Check merge status for this user
            MergeStatus status = userMergeStatus.get(username);

            if (status == null) {
                // No status tracked yet - assume merge just started
                userMergeStatus.put(username, new MergeStatus(username, System.currentTimeMillis()));
                response.put("mergeComplete", false);
                response.put("mergeNeeded", true);
                response.put("message", "Background merge in progress");
                response.put("estimatedTimeRemaining", 8); // seconds
            } else {
                long elapsedSeconds = (System.currentTimeMillis() - status.startTime) / 1000;

                if (elapsedSeconds >= 10 || status.completed) {
                    // Assume complete after 10 seconds or if explicitly marked
                    status.completed = true;
                    response.put("mergeComplete", true);
                    response.put("mergeNeeded", true);
                    response.put("message", "Background merge completed");
                    response.put("elapsedTime", elapsedSeconds);

                    // Clean up status after reporting completion
                    userMergeStatus.remove(username);
                } else {
                    response.put("mergeComplete", false);
                    response.put("mergeNeeded", true);
                    response.put("message", "Background merge in progress");
                    response.put("elapsedTime", elapsedSeconds);
                    response.put("estimatedTimeRemaining", Math.max(1, 10 - elapsedSeconds));
                }
            }

            LoggerUtil.debug(this.getClass(), String.format("Merge status for %s: %s", username, response));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting merge status: " + e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("mergeComplete", true); // Assume complete on error
            errorResponse.put("mergeNeeded", false);
            errorResponse.put("message", "Status check failed");

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Get login optimization status
     */
    @GetMapping("/login-status")
    public ResponseEntity<Map<String, Object>> getLoginStatus() {
        try {
            Map<String, Object> response = new HashMap<>();

            response.put("loginCount", loginMergeCacheService.getCurrentLoginCount());
            response.put("isFirstLogin", loginMergeCacheService.isInInitialState() || loginMergeCacheService.getCurrentLoginCount() == 1);
            response.put("shouldPerformFullMerge", loginMergeCacheService.shouldPerformFullMerge());
            response.put("shouldPerformFastRefresh", loginMergeCacheService.shouldPerformFastCacheRefresh());
            response.put("status", loginMergeCacheService.getStatus());
            response.put("performanceBenefit", loginMergeCacheService.getPerformanceBenefit());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting login status: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Mark merge as complete for a user (can be called by AuthenticationService when merge finishes)
     */
    public void markMergeComplete(String username) {
        MergeStatus status = userMergeStatus.get(username);
        if (status != null) {
            status.completed = true;
            LoggerUtil.info(this.getClass(), String.format("Marked background merge complete for user: %s", username));
        }
    }

    // ========================================================================
    // ADD THIS METHOD TO MergeStatusController.java
    // ========================================================================

    /**
     * Mark merge as started for a user (called by AuthenticationService when merge begins)
     */
    public void markMergeStarted(String username) {
        MergeStatus status = new MergeStatus(username, System.currentTimeMillis());
        userMergeStatus.put(username, status);
        LoggerUtil.info(this.getClass(), String.format("Marked background merge started for user: %s", username));
    }

    /**
     * Simple class to track merge status
     */
    private static class MergeStatus {
        final String username;
        final long startTime;
        boolean completed;

        MergeStatus(String username, long startTime) {
            this.username = username;
            this.startTime = startTime;
            this.completed = false;
        }
    }
}