package com.ctgraphdep.security;

import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * REFACTORED: Service that provides user context for both web and background operations.
 * FIXED CIRCULAR DEPENDENCY: Now uses UserDataService directly instead of UserService.
 * OLD: UserContextService → UserService → UserContextService (CIRCULAR)
 * NEW: UserContextService → UserDataService (CLEAN)
 * This service acts as the primary interface for getting current user information
 * throughout the application, replacing the need for complex authentication context management.
 */
@Service
public class UserContextService {

    private final UserContextCache userContextCache;
    private final UserDataService userDataService;  // CHANGED: Direct UserDataService instead of UserService

    public UserContextService(UserContextCache userContextCache, UserDataService userDataService) {
        this.userContextCache = userContextCache;
        this.userDataService = userDataService;  // CHANGED: Inject UserDataService
        LoggerUtil.initialize(this.getClass(), null);
    }
    /**
     * Initialize user context from User object (for startup)
     * @param user The user to set in context
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
     * REFACTORED: Handle successful login - updates cache with authenticated user
     * Now uses UserDataService directly for user lookup
     * @param username The authenticated username
     */
    public void handleSuccessfulLogin(String username) {
        try {
            // CHANGED: Get complete user data from UserDataService instead of UserService
            Optional<User> userOptional = userDataService.findUserByUsernameForAuthentication(username);

            if (userOptional.isPresent()) {
                User user = userOptional.get();

                // Update cache with complete user data
                userContextCache.updateFromLogin(user);

                LoggerUtil.info(this.getClass(), String.format(
                        "Login cache updated for user: %s (ID: %d, Role: %s)",
                        user.getUsername(), user.getUserId(), user.getRole()));
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
     * Handle logout - invalidates cache
     */
    public void handleLogout() {
        userContextCache.invalidateCache();
        LoggerUtil.info(this.getClass(), "User context cache cleared on logout");
    }

    /**
     * Get current user - works for both web and background contexts
     * @return Current user (never null, falls back to system user)
     */
    public User getCurrentUser() {
        return userContextCache.getCurrentUser();
    }

    /**
     * Get current username - convenience method
     * @return Current username or "system"
     */
    public String getCurrentUsername() {
        return userContextCache.getCurrentUsername();
    }

    /**
     * REFACTORED: Get sanitized current user (for display purposes)
     * Now uses UserDataService for lookup instead of UserService
     * @return Sanitized user without sensitive information
     */
    public User getCurrentUserSanitized() {
        User user = getCurrentUser();
        if (user == null || "system".equals(user.getUsername())) {
            return user; // System user is already clean
        }

        // CHANGED: Get sanitized version from UserDataService instead of UserService
        // Use smart fallback pattern: local first for own data
        Optional<User> sanitizedUser = userDataService.userReadLocalReadOnly(user.getUsername(), user.getUserId(), user.getUsername());

        if (sanitizedUser.isPresent()) {
            // Return user without password (UserDataService read method provides this)
            User cleanUser = sanitizedUser.get();
            cleanUser.setPassword(null); // Ensure no password in sanitized version
            return cleanUser;
        }

        return user; // Fallback to cached user
    }

    /**
     * Check if we have a real user (not system user)
     * @return true if current user is a real authenticated user
     */
    public boolean hasRealUser() {
        User user = getCurrentUser();
        return user != null && !"system".equals(user.getUsername());
    }

    /**
     * Get current user ID - convenience method
     * @return Current user ID or null for system user
     */
    public Integer getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    /**
     * Check if current user is admin
     * @return true if current user has admin role
     */
    public boolean isCurrentUserAdmin() {
        User user = getCurrentUser();
        return user != null && user.isAdmin();
    }

    /**
     * Get current user role
     * @return Current user role or "SYSTEM"
     */
    public String getCurrentUserRole() {
        User user = getCurrentUser();
        return user != null ? user.getRole() : "SYSTEM";
    }

    /**
     * Check if cache is properly initialized
     * @return true if cache has valid user data
     */
    public boolean isCacheInitialized() {
        return hasRealUser();
    }
}