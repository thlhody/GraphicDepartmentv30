package com.ctgraphdep.service.cache;

import com.ctgraphdep.fileOperations.data.TimeOffDataService;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.TimeOffRequest;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * INDEPENDENT TimeOffCacheService - Buffer Layer for Time Off Operations.
 * Architecture:
 * 1. Page Load → Cache loads user's yearly timeoff data from files
 * 2. All Operations → Go THROUGH cache (write-through buffer)
 * 3. Cache → Immediately persists to files (write-through)
 * 4. Admin Operations → Bypass this cache entirely (direct file ops)
 * Features:
 * - Per-user session cache (yearly timeoff data)
 * - 1h timeout + manual refresh
 * - Write-through persistence
 * - Thread-safe operations
 * - No external service dependencies (independent)
 */
@Service
public class TimeOffCacheService {

    private final TimeOffDataService timeOffDataService;
    private final MainDefaultUserContextCache mainDefaultUserContextCache;
    private final AllUsersCacheService allUsersCacheService;

    // Thread-safe cache - userKey as key (format: "username-year")
    private final ConcurrentHashMap<String, TimeOffCacheEntry> userSessions = new ConcurrentHashMap<>();

    // Global cache lock for cleanup operations
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();

    @Autowired
    public TimeOffCacheService(TimeOffDataService timeOffDataService, MainDefaultUserContextCache mainDefaultUserContextCache, AllUsersCacheService allUsersCacheService) {
        this.timeOffDataService = timeOffDataService;
        this.mainDefaultUserContextCache = mainDefaultUserContextCache;
        this.allUsersCacheService = allUsersCacheService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // SESSION MANAGEMENT - Page Load/Invalidation
    // ========================================================================

    /**
     * Load user's timeoff session on page access
     * Called when user accesses time management page
     */
    public boolean loadUserSession(String username, Integer userId, int year) {
        try {
            String userKey = createUserKey(username, year);

            LoggerUtil.info(this.getClass(), String.format("Loading timeoff session for %s - %d", username, year));

            // Check if already loaded and valid
            TimeOffCacheEntry existingEntry = userSessions.get(userKey);
            if (existingEntry != null && existingEntry.isValid() && !existingEntry.isExpired()) {
                LoggerUtil.debug(this.getClass(), String.format("Session already loaded and valid for %s - %d", username, year));
                return true;
            }

            // Load from file
            TimeOffTracker tracker = timeOffDataService.readUserLocalTrackerReadOnly(username, userId, username, year);

            if (tracker == null) {
                // Create new empty tracker if none exists
                tracker = createEmptyTracker(username, userId, year);
                LoggerUtil.debug(this.getClass(), String.format(
                        "Created new empty tracker for %s - %d", username, year));
            }

            // Create and store cache entry
            TimeOffCacheEntry cacheEntry = new TimeOffCacheEntry();
            cacheEntry.initializeFromService(username, userId, year, tracker);

            userSessions.put(userKey, cacheEntry);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded timeoff session for %s - %d with %d requests",
                    username, year, tracker.getRequests() != null ? tracker.getRequests().size() : 0));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading timeoff session for %s - %d: %s", username, year, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Invalidate user session (manual refresh or timeout)
     */
    public void invalidateUserSession(String username, int year) {
        globalLock.writeLock().lock();
        try {
            String userKey = createUserKey(username, year);
            TimeOffCacheEntry removed = userSessions.remove(userKey);

            if (removed != null) {
                removed.clear();
                LoggerUtil.info(this.getClass(), String.format("Invalidated timeoff session for %s - %d", username, year));
            }
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
            List<String> expiredKeys = userSessions.entrySet().stream().filter(entry -> entry.getValue().
                    isExpired()).map(Map.Entry::getKey).toList();

            for (String key : expiredKeys) {
                TimeOffCacheEntry removed = userSessions.remove(key);
                if (removed != null) {
                    removed.clear();
                }
            }

            if (!expiredKeys.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("Cleaned up %d expired timeoff sessions", expiredKeys.size()));
            }

        } finally {
            globalLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // NEW: BALANCE-NEUTRAL WRITE-THROUGH OPERATIONS (FOR COMMANDS)
    // ========================================================================

    /**
     * Add time off through cache WITHOUT holiday balance adjustment.
     * Used by commands that handle balance logic themselves.
     */
    public boolean addTimeOffToCacheWithoutBalanceUpdate(String username, Integer userId, int year, List<LocalDate> dates, String timeOffType) {
        try {
            String userKey = createUserKey(username, year);
            TimeOffCacheEntry cacheEntry = userSessions.get(userKey);

            if (cacheEntry == null || !cacheEntry.isValid()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No valid session found for %s - %d, loading session first", username, year));

                if (!loadUserSession(username, userId, year)) {
                    return false;
                }
                cacheEntry = userSessions.get(userKey);
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Adding %d time off requests to cache for %s (%s) [balance-neutral]", dates.size(), username, timeOffType));

            // Get tracker from cache
            TimeOffTracker tracker = cacheEntry.getTracker();
            if (tracker == null) {
                LoggerUtil.error(this.getClass(), "Tracker is null in cache entry");
                return false;
            }

            // Add requests to tracker
            int addedCount = 0;
            for (LocalDate date : dates) {
                // Check if request already exists for this date
                boolean exists = tracker.getRequests() != null && tracker.getRequests().stream()
                        .anyMatch(req -> date.equals(req.getDate()) && timeOffType.equals(req.getTimeOffType()));

                if (!exists) {
                    TimeOffRequest request = new TimeOffRequest();
                    request.setRequestId(java.util.UUID.randomUUID().toString());
                    request.setDate(date);
                    request.setTimeOffType(timeOffType);
                    request.setCreatedAt(LocalDateTime.now());
                    request.setLastUpdated(LocalDateTime.now());
                    request.setStatus("APPROVED"); // Immediate approval for user requests
                    request.setEligibleDays(0);
                    request.setNotes("Added via time management interface");

                    // Add to tracker
                    if (tracker.getRequests() == null) {
                        tracker.setRequests(new ArrayList<>());
                    }
                    tracker.getRequests().add(request);
                    addedCount++;

                    LoggerUtil.debug(this.getClass(), String.format(
                            "Added %s request for %s on %s to tracker", timeOffType, username, date));
                } else {
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Skipped duplicate %s request for %s on %s", timeOffType, username, date));
                }
            }

            if (addedCount > 0) {
                // Update tracker metadata
                tracker.setLastSyncTime(LocalDateTime.now());

                // Update cache
                cacheEntry.updateTracker(tracker);

                // Write-through: Save to file immediately
                timeOffDataService.writeUserLocalTrackerWithSyncAndBackup(username, userId, tracker, year);

                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully added and persisted %d new time off requests for %s (balance-neutral)", addedCount, username));
            }

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error adding time off to cache for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Remove time off through cache WITHOUT holiday balance adjustment.
     * Used by commands that handle balance logic themselves.
     */
    public boolean removeTimeOffFromCacheWithoutBalanceUpdate(String username, Integer userId, int year, LocalDate date) {
        try {
            String userKey = createUserKey(username, year);
            TimeOffCacheEntry cacheEntry = userSessions.get(userKey);

            if (cacheEntry == null || !cacheEntry.isValid()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No valid session found for %s - %d", username, year));
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Removing time off request from cache for %s on %s [balance-neutral]", username, date));

            // Get tracker from cache
            TimeOffTracker tracker = cacheEntry.getTracker();
            if (tracker == null || tracker.getRequests() == null) {
                LoggerUtil.warn(this.getClass(), "No requests found in tracker");
                return false;
            }

            // Find and remove request
            TimeOffRequest removedRequest = null;
            for (TimeOffRequest request : new ArrayList<>(tracker.getRequests())) {
                if (date.equals(request.getDate())) {
                    removedRequest = request;
                    tracker.getRequests().remove(request);
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Removed %s request for %s on %s from tracker", request.getTimeOffType(), username, date));
                    break;
                }
            }

            if (removedRequest != null) {
                // Update tracker metadata
                tracker.setLastSyncTime(LocalDateTime.now());

                // Update cache
                cacheEntry.updateTracker(tracker);

                // Write-through: Save to file immediately
                timeOffDataService.writeUserLocalTrackerWithSyncAndBackup(username, userId, tracker, year);

                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully removed and persisted time off request for %s on %s (balance-neutral)", username, date));
                return true;
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "No time off request found to remove for %s on %s", username, date));
                return false;
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error removing time off from cache for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        }
    }

    // ========================================================================
    // FAST READ OPERATIONS (From Cache)
    // ========================================================================

    /**
     * Get time off tracker from cache (fast read)
     */
    public TimeOffTracker getTracker(String username, int year) {
        try {
            String userKey = createUserKey(username, year);
            TimeOffCacheEntry cacheEntry = userSessions.get(userKey);

            if (cacheEntry != null && cacheEntry.isValid() && !cacheEntry.isExpired()) {
                return cacheEntry.getTracker();
            }

            LoggerUtil.debug(this.getClass(), String.format("No valid cached tracker for %s - %d", username, year));
            return null;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting tracker from cache for %s - %d: %s", username, year, e.getMessage()));
            return null;
        }
    }

    /**
     * Get time off summary from cached tracker (fast display)
     */
    public TimeOffSummaryDTO getSummary(String username, int year) {
        try {
            TimeOffTracker tracker = getTracker(username, year);

            if (tracker == null) {
                LoggerUtil.debug(this.getClass(), String.format("No tracker found for summary calculation %s - %d", username, year));
                return createEmptySummary(username);
            }

            // Calculate summary from tracker
            return buildSummaryFromTracker(tracker, username, year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting summary from cache for %s - %d: %s", username, year, e.getMessage()));
            return createEmptySummary(username);
        }
    }

    /**
     * Get upcoming time off from cached tracker (fast display)
     */
    public List<WorkTimeTable> getUpcomingTimeOff(String username, Integer userId, int year) {
        try {
            TimeOffTracker tracker = getTracker(username, year);

            if (tracker == null || tracker.getRequests() == null) {
                return new ArrayList<>();
            }

            // Filter for upcoming entries and convert to WorkTimeTable
            LocalDate today = LocalDate.now();
            return tracker.getRequests().stream()
                    .filter(request -> request.getDate() != null && request.getDate().isAfter(today))
                    .filter(request -> "APPROVED".equals(request.getStatus()))
                    .map(request -> convertToWorkTimeTable(request, userId))
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate)).collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting upcoming timeoff from cache for %s - %d: %s", username, year, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // CACHE DIAGNOSTICS AND MANAGEMENT
    // ========================================================================

    /**
     * Check if user has active session
     */
    public boolean hasActiveSession(String username, int year) {
        String userKey = createUserKey(username, year);
        TimeOffCacheEntry entry = userSessions.get(userKey);
        return entry != null && entry.isValid() && !entry.isExpired();
    }

    /**
     * Get cache statistics for monitoring
     */
    public String getCacheStatistics() {
        globalLock.readLock().lock();
        try {
            int totalSessions = userSessions.size();
            long validSessions = userSessions.values().stream().filter(TimeOffCacheEntry::isValid).count();
            long expiredSessions = userSessions.values().stream().filter(TimeOffCacheEntry::isExpired).count();

            return String.format("TimeOffCache: %d total sessions, %d valid, %d expired", totalSessions, validSessions, expiredSessions);
        } finally {
            globalLock.readLock().unlock();
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Create user key for cache indexing
     */
    private String createUserKey(String username, int year) {
        return String.format("%s-%d", username, year);
    }

    /**
     * Create empty tracker for new users
     */
    private TimeOffTracker createEmptyTracker(String username, Integer userId, int year) {
        TimeOffTracker tracker = new TimeOffTracker();
        tracker.setUsername(username);
        tracker.setUserId(userId);
        tracker.setYear(year);
        tracker.setRequests(new ArrayList<>());
        return tracker;
    }

    /**
     * Build summary from tracker data
     */
    private TimeOffSummaryDTO buildSummaryFromTracker(TimeOffTracker tracker, String username, int year) {
        try {
            int coDays = 0;
            int cmDays = 0;
            int snDays = 0;

            if (tracker.getRequests() != null) {
                for (TimeOffRequest request : tracker.getRequests()) {
                    if ("APPROVED".equals(request.getStatus())) {
                        switch (request.getTimeOffType()) {
                            case "CO" -> coDays++;
                            case "CM" -> cmDays++;
                            case "SN" -> snDays++;
                        }
                    }
                }
            }

            // Get available holiday days from user profile
            int availablePaidDays = getHolidayBalance(username);
            int paidDaysTaken = coDays; // CO days are paid vacation days
            int remainingPaidDays = Math.max(0, availablePaidDays - paidDaysTaken);

            return TimeOffSummaryDTO.builder()
                    .coDays(coDays)
                    .cmDays(cmDays)
                    .snDays(snDays)
                    .paidDaysTaken(paidDaysTaken)
                    .remainingPaidDays(remainingPaidDays)
                    .availablePaidDays(availablePaidDays)
                    .build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error building summary from tracker for %s: %s", username, e.getMessage()));
            return createEmptySummary(username);
        }
    }

    /**
     * Convert TimeOffRequest to WorkTimeTable for display compatibility
     */
    private WorkTimeTable convertToWorkTimeTable(TimeOffRequest request, Integer userId) {

        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(request.getDate());
        entry.setTimeOffType(request.getTimeOffType());
        entry.setAdminSync(MergingStatusConstants.USER_INPUT);
        // Clear work-related fields for time off
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);

        return entry;
    }

    /**
     * Get holiday balance from user context cache (for current user's own data)
     */
    private int getHolidayBalance(String username) {
        try {
            // Get current user context
            String currentUsername = mainDefaultUserContextCache.getCurrentUsername();

            if (username.equals(currentUsername)) {
                // For current user - use MainDefaultUserContextCache (authoritative)
                Integer balance = mainDefaultUserContextCache.getCurrentPaidHolidayDays();
                LoggerUtil.debug(this.getClass(), String.format("Got holiday balance from MainDefaultUserContextCache for %s: %d", username, balance != null ? balance : 0));
                return balance != null ? balance : 0;
            } else {
                Optional<User> userOpt = allUsersCacheService.getUserAsUserObject(username);
                if (userOpt.isPresent()) {
                    Integer balance = userOpt.get().getPaidHolidayDays();
                    LoggerUtil.debug(this.getClass(), String.format("Got holiday balance from AllUsersCacheService for %s: %d", username, balance != null ? balance : 0));
                    return balance != null ? balance : 0;
                } else {
                    LoggerUtil.warn(this.getClass(), String.format("User not found in AllUsersCacheService: %s", username));
                    return 0;
                }
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error getting holiday balance for %s: %s", username, e.getMessage()));
            return 0;
        }
    }

    /**
     * Create empty time off summary
     */
    private TimeOffSummaryDTO createEmptySummary(String username) {

        return TimeOffSummaryDTO.builder()
                .coDays(0)
                .snDays(0)
                .cmDays(0)
                .paidDaysTaken(0)
                .remainingPaidDays(getHolidayBalance(username))
                .availablePaidDays(getHolidayBalance(username))
                .build();
    }
}