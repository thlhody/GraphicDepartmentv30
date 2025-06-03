package com.ctgraphdep.security;

import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ENHANCED UserContextCache with Role Elevation Support.
 * NEW FEATURE: Admin role elevation without losing original user context.
 * Key Features:
 * - Maintains original user for background processes
 * - Supports temporary admin elevation for web interface
 * - Preserves existing functionality for non-admin operations
 * - Thread-safe elevation operations
 */
@Component
public class UserContextCache {

    private final UserDataService userDataService;

    // Thread-safe cache state
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final AtomicReference<CacheEntry> cacheEntry = new AtomicReference<>();
    private final AtomicReference<ElevationEntry> elevationEntry = new AtomicReference<>(); // NEW
    private final AtomicLong accessCounter = new AtomicLong(0);

    // Configuration
    private static final long CACHE_REFRESH_INTERVAL_MINUTES = 120; // 2 hours
    private static final long CACHE_HEALTH_CHECK_INTERVAL_MINUTES = 5; // 5 minutes
    private static final String SYSTEM_USERNAME = "system";

    // Health monitoring (protected by cacheLock)
    @Getter
    private LocalDateTime lastSuccessfulRefresh = null;
    private int consecutiveFailures = 0;
    private boolean emergencyMode = false;

    /**
     * Immutable cache entry for original user
     */
    private static class CacheEntry {
        final User user;
        final LocalDateTime timestamp;
        final String source;
        final long version;

        CacheEntry(User user, String source, long version) {
            this.user = user;
            this.timestamp = LocalDateTime.now();
            this.source = source;
            this.version = version;
        }

        boolean isExpired() {
            if (timestamp == null) return true;
            long ageMinutes = ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now());
            return ageMinutes >= CACHE_REFRESH_INTERVAL_MINUTES;
        }

        boolean isValid() {
            return user != null && user.getUsername() != null && !SYSTEM_USERNAME.equals(user.getUsername());
        }

        @Override
        public String toString() {
            return String.format("CacheEntry{user=%s, age=%d min, source=%s, version=%d}",
                    user != null ? user.getUsername() : "null",
                    timestamp != null ? ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now()) : -1,
                    source, version);
        }
    }

    /**
     * NEW: Elevation entry for admin user
     */
    private static class ElevationEntry {
        final User adminUser;
        final LocalDateTime elevationTime;
        final String elevationSource;
        final long version;

        ElevationEntry(User adminUser, String source, long version) {
            this.adminUser = adminUser;
            this.elevationTime = LocalDateTime.now();
            this.elevationSource = source;
            this.version = version;
        }

        boolean isValid() {
            return adminUser != null && adminUser.getUsername() != null && adminUser.isAdmin();
        }

        @Override
        public String toString() {
            return String.format("ElevationEntry{admin=%s, elevated=%d min ago, source=%s, version=%d}",
                    adminUser != null ? adminUser.getUsername() : "null",
                    elevationTime != null ? ChronoUnit.MINUTES.between(elevationTime, LocalDateTime.now()) : -1,
                    elevationSource, version);
        }
    }

    @Autowired
    public UserContextCache(UserDataService userDataService) {
        this.userDataService = userDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PostConstruct
    public void initialize() {
        LoggerUtil.info(this.getClass(), "Initializing UserContextCache with role elevation support");
        refreshCacheFromFile("initialization");
        LoggerUtil.info(this.getClass(), "UserContextCache initialized successfully");
    }

    // ========================================================================
    // EXISTING API METHODS (ENHANCED FOR ELEVATION)
    // ========================================================================

    /**
     * Gets the current user - ENHANCED to consider elevation
     * Returns elevated admin if present, otherwise original user
     * @return Current user (never null, falls back to system user)
     */
    public User getCurrentUser() {
        long accessId = accessCounter.incrementAndGet();

        cacheLock.readLock().lock();
        try {
            // Check for admin elevation first
            ElevationEntry elevation = elevationEntry.get();
            if (elevation != null && elevation.isValid()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Access #%d: Returning elevated admin user: %s", accessId, elevation.adminUser.getUsername()));
                return elevation.adminUser;
            }

            // No elevation - return original user logic
            CacheEntry entry = cacheEntry.get();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Access #%d: getCurrentUser() on thread %s, entry: %s",
                    accessId, Thread.currentThread().getName(), entry));

            // Check if we have a valid, non-expired entry
            if (entry != null && entry.isValid() && !entry.isExpired()) {
                LoggerUtil.debug(this.getClass(), String.format("Cache hit for user: %s (access #%d)",
                        entry.user.getUsername(), accessId));
                return entry.user;
            }

            // Cache miss or expired - need to refresh
            LoggerUtil.info(this.getClass(), String.format(
                    "Cache miss/expired (access #%d): entry=%s, triggering refresh", accessId, entry));

        } finally {
            cacheLock.readLock().unlock();
        }

        // Try to refresh cache (outside of read lock to avoid deadlock)
        User refreshedUser = attemptCacheRefresh("on-demand-" + accessId);
        if (refreshedUser != null && !SYSTEM_USERNAME.equals(refreshedUser.getUsername())) {
            return refreshedUser;
        }

        // Final fallback - return system user
        LoggerUtil.warn(this.getClass(), String.format(
                "Returning system user as fallback (access #%d)", accessId));
        return createSystemUser();
    }

    /**
     * Gets the current username - ENHANCED to consider elevation
     * @return Current username (never null)
     */
    public String getCurrentUsername() {
        User user = getCurrentUser();
        return user != null ? user.getUsername() : SYSTEM_USERNAME;
    }

    /**
     * Updates cache from login - UNCHANGED (for regular users)
     * @param user The authenticated user
     */
    public void updateFromLogin(User user) {
        if (user == null) {
            LoggerUtil.error(this.getClass(), "Attempted to update cache with null user");
            return;
        }

        long version = accessCounter.incrementAndGet();

        cacheLock.writeLock().lock();
        try {
            CacheEntry newEntry = new CacheEntry(user, "login", version);
            cacheEntry.set(newEntry);

            consecutiveFailures = 0; // Reset failure counter
            emergencyMode = false;   // Exit emergency mode
            lastSuccessfulRefresh = LocalDateTime.now();

            LoggerUtil.info(this.getClass(), String.format(
                    "Cache updated from login: %s (ID: %d, Role: %s, version: %d) on thread: %s",
                    user.getUsername(), user.getUserId(), user.getRole(), version,
                    Thread.currentThread().getName()));

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // NEW: ROLE ELEVATION API
    // ========================================================================

    /**
     * NEW: Elevate to admin role without losing original user context
     * @param adminUser The admin user to elevate to
     */
    public void elevateToAdminRole(User adminUser) {
        if (adminUser == null || !adminUser.isAdmin()) {
            LoggerUtil.error(this.getClass(), "Cannot elevate: invalid admin user");
            return;
        }

        long version = accessCounter.incrementAndGet();

        cacheLock.writeLock().lock();
        try {
            // Ensure we have an original user in cache
            CacheEntry originalEntry = cacheEntry.get();
            if (originalEntry == null || !originalEntry.isValid()) {
                LoggerUtil.warn(this.getClass(), "No valid original user in cache during admin elevation");
                // Still proceed with elevation - background processes will use system user if needed
            }

            // Create elevation entry
            ElevationEntry newElevation = new ElevationEntry(adminUser, "admin-login", version);
            elevationEntry.set(newElevation);

            LoggerUtil.info(this.getClass(), String.format(
                    "Admin role elevated: %s (original user: %s, version: %d)",
                    adminUser.getUsername(),
                    originalEntry != null ? originalEntry.user.getUsername() : "none",
                    version));

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * NEW: Get elevated admin user if present
     * @return Admin user if elevated, null otherwise
     */
    public User getElevatedAdminUser() {
        cacheLock.readLock().lock();
        try {
            ElevationEntry elevation = elevationEntry.get();
            if (elevation != null && elevation.isValid()) {
                return elevation.adminUser;
            }
            return null;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * NEW: Get original user (ignoring elevation)
     * This is for background processes that should always use the original user
     * @return Original user (never null, falls back to system user)
     */
    public User getOriginalUser() {
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = cacheEntry.get();
            if (entry != null && entry.isValid() && !entry.isExpired()) {
                return entry.user;
            }
        } finally {
            cacheLock.readLock().unlock();
        }

        // Try to refresh cache if original user is missing/expired
        User refreshedUser = attemptCacheRefresh("original-user-request");
        if (refreshedUser != null && !SYSTEM_USERNAME.equals(refreshedUser.getUsername())) {
            return refreshedUser;
        }

        return createSystemUser();
    }

    /**
     * NEW: Check if currently elevated to admin
     * @return true if admin elevation is active
     */
    public boolean isElevated() {
        cacheLock.readLock().lock();
        try {
            ElevationEntry elevation = elevationEntry.get();
            return elevation != null && elevation.isValid();
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * NEW: Clear admin elevation (return to original user)
     */
    public void clearAdminElevation() {
        cacheLock.writeLock().lock();
        try {
            ElevationEntry oldElevation = elevationEntry.get();
            elevationEntry.set(null);

            LoggerUtil.info(this.getClass(), String.format(
                    "Admin elevation cleared: %s",
                    oldElevation != null ? oldElevation.toString() : "none"));

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // EXISTING PAID HOLIDAY DAYS SUPPORT (ENHANCED FOR ELEVATION)
    // ========================================================================

    /**
     * Update paid holiday days with immediate write-through - ENHANCED for elevation
     * Uses elevated admin context if available, otherwise original user context
     */
    public boolean updatePaidHolidayDays(Integer newPaidHolidayDays) {
        cacheLock.writeLock().lock();
        try {
            // Determine which user context to update
            User targetUser;
            String contextType;

            ElevationEntry elevation = elevationEntry.get();
            if (elevation != null && elevation.isValid()) {
                // Admin is elevated - update admin context (though this is unusual)
                targetUser = elevation.adminUser;
                contextType = "elevated-admin";
            } else {
                // Normal operation - update original user
                CacheEntry currentEntry = cacheEntry.get();
                if (currentEntry == null || !currentEntry.isValid()) {
                    LoggerUtil.error(this.getClass(), "Cannot update holiday days: no valid user in cache");
                    return false;
                }
                targetUser = currentEntry.user;
                contextType = "original-user";
            }

            String username = targetUser.getUsername();
            Integer userId = targetUser.getUserId();

            LoggerUtil.info(this.getClass(), String.format(
                    "Updating paid holiday days for %s (%s): %d -> %d (write-through)",
                    username, contextType, targetUser.getPaidHolidayDays(), newPaidHolidayDays));

            // File update logic (same as before)
            try {
                boolean isOwnData = username.equals(getCurrentUsernameInternal());

                if (isOwnData) {
                    userDataService.updateUserHolidayDaysUser(username, userId, newPaidHolidayDays);
                } else {
                    userDataService.updateUserHolidayDaysAdmin(username, userId, newPaidHolidayDays);
                }

                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully updated user file for %s with %d paid holiday days",
                        username, newPaidHolidayDays));

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format(
                        "Failed to update user file for %s: %s", username, e.getMessage()), e);
                return false;
            }

            // Update cache after successful file write
            try {
                User updatedUser = cloneUser(targetUser);
                updatedUser.setPaidHolidayDays(newPaidHolidayDays);

                long version = accessCounter.incrementAndGet();

                if ("elevated-admin".equals(contextType)) {
                    // Update elevation entry
                    ElevationEntry newElevation = new ElevationEntry(updatedUser, "holiday-update", version);
                    elevationEntry.set(newElevation);
                } else {
                    // Update original user entry
                    CacheEntry newEntry = new CacheEntry(updatedUser, "holiday-update", version);
                    cacheEntry.set(newEntry);
                }

                LoggerUtil.info(this.getClass(), String.format(
                        "Cache updated after holiday days change: %s (%s) now has %d days (version: %d)",
                        username, contextType, newPaidHolidayDays, version));

                return true;

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format(
                        "Failed to update cache after holiday days change for %s: %s",
                        username, e.getMessage()), e);
                return true; // File update succeeded
            }

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Get current paid holiday days - ENHANCED for elevation
     */
    public Integer getCurrentPaidHolidayDays() {
        User currentUser = getCurrentUser();
        if (currentUser != null && !SYSTEM_USERNAME.equals(currentUser.getUsername())) {
            return currentUser.getPaidHolidayDays();
        }
        return null;
    }

    /**
     * Reduce paid holiday days - ENHANCED for elevation
     */
    public boolean reducePaidHolidayDays(int daysToReduce) {
        if (daysToReduce <= 0) {
            LoggerUtil.warn(this.getClass(), "Cannot reduce holiday days by non-positive amount: " + daysToReduce);
            return false;
        }

        Integer currentDays = getCurrentPaidHolidayDays();
        if (currentDays == null) {
            LoggerUtil.error(this.getClass(), "Cannot reduce holiday days: no current balance available");
            return false;
        }

        if (currentDays < daysToReduce) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Insufficient holiday days: requested %d, available %d", daysToReduce, currentDays));
            return false;
        }

        int newBalance = currentDays - daysToReduce;
        boolean success = updatePaidHolidayDays(newBalance);

        if (success) {
            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully reduced holiday days by %d: %d -> %d",
                    daysToReduce, currentDays, newBalance));
        }

        return success;
    }

    // ========================================================================
    // EXISTING METHODS (UNCHANGED)
    // ========================================================================

    /**
     * Invalidates the cache - ENHANCED to clear elevation
     */
    public void invalidateCache() {
        cacheLock.writeLock().lock();
        try {
            CacheEntry oldEntry = cacheEntry.get();
            ElevationEntry oldElevation = elevationEntry.get();

            cacheEntry.set(null);
            elevationEntry.set(null); // Clear elevation too

            LoggerUtil.warn(this.getClass(), String.format(
                    "Cache invalidated on thread %s, old entry: %s, old elevation: %s",
                    Thread.currentThread().getName(), oldEntry, oldElevation));

            logInvalidationStackTrace();

        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // SCHEDULED REFRESH MECHANISMS (UNCHANGED)
    // ========================================================================

    @Scheduled(fixedRate = CACHE_REFRESH_INTERVAL_MINUTES * 60 * 1000)
    public void scheduledRefresh() {
        LoggerUtil.info(this.getClass(), "Performing scheduled cache refresh");
        refreshCacheFromFile("scheduled");
    }

    @Scheduled(fixedRate = CACHE_HEALTH_CHECK_INTERVAL_MINUTES * 60 * 1000)
    public void healthCheck() {
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = cacheEntry.get();
            boolean needsEmergencyRefresh = false;

            if (entry == null) {
                LoggerUtil.warn(this.getClass(), "Health check: Cache entry is null");
                needsEmergencyRefresh = true;
            } else if (!entry.isValid()) {
                LoggerUtil.warn(this.getClass(), "Health check: Cache entry is invalid: " + entry);
                needsEmergencyRefresh = true;
            } else if (consecutiveFailures >= 3) {
                LoggerUtil.warn(this.getClass(), "Health check: Too many consecutive failures: " + consecutiveFailures);
                needsEmergencyRefresh = true;
            }

            if (needsEmergencyRefresh && !emergencyMode) {
                LoggerUtil.error(this.getClass(), "Health check FAILED - entering emergency mode");
                emergencyMode = true;
            }

        } finally {
            cacheLock.readLock().unlock();
        }

        if (emergencyMode) {
            LoggerUtil.info(this.getClass(), "Performing emergency cache refresh");
            refreshCacheFromFile("emergency");
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS (ENHANCED)
    // ========================================================================

    /**
     * Get current username without external call - ENHANCED for elevation
     */
    private String getCurrentUsernameInternal() {
        // Check elevation first
        ElevationEntry elevation = elevationEntry.get();
        if (elevation != null && elevation.isValid()) {
            return elevation.adminUser.getUsername();
        }

        // Fall back to original user
        CacheEntry entry = cacheEntry.get();
        if (entry != null && entry.isValid()) {
            return entry.user.getUsername();
        }
        return SYSTEM_USERNAME;
    }

    /**
     * Clone user object for cache updates
     */
    private User cloneUser(User original) {
        User clone = new User();
        clone.setUserId(original.getUserId());
        clone.setUsername(original.getUsername());
        clone.setName(original.getName());
        clone.setEmployeeId(original.getEmployeeId());
        clone.setSchedule(original.getSchedule());
        clone.setPaidHolidayDays(original.getPaidHolidayDays());
        clone.setPassword(original.getPassword());
        clone.setRole(original.getRole());
        return clone;
    }

    // [Rest of the private methods remain unchanged - refreshCacheFromFile, attemptCacheRefresh, etc.]

    private User attemptCacheRefresh(String trigger) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Attempting cache refresh (trigger: %s)", trigger));

            Optional<User> userOpt = userDataService.scanForAnyLocalUser();

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                long version = accessCounter.incrementAndGet();
                CacheEntry newEntry = new CacheEntry(user, trigger, version);

                cacheLock.writeLock().lock();
                try {
                    cacheEntry.set(newEntry);
                    consecutiveFailures = 0;
                    emergencyMode = false;
                    lastSuccessfulRefresh = LocalDateTime.now();

                    LoggerUtil.info(this.getClass(), String.format(
                            "Cache refreshed successfully: %s (trigger: %s, version: %d)",
                            user.getUsername(), trigger, version));

                    return user;

                } finally {
                    cacheLock.writeLock().unlock();
                }

            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No local user found during refresh (trigger: %s)", trigger));
                recordRefreshFailure(trigger);
                return null;
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error during cache refresh (trigger: %s): %s", trigger, e.getMessage()), e);
            recordRefreshFailure(trigger);
            return null;
        }
    }

    private void refreshCacheFromFile(String trigger) {
        User refreshedUser = attemptCacheRefresh(trigger);
        if (refreshedUser == null) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Cache refresh failed (trigger: %s), consecutive failures: %d",
                    trigger, consecutiveFailures));
        }
    }

    private void recordRefreshFailure(String trigger) {
        cacheLock.writeLock().lock();
        try {
            consecutiveFailures++;
            if (consecutiveFailures >= 5) {
                emergencyMode = true;
                LoggerUtil.error(this.getClass(), String.format(
                        "Entering emergency mode after %d consecutive failures (trigger: %s)",
                        consecutiveFailures, trigger));
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private User createSystemUser() {
        User systemUser = new User();
        systemUser.setUsername(SYSTEM_USERNAME);
        systemUser.setUserId(0);
        systemUser.setName("System User");
        systemUser.setRole("SYSTEM");
        systemUser.setSchedule(8);
        return systemUser;
    }

    private void logInvalidationStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder("Cache invalidation stack trace:\n");
        for (int i = 3; i < Math.min(stackTrace.length, 10); i++) {
            sb.append("  ").append(stackTrace[i].toString()).append("\n");
        }
        LoggerUtil.debug(this.getClass(), sb.toString());
    }

    // ========================================================================
    // DIAGNOSTICS AND MONITORING (ENHANCED)
    // ========================================================================

    public boolean isHealthy() {
        cacheLock.readLock().lock();
        try {
            CacheEntry entry = cacheEntry.get();
            return entry != null && entry.isValid() && !emergencyMode && consecutiveFailures < 3;
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    public boolean forceRefresh() {
        LoggerUtil.warn(this.getClass(), "Force refresh requested");
        User refreshedUser = attemptCacheRefresh("force");
        return refreshedUser != null && !SYSTEM_USERNAME.equals(refreshedUser.getUsername());
    }

    public void midnightReset() {
        cacheLock.writeLock().lock();
        try {
            long oldAccessCount = accessCounter.get();
            accessCounter.set(0);
            consecutiveFailures = 0;
            emergencyMode = false;

            // Clear admin elevation at midnight
            ElevationEntry oldElevation = elevationEntry.get();
            if (oldElevation != null) {
                elevationEntry.set(null);
                LoggerUtil.info(this.getClass(), "Admin elevation cleared during midnight reset");
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Midnight reset completed - Access counter: %d -> 0, Emergency mode: false, Failures: 0, Elevation cleared: %s",
                    oldAccessCount, oldElevation != null));

        } finally {
            cacheLock.writeLock().unlock();
        }

        refreshCacheFromFile("midnight-reset");
    }

    @PreDestroy
    public void shutdown() {
        LoggerUtil.info(this.getClass(), "UserContextCache shutting down");

        cacheLock.writeLock().lock();
        try {
            CacheEntry entry = cacheEntry.get();
            ElevationEntry elevation = elevationEntry.get();
            LoggerUtil.info(this.getClass(), String.format(
                    "Final cache state: %s, elevation: %s, access count: %d",
                    entry, elevation, accessCounter.get()));
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
}