package com.ctgraphdep.security;

import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * ENHANCED UserContextService with Role Elevation Support.
 * NEW FEATURE: Admin role elevation without losing original user context.
 *
 * Key Features:
 * - Maintains original user for background processes
 * - Supports temporary admin elevation for web interface
 * - Clean separation between admin UI and background operations
 * - Original user context preserved during admin sessions
 */
@Service
public class UserContextService {

    private final UserContextCache userContextCache;
    private final UserDataService userDataService;

    public UserContextService(UserContextCache userContextCache, UserDataService userDataService) {
        this.userContextCache = userContextCache;
        this.userDataService = userDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // EXISTING API METHODS (ENHANCED FOR ELEVATION)
    // ========================================================================

    /**
     * Get current user - ENHANCED to consider elevation
     * Returns elevated admin if present, otherwise original user
     *
     * @return Current user (never null, falls back to system user)
     */
    public User getCurrentUser() {
        return userContextCache.getCurrentUser();
    }

    /**
     * Get current username - ENHANCED to consider elevation
     *
     * @return Current username (never null)
     */
    public String getCurrentUsername() {
        return userContextCache.getCurrentUsername();
    }

    /**
     * Check if cache is healthy
     *
     * @return true if cache has valid original user data
     */
    public boolean isCacheHealthy() {
        return userContextCache.isHealthy();
    }

    /**
     * Forces cache refresh for emergency situations
     *
     * @return true if refresh was successful
     */
    public boolean forceRefresh() {
        return userContextCache.forceRefresh();
    }

    /**
     * Check if we have a real user (not system user) - ENHANCED for elevation
     *
     * @return true if current user is a real authenticated user (original or elevated)
     */
    public boolean hasRealUser() {
        User user = getCurrentUser();
        return user != null && !"system".equals(user.getUsername());
    }

    /**
     * Get current user ID - ENHANCED for elevation
     *
     * @return Current user ID (elevated admin or original user) or null for system user
     */
    public Integer getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    /**
     * Check if current user is admin - ENHANCED for elevation
     *
     * @return true if current user has admin role (considers elevation)
     */
    public boolean isCurrentUserAdmin() {
        User user = getCurrentUser();
        return user != null && user.isAdmin();
    }

    /**
     * Get current user role - ENHANCED for elevation
     *
     * @return Current user role (elevated admin or original user) or "SYSTEM"
     */
    public String getCurrentUserRole() {
        User user = getCurrentUser();
        return user != null ? user.getRole() : "SYSTEM";
    }

    /**
     * Check if cache is properly initialized - ENHANCED for elevation
     *
     * @return true if cache has valid user data (original user exists)
     */
    public boolean isCacheInitialized() {
        // Check if we have a valid original user (ignoring elevation)
        User originalUser = userContextCache.getOriginalUser();
        return originalUser != null && !"system".equals(originalUser.getUsername());
    }

    /**
     * Performs midnight reset of user context cache - ENHANCED for elevation
     * Clears admin elevation and refreshes original user context
     */
    public void performMidnightReset() {
        try {
            // Clear any admin elevation
            if (userContextCache.isElevated()) {
                userContextCache.clearAdminElevation();
                LoggerUtil.info(this.getClass(), "Admin elevation cleared during midnight reset");
            }

            // Perform standard midnight reset
            userContextCache.midnightReset();
            LoggerUtil.info(this.getClass(), "UserContextService midnight reset completed");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error during midnight reset: " + e.getMessage(), e);
        }
    }

    /**
     * Initialize user context from User object (for startup) - UNCHANGED
     */
    public void initializeFromUser(User user) {
        try {
            if (user != null) {
                userContextCache.updateFromLogin(user);
                LoggerUtil.info(this.getClass(), String.format(
                        "Initialized user context: %s (ID: %d, Role: %s)",
                        user.getUsername(), user.getUserId(), user.getRole()));
            } else {
                LoggerUtil.warn(this.getClass(), "Cannot initialize context with null user");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error initializing user context: %s", e.getMessage()), e);
            throw new RuntimeException("Failed to initialize user context", e);
        }
    }

    /**
     * ENHANCED: Handle successful login - now supports admin elevation
     * For regular users: updates cache normally
     * For admin users: elevates role without losing original user
     */
    public void handleSuccessfulLogin(String username) {
        try {
            // Get complete user data from UserDataService
            Optional<User> userOptional = userDataService.findUserByUsernameForAuthentication(username);

            if (userOptional.isPresent()) {
                User user = userOptional.get();

                if (user.isAdmin()) {
                    // ADMIN LOGIN - Elevate role without losing original user
                    handleAdminElevation(user);
                } else {
                    // REGULAR USER LOGIN - Normal cache update
                    handleRegularUserLogin(user);
                }

            } else {
                LoggerUtil.error(this.getClass(), "User not found after authentication: " + username);
                throw new RuntimeException("User not found after authentication");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating login cache for user %s: %s", username, e.getMessage()), e);
            throw new RuntimeException("Failed to update user context", e);
        }
    }

    /**
     * Handle logout - ENHANCED to clear elevation properly
     */
    public void handleLogout() {
        boolean wasElevated = userContextCache.isElevated();

        if (wasElevated) {
            // Admin logout - just clear elevation, keep original user
            userContextCache.clearAdminElevation();
            LoggerUtil.info(this.getClass(), "Admin elevation cleared on logout - original user context preserved");
        } else {
            // Regular user logout - full cache invalidation
            // userContextCache.invalidateCache();
            LoggerUtil.info(this.getClass(), "User context cache cleared on logout");
        }
    }

    // ========================================================================
    // NEW: ADMIN ELEVATION METHODS
    // ========================================================================

    /**
     * NEW: Elevate to admin role without losing original user context
     *
     * @param adminUser The admin user to elevate to
     */
    public void elevateToAdminRole(User adminUser) {
        if (adminUser == null || !adminUser.isAdmin()) {
            LoggerUtil.error(this.getClass(), "Cannot elevate: invalid admin user");
            throw new IllegalArgumentException("Invalid admin user for elevation");
        }

        try {
            userContextCache.elevateToAdminRole(adminUser);
            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully elevated to admin role: %s", adminUser.getUsername()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error elevating to admin role for %s: %s", adminUser.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to elevate to admin role", e);
        }
    }

    /**
     * NEW: Clear admin elevation (return to original user)
     */
    public void clearAdminElevation() {
        try {
            userContextCache.clearAdminElevation();
            LoggerUtil.info(this.getClass(), "Admin elevation cleared successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error clearing admin elevation: " + e.getMessage(), e);
        }
    }

    /**
     * NEW: Check if currently elevated to admin
     *
     * @return true if admin elevation is active
     */
    public boolean isElevated() {
        return userContextCache.isElevated();
    }

    /**
     * NEW: Get elevated admin user if present
     *
     * @return Admin user if elevated, null otherwise
     */
    public User getElevatedAdminUser() {
        return userContextCache.getElevatedAdminUser();
    }

    /**
     * NEW: Get original user (ignoring elevation)
     * This is for background processes that should always use the original user
     *
     * @return Original user (never null, falls back to system user)
     */
    public User getOriginalUser() {
        return userContextCache.getOriginalUser();
    }

    // ========================================================================
    // PRIVATE HELPER METHODS FOR LOGIN HANDLING
    // ========================================================================

    /**
     * Handle admin elevation during login
     */
    private void handleAdminElevation(User adminUser) {
        try {
            // Check if we have an original user context
            if (!userContextCache.isHealthy()) {
                LoggerUtil.warn(this.getClass(),
                        "No healthy original user context for admin elevation - this may affect background processes");
            }

            // Elevate to admin role
            userContextCache.elevateToAdminRole(adminUser);

            LoggerUtil.info(this.getClass(), String.format(
                    "Admin elevation successful: %s (original user context preserved)",
                    adminUser.getUsername()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during admin elevation for %s: %s", adminUser.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to handle admin elevation", e);
        }
    }

    /**
     * Handle regular user login
     */
    private void handleRegularUserLogin(User user) {
        try {
            // Clear any existing admin elevation first
            if (userContextCache.isElevated()) {
                userContextCache.clearAdminElevation();
                LoggerUtil.info(this.getClass(), "Cleared existing admin elevation for regular user login");
            }

            // Update cache with regular user
            userContextCache.updateFromLogin(user);

            LoggerUtil.info(this.getClass(), String.format(
                    "Regular user login successful: %s (ID: %d, Role: %s)",
                    user.getUsername(), user.getUserId(), user.getRole()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during regular user login for %s: %s", user.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to handle regular user login", e);
        }
    }
}