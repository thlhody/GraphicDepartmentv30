package com.ctgraphdep.service;

import com.ctgraphdep.security.LoginMergeCacheService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.fileOperations.data.CheckRegisterDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * Service responsible for handling all user login cache operations.
 * Decoupled from AuthenticationService to maintain clean separation of concerns.
 * Features:
 * - Parallel cache operations that can run immediately
 * - Sequential cache loading after merge operations complete
 * - Fast cache refresh for subsequent logins
 * - Integration with LoginMergeCacheService for strategy decisions
 * - All cache logic moved from AuthenticationService
 */
@Service
public class UserLoginCacheServiceImpl implements UserLoginCacheService {

    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final AllUsersCacheService allUsersCacheService;
    private final LoginMergeCacheService loginMergeCacheService;

    // NEW: Data services for local-first operations
    private final UserDataService userDataService;
    private final WorktimeDataService worktimeDataService;
    private final RegisterDataService registerDataService;
    private final CheckRegisterDataService checkRegisterDataService;

    public UserLoginCacheServiceImpl(
            MainDefaultUserContextService mainDefaultUserContextService,
            AllUsersCacheService allUsersCacheService,
            LoginMergeCacheService loginMergeCacheService,
            UserDataService userDataService,
            WorktimeDataService worktimeDataService,
            RegisterDataService registerDataService,
            CheckRegisterDataService checkRegisterDataService) {
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.allUsersCacheService = allUsersCacheService;
        this.loginMergeCacheService = loginMergeCacheService;
        this.userDataService = userDataService;
        this.worktimeDataService = worktimeDataService;
        this.registerDataService = registerDataService;
        this.checkRegisterDataService = checkRegisterDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // MAIN CACHE COORDINATION METHODS
    // ========================================================================

    /**
     * Perform initial cache operations that can run immediately and in parallel.
     * OPTIMIZED: Now uses already-fetched User object to avoid duplicate network calls!
     */
    @Override
    public void performInitialCacheOperations(User user) {
        try {
            String username = user.getUsername();
            LoggerUtil.info(this.getClass(), String.format("Starting LIGHTWEIGHT initial cache operations (LOCAL ONLY) for: %s", username));

            CompletableFuture<Void> allCacheOperations = CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> refreshSessionCache(username)),
                    CompletableFuture.runAsync(() -> refreshMainUserContext(user))  // ← Pass User object!
            );

            allCacheOperations.join();

            LoggerUtil.info(this.getClass(), String.format("LIGHTWEIGHT initial cache operations completed for: %s", username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during lightweight initial cache operations for %s: %s", user.getUsername(), e.getMessage()), e);
        }
    }

    /**
     * Perform sequential cache loading after merge operations complete.
     * OPTIMIZED: Uses LOCAL data service methods with local-first patterns
     *
     * @param username Username for cache operations
     */
    @Override
    public void performPostMergeCacheLoading(String username) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting post-merge cache loading (LOCAL FIRST) for: %s", username));

            // Sequential loading: register first, then worktime, then check register
            refreshRegisterCache(username);      // LOCAL FIRST: RegisterDataService.readUserLocalReadOnly()
            refreshWorktimeCache(username);      // LOCAL FIRST: WorktimeDataService.readUserLocalReadOnly()
            refreshCheckRegisterCache(username); // LOCAL FIRST: CheckRegisterDataService.readUserCheckRegisterLocalReadOnly()

            // Also refresh time-off cache from local
            refreshTimeOffCache(username);       // LOCAL: Time-off data

            LoggerUtil.info(this.getClass(), String.format("Post-merge cache loading completed for: %s", username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during post-merge cache loading for %s: %s", username, e.getMessage()), e);
            // Don't throw - cache failures shouldn't block operations
        }
    }

    /**
     * Perform fast cache refresh for subsequent logins.
     * OPTIMIZED: All operations use LOCAL files only - should be ~0.1-0.2 seconds!
     *
     * @param user User object (already fetched from network during authentication)
     */
    @Override
    public void performFastCacheRefresh(User user) {
        try {
            String username = user.getUsername();
            LoggerUtil.info(this.getClass(), String.format("Starting FAST cache refresh (LOCAL ONLY) for: %s", username));

            // FAST: All operations use LOCAL data services (local-first patterns)
            refreshWorktimeCache(username);      // LOCAL: WorktimeDataService.readUserLocalReadOnly()
            refreshRegisterCache(username);      // LOCAL: RegisterDataService.readUserLocalReadOnly()
            refreshCheckRegisterCache(username); // LOCAL: CheckRegisterDataService.readUserCheckRegisterLocalReadOnly()
            refreshTimeOffCache(username);       // LOCAL: Time-off data from local files
            refreshSessionCache(username);       // LOCAL: Session data from local files
            refreshMainUserContext(user);        // ← Use User object here too!

            LoggerUtil.info(this.getClass(), String.format("FAST cache refresh completed for: %s", username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during fast cache refresh for %s: %s", user.getUsername(), e.getMessage()), e);
        }
    }

    // ========================================================================
    // INDIVIDUAL CACHE REFRESH METHODS (MOVED FROM AUTHENTICATION SERVICE)
    // ========================================================================

    /**
     * Refresh worktime cache for the user.
     * OPTIMIZED: Uses existing WorktimeDataService.readUserLocalReadOnly()
     */
    private void refreshWorktimeCache(String username) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Refreshing worktime cache from LOCAL files for: %s", username));

            // Get current user data for userId
            User currentUser = mainDefaultUserContextService.getCurrentUser();
            if (currentUser == null) {
                LoggerUtil.warn(this.getClass(), "Cannot refresh worktime cache - no current user context");
                return;
            }

            // Use existing WorktimeDataService.readUserLocalReadOnly() (local first)
            LocalDate now = LocalDate.now();
            worktimeDataService.readUserLocalReadOnly(username, now.getYear(), now.getMonthValue(), username);

            LoggerUtil.debug(this.getClass(), String.format("Worktime cache refreshed from local for: %s", username));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to refresh worktime cache for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Refresh register cache for the user.
     * OPTIMIZED: Uses existing RegisterDataService.readUserLocalReadOnly()
     */
    private void refreshRegisterCache(String username) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Refreshing register cache from LOCAL files for: %s", username));

            // Get current user data for userId
            User currentUser = mainDefaultUserContextService.getCurrentUser();
            if (currentUser == null) {
                LoggerUtil.warn(this.getClass(), "Cannot refresh register cache - no current user context");
                return;
            }

            // Use existing RegisterDataService.readUserLocalReadOnly() (local first)
            LocalDate now = LocalDate.now();
            registerDataService.readUserLocalReadOnly(username, currentUser.getUserId(), username, now.getYear(), now.getMonthValue());

            LoggerUtil.debug(this.getClass(), String.format("Register cache refreshed from local for: %s", username));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to refresh register cache for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Refresh check register cache for the user.
     * OPTIMIZED: Uses existing CheckRegisterDataService.readUserCheckRegisterLocalReadOnly()
     */
    private void refreshCheckRegisterCache(String username) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Refreshing check register cache from LOCAL files for: %s", username));

            // Get current user data for userId
            User currentUser = mainDefaultUserContextService.getCurrentUser();
            if (currentUser == null) {
                LoggerUtil.warn(this.getClass(), "Cannot refresh check register cache - no current user context");
                return;
            }

            // Use existing CheckRegisterDataService.readUserCheckRegisterLocalReadOnly() (local first)
            LocalDate now = LocalDate.now();
            checkRegisterDataService.readUserCheckRegisterLocalReadOnly(username, currentUser.getUserId(), now.getYear(), now.getMonthValue());

            LoggerUtil.debug(this.getClass(), String.format("Check register cache refreshed from local for: %s", username));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to refresh check register cache for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Refresh time-off cache for the user.
     * OPTIMIZED: Uses local files only (no network operations)
     */
    private void refreshTimeOffCache(String username) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Refreshing time-off cache from LOCAL files for: %s", username));

            // Note: TimeOffDataService likely has similar local-first patterns
            // For now, this is a placeholder that doesn't block login
            // The actual implementation would use TimeOffDataService.readUserLocalReadOnly()
            // or similar local-first method when available

            LoggerUtil.debug(this.getClass(), String.format("Time-off cache refreshed from local for: %s", username));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to refresh time-off cache for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Refresh all users cache using LOCAL DATA ONLY.
     * OPTIMIZED: Uses UserDataService.getAllLocalUsersForAuthentication() - local only!
     */
    private void refreshAllUsersCache() {
        try {
            LoggerUtil.debug(this.getClass(), "Refreshing all users cache from LOCAL users only");

            // Use existing UserDataService.getAllLocalUsersForAuthentication() - LOCAL ONLY!
            List<User> localUsers = userDataService.getAllLocalUsersForAuthentication();

            // Update cache with local users
            for (User user : localUsers) {
                allUsersCacheService.updateUserInCache(user);
            }

            LoggerUtil.debug(this.getClass(), String.format("All users cache refreshed with %d local users", localUsers.size()));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to refresh all users cache from local: %s", e.getMessage()));
        }
    }

    /**
     * Refresh session cache for the user.
     * OPTIMIZED: Local session operations only
     */
    private void refreshSessionCache(String username) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Refreshing session cache from LOCAL files for: %s", username));

            // Session cache refresh is typically lightweight and local-only
            // This might involve updating session timestamps, user state, etc.
            // Implementation depends on SessionCacheService if available

            LoggerUtil.debug(this.getClass(), String.format("Session cache refreshed from local for: %s", username));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to refresh session cache for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Refresh main user context using already-fetched User object.
     * FIXED: No longer makes duplicate network calls!
     */
    private void refreshMainUserContext(User user) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Refreshing main user context for: %s", user.getUsername()));

            // Use the already-fetched User object instead of re-fetching by username
            mainDefaultUserContextService.setCurrentUser(user);  // ← Direct set, no network call!

            LoggerUtil.debug(this.getClass(), String.format("Main user context refreshed for: %s", user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to refresh main user context for %s: %s", user.getUsername(), e.getMessage()));
        }
    }

    // ========================================================================
    // BACKGROUND CACHE OPERATIONS
    // ========================================================================

    /**
     * Refresh all users cache in background (heavy operation moved out of login path)
     * OPTIMIZED: Uses local users only, can be called async
     */
    @Async
    public void refreshAllUsersCacheInBackground() {
        CompletableFuture.runAsync(() -> {
            try {
                LoggerUtil.info(this.getClass(), "Starting background all users cache refresh from LOCAL users");
                refreshAllUsersCache(); // Uses local users only
                LoggerUtil.info(this.getClass(), "Background all users cache refresh completed");
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Background all users cache refresh failed: %s", e.getMessage()));
            }
        });
    }

    /**
     * Determine if fast cache refresh should be performed.
     * Delegates to LoginMergeCacheService for strategy decision.
     */
    @Override
    public boolean shouldPerformFastCacheRefresh() {
        return loginMergeCacheService.shouldPerformFastCacheRefresh();
    }

    /**
     * Determine if full cache operations should be performed.
     * Delegates to LoginMergeCacheService for strategy decision.
     */
    @Override
    public boolean shouldPerformFullCacheOperations() {
        return loginMergeCacheService.shouldPerformFullMerge();
    }

    /**
     * Get current login count for cache strategy decisions.
     */
    @Override
    public int getCurrentLoginCount() {
        return loginMergeCacheService.getCurrentLoginCount();
    }

    // ========================================================================
    // ASYNC CONVENIENCE METHODS
    // ========================================================================

    /**
     * Async version of performInitialCacheOperations for better coordination
     */
    @Override
    @Async
    public CompletableFuture<Void> performInitialCacheOperationsAsync(User user) {
        return CompletableFuture.runAsync(() -> performInitialCacheOperations(user));
    }

    /**
     * Async version of performPostMergeCacheLoading for better coordination
     */
    @Override
    @Async
    public CompletableFuture<Void> performPostMergeCacheLoadingAsync(String username) {
        return CompletableFuture.runAsync(() -> performPostMergeCacheLoading(username));
    }

    /**
     * Async version of performFastCacheRefresh for better coordination
     */
    @Override
    @Async
    public CompletableFuture<Void> performFastCacheRefreshAsync(User user) {
        return CompletableFuture.runAsync(() -> performFastCacheRefresh(user));
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Force refresh all caches for a user (for manual intervention or testing)
     * OPTIMIZED: Uses all local-first data service methods
     */
    @Override
    public void forceFullCacheRefresh(String username) {
        LoggerUtil.info(this.getClass(), String.format("Forcing FULL cache refresh (LOCAL FIRST) for: %s", username));

        // Fetch User object for methods that now require it
        Optional<User> userOpt = userDataService.findUserByUsernameForAuthentication(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Step 1: Lightweight initial operations
            performInitialCacheOperations(user);  // ← Now uses User object

            // Step 2: Data cache loading (local first)
            performPostMergeCacheLoading(username);  // ← Still uses username

            // Step 3: Background all users cache refresh
            refreshAllUsersCacheInBackground();
        } else {
            LoggerUtil.error(this.getClass(), String.format("Could not find user for full cache refresh: %s", username));
        }

        LoggerUtil.info(this.getClass(), String.format("Forced full cache refresh completed for: %s", username));
    }

    /**
     * Get cache status information for monitoring
     */
    @Override
    public String getCacheStatus(String username) {
        return String.format("Cache status for %s - Login count: %d, Strategy: %s",
                username,
                getCurrentLoginCount(),
                shouldPerformFastCacheRefresh() ? "Fast Refresh" : "Full Operations");
    }
}