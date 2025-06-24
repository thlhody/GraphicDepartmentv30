package com.ctgraphdep.worktime.context;

import com.ctgraphdep.fileOperations.data.CheckRegisterDataService;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.fileOperations.data.SessionDataService;
import com.ctgraphdep.fileOperations.data.TimeOffDataService;
import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.service.cache.MainDefaultUserContextCache;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.UserRegisterService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import lombok.Getter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * ENHANCED Context providing unified access to all services needed for worktime operations.
 * NEW ADDITIONS for Team Operations:
 * - UserRegisterService for register operations
 * - SessionDataService for session operations
 * - Team-specific helper methods
 */
@Component
public class WorktimeOperationContext {

    private final WorktimeDataService worktimeDataService;
    private final MainDefaultUserContextCache mainDefaultUserContextCache;
    private final TimeOffDataService timeOffDataService;
    private final TimeOffCacheService timeOffCacheService;
    private final UserService userService;
    @Getter
    private final TimeValidationService timeValidationService;

    // NEW: Additional services for holiday management
    private final UserDataService userDataService;
    private final AllUsersCacheService allUsersCacheService;

    // NEW: Additional services for status commands
    private final RegisterDataService registerDataService;
    private final CheckRegisterDataService checkRegisterDataService;

    // NEW: Additional services for team operations
    private final UserRegisterService userRegisterService;
    private final SessionDataService sessionDataService;

    public WorktimeOperationContext(
            WorktimeDataService worktimeDataService,
            MainDefaultUserContextCache mainDefaultUserContextCache,
            TimeOffDataService timeOffDataService,
            TimeOffCacheService timeOffCacheService,
            UserService userService,
            TimeValidationService timeValidationService,
            UserDataService userDataService,
            AllUsersCacheService allUsersCacheService,
            RegisterDataService registerDataService,
            CheckRegisterDataService checkRegisterDataService,
            UserRegisterService userRegisterService,
            SessionDataService sessionDataService) {
        this.worktimeDataService = worktimeDataService;
        this.mainDefaultUserContextCache = mainDefaultUserContextCache;
        this.timeOffDataService = timeOffDataService;
        this.timeOffCacheService = timeOffCacheService;
        this.userService = userService;
        this.timeValidationService = timeValidationService;
        this.userDataService = userDataService;
        this.allUsersCacheService = allUsersCacheService;
        this.registerDataService = registerDataService;
        this.checkRegisterDataService = checkRegisterDataService;
        this.userRegisterService = userRegisterService;
        this.sessionDataService = sessionDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // EXISTING USER CONTEXT OPERATIONS (UNCHANGED)
    // ========================================================================

    /**
     * ENHANCED: Context-aware user access
     * - Web operations: Use SecurityContext (require active login)
     * - Background operations: Use original user (ignore admin elevation)
     */
    public String getCurrentUsername() {
        String threadName = Thread.currentThread().getName();

        // Background threads should ALWAYS use original user (ignore elevation)
        if (isBackgroundThread(threadName)) {
            User originalUser = mainDefaultUserContextCache.getOriginalUser();
            return originalUser != null ? originalUser.getUsername() : null;
        }

        // Web request threads should use SecurityContext (enforce active session)
        try {
            String securityUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            if (securityUsername != null) {
                return securityUsername;
            }
        } catch (Exception e) {
            LoggerUtil.debug(this.getClass(), "SecurityContext not available for web operation");
        }

        // Fallback for web threads when SecurityContext fails
        LoggerUtil.warn(this.getClass(), "Web operation attempted without active SecurityContext");
        return null; // Should fail gracefully for security
    }

    public User getCurrentUser() {
        String threadName = Thread.currentThread().getName();

        // Background threads should ALWAYS use original user (ignore elevation)
        if (isBackgroundThread(threadName)) {
            return mainDefaultUserContextCache.getOriginalUser();
        }

        // Web operations can use elevated admin or original user
        return mainDefaultUserContextCache.getCurrentUser();
    }

    private boolean isBackgroundThread(String threadName) {
        return threadName.startsWith("GeneralTask-") ||
                threadName.startsWith("SessionMonitor-") ||
                threadName.startsWith("backup-event-") ||
                threadName.startsWith("stalled-notification-");
    }

    /**
     * Check if current user is admin
     */
    public boolean isCurrentUserAdmin() {
        User user = getCurrentUser();
        return user != null && user.isAdmin();
    }

    /**
     * Get user by username
     */
    public Optional<User> getUser(String username) {
        return userService.getUserByUsername(username);
    }

    /**
     * Get user ID for username
     */
    public Integer getUserId(String username) {
        return getUser(username)
                .map(User::getUserId)
                .orElse(null);
    }

    /**
     * Validate user permissions for operation
     */
    public void validateUserPermissions(String targetUsername, String operation) {
        String currentUsername = getCurrentUsername();

        if (currentUsername == null) {
            throw new SecurityException("No authenticated user");
        }

        // Admin can edit anyone
        if (isCurrentUserAdmin()) {
            return;
        }

        // Users can only edit their own data
        if (!currentUsername.equals(targetUsername)) {
            throw new SecurityException(String.format(
                    "User %s cannot perform %s on %s's data",
                    currentUsername, operation, targetUsername));
        }
    }

    // ========================================================================
    // USER MANAGEMENT OPERATIONS (EXISTING)
    // ========================================================================

    /**
     * Get all users from UserService
     */
    public List<User> getAllUsers() {
        try {
            return userService.getAllUsers();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting all users: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Get all non-admin users from UserService
     */
    public List<User> getNonAdminUsers() {
        try {
            List<User> allUsers = getAllUsers();
            return userService.getNonAdminUsers(allUsers);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting non-admin users: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Get user by ID
     */
    public Optional<User> getUserById(Integer userId) {
        try {
            return userService.getUserById(userId);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting user by ID %d: %s", userId, e.getMessage()));
            return Optional.empty();
        }
    }

    // ========================================================================
    // NEW: TEAM OPERATIONS SUPPORT
    // ========================================================================

    /**
     * Load view-only worktime (replacement for WorktimeManagementService.loadViewOnlyWorktime)
     */
    public List<WorkTimeTable> loadViewOnlyWorktime(String username, int year, int month) {
        try {
            // Use existing worktime data service for read-only access
            String currentUsername = getCurrentUsername();
            List<WorkTimeTable> entries = worktimeDataService.readUserLocalReadOnly(username, year, month, currentUsername);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading view-only worktime for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Load user register entries using UserRegisterService
     */
    public ServiceResult<List<RegisterEntry>> loadUserRegisterEntries(String username, Integer userId, int year, int month) {
        try {
            return userRegisterService.loadMonthEntries(username, userId, year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading register entries for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return ServiceResult.failure("Failed to load register entries: " + e.getMessage());
        }
    }

    /**
     * Read network session file for user
     */
    public WorkUsersSessionsStates readNetworkSessionFile(String username, Integer userId) {
        try {
            return sessionDataService.readNetworkSessionFileReadOnly(username, userId);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading network session file for %s: %s", username, e.getMessage()));
            return null;
        }
    }

    /**
     * Write team members to file
     */
    public void writeTeamMembers(List<TeamMemberDTO> teamMembers, String teamLeadUsername, int year, int month) {
        try {
            registerDataService.writeTeamMembers(teamMembers, teamLeadUsername, year, month);
            LoggerUtil.debug(this.getClass(), String.format(
                    "Saved %d team members for %s - %d/%d", teamMembers.size(), teamLeadUsername, year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error saving team members for %s - %d/%d: %s", teamLeadUsername, year, month, e.getMessage()));
            throw new RuntimeException("Failed to save team members", e);
        }
    }

    /**
     * Read team members from file
     */
    public List<TeamMemberDTO> readTeamMembers(String teamLeadUsername, int year, int month) {
        try {
            List<TeamMemberDTO> teamMembers = registerDataService.readTeamMembers(teamLeadUsername, year, month);
            return teamMembers != null ? teamMembers : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading team members for %s - %d/%d: %s", teamLeadUsername, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // NEW: HOLIDAY BALANCE MANAGEMENT OPERATIONS
    // ========================================================================

    /**
     * Get username from user ID (used by holiday management commands)
     */
    public String getUsernameFromUserId(Integer userId) {
        try {
            return getUserById(userId)
                    .map(User::getUsername)
                    .orElse(null);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting username for user ID %d: %s", userId, e.getMessage()));
            return null;
        }
    }

    /**
     * Update user holiday balance (admin operation)
     * This replaces the HolidayManagementService.updateUserHolidayDays method
     */
    public boolean updateUserHolidayBalance(Integer userId, Integer newBalance) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "=== UPDATING HOLIDAY BALANCE: userId=%d, newBalance=%d ===", userId, newBalance));

            // Step 1: Get user from cache
            Optional<User> userOptional = allUsersCacheService.getUserByIdAsUserObject(userId);
            if (userOptional.isEmpty()) {
                LoggerUtil.error(this.getClass(), String.format("User not found with ID: %d", userId));
                return false;
            }

            User user = userOptional.get();
            String username = user.getUsername();

            LoggerUtil.info(this.getClass(), String.format(
                    "Step 1: Found user %s, current holidayDays: %s", username, user.getPaidHolidayDays()));

            // Step 2: Determine context (admin vs user operation)
            String currentUsername = getCurrentUsername();
            boolean isAdminContext = !currentUsername.equals(username);

            LoggerUtil.info(this.getClass(), String.format(
                    "Step 2: Current user: %s, target user: %s, isAdmin context: %s",
                    currentUsername, username, isAdminContext));

            // Step 3: Update user file using appropriate method
            if (isAdminContext) {
                LoggerUtil.info(this.getClass(), "Step 3: Using admin update method");
                userDataService.updateUserHolidayDaysAdmin(username, userId, newBalance);
            } else {
                LoggerUtil.info(this.getClass(), "Step 3: Using user update method");
                userDataService.updateUserHolidayDaysUser(username, userId, newBalance);
            }

            // Step 4: Update cache
            user.setPaidHolidayDays(newBalance);
            allUsersCacheService.updateUserInCache(user);

            LoggerUtil.info(this.getClass(), String.format(
                    "Step 4: Updated cache for user %s with %d days", username, newBalance));

            // Step 5: Verify cache update
            Optional<User> verifyUser = allUsersCacheService.getUserByIdAsUserObject(userId);
            verifyUser.ifPresent(value -> LoggerUtil.info(this.getClass(), String.format(
                    "Step 5: Cache verification - user %s now has %s days",
                    username, value.getPaidHolidayDays())));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating holiday balance for user %d: %s", userId, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Get user holiday balance by user ID (for validation and side effects tracking)
     */
    public Integer getUserHolidayBalance(Integer userId) {
        try {
            Optional<User> userOptional = allUsersCacheService.getUserByIdAsUserObject(userId);
            if (userOptional.isPresent()) {
                Integer holidayDays = userOptional.get().getPaidHolidayDays();
                return holidayDays != null ? holidayDays : 0;
            }

            LoggerUtil.warn(this.getClass(), String.format(
                    "User not found in cache for holiday balance lookup by ID: %d", userId));
            return 0;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting holiday balance for user %d: %s", userId, e.getMessage()));
            return 0;
        }
    }

    /**
     * Invalidate user cache after holiday balance update
     */
    public void invalidateUserCache(String username, Integer userId) {
        try {
            // Invalidate time off cache for current year
            int currentYear = LocalDate.now().getYear();
            invalidateTimeOffCache(username, currentYear);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Invalidated caches for user %s (ID: %d)", username, userId));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error invalidating cache for user %s: %s", username, e.getMessage()));
        }
    }

    // ========================================================================
    // VALIDATION OPERATIONS (EXISTING)
    // ========================================================================

    /**
     * Validate holiday date using TimeValidationService
     */
    public void validateHolidayDate(LocalDate date) {
        try {
            var validateCommand = timeValidationService.getValidationFactory()
                    .createValidateHolidayDateCommand(date);
            timeValidationService.execute(validateCommand);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error validating holiday date %s: %s", date, e.getMessage()));
            throw new IllegalArgumentException("Holiday date validation failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // WORKTIME FILE OPERATIONS (EXISTING - UNCHANGED)
    // ========================================================================

    /**
     * Load user worktime entries for month
     */
    public List<WorkTimeTable> loadUserWorktime(String username, int year, int month) {
        try {
            String currentUsername = getCurrentUsername();
            List<WorkTimeTable> entries = worktimeDataService.readUserLocalReadOnly(username, year, month, currentUsername);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading worktime for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Save user worktime entries for month
     */
    public void saveUserWorktime(String username, List<WorkTimeTable> entries, int year, int month) {
        try {
            worktimeDataService.writeUserLocalWithSyncAndBackup(username, entries, year, month);
            LoggerUtil.debug(this.getClass(), String.format(
                    "Saved %d worktime entries for %s - %d/%d", entries.size(), username, year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error saving worktime for %s - %d/%d: %s", username, year, month, e.getMessage()));
            throw new RuntimeException("Failed to save worktime entries", e);
        }
    }

    /**
     * Add time off requests to tracker (balance-neutral, for commands)
     */
    public void addTimeOffRequestsToTracker(String username, Integer userId, List<LocalDate> dates, String timeOffType, int year) {
        timeOffCacheService.addTimeOffToCacheWithoutBalanceUpdate(username, userId, year, dates, timeOffType);
    }

    /**
     * Remove time off request from tracker (balance-neutral, for commands)
     */
    public void removeTimeOffFromTracker(String username, Integer userId, LocalDate date, int year) {
        timeOffCacheService.removeTimeOffFromCacheWithoutBalanceUpdate(username, userId, year, date);
    }

    /**
     * Load admin worktime entries for month
     */
    public List<WorkTimeTable> loadAdminWorktime(int year, int month) {
        try {
            List<WorkTimeTable> entries = worktimeDataService.readAdminLocalReadOnly(year, month);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading admin worktime for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Save admin worktime entries for month
     */
    public void saveAdminWorktime(List<WorkTimeTable> entries, int year, int month) {
        try {
            worktimeDataService.writeAdminLocalWithSyncAndBackup(entries, year, month);
            LoggerUtil.debug(this.getClass(), String.format(
                    "Saved %d admin worktime entries for %d/%d", entries.size(), year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error saving admin worktime for %d/%d: %s", year, month, e.getMessage()));
            throw new RuntimeException("Failed to save admin worktime entries", e);
        }
    }

    // ========================================================================
    // NEW: NETWORK-ONLY DATA ACCESS FOR STATUS OPERATIONS
    // ========================================================================

    /**
     * Load worktime data from network files (for status display of other users)
     */
    public List<WorkTimeTable> loadWorktimeFromNetwork(String username, int year, int month) {
        try {
            // Use network-only read method
            List<WorkTimeTable> entries = worktimeDataService.readUserFromNetworkOnly(username, year, month);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading network worktime for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Load time off tracker from network files (for status display of other users)
     */
    public TimeOffTracker loadTimeOffTrackerFromNetwork(String username, Integer userId, int year) {
        try {
            // Use network-only read method
            return timeOffDataService.readTrackerFromNetworkReadOnly(username, userId, year);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading network time off tracker for %s - %d: %s", username, year, e.getMessage()));
            return null;
        }
    }

    /**
     * Load time off tracker from local files (for own data)
     */
    public TimeOffTracker loadTimeOffTrackerFromLocal(String username, Integer userId, int year) {
        try {
            String currentUsername = getCurrentUsername();
            return timeOffDataService.readUserLocalTrackerReadOnly(username, userId, currentUsername, year);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading local time off tracker for %s - %d: %s", username, year, e.getMessage()));
            return null;
        }
    }

    /**
     * Load register entries from network files (for status display of other users)
     */
    public List<RegisterEntry> loadRegisterFromNetwork(String username, Integer userId, int year, int month) {
        try {
            List<RegisterEntry> entries = registerDataService.readUserFromNetworkOnly(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading network register for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Load register entries from local files (for own data)
     */
    public List<RegisterEntry> loadRegisterFromLocal(String username, Integer userId, int year, int month) {
        try {
            String currentUsername = getCurrentUsername();
            List<RegisterEntry> entries = registerDataService.readUserLocalReadOnly(username, userId, currentUsername, year, month);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading local register for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Load check register entries from network files (for status display of other users)
     */
    public List<RegisterCheckEntry> loadCheckRegisterFromNetwork(String username, Integer userId, int year, int month) {
        try {
            List<RegisterCheckEntry> entries = checkRegisterDataService.readUserCheckRegisterFromNetworkOnly(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading network check register for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Load check register entries from local files (for own data)
     */
    public List<RegisterCheckEntry> loadCheckRegisterFromLocal(String username, Integer userId, int year, int month) {
        try {
            List<RegisterCheckEntry> entries = checkRegisterDataService.readUserCheckRegisterLocalReadOnly(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading local check register for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // CURRENT USER HOLIDAY BALANCE OPERATIONS (EXISTING - UNCHANGED)
    // ========================================================================

    /**
     * Get current holiday balance for authenticated user ---- correct
     */
    public Integer getCurrentHolidayBalance() {
        return mainDefaultUserContextCache.getCurrentPaidHolidayDays();
    }

    /**
     * Update holiday balance with validation
     */
    public boolean updateHolidayBalance(int dayChange) {
        try {
            if (dayChange > 0) {
                // Adding days - just update
                Integer currentBalance = getCurrentHolidayBalance();
                if (currentBalance == null) currentBalance = 0;
                return mainDefaultUserContextCache.updatePaidHolidayDays(currentBalance + dayChange);
            } else {
                // Reducing days - use the built-in validation
                return mainDefaultUserContextCache.reducePaidHolidayDays(Math.abs(dayChange));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating holiday balance by %d: %s", dayChange, e.getMessage()));
            return false;
        }
    }

    /**
     * Check if sufficient holiday balance exists for the requested days
     */
    public boolean hasSufficientHolidayBalance(int daysRequired) {
        Integer currentBalance = getCurrentHolidayBalance();
        if (currentBalance == null) {
            LoggerUtil.warn(this.getClass(), "Holiday balance is null, treating as insufficient");
            return false;
        }

        boolean sufficient = currentBalance >= daysRequired;

        LoggerUtil.debug(this.getClass(), String.format(
                "Holiday balance check: required=%d, available=%d, sufficient=%s",
                daysRequired, currentBalance, sufficient));

        return sufficient;
    }

    /**
     * Validate holiday balance and throw exception if insufficient
     */
    public void validateSufficientHolidayBalance(int daysRequired, String operation) {
        if (!hasSufficientHolidayBalance(daysRequired)) {
            Integer currentBalance = getCurrentHolidayBalance();
            throw new IllegalArgumentException(String.format(
                    "Insufficient vacation balance for %s. Required: %d days, Available: %d days",
                    operation, daysRequired, currentBalance != null ? currentBalance : 0));
        }
    }

    /**
     * Get remaining holiday days with null safety
     */
    public int getRemainingHolidayDays() {
        Integer balance = getCurrentHolidayBalance();
        return balance != null ? balance : 0;
    }

    // ========================================================================
    // CACHE OPERATIONS (FIXED)
    // ========================================================================

    /**
     * FIXED: Invalidate time off cache for user and year
     */
    public void invalidateTimeOffCache(String username, int year) {
        try {
            // FIXED: Use the correct method name from TimeOffCacheService
            timeOffCacheService.invalidateUserSession(username, year);
            LoggerUtil.debug(this.getClass(), String.format(
                    "Invalidated time off cache for %s - %d", username, year));
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error invalidating time off cache for %s - %d: %s",
                    username, year, e.getMessage()));
        }
    }

    /**
     * FIXED: Refresh time off tracker from worktime files
     */
    public void refreshTimeOffTracker(String username, Integer userId, int year) {
        try {
            // FIXED: Since refreshTrackerFromWorktime doesn't exist, we'll invalidate and reload
            timeOffCacheService.invalidateUserSession(username, year);

            // Force reload by accessing the session
            boolean loaded = timeOffCacheService.loadUserSession(username, userId, year);

            if (loaded) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Refreshed time off tracker for %s - %d", username, year));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to refresh time off tracker for %s - %d", username, year));
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error refreshing time off tracker for %s - %d: %s",
                    username, year, e.getMessage()));
        }
    }

    // ========================================================================
    // UTILITY METHODS (EXISTING - UNCHANGED)
    // ========================================================================

    /**
     * Find entry in list by date and user ID
     */
    public Optional<WorkTimeTable> findEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.stream()
                .filter(entry -> userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()))
                .findFirst();
    }

    /**
     * Remove entry from list by date and user ID
     */
    public boolean removeEntryByDate(List<WorkTimeTable> entries, Integer userId, LocalDate date) {
        return entries.removeIf(entry ->
                userId.equals(entry.getUserId()) && date.equals(entry.getWorkDate()));
    }

    /**
     * Add or replace entry in list
     */
    public void addOrReplaceEntry(List<WorkTimeTable> entries, WorkTimeTable newEntry) {
        removeEntryByDate(entries, newEntry.getUserId(), newEntry.getWorkDate());
        entries.add(newEntry);

        // Keep list sorted
        entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));
    }

    /**
     * Create file path identifier for logging
     */
    public String createFilePathId(String username, int year, int month) {
        return String.format("%s/%d/%d", username, year, month);
    }

    /**
     * Create cache key identifier
     */
    public String createCacheKey(String username, int year) {
        return String.format("%s-%d", username, year);
    }

    // ========================================================================
    // VALIDATION HELPERS (EXISTING - UNCHANGED)
    // ========================================================================

    /**
     * Validate date is editable by user
     */
    public void validateDateEditable(LocalDate date, String reason) {
        LocalDate today = LocalDate.now();

        if (date.equals(today)) {
            throw new IllegalArgumentException("Cannot edit current day");
        }

        if (reason != null && !reason.trim().isEmpty()) {
            throw new IllegalArgumentException(reason);
        }
    }

    /**
     * Validate time off can be added to date
     */
    public void validateTimeOffDate(LocalDate date) {
        validateDateEditable(date, null);

        // Check for weekend
        if (date.getDayOfWeek().getValue() >= 6) {
            throw new IllegalArgumentException("Cannot add time off on weekends");
        }
    }

    /**
     * Check if operation requires admin privileges
     */
    public void requireAdminPrivileges(String operation) {
        if (!isCurrentUserAdmin()) {
            throw new SecurityException(String.format(
                    "Operation %s requires admin privileges", operation));
        }
    }

    // ========================================================================
    // ADMIN OPERATIONS SUPPORT (EXISTING - UNCHANGED)
    // ========================================================================

    /**
     * Remove entries by date for all users
     */
    public int removeEntriesByDate(List<WorkTimeTable> entries, LocalDate date) {
        int removedCount = 0;
        var iterator = entries.iterator();
        while (iterator.hasNext()) {
            WorkTimeTable entry = iterator.next();
            if (entry.getWorkDate().equals(date)) {
                iterator.remove();
                removedCount++;
            }
        }
        return removedCount;
    }

    /**
     * Add multiple entries to list
     */
    public void addEntries(List<WorkTimeTable> targetList, List<WorkTimeTable> entriesToAdd) {
        targetList.addAll(entriesToAdd);

        // Keep list sorted
        targetList.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));
    }

// ========================================================================
// NATIONAL HOLIDAY CHECKING OPERATIONS
// ========================================================================

    /**
     * Check if a date is already marked as a national holiday (SN) in admin entries
     * @param date The date to check
     * @return true if the date is marked as SN (national holiday) in admin entries
     */
    public boolean isExistingNationalHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }

        try {
            // Load admin entries for this date's month
            List<WorkTimeTable> adminEntries = loadAdminWorktime(date.getYear(), date.getMonthValue());

            // Check if any entry for this date is marked as SN
            boolean isHoliday = adminEntries.stream()
                    .anyMatch(entry -> date.equals(entry.getWorkDate()) &&
                            "SN".equals(entry.getTimeOffType()));

            LoggerUtil.debug(this.getClass(), String.format(
                    "Holiday check for %s: %s", date, isHoliday ? "IS national holiday" : "not holiday"));

            return isHoliday;

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error checking existing holiday for date %s: %s", date, e.getMessage()));
            return false; // If we can't check, assume it's not a holiday
        }
    }

    /**
     * Calculate actual vacation days needed from a list of dates, excluding existing SN days
     * @param dates List of dates to check
     * @return Number of dates that are NOT already national holidays
     */
    public int calculateActualVacationDaysNeeded(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return 0;
        }

        int vacationDaysNeeded = 0;
        int snDaysSkipped = 0;

        for (LocalDate date : dates) {
            if (!isExistingNationalHoliday(date)) {
                vacationDaysNeeded++;
            } else {
                snDaysSkipped++;
                LoggerUtil.debug(this.getClass(), String.format(
                        "Date %s is already a national holiday (SN), not charging vacation day", date));
            }
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Vacation days calculation: %d total dates, %d vacation days needed, %d SN days skipped",
                dates.size(), vacationDaysNeeded, snDaysSkipped));

        return vacationDaysNeeded;
    }

    /**
     * Check if a vacation day should be charged/restored for a specific date
     * @param date The date to check
     * @param operation Description of the operation for logging
     * @return true if vacation day should be processed, false if it's a national holiday
     */
    public boolean shouldProcessVacationDay(LocalDate date, String operation) {
        boolean shouldProcess = !isExistingNationalHoliday(date);

        LoggerUtil.debug(this.getClass(), String.format(
                "Vacation day processing for %s (%s): %s",
                date, operation, shouldProcess ? "PROCESS" : "SKIP - national holiday"));

        return shouldProcess;
    }
}