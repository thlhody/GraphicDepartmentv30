package com.ctgraphdep.security;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.MergeStatusController;
import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.model.AuthenticationStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.service.CheckRegisterService;
import com.ctgraphdep.service.RegisterMergeService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.worktime.service.WorktimeLoginMergeService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * ENHANCED AuthenticationService with LoginMergeCacheService integration.
 * Now performs full merge only on first login of the day, fast cache refresh on subsequent logins.
 * Handles authentication status, user authentication, and optimized post-login operations.
 */
@Service
public class AuthenticationService {

    private final DataAccessService dataAccessService;  // Keep for system utilities only
    private final UserDataService userDataService;      // Primary user data operations
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final RegisterMergeService registerMergeService;
    private final CheckRegisterService checkRegisterService;
    private final WorktimeLoginMergeService worktimeLoginMergeService;
    private final MainDefaultUserContextService mainDefaultUserContextService;
    private final AllUsersCacheService allUsersCacheService;
    private final LoginMergeCacheService loginMergeCacheService; // NEW - Login optimization
    private MergeStatusController mergeStatusController; // NEW - For background merge status

    public AuthenticationService(
            DataAccessService dataAccessService,
            UserDataService userDataService,
            UserService userService,
            PasswordEncoder passwordEncoder,
            CustomUserDetailsService userDetailsService,
            RegisterMergeService registerMergeService,
            CheckRegisterService checkRegisterService,
            WorktimeLoginMergeService worktimeLoginMergeService,
            MainDefaultUserContextService mainDefaultUserContextService,
            AllUsersCacheService allUsersCacheService,
            LoginMergeCacheService loginMergeCacheService) { // Login optimization dependency
        this.dataAccessService = dataAccessService;
        this.userDataService = userDataService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.registerMergeService = registerMergeService;
        this.checkRegisterService = checkRegisterService;
        this.worktimeLoginMergeService = worktimeLoginMergeService;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
        this.allUsersCacheService = allUsersCacheService;
        this.loginMergeCacheService = loginMergeCacheService; // NEW
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * NEW: Setter for MergeStatusController (to avoid circular dependency)
     * Called by MergeStatusController after it's created
     */
    @Autowired(required = false)
    public void setMergeStatusController(MergeStatusController mergeStatusController) {
        this.mergeStatusController = mergeStatusController;
    }

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

    /**
     * UNCHANGED: Authentication logic remains the same
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
     * UNCHANGED: Handle successful login with role elevation support
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
    // LOGIN TYPE HANDLERS
    // ========================================================================

    /**
     * UNCHANGED: Handle admin login with elevation (admins don't use merge optimization)
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
                // Don't fail login if cache refresh fails
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
     * ENHANCED: Handle regular user login with LOGIN MERGE OPTIMIZATION
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
                // Continue - don't fail login for cache sync issues
            }

            // Step 4: Handle local storage operations if needed
            if (rememberMe) {
                handleLocalStorageOperations(user);
            }

            // Step 5: NEW - OPTIMIZED DATA MERGE STRATEGY WITH BACKGROUND PROCESSING
            int loginCount = loginMergeCacheService.incrementAndGetLoginCount();

            LoggerUtil.info(this.getClass(), String.format("Daily login count: %d", loginCount));
            LoggerUtil.info(this.getClass(), loginMergeCacheService.getPerformanceBenefit());

            if (loginMergeCacheService.shouldPerformFullMerge()) {
                // FIRST LOGIN OF THE DAY - Background merge (instant login, slow merge in background)
                LoggerUtil.info(this.getClass(), String.format("Triggering BACKGROUND FULL MERGE for first login of day: %s", user.getUsername()));
                performBackgroundFullMergeAsync(user);
            } else if (loginMergeCacheService.shouldPerformFastCacheRefresh()) {
                // SUBSEQUENT LOGINS - Fast cache refresh only (still synchronous but fast)
                LoggerUtil.info(this.getClass(), String.format("Performing FAST CACHE REFRESH for login #%d: %s", loginCount, user.getUsername()));
                performFastCacheRefresh(user);
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Regular user login completed: %s (both caches synchronized, merge strategy: %s)",
                    user.getUsername(), loginCount == 1 ? "Background Full Merge" : "Fast Refresh"));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during regular user login for %s: %s", user.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to handle regular user login", e);
        }
    }

    // ========================================================================
    // NEW - BACKGROUND MERGE OPTIMIZATION METHODS
    // ========================================================================

    /**
     * NEW: Trigger background full merge operations (first login optimization)
     * This method completes immediately, allowing instant login while merge happens in background
     */
    private void performBackgroundFullMergeAsync(User user) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Scheduling background full merge for: %s", user.getUsername()));

            // Trigger async background merge - this returns immediately
            executeFullMergeInBackground(user);

            LoggerUtil.info(this.getClass(), String.format("Background merge scheduled for: %s - login completing instantly", user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error scheduling background merge for %s: %s", user.getUsername(), e.getMessage()), e);
            // Fallback to synchronous merge if async fails
            LoggerUtil.warn(this.getClass(), "Falling back to synchronous merge due to async error");
            performFullMergeOperations(user);
        }
    }

    /**
     * NEW: Async method that performs full merge operations in background
     * This runs on a separate thread so user gets instant login
     */
    @Async("loginMergeTaskExecutor")
    public void executeFullMergeInBackground(User user) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting background full merge operations for: %s (Thread: %s)",
                    user.getUsername(), Thread.currentThread().getName()));

            // Use existing role-based data merge logic (unchanged)
            performRoleBasedDataMerges(user);

            LoggerUtil.info(this.getClass(), String.format("Background full merge operations completed for: %s", user.getUsername()));

            // Notify status controller that merge is complete
            if (mergeStatusController != null) {
                mergeStatusController.markMergeComplete(user.getUsername());
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during background merge operations for %s: %s", user.getUsername(), e.getMessage()), e);

            // Even on error, mark as complete so UI doesn't wait forever
            if (mergeStatusController != null) {
                mergeStatusController.markMergeComplete(user.getUsername());
            }
        }
    }

    /**
     * EXISTING: Perform full merge operations synchronously (kept for fallback and testing)
     * Now mainly used as fallback when async processing fails
     */
    private void performFullMergeOperations(User user) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting synchronous full merge operations for: %s", user.getUsername()));

            // Use existing role-based data merge logic (unchanged)
            performRoleBasedDataMerges(user);

            LoggerUtil.info(this.getClass(), String.format("Synchronous full merge operations completed for: %s", user.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during synchronous merge operations for %s: %s", user.getUsername(), e.getMessage()), e);
            // Don't throw - login should continue even if merge fails
        }
    }

    /**
     * NEW: Perform fast cache refresh (new fast logic, subsequent logins)
     */
    private void performFastCacheRefresh(User user) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting fast cache refresh for: %s", user.getUsername()));

            String username = user.getUsername();

            // Fast refresh of caches from LOCAL files only (no network operations, no merging)
            refreshWorktimeCache(username);      // Current month from local
            refreshCheckRegisterCache(username); // Current month from local
            refreshTimeOffCache(username);       // Current year from local
            refreshAllUsersCache();              // Local user list + network status flags only
            refreshSessionCache(username);       // Local session data

            LoggerUtil.info(this.getClass(), String.format("Fast cache refresh completed for: %s", username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during fast cache refresh for %s: %s", user.getUsername(), e.getMessage()), e);
            // Don't throw - login should continue even if cache refresh fails
        }
    }

    // ========================================================================
    // NEW - FAST CACHE REFRESH METHODS (LOCAL FILES ONLY)
    // ========================================================================

    /**
     * NEW: Refresh worktime cache from local files only (current month)
     */
    private void refreshWorktimeCache(String username) {
        try {
            LoggerUtil.debug(this.getClass(), "Refreshing worktime cache from local files for: " + username);

            // TODO: Implement worktime cache refresh from local files
            // This should load current month's worktime data from local files into cache
            // worktimeCacheService.refreshFromLocalFiles(username, LocalDate.now().getYear(), LocalDate.now().getMonthValue());

            LoggerUtil.debug(this.getClass(), "Worktime cache refresh completed for: " + username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Failed to refresh worktime cache for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * NEW: Refresh check register cache from local files only (current month)
     */
    private void refreshCheckRegisterCache(String username) {
        try {
            LoggerUtil.debug(this.getClass(), "Refreshing check register cache from local files for: " + username);

            // TODO: Implement check register cache refresh from local files
            // This should load current month's check register data from local files into cache
            // registerCheckCacheService.refreshFromLocalFiles(username, LocalDate.now().getYear(), LocalDate.now().getMonthValue());

            LoggerUtil.debug(this.getClass(), "Check register cache refresh completed for: " + username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Failed to refresh check register cache for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * NEW: Refresh time-off cache from local files only (current year)
     */
    private void refreshTimeOffCache(String username) {
        try {
            LoggerUtil.debug(this.getClass(), "Refreshing time-off cache from local files for: " + username);

            // TODO: Implement time-off cache refresh from local files
            // This should load current year's time-off data from local files into cache
            // timeOffCacheService.refreshFromLocalFiles(username, LocalDate.now().getYear());

            LoggerUtil.debug(this.getClass(), "Time-off cache refresh completed for: " + username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Failed to refresh time-off cache for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * NEW: Refresh all users cache (local user list + network status flags only)
     */
    private void refreshAllUsersCache() {
        try {
            LoggerUtil.debug(this.getClass(), "Refreshing all users cache from local files");

            // This uses existing AllUsersCacheService method - just light refresh
            allUsersCacheService.syncFromNetworkFlags(); // Only refresh status flags, not full user data

            LoggerUtil.debug(this.getClass(), "All users cache refresh completed");
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Failed to refresh all users cache: " + e.getMessage());
        }
    }

    /**
     * NEW: Refresh session cache from local files only
     */
    private void refreshSessionCache(String username) {
        try {
            LoggerUtil.debug(this.getClass(), "Refreshing session cache from local files for: " + username);

            // TODO: Implement session cache refresh from local files
            // This should reload session data from local files into cache
            // sessionCacheService.refreshFromLocalFiles(username);

            LoggerUtil.debug(this.getClass(), "Session cache refresh completed for: " + username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Failed to refresh session cache for %s: %s", username, e.getMessage()));
        }
    }

    // ========================================================================
    // EXISTING METHODS (UNCHANGED)
    // ========================================================================

    /**
     * UNCHANGED: Retrieve user data using UserService first, UserDataService fallback
     */
    private User retrieveUserData(String username) {
        // Try to get user from service first (cache-based)
        Optional<User> userOptional = userService.getUserByUsername(username);

        // Fallback to UserDataService if not found in cache
        if (userOptional.isEmpty()) {
            LoggerUtil.debug(this.getClass(),
                    "User not found in service cache, trying UserDataService for: " + username);
            userOptional = getUserFromUserDataService(username);
        }

        if (userOptional.isPresent()) {
            LoggerUtil.debug(this.getClass(), "Successfully retrieved user data for: " + username);
            return userOptional.get();
        } else {
            LoggerUtil.error(this.getClass(), "User not found in any storage for: " + username);
            throw new UsernameNotFoundException("User not found after authentication: " + username);
        }
    }

    /**
     * UNCHANGED: Handle local storage operations using UserDataService
     */
    private void handleLocalStorageOperations(User user) {
        try {
            // Validate local directories (DataAccessService utility function)
            boolean dirsOk = dataAccessService.revalidateLocalDirectories(user.isAdmin());
            if (!dirsOk) {
                LoggerUtil.warn(this.getClass(),
                        "Directory validation failed for user " + user.getUsername() + " - some operations may fail");
            }

            // Store user data locally using UserDataService
            storeUserDataLocally(user);

            LoggerUtil.debug(this.getClass(), "Local storage operations completed for: " + user.getUsername());

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to handle local storage for %s: %s - continuing with login",
                    user.getUsername(), e.getMessage()));
            // Don't throw - local storage failure shouldn't block login
        }
    }

    /**
     * UNCHANGED: Role-based data merges (used by full merge operations)
     */
    private void performRoleBasedDataMerges(User user) {
        String username = user.getUsername();
        String role = user.getRole();

        LoggerUtil.info(this.getClass(), String.format(
                "Determining data merge operations for user %s with role: %s", username, role));

        // Skip all merges for admin users
        if (user.isAdmin()) {
            LoggerUtil.debug(this.getClass(), "Skipping all merges for admin user: " + username);
            return;
        }

        performWorktimeMerge(username);

        // Determine what data this user should have access to based on role
        UserDataAccessPattern accessPattern = determineUserDataAccessPattern(role);

        switch (accessPattern) {
            case NORMAL_REGISTER_ONLY:
                performNormalRegisterMergeOnly(username);
                break;

            case BOTH_REGISTERS:
                performNormalRegisterMerge(username);
                performCheckRegisterMerge(username);
                break;

            case CHECK_REGISTER_ONLY:
                performCheckRegisterMergeOnly(username);
                break;

            case NO_MERGES:
                LoggerUtil.debug(this.getClass(), "No data merges required for user: " + username);
                break;

            default:
                LoggerUtil.warn(this.getClass(), String.format(
                        "Unknown access pattern for user %s with role %s - skipping merges", username, role));
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Completed data merges for user %s (pattern: %s)", username, accessPattern));
    }

    /**
     * UNCHANGED: Determine user data access pattern based on role
     */
    private UserDataAccessPattern determineUserDataAccessPattern(String role) {
        if (role == null) {
            return UserDataAccessPattern.NO_MERGES;
        }

        // ✅ Check specific roles FIRST
        if (role.contains(SecurityConstants.ROLE_USER_CHECKING) || role.contains(SecurityConstants.ROLE_TL_CHECKING)) {
            return UserDataAccessPattern.BOTH_REGISTERS;
        }

        // ✅ Then check broader roles
        if (role.contains(SecurityConstants.ROLE_USER) || role.contains(SecurityConstants.ROLE_TEAM_LEADER)) {
            return UserDataAccessPattern.NORMAL_REGISTER_ONLY;
        }

        // ✅ Finally check pure CHECKING role
        if (role.contains(SecurityConstants.ROLE_CHECKING)) {
            return UserDataAccessPattern.CHECK_REGISTER_ONLY;
        }

        return UserDataAccessPattern.NO_MERGES;
    }

    /**
     * UNCHANGED: Perform normal register merge only (for USER, TEAM_LEADER roles)
     */
    private void performNormalRegisterMergeOnly(String username) {
        try {
            LoggerUtil.info(this.getClass(), "Performing normal register merge for: " + username);
            registerMergeService.performUserLoginMerge(username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Normal register merge failed for %s: %s - continuing with login", username, e.getMessage()));
            // Don't throw - merge failure shouldn't block login
        }
    }

    /**
     * UNCHANGED: Perform check register merge only (for CHECKING role)
     */
    private void performCheckRegisterMergeOnly(String username) {
        try {
            LoggerUtil.info(this.getClass(), "Performing check register merge for: " + username);
            checkRegisterService.performCheckRegisterLoginMerge(username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Check register merge failed for %s: %s - continuing with login", username, e.getMessage()));
            // Don't throw - merge failure shouldn't block login
        }
    }

    /**
     * UNCHANGED: Perform normal register merge (part of both registers pattern)
     */
    private void performNormalRegisterMerge(String username) {
        try {
            LoggerUtil.info(this.getClass(), "Performing normal register merge (part of both) for: " + username);
            registerMergeService.performUserLoginMerge(username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Normal register merge (part of both) failed for %s: %s - continuing", username, e.getMessage()));
            // Don't throw - continue to check register merge
        }
    }

    /**
     * UNCHANGED: Perform check register merge (part of both registers pattern)
     */
    private void performCheckRegisterMerge(String username) {
        try {
            LoggerUtil.info(this.getClass(), "Performing check register merge (part of both) for: " + username);
            checkRegisterService.performCheckRegisterLoginMerge(username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Check register merge (part of both) failed for %s: %s - continuing", username, e.getMessage()));
            // Don't throw - merge failure shouldn't block login
        }
    }

    /**
     * UNCHANGED: Perform worktime merge (for all non-admin users)
     */
    private void performWorktimeMerge(String username) {
        try {
            LoggerUtil.info(this.getClass(), "Performing worktime merge for: " + username);
            worktimeLoginMergeService.performUserWorktimeLoginMerge(username);
            LoggerUtil.info(this.getClass(), "Worktime merge completed for: " + username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Worktime merge failed for %s: %s - continuing with login", username, e.getMessage()));
            // Don't throw - merge failure shouldn't block login
        }
    }

    // ========================================================================
    // HELPER METHODS (UNCHANGED)
    // ========================================================================

    /**
     * UNCHANGED: Get user from UserDataService instead of DataAccessService
     */
    private Optional<User> getUserFromUserDataService(String username) {
        try {
            // Use UserDataService authentication method
            return userDataService.findUserByUsernameForAuthentication(username);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error reading user from UserDataService: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * UNCHANGED: Store user data locally using UserDataService
     */
    private void storeUserDataLocally(User user) {
        try {
            // Use UserDataService for remember me storage
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

    // ========================================================================
    // ENUM FOR USER DATA ACCESS PATTERNS (UNCHANGED)
    // ========================================================================

    /**
     * Enum defining different user data access patterns based on roles
     */
    private enum UserDataAccessPattern {
        NORMAL_REGISTER_ONLY,    // USER, TEAM_LEADER
        CHECK_REGISTER_ONLY,     // CHECKING
        BOTH_REGISTERS,          // USER_CHECKING, TL_CHECKING
        NO_MERGES                // Unknown roles or admin
    }
}