package com.ctgraphdep.merge.login;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple service to track daily login count and determine merge strategy.
 * Strategy:
 * - Login #1 of the day: Full merge (slow, ~7 seconds)
 * - Login #2+ of the day: Fast cache refresh only (~0.5 seconds)
 * - Midnight reset: Counter back to 0
 * - App restart: Counter resets to 0 (triggers slow login, which is acceptable)
 * Thread-safe implementation for concurrent login attempts.
 */
@Service
public class LoginMergeStrategy {

    // Thread-safe login counter, starts at 0 each day/app restart
    private final AtomicInteger dailyLoginCount = new AtomicInteger(0);

    public LoginMergeStrategy() {
        LoggerUtil.initialize(this.getClass(), null);
        LoggerUtil.info(this.getClass(), "LoginMergeStrategy initialized - daily login counter reset to 0");
    }

    // ========================================================================
    // LOGIN COUNTING METHODS
    // ========================================================================

    /**
     * Increment login count and return the new count.
     * This is called every time a user logs in.
     *
     * @return The new login count (1, 2, 3, etc.)
     */
    public int incrementAndGetLoginCount() {
        int newCount = dailyLoginCount.incrementAndGet();
        LoggerUtil.info(this.getClass(), String.format("Daily login count incremented to: %d", newCount));
        return newCount;
    }

    /**
     * Get current login count without incrementing.
     *
     * @return Current login count
     */
    public int getCurrentLoginCount() {
        return dailyLoginCount.get();
    }

    /**
     * Reset login count to 0.
     * Called by SessionMidnightHandler at midnight.
     */
    public void resetDailyLoginCount() {
        int previousCount = dailyLoginCount.getAndSet(0);
        LoggerUtil.info(this.getClass(), String.format("Daily login count reset from %d to 0", previousCount));
    }

    // ========================================================================
    // MERGE STRATEGY DECISION METHODS
    // ========================================================================

    /**
     * Determine if we should perform full merge operations.
     * Only true for the first login of the day.
     *
     * @return true if this is the first login of the day
     */
    public boolean shouldPerformFullMerge() {
        int currentCount = getCurrentLoginCount();
        boolean shouldMerge = (currentCount == 1);

        LoggerUtil.info(this.getClass(), String.format(
                "Merge decision: login count = %d, perform full merge = %s",
                currentCount, shouldMerge));

        return shouldMerge;
    }

    /**
     * Determine if we should perform fast cache refresh only.
     * True for second+ login of the day.
     *
     * @return true if this is not the first login of the day
     */
    public boolean shouldPerformFastCacheRefresh() {
        int currentCount = getCurrentLoginCount();
        boolean shouldRefresh = (currentCount > 1);

        LoggerUtil.debug(this.getClass(), String.format(
                "Cache refresh decision: login count = %d, perform fast refresh = %s",
                currentCount, shouldRefresh));

        return shouldRefresh;
    }

    /**
     * Check if this is the very first login of the day.
     *
     * @return true if no logins have occurred today
     */
    public boolean isFirstLoginOfDay() {
        return getCurrentLoginCount() == 0;
    }

    // ========================================================================
    // MANUAL OVERRIDE METHODS (for future admin refresh button)
    // ========================================================================

    /**
     * Force a full merge on next login by resetting counter.
     * This can be used by admin refresh functionality in the future.
     */
    public void forceFullMergeOnNextLogin() {
        int previousCount = dailyLoginCount.getAndSet(0);
        LoggerUtil.info(this.getClass(), String.format(
                "Forced full merge: reset login count from %d to 0", previousCount));
    }

    /**
     * Simulate first login to trigger full merge.
     * Sets counter to 1, so shouldPerformFullMerge() returns true.
     */
    public void triggerFullMergeNow() {
        dailyLoginCount.set(1);
        LoggerUtil.info(this.getClass(), "Triggered full merge: set login count to 1");
    }

    // ========================================================================
    // STATUS AND MONITORING METHODS
    // ========================================================================

    /**
     * Get current status for monitoring/debugging.
     *
     * @return Status string with current count and strategy
     */
    public String getStatus() {
        int count = getCurrentLoginCount();
        String strategy = shouldPerformFullMerge() ? "Full Merge" :
                shouldPerformFastCacheRefresh() ? "Fast Cache Refresh" : "No Action";

        return String.format("Login Count: %d, Next Strategy: %s", count, strategy);
    }

    /**
     * Check if the service is in initial state (no logins today).
     *
     * @return true if counter is at 0
     */
    public boolean isInInitialState() {
        return getCurrentLoginCount() == 0;
    }

    /**
     * Get performance benefit estimate.
     *
     * @return String describing expected performance
     */
    public String getPerformanceBenefit() {
        int count = getCurrentLoginCount();
        if (count == 0) {
            return "Next login: Full merge (~7 seconds)";
        } else if (count == 1) {
            return "Next login: Fast cache refresh (~0.5 seconds)";
        } else {
            return String.format("Login %d+: Fast cache refresh (~0.5 seconds, %d%% faster)",
                    count + 1, 93); // 93% = (7-0.5)/7 * 100
        }
    }
}