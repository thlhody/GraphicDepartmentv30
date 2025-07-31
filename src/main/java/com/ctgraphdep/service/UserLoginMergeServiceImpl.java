package com.ctgraphdep.service;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.monitoring.NetworkStatusMonitor;
import com.ctgraphdep.monitoring.events.NetworkStatusChangedEvent;
import com.ctgraphdep.worktime.service.WorktimeLoginMergeService;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for handling all user login merge operations.
 * Decoupled from AuthenticationService to maintain clean separation of concerns.
 * Features:
 * - Role-based merge logic (moved from AuthenticationService)
 * - Network-aware operations with automatic retry
 * - Async execution for all merge operations
 * - Integration with NetworkStatusChangedEvent for intelligent retry
 * - Queuing system for offline merge operations
 */
@Service
public class UserLoginMergeServiceImpl implements UserLoginMergeService, ApplicationListener<NetworkStatusChangedEvent> {

    private final RegisterMergeService registerMergeService;
    private final CheckRegisterService checkRegisterService;
    private final WorktimeLoginMergeService worktimeLoginMergeService;
    private final NetworkStatusMonitor networkStatusMonitor;

    // Queue for retrying failed merges when network becomes available
    private final ConcurrentHashMap<String, PendingMergeOperation> pendingMerges = new ConcurrentHashMap<>();

    public UserLoginMergeServiceImpl(
            RegisterMergeService registerMergeService,
            CheckRegisterService checkRegisterService,
            WorktimeLoginMergeService worktimeLoginMergeService,
            NetworkStatusMonitor networkStatusMonitor) {
        this.registerMergeService = registerMergeService;
        this.checkRegisterService = checkRegisterService;
        this.worktimeLoginMergeService = worktimeLoginMergeService;
        this.networkStatusMonitor = networkStatusMonitor;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // MAIN ENTRY POINT
    // ========================================================================

    /**
     * Main entry point for all login merge operations.
     * Replaces the direct merge calls in AuthenticationService.
     * @param username Username for merge operations
     * @param role User role for determining merge pattern
     * @return CompletableFuture for coordination
     */
    @Override
    @Async
    public CompletableFuture<Void> performLoginMerges(String username, String role) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting login merge operations for user: %s (role: %s)", username, role));

            // Check network availability first
            if (!networkStatusMonitor.isNetworkAvailable()) {
                LoggerUtil.warn(this.getClass(), String.format("Network unavailable - queuing merge operations for user: %s", username));
                queuePendingMerge(username, role);
                return CompletableFuture.completedFuture(null);
            }
            // NEW: Load check values for checking roles
            if (role.equals(SecurityConstants.ROLE_TL_CHECKING) || role.equals(SecurityConstants.ROLE_CHECKING) || role.equals(SecurityConstants.ROLE_USER_CHECKING)) {

               performCheckValuesLoading(username);
            }

            // Execute role-based merges
            return executeRoleBasedMerges(username, role);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during login merge operations for %s: %s", username, e.getMessage()), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ========================================================================
    // ROLE-BASED MERGE LOGIC (MOVED FROM AUTHENTICATION SERVICE)
    // ========================================================================

    /**
     * Execute merge operations based on user role.
     * Logic moved from AuthenticationService.performRoleBasedDataMerges()
     */
    private CompletableFuture<Void> executeRoleBasedMerges(String username, String role) {
        UserDataAccessPattern accessPattern = determineUserDataAccessPattern(role);

        LoggerUtil.info(this.getClass(), String.format("Executing merge pattern: %s for user: %s", accessPattern, username));

        return switch (accessPattern) {
            case NORMAL_REGISTER_ONLY -> performNormalRegisterMergeOnlyAsync(username);
            case BOTH_REGISTERS -> performBothRegistersAsync(username);
            case CHECK_REGISTER_ONLY -> performCheckRegisterMergeOnlyAsync(username);
            case NO_MERGES -> {
                LoggerUtil.debug(this.getClass(), "No data merges required for user: " + username);
                yield CompletableFuture.completedFuture(null);
            }
        };
    }

    /**
     * Determine user data access pattern based on role.
     * Logic moved from AuthenticationService.determineUserDataAccessPattern()
     */
    private UserDataAccessPattern determineUserDataAccessPattern(String role) {
        if (role == null) {
            return UserDataAccessPattern.NO_MERGES;
        }

        // Check specific roles FIRST
        if (role.contains(SecurityConstants.ROLE_USER_CHECKING) || role.contains(SecurityConstants.ROLE_TL_CHECKING)) {
            return UserDataAccessPattern.BOTH_REGISTERS;
        }

        // Then check broader roles
        if (role.contains(SecurityConstants.ROLE_USER) || role.contains(SecurityConstants.ROLE_TEAM_LEADER)) {
            return UserDataAccessPattern.NORMAL_REGISTER_ONLY;
        }

        // Finally check pure CHECKING role
        if (role.contains(SecurityConstants.ROLE_CHECKING)) {
            return UserDataAccessPattern.CHECK_REGISTER_ONLY;
        }

        return UserDataAccessPattern.NO_MERGES;
    }

    // ========================================================================
    // INDIVIDUAL ASYNC MERGE OPERATIONS
    // ========================================================================

    /**
     * Perform normal register merge only (for USER, TEAM_LEADER roles)
     */
    @Override
    @Async
    public CompletableFuture<Void> performNormalRegisterMergeOnlyAsync(String username) {
        return CompletableFuture.runAsync(() -> {
            try {
                LoggerUtil.info(this.getClass(), "Performing normal register merge for: " + username);
                registerMergeService.performUserLoginMerge(username);

                // Also perform worktime merge for non-admin users
                performWorktimeMergeInternal(username);

                LoggerUtil.info(this.getClass(), "Normal register merge completed for: " + username);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Normal register merge failed for %s: %s", username, e.getMessage()));
                throw new RuntimeException("Normal register merge failed", e);
            }
        });
    }

    /**
     * ENHANCED: Perform both registers merge WITH check values preloading
     * This is called for USER_CHECKING and TL_CHECKING roles
     */
    @Override
    @Async
    public CompletableFuture<Void> performBothRegistersAsync(String username) {
        return CompletableFuture.runAsync(() -> {
            try {
                LoggerUtil.info(this.getClass(), "Performing both registers merge for: " + username);

                // Normal register merge first
                registerMergeService.performUserLoginMerge(username);

                // Then check register merge
                checkRegisterService.performCheckRegisterLoginMerge(username);

                // Also perform worktime merge
                performWorktimeMergeInternal(username);

                LoggerUtil.info(this.getClass(), "Both registers merge completed for: " + username);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Both registers merge failed for %s: %s", username, e.getMessage()));
                throw new RuntimeException("Both registers merge failed", e);
            }
        });
    }

    /**
     * ENHANCED: Perform check register merge only WITH check values preloading
     * This is called for CHECKING role
     */
    @Override
    @Async
    public CompletableFuture<Void> performCheckRegisterMergeOnlyAsync(String username) {
        return CompletableFuture.runAsync(() -> {
            try {
                LoggerUtil.info(this.getClass(), "Performing check register merge for: " + username);

                checkRegisterService.performCheckRegisterLoginMerge(username);

                LoggerUtil.info(this.getClass(), "Check register merge completed for: " + username);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Check register merge failed for %s: %s", username, e.getMessage()));
                throw new RuntimeException("Check register merge failed", e);
            }
        });
    }
    /**
     * Perform worktime merge (for all non-admin users)
     */
    private void performWorktimeMergeInternal(String username) {
        try {
            LoggerUtil.info(this.getClass(), "Performing worktime merge for: " + username);
            worktimeLoginMergeService.performUserWorktimeLoginMerge(username);
            LoggerUtil.info(this.getClass(), "Worktime merge completed for: " + username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Worktime merge failed for %s: %s - continuing", username, e.getMessage()));
            // Don't throw - worktime merge failure shouldn't block other operations
        }
    }

    private void performCheckValuesLoading(String username){
        //TODO need to add or check where this is happening
        LoggerUtil.info(this.getClass(),"Username: "+ username);

    }

    // ========================================================================
    // NETWORK-AWARE RETRY LOGIC
    // ========================================================================

    /**
     * Listen for network status changes and retry pending merges when network becomes available
     */
    @Override
    public void onApplicationEvent(NetworkStatusChangedEvent event) {
        if (event.isNetworkAvailable()) {
            LoggerUtil.info(this.getClass(), "Network available - retrying pending merge operations");
            retryPendingMerges();
        } else {
            LoggerUtil.info(this.getClass(), "Network unavailable - new merge operations will be queued");
        }
    }

    /**
     * Queue a merge operation for retry when network becomes available
     */
    private void queuePendingMerge(String username, String role) {
        PendingMergeOperation operation = new PendingMergeOperation(username, role, System.currentTimeMillis());
        pendingMerges.put(username, operation);
        LoggerUtil.info(this.getClass(), String.format(
                "Queued merge operation for retry when network available: %s (role: %s)", username, role));
    }

    /**
     * Retry all pending merge operations when network becomes available
     */
    @Override
    @Async
    public void retryPendingMerges() {
        if (pendingMerges.isEmpty()) {
            return;
        }

        LoggerUtil.info(this.getClass(), String.format("Retrying %d pending merge operations", pendingMerges.size()));

        pendingMerges.forEach((username, operation) -> {
            try {
                executeRoleBasedMerges(username, operation.getRole())
                        .thenRun(() -> {
                            pendingMerges.remove(username);
                            LoggerUtil.info(this.getClass(), String.format("Successfully retried merge for: %s", username));
                        })
                        .exceptionally(throwable -> {
                            LoggerUtil.warn(this.getClass(), String.format(
                                    "Failed to retry merge for %s: %s", username, throwable.getMessage()));
                            return null;
                        });
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Error setting up retry for %s: %s", username, e.getMessage()));
            }
        });
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get the number of pending merge operations
     */
    @Override
    public int getPendingMergeCount() {
        return pendingMerges.size();
    }

    /**
     * Check if there are pending merges for a specific user
     */
    @Override
    public boolean hasPendingMerges(String username) {
        return pendingMerges.containsKey(username);
    }

    /**
     * Clear all pending merges (for testing or manual intervention)
     */
    @Override
    public void clearPendingMerges() {
        int count = pendingMerges.size();
        pendingMerges.clear();
        LoggerUtil.info(this.getClass(), String.format("Cleared %d pending merge operations", count));
    }

    // ========================================================================
    // INNER CLASSES AND ENUMS
    // ========================================================================

    /**
     * Enum defining different user data access patterns based on roles
     * Moved from AuthenticationService
     */
    private enum UserDataAccessPattern {
        NORMAL_REGISTER_ONLY,    // USER, TEAM_LEADER
        CHECK_REGISTER_ONLY,     // CHECKING
        BOTH_REGISTERS,          // USER_CHECKING, TL_CHECKING
        NO_MERGES                // Unknown roles or admin
    }

    /**
     * Class to hold pending merge operation details
     */
    @Getter
    private static class PendingMergeOperation {
        private final String username;
        private final String role;
        private final long queuedTimestamp;

        public PendingMergeOperation(String username, String role, long queuedTimestamp) {
            this.username = username;
            this.role = role;
            this.queuedTimestamp = queuedTimestamp;
        }

    }
}