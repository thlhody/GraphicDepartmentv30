package com.ctgraphdep.merge.login.interfaces;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for LoginMergeService to avoid Spring proxy injection issues.
 * This interface defines the public API for merge operations while allowing
 * the implementation to implement ApplicationListener for network events.
 */
public interface LoginMergeService {

    // ========================================================================
    // MAIN MERGE OPERATIONS
    // ========================================================================

    /**
     * Main entry point for all login merge operations.
     * Replaces the direct merge calls in AuthenticationService.
     *
     * @param username Username for merge operations
     * @param role User role for determining merge pattern
     * @return CompletableFuture for coordination
     */
    CompletableFuture<Void> performLoginMerges(String username, String role);

    /**
     * Perform normal register merge only (for USER, TEAM_LEADER roles)
     */
    CompletableFuture<Void> performNormalRegisterMergeOnlyAsync(String username);

    /**
     * Perform both registers merge (for USER_CHECKING, TL_CHECKING roles)
     */
    CompletableFuture<Void> performBothRegistersAsync(String username);

    /**
     * Perform check register merge only (for CHECKING role)
     */
    CompletableFuture<Void> performCheckRegisterMergeOnlyAsync(String username);

    // ========================================================================
    // NETWORK RETRY OPERATIONS
    // ========================================================================

    /**
     * Retry all pending merge operations when network becomes available
     */
    void retryPendingMerges();

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get the number of pending merge operations
     */
    int getPendingMergeCount();

    /**
     * Check if there are pending merges for a specific user
     */
    boolean hasPendingMerges(String username);

    /**
     * Clear all pending merges (for testing or manual intervention)
     */
    void clearPendingMerges();
}