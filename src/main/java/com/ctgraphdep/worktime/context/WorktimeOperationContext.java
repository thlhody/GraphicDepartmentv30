package com.ctgraphdep.worktime.context;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.data.*;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.service.cache.*;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.UserRegisterService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.accessor.*;
import lombok.Getter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Refactored WorktimeOperationContext - Clean architecture with accessor pattern.
 * Key Responsibilities:
 * 1. Data Accessor Factory - Primary interface for data access
 * 2. User Context Management - Authentication and authorization
 * 3. Cache Management - Unified cache operations
 * 4. Validation Services - Security and business rule validation
 */
@Component
@Getter
public class WorktimeOperationContext {

    // ========================================================================
    // CORE SERVICES - Essential dependencies
    // ========================================================================
    private final TimeValidationService timeValidationService;
    private final MainDefaultUserContextCache mainDefaultUserContextCache;
    private final UserService userService;
    private final AllUsersCacheService allUsersCacheService;

    // Data Services
    private final WorktimeDataService worktimeDataService;
    private final TimeOffDataService timeOffDataService;
    private final RegisterDataService registerDataService;
    private final CheckRegisterDataService checkRegisterDataService;
    private final UserDataService userDataService;
    private final SessionDataService sessionDataService;
    private final UserRegisterService userRegisterService;

    // Cache Services
    private final WorktimeCacheService worktimeCacheService;
    private final TimeOffCacheService timeOffCacheService;
    private final RegisterCacheService registerCacheService;
    private final RegisterCheckCacheService registerCheckCacheService;

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================
    public WorktimeOperationContext(
            // Core services
            TimeValidationService timeValidationService,
            MainDefaultUserContextCache mainDefaultUserContextCache,
            UserService userService,
            AllUsersCacheService allUsersCacheService,

            // Data services
            WorktimeDataService worktimeDataService,
            TimeOffDataService timeOffDataService,
            RegisterDataService registerDataService,
            CheckRegisterDataService checkRegisterDataService,
            UserDataService userDataService,
            SessionDataService sessionDataService,
            UserRegisterService userRegisterService,

            // Cache services
            WorktimeCacheService worktimeCacheService,
            TimeOffCacheService timeOffCacheService,
            RegisterCacheService registerCacheService,
            RegisterCheckCacheService registerCheckCacheService) {

        this.timeValidationService = timeValidationService;
        this.mainDefaultUserContextCache = mainDefaultUserContextCache;
        this.userService = userService;
        this.allUsersCacheService = allUsersCacheService;

        this.worktimeDataService = worktimeDataService;
        this.timeOffDataService = timeOffDataService;
        this.registerDataService = registerDataService;
        this.checkRegisterDataService = checkRegisterDataService;
        this.userDataService = userDataService;
        this.sessionDataService = sessionDataService;
        this.userRegisterService = userRegisterService;

        this.worktimeCacheService = worktimeCacheService;
        this.timeOffCacheService = timeOffCacheService;
        this.registerCacheService = registerCacheService;
        this.registerCheckCacheService = registerCheckCacheService;

        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // PRIMARY INTERFACE - DATA ACCESSOR FACTORY
    // ========================================================================

    // Get appropriate data accessor based on target user.
    // This is the primary interface for all data access operations.
    public WorktimeDataAccessor getDataAccessor(String targetUsername) {
        String currentUsername = getCurrentUsername();
        boolean isAdmin = isCurrentUserAdmin();

        LoggerUtil.debug(this.getClass(), String.format("Creating data accessor: current=%s, target=%s, isAdmin=%s",
                currentUsername, targetUsername, isAdmin));

        // Admin accessing admin consolidated files
        if (isAdmin && SecurityConstants.ADMIN_SIMPLE.equals(targetUsername)) {
            return new AdminOwnDataAccessor(worktimeDataService);
        }

        // User accessing own data (cache with backup)
        if (currentUsername.equals(targetUsername)) {
            return new UserOwnDataAccessor(worktimeCacheService, timeOffCacheService, registerCacheService, registerCheckCacheService, this
            );
        }

        // Viewing other user data (network read-only)
        return new NetworkOnlyAccessor(worktimeDataService, registerDataService, checkRegisterDataService, timeOffDataService);
    }

    // ========================================================================
    // USER CONTEXT MANAGEMENT
    // ========================================================================

    public String getCurrentUsername() {
        User originalUser = mainDefaultUserContextCache.getOriginalUser();
        // Background threads use original user
        if (isBackgroundThread()) {
            return originalUser != null ? originalUser.getUsername() : null;
        }

        // Web threads use SecurityContext with MainDefaultUserContextCache fallback
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Web operation without active SecurityContext - falling back to cache");
            // FIXED: Fall back to MainDefaultUserContextCache instead of returning null
            return originalUser != null ? originalUser.getUsername() : null;
        }
    }

    // Get current user object with context awareness
    public User getCurrentUser() {
        return isBackgroundThread() ? mainDefaultUserContextCache.getOriginalUser() : mainDefaultUserContextCache.getCurrentUser();
    }

    // Check if current user has admin privileges
    public boolean isCurrentUserAdmin() {
        User user = getCurrentUser();
        return user != null && user.isAdmin();
    }

    // Get user by username
    public Optional<User> getUser(String username) {
        return userService.getUserByUsername(username);
    }

    // Get user ID for username (used by accessors)
    public Integer getUserId(String username) {
        return getUser(username).map(User::getUserId).orElse(null);
    }

    // Check if current thread is a background thread
    private boolean isBackgroundThread() {
        String threadName = Thread.currentThread().getName();
        return threadName.startsWith("GeneralTask-") ||
                threadName.startsWith("SessionMonitor-") ||
                threadName.startsWith("backup-event-") ||
                threadName.startsWith("stalled-notification-");
    }

    // ========================================================================
    // SECURITY & VALIDATION
    // ========================================================================

    // Validate user has permission for operation on target user
    public void validateUserPermissions(String targetUsername, String operation) {
        String currentUsername = getCurrentUsername();

        if (currentUsername == null) {
            throw new SecurityException("No authenticated user");
        }

        // Admin can do anything
        if (isCurrentUserAdmin()) {
            return;
        }

        // Users can only modify their own data
        if (!currentUsername.equals(targetUsername)) {
            throw new SecurityException(String.format(
                    "User %s cannot perform %s on %s's data",
                    currentUsername, operation, targetUsername));
        }
    }

    // Validate holiday date
    public void validateHolidayDate(LocalDate date) {
        try {
            var validateCommand = timeValidationService.getValidationFactory().createValidateHolidayDateCommand(date);
            timeValidationService.execute(validateCommand);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Holiday date validation failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // CACHE MANAGEMENT
    // ========================================================================

    // Invalidate time off cache for user and year
    public void invalidateTimeOffCache(String username, int year) {
        try {
            timeOffCacheService.invalidateUserSession(username, year);
            LoggerUtil.debug(this.getClass(), String.format("Invalidated time off cache for %s - %d", username, year));
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error invalidating time off cache for %s - %d: %s", username, year, e.getMessage()));
        }
    }

    // Refresh time off tracker from files
    public void refreshTimeOffTracker(String username, Integer userId, int year) {
        try {
            timeOffCacheService.invalidateUserSession(username, year);
            boolean loaded = timeOffCacheService.loadUserSession(username, userId, year);

            LoggerUtil.debug(this.getClass(), String.format("Refreshed time off tracker for %s - %d: %s", username, year, loaded ? "success" : "failed"));
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error refreshing time off tracker for %s - %d: %s", username, year, e.getMessage()));
        }
    }

    // Invalidate user cache after updates
    public void invalidateUserCache(String username, Integer userId) {
        try {
            invalidateTimeOffCache(username, LocalDate.now().getYear());
            LoggerUtil.debug(this.getClass(), String.format("Invalidated caches for user %s (ID: %d)", username, userId));
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error invalidating cache for user %s: %s", username, e.getMessage()));
        }
    }

    // ========================================================================
    // HOLIDAY BALANCE MANAGEMENT
    // ========================================================================

    // Get current user's holiday balance
    public Integer getCurrentHolidayBalance() {
        return mainDefaultUserContextCache.getCurrentPaidHolidayDays();
    }

    // Update holiday balance for user (admin operation)
    public boolean updateUserHolidayBalance(Integer userId, Integer newBalance) {
        try {
            Optional<User> userOptional = allUsersCacheService.getUserByIdAsUserObject(userId);
            if (userOptional.isEmpty()) {
                LoggerUtil.error(this.getClass(), String.format("User not found with ID: %d", userId));
                return false;
            }

            User user = userOptional.get();
            String username = user.getUsername();
            String currentUsername = getCurrentUsername();
            boolean isAdminContext = !currentUsername.equals(username);

            // Update file
            if (isAdminContext) {
                userDataService.updateUserHolidayDaysAdmin(username, userId, newBalance);
            } else {
                userDataService.updateUserHolidayDaysUser(username, userId, newBalance);
            }

            // Update cache
            user.setPaidHolidayDays(newBalance);
            allUsersCacheService.updateUserInCache(user);

            LoggerUtil.info(this.getClass(), String.format("Updated holiday balance for %s: %d days", username, newBalance));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating holiday balance for user %d: %s", userId, e.getMessage()), e);
            return false;
        }
    }

    // Update current user's holiday balance with validation
    public boolean updateHolidayBalance(int dayChange) {
        try {
            Integer currentBalance = getCurrentHolidayBalance();
            if (currentBalance == null) currentBalance = 0;

            if (dayChange > 0) {
                return mainDefaultUserContextCache.updatePaidHolidayDays(currentBalance + dayChange);
            } else {
                return mainDefaultUserContextCache.reducePaidHolidayDays(Math.abs(dayChange));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating holiday balance by %d: %s", dayChange, e.getMessage()));
            return false;
        }
    }

    // Check if user has sufficient holiday balance
    public boolean hasSufficientHolidayBalance(int daysRequired) {
        Integer currentBalance = getCurrentHolidayBalance();
        return currentBalance != null && currentBalance >= daysRequired;
    }

    // Validate sufficient holiday balance or throw exception
    public void validateSufficientHolidayBalance(int daysRequired, String operation) {
        if (!hasSufficientHolidayBalance(daysRequired)) {
            Integer currentBalance = getCurrentHolidayBalance();
            throw new IllegalArgumentException(String.format("Insufficient vacation balance for %s. Required: %d days, Available: %d days",
                    operation, daysRequired, currentBalance != null ? currentBalance : 0));
        }
    }

    // ========================================================================
    // NATIONAL HOLIDAY OPERATIONS
    // ========================================================================

    // Check if date is marked as national holiday in admin entries
    public boolean isExistingNationalHoliday(LocalDate date) {
        if (date == null) return false;

        try {
            List<WorkTimeTable> adminEntries = loadAdminWorktime(date.getYear(), date.getMonthValue());
            return adminEntries.stream().anyMatch(entry ->
                    date.equals(entry.getWorkDate()) &&
                            WorkCode.NATIONAL_HOLIDAY_CODE.equals(entry.getTimeOffType())
            );
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error checking existing holiday for date %s: %s", date, e.getMessage()));
            return false;
        }
    }

    // Calculate actual vacation days needed, excluding existing national holidays
    public int calculateActualVacationDaysNeeded(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) return 0;

        return (int) dates.stream().filter(date -> !isExistingNationalHoliday(date)).count();
    }

    // Check if vacation day should be processed (not a national holiday)
    public boolean shouldProcessVacationDay(LocalDate date, String operation) {
        boolean shouldProcess = !isExistingNationalHoliday(date);
        LoggerUtil.debug(this.getClass(), String.format("Vacation day processing for %s (%s): %s", date, operation, shouldProcess ? "PROCESS" : "SKIP - national holiday"));
        return shouldProcess;
    }

    // ========================================================================
    // TIME OFF OPERATIONS
    // ========================================================================

    // Add time off requests to tracker (balance-neutral)
    public void addTimeOffRequestsToTracker(String username, Integer userId, List<LocalDate> dates, String timeOffType, int year) {
        timeOffCacheService.addTimeOffToCacheWithoutBalanceUpdate(username, userId, year, dates, timeOffType);
    }

    // Remove time off request from tracker (balance-neutral)
    public boolean removeTimeOffFromTracker(String username, Integer userId, LocalDate date, int year) {
        return timeOffCacheService.removeTimeOffFromCacheWithoutBalanceUpdate(username, userId, year, date);
    }

    public boolean loadUserTrackerSession(String username, Integer userId, int year){
        return timeOffCacheService.loadUserSession(username,userId,year);
    }



    // ========================================================================
    // ADMIN WORKTIME OPERATIONS
    // ========================================================================

    // Load admin worktime entries
    public List<WorkTimeTable> loadAdminWorktime(int year, int month) {
        try {
            String currentRole = getCurrentUser().getRole();
            List<WorkTimeTable> entries;

            if (SecurityConstants.ROLE_ADMIN.equals(currentRole)) {
                entries = worktimeDataService.readAdminLocalReadOnly(year, month);
            } else {
                entries = worktimeDataService.readAdminByUserNetworkReadOnly(year, month);
            }
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error loading admin worktime, using network-only for %d/%d: %s", year, month, e.getMessage()));
            try {
                return worktimeDataService.readAdminByUserNetworkReadOnly(year, month);
            } catch (Exception e2) {
                LoggerUtil.error(this.getClass(), String.format("Error loading admin worktime for %d/%d: %s", year, month, e2.getMessage()));
                return new ArrayList<>();
            }
        }
    }

    // Save admin worktime entries
    public void saveAdminWorktime(List<WorkTimeTable> entries, int year, int month) {
        try {
            worktimeDataService.writeAdminLocalWithSyncAndBackup(entries, year, month);
            LoggerUtil.debug(this.getClass(), String.format("Saved %d admin worktime entries for %d/%d", entries.size(), year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving admin worktime for %d/%d: %s", year, month, e.getMessage()));
            throw new RuntimeException("Failed to save admin worktime entries", e);
        }
    }

    // ========================================================================
    // TEAM OPERATIONS
    // ========================================================================

    // Load user register entries using UserRegisterService
    public ServiceResult<List<RegisterEntry>> loadUserRegisterEntries(String username, Integer userId, int year, int month) {
        try {
            return userRegisterService.loadMonthEntries(username, userId, year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading register entries for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return ServiceResult.failure("Failed to load register entries: " + e.getMessage());
        }
    }

    // Read network session file for user
    public WorkUsersSessionsStates readNetworkSessionFile(String username, Integer userId) {
        try {
            return sessionDataService.readNetworkSessionFileReadOnly(username, userId);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading network session file for %s: %s", username, e.getMessage()));
            return null;
        }
    }

    // Write team members to file
    public void writeTeamMembers(List<TeamMemberDTO> teamMembers, String teamLeadUsername, int year, int month) {
        try {
            registerDataService.writeTeamMembers(teamMembers, teamLeadUsername, year, month);
            LoggerUtil.debug(this.getClass(), String.format("Saved %d team members for %s - %d/%d", teamMembers.size(), teamLeadUsername, year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving team members for %s - %d/%d: %s", teamLeadUsername, year, month, e.getMessage()));
            throw new RuntimeException("Failed to save team members", e);
        }
    }

    // Read team members from file
    public List<TeamMemberDTO> readTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            List<TeamMemberDTO> teamMembers = registerDataService.readTeamMembers(teamLeadUsername, year, month);
            return teamMembers != null ? teamMembers : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading team members for %s - %d/%d: %s", teamLeadUsername, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // USER MANAGEMENT OPERATIONS
    // ========================================================================

    // Get all users from UserService
    public List<User> getAllUsers() {
        try {
            return userService.getAllUsers();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting all users: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Get all non-admin users
    public List<User> getNonAdminUsers() {
        try {
            return userService.getNonAdminUsers(getAllUsers());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting non-admin users: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Get user by ID
    public Optional<User> getUserById(Integer userId) {
        try {
            return userService.getUserById(userId);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting user by ID %d: %s", userId, e.getMessage()));
            return Optional.empty();
        }
    }

}