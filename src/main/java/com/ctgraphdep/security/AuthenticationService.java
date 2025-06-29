package com.ctgraphdep.security;

import com.ctgraphdep.controller.MergeStatusController;
import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.model.AuthenticationStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.service.UserLoginCacheService;
import com.ctgraphdep.service.UserLoginMergeService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REFACTORED AuthenticationService - focused on authentication only.
 * Changes made:
 * - Removed direct merge service dependencies (RegisterMergeService, CheckRegisterService, WorktimeLoginMergeService)
 * - Removed cache operation methods (moved to UserLoginCacheService)
 * - Removed role-based merge logic (moved to UserLoginMergeService)
 * - Simplified post-login operations to use new decoupled services
 * - Maintained all authentication logic unchanged
 * - Added admin elevation logic
 * - Cleaner separation of concerns: Authentication vs Merging vs Caching
 */
@Service
public class AuthenticationService {

    private final DataAccessService dataAccessService;  // Keep for system utilities only
    private final UserDataService userDataService;      // Primary user data operations
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final AllUsersCacheService allUsersCacheService;
    private final LoginMergeCacheService loginMergeCacheService;

    // NEW: Decoupled services for merge and cache operations
    private final UserLoginMergeService userLoginMergeService;
    private final UserLoginCacheService userLoginCacheService;

    private MergeStatusController mergeStatusController; // For background merge status

    public AuthenticationService(
            DataAccessService dataAccessService,
            UserDataService userDataService,
            PasswordEncoder passwordEncoder,
            CustomUserDetailsService userDetailsService,
            MainDefaultUserContextService mainDefaultUserContextService,
            AllUsersCacheService allUsersCacheService,
            LoginMergeCacheService loginMergeCacheService,
            UserLoginMergeService userLoginMergeService,
            UserLoginCacheService userLoginCacheService) {
        this.dataAccessService = dataAccessService;
        this.userDataService = userDataService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.allUsersCacheService = allUsersCacheService;
        this.loginMergeCacheService = loginMergeCacheService;
        this.userLoginMergeService = userLoginMergeService;
        this.userLoginCacheService = userLoginCacheService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Setter for MergeStatusController (to avoid circular dependency)
     */
    @Autowired(required = false)
    public void setMergeStatusController(MergeStatusController mergeStatusController) {
        this.mergeStatusController = mergeStatusController;
    }

    // ========================================================================
    // AUTHENTICATION STATUS (UNCHANGED)
    // ========================================================================

    /**
     * UNCHANGED: Get authentication status using UserDataService
     */
    public AuthenticationStatus getAuthenticationStatus() {
        try {
            // Check network status via DataAccessService (system utility)
            boolean networkAvailable = dataAccessService.isNetworkAvailable();

            // Check if local users exist via UserDataService
            boolean offlineModeAvailable = false;
            try {
                offlineModeAvailable = userDataService.hasLocalUsersForAuthenticationStatus();
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), "Error checking local users via UserDataService: " + e.getMessage());
            }

            String status = networkAvailable ? "ONLINE" : offlineModeAvailable ? "OFFLINE" : "UNAVAILABLE";

            LoggerUtil.info(this.getClass(), String.format("Authentication Status: Network=%b, Local=%b, Status=%s",
                    networkAvailable, offlineModeAvailable, status));

            return new AuthenticationStatus(networkAvailable, offlineModeAvailable, status);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking authentication status: " + e.getMessage());
            return new AuthenticationStatus(false, false, "UNAVAILABLE");
        }
    }

    // ========================================================================
    // USER AUTHENTICATION METHODS (CORRECTED RETURN TYPE)
    // ========================================================================

    /**
     * UNCHANGED: Authentication logic for Spring Security integration
     * Returns UserDetails as expected by Spring Security
     */
    public UserDetails authenticateUser(String username, String password, boolean offlineMode) {
        // Get the full user object without sanitization
        UserDetails userDetails = offlineMode ?
                userDetailsService.loadUserByUsernameOffline(username) :
                userDetailsService.loadUserByUsername(username);

        LoggerUtil.debug(this.getClass(), String.format("Authenticating user: %s", username));

        if (passwordEncoder.matches(password, userDetails.getPassword())) {
            LoggerUtil.info(this.getClass(), String.format("Successfully authenticated user %s", username));
            return userDetails;
        }

        LoggerUtil.warn(this.getClass(), "Invalid credentials for user: " + username);
        throw new BadCredentialsException("Invalid credentials");
    }

    /**
     * REFACTORED: Handle successful login with role elevation support and new services
     */
    public void handleSuccessfulLogin(String username, boolean rememberMe) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing login for user: %s (rememberMe: %s)", username, rememberMe));

            // Step 1: Retrieve and validate user data
            User user = retrieveUserData(username);

            // Step 2: Determine login type and handle accordingly
            if (user.isAdmin()) {
                handleAdminLogin(user, rememberMe);
            } else {
                handleRegularUserLogin(user, rememberMe);
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully completed login processing for user: %s (admin: %s, elevated: %s)",
                    username, user.isAdmin(), mainDefaultUserContextService.isElevated()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error handling login for user %s: %s", username, e.getMessage()), e);
            throw new RuntimeException("Failed to handle login", e);
        }
    }

    // ========================================================================
    // LOGIN TYPE HANDLERS (NEW - MOVED FROM ORIGINAL)
    // ========================================================================

    /**
     * Handle admin login with elevation (admins don't use merge optimization)
     */
    private void handleAdminLogin(User adminUser, boolean rememberMe) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing admin login: %s", adminUser.getUsername()));

            // Step 1: Ensure we have a healthy original user context for background processes
            if (!mainDefaultUserContextService.isCacheInitialized()) {
                LoggerUtil.warn(this.getClass(),
                        "No original user context found - background processes may use system user");
            }

            // Step 2: Elevate to admin role (preserves original user context)
            mainDefaultUserContextService.elevateToAdminRole(adminUser);

            // Step 3: Refresh all users cache for accurate admin data
            LoggerUtil.info(this.getClass(), "Refreshing all users cache for admin login");
            try {
                allUsersCacheService.refreshAllUsersFromUserDataServiceWithCompleteData();
                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully refreshed cache with %d users for admin %s",
                        allUsersCacheService.getCachedUserCount(), adminUser.getUsername()));
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to refresh users cache for admin %s: %s - continuing with login",
                        adminUser.getUsername(), e.getMessage()));
            }

            // Step 4: Handle local storage if requested (for admin user)
            if (rememberMe) {
                handleLocalStorageOperations(adminUser);
            }

            // Step 5: Skip data merges for admin users (they don't need user data merges)
            LoggerUtil.info(this.getClass(), String.format(
                    "Admin login completed: %s (elevation active, original user preserved, cache refreshed)",
                    adminUser.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during admin login for %s: %s", adminUser.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to handle admin login", e);
        }
    }

    /**
     * Handle regular user login with LOGIN MERGE OPTIMIZATION using new services
     */
    private void handleRegularUserLogin(User user, boolean rememberMe) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing regular user login: %s", user.getUsername()));

            // Step 1: Clear any existing admin elevation
            if (mainDefaultUserContextService.isElevated()) {
                mainDefaultUserContextService.clearAdminElevation();
                LoggerUtil.info(this.getClass(), "Cleared existing admin elevation for regular user login");
            }

            // Step 2: Update MainDefaultUserContextService (uses MainDefaultUserContextCache)
            mainDefaultUserContextService.handleSuccessfulLogin(user.getUsername());

            // Step 3: ALSO update AllUsersCacheService for complete synchronization
            try {
                allUsersCacheService.updateUserInCache(user);
                LoggerUtil.info(this.getClass(), String.format(
                        "User synchronized in AllUsersCacheService: %s", user.getUsername()));
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to sync user to AllUsersCacheService: %s - %s",
                        user.getUsername(), e.getMessage()));
            }

            // Step 4: Handle local storage operations if needed
            if (rememberMe) {
                handleLocalStorageOperations(user);
            }

            // Step 5: NEW - COORDINATED POST-LOGIN OPERATIONS using decoupled services
            performPostLoginOperations(user);

            LoggerUtil.info(this.getClass(), String.format(
                    "Regular user login completed: %s (both caches synchronized, merge strategy applied)",
                    user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during regular user login for %s: %s", user.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to handle regular user login", e);
        }
    }

    /**
     * Handle first login of the day - full merge and cache operations
     */
    private void performFirstLoginOperations(User user) {  // ← Accept User object
        String username = user.getUsername();
        String role = user.getRole();

        LoggerUtil.info(this.getClass(), String.format("Performing FIRST LOGIN operations for: %s", username));

        if (mergeStatusController != null) {
            mergeStatusController.markMergeStarted(username);
        }

        // Pass User object instead of username
        userLoginCacheService.performInitialCacheOperations(user);  // ← Pass User object

        CompletableFuture<Void> mergeOperations = userLoginMergeService.performLoginMerges(username, role);

        mergeOperations.thenRun(() -> {
            userLoginCacheService.performPostMergeCacheLoading(username);

            if (mergeStatusController != null) {
                mergeStatusController.markMergeComplete(username);
            }

            LoggerUtil.info(this.getClass(), String.format("First login operations completed for: %s", username));
        }).exceptionally(throwable -> {
            // error handling...
            return null;
        });
    }

    /**
     * NEW: Coordinated post-login operations using decoupled services
     */
    private void performPostLoginOperations(User user) {
        String username = user.getUsername();

        int loginCount = loginMergeCacheService.incrementAndGetLoginCount();

        LoggerUtil.info(this.getClass(), String.format("Daily login count: %d for user: %s", loginCount, username));
        LoggerUtil.info(this.getClass(), loginMergeCacheService.getPerformanceBenefit());

        if (loginMergeCacheService.shouldPerformFullMerge()) {
            performFirstLoginOperations(user);  // ← Pass User object instead of username, role
        } else if (loginMergeCacheService.shouldPerformFastCacheRefresh()) {
            performSubsequentLoginOperations(user);
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Post-login operations initiated for: %s (strategy: %s)",
                username, loginCount == 1 ? "Full merge + cache" : "Fast cache refresh"));
    }

    /**
     * Handle subsequent logins - fast cache refresh only
     */
    private void performSubsequentLoginOperations(User user) {  // ← Change parameter
        LoggerUtil.info(this.getClass(), String.format("Performing SUBSEQUENT LOGIN operations (fast refresh) for: %s", user.getUsername()));

        try {
            // Fast cache refresh only (synchronous but fast ~0.5 seconds)
            userLoginCacheService.performFastCacheRefresh(user);  // ← Pass User object

            LoggerUtil.info(this.getClass(), String.format("Subsequent login operations completed for: %s", user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during subsequent login operations for %s: %s", user.getUsername(), e.getMessage()), e);
        }
    }
    // ========================================================================
    // HELPER METHODS (UNCHANGED/CORRECTED)
    // ========================================================================

    /**
     * Retrieve and validate user data from UserDataService
     */
    private User retrieveUserData(String username) {
        Optional<User> userOptional = userDataService.findUserByUsernameForAuthentication(username);

        if (userOptional.isPresent()) {
            return userOptional.get();
        } else {
            LoggerUtil.error(this.getClass(), "User not found after authentication: " + username);
            throw new RuntimeException("User not found after authentication");
        }
    }

    /**
     * UNCHANGED: Store user data locally using UserDataService
     */
    private void storeUserDataLocally(User user) {
        try {
            boolean stored = userDataService.storeUserDataForRememberMe(user);

            if (stored) {
                LoggerUtil.info(this.getClass(), String.format("Stored complete user data locally for: %s", user.getUsername()));
            } else {
                LoggerUtil.error(this.getClass(), String.format("Failed to store user data locally for: %s", user.getUsername()));
                throw new RuntimeException("Failed to store complete user data locally");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error storing user data locally: %s", e.getMessage()));
            throw new RuntimeException("Failed to store user data locally", e);
        }
    }

    /**
     * UNCHANGED: Handle local storage operations for remember me
     */
    private void handleLocalStorageOperations(User user) {
        storeUserDataLocally(user);
    }

    // ========================================================================
    // PUBLIC API METHODS FOR COORDINATION
    // ========================================================================

    /**
     * Get current login optimization status
     */
    public String getLoginOptimizationStatus() {
        return loginMergeCacheService.getStatus();
    }

    /**
     * Check if there are pending merge operations for a user
     */
    public boolean hasPendingMerges(String username) {
        return userLoginMergeService.hasPendingMerges(username);
    }

    /**
     * Get the number of pending merge operations
     */
    public int getPendingMergeCount() {
        return userLoginMergeService.getPendingMergeCount();
    }

    /**
     * Force retry of pending merges (for manual intervention)
     */
    public void retryPendingMerges() {
        userLoginMergeService.retryPendingMerges();
    }

    /**
     * Force full cache refresh for a user (for manual intervention)
     */
    public void forceFullCacheRefresh(String username) {
        userLoginCacheService.forceFullCacheRefresh(username);
    }
}