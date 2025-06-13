package com.ctgraphdep.service.cache;

import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UNIFIED CacheImplementation - Single Entry Point for All Cache Operations
 * This service acts as a facade for all cache services in the application, providing:
 * - Single point of access for all cache operations
 * - Simplified method signatures
 * - Consistent error handling and logging
 * - Easy maintenance and refactoring
 * Usage: Inject CacheImplementation instead of individual cache services
 * Benefits: Cleaner code, centralized cache management, easier testing
 */
@Service
public class CacheImplementation {

    // All cache service dependencies
    private final MainDefaultUserContextCache userContextCache;
    private final AllUsersCacheService allUsersCache;
    private final SessionCacheService sessionCache;
    private final TimeOffCacheService timeOffCache;
    private final WorktimeCacheService worktimeCache;
    private final RegisterCacheService registerCache;
    private final RegisterCheckCacheService registerCheckCache;
    private final CheckValuesCacheManager checkValuesCache;

    @Autowired
    public CacheImplementation(
            MainDefaultUserContextCache userContextCache,
            AllUsersCacheService allUsersCache,
            SessionCacheService sessionCache,
            TimeOffCacheService timeOffCache,
            WorktimeCacheService worktimeCache,
            RegisterCacheService registerCache,
            RegisterCheckCacheService registerCheckCache,
            CheckValuesCacheManager checkValuesCache) {

        this.userContextCache = userContextCache;
        this.allUsersCache = allUsersCache;
        this.sessionCache = sessionCache;
        this.timeOffCache = timeOffCache;
        this.worktimeCache = worktimeCache;
        this.registerCache = registerCache;
        this.registerCheckCache = registerCheckCache;
        this.checkValuesCache = checkValuesCache;

        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // USER CONTEXT OPERATIONS (MainDefaultUserContextCache)
    // ========================================================================

    /**
     * Get current user (considers admin elevation)
     * @return Current user (never null, falls back to system user)
     */
    public User getCurrentUser() {
        return userContextCache.getCurrentUser();
    }

    /**
     * Get current username (considers admin elevation)
     * @return Current username (never null)
     */
    public String getCurrentUsername() {
        return userContextCache.getCurrentUsername();
    }

    /**
     * Get current user ID (considers admin elevation)
     * @return Current user ID or null for system user
     */
    public Integer getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    /**
     * Check if current user is admin (considers admin elevation)
     * @return true if current user has admin role
     */
    public boolean isCurrentUserAdmin() {
        User user = getCurrentUser();
        return user != null && user.isAdmin();
    }

    /**
     * Get current user role (considers admin elevation)
     * @return Current user role or "SYSTEM"
     */
    public String getCurrentUserRole() {
        User user = getCurrentUser();
        return user != null ? user.getRole() : "SYSTEM";
    }

    /**
     * Check if currently elevated to admin
     * @return true if admin elevation is active
     */
    public boolean isElevated() {
        return userContextCache.isElevated();
    }

    /**
     * Elevate to admin role without losing original user context
     * @param adminUser The admin user to elevate to
     */
    public void elevateToAdminRole(User adminUser) {
        userContextCache.elevateToAdminRole(adminUser);
    }

    /**
     * Clear admin elevation (return to original user)
     */
    public void clearAdminElevation() {
        userContextCache.clearAdminElevation();
    }

    /**
     * Get elevated admin user if present
     * @return Admin user if elevated, null otherwise
     */
    public User getElevatedAdminUser() {
        return userContextCache.getElevatedAdminUser();
    }

    /**
     * Get original user (ignoring elevation) - for background processes
     * @return Original user (never null, falls back to system user)
     */
    public User getOriginalUser() {
        return userContextCache.getOriginalUser();
    }

    /**
     * Get current paid holiday days (considers admin elevation)
     * @return Current paid holiday days balance
     */
    public Integer getCurrentPaidHolidayDays() {
        return userContextCache.getCurrentPaidHolidayDays();
    }

    /**
     * Update paid holiday days with immediate write-through
     * @param newPaidHolidayDays New holiday balance
     * @return true if update was successful
     */
    public boolean updatePaidHolidayDays(Integer newPaidHolidayDays) {
        return userContextCache.updatePaidHolidayDays(newPaidHolidayDays);
    }

    /**
     * Reduce paid holiday days by specified amount
     * @param daysToReduce Number of days to reduce
     * @return true if reduction was successful
     */
    public boolean reducePaidHolidayDays(int daysToReduce) {
        return userContextCache.reducePaidHolidayDays(daysToReduce);
    }

    /**
     * Check if user context cache is healthy
     * @return true if cache has valid original user data
     */
    public boolean isUserContextHealthy() {
        return userContextCache.isHealthy();
    }

    /**
     * Force refresh of user context cache
     * @return true if refresh was successful
     */
    public boolean forceRefreshUserContext() {
        return userContextCache.forceRefresh();
    }

    /**
     * Invalidate user context cache
     */
    public void invalidateUserContext() {
        userContextCache.invalidateCache();
    }

    /**
     * Perform midnight reset of user context cache
     */
    public void performUserContextMidnightReset() {
        userContextCache.midnightReset();
    }

    // ========================================================================
    // ALL USERS CACHE OPERATIONS (AllUsersCacheService)
    // ========================================================================

    /**
     * Get all users as User objects (primary method for user operations)
     * @return List of User objects from cache
     */
    public List<User> getAllUsers() {
        return allUsersCache.getAllUsersAsUserObjects();
    }

    /**
     * Get specific user as User object
     * @param username Username to lookup
     * @return User object from cache, or empty if not found
     */
    public Optional<User> getUserByUsernameAsObject(String username) {
        return allUsersCache.getUserAsUserObject(username);
    }

    /**
     * Get user by ID as User object
     * @param userId User ID to lookup
     * @return User object from cache, or empty if not found
     */
    public Optional<User> getUserById(Integer userId) {
        return allUsersCache.getUserByIdAsUserObject(userId);
    }

    /**
     * Get non-admin users as User objects
     * @return List of non-admin User objects from cache
     */
    public List<User> getNonAdminUsers() {
        return allUsersCache.getNonAdminUsersAsUserObjects();
    }

    /**
     * Update user in cache (write-through support)
     * @param user Updated user object
     */
    public void updateUserInCache(User user) {
        allUsersCache.updateUserInCache(user);
    }

    /**
     * Remove user from cache (for user deletion)
     * @param username Username to remove
     */
    public void removeUserFromCache(String username) {
        allUsersCache.removeUserFromCache(username);
    }

    /**
     * Get all user statuses from cache (for UI display)
     * @return List of UserStatusDTO for display
     */
    public List<UserStatusDTO> getAllUserStatuses() {
        return allUsersCache.getAllUserStatuses();
    }

    /**
     * Update user status in cache (memory-only)
     * @param username The username
     * @param userId The user ID
     * @param status The new status
     * @param timestamp The timestamp
     */
    public void updateUserStatus(String username, Integer userId, String status, LocalDateTime timestamp) {
        allUsersCache.updateUserStatus(username, userId, status, timestamp);
    }

    /**
     * Check if cache has any user data
     * @return true if cache has valid user entries
     */
    public boolean hasUserData() {
        return allUsersCache.hasUserData();
    }

    /**
     * Get count of cached users
     * @return Number of valid user entries in cache
     */
    public int getCachedUserCount() {
        return allUsersCache.getCachedUserCount();
    }

    /**
     * Refresh all users from UserDataService with complete data
     */
    public void refreshAllUsersFromDataService() {
        allUsersCache.refreshAllUsersFromUserDataServiceWithCompleteData();
    }

    /**
     * Sync user statuses from network flags
     */
    public void syncUserStatusesFromNetwork() {
        allUsersCache.syncFromNetworkFlags();
    }

    /**
     * Write user status cache to file
     */
    public void writeUserStatusesToFile() {
        allUsersCache.writeToFile();
    }

    /**
     * Clear all user status cache
     */
    public void clearAllUsersCache() {
        allUsersCache.clearAllCache();
    }

    // ========================================================================
    // SESSION CACHE OPERATIONS (SessionCacheService)
    // ========================================================================

    /**
     * Read session from cache (primary method for all session reads)
     * @param username The username
     * @param userId The user ID
     * @return Session data from cache or file
     */
    public WorkUsersSessionsStates readSession(String username, Integer userId) {
        return sessionCache.readSession(username, userId);
    }

    /**
     * Refresh cache from file data (called after commands write to file)
     * @param username The username
     * @param sessionData Updated session data from file
     */
    public void refreshSessionFromFile(String username, WorkUsersSessionsStates sessionData) {
        sessionCache.refreshCacheFromFile(username, sessionData);
    }

    /**
     * Update only calculated values in cache (called by SessionMonitorService)
     * @param username The username
     * @param calculatedSession Session with updated calculations
     */
    public void updateSessionCalculatedValues(String username, WorkUsersSessionsStates calculatedSession) {
        sessionCache.updateCalculatedValues(username, calculatedSession);
    }

    /**
     * Clear cache for specific user (midnight reset)
     * @param username The username
     */
    public void clearUserSessionCache(String username) {
        sessionCache.clearUserCache(username);
    }

    /**
     * Clear entire session cache (full reset)
     */
    public void clearAllSessionCache() {
        sessionCache.clearAllCache();
    }

    // ========================================================================
    // TIME OFF CACHE OPERATIONS (TimeOffCacheService)
    // ========================================================================

    /**
     * Load user's timeoff session on page access
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return true if session was loaded successfully
     */
    public boolean loadTimeOffSession(String username, Integer userId, int year) {
        return timeOffCache.loadUserSession(username, userId, year);
    }

    /**
     * Get time off tracker from cache (fast read)
     * @param username Username
     * @param year Year
     * @return TimeOffTracker or null if not cached
     */
    public TimeOffTracker getTimeOffTracker(String username, int year) {
        return timeOffCache.getTracker(username, year);
    }

    /**
     * Get time off summary from cached tracker (fast display)
     * @param username Username
     * @param year Year
     * @return TimeOffSummaryDTO with calculated values
     */
    public TimeOffSummaryDTO getTimeOffSummary(String username, int year) {
        return timeOffCache.getSummary(username, year);
    }

    /**
     * Get upcoming time off from cached tracker (fast display)
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return List of upcoming WorkTimeTable entries
     */
    public List<WorkTimeTable> getUpcomingTimeOff(String username, Integer userId, int year) {
        return timeOffCache.getUpcomingTimeOff(username, userId, year);
    }

    /**
     * Add time off through cache WITHOUT holiday balance adjustment
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param dates List of dates to add
     * @param timeOffType Type of time off (CO/CM/SN)
     * @return true if operation was successful
     */
    public boolean addTimeOffToCache(String username, Integer userId, int year, List<LocalDate> dates, String timeOffType) {
        return timeOffCache.addTimeOffToCacheWithoutBalanceUpdate(username, userId, year, dates, timeOffType);
    }

    /**
     * Remove time off through cache WITHOUT holiday balance adjustment
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param date Date to remove
     * @return true if operation was successful
     */
    public boolean removeTimeOffFromCache(String username, Integer userId, int year, LocalDate date) {
        return timeOffCache.removeTimeOffFromCacheWithoutBalanceUpdate(username, userId, year, date);
    }

    /**
     * Invalidate user timeoff session (manual refresh or timeout)
     * @param username Username
     * @param year Year
     */
    public void invalidateTimeOffSession(String username, int year) {
        timeOffCache.invalidateUserSession(username, year);
    }

    /**
     * Check if user has active timeoff session
     * @param username Username
     * @param year Year
     * @return true if session is active and valid
     */
    public boolean hasActiveTimeOffSession(String username, int year) {
        return timeOffCache.hasActiveSession(username, year);
    }

    /**
     * Clean up expired timeoff sessions
     */
    public void cleanupExpiredTimeOffSessions() {
        timeOffCache.cleanupExpiredSessions();
    }

    // ========================================================================
    // WORKTIME CACHE OPERATIONS (WorktimeCacheService)
    // ========================================================================

    /**
     * Load user's worktime session for specific month
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return true if session was loaded successfully
     */
    public boolean loadWorktimeSession(String username, Integer userId, int year, int month) {
        return worktimeCache.loadUserMonthSession(username, userId, year, month);
    }

    /**
     * Switch user to different month (common operation in time management)
     * @param username Username
     * @param userId User ID
     * @param newYear New year
     * @param newMonth New month
     * @return true if switch was successful
     */
    public boolean switchWorktimeToMonth(String username, Integer userId, int newYear, int newMonth) {
        return worktimeCache.switchUserToMonth(username, userId, newYear, newMonth);
    }

    /**
     * Get month entries from cache (fast read)
     * @param username Username
     * @param year Year
     * @param month Month
     * @return List of WorkTimeTable entries for the month
     */
    public List<WorkTimeTable> getWorktimeEntries(String username, int year, int month) {
        return worktimeCache.getMonthEntries(username, year, month);
    }

    /**
     * Get specific entry for date from cache
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param date Date
     * @return WorkTimeTable entry or empty if not found
     */
    public Optional<WorkTimeTable> getWorktimeEntryForDate(String username, Integer userId, int year, int month, LocalDate date) {
        return worktimeCache.getEntryForDate(username, userId, year, month, date);
    }

    /**
     * Update start time through cache with write-through persistence
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param date Date
     * @param startTime Start time
     * @return true if update was successful
     */
    public boolean updateWorktimeStartTime(String username, Integer userId, int year, int month, LocalDate date, LocalDateTime startTime) {
        return worktimeCache.updateStartTime(username, userId, year, month, date, startTime);
    }

    /**
     * Update end time through cache with write-through persistence
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param date Date
     * @param endTime End time
     * @return true if update was successful
     */
    public boolean updateWorktimeEndTime(String username, Integer userId, int year, int month, LocalDate date, LocalDateTime endTime) {
        return worktimeCache.updateEndTime(username, userId, year, month, date, endTime);
    }

    /**
     * Add time off entry through cache with write-through persistence
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param date Date
     * @param timeOffType Time off type
     * @return true if operation was successful
     */
    public boolean addWorktimeTimeOffEntry(String username, Integer userId, int year, int month, LocalDate date, String timeOffType) {
        return worktimeCache.addTimeOffEntry(username, userId, year, month, date, timeOffType);
    }

    /**
     * Remove time off entry through cache with write-through persistence
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @param date Date
     * @return true if operation was successful
     */
    public boolean removeWorktimeTimeOffEntry(String username, Integer userId, int year, int month, LocalDate date) {
        return worktimeCache.removeTimeOffEntry(username, userId, year, month, date);
    }

    /**
     * Invalidate specific user month session
     * @param username Username
     * @param year Year
     * @param month Month
     */
    public void invalidateWorktimeSession(String username, int year, int month) {
        worktimeCache.invalidateUserMonthSession(username, year, month);
    }

    /**
     * Invalidate all sessions for user (logout/refresh)
     * @param username Username
     */
    public void invalidateAllWorktimeSessions(String username) {
        worktimeCache.invalidateAllUserSessions(username);
    }

    /**
     * Check if user has active session for month
     * @param username Username
     * @param year Year
     * @param month Month
     * @return true if session is active and valid
     */
    public boolean hasActiveWorktimeSession(String username, int year, int month) {
        return worktimeCache.hasActiveMonthSession(username, year, month);
    }

    /**
     * Clean up expired worktime sessions
     */
    public void cleanupExpiredWorktimeSessions() {
        worktimeCache.cleanupExpiredSessions();
    }

    // ========================================================================
    // REGISTER CACHE OPERATIONS (RegisterCacheService)
    // ========================================================================

    /**
     * Get register entries for a specific month (loads from file if not cached)
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return List of register entries for the month
     */
    public List<RegisterEntry> getRegisterEntries(String username, Integer userId, int year, int month) {
        return registerCache.getMonthEntries(username, userId, year, month);
    }

    /**
     * Add new register entry with write-through
     * @param username Username
     * @param userId User ID
     * @param entry Register entry to add
     * @return true if entry was added successfully
     */
    public boolean addRegisterEntry(String username, Integer userId, RegisterEntry entry) {
        return registerCache.addEntry(username, userId, entry);
    }

    /**
     * Update existing register entry with write-through
     * @param username Username
     * @param userId User ID
     * @param entry Register entry to update
     * @return true if entry was updated successfully
     */
    public boolean updateRegisterEntry(String username, Integer userId, RegisterEntry entry) {
        return registerCache.updateEntry(username, userId, entry);
    }

    /**
     * Delete register entry with write-through
     * @param username Username
     * @param userId User ID
     * @param entryId Entry ID to delete
     * @param year Year
     * @param month Month
     * @return true if entry was deleted successfully
     */
    public boolean deleteRegisterEntry(String username, Integer userId, Integer entryId, int year, int month) {
        return registerCache.deleteEntry(username, userId, entryId, year, month);
    }

    /**
     * Get specific register entry
     * @param username Username
     * @param userId User ID
     * @param entryId Entry ID
     * @param year Year
     * @param month Month
     * @return Register entry or null if not found
     */
    public RegisterEntry getRegisterEntry(String username, Integer userId, Integer entryId, int year, int month) {
        return registerCache.getEntry(username, userId, entryId, year, month);
    }

    /**
     * Clear specific month from register cache
     * @param username Username
     * @param year Year
     * @param month Month
     */
    public void clearRegisterMonth(String username, int year, int month) {
        registerCache.clearMonth(username, year, month);
    }

    /**
     * Clear entire register cache
     */
    public void clearAllRegisterCache() {
        registerCache.clearAllCache();
    }

    // ========================================================================
    // CHECK REGISTER CACHE OPERATIONS (RegisterCheckCacheService)
    // ========================================================================

    /**
     * Get check register entries for a specific month (loads from file if not cached)
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return List of check register entries for the month
     */
    public List<RegisterCheckEntry> getCheckRegisterEntries(String username, Integer userId, int year, int month) {
        return registerCheckCache.getMonthEntries(username, userId, year, month);
    }

    /**
     * Add new check register entry with write-through
     * @param username Username
     * @param userId User ID
     * @param entry Check register entry to add
     * @return true if entry was added successfully
     */
    public boolean addCheckRegisterEntry(String username, Integer userId, RegisterCheckEntry entry) {
        return registerCheckCache.addEntry(username, userId, entry);
    }

    /**
     * Update existing check register entry with write-through
     * @param username Username
     * @param userId User ID
     * @param entry Check register entry to update
     * @return true if entry was updated successfully
     */
    public boolean updateCheckRegisterEntry(String username, Integer userId, RegisterCheckEntry entry) {
        return registerCheckCache.updateEntry(username, userId, entry);
    }

    /**
     * Delete check register entry with write-through
     * @param username Username
     * @param userId User ID
     * @param entryId Entry ID to delete
     * @param year Year
     * @param month Month
     * @return true if entry was deleted successfully
     */
    public boolean deleteCheckRegisterEntry(String username, Integer userId, Integer entryId, int year, int month) {
        return registerCheckCache.deleteEntry(username, userId, entryId, year, month);
    }

    /**
     * Get specific check register entry
     * @param username Username
     * @param userId User ID
     * @param entryId Entry ID
     * @param year Year
     * @param month Month
     * @return Check register entry or null if not found
     */
    public RegisterCheckEntry getCheckRegisterEntry(String username, Integer userId, Integer entryId, int year, int month) {
        return registerCheckCache.getEntry(username, userId, entryId, year, month);
    }

    /**
     * Clear specific month from check register cache
     * @param username Username
     * @param year Year
     * @param month Month
     */
    public void clearCheckRegisterMonth(String username, int year, int month) {
        registerCheckCache.clearMonth(username, year, month);
    }

    /**
     * Clear entire check register cache
     */
    public void clearAllCheckRegisterCache() {
        registerCheckCache.clearAllCache();
    }

    // ========================================================================
    // CHECK VALUES CACHE OPERATIONS (CheckValuesCacheManager)
    // ========================================================================

    /**
     * Get user's check values from cache
     * @param username Username
     * @return Cached check values or null if not in cache
     */
    public CheckValuesEntry getCheckValues(String username) {
        return checkValuesCache.getCachedCheckValues(username);
    }

    /**
     * Cache user's check values in memory
     * @param username Username
     * @param values Check values to cache
     */
    public void cacheCheckValues(String username, CheckValuesEntry values) {
        checkValuesCache.cacheCheckValues(username, values);
    }

    /**
     * Check if user has cached check values
     * @param username Username
     * @return true if user has cached values
     */
    public boolean hasCachedCheckValues(String username) {
        return checkValuesCache.hasCachedCheckValues(username);
    }

    /**
     * Get target work units per hour from cache
     * @param username Username
     * @return Target work units per hour
     */
    public double getTargetWorkUnitsPerHour(String username) {
        return checkValuesCache.getTargetWorkUnitsPerHour(username);
    }

    /**
     * Get value for specific check type from cache
     * @param username Username
     * @param checkType Check type
     * @return Value for the check type, or default if not found
     */
    public double getCheckTypeValue(String username, String checkType) {
        return checkValuesCache.getCheckTypeValue(username, checkType);
    }

    /**
     * Clear all cached check values
     */
    public void clearAllCheckValuesCache() {
        checkValuesCache.clearAllCachedCheckValues();
    }

    // ========================================================================
    // GLOBAL CACHE MANAGEMENT OPERATIONS
    // ========================================================================

    /**
     * Clear all caches across the entire application
     */
    public void clearAllCaches() {
        LoggerUtil.info(this.getClass(), "Clearing all caches across the application");

        // Clear all individual caches
        clearAllUsersCache();
        clearAllSessionCache();
        cleanupExpiredTimeOffSessions();
        cleanupExpiredWorktimeSessions();
        clearAllRegisterCache();
        clearAllCheckRegisterCache();
        clearAllCheckValuesCache();

        LoggerUtil.info(this.getClass(), "All caches cleared successfully");
    }

    /**
     * Perform midnight reset across all caches
     */
    public void performMidnightReset() {
        LoggerUtil.info(this.getClass(), "Performing midnight reset across all caches");

        // User context midnight reset
        performUserContextMidnightReset();

        // Clean up expired sessions
        cleanupExpiredTimeOffSessions();
        cleanupExpiredWorktimeSessions();

        // Refresh user data
        refreshAllUsersFromDataService();
        syncUserStatusesFromNetwork();
        writeUserStatusesToFile();

        LoggerUtil.info(this.getClass(), "Midnight reset completed successfully");
    }

    /**
     * Get comprehensive cache statistics for monitoring
     * @return Detailed cache statistics across all services
     */
    public String getAllCacheStatistics() {
        StringBuilder stats = new StringBuilder();

        stats.append("========================================\n");
        stats.append("     COMPREHENSIVE CACHE STATISTICS     \n");
        stats.append("========================================\n\n");

        // User Context Cache
        stats.append("USER CONTEXT CACHE:\n");
        stats.append("- Healthy: ").append(isUserContextHealthy()).append("\n");
        stats.append("- Current User: ").append(getCurrentUsername()).append("\n");
        stats.append("- Is Elevated: ").append(isElevated()).append("\n");
        stats.append("- Holiday Balance: ").append(getCurrentPaidHolidayDays()).append("\n\n");

        // All Users Cache
        stats.append("ALL USERS CACHE:\n");
        stats.append("- Total Users: ").append(getCachedUserCount()).append("\n");
        stats.append("- Has Data: ").append(hasUserData()).append("\n\n");

        // Individual cache service statistics
        stats.append("TIME OFF CACHE:\n");
        stats.append(timeOffCache.getCacheStatistics()).append("\n\n");

        stats.append("WORKTIME CACHE:\n");
        stats.append(worktimeCache.getCacheStatistics()).append("\n\n");

        stats.append("SESSION CACHE:\n");
        stats.append(sessionCache.getCacheStatus()).append("\n\n");

        stats.append("========================================\n");

        return stats.toString();
    }

    /**
     * Health check across all cache services
     * @return true if all critical caches are healthy
     */
    public boolean isAllCachesHealthy() {
        return isUserContextHealthy() && hasUserData();
    }

    /**
     * Emergency cache recovery - attempt to restore all caches
     * @return true if recovery was successful
     */
    public boolean performEmergencyRecovery() {
        LoggerUtil.warn(this.getClass(), "Performing emergency cache recovery");

        boolean recovery = false;

        try {
            // Force refresh user context
            if (forceRefreshUserContext()) {
                recovery = true;
                LoggerUtil.info(this.getClass(), "User context cache recovered");
            }

            // Refresh user data
            refreshAllUsersFromDataService();
            recovery = true;
            LoggerUtil.info(this.getClass(), "All users cache recovered");

            // Clean up all expired sessions
            cleanupExpiredTimeOffSessions();
            cleanupExpiredWorktimeSessions();
            LoggerUtil.info(this.getClass(), "Expired sessions cleaned up");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Emergency cache recovery failed: " + e.getMessage(), e);
            return false;
        }

        LoggerUtil.info(this.getClass(), "Emergency cache recovery completed: " + (recovery ? "SUCCESS" : "FAILED"));
        return recovery;
    }
}