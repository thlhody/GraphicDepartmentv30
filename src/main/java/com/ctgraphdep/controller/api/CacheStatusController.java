// CREATE new REST controller for cache status

package com.ctgraphdep.controller.api;

import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API for cache status monitoring and dashboard refresh triggers
 */
@RestController
@RequestMapping("/api/cache")
public class CacheStatusController {

    private final AllUsersCacheService allUsersCacheService;

    public CacheStatusController(AllUsersCacheService allUsersCacheService) {
        this.allUsersCacheService = allUsersCacheService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Get current cache refresh status for dashboard auto-refresh
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("isRefreshing", allUsersCacheService.isRefreshing());
            status.put("status", allUsersCacheService.getRefreshStatus());
            status.put("userCount", allUsersCacheService.getCachedUserCount());
            status.put("timestamp", System.currentTimeMillis());

            LoggerUtil.debug(this.getClass(), "Cache status requested: " + status.get("status"));

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting cache status: " + e.getMessage());

            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("isRefreshing", false);
            errorStatus.put("status", "ERROR");
            errorStatus.put("userCount", 0);
            errorStatus.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(errorStatus);
        }
    }

    /**
     * Get fresh dashboard metrics (for auto-refresh)
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getFreshMetrics() {
        try {
            int onlineUsers = allUsersCacheService.getAllUserStatuses().stream()
                    .mapToInt(user -> "Online".equals(user.getStatus()) ? 1 : 0)
                    .sum();

            int totalUsers = allUsersCacheService.getCachedUserCount();

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("onlineUsers", onlineUsers);
            metrics.put("activeUsers", onlineUsers); // Same for now
            metrics.put("totalUsers", totalUsers);
            metrics.put("isRefreshing", allUsersCacheService.isRefreshing());
            metrics.put("lastUpdate", LocalDateTime.now().toString());

            LoggerUtil.debug(this.getClass(), "Fresh metrics requested: " + onlineUsers + " online users");

            return ResponseEntity.ok(metrics);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting fresh metrics: " + e.getMessage());

            Map<String, Object> errorMetrics = new HashMap<>();
            errorMetrics.put("onlineUsers", 0);
            errorMetrics.put("activeUsers", 0);
            errorMetrics.put("totalUsers", 0);
            errorMetrics.put("isRefreshing", false);
            errorMetrics.put("lastUpdate", LocalDateTime.now().toString());

            return ResponseEntity.ok(errorMetrics);
        }
    }
}