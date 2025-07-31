package com.ctgraphdep.service.cache;

import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ENHANCED WorktimeCacheService - Complete Buffer Layer with Comprehensive Fallback Support.
 * Key Enhancements:
 * - Complete fallback strategy for all operations (cache → file → emergency)
 * - New methods for all worktime operations (temporary stops, transforms, bulk operations)
 * - Write-through pattern with file-first priority
 * - Automatic cache invalidation on write failures
 * - Month switching with memory optimization
 * - Comprehensive error handling and recovery
 * Architecture:
 * 1. All reads: Cache → File fallback → Emergency direct read
 * 2. All writes: File first → Cache second → Cache invalidation on failure
 * 3. Cache miss: Auto-repopulate from file
 * 4. Write failure: Graceful degradation with cache invalidation
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
    // ENHANCED SESSION MANAGEMENT WITH FALLBACK
    // ========================================================================

    /**
     * Load user's worktime session for specific month with comprehensive fallback
     */
    public boolean loadUserMonthSession(String username, Integer userId, int year, int month) {
        try {
            String monthKey = createMonthKey(username, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "Loading worktime session for %s - %d/%d", username, year, month));

            // Validate this is for current user's own data
            if (isNonCurrentUserData(username)) {
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

            // Load from file with fallback strategy
            List<WorkTimeTable> entries = loadEntriesWithFallback(username, year, month);

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
     * Switch user to different month with memory optimization and fallback
     */
    public boolean switchUserToMonth(String username, Integer userId, int newYear, int newMonth) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Switching user %s to month %d/%d", username, newYear, newMonth));

            // Invalidate all other months for this user to save memory
            invalidateUserOtherMonths(username, newYear, newMonth);

            // Load new month with fallback
            return loadUserMonthSession(username, userId, newYear, newMonth);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error switching user %s to month %d/%d: %s", username, newYear, newMonth, e.getMessage()), e);
            return false;
        }
    }

    // ========================================================================
    // ENHANCED READ OPERATIONS WITH COMPREHENSIVE FALLBACK
    // ========================================================================

    /**
     * Get month entries with smart fallback - cache first, file if cache miss, emergency fallback
     * This is the main method for all worktime data retrieval
     */
    public List<WorkTimeTable> getMonthEntriesWithFallback(String username, Integer userId, int year, int month) {
        try {
            // Step 1: Try cache first (fastest)
            List<WorkTimeTable> cachedData = getMonthEntriesFromCache(username, year, month);
            if (!cachedData.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format("Cache hit for %s - %d/%d (%d entries)",
                        username, year, month, cachedData.size()));
                return cachedData;
            }

            // Step 2: Cache miss - load session and try again
            LoggerUtil.info(this.getClass(), String.format("Cache miss for %s - %d/%d, loading session",
                    username, year, month));

            boolean sessionLoaded = loadUserMonthSession(username, userId, year, month);
            if (sessionLoaded) {
                cachedData = getMonthEntriesFromCache(username, year, month);
                // ✅ FIXED: If session loaded successfully, trust the cache data (even if empty)
                LoggerUtil.info(this.getClass(), String.format("Cache populated successfully for %s - %d/%d (%d entries)",
                        username, year, month, cachedData.size()));
                return cachedData;
            }

            // Step 3: Emergency fallback - direct file read
            LoggerUtil.warn(this.getClass(), String.format("Cache loading failed for %s - %d/%d, using emergency fallback",
                    username, year, month));
            return loadEntriesWithFallback(username, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting worktime data for %s - %d/%d: %s",
                    username, year, month, e.getMessage()), e);

            // Ultimate fallback - try direct file read one more time
            try {
                String currentUsername = mainDefaultUserContextCache.getCurrentUsername();
                return worktimeDataService.readUserLocalReadOnly(username, year, month, currentUsername);
            } catch (Exception fallbackError) {
                LoggerUtil.error(this.getClass(), String.format("Emergency fallback also failed for %s - %d/%d: %s",
                        username, year, month, fallbackError.getMessage()));
                return new ArrayList<>();
            }
        }
    }

    /**
     * Get specific entry for date with fallback
     */
    public Optional<WorkTimeTable> getEntryForDateWithFallback(String username, Integer userId, int year, int month, LocalDate date) {
        try {
            List<WorkTimeTable> entries = getMonthEntriesWithFallback(username, userId, year, month);
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
    // ENHANCED WRITE OPERATIONS WITH WRITE-THROUGH AND FALLBACK
    // ========================================================================

    /**
     * NEW: Save complete month entries with write-through and fallback
     * This is the primary method for bulk operations and command system integration
     */
    public boolean saveMonthEntriesWithWriteThrough(String username, Integer userId, int year, int month, List<WorkTimeTable> entries) {
        try {
            // Validate this is for current user's own data
            if (isNonCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Saving month entries with write-through for %s - %d/%d (%d entries)",
                    username, year, month, entries.size()));

            boolean fileSuccess = false;
            boolean cacheSuccess = false;

            try {
                // Step 1: Write to file first (most critical)
                worktimeDataService.writeUserLocalWithSyncAndBackup(username, entries, year, month);
                fileSuccess = true;
                LoggerUtil.debug(this.getClass(), String.format("File write successful for %s - %d/%d", username, year, month));

                // Step 2: Update cache (secondary priority)
                String monthKey = createMonthKey(username, year, month);
                WorktimeCacheEntry cacheEntry = userMonthSessions.get(monthKey);

                if (cacheEntry != null && cacheEntry.isValid()) {
                    cacheEntry.updateEntries(entries);
                    cacheSuccess = true;
                    LoggerUtil.debug(this.getClass(), String.format("Cache update successful for %s - %d/%d", username, year, month));
                } else {
                    // Cache not loaded - create new session
                    boolean sessionCreated = loadUserMonthSession(username, userId, year, month);
                    cacheSuccess = sessionCreated;
                    LoggerUtil.debug(this.getClass(), String.format("Cache session created for %s - %d/%d: %s",
                            username, year, month, sessionCreated));
                }

                return true;

            } catch (Exception writeError) {
                LoggerUtil.error(this.getClass(), String.format("Write operation failed for %s - %d/%d: %s",
                        username, year, month, writeError.getMessage()), writeError);

                if (!fileSuccess) {
                    // CRITICAL: File write failed - operation failed completely
                    LoggerUtil.error(this.getClass(), String.format("CRITICAL: File write failed for %s - %d/%d", username, year, month));
                    return false;
                } else {
                    // File succeeded, cache failed - invalidate cache for safety
                    invalidateUserMonthSession(username, year, month);
                    LoggerUtil.warn(this.getClass(), String.format(
                            "File saved but cache update failed for %s - %d/%d - cache invalidated for safety", username, year, month));
                    return true; // File write succeeded, which is what matters most
                }
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in saveMonthEntriesWithWriteThrough for %s - %d/%d: %s",
                    username, year, month, e.getMessage()), e);
            return false;
        }
    }

    /**
     * NEW: Update single entry with write-through and fallback
     * Used by individual field update commands
     */
    public boolean updateEntryWithWriteThrough(String username, Integer userId, int year, int month, LocalDate date, WorkTimeTable updatedEntry) {
        try {
            // Validate this is for current user's own data
            if (isNonCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Updating entry with write-through for %s on %s", username, date));

            // Load current entries
            List<WorkTimeTable> entries = getMonthEntriesWithFallback(username, userId, year, month);

            // Remove existing entry for this date if any
            entries.removeIf(existing -> existing.getWorkDate().equals(date) && userId.equals(existing.getUserId()));

            // Add updated entry
            entries.add(updatedEntry);

            // Save with write-through
            return saveMonthEntriesWithWriteThrough(username, userId, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating entry for %s on %s: %s",
                    username, date, e.getMessage()), e);
            return false;
        }
    }

    /**
     * ENHANCED: Update start time with write-through and fallback
     */
    public boolean updateStartTime(String username, Integer userId, int year, int month, LocalDate date, LocalDateTime startTime) {
        try {
            // Validate this is for current user's own data
            if (isNonCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Updating start time for %s on %s to %s",
                    username, date, startTime != null ? startTime.toLocalTime() : "null"));

            // Get current entries with fallback
            List<WorkTimeTable> entries = getMonthEntriesWithFallback(username, userId, year, month);

            // Find or create entry for date
            WorkTimeTable entry = findOrCreateEntryForDate(entries, userId, date);
            entry.setDayStartTime(startTime);
            entry.setAdminSync(MergingStatusConstants.USER_INPUT);

            // Recalculate work time if both start and end exist
            recalculateWorkTime(entry);

            // Save with write-through
            return saveMonthEntriesWithWriteThrough(username, userId, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating start time for %s on %s: %s",
                    username, date, e.getMessage()), e);
            return false;
        }
    }

    /**
     * ENHANCED: Update end time with write-through and fallback
     */
    public boolean updateEndTime(String username, Integer userId, int year, int month, LocalDate date, LocalDateTime endTime) {
        try {
            // Validate this is for current user's own data
            if (isNonCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Updating end time for %s on %s to %s",
                    username, date, endTime != null ? endTime.toLocalTime() : "null"));

            // Get current entries with fallback
            List<WorkTimeTable> entries = getMonthEntriesWithFallback(username, userId, year, month);

            // Find or create entry for date
            WorkTimeTable entry = findOrCreateEntryForDate(entries, userId, date);
            entry.setDayEndTime(endTime);
            entry.setAdminSync(MergingStatusConstants.USER_INPUT);

            // Recalculate work time if both start and end exist
            recalculateWorkTime(entry);

            // Save with write-through
            return saveMonthEntriesWithWriteThrough(username, userId, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating end time for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        }
    }

    /**
     * NEW: Update temporary stop with write-through and fallback
     */
    public boolean updateTemporaryStop(String username, Integer userId, int year, int month, LocalDate date, Integer tempStopMinutes) {
        try {
            // Validate this is for current user's own data
            if (isNonCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Updating temporary stop for %s on %s to %d minutes", username, date, tempStopMinutes));

            // Get current entries with fallback
            List<WorkTimeTable> entries = getMonthEntriesWithFallback(username, userId, year, month);

            // Find or create entry for date
            WorkTimeTable entry = findOrCreateEntryForDate(entries, userId, date);
            entry.setTotalTemporaryStopMinutes(tempStopMinutes);
            entry.setTemporaryStopCount(tempStopMinutes > 0 ? 1 : 0);
            entry.setAdminSync(MergingStatusConstants.USER_INPUT);

            // Recalculate work time with temporary stops
            recalculateWorkTimeWithTempStops(entry);

            // Save with write-through
            return saveMonthEntriesWithWriteThrough(username, userId, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating temporary stop for %s on %s: %s",
                    username, date, e.getMessage()), e);
            return false;
        }
    }

    /**
     * NEW: Remove temporary stop with write-through and fallback
     */
    public boolean removeTemporaryStop(String username, Integer userId, int year, int month, LocalDate date) {
        try {
            // Validate this is for current user's own data
            if (isNonCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format("Removing temporary stop for %s on %s", username, date));

            // Get current entries with fallback
            List<WorkTimeTable> entries = getMonthEntriesWithFallback(username, userId, year, month);

            // Find entry for date
            Optional<WorkTimeTable> entryOpt = entries.stream()
                    .filter(e -> e.getUserId().equals(userId) && e.getWorkDate().equals(date))
                    .findFirst();

            if (entryOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No entry found to remove temporary stop for %s on %s", username, date));
                return false;
            }

            WorkTimeTable entry = entryOpt.get();
            entry.setTotalTemporaryStopMinutes(0);
            entry.setTemporaryStopCount(0);
            entry.setAdminSync(MergingStatusConstants.USER_INPUT);

            // Recalculate work time without temporary stops
            recalculateWorkTime(entry);

            // Save with write-through
            return saveMonthEntriesWithWriteThrough(username, userId, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error removing temporary stop for %s on %s: %s",
                    username, date, e.getMessage()), e);
            return false;
        }
    }

    /**
     * ENHANCED: Add time off entry with write-through and fallback
     */
    public boolean addTimeOffEntry(String username, Integer userId, int year, int month, LocalDate date, String timeOffType) {
        try {
            // Validate this is for current user's own data
            if (isNonCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Adding time off entry for %s on %s (%s)", username, date, timeOffType));

            // Get current entries with fallback
            List<WorkTimeTable> entries = getMonthEntriesWithFallback(username, userId, year, month);

            // Find or create entry for date
            WorkTimeTable entry = findOrCreateEntryForDate(entries, userId, date);
            entry.setTimeOffType(timeOffType.toUpperCase());
            entry.setAdminSync(MergingStatusConstants.USER_INPUT);

            // Clear work time when setting time off
            clearWorkFields(entry);

            // Save with write-through
            return saveMonthEntriesWithWriteThrough(username, userId, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error adding time off entry for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        }
    }

    /**
     * ENHANCED: Remove time off entry with write-through and fallback
     */
    public boolean removeTimeOffEntry(String username, Integer userId, int year, int month, LocalDate date) {
        try {
            // Validate this is for current user's own data
            if (isNonCurrentUserData(username)) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "WorktimeCacheService write operation rejected for non-current user: %s", username));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format("Removing time off entry for %s on %s", username, date));

            // Get current entries with fallback
            List<WorkTimeTable> entries = getMonthEntriesWithFallback(username, userId, year, month);

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
            entry.setAdminSync(MergingStatusConstants.USER_INPUT);

            // Save with write-through
            return saveMonthEntriesWithWriteThrough(username, userId, year, month, entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error removing time off entry for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        }
    }

    // ========================================================================
    // CACHE MANAGEMENT WITH ENHANCED ERROR HANDLING
    // ========================================================================

    /**
     * Invalidate specific user month session with safety checks
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
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error invalidating session for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Invalidate all sessions for user with safety checks
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

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error invalidating all sessions for %s: %s",
                    username, e.getMessage()));
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    /**
     * Clean up expired sessions with enhanced error handling
     */
    public void cleanupExpiredSessions() {
        globalLock.writeLock().lock();
        try {
            List<String> expiredKeys = userMonthSessions.entrySet().stream()
                    .filter(entry -> entry.getValue().isExpired())
                    .map(Map.Entry::getKey)
                    .toList();

            for (String key : expiredKeys) {
                try {
                    WorktimeCacheEntry removed = userMonthSessions.remove(key);
                    if (removed != null) {
                        removed.clear();
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Error cleaning up session %s: %s", key, e.getMessage()));
                }
            }

            if (!expiredKeys.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Cleaned up %d expired worktime sessions", expiredKeys.size()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during session cleanup: %s", e.getMessage()));
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS WITH ENHANCED FALLBACK
    // ========================================================================

    /**
     * Load entries with comprehensive fallback strategy
     */
    private List<WorkTimeTable> loadEntriesWithFallback(String username, int year, int month) {
        String currentUsername = mainDefaultUserContextCache.getCurrentUsername();

        try {
            // Primary: Read user local file
            List<WorkTimeTable> entries = worktimeDataService.readUserLocalReadOnly(username, year, month, currentUsername);
            if (entries != null) {
                LoggerUtil.debug(this.getClass(), String.format("Loaded %d entries from file for %s - %d/%d",
                        entries.size(), username, year, month));
                return entries;
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Primary file read failed for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
        }

        try {
            // Fallback: Try alternative read method
            List<WorkTimeTable> entries = worktimeDataService.readUserLocalReadOnly(username, year, month, username);
            if (entries != null) {
                LoggerUtil.info(this.getClass(), String.format("Fallback read succeeded for %s - %d/%d", username, year, month));
                return entries;
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Fallback file read also failed for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
        }

        // Ultimate fallback: Return empty list
        LoggerUtil.warn(this.getClass(), String.format("All read attempts failed for %s - %d/%d, returning empty list",
                username, year, month));
        return new ArrayList<>();
    }

    /**
     * Get month entries from cache only (no fallback)
     */
    private List<WorkTimeTable> getMonthEntriesFromCache(String username, int year, int month) {
        try {
            String monthKey = createMonthKey(username, year, month);
            WorktimeCacheEntry cacheEntry = userMonthSessions.get(monthKey);

            if (cacheEntry != null && cacheEntry.isValid() && !cacheEntry.isExpired()) {
                return new ArrayList<>(cacheEntry.getEntries()); // Return copy for safety
            }

            return new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting entries from cache for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Create month key for cache indexing
     */
    private String createMonthKey(String username, int year, int month) {
        return String.format("%s-%d-%d", username, year, month);
    }

    /**
     * Validate that the requested operation is for current user's own data
     * WorktimeCacheService should only be used for current user's session data
     */
    private boolean isNonCurrentUserData(String username) {
        try {
            String currentUsername = mainDefaultUserContextCache.getCurrentUsername();
            return !username.equals(currentUsername);  // Returns true when it's NOT current user
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error checking current user context: %s", e.getMessage()));
            return true;  // Treat as non-current user on error (deny access)
        }
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
        newEntry.setAdminSync(MergingStatusConstants.USER_INPUT);
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
     * Recalculate work time including temporary stops
     */
    private void recalculateWorkTimeWithTempStops(WorkTimeTable entry) {
        // First calculate base work time
        recalculateWorkTime(entry);

        // Subtract temporary stops from total worked time
        if (entry.getTotalTemporaryStopMinutes() != null && entry.getTotalTemporaryStopMinutes() > 0) {
            int workedMinutes = entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0;
            int adjustedMinutes = Math.max(0, workedMinutes - entry.getTotalTemporaryStopMinutes());
            entry.setTotalWorkedMinutes(adjustedMinutes);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Adjusted work time with temp stops: %d - %d = %d minutes",
                    workedMinutes, entry.getTotalTemporaryStopMinutes(), adjustedMinutes));
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
                try {
                    WorktimeCacheEntry removed = userMonthSessions.remove(key);
                    if (removed != null) {
                        removed.clear();
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Error removing session %s: %s", key, e.getMessage()));
                }
            }

            if (!userKeysToRemove.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Invalidated %d other month sessions for user %s to save memory",
                        userKeysToRemove.size(), username));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error invalidating other months for %s: %s",
                    username, e.getMessage()));
        } finally {
            globalLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // DIAGNOSTIC AND MONITORING METHODS
    // ========================================================================

    /**
     * Check if user has active session for month
     */
    public boolean hasActiveMonthSession(String username, int year, int month) {
        try {
            String monthKey = createMonthKey(username, year, month);
            WorktimeCacheEntry entry = userMonthSessions.get(monthKey);
            return entry != null && entry.isValid() && !entry.isExpired();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking active session for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
            return false;
        }
    }

    /**
     * Get cache statistics for monitoring and diagnostics
     */
    public String getCacheStatistics() {
        globalLock.readLock().lock();
        try {
            int totalSessions = userMonthSessions.size();
            long validSessions = userMonthSessions.values().stream()
                    .filter(WorktimeCacheEntry::isValid)
                    .count();
            long expiredSessions = userMonthSessions.values().stream()
                    .filter(WorktimeCacheEntry::isExpired)
                    .count();

            return String.format("WorktimeCache: %d total sessions, %d valid, %d expired",
                    totalSessions, validSessions, expiredSessions);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting cache statistics: %s", e.getMessage()));
            return "WorktimeCache: Statistics unavailable due to error";
        } finally {
            globalLock.readLock().unlock();
        }
    }

    /**
     * Get detailed cache status for specific user
     */
    public String getUserCacheStatus(String username) {
        try {
            List<String> userSessions = userMonthSessions.keySet().stream()
                    .filter(key -> key.startsWith(username + "-"))
                    .sorted()
                    .toList();

            if (userSessions.isEmpty()) {
                return String.format("User %s: No active cache sessions", username);
            }

            StringBuilder status = new StringBuilder();
            status.append(String.format("User %s cache sessions (%d):\n", username, userSessions.size()));

            for (String sessionKey : userSessions) {
                WorktimeCacheEntry entry = userMonthSessions.get(sessionKey);
                if (entry != null) {
                    status.append(String.format("  - %s: %s\n", sessionKey, entry.toString()));
                }
            }

            return status.toString();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting user cache status for %s: %s",
                    username, e.getMessage()));
            return String.format("User %s: Cache status unavailable due to error", username);
        }
    }

    /**
     * Force refresh cache from file for specific month
     * Used for manual cache refresh or recovery scenarios
     */
    public boolean forceRefreshMonthFromFile(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Force refreshing cache from file for %s - %d/%d",
                    username, year, month));

            // Invalidate current session
            invalidateUserMonthSession(username, year, month);

            // Reload from file
            boolean reloaded = loadUserMonthSession(username, userId, year, month);

            if (reloaded) {
                LoggerUtil.info(this.getClass(), String.format("Successfully force refreshed cache for %s - %d/%d",
                        username, year, month));
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Force refresh failed for %s - %d/%d",
                        username, year, month));
            }

            return reloaded;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error force refreshing cache for %s - %d/%d: %s",
                    username, year, month, e.getMessage()), e);
            return false;
        }
    }

    // ========================================================================
    // LEGACY COMPATIBILITY METHODS (For backward compatibility)
    // ========================================================================

    /**
     * Get month entries (legacy method - now uses fallback)
     * @deprecated Use getMonthEntriesWithFallback() instead
     */
    @Deprecated
    public List<WorkTimeTable> getMonthEntries(String username, int year, int month) {
        // Redirect to fallback method for safety
        Integer userId = mainDefaultUserContextCache.getCurrentUser().getUserId();
        return getMonthEntriesWithFallback(username, userId, year, month);
    }

    /**
     * Get specific entry for date (legacy method - now uses fallback)
     * @deprecated Use getEntryForDateWithFallback() instead
     */
    @Deprecated
    public Optional<WorkTimeTable> getEntryForDate(String username, Integer userId, int year, int month, LocalDate date) {
        return getEntryForDateWithFallback(username, userId, year, month, date);
    }
}