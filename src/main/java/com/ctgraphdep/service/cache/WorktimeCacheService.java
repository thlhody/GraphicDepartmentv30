package com.ctgraphdep.service.cache;

import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * INDEPENDENT WorktimeCacheService - Buffer Layer for Worktime Operations.
 * Architecture:
 * 1. Page Load → Cache loads user's monthly worktime data from files
 * 2. Month Switch → Cache updates to new month data
 * 3. All Operations → Go THROUGH cache (write-through buffer)
 * 4. Cache → Immediately persists to files (write-through)
 * 5. Admin Operations → Bypass this cache entirely (direct file ops)
 * Features:
 * - Per-user, per-month session cache
 * - Month switching updates cache to new month
 * - 1h timeout + manual refresh
 * - Write-through persistence
 * - Thread-safe operations
 * - Uses WorktimeDataService for file operations
 * - Uses MainDefaultUserContextCache for current user context
 * - No external service dependencies (independent)
 */
@Service
public class WorktimeCacheService {

    private final WorktimeDataService worktimeDataService;
    private final MainDefaultUserContextCache mainDefaultUserContextCache;

    // Thread-safe cache - monthKey as key (format: "username-year-month")
    private final ConcurrentHashMap<String, WorktimeCacheEntry> userMonthSessions = new ConcurrentHashMap<>();

    // Global cache lock for cleanup operations
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    @Autowired
    public WorktimeCacheService(WorktimeDataService worktimeDataService, MainDefaultUserContextCache mainDefaultUserContextCache) {
        this.worktimeDataService = worktimeDataService;
        this.mainDefaultUserContextCache = mainDefaultUserContextCache;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // SESSION MANAGEMENT - Page Load/Month Switch
    // ========================================================================

    /**
     * Load user's worktime session for specific month
     * Called when user accesses time management page or switches months
     */
    public boolean loadUserMonthSession(String username, Integer userId, int year, int month) {
        try {
            String monthKey = createMonthKey(username, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "Loading worktime session for %s - %d/%d", username, year, month));

            // Validate this is for current user's own data
            if (!isCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService should only be used for current user's own data. Requested: %s, Current: %s",
                        username, mainDefaultUserContextCache.getCurrentUsername()));
                return false;
            }

            // Check if already loaded and valid
            WorktimeCacheEntry existingEntry = userMonthSessions.get(monthKey);
            if (existingEntry != null && existingEntry.isValid() && !existingEntry.isExpired()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Session already loaded and valid for %s - %d/%d", username, year, month));
                return true;
            }

            // Load from file using WorktimeDataService
            String currentUsername = mainDefaultUserContextCache.getCurrentUsername();
            List<WorkTimeTable> entries = worktimeDataService.readUserLocalReadOnly(username, year, month, currentUsername);

            if (entries == null) {
                entries = new ArrayList<>();
                LoggerUtil.debug(this.getClass(), String.format(
                        "No worktime entries found, created empty list for %s - %d/%d", username, year, month));
            }

            // Create and store cache entry
            WorktimeCacheEntry cacheEntry = new WorktimeCacheEntry();
            cacheEntry.initializeFromService(username, userId, year, month, entries);

            userMonthSessions.put(monthKey, cacheEntry);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded worktime session for %s - %d/%d with %d entries",
                    username, year, month, entries.size()));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading worktime session for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Switch user to different month (common operation in time management)
     */
    public boolean switchUserToMonth(String username, Integer userId, int newYear, int newMonth) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Switching user %s to month %d/%d", username, newYear, newMonth));

            // Invalidate all other months for this user to save memory
            invalidateUserOtherMonths(username, newYear, newMonth);

            // Load new month
            return loadUserMonthSession(username, userId, newYear, newMonth);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error switching user %s to month %d/%d: %s", username, newYear, newMonth, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Invalidate specific user month session
     */
    public void invalidateUserMonthSession(String username, int year, int month) {
        globalLock.writeLock().lock();
        try {
            String monthKey = createMonthKey(username, year, month);
            WorktimeCacheEntry removed = userMonthSessions.remove(monthKey);

            if (removed != null) {
                removed.clear();
                LoggerUtil.info(this.getClass(), String.format(
                        "Invalidated worktime session for %s - %d/%d", username, year, month));
            }
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Invalidate all sessions for user (logout/refresh)
     */
    public void invalidateAllUserSessions(String username) {
        globalLock.writeLock().lock();
        try {
            List<String> userKeys = userMonthSessions.keySet().stream()
                    .filter(key -> key.startsWith(username + "-"))
                    .toList();

            for (String key : userKeys) {
                WorktimeCacheEntry removed = userMonthSessions.remove(key);
                if (removed != null) {
                    removed.clear();
                }
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Invalidated all worktime sessions for user %s (%d sessions)", username, userKeys.size()));

        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Clean up expired sessions (called periodically)
     */
    public void cleanupExpiredSessions() {
        globalLock.writeLock().lock();
        try {
            List<String> expiredKeys = userMonthSessions.entrySet().stream()
                    .filter(entry -> entry.getValue().isExpired())
                    .map(Map.Entry::getKey)
                    .toList();

            for (String key : expiredKeys) {
                WorktimeCacheEntry removed = userMonthSessions.remove(key);
                if (removed != null) {
                    removed.clear();
                }
            }

            if (!expiredKeys.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Cleaned up %d expired worktime sessions", expiredKeys.size()));
            }

        } finally {
            globalLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // WRITE-THROUGH BUFFER OPERATIONS
    // ========================================================================

    /**
     * Update start time through cache with write-through persistence
     */
    public boolean updateStartTime(String username, Integer userId, int year, int month, LocalDate date, LocalDateTime startTime) {
        try {
            // Validate this is for current user's own data
            if (!isCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            String monthKey = createMonthKey(username, year, month);
            WorktimeCacheEntry cacheEntry = userMonthSessions.get(monthKey);

            if (!validateCacheEntry(cacheEntry, username, userId, year, month)) {
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Updating start time in cache for %s on %s to %s",
                    username, date, startTime != null ? startTime.toLocalTime() : "null"));

            // Get entries from cache
            List<WorkTimeTable> entries = new ArrayList<>(cacheEntry.getEntries());

            // Find or create entry for date
            WorkTimeTable entry = findOrCreateEntryForDate(entries, userId, date);
            entry.setDayStartTime(startTime);
            entry.setAdminSync(SyncStatusMerge.USER_INPUT);

            // Recalculate work time if both start and end exist
            recalculateWorkTime(entry);

            // Update cache
            cacheEntry.updateEntries(entries);

            // Write-through: Save to file immediately
            return persistToFile(username, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating start time in cache for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Update end time through cache with write-through persistence
     */
    public boolean updateEndTime(String username, Integer userId, int year, int month, LocalDate date, LocalDateTime endTime) {
        try {
            // Validate this is for current user's own data
            if (!isCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            String monthKey = createMonthKey(username, year, month);
            WorktimeCacheEntry cacheEntry = userMonthSessions.get(monthKey);

            if (!validateCacheEntry(cacheEntry, username, userId, year, month)) {
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Updating end time in cache for %s on %s to %s",
                    username, date, endTime != null ? endTime.toLocalTime() : "null"));

            // Get entries from cache
            List<WorkTimeTable> entries = new ArrayList<>(cacheEntry.getEntries());

            // Find or create entry for date
            WorkTimeTable entry = findOrCreateEntryForDate(entries, userId, date);
            entry.setDayEndTime(endTime);
            entry.setAdminSync(SyncStatusMerge.USER_INPUT);

            // Recalculate work time if both start and end exist
            recalculateWorkTime(entry);

            // Update cache
            cacheEntry.updateEntries(entries);

            // Write-through: Save to file immediately
            return persistToFile(username, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating end time in cache for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Add time off entry through cache with write-through persistence
     */
    public boolean addTimeOffEntry(String username, Integer userId, int year, int month, LocalDate date, String timeOffType) {
        try {
            // Validate this is for current user's own data
            if (!isCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            String monthKey = createMonthKey(username, year, month);
            WorktimeCacheEntry cacheEntry = userMonthSessions.get(monthKey);

            if (!validateCacheEntry(cacheEntry, username, userId, year, month)) {
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Adding time off entry in cache for %s on %s (%s)", username, date, timeOffType));

            // Get entries from cache
            List<WorkTimeTable> entries = new ArrayList<>(cacheEntry.getEntries());

            // Find or create entry for date
            WorkTimeTable entry = findOrCreateEntryForDate(entries, userId, date);
            entry.setTimeOffType(timeOffType.toUpperCase());
            entry.setAdminSync(SyncStatusMerge.USER_INPUT);

            // Clear work time when setting time off
            clearWorkFields(entry);

            // Update cache
            cacheEntry.updateEntries(entries);

            // Write-through: Save to file immediately
            return persistToFile(username, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error adding time off entry in cache for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Remove time off entry through cache with write-through persistence
     */
    public boolean removeTimeOffEntry(String username, Integer userId, int year, int month, LocalDate date) {
        try {
            // Validate this is for current user's own data
            if (!isCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            String monthKey = createMonthKey(username, year, month);
            WorktimeCacheEntry cacheEntry = userMonthSessions.get(monthKey);

            if (!validateCacheEntry(cacheEntry, username, userId, year, month)) {
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Removing time off entry in cache for %s on %s", username, date));

            // Get entries from cache
            List<WorkTimeTable> entries = new ArrayList<>(cacheEntry.getEntries());

            // Find entry for date
            Optional<WorkTimeTable> entryOpt = entries.stream()
                    .filter(e -> e.getUserId().equals(userId) && e.getWorkDate().equals(date))
                    .findFirst();

            if (entryOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No entry found to remove time off for %s on %s", username, date));
                return false;
            }

            WorkTimeTable entry = entryOpt.get();
            entry.setTimeOffType(null);
            entry.setAdminSync(SyncStatusMerge.USER_INPUT);

            // Update cache
            cacheEntry.updateEntries(entries);

            // Write-through: Save to file immediately
            return persistToFile(username, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error removing time off entry in cache for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        }
    }

    // ========================================================================
    // FAST READ OPERATIONS (From Cache)
    // ========================================================================

    /**
     * Get month entries from cache (fast read)
     */
    public List<WorkTimeTable> getMonthEntries(String username, int year, int month) {
        try {
            String monthKey = createMonthKey(username, year, month);
            WorktimeCacheEntry cacheEntry = userMonthSessions.get(monthKey);

            if (cacheEntry != null && cacheEntry.isValid() && !cacheEntry.isExpired()) {
                return new ArrayList<>(cacheEntry.getEntries()); // Return copy for safety
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "No valid cached entries for %s - %d/%d", username, year, month));
            return new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting entries from cache for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Get specific entry for date from cache
     */
    public Optional<WorkTimeTable> getEntryForDate(String username, Integer userId, int year, int month, LocalDate date) {
        try {
            List<WorkTimeTable> entries = getMonthEntries(username, year, month);
            return entries.stream()
                    .filter(e -> e.getUserId().equals(userId) && e.getWorkDate().equals(date))
                    .findFirst();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting entry for date from cache for %s on %s: %s", username, date, e.getMessage()));
            return Optional.empty();
        }
    }

    // ========================================================================
    // CACHE DIAGNOSTICS AND MANAGEMENT
    // ========================================================================

    /**
     * Check if user has active session for month
     */
    public boolean hasActiveMonthSession(String username, int year, int month) {
        String monthKey = createMonthKey(username, year, month);
        WorktimeCacheEntry entry = userMonthSessions.get(monthKey);
        return entry != null && entry.isValid() && !entry.isExpired();
    }

    /**
     * Get cache statistics for monitoring
     */
    public String getCacheStatistics() {
        globalLock.readLock().lock();
        try {
            int totalSessions = userMonthSessions.size();
            long validSessions = userMonthSessions.values().stream().filter(WorktimeCacheEntry::isValid).count();
            long expiredSessions = userMonthSessions.values().stream().filter(WorktimeCacheEntry::isExpired).count();

            return String.format("WorktimeCache: %d total sessions, %d valid, %d expired",
                    totalSessions, validSessions, expiredSessions);
        } finally {
            globalLock.readLock().unlock();
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Create month key for cache indexing
     */
    private String createMonthKey(String username, int year, int month) {
        return String.format("%s-%d-%d", username, year, month);
    }

    /**
     * Validate cache entry and autoload if needed
     */
    private boolean validateCacheEntry(WorktimeCacheEntry cacheEntry, String username, Integer userId, int year, int month) {
        if (cacheEntry == null || !cacheEntry.isValid()) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "No valid session found for %s - %d/%d, loading session first", username, year, month));

            return loadUserMonthSession(username, userId, year, month);
        }
        return true;
    }

    /**
     * Find existing entry or create new one for date
     */
    private WorkTimeTable findOrCreateEntryForDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        Optional<WorkTimeTable> existingEntry = entries.stream()
                .filter(e -> e.getUserId().equals(userId) && e.getWorkDate().equals(date))
                .findFirst();

        if (existingEntry.isPresent()) {
            return existingEntry.get();
        }

        // Create new entry
        WorkTimeTable newEntry = new WorkTimeTable();
        newEntry.setUserId(userId);
        newEntry.setWorkDate(date);
        newEntry.setAdminSync(SyncStatusMerge.USER_INPUT);
        clearWorkFields(newEntry);

        entries.add(newEntry);
        return newEntry;
    }

    /**
     * Clear work-related fields for time off entries
     */
    private void clearWorkFields(WorkTimeTable entry) {
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);
    }

    /**
     * Recalculate work time based on start and end times
     */
    private void recalculateWorkTime(WorkTimeTable entry) {
        if (entry.getDayStartTime() == null || entry.getDayEndTime() == null) {
            entry.setTotalWorkedMinutes(0);
            entry.setLunchBreakDeducted(false);
            return;
        }

        // Calculate total minutes
        long minutes = java.time.Duration.between(entry.getDayStartTime(), entry.getDayEndTime()).toMinutes();
        int totalMinutes = Math.max(0, (int) minutes);

        entry.setTotalWorkedMinutes(totalMinutes);

        // Determine lunch break (more than 6 hours)
        boolean lunchBreak = totalMinutes > (6 * 60);
        entry.setLunchBreakDeducted(lunchBreak);

        LoggerUtil.debug(this.getClass(), String.format(
                "Recalculated work time: %d minutes, lunch break: %s", totalMinutes, lunchBreak));
    }

    /**
     * Persist entries to file (write-through)
     */
    private boolean persistToFile(String username, int year, int month, List<WorkTimeTable> entries) {
        try {
            worktimeDataService.writeUserLocalWithSyncAndBackup(username, entries, year, month);
            LoggerUtil.debug(this.getClass(), String.format(
                    "Successfully persisted %d entries to file for %s - %d/%d", entries.size(), username, year, month));
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Failed to persist entries to file for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Invalidate other months for user to save memory
     */
    private void invalidateUserOtherMonths(String username, int keepYear, int keepMonth) {
        globalLock.writeLock().lock();
        try {
            String keepKey = createMonthKey(username, keepYear, keepMonth);

            List<String> userKeysToRemove = userMonthSessions.keySet().stream()
                    .filter(key -> key.startsWith(username + "-"))
                    .filter(key -> !key.equals(keepKey))
                    .toList();

            for (String key : userKeysToRemove) {
                WorktimeCacheEntry removed = userMonthSessions.remove(key);
                if (removed != null) {
                    removed.clear();
                }
            }

            if (!userKeysToRemove.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Invalidated %d other month sessions for user %s to save memory",
                        userKeysToRemove.size(), username));
            }

        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Validate that the requested operation is for current user's own data
     * WorktimeCacheService should only be used for current user's session data
     */
    private boolean isCurrentUserData(String username) {
        try {
            String currentUsername = mainDefaultUserContextCache.getCurrentUsername();
            return username.equals(currentUsername);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error checking current user context: %s", e.getMessage()));
            return false;
        }
    }


}