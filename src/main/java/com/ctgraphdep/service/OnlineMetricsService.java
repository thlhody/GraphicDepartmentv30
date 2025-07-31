package com.ctgraphdep.service;

import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class OnlineMetricsService {

    private final SessionStatusService sessionStatusService;
    private final AllUsersCacheService allUsersCacheService;
    private volatile int lastKnownOnlineCount = 0;
    private volatile int lastKnownActiveCount = 0;
    private volatile long lastUpdateTime = System.currentTimeMillis();

    public OnlineMetricsService(SessionStatusService sessionStatusService, AllUsersCacheService allUsersCacheService) {
        this.sessionStatusService = sessionStatusService;
        this.allUsersCacheService = allUsersCacheService;
        LoggerUtil.initialize(this.getClass(), null);
    }

//    public int getOnlineUserCount() {
//        return sessionStatusService.getOnlineUserCount();
//    }
//
//    public int getActiveUserCount() {
//        return sessionStatusService.getActiveUserCount();
//    }

    /**
     * OPTIMIZED: Get online user count without blocking dashboard
     * Returns cached value during refresh, fresh data when available
     */
    public int getOnlineUserCount() {
        try {
            // Get user statuses (this now handles refresh state internally)
            List<UserStatusDTO> userStatuses = allUsersCacheService.getAllUserStatuses();

            // Count online users (Online, Temporary Stop)
            int onlineCount = (int) userStatuses.stream()
                    .filter(user -> "Online".equals(user.getStatus()) ||
                            "Temporary Stop".equals(user.getStatus()))
                    .count();

            // Update cached values for future use
            lastKnownOnlineCount = onlineCount;
            lastUpdateTime = System.currentTimeMillis();

            LoggerUtil.debug(this.getClass(), String.format("Online user count: %d (from %d total users)",
                    onlineCount, userStatuses.size()));

            return onlineCount;

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error getting online user count, returning last known value (%d): %s",
                    lastKnownOnlineCount, e.getMessage()));
            return lastKnownOnlineCount;
        }
    }

    /**
     * OPTIMIZED: Get active user count without blocking dashboard
     * Returns cached value during refresh, fresh data when available
     */
    public int getActiveUserCount() {
        try {
            // Get user statuses (this now handles refresh state internally)
            List<UserStatusDTO> userStatuses = allUsersCacheService.getAllUserStatuses();

            // Count active users (anything except Offline)
            int activeCount = (int) userStatuses.stream()
                    .filter(user -> !"Offline".equals(user.getStatus()))
                    .count();

            // Update cached values for future use
            lastKnownActiveCount = activeCount;
            lastUpdateTime = System.currentTimeMillis();

            LoggerUtil.debug(this.getClass(), String.format("Active user count: %d (from %d total users)",
                    activeCount, userStatuses.size()));

            return activeCount;

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error getting active user count, returning last known value (%d): %s",
                    lastKnownActiveCount, e.getMessage()));
            return lastKnownActiveCount;
        }
    }

    /**
     * Get metrics status for monitoring and debugging
     */
    public String getMetricsStatus() {
        long ageMs = System.currentTimeMillis() - lastUpdateTime;
        return String.format("OnlineMetrics: %d online, %d active (updated %d ms ago)",
                lastKnownOnlineCount, lastKnownActiveCount, ageMs);
    }

    /**
     * Force refresh metrics (for manual triggers)
     */
    public void forceRefreshMetrics() {
        LoggerUtil.info(this.getClass(), "Force refreshing metrics");
        getOnlineUserCount(); // This will update both counts
        getActiveUserCount();
        LoggerUtil.info(this.getClass(), "Metrics force refreshed: " + getMetricsStatus());
    }
}
