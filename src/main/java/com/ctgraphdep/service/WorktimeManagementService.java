package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.IsNationalHolidayCommand;
import com.ctgraphdep.validation.commands.ValidateHolidayDateCommand;
import lombok.Getter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Comprehensive service for managing worktime entries.
 * This consolidated service combines functionality from:
 * - UserWorkTimeService
 * - WorkTimeConsolidationService
 * - WorkTimeManagementService (original)
 */
@Service
public class WorktimeManagementService {

    private final WorktimeDataService worktimeDataService;
    private final UserService userService;
    private final HolidayManagementService holidayManagementService;
    private final WorktimeMergeService worktimeMergeService;
    private final TimeValidationService timeValidationService;

    // Locks for concurrent operations
    private final ReentrantReadWriteLock userLock = new ReentrantReadWriteLock();
    private final ReentrantLock adminLock = new ReentrantLock();
    private final ReentrantLock consolidationLock = new ReentrantLock();

    public WorktimeManagementService(WorktimeDataService worktimeDataService, UserService userService, HolidayManagementService holidayManagementService,
            WorktimeMergeService worktimeMergeService, TimeValidationService timeValidationService) {
        this.worktimeDataService = worktimeDataService;
        this.userService = userService;
        this.holidayManagementService = holidayManagementService;
        this.worktimeMergeService = worktimeMergeService;
        this.timeValidationService = timeValidationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ============= USER WORKTIME OPERATIONS =============

    /**
     * Load month worktime for a user - local with network sync
     */
    @PreAuthorize("#username == authentication.name")
    public List<WorkTimeTable> loadMonthWorktime(String username, int year, int month) {
        Integer userId = getUserId(username);
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        userLock.readLock().lock();
        try {
            // Use WorktimeDataService - handles smart fallback automatically
            List<WorkTimeTable> userEntries = worktimeDataService.readUserLocalReadOnly(
                    username, year, month, currentUsername);

            // Load admin entries for merging
            List<WorkTimeTable> adminEntries = loadAdminEntries(userId, year, month);

            // Merge user + admin entries
            List<WorkTimeTable> mergedEntries = worktimeMergeService.mergeEntries(
                    userEntries, adminEntries, userId);

            // Write back merged entries if there's data to merge
            if (!adminEntries.isEmpty() || !userEntries.isEmpty()) {
                worktimeDataService.writeUserLocalWithSyncAndBackup(
                        username, mergedEntries, year, month);
            }

            return mergedEntries.stream()
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                    .collect(Collectors.toList());

        } finally {
            userLock.readLock().unlock();
        }
    }

    /**
     * Save worktime entries for a user
     */
    @PreAuthorize("#username == authentication.name")
    public void saveWorkTimeEntries(String username, List<WorkTimeTable> entries) {
        if (entries.isEmpty()) {
            return;
        }

        Integer userId = getUserId(username);
        userLock.writeLock().lock();
        try {
            // Validate all entries
            validateEntries(entries, userId);

            // Set sync status for all entries
            entries.forEach(entry -> entry.setAdminSync(SyncStatusMerge.USER_INPUT));

            // Group entries by month for processing
            Map<YearMonth, List<WorkTimeTable>> entriesByMonth = entries.stream().collect(Collectors.groupingBy(entry -> YearMonth.from(entry.getWorkDate())));

            // Process each month's entries
            entriesByMonth.forEach((yearMonth, monthEntries) -> processMonthEntries(username, userId, monthEntries, yearMonth.getYear(), yearMonth.getMonthValue()));

            LoggerUtil.info(this.getClass(), String.format("Saved %d entries for user %s", entries.size(), username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving worktime entries for %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to save worktime entries", e);
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * View-only worktime data for team leaders and admins
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM_LEADER')")
    public List<WorkTimeTable> loadViewOnlyWorktime(String username, int year, int month) {
        Integer userId = getUserId(username);

        // Network read-only - no local file access, no write-back
        List<WorkTimeTable> userEntries = worktimeDataService.readUserFromNetworkOnly(username, year, month);
        List<WorkTimeTable> adminEntries = loadAdminEntries(userId, year, month);

        // Simple merge for display only (no write-back)
        Map<LocalDate, WorkTimeTable> mergedMap = new HashMap<>();

        if (userEntries != null) {
            userEntries.forEach(entry -> mergedMap.put(entry.getWorkDate(), entry));
        }

        if (adminEntries != null) {
            adminEntries.forEach(adminEntry -> {
                if (adminEntry.getAdminSync() == SyncStatusMerge.ADMIN_EDITED) {
                    adminEntry.setAdminSync(SyncStatusMerge.USER_DONE);
                    mergedMap.put(adminEntry.getWorkDate(), adminEntry);
                }
            });
        }

        return new ArrayList<>(mergedMap.values()).stream().sorted(Comparator.comparing(WorkTimeTable::getWorkDate)).collect(Collectors.toList());
    }

    /**
     * Save a single worktime entry with file-based authentication
     */
    public void saveWorkTimeEntry(String username, WorkTimeTable entry, int year, int month, String operatingUsername) {
        userLock.writeLock().lock();
        try {
            List<WorkTimeTable> entries = loadUserEntries(username, year, month, operatingUsername);
            if (entries == null) {
                entries = new ArrayList<>();
            }

            // Remove existing entry if present
            entries.removeIf(e -> e.getUserId().equals(entry.getUserId()) && e.getWorkDate().equals(entry.getWorkDate()));

            entries.add(entry);
            entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparing(WorkTimeTable::getUserId));

            try {
                worktimeDataService.writeUserLocalWithSyncAndBackup(username, entries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Saved worktime entry for user %s - %d/%d using file-based auth", username, year, month));
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Failed to save worktime entry for user %s: %s", username, e.getMessage()));
                throw new RuntimeException("Failed to save worktime entry", e);
            }
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * Check if a date is not a national holiday.
     */
    public boolean isNotHoliday(LocalDate date) {
        if (date == null) {
            return true; // Null dates are considered "not holidays"
        }

        // Fetch the data here
        List<WorkTimeTable> entries = worktimeDataService.readAdminByUserNetworkReadOnly(date.getYear(), date.getMonthValue());

        // Create and execute the command with the fetched data
        IsNationalHolidayCommand command = timeValidationService.getValidationFactory().createIsNationalHolidayCommand(date, entries);

        return !timeValidationService.execute(command);
    }

    /**
     * Process entries for a specific month - combining entries from different sources
     */
    private void processMonthEntries(String username, Integer userId, List<WorkTimeTable> newEntries, int year, int month) {
        try {
            List<WorkTimeTable> existingEntries = loadUserEntries(username, year, month);

            // Remove existing entries for these dates
            Set<LocalDate> newDates = newEntries.stream().map(WorkTimeTable::getWorkDate).collect(Collectors.toSet());

            List<WorkTimeTable> remainingEntries = existingEntries.stream().filter(entry -> !entry.getUserId().equals(userId) || !newDates.contains(entry.getWorkDate()))
                    .collect(Collectors.toList());

            remainingEntries.addAll(newEntries);
            remainingEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparing(WorkTimeTable::getUserId));

            worktimeDataService.writeUserLocalWithSyncAndBackup(username, remainingEntries, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error processing month entries for %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to process month entries", e);
        }
    }

    public List<WorkTimeTable> loadUserEntries(String username, int year, int month, String operatingUsername) {
        try {
            List<WorkTimeTable> entries = worktimeDataService.readUserLocalReadOnly(username, year, month, operatingUsername);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading user entries for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ============= ADMIN WORKTIME OPERATIONS =============

    /**
     * Process admin worktime update for a specific date
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void processWorktimeUpdate(Integer userId, LocalDate date, String value) {
        adminLock.lock();
        try {
            validateUpdateRequest(userId, date);

            // Get existing entry for comparison
            WorkTimeTable existingEntry = getExistingEntry(userId, date);

            // Create new entry based on input value
            WorkTimeTable newEntry = createEntryFromValue(userId, date, value);

            // Handle paid holiday balance updates
            handlePaidHolidayBalance(existingEntry, newEntry);

            // Save the entry
            saveAdminEntry(newEntry, date.getYear(), date.getMonthValue());

            LoggerUtil.info(this.getClass(), String.format("Processed admin worktime update for user %d on %s: %s", userId, date, value));

        } finally {
            adminLock.unlock();
        }
    }

    /**
     * Add a national holiday for all users
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void addNationalHoliday(LocalDate date) {
        adminLock.lock();
        try {
            validateHolidayDate(date);

            List<WorkTimeTable> entries = loadAdminEntries(date.getYear(), date.getMonthValue());
            List<User> nonAdminUsers = getNonAdminUsers();

            // Remove existing entries for this date
            entries.removeIf(entry -> entry.getWorkDate().equals(date));

            // Create holiday entries for each user
            List<WorkTimeTable> holidayEntries = createHolidayEntriesForUsers(nonAdminUsers, date);

            if (!holidayEntries.isEmpty()) {
                entries.addAll(holidayEntries);
                saveAdminEntry(entries, date.getYear(), date.getMonthValue());
                LoggerUtil.info(this.getClass(), String.format("Added national holiday for %s with %d entries", date, holidayEntries.size()));
            }
        } finally {
            adminLock.unlock();
        }
    }

    /**
     * Get worked days count for a user in a specific month
     */
    @PreAuthorize("hasRole('ADMIN')")
    public int getWorkedDays(Integer userId, int year, int month) {
        return (int) loadAdminEntries(year, month).stream().filter(entry -> entry.getUserId().equals(userId))
                .filter(entry -> entry.getTimeOffType() == null && entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0).count();
    }

    // ============= WORKTIME CONSOLIDATION OPERATIONS =============

    /**
     * Consolidate worktime entries between admin and user files
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void consolidateWorkTimeEntries(int year, int month) {
        consolidationLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting worktime consolidation for %d/%d", month, year));

            // Get all non-admin users
            List<User> users = userService.getAllUsers().stream().filter(user -> !user.isAdmin()).toList();

            LoggerUtil.info(this.getClass(), String.format("Found %d non-admin users to process", users.size()));

            // Load existing admin entries
            List<WorkTimeTable> adminEntries = loadAdminWorktime(year, month);

            LoggerUtil.info(this.getClass(), String.format("Loaded %d existing admin entries", adminEntries != null ? adminEntries.size() : 0));

            // Create a map of admin entries by user and date for efficient lookup
            assert adminEntries != null;
//            if(adminEntries != null){}
            Map<String, WorkTimeTable> adminEntriesMap = createAdminEntriesMap(adminEntries);

            // Process each user's entries
            List<WorkTimeTable> consolidatedEntries = new ArrayList<>();
            for (User user : users) {
                try {
                    List<WorkTimeTable> userProcessedEntries = processUserEntries(user, year, month, adminEntriesMap);
                    consolidatedEntries.addAll(userProcessedEntries);

                    LoggerUtil.info(this.getClass(), String.format("Processed %d entries for user %s", userProcessedEntries.size(), user.getUsername()));
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error processing user %s: %s", user.getUsername(), e.getMessage()));
                    // Continue with next user
                }
            }

            // Sort and save consolidated entries
            if (!consolidatedEntries.isEmpty()) {
                saveConsolidatedEntries(consolidatedEntries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Saved %d consolidated entries", consolidatedEntries.size()));
            } else {
                LoggerUtil.info(this.getClass(), "No entries to save after consolidation");
            }

        } finally {
            consolidationLock.unlock();
        }
    }

    /**
     * Process a user's entries for consolidation
     */
    private List<WorkTimeTable> processUserEntries(User user, int year, int month, Map<String, WorkTimeTable> adminEntriesMap) {
        try {
            // Load user entries from network with null safety
            List<WorkTimeTable> userEntries = worktimeDataService.readUserFromNetworkOnly(user.getUsername(), year, month);

            if (userEntries == null) {
                userEntries = new ArrayList<>();
            }

            List<WorkTimeTable> processedEntries = new ArrayList<>();
            Map<String, WorkTimeTable> processedEntriesMap = new HashMap<>();

            // First, check for resolved entries that should replace unresolved admin entries
            for (WorkTimeTable userEntry : userEntries) {
                if (SyncStatusMerge.USER_INPUT.equals(userEntry.getAdminSync())) {
                    // This is a resolved entry from the user
                    String entryKey = createEntryKey(userEntry.getUserId(), userEntry.getWorkDate());

                    // Check if admin has an unresolved entry for this date
                    WorkTimeTable adminEntry = adminEntriesMap != null ? adminEntriesMap.get(entryKey) : null;

                    if (adminEntry != null && SyncStatusMerge.USER_IN_PROCESS.equals(adminEntry.getAdminSync())) {
                        LoggerUtil.info(this.getClass(), String.format("Admin consolidation: Updating admin USER_IN_PROCESS entry with user's resolved entry for %s",
                                        userEntry.getWorkDate()));

                        // Use the resolved user entry instead of the unresolved admin entry
                        processedEntries.add(userEntry);
                        processedEntriesMap.put(entryKey, userEntry);
                    }
                }
            }

            // Next, process remaining admin entries (except those we just replaced)
            if (adminEntriesMap != null) {
                for (Map.Entry<String, WorkTimeTable> entry : adminEntriesMap.entrySet()) {
                    String entryKey = entry.getKey();
                    WorkTimeTable adminEntry = entry.getValue();

                    // Skip if we already processed this entry
                    if (processedEntriesMap.containsKey(entryKey)) {
                        continue;
                    }

                    // Only include admin entry if it's for this user
                    if (adminEntry.getUserId().equals(user.getUserId())) {
                        if (SyncStatusMerge.ADMIN_BLANK.equals(adminEntry.getAdminSync())) {
                            // Keep ADMIN_BLANK entries to ensure they're properly handled
                            processedEntries.add(adminEntry);
                            processedEntriesMap.put(entryKey, adminEntry);
                        }
                        else if (!SyncStatusMerge.USER_IN_PROCESS.equals(adminEntry.getAdminSync())) {
                            // Include all admin entries EXCEPT USER_IN_PROCESS
                            processedEntries.add(adminEntry);
                            processedEntriesMap.put(entryKey, adminEntry);
                        }
                    }
                }
            }

            // Finally, process remaining user entries
            for (WorkTimeTable userEntry : userEntries) {
                // Use worktimeMergeService for consistent merge logic
                WorkTimeTable mergedEntry = worktimeMergeService.mergeForConsolidation(
                        userEntry, null, processedEntriesMap);

                if (mergedEntry != null) {
                    String entryKey = createEntryKey(mergedEntry.getUserId(), mergedEntry.getWorkDate());

                    // Only add if not already processed
                    if (!processedEntriesMap.containsKey(entryKey)) {
                        processedEntries.add(mergedEntry);
                        processedEntriesMap.put(entryKey, mergedEntry);
                    }
                }
            }

            return processedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error processing entries for user %s: %s", user.getUsername(), e.getMessage()));
            return new ArrayList<>();  // Return empty list instead of throwing
        }
    }

    /**
     * Get viewable entries for admin display - filtering out USER_IN_PROCESS entries
     */
    public List<WorkTimeTable> getViewableEntries(int year, int month) {
        List<WorkTimeTable> allEntries = loadAdminWorktime(year, month);

        // Filter out USER_IN_PROCESS entries
        return allEntries.stream()
                .filter(entry -> !SyncStatusMerge.USER_IN_PROCESS.equals(entry.getAdminSync()))
                .collect(Collectors.toList());
    }

// ========================================================================
// NEW: INDIVIDUAL FIELD UPDATE OPERATIONS
// ========================================================================

    /**
     * Update start time for a specific worktime entry
     */
    public boolean updateStartTime(String username, Integer userId, LocalDate date, LocalDateTime startTime) {
        userLock.writeLock().lock();
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Updating start time for %s on %s to %s", username, date, startTime));

            // Validate edit permissions
            FieldEditValidation validation = canEditField(username, userId, date, "startTime");
            if (!validation.isCanEdit()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Cannot edit start time for %s on %s: %s", username, date, validation.getReason()));
                return false;
            }

            // Load current month entries
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = loadUserEntries(username, year, month);

            // Find and update the specific entry
            boolean entryFound = false;
            for (WorkTimeTable entry : entries) {
                if (entry.getUserId().equals(userId) && entry.getWorkDate().equals(date)) {
                    entry.setDayStartTime(startTime);
                    entry.setAdminSync(SyncStatusMerge.USER_INPUT);
                    entryFound = true;
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Updated start time for entry on %s", date));
                    break;
                }
            }

            // If entry doesn't exist, create new one
            if (!entryFound) {
                WorkTimeTable newEntry = createNewWorktimeEntry(userId, date);
                newEntry.setDayStartTime(startTime);
                newEntry.setAdminSync(SyncStatusMerge.USER_INPUT);
                entries.add(newEntry);
                LoggerUtil.info(this.getClass(), String.format(
                        "Created new entry with start time for %s on %s", username, date));
            }

            // Save back to file
            worktimeDataService.writeUserLocalWithSyncAndBackup(username, entries, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully updated start time for %s on %s", username, date));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating start time for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * Update end time for a specific worktime entry
     */
    public boolean updateEndTime(String username, Integer userId, LocalDate date, LocalDateTime endTime) {
        userLock.writeLock().lock();
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Updating end time for %s on %s to %s", username, date, endTime));

            // Validate edit permissions
            FieldEditValidation validation = canEditField(username, userId, date, "endTime");
            if (!validation.isCanEdit()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Cannot edit end time for %s on %s: %s", username, date, validation.getReason()));
                return false;
            }

            // Load current month entries
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = loadUserEntries(username, year, month);

            // Find and update the specific entry
            boolean entryFound = false;
            for (WorkTimeTable entry : entries) {
                if (entry.getUserId().equals(userId) && entry.getWorkDate().equals(date)) {
                    entry.setDayEndTime(endTime);

                    // Recalculate total worked minutes if both start and end times exist
                    if (entry.getDayStartTime() != null && endTime != null) {
                        int totalMinutes = calculateTotalMinutes(entry.getDayStartTime(), endTime);
                        entry.setTotalWorkedMinutes(totalMinutes);

                        // Determine lunch break
                        boolean lunchBreak = totalMinutes > (6 * 60); // More than 6 hours
                        entry.setLunchBreakDeducted(lunchBreak);

                        LoggerUtil.debug(this.getClass(), String.format(
                                "Recalculated total minutes: %d, lunch break: %s", totalMinutes, lunchBreak));
                    }

                    entry.setAdminSync(SyncStatusMerge.USER_INPUT);
                    entryFound = true;
                    break;
                }
            }

            // If entry doesn't exist, create new one
            if (!entryFound) {
                WorkTimeTable newEntry = createNewWorktimeEntry(userId, date);
                newEntry.setDayEndTime(endTime);
                newEntry.setAdminSync(SyncStatusMerge.USER_INPUT);
                entries.add(newEntry);
                LoggerUtil.info(this.getClass(), String.format(
                        "Created new entry with end time for %s on %s", username, date));
            }

            // Save back to file
            worktimeDataService.writeUserLocalWithSyncAndBackup(username, entries, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully updated end time for %s on %s", username, date));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating end time for %s on %s: %s", username, date, e.getMessage()), e);
            return false;
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * Clear time off type for a specific worktime entry
     */
    public void clearTimeOff(String username, Integer userId, LocalDate date) {
        userLock.writeLock().lock();
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Clearing time off for %s on %s", username, date));

            // Basic validation - current day check
            LocalDate today = LocalDate.now();
            if (date.equals(today)) {
                LoggerUtil.warn(this.getClass(), "Cannot edit current day");
                return;
            }

            // Load current month entries
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = loadUserEntries(username, year, month);

            // Find and clear the time off entry
            boolean entryFound = false;
            for (WorkTimeTable entry : entries) {
                if (entry.getUserId().equals(userId) && entry.getWorkDate().equals(date)) {
                    entry.setTimeOffType(null);
                    entry.setAdminSync(SyncStatusMerge.USER_INPUT);
                    entryFound = true;
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Cleared time off for entry on %s", date));
                    break;
                }
            }

            if (entryFound) {
                // Save back to file
                worktimeDataService.writeUserLocalWithSyncAndBackup(username, entries, year, month);
                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully cleared time off for %s on %s", username, date));
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No entry found to clear for %s on %s", username, date));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error clearing time off for %s on %s: %s", username, date, e.getMessage()), e);
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * Check if a field can be edited for a specific date
     */
    public FieldEditValidation canEditField(String username, Integer userId, LocalDate date, String fieldType) {
        try {
            // Check if current day
            LocalDate today = LocalDate.now();
            if (date.equals(today)) {
                return new FieldEditValidation(false, "Cannot edit current day", null);
            }

            // Check if future date
            if (date.isAfter(today)) {
                return new FieldEditValidation(false, "Cannot edit future dates", null);
            }

            // Check if weekend (for time off)
            if ("timeOff".equals(fieldType) && isWeekend(date)) {
                return new FieldEditValidation(false, "Cannot add time off on weekends", null);
            }

            // Check if national holiday (skip this check for now, can add later)
            // if (isNationalHoliday(date)) {
            //     return new FieldEditValidation(false, "Date is a national holiday", null);
            // }

            // Load existing entry to check status
            int year = date.getYear();
            int month = date.getMonthValue();
            List<WorkTimeTable> entries = loadUserEntries(username, year, month);

            WorkTimeTable existingEntry = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId) && entry.getWorkDate().equals(date))
                    .findFirst()
                    .orElse(null);

            // Check status if entry exists
            if (existingEntry != null) {
                SyncStatusMerge status = existingEntry.getAdminSync();
                if (status == SyncStatusMerge.USER_IN_PROCESS) {
                    return new FieldEditValidation(false, "Entry is currently in process", status);
                }
                if (status == SyncStatusMerge.USER_DONE && !"timeOff".equals(fieldType)) {
                    // Allow time off changes even on DONE entries, but not time changes
                    return new FieldEditValidation(false, "Entry is already completed", status);
                }
            }

            // All checks passed
            return new FieldEditValidation(true, "Can edit",
                    existingEntry != null ? existingEntry.getAdminSync() : null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error checking edit permissions for %s on %s: %s", username, date, e.getMessage()));
            return new FieldEditValidation(false, "Error checking permissions", null);
        }
    }

    // ============= HELPER METHODS =============

    /**
     * Create a new worktime entry with default values
     */
    private WorkTimeTable createNewWorktimeEntry(Integer userId, LocalDate date) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setLunchBreakDeducted(false);
        entry.setAdminSync(SyncStatusMerge.USER_INPUT);
        return entry;
    }

    /**
     * Calculate total minutes between start and end time
     */
    private int calculateTotalMinutes(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || endTime.isBefore(startTime)) {
            return 0;
        }
        return (int) Duration.between(startTime, endTime).toMinutes();
    }

    /**
     * Check if date is weekend
     */
    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek().getValue() >= 6; // Saturday = 6, Sunday = 7
    }

    private List<WorkTimeTable> loadUserEntries(String username, int year, int month) {
        try {
            // Get current username from SecurityContext if available
            String currentUsername = SecurityContextHolder.getContext().getAuthentication() != null ? SecurityContextHolder.getContext().getAuthentication().getName() : null;

            // Use the most appropriate method to load entries
            if (currentUsername != null && currentUsername.equals(username)) {
                // Normal security context flow for user accessing their own data
                List<WorkTimeTable> entries = worktimeDataService.readUserLocalReadOnly(username, year, month,username);
                return entries != null ? entries : new ArrayList<>();
            } else {
                // For admin or team lead accessing other user data
                try {
                    return worktimeDataService.readUserFromNetworkOnly(username, year, month);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error reading network worktime for user %s: %s", username, e.getMessage()));
                    return new ArrayList<>();
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading user entries for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private List<WorkTimeTable> loadAdminEntries(int year, int month) {
        try {
            List<WorkTimeTable> adminEntries = worktimeDataService.readAdminLocalReadOnly(year, month);
            return adminEntries != null ? adminEntries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading admin entries for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private List<WorkTimeTable> loadAdminEntries(Integer userId, int year, int month) {
        try {
            List<WorkTimeTable> adminEntries = worktimeDataService.readAdminByUserNetworkReadOnly(year, month);
            return adminEntries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .filter(entry -> entry.getAdminSync() == SyncStatusMerge.ADMIN_EDITED
                            || entry.getAdminSync() == SyncStatusMerge.ADMIN_BLANK)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading admin entries for user %d: %s", userId, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private List<WorkTimeTable> loadAdminWorktime(int year, int month) {
        try {
            return worktimeDataService.readAdminLocalReadOnly(year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading admin worktime for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private void saveConsolidatedEntries(List<WorkTimeTable> entries, int year, int month) {
        try {
            // Sort entries
            entries.sort(Comparator
                    .comparing(WorkTimeTable::getWorkDate)
                    .thenComparing(WorkTimeTable::getUserId));

            // Save to admin worktime - this will handle local save and network sync
            worktimeDataService.writeAdminLocalWithSyncAndBackup(entries, year, month);

            LoggerUtil.info(this.getClass(), String.format("Saved %d consolidated entries for %d/%d", entries.size(), month, year));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving consolidated entries for %d/%d: %s", month, year, e.getMessage()));
            throw new RuntimeException("Failed to save consolidated entries", e);
        }
    }

    private void saveAdminEntry(List<WorkTimeTable> entries, int year, int month) {
        try {
            // Sort entries before saving
            entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparing(WorkTimeTable::getUserId));

            // Write using DataAccessService which handles local save and network sync
            worktimeDataService.writeAdminLocalWithSyncAndBackup(entries, year, month);

            LoggerUtil.info(this.getClass(), String.format("Saved %d entries to admin worktime", entries.size()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving admin entries for %d/%d: %s", year, month, e.getMessage()));
            throw new RuntimeException("Failed to save admin entries", e);
        }
    }

    private void saveAdminEntry(WorkTimeTable entry, int year, int month) {
        List<WorkTimeTable> entries = loadAdminEntries(year, month);

        // Remove existing entry
        entries.removeIf(e -> e.getUserId().equals(entry.getUserId()) && e.getWorkDate().equals(entry.getWorkDate()));

        // Add new entry
        entries.add(entry);

        // Sort and save
        saveAdminEntry(entries, year, month);
    }

    private WorkTimeTable getExistingEntry(Integer userId, LocalDate date) {
        return loadAdminEntries(date.getYear(), date.getMonthValue()).stream()
                .filter(entry -> entry.getUserId().equals(userId) && entry.getWorkDate().equals(date))
                .findFirst()
                .orElse(null);
    }

    private List<User> getNonAdminUsers() {
        return userService.getAllUsers().stream().filter(user -> !user.isAdmin()).toList();
    }

    private List<WorkTimeTable> createHolidayEntriesForUsers(List<User> users, LocalDate date) {
        return users.stream()
                .map(user -> createTimeOffEntry(user.getUserId(), date, WorkCode.NATIONAL_HOLIDAY_CODE))
                .collect(Collectors.toList());
    }

    private Map<String, WorkTimeTable> createAdminEntriesMap(List<WorkTimeTable> adminEntries) {
        return adminEntries.stream()
                .collect(Collectors.toMap(
                        entry -> createEntryKey(entry.getUserId(), entry.getWorkDate()),
                        entry -> entry,
                        (existing, replacement) -> replacement
                ));
    }

    private String createEntryKey(Integer userId, LocalDate date) {
        return userId + "_" + date.toString();
    }

    private WorkTimeTable createEntryFromValue(Integer userId, LocalDate date, String value) {
        if (isRemoveValue(value)) {
            return createAdminBlankEntry(userId, date);
        }

        if (isTimeOffValue(value)) {
            return createTimeOffEntry(userId, date, value);
        }

        if (isWorkHoursValue(value)) {
            return createWorkHoursEntry(userId, date, Integer.parseInt(value));
        }

        throw new IllegalArgumentException("Invalid value format: " + value);
    }

    private boolean isRemoveValue(String value) {
        return value == null || value.trim().isEmpty() || "REMOVE".equals(value);
    }

    private boolean isTimeOffValue(String value) {
        return value != null && value.matches("^(SN|CO|CM)$");
    }

    private boolean isWorkHoursValue(String value) {
        return value != null && value.matches("^([1-9]|1\\d|2[0-4])$");
    }

    private boolean isTimeOffEntry(WorkTimeTable entry) {
        return entry != null && WorkCode.TIME_OFF_CODE.equals(entry.getTimeOffType());
    }

    private WorkTimeTable createAdminBlankEntry(Integer userId, LocalDate date) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);
        entry.setTimeOffType(null);
        entry.setAdminSync(SyncStatusMerge.ADMIN_BLANK);
        resetEntryValues(entry);
        return entry;
    }

    private WorkTimeTable createTimeOffEntry(Integer userId, LocalDate date, String type) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);
        entry.setTimeOffType(type.toUpperCase());
        entry.setAdminSync(SyncStatusMerge.ADMIN_EDITED);
        resetEntryValues(entry);
        return entry;
    }

    private WorkTimeTable createWorkHoursEntry(Integer userId, LocalDate date, int hours) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);

        LocalDateTime startTime = date.atTime(WorkCode.START_HOUR, 0);
        entry.setDayStartTime(startTime);

        int totalMinutes = (hours * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION;
        entry.setTotalWorkedMinutes(totalMinutes);

        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);
        entry.setDayEndTime(endTime);

        entry.setLunchBreakDeducted(hours > WorkCode.INTERVAL_HOURS_A);
        entry.setTimeOffType(null);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setAdminSync(SyncStatusMerge.ADMIN_EDITED);

        return entry;
    }

    private void resetEntryValues(WorkTimeTable entry) {
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);
    }

    private void handlePaidHolidayBalance(WorkTimeTable existingEntry, WorkTimeTable newEntry) {
        boolean wasTimeOff = isTimeOffEntry(existingEntry);
        boolean isTimeOff = isTimeOffEntry(newEntry);

        if (wasTimeOff && !isTimeOff) {
            // Restore paid holiday day when removing CO
            restorePaidHoliday(existingEntry.getUserId());
        } else if (!wasTimeOff && isTimeOff && WorkCode.TIME_OFF_CODE.equals(newEntry.getTimeOffType())) {
            // Deduct paid holiday day when adding CO
            processTimeOffUpdate(newEntry.getUserId(), newEntry.getTimeOffType());
        }
    }

    private void processTimeOffUpdate(Integer userId, String type) {
        if (WorkCode.TIME_OFF_CODE.equals(type)) {
            int availableDays = holidayManagementService.getRemainingHolidayDays(userId);
            if (availableDays < 1) {
                throw new IllegalStateException("Insufficient paid holiday days available");
            }
            holidayManagementService.updateUserHolidayDays(userId, availableDays - 1);
            LoggerUtil.info(this.getClass(), String.format("Deducted 1 paid holiday day for user %d. New balance: %d", userId, availableDays - 1));
        }
    }

    private void restorePaidHoliday(Integer userId) {
        try {
            int currentDays = holidayManagementService.getRemainingHolidayDays(userId);
            holidayManagementService.updateUserHolidayDays(userId, currentDays + 1);
            LoggerUtil.info(this.getClass(), String.format("Restored paid holiday day for user %d. New balance: %d", userId, currentDays + 1));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error restoring paid holiday: " + e.getMessage());
            throw new RuntimeException("Failed to restore paid holiday", e);
        }
    }

    private void validateUpdateRequest(Integer userId, LocalDate date) {
        if (userId == null || date == null) {
            throw new IllegalArgumentException("Invalid update parameters");
        }

        YearMonth requested = YearMonth.from(date);
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(4);

        if (requested.isBefore(previous) || requested.isAfter(current)) {
            throw new IllegalArgumentException("Can only update current or previous month");
        }

        if (requested.equals(previous) && LocalDate.now().getDayOfMonth() > 15) {
            throw new IllegalArgumentException("Previous month no longer editable after 15th");
        }
    }

    private void validateHolidayDate(LocalDate date) {
        // Create a command to validate the holiday date
        ValidateHolidayDateCommand command = new ValidateHolidayDateCommand(date, timeValidationService.getValidationFactory().getTimeProvider());
        try {
            timeValidationService.execute(command);
        } catch (IllegalArgumentException e) {
            // Re-throw the exception with the same message to maintain backward compatibility
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private void validateEntries(List<WorkTimeTable> entries, Integer userId) {
        entries.forEach(entry -> {
            if (!userId.equals(entry.getUserId())) {
                throw new SecurityException("Invalid user ID in entry");
            }
            if (entry.getWorkDate() == null) {
                throw new IllegalArgumentException("Work date cannot be null");
            }
            // Validate entry data
            validateWorkTimeEntry(entry);
        });
    }

    private void validateWorkTimeEntry(WorkTimeTable entry) {
        // Removed BLANK from valid time off types
        if (entry.getTimeOffType() != null && !entry.getTimeOffType().matches("^(SN|CO|CM)$")) {
            throw new IllegalArgumentException("Invalid time off type: " + entry.getTimeOffType());
        }

        // Validate worked minutes
        if (entry.getTotalWorkedMinutes() < 0) {
            throw new IllegalArgumentException("Total worked minutes cannot be negative");
        }

        // Validate temporary stop count
        if (entry.getTemporaryStopCount() < 0) {
            throw new IllegalArgumentException("Temporary stop count cannot be negative");
        }

        // Validate overtime minutes
        if (entry.getTotalOvertimeMinutes() < 0) {
            throw new IllegalArgumentException("Overtime minutes cannot be negative");
        }
    }

    private Integer getUserId(String username) {
        return userService.getUserByUsername(username)
                .map(User::getUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }


    /**
     * Validation result class for field editing
     */
    @Getter
    public static class FieldEditValidation {
        private final boolean canEdit;
        private final String reason;
        private final SyncStatusMerge currentStatus;

        public FieldEditValidation(boolean canEdit, String reason, SyncStatusMerge currentStatus) {
            this.canEdit = canEdit;
            this.reason = reason;
            this.currentStatus = currentStatus;
        }
    }

}