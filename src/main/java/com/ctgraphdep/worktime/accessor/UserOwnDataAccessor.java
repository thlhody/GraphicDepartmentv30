
// ========================================================================
// 2. UserOwnDataAccessor - User accessing own data through cache
// ========================================================================

package com.ctgraphdep.worktime.accessor;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
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
            LoggerUtil.debug(this.getClass(), String.format(
                    "User reading own worktime data through cache with backup for %s: %d/%d", username, year, month));

            Integer userId = context.getUserId(username);
            if (userId == null) {
                LoggerUtil.warn(this.getClass(), String.format("User ID not found for %s", username));
                return new ArrayList<>();
            }

            // Use WorktimeCacheService: cache → file fallback → emergency
            List<WorkTimeTable> entries = worktimeCacheService.getMonthEntriesWithFallback(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading own worktime data for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void writeWorktimeWithStatus(String username, List<WorkTimeTable> entries,
                                        int year, int month, String userRole) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "User writing %d worktime entries with intelligent status management for %s: %d/%d (role: %s)",
                    entries.size(), username, year, month, userRole));

            Integer userId = context.getUserId(username);
            if (userId == null) {
                throw new IllegalArgumentException("User ID not found for " + username);
            }

            // INTELLIGENT STATUS MANAGEMENT: Determine status for each entry
            List<WorkTimeTable> processedEntries = new ArrayList<>();

            for (WorkTimeTable entry : entries) {
                WorkTimeTable processedEntry = cloneEntry(entry);

                // Find existing entry to determine appropriate status
                WorkTimeTable existingEntry = findExistingEntry(username, userId,
                        processedEntry.getWorkDate(), year, month);

                // Apply intelligent status determination
                String appropriateStatus = determineAppropriateStatus(existingEntry, userRole);
                processedEntry.setAdminSync(appropriateStatus);

                LoggerUtil.debug(this.getClass(), String.format(
                        "Set status '%s' for user %d on %s (existing: %s, role: %s)",
                        appropriateStatus, processedEntry.getUserId(), processedEntry.getWorkDate(),
                        existingEntry != null ? "exists" : "new", userRole));

                processedEntries.add(processedEntry);
            }

            // Sort entries for consistency
            processedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate)
                    .thenComparingInt(WorkTimeTable::getUserId));

            // Use WorktimeCacheService: file first → cache second
            boolean success = worktimeCacheService.saveMonthEntriesWithWriteThrough(
                    username, userId, year, month, processedEntries);

            if (!success) {
                throw new RuntimeException("Failed to save worktime entries with status through cache");
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d user worktime entries with intelligent status to %d/%d for %s",
                    processedEntries.size(), year, month, username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error writing user worktime with status for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            throw new RuntimeException("Failed to write user worktime entries with status", e);
        }
    }

    @Override
    public void writeWorktimeEntryWithStatus(String username, WorkTimeTable entry, String userRole) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "User writing single worktime entry with status for %s: user %d on %s (role: %s)",
                    username, entry.getUserId(), entry.getWorkDate(), userRole));

            LocalDate date = entry.getWorkDate();
            int year = date.getYear();
            int month = date.getMonthValue();

            // Load existing entries
            List<WorkTimeTable> existingEntries = readWorktime(username, year, month);

            // Remove existing entry for same user/date if any
            existingEntries.removeIf(existing ->
                    existing.getUserId().equals(entry.getUserId()) &&
                            existing.getWorkDate().equals(date));

            // Add new entry
            existingEntries.add(entry);

            // Write all entries with status management
            writeWorktimeWithStatus(username, existingEntries, year, month, userRole);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Successfully wrote single user entry with intelligent status for %s: user %d on %s",
                    username, entry.getUserId(), date));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error writing single user entry with status for %s: user %d on %s: %s",
                    username, entry.getUserId(), entry.getWorkDate(), e.getMessage()), e);
            throw new RuntimeException("Failed to write user worktime entry with status", e);
        }
    }

    // ========================================================================
    // INTELLIGENT STATUS DETERMINATION LOGIC
    // ========================================================================

    /**
     * MOVED FROM SERVICE: Determine appropriate status when saving entries.
     * Same logic as WorktimeOperationService.determineAppropriateStatus() but now in accessor.
     * Status Transition Rules:
     * - If no existing entry → use base input status (USER_INPUT, ADMIN_INPUT, TEAM_INPUT)
     * - If entry status null/empty → use base input status
     * - If entry status is modifiable → create timestamped edit status
     * - If entry status is final → throw exception (cannot modify)
     */
    private String determineAppropriateStatus(WorkTimeTable existingEntry, String userRole) {
        // If no existing entry → use base input status
        if (existingEntry == null) {
            return getBaseInputStatusForRole(userRole);
        }

        // Get the existing entry's current status
        String currentStatus = existingEntry.getAdminSync();

        // Handle null or empty status as if entry doesn't exist
        if (currentStatus == null || currentStatus.trim().isEmpty()) {
            return getBaseInputStatusForRole(userRole);
        }

        // Check if existing entry can be modified
        if (MergingStatusConstants.isFinalStatus(currentStatus)) {
            String errorMsg = String.format("Cannot modify entry with final status: %s (user: %d, date: %s)",
                    currentStatus, existingEntry.getUserId(), existingEntry.getWorkDate());
            LoggerUtil.error(this.getClass(), errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // For all modifiable statuses → create timestamped edit status
        return getEditedStatusForRole(userRole);
    }

    /**
     * Get base input status for role (new entries)
     */
    private String getBaseInputStatusForRole(String userRole) {
        return switch(userRole.toUpperCase()) {
            case SecurityConstants.ROLE_ADMIN -> MergingStatusConstants.ADMIN_INPUT;
            case SecurityConstants.ROLE_TL_CHECKING -> MergingStatusConstants.TEAM_INPUT;
            default -> MergingStatusConstants.USER_INPUT;
        };
    }

    /**
     * Get timestamped edit status for role (existing entries)
     */
    private String getEditedStatusForRole(String userRole) {
        return switch(userRole.toUpperCase()) {
            case SecurityConstants.ROLE_ADMIN -> MergingStatusConstants.createAdminEditedStatus();
            case SecurityConstants.ROLE_TL_CHECKING -> MergingStatusConstants.createTeamEditedStatus();
            default -> MergingStatusConstants.createUserEditedStatus();
        };
    }

    /**
     * Find existing entry for status determination
     */
    private WorkTimeTable findExistingEntry(String username, Integer userId, LocalDate date, int year, int month) {
        try {
            List<WorkTimeTable> existingEntries = readWorktime(username, year, month);

            if (existingEntries == null) {
                return null;
            }

            return existingEntries.stream()
                    .filter(entry -> entry.getWorkDate().equals(date) && userId.equals(entry.getUserId()))
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Could not find existing entry for %s on %s: %s", username, date, e.getMessage()));
            return null;
        }
    }

    // ========================================================================
    // REGISTER OPERATIONS - CACHE WITH BACKUP
    // ========================================================================

    @Override
    public List<RegisterEntry> readRegister(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "User reading own register data through cache with backup for %s: %d/%d", username, year, month));

            // Use RegisterCacheService: cache → file fallback with backup
            List<RegisterEntry> entries = registerCacheService.getMonthEntries(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading own register data for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<RegisterCheckEntry> readCheckRegister(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "User reading own check register data through cache with backup for %s: %d/%d", username, year, month));

            // Use RegisterCheckCacheService: cache → file fallback with backup
            List<RegisterCheckEntry> entries = registerCheckCacheService.getMonthEntries(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading own check register data for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // TIME OFF OPERATIONS - CACHE WITH BACKUP
    // ========================================================================

    @Override
    public TimeOffTracker readTimeOffTracker(String username, Integer userId, int year) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "User reading own time off data through cache with backup for %s: %d", username, year));

            // Use TimeOffCacheService: session load → cache get with backup
            boolean sessionLoaded = timeOffCacheService.loadUserSession(username, userId, year);
            if (!sessionLoaded) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to load time off session for %s - %d", username, year));
            }

            TimeOffTracker tracker = timeOffCacheService.getTracker(username, year);
            LoggerUtil.debug(this.getClass(), String.format(
                    "Retrieved own time off tracker for %s - %d: %s",
                    username, year, tracker != null ? "found" : "null"));

            return tracker;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading own time off tracker for %s - %d: %s", username, year, e.getMessage()), e);
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

    /**
     * Clone entry to avoid modifying the original
     */
    private WorkTimeTable cloneEntry(WorkTimeTable original) {
        WorkTimeTable clone = new WorkTimeTable();

        // Copy all fields
        clone.setUserId(original.getUserId());
        clone.setWorkDate(original.getWorkDate());
        clone.setDayStartTime(original.getDayStartTime());
        clone.setDayEndTime(original.getDayEndTime());
        clone.setTotalWorkedMinutes(original.getTotalWorkedMinutes());
        clone.setTotalOvertimeMinutes(original.getTotalOvertimeMinutes());
        clone.setTotalTemporaryStopMinutes(original.getTotalTemporaryStopMinutes());
        clone.setTemporaryStopCount(original.getTemporaryStopCount());
        clone.setLunchBreakDeducted(original.isLunchBreakDeducted());
        clone.setTimeOffType(original.getTimeOffType());
        clone.setAdminSync(original.getAdminSync()); // Will be overridden with admin status

        return clone;
    }
}
