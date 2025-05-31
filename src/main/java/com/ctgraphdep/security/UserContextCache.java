package com.ctgraphdep.security;

import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * REFACTORED UserContextCache using UserDataService for all I/O operations.
 * Single-user context cache for applications where each PC has one user.
 * Provides user context to both web requests and background tasks.
 * Features:
 * - Strategy 1: Interactive login updates cache
 * - Strategy 2: Background autoload using UserDataService (network first, local fallback)
 * - Thread-safe operations
 * - Graceful fallback to "system" user
 * - Integrated with UserDataService for consistent file operations
 */
@Component
public class UserContextCache {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Cache state - NO UserDataService dependency
    private volatile User currentUser;
    private volatile long lastUpdated;
    private volatile boolean cacheValid;

    // Cache settings
    private static final long CACHE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    private static final String SYSTEM_USERNAME = "system";

    // NO UserDataService in constructor - breaks circular dependency
    public UserContextCache() {
        LoggerUtil.initialize(this.getClass(), null);
        LoggerUtil.info(this.getClass(), "UserContextCache initialized - login-only mode");
    }

    /**
     * ONLY STRATEGY: Update cache from login
     * This is called by AuthenticationService after successful login
     */
    public void updateFromLogin(User user) {
        lock.writeLock().lock();
        try {
            if (user != null) {
                this.currentUser = user;
                this.lastUpdated = System.currentTimeMillis();
                this.cacheValid = true;
                LoggerUtil.info(this.getClass(), String.format(
                        "Cache updated from login: %s (ID: %d, Role: %s)",
                        user.getUsername(), user.getUserId(), user.getRole()));
            } else {
                invalidateCache();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get current user - NO autoloading, login-only
     */
    public User getCurrentUser() {
        // Simple check: is cache valid?
        if (isCacheValid() && currentUser != null) {
            return currentUser;
        }

        // No autoloading! Return system user if no login
        LoggerUtil.debug(this.getClass(), "No valid user in cache, returning system user");
        return createSystemUser();
    }

    public String getCurrentUsername() {
        User user = getCurrentUser();
        return user != null ? user.getUsername() : SYSTEM_USERNAME;
    }

    public void invalidateCache() {
        lock.writeLock().lock();
        try {
            this.currentUser = null;
            this.lastUpdated = 0;
            this.cacheValid = false;
            LoggerUtil.info(this.getClass(), "User context cache invalidated");
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean isCacheValid() {
        return cacheValid &&
                currentUser != null &&
                (System.currentTimeMillis() - lastUpdated) < CACHE_TIMEOUT_MS;
    }

    private User createSystemUser() {
        User systemUser = new User();
        systemUser.setUsername(SYSTEM_USERNAME);
        systemUser.setUserId(null);
        systemUser.setName("System User");
        systemUser.setRole("SYSTEM");
        return systemUser;
    }
}