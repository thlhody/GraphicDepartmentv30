package com.ctgraphdep.merge.login.interfaces;

import com.ctgraphdep.model.User;

/**
 * Interface for LoginCacheService to avoid Spring proxy injection issues.
 * This interface defines the public API for cache operations while keeping
 * the implementation details hidden.
 * Focuses on cache execution - strategy decisions are handled by LoginMergeStrategy.
 */
public interface LoginCacheService {

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
    // UTILITY METHODS
    // ========================================================================

    /**
     * Force refresh all caches for a user (for manual intervention or testing)
     */
    void forceFullCacheRefresh(String username);
}