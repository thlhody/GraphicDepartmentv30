package com.ctgraphdep.worktime.accessor;

import com.ctgraphdep.worktime.util.OptimizedStatusUpdateUtil;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.cache.WorktimeCacheService;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.service.cache.RegisterCacheService;
import com.ctgraphdep.service.cache.RegisterCheckCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * User own data accessor - COMPREHENSIVE CACHE ACCESS.
 * Used when user accesses their own data (all types: worktime/register/checkregister/timeoff).
 * Everything goes through cache with backup fallback.
 * Supports both read and write operations through cache.
 */
public class UserOwnDataAccessor implements WorktimeDataAccessor {

    private final WorktimeCacheService worktimeCacheService;
    private final TimeOffCacheService timeOffCacheService;
    private final RegisterCacheService registerCacheService;
    private final RegisterCheckCacheService registerCheckCacheService;
    private final WorktimeOperationContext context;

    public UserOwnDataAccessor(WorktimeCacheService worktimeCacheService,
                               TimeOffCacheService timeOffCacheService,
                               RegisterCacheService registerCacheService,
                               RegisterCheckCacheService registerCheckCacheService,
                               WorktimeOperationContext context) {
        this.worktimeCacheService = worktimeCacheService;
        this.timeOffCacheService = timeOffCacheService;
        this.registerCacheService = registerCacheService;
        this.registerCheckCacheService = registerCheckCacheService;
        this.context = context;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // WORKTIME OPERATIONS - CACHE WITH BACKUP
    // ========================================================================

    @Override
    public List<WorkTimeTable> readWorktime(String username, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("User reading own worktime data through cache with backup for %s: %d/%d", username, year, month));

            Integer userId = context.getUserId(username);
            if (userId == null) {
                LoggerUtil.warn(this.getClass(), String.format("User ID not found for %s", username));
                return new ArrayList<>();
            }

            // Use WorktimeCacheService: cache → file fallback → emergency
            List<WorkTimeTable> entries = worktimeCacheService.getMonthEntriesWithFallback(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading own worktime data for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void writeWorktimeWithStatus(String username, List<WorkTimeTable> entries, int year, int month, String userRole) {
        try {
            LoggerUtil.info(this.getClass(), String.format("OPTIMIZED user writing %d worktime entries with intelligent status management for %s: %d/%d (role: %s)",
                    entries.size(), username, year, month, userRole));

            Integer userId = context.getUserId(username);
            if (userId == null) {
                throw new IllegalArgumentException("User ID not found for " + username);
            }

            // OPTIMIZATION: Load existing entries ONCE for comparison
            List<WorkTimeTable> existingEntries = worktimeCacheService.getMonthEntriesWithFallback(username, userId, year, month);
            if (existingEntries == null) {
                existingEntries = new ArrayList<>();
            }

            // OPTIMIZATION: Use new optimized status update utility
            OptimizedStatusUpdateUtil.StatusUpdateResult result = OptimizedStatusUpdateUtil.updateChangedEntriesOnly(
                    entries, existingEntries, String.format("user-write-%s-%d/%d", username, year, month));

            // Sort entries for consistency
            List<WorkTimeTable> processedEntries = result.getProcessedEntries();
            processedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));

            // Use WorktimeCacheService: file first → cache second
            boolean success = worktimeCacheService.saveMonthEntriesWithWriteThrough(username, userId, year, month, processedEntries);

            if (!success) {
                throw new RuntimeException("Failed to save worktime entries with status through cache");
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully wrote %d user worktime entries to %d/%d for %s. %s",
                    processedEntries.size(), year, month, username, result.getPerformanceSummary()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error writing user worktime with status for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            throw new RuntimeException("Failed to write user worktime entries with status", e);
        }
    }

    @Override
    public void writeWorktimeEntryWithStatus(String username, WorkTimeTable entry, String userRole) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("OPTIMIZED user writing single worktime entry with status for %s: user %d on %s (role: %s)",
                    username, entry.getUserId(), entry.getWorkDate(), userRole));

            LocalDate date = entry.getWorkDate();
            int year = date.getYear();
            int month = date.getMonthValue();

            // OPTIMIZATION: Load existing entries for the month
            List<WorkTimeTable> existingEntries = readWorktime(username, year, month);

            // Create single-entry list for processing
            List<WorkTimeTable> singleEntryList = new ArrayList<>();
            singleEntryList.add(entry);

            // Use optimized status update utility
            OptimizedStatusUpdateUtil.StatusUpdateResult result = OptimizedStatusUpdateUtil.updateChangedEntriesOnly(
                    singleEntryList, existingEntries, String.format("user-single-write-%s-%s", username, date));

            // Get the processed entry
            WorkTimeTable processedEntry = result.getProcessedEntries().get(0);

            // Replace entry in existing list
            existingEntries.removeIf(existing ->
                    existing.getUserId().equals(processedEntry.getUserId()) &&
                            existing.getWorkDate().equals(date));
            existingEntries.add(processedEntry);

            // Write all entries with optimized status management
            writeWorktimeWithStatus(username, existingEntries, year, month, userRole);

            LoggerUtil.debug(this.getClass(), String.format("Successfully wrote single user entry for %s: user %d on %s. %s",
                    username, entry.getUserId(), date, result.getPerformanceSummary()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error writing single user entry with status for %s: user %d on %s: %s",
                    username, entry.getUserId(), entry.getWorkDate(), e.getMessage()), e);
            throw new RuntimeException("Failed to write user worktime entry with status", e);
        }
    }

    // ========================================================================
    // REGISTER OPERATIONS - CACHE WITH BACKUP
    // ========================================================================

    @Override
    public List<RegisterEntry> readRegister(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("User reading own register data through cache with backup for %s: %d/%d", username, year, month));

            // Use RegisterCacheService: cache → file fallback with backup
            List<RegisterEntry> entries = registerCacheService.getMonthEntries(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading own register data for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<RegisterCheckEntry> readCheckRegister(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("User reading own check register data through cache with backup for %s: %d/%d", username, year, month));

            // Use RegisterCheckCacheService: cache → file fallback with backup
            List<RegisterCheckEntry> entries = registerCheckCacheService.getMonthEntries(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading own check register data for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // TIME OFF OPERATIONS - CACHE WITH BACKUP
    // ========================================================================

    @Override
    public TimeOffTracker readTimeOffTracker(String username, Integer userId, int year) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("User reading own time off data through cache with backup for %s: %d", username, year));

            // Use TimeOffCacheService: session load → cache get with backup
            boolean sessionLoaded = timeOffCacheService.loadUserSession(username, userId, year);
            if (!sessionLoaded) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to load time off session for %s - %d", username, year));
            }

            TimeOffTracker tracker = timeOffCacheService.getTracker(username, year);
            LoggerUtil.debug(this.getClass(), String.format("Retrieved own time off tracker for %s - %d: %s",
                    username, year, tracker != null ? "found" : "null"));

            return tracker;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading own time off tracker for %s - %d: %s", username, year, e.getMessage()), e);
            return null;
        }
    }

    @Override
    public String getAccessType() {
        return "USER_OWN_DATA_CACHE";
    }

    @Override
    public boolean supportsWrite() {
        return true;
    }
}
