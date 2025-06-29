package com.ctgraphdep.service;

import com.ctgraphdep.model.User;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for UserLoginCacheService to avoid Spring proxy injection issues.
 * This interface defines the public API for cache operations while keeping
 * the implementation details hidden.
 */
public interface UserLoginCacheService {

    // ========================================================================
    // MAIN CACHE COORDINATION METHODS
    // ========================================================================

    /**
     * Perform initial cache operations that can run immediately and in parallel.
     * Now accepts User object to avoid re-fetching from network.
     *
     * @param user User object (already fetched from network during authentication)
     */
    void performInitialCacheOperations(User user);
    /**
     * Perform sequential cache loading after merge operations complete.
     * Register cache loads first, then worktime cache.
     *
     * @param username Username for cache operations
     */
    void performPostMergeCacheLoading(String username);

    /**
     * Perform fast cache refresh for subsequent logins.
     * Logic moved from AuthenticationService.performFastCacheRefresh()

     */
    void performFastCacheRefresh(User user);

    // ========================================================================
    // CACHE STRATEGY METHODS
    // ========================================================================

    /**
     * Determine if fast cache refresh should be performed.
     * Delegates to LoginMergeCacheService for strategy decision.
     */
    boolean shouldPerformFastCacheRefresh();

    /**
     * Determine if full cache operations should be performed.
     * Delegates to LoginMergeCacheService for strategy decision.
     */
    boolean shouldPerformFullCacheOperations();

    /**
     * Get current login count for cache strategy decisions.
     */
    int getCurrentLoginCount();

    // ========================================================================
    // ASYNC CONVENIENCE METHODS
    // ========================================================================

    /**
     * Async version of performInitialCacheOperations for better coordination
     */
    CompletableFuture<Void> performInitialCacheOperationsAsync(User user);

    /**
     * Async version of performPostMergeCacheLoading for better coordination
     */
    CompletableFuture<Void> performPostMergeCacheLoadingAsync(String username);

    /**
     * Async version of performFastCacheRefresh for better coordination
     */
    CompletableFuture<Void> performFastCacheRefreshAsync(User user);

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Force refresh all caches for a user (for manual intervention or testing)
     */
    void forceFullCacheRefresh(String username);

    /**
     * Get cache status information for monitoring
     */
    String getCacheStatus(String username);
}