package com.ctgraphdep.worktime.service;


import com.ctgraphdep.worktime.commands.*;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Consolidated service for all worktime operations.
 * Replaces: HolidayManagementService, TimeManagementService, TimeOffManagementService, WorktimeManagementService
 * Key Responsibilities:
 * - User worktime operations (start/end time updates)
 * - Time off operations (add/remove CO/CM/SN)
 * - Holiday balance management
 * - Admin worktime operations
 * - File operations coordination
 * - Cache management
 */
@Service
public class WorktimeOperationService {

    private final WorktimeOperationContext context;
    private final WorktimeMergeService worktimeMergeService;

    // Locks for concurrent operations
    private final ReentrantReadWriteLock userLock = new ReentrantReadWriteLock();
    private final ReentrantLock adminLock = new ReentrantLock();
    private final ReentrantLock consolidationLock = new ReentrantLock();

    public WorktimeOperationService(
            WorktimeOperationContext context,
            WorktimeMergeService worktimeMergeService) {
        this.context = context;
        this.worktimeMergeService = worktimeMergeService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // USER OPERATIONS - Time Field Updates
    // ========================================================================

    /**
     * Update start time for a user's worktime entry
     * MODIFIED: Now passes user schedule to command
     */
    @PreAuthorize("#username == authentication.name")
    public OperationResult updateUserStartTime(String username, Integer userId, LocalDate date, String startTime) {
        userLock.writeLock().lock();
        try {
            // GET USER SCHEDULE HERE
            Optional<User> userOpt = context.getUser(username);
            if (userOpt.isEmpty()) {
                return OperationResult.failure("User not found: " + username, OperationResult.OperationType.UPDATE_START_TIME);
            }

            User user = userOpt.get();
            int userScheduleHours = user.getSchedule(); // e.g., 8 hours

            LoggerUtil.debug(this.getClass(), String.format(
                    "User %s schedule: %d hours", username, userScheduleHours));

            return new UpdateStartTimeCommand(context, username, userId, date, startTime, userScheduleHours).execute();
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * Update end time for a user's worktime entry
     * MODIFIED: Now passes user schedule to command
     */
    @PreAuthorize("#username == authentication.name")
    public OperationResult updateUserEndTime(String username, Integer userId, LocalDate date, String endTime) {
        userLock.writeLock().lock();
        try {
            // GET USER SCHEDULE HERE
            Optional<User> userOpt = context.getUser(username);
            if (userOpt.isEmpty()) {
                return OperationResult.failure("User not found: " + username, OperationResult.OperationType.UPDATE_END_TIME);
            }

            User user = userOpt.get();
            int userScheduleHours = user.getSchedule(); // e.g., 8 hours

            LoggerUtil.debug(this.getClass(), String.format(
                    "User %s schedule: %d hours", username, userScheduleHours));

            return new UpdateEndTimeCommand(context, username, userId, date, endTime, userScheduleHours).execute();
        } finally {
            userLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // USER OPERATIONS - Time Off Management
    // ========================================================================

    /**
     * Add time off for user (CO/CM requests)
     */
    @PreAuthorize("#username == authentication.name")
    public OperationResult addUserTimeOff(String username, Integer userId, List<LocalDate> dates, String timeOffType) {
        userLock.writeLock().lock();
        try {
            return new AddTimeOffCommand(context, username, userId, dates, timeOffType).execute();
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * Remove time off for user
     */
    @PreAuthorize("#username == authentication.name")
    public OperationResult removeUserTimeOff(String username, Integer userId, LocalDate date) {
        userLock.writeLock().lock();
        try {
            return new RemoveTimeOffCommand(context, username, userId, date).execute();
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * Transform work entry to time off (atomic operation)
     */
    @PreAuthorize("#username == authentication.name")
    public OperationResult transformWorkToTimeOff(String username, Integer userId, LocalDate date, String timeOffType) {
        userLock.writeLock().lock();
        try {
            return new TransformWorkToTimeOffCommand(context, username, userId, date, timeOffType).execute();
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * Transform time off entry to work
     */
    @PreAuthorize("#username == authentication.name")
    public OperationResult transformTimeOffToWork(String username, Integer userId, LocalDate date,
                                                  LocalDateTime startTime, LocalDateTime endTime) {
        userLock.writeLock().lock();
        try {
            return new TransformTimeOffToWorkCommand(context, username, userId, date, startTime, endTime).execute();
        } finally {
            userLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // ADMIN OPERATIONS
    // ========================================================================

    /**
     * Process admin worktime update (admin sets hours, time off, or removes entries)
     * REFACTORED: Now includes comprehensive holiday balance management
     */
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult processAdminUpdate(Integer userId, LocalDate date, String value) {
        adminLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing admin update: userId=%d, date=%s, value=%s", userId, date, value));

            return new AdminUpdateCommand(context, userId, date, value).execute();

        } finally {
            adminLock.unlock();
        }
    }

    /**
     * Add national holiday for all users
     */
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult addNationalHoliday(LocalDate date) {
        adminLock.lock();
        try {
            return new AddNationalHolidayCommand(context, date).execute();
        } finally {
            adminLock.unlock();
        }
    }

    /**
     * Update holiday balance for user (admin operation)
     * REFACTORED: Now uses UpdateHolidayBalanceCommand instead of HolidayManagementService
     */
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult updateHolidayBalance(Integer userId, Integer newBalance) {
        adminLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Admin updating holiday balance: userId=%d, newBalance=%d", userId, newBalance));

            return new UpdateHolidayBalanceCommand(context, userId, newBalance).execute();

        } finally {
            adminLock.unlock();
        }
    }

    /**
     * Update admin SN entry with work time
     * Allows admin to set work hours for national holidays
     * Business Rules:
     * - Only admin can set SN work time
     * - Work hours become overtime (no regular work on holidays)
     * - Only full hours counted (partial hours discarded)
     * - No lunch break for holiday work
     */
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult updateAdminSNWithWorkTime(Integer userId, LocalDate date, double workHours) {
        adminLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Admin updating SN work time: userId=%d, date=%s, hours=%.2f",
                    userId, date, workHours));

            return new UpdateAdminSNWorkCommand(context, userId, date, workHours).execute();
        } finally {
            adminLock.unlock();
        }
    }

    /**
     * Parse and update SN work time from SN:hours format
     * Helper method for controller to handle "SN:7.5" format strings
     */
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult updateAdminSNFromString(Integer userId, LocalDate date, String snWorkValue) {
        adminLock.lock();
        try {
            // Parse SN:7.5 format
            String[] parts = snWorkValue.split(":");
            if (parts.length != 2 || !parts[0].equals("SN")) {
                return OperationResult.failure(
                        "Invalid SN format. Use SN:hours (e.g., SN:7.5)",
                        "ADMIN_UPDATE_SN_WORK"
                );
            }

            try {
                double workHours = Double.parseDouble(parts[1]);
                return updateAdminSNWithWorkTime(userId, date, workHours);
            } catch (NumberFormatException e) {
                return OperationResult.failure(
                        "Invalid hours format. Use decimal numbers (e.g., SN:7.5)",
                        "ADMIN_UPDATE_SN_WORK"
                );
            }

        } finally {
            adminLock.unlock();
        }
    }

    // ========================================================================
    // BULK OPERATIONS
    // ========================================================================

    /**
     * Consolidate worktime entries between admin and user files
     * REFACTORED: Now uses proper username resolution and comprehensive error handling
     */
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult consolidateWorkTime(int year, int month) {
        consolidationLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Starting worktime consolidation for %d/%d", month, year));

            return new ConsolidateWorkTimeCommand(context, worktimeMergeService, year, month).execute();

        } finally {
            consolidationLock.unlock();
        }
    }

    /**
     * Load user worktime with merged admin changes
     */
    @PreAuthorize("#username == authentication.name or hasRole('ADMIN')")
    public List<WorkTimeTable> loadUserWorktime(String username, int year, int month) {
        userLock.readLock().lock();
        try {
            // Get user ID
            Integer userId = context.getUserId(username);
            if (userId == null) {
                throw new IllegalArgumentException("User not found: " + username);
            }

            // Load user entries
            List<WorkTimeTable> userEntries = context.loadUserWorktime(username, year, month);

            // Load admin entries for this user
            List<WorkTimeTable> adminEntries = loadAdminEntriesForUser(userId, year, month);

            // Merge if admin entries exist
            if (!adminEntries.isEmpty()) {
                List<WorkTimeTable> mergedEntries = worktimeMergeService.mergeEntries(
                        userEntries, adminEntries, userId);

                // Save merged result back
                context.saveUserWorktime(username, mergedEntries, year, month);

                return mergedEntries;
            }

            return userEntries;

        } finally {
            userLock.readLock().unlock();
        }
    }

    // ========================================================================
    // USER OPERATIONS - Temporary Stop Updates
    // ========================================================================

    /**
     * Update temporary stop minutes for a user's worktime entry
     * MODIFIED: Now passes user schedule to command
     */
    @PreAuthorize("#username == authentication.name")
    public OperationResult updateUserTemporaryStop(String username, Integer userId, LocalDate date, Integer tempStopMinutes) {
        userLock.writeLock().lock();
        try {
            // GET USER SCHEDULE HERE
            Optional<User> userOpt = context.getUser(username);
            if (userOpt.isEmpty()) {
                return OperationResult.failure("User not found: " + username, OperationResult.OperationType.UPDATE_TEMPORARY_STOP);
            }

            User user = userOpt.get();
            int userScheduleHours = user.getSchedule(); // e.g., 8 hours

            LoggerUtil.debug(this.getClass(), String.format(
                    "User %s schedule: %d hours", username, userScheduleHours));

            return new AddTemporaryStopCommand(context, username, userId, date, tempStopMinutes, userScheduleHours).execute();
        } finally {
            userLock.writeLock().unlock();
        }
    }

    /**
     * Remove temporary stop from a user's worktime entry
     * MODIFIED: Now passes user schedule to command
     */
    @PreAuthorize("#username == authentication.name")
    public OperationResult removeUserTemporaryStop(String username, Integer userId, LocalDate date) {
        userLock.writeLock().lock();
        try {
            // GET USER SCHEDULE HERE
            Optional<User> userOpt = context.getUser(username);
            if (userOpt.isEmpty()) {
                return OperationResult.failure("User not found: " + username, OperationResult.OperationType.REMOVE_TEMPORARY_STOP);
            }

            User user = userOpt.get();
            int userScheduleHours = user.getSchedule(); // e.g., 8 hours

            LoggerUtil.debug(this.getClass(), String.format(
                    "User %s schedule: %d hours", username, userScheduleHours));

            return new RemoveTemporaryStopCommand(context, username, userId, date, userScheduleHours).execute();
        } finally {
            userLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // QUERY OPERATIONS
    // ========================================================================

    /**
     * Get holiday balance for user
     */
    public Integer getHolidayBalance(String username) {
        // If requesting own balance, use context cache
        if (username.equals(context.getCurrentUsername())) {
            return context.getCurrentHolidayBalance();
        }

        Optional<User> user = context.getUser(username);
        return user.map(User::getPaidHolidayDays).orElse(0);
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Load admin entries for specific user
     */
    private List<WorkTimeTable> loadAdminEntriesForUser(Integer userId, int year, int month) {
        try {
            List<WorkTimeTable> allAdminEntries = context.loadAdminWorktime(year, month);
            return allAdminEntries.stream()
                    .filter(entry -> userId.equals(entry.getUserId()))
                    .filter(entry -> entry.getAdminSync() == SyncStatusMerge.ADMIN_EDITED
                            || entry.getAdminSync() == SyncStatusMerge.ADMIN_BLANK)
                    .toList();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading admin entries for user %d: %s", userId, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // CONVENIENCE METHODS FOR BACKWARD COMPATIBILITY
    // ========================================================================

    /**
     * Check worked days count for a user in a specific month
     * Convenience method that delegates to loadUserWorktime + filtering
     */
    @PreAuthorize("hasRole('ADMIN')")
    public int getWorkedDays(Integer userId, int year, int month) {
        try {
            // Get username from userId
            String username = context.getUsernameFromUserId(userId);
            if (username == null) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Cannot get worked days: user not found for ID %d", userId));
                return 0;
            }

            // Load worktime and count work days
            List<WorkTimeTable> entries = loadUserWorktime(username, year, month);

            long workedDays = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .filter(entry -> entry.getTimeOffType() == null)
                    .filter(entry -> entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0)
                    .count();

            LoggerUtil.debug(this.getClass(), String.format(
                    "User %d worked %d days in %d/%d", userId, workedDays, month, year));

            return (int) workedDays;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting worked days for user %d in %d/%d: %s", userId, year, month, e.getMessage()));
            return 0;
        }
    }

    /**
     * Get viewable entries for admin display (filtering out USER_IN_PROCESS entries)
     * Convenience method for admin controllers
     */
    @PreAuthorize("hasRole('ADMIN')")
    public List<WorkTimeTable> getViewableEntries(int year, int month) {
        try {
            List<WorkTimeTable> allEntries = context.loadAdminWorktime(year, month);

            // Filter out USER_IN_PROCESS entries for admin display
            List<WorkTimeTable> viewableEntries = allEntries.stream()
                    .filter(entry -> !com.ctgraphdep.enums.SyncStatusMerge.USER_IN_PROCESS.equals(entry.getAdminSync()))
                    .collect(java.util.stream.Collectors.toList());

            LoggerUtil.debug(this.getClass(), String.format(
                    "Filtered %d admin entries to %d viewable entries for %d/%d",
                    allEntries.size(), viewableEntries.size(), month, year));

            return viewableEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting viewable entries for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }
}