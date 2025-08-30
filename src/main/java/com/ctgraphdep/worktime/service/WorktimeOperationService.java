package com.ctgraphdep.worktime.service;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.commands.*;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

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

    // Update start time - let command handle everything
    @PreAuthorize("#username == authentication.name")
    public OperationResult updateUserStartTime(String username, Integer userId, LocalDate date, String startTime) {
        userLock.writeLock().lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Executing start time update for %s on %s", username, date));

            // FACTORY METHOD - handles user lookup and setup internally
            UpdateStartTimeCommand command = UpdateStartTimeCommand.forUser(context, username, userId, date, startTime);
            return command.execute();

        } finally {
            userLock.writeLock().unlock();
        }
    }

    // Update end time - let command handle everything
    @PreAuthorize("#username == authentication.name")
    public OperationResult updateUserEndTime(String username, Integer userId, LocalDate date, String endTime) {
        userLock.writeLock().lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Executing end time update for %s on %s", username, date));

            // FACTORY METHOD - handles user lookup and setup internally
            UpdateEndTimeCommand command = UpdateEndTimeCommand.forUser(context, username, userId, date, endTime);
            return command.execute();

        } finally {
            userLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // USER OPERATIONS - Time Off Management
    // ========================================================================

    // Add time off - factory method
    @PreAuthorize("#username == authentication.name")
    public OperationResult addUserTimeOff(String username, Integer userId, List<LocalDate> dates, String timeOffType) {
        userLock.writeLock().lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format("Executing time off addition for %s: %d dates, type=%s", username, dates.size(), timeOffType));

            AddTimeOffCommand command = AddTimeOffCommand.forUser(context, username, userId, dates, timeOffType);
            return command.execute();

        } finally {
            userLock.writeLock().unlock();
        }
    }

    // Remove time off - let command handle everything
    @PreAuthorize("#username == authentication.name")
    public OperationResult removeUserTimeOff(String username, Integer userId, LocalDate date) {
        userLock.writeLock().lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Executing time off removal for %s on %s", username, date));

            // NEW: Use enhanced RemoveCommand instead of
            WorktimeDataAccessor accessor = context.getDataAccessor(username);
            RemoveCommand command = RemoveCommand.forTimeOffRemoval(context, accessor, username, date);
            return command.execute();

        } finally {
            userLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // USER OPERATIONS - Temporary Stop Updates
    // ========================================================================

    // Update temporary stop - factory method
    @PreAuthorize("#username == authentication.name")
    public OperationResult updateUserTemporaryStop(String username, Integer userId, LocalDate date, Integer tempStopMinutes) {
        userLock.writeLock().lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Executing temporary stop update for %s on %s", username, date));

            AddTemporaryStopCommand command = AddTemporaryStopCommand.forUser(context, username, userId, date, tempStopMinutes);
            return command.execute();

        } finally {
            userLock.writeLock().unlock();
        }
    }

    // Remove temporary stop - factory method
    @PreAuthorize("#username == authentication.name")
    public OperationResult removeUserTemporaryStop(String username, Integer userId, LocalDate date) {
        userLock.writeLock().lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Executing temporary stop removal for %s on %s", username, date));

            RemoveTemporaryStopCommand command = RemoveTemporaryStopCommand.forUser(context, username, userId, date);
            return command.execute();

        } finally {
            userLock.writeLock().unlock();
        }
    }

    // ========================================================================
    // ADMIN OPERATIONS
    // ========================================================================

    // Admin add time off - factory method
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult addAdminTimeOff(String targetUsername, Integer targetUserId, List<LocalDate> dates, String timeOffType) {
        adminLock.lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format("Executing admin time off addition for %s: %d dates, type=%s", targetUsername, dates.size(), timeOffType));

            AddTimeOffCommand command = AddTimeOffCommand.forAdmin(context, targetUsername, targetUserId, dates, timeOffType);
            return command.execute();

        } finally {
            adminLock.unlock();
        }
    }

    // Add national holiday - let command handle everything
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult addNationalHoliday(LocalDate date) {
        adminLock.lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format("Executing national holiday addition for %s", date));

            AddNationalHolidayCommand command = AddNationalHolidayCommand.forDate(context, date);
            return command.execute();

        } finally {
            adminLock.unlock();
        }
    }

    // Finalize worktime entries for a period
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult finalizeWorktimePeriod(int year, int month, Integer userId) {
        adminLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Admin finalizing worktime: year=%d, month=%d, userId=%s", year, month, userId));

            // Load all admin worktime entries for the period
            List<WorkTimeTable> adminEntries = context.loadAdminWorktime(year, month);

            if (adminEntries.isEmpty()) {
                return OperationResult.failure("No worktime entries found for " + year + "/" + month,
                        OperationResult.OperationType.FINALIZE_WORKTIME);
            }

            // Perform direct finalization with statistics tracking
            FinalizationStats stats = performDirectFinalization(adminEntries, userId);

            // Save updated entries back to admin worktime file
            context.saveAdminWorktime(adminEntries, year, month);

            // Create success message with statistics
            String message = String.format(
                    "Finalization completed: %d processed, %d finalized, %d already final, %d skipped",
                    stats.totalProcessed,
                    stats.totalFinalized,
                    stats.skippedAlreadyFinal,
                    stats.skippedNotModifiable);

            LoggerUtil.info(this.getClass(), message);

            return OperationResult.success(message, OperationResult.OperationType.FINALIZE_WORKTIME, stats);

        } catch (Exception e) {
            String errorMsg = String.format("Error finalizing worktime for %d/%d: %s", year, month, e.getMessage());
            LoggerUtil.error(this.getClass(), errorMsg, e);
            return OperationResult.failure(errorMsg, OperationResult.OperationType.FINALIZE_WORKTIME);
        } finally {
            adminLock.unlock();
        }
    }

    // Load user time-off tracker for admin operations (network access)
    // Admin can view all users' time-off requests from tracker files
    @PreAuthorize("hasRole('ADMIN')")
    public TimeOffTracker loadUserTimeOffTracker(String username, Integer userId, int year) {
        adminLock.lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Loading time-off tracker for admin: user %s, year %d", username, year));

            // Admin reads time-off tracker from network files
            return context.getTimeOffDataService().readTrackerFromNetworkReadOnly(context.getCurrentUsername(), userId,  year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading time-off tracker for %s - %d: %s", username, year, e.getMessage()), e);
            return null;
        } finally {
            adminLock.unlock();
        }
    }

    // Load user worktime from network files (admin operations)
    // Used when admin needs to see authoritative network data
    @PreAuthorize("hasRole('ADMIN')")
    public List<WorkTimeTable> loadUserWorkTimeFromNetwork(String username, int year, int month) {
        adminLock.lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Loading worktime from network for admin: user %s - %d/%d", username, year, month));

            Integer userId = context.getUserId(username);
            if (userId == null) {
                LoggerUtil.warn(this.getClass(), String.format("User not found: %s", username));
                return new ArrayList<>();
            }

            // Option 1: Check admin consolidated file first (fastest for admin)
            List<WorkTimeTable> adminEntries = context.loadAdminWorktime(year, month);

            // Filter for specific user
            List<WorkTimeTable> userEntriesFromAdmin = adminEntries.stream()
                    .filter(entry -> userId.equals(entry.getUserId()))
                    .collect(Collectors.toList());

            if (!userEntriesFromAdmin.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Found %d entries for user %s in admin consolidated file",
                        userEntriesFromAdmin.size(), username));
                return userEntriesFromAdmin;
            }

            // Option 2: Fallback to user's network file
            LoggerUtil.debug(this.getClass(), String.format(
                    "No admin entries found, reading user %s network file", username));

            List<WorkTimeTable> networkEntries = context.getWorktimeDataService().readUserFromNetworkOnly(context.getCurrentUsername(), year, month);

            return networkEntries != null ? networkEntries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading worktime from network for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        } finally {
            adminLock.unlock();
        }
    }

    // Load admin worktime entries
    @PreAuthorize("hasRole('ADMIN')")
    public List<WorkTimeTable> loadAdminWorktime(int year, int month) {
        return context.loadAdminWorktime(year, month);
    }

    // Save admin worktime entries
    @PreAuthorize("hasRole('ADMIN')")
    public void saveAdminWorktime(List<WorkTimeTable> entries, int year, int month) {
        context.saveAdminWorktime(entries, year, month);
    }

    // Direct finalization logic for WorkTimeTable entries
    private FinalizationStats performDirectFinalization(List<WorkTimeTable> entries, Integer targetUserId) {
        FinalizationStats stats = new FinalizationStats();

        for (WorkTimeTable entry : entries) {
            // Apply user filter if specified
            if (targetUserId != null && !targetUserId.equals(entry.getUserId())) {
                continue; // Skip entries for other users
            }

            stats.totalProcessed++;

            String currentStatus = entry.getAdminSync();

            // Skip if already finalized
            if (MergingStatusConstants.isFinalStatus(currentStatus)) {
                stats.skippedAlreadyFinal++;
                LoggerUtil.debug(this.getClass(), String.format(
                        "Skipping already final entry for user %d on %s: %s",
                        entry.getUserId(), entry.getWorkDate(), currentStatus));
                continue;
            }

            // Check if entry can be modified (not USER_IN_PROCESS)
            if (MergingStatusConstants.USER_IN_PROCESS.equals(currentStatus)) {
                stats.skippedNotModifiable++;
                LoggerUtil.debug(this.getClass(), String.format(
                        "Skipping in-process entry for user %d on %s: %s",
                        entry.getUserId(), entry.getWorkDate(), currentStatus));
                continue;
            }

            // Finalize the entry
            entry.setAdminSync(MergingStatusConstants.ADMIN_FINAL);
            stats.totalFinalized++;

            LoggerUtil.debug(this.getClass(), String.format(
                    "Finalized entry for user %d on %s: %s → %s",
                    entry.getUserId(), entry.getWorkDate(), currentStatus, MergingStatusConstants.ADMIN_FINAL));
        }

        return stats;
    }

    // Simple stats tracking class for finalization results
    private static class FinalizationStats {
        int totalProcessed = 0;
        int totalFinalized = 0;
        int skippedAlreadyFinal = 0;
        int skippedNotModifiable = 0;

        @Override
        public String toString() {
            return String.format("FinalizationStats[processed=%d, finalized=%d, skippedFinal=%d, skippedNotModifiable=%d]",
                    totalProcessed, totalFinalized, skippedAlreadyFinal, skippedNotModifiable);
        }
    }

    // ========================================================================
    // DATA LOADING OPERATIONS (UNCHANGED PUBLIC API)
    // ========================================================================

    // Load user worktime with intelligent routing:
    public List<WorkTimeTable> loadUserWorktime(String username, int year, int month) {
        userLock.readLock().lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Loading worktime for %s - %d/%d with intelligent routing", username, year, month));

            Integer userId = context.getUserId(username);
            if (userId == null) {
                LoggerUtil.warn(this.getClass(), String.format("User not found: %s", username));
                return new ArrayList<>();
            }

            String currentUsername = context.getCurrentUsername();
            boolean isCurrentUser = username.equals(currentUsername);
            boolean isAdmin = context.isCurrentUserAdmin();

            if (isCurrentUser && !isAdmin) {
                // USER ACCESSING OWN DATA: Use cache service (fast session)
                LoggerUtil.debug(this.getClass(), String.format(
                        "User %s accessing own data - using CACHE service", username));

                return context.getWorktimeCacheService().getMonthEntriesWithFallback(
                        username, userId, year, month);

            } else if (isAdmin) {
                // ADMIN ACCESSING ANY DATA: Use network/admin consolidated files
                LoggerUtil.debug(this.getClass(), String.format(
                        "Admin accessing %s data - using NETWORK/ADMIN files", username));

                return loadUserWorkTimeFromNetwork(username, year, month);

            } else {
                // UNAUTHORIZED ACCESS: Fall back to old merge logic for safety
                LoggerUtil.warn(this.getClass(), String.format("Non-admin user %s accessing %s data - using merge fallback", currentUsername, username));

                return loadUserWorktimeWithMergeFallback(username, userId, year, month);
            }

        } finally {
            userLock.readLock().unlock();
        }
    }

    // Fallback merge logic for edge cases (preserved from original)
    private List<WorkTimeTable> loadUserWorktimeWithMergeFallback(String username, Integer userId, int year, int month) {
        try {
            // Use original merge logic as fallback
            WorktimeDataAccessor userAccessor = context.getDataAccessor(username);
            List<WorkTimeTable> userEntries = userAccessor.readWorktime(username, year, month);
            if (userEntries == null) {
                userEntries = new ArrayList<>();
            }

            // Load admin entries for this user
            WorktimeDataAccessor adminAccessor = context.getDataAccessor(SecurityConstants.ADMIN_SIMPLE);
            List<WorkTimeTable> allAdminEntries = adminAccessor.readWorktime(SecurityConstants.ADMIN_SIMPLE, year, month);

            // Filter admin entries for this specific user
            List<WorkTimeTable> adminEntries = allAdminEntries != null ?
                    allAdminEntries.stream()
                            .filter(entry -> userId.equals(entry.getUserId()))
                            .collect(Collectors.toList()) :
                    new ArrayList<>();

            // Merge if admin entries exist for this user
            if (!adminEntries.isEmpty()) {
                List<WorkTimeTable> mergedEntries = worktimeMergeService.mergeEntries(userEntries, adminEntries, userId);

                // Save merged result back using user accessor
                try {
                    userAccessor.writeWorktimeWithStatus(username, mergedEntries, year, month, getCurrentUserRole());
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Saved merged worktime for %s - %d/%d: %d entries", username, year, month, mergedEntries.size()));
                } catch (UnsupportedOperationException e) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Cannot save merged worktime for %s (read-only accessor): %s", username, e.getMessage()));
                }

                return mergedEntries;
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "Loaded user worktime for %s - %d/%d: %d entries (no admin entries to merge)",
                    username, year, month, userEntries.size()));

            return userEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error in merge fallback for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // CONSOLIDATION OPERATIONS
    // ========================================================================

    // Consolidate worktime with proper status handling
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult consolidateWorktime(int year, int month) {
        consolidationLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting worktime consolidation for %d/%d", year, month));

            ConsolidateWorkTimeCommand command = ConsolidateWorkTimeCommand.forPeriod(context, worktimeMergeService, year, month);
            OperationResult result = command.execute();

            LoggerUtil.info(this.getClass(), String.format("Worktime consolidation completed for %d/%d: %s",
                    year, month, result.isSuccess() ? "SUCCESS" : "FAILED"));

            return result;
        } finally {
            consolidationLock.unlock();
        }
    }

    // ========================================================================
    // HELPER METHODS - New status management logic
    // ========================================================================

    // Get current user's role for status determination
    private String getCurrentUserRole() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals(SecurityConstants.SPRING_ROLE_ADMIN));

            if (isAdmin) return SecurityConstants.ROLE_ADMIN;
        }
        return SecurityConstants.ROLE_USER;
    }

    // Update holiday balance for user (admin operation)
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult updateHolidayBalance(Integer userId, Integer newBalance) {
        adminLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format("User updating holiday balance: userId=%d, newBalance=%d", userId, newBalance));

            UpdateHolidayBalanceCommand command = UpdateHolidayBalanceCommand.forUser(context, userId, newBalance);
            return command.execute();

        } finally {
            adminLock.unlock();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public int getWorkedDays(Integer userId, int year, int month) {
        try {
            // Get user information
            Optional<User> userOpt = context.getUserById(userId);
            if (userOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Cannot get worked days: user not found for ID %d", userId));
                return 0;
            }

            User user = userOpt.get();
            String username = user.getUsername();

            // Load worktime using accessor and count work days
            List<WorkTimeTable> entries = loadUserWorktime(username, year, month);

            long workedDays = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .filter(entry -> entry.getTimeOffType() == null)
                    .filter(entry -> entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0)
                    .count();

            LoggerUtil.debug(this.getClass(), String.format("User %d (%s) worked %d days in %d/%d", userId, username, workedDays, month, year));

            return (int) workedDays;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting worked days for user %d in %d/%d: %s", userId, year, month, e.getMessage()));
            return 0;
        }
    }

    // Get viewable entries for admin display (filtering out USER_IN_PROCESS entries)
    @PreAuthorize("hasRole('ADMIN')")
    public List<WorkTimeTable> getViewableEntries(int year, int month) {
        try {
            List<WorkTimeTable> allEntries = context.loadAdminWorktime(year, month);

            // Filter out USER_IN_PROCESS entries for admin display
            List<WorkTimeTable> viewableEntries = allEntries.stream()
                    .filter(entry -> !MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync()))
                    .collect(Collectors.toList());

            LoggerUtil.debug(this.getClass(), String.format("Filtered %d admin entries to %d viewable entries for %d/%d",
                    allEntries.size(), viewableEntries.size(), month, year));

            return viewableEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting viewable entries for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Handle regular admin updates
    @PreAuthorize("hasRole('ADMIN')")
    public OperationResult processAdminUpdate(Integer userId, LocalDate date, String value) {
        adminLock.lock();
        try {
            LoggerUtil.debug(this.getClass(), String.format("Executing admin regular update for user %d on %s", userId, date));

            AdminUpdateCommand command = AdminUpdateCommand.forUpdate(context, userId, date, value);
            return command.execute();

        } finally {
            adminLock.unlock();
        }
    }

    // Get holiday balance for user
    public Integer getHolidayBalance(String username) {
        // If requesting own balance, use context cache
        if (username.equals(context.getCurrentUsername())) {
            return context.getCurrentHolidayBalance();
        }

        Optional<User> user = context.getUser(username);
        return user.map(User::getPaidHolidayDays).orElse(0);
    }
}