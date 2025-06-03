package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.data.UserDataService;
import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.security.UserContextService;
import com.ctgraphdep.service.cache.StatusCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * REFACTORED HolidayManagementService using StatusCacheService + UserDataService.
 * Holiday data is stored in User objects (paidHolidayDays field).
 * Key Changes:
 * - All reads from StatusCacheService (cache-based)
 * - Holiday updates via User object modifications + appropriate write pattern
 * - Admin operations: Direct network writes + cache sync
 * - User operations: Local → network sync + cache sync
 */
@Service
public class HolidayManagementService {
    private final UserDataService userDataService;           // NEW - User file operations
    private final StatusCacheService statusCacheService;     // NEW - Cache operations
    private final UserContextService userContextService;     // NEW - Current user context
    private final WorktimeDataService worktimeDataService;
    private final ReentrantLock holidayLock = new ReentrantLock();

    public HolidayManagementService(UserDataService userDataService, StatusCacheService statusCacheService,
                                    UserContextService userContextService, WorktimeDataService worktimeDataService) {

        this.userDataService = userDataService;
        this.statusCacheService = statusCacheService;
        this.userContextService = userContextService;
        this.worktimeDataService = worktimeDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ============= Admin Operations =============

    /**
     * ENHANCED: Load holiday list with comprehensive debug logging
     */
    public List<PaidHolidayEntryDTO> loadHolidayList() {
        try {
            LoggerUtil.info(this.getClass(), "=== LOADING HOLIDAY LIST DEBUG ===");

            // Step 1: Get all users from cache
            List<User> users = statusCacheService.getAllUsersAsUserObjects();
            LoggerUtil.info(this.getClass(), String.format("Step 1: Got %d users from cache", users.size()));

            // Step 2: Debug each user's data
            for (User user : users) {
                if (!user.isAdmin()) {
                    LoggerUtil.info(this.getClass(), String.format(
                            "🔍 USER DEBUG: %s (ID: %d) - paidHolidayDays: %s, role: %s",
                            user.getUsername(),
                            user.getUserId(),
                            user.getPaidHolidayDays(),
                            user.getRole()));
                }
            }

            // Step 3: Create DTOs and debug them
            List<PaidHolidayEntryDTO> entries = users.stream()
                    .filter(user -> !user.isAdmin())
                    .map(user -> {
                        PaidHolidayEntryDTO dto = PaidHolidayEntryDTO.fromUser(user);
                        LoggerUtil.info(this.getClass(), String.format(
                                "🔍 DTO DEBUG: %s -> DTO paidHolidayDays: %d",
                                user.getUsername(), dto.getPaidHolidayDays()));
                        return dto;
                    })
                    .collect(Collectors.toList());

            LoggerUtil.info(this.getClass(), String.format("Step 3: Created %d holiday entries", entries.size()));

            // Step 4: Final verification
            LoggerUtil.info(this.getClass(), "=== FINAL HOLIDAY ENTRIES ===");
            for (PaidHolidayEntryDTO entry : entries) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Final Entry: %s (ID: %d) = %d days",
                        entry.getUsername(), entry.getUserId(), entry.getPaidHolidayDays()));
            }

            return entries;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading holiday list from cache: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * REFACTORED: Save holiday list using dedicated admin holiday methods
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void saveHolidayList(List<PaidHolidayEntryDTO> entries) {
        holidayLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format("Admin saving holiday list with %d entries", entries.size()));

            // Process each entry separately using dedicated admin method
            for (PaidHolidayEntryDTO entry : entries) {
                try {
                    // Use dedicated admin holiday method + cache sync
                    userDataService.updateUserHolidayDaysAdmin(entry.getUsername(), entry.getUserId(), entry.getPaidHolidayDays());

                    // Update cache
                    Optional<User> userOptional = statusCacheService.getUserAsUserObject(entry.getUsername());
                    if (userOptional.isPresent()) {
                        User user = userOptional.get();
                        user.setPaidHolidayDays(entry.getPaidHolidayDays());
                        statusCacheService.updateUserInCache(user);
                    }

                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error updating holiday days for user %s: %s", entry.getUsername(), e.getMessage()));
                }
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully saved holiday list with %d entries", entries.size()));
        } finally {
            holidayLock.unlock();
        }
    }

    /**
     * ENHANCED: Update user holiday days with debug logging
     */
    public void updateUserHolidayDays(Integer userId, Integer holidayDays) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "=== UPDATING HOLIDAY DAYS DEBUG: userId=%d, newDays=%d ===", userId, holidayDays));

            // Step 1: Get user from cache
            Optional<User> userOptional = statusCacheService.getUserByIdAsUserObject(userId);
            if (userOptional.isEmpty()) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }

            User user = userOptional.get();
            String username = user.getUsername();

            LoggerUtil.info(this.getClass(), String.format(
                    "Step 1: Found user %s, current holidayDays: %s", username, user.getPaidHolidayDays()));

            // Step 2: Determine context
            String currentUsername = userContextService.getCurrentUsername();
            boolean isAdminContext = !currentUsername.equals(username);

            LoggerUtil.info(this.getClass(), String.format(
                    "Step 2: Current user: %s, target user: %s, isAdmin context: %s",
                    currentUsername, username, isAdminContext));

            // Step 3: Update file
            if (isAdminContext) {
                LoggerUtil.info(this.getClass(), "Step 3: Using admin update method");
                userDataService.updateUserHolidayDaysAdmin(username, userId, holidayDays);
            } else {
                LoggerUtil.info(this.getClass(), "Step 3: Using user update method");
                userDataService.updateUserHolidayDaysUser(username, userId, holidayDays);
            }

            // Step 4: Update cache
            user.setPaidHolidayDays(holidayDays);
            statusCacheService.updateUserInCache(user);

            LoggerUtil.info(this.getClass(), String.format(
                    "Step 4: Updated cache for user %s with %d days", username, holidayDays));

            // Step 5: Verify cache update
            Optional<User> verifyUser = statusCacheService.getUserByIdAsUserObject(userId);
            verifyUser.ifPresent(value -> LoggerUtil.info(this.getClass(), String.format(
                    "Step 5: Cache verification - user %s now has %s days",
                    username, value.getPaidHolidayDays())));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating holiday days for user %d: %s", userId, e.getMessage()), e);
            throw new RuntimeException("Failed to update holiday days", e);
        }
    }

    /**
     * REFACTORED: Admin-specific holiday days update using dedicated method
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void updateUserHolidayDaysAdmin(Integer userId, Integer holidayDays) {
        holidayLock.lock();
        try {
            // Get user from cache
            Optional<User> userOptional = statusCacheService.getUserByIdAsUserObject(userId);
            if (userOptional.isEmpty()) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }

            User user = userOptional.get();
            String username = user.getUsername();

            // Use dedicated admin holiday method
            userDataService.updateUserHolidayDaysAdmin(username, userId, holidayDays);

            // Update cache with new holiday days
            user.setPaidHolidayDays(holidayDays);
            statusCacheService.updateUserInCache(user);

        } finally {
            holidayLock.unlock();
        }
    }

    /**
     * REFACTORED: Create or update holiday list for all users
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void createOrUpdateHolidayList(List<User> users) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntryDTO> currentEntries = loadHolidayList();

            // Create entries for new users
            List<PaidHolidayEntryDTO> newEntries = users.stream().filter(user -> !user.isAdmin()).filter(user -> currentEntries.stream()
                            .noneMatch(entry -> entry.getUserId().equals(user.getUserId()))).map(PaidHolidayEntryDTO::fromUser).toList();

            if (!newEntries.isEmpty()) {
                currentEntries.addAll(newEntries);
                saveHolidayList(currentEntries);
                LoggerUtil.info(this.getClass(), String.format("Added %d new holiday entries", newEntries.size()));
            }
        } finally {
            holidayLock.unlock();
        }
    }

    /**
     * REFACTORED: Initialize holiday entry for new user using dedicated admin method
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void initializeUserHolidays(User user, Integer initialDays) {
        holidayLock.lock();
        try {
            // Check if user already has holiday days set
            Optional<User> existingUser = statusCacheService.getUserAsUserObject(user.getUsername());

            if (existingUser.isPresent() && existingUser.get().getPaidHolidayDays() != null) {
                LoggerUtil.debug(this.getClass(), String.format("User %s already has holiday days initialized", user.getUsername()));
                return;
            }

            // Use dedicated admin holiday method
            userDataService.updateUserHolidayDaysAdmin(user.getUsername(), user.getUserId(), initialDays);

            // Update cache
            user.setPaidHolidayDays(initialDays);
            statusCacheService.updateUserInCache(user);

            LoggerUtil.info(this.getClass(), String.format("Initialized holiday entry for user %s with %d days", user.getUsername(), initialDays));
        } finally {
            holidayLock.unlock();
        }
    }

    /**
     * REFACTORED: Restore one holiday day for user using dedicated admin method
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void restoreHolidayDay(Integer userId) {
        holidayLock.lock();
        try {
            // Get current holiday days from cache
            Optional<User> userOptional = statusCacheService.getUserByIdAsUserObject(userId);
            if (userOptional.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("Cannot restore holiday day: user ID %d not found", userId));
                return;
            }

            User user = userOptional.get();
            Integer currentDays = user.getPaidHolidayDays();
            if (currentDays == null) {
                currentDays = 0;
            }

            // Calculate new days
            int newDays = currentDays + 1;

            // Use dedicated admin holiday method
            userDataService.updateUserHolidayDaysAdmin(user.getUsername(), userId, newDays);

            // Update cache
            user.setPaidHolidayDays(newDays);
            statusCacheService.updateUserInCache(user);

            LoggerUtil.info(this.getClass(), String.format("Restored holiday day for user %s (ID: %d). New balance: %d", user.getUsername(), userId, newDays));

        } finally {
            holidayLock.unlock();
        }
    }

    // ============= User Operations =============

    /**
     * REFACTORED: Get remaining holiday days from cache
     */
    @PreAuthorize("#username == authentication.name or hasRole('ADMIN')")
    public int getRemainingHolidayDays(String username, Integer userId) {
        Optional<User> userOptional = statusCacheService.getUserAsUserObject(username);
        if (userOptional.isPresent()) {
            Integer holidayDays = userOptional.get().getPaidHolidayDays();
            return holidayDays != null ? holidayDays : 0;
        }

        LoggerUtil.warn(this.getClass(), String.format("User not found in cache for holiday days lookup: %s", username));
        return 0;
    }

    /**
     * REFACTORED: Get remaining holiday days by user ID from cache
     */
    public int getRemainingHolidayDays(Integer userId) {
        Optional<User> userOptional = statusCacheService.getUserByIdAsUserObject(userId);
        if (userOptional.isPresent()) {
            Integer holidayDays = userOptional.get().getPaidHolidayDays();
            return holidayDays != null ? holidayDays : 0;
        }

        LoggerUtil.warn(this.getClass(), String.format("User not found in cache for holiday days lookup by ID: %d", userId));
        return 0;
    }

    /**
     * REFACTORED: Read-only version using cache
     */
    public int getRemainingHolidayDaysReadOnly(Integer userId) {
        // Same as above since cache is already read-only for this operation
        return getRemainingHolidayDays(userId);
    }

    /**
     * REFACTORED: Use holiday days for time-off request using dedicated user method
     */
    @PreAuthorize("#username == authentication.name")
    public boolean useHolidayDays(String username, Integer userId, Integer daysToUse, String timeOffType) {
        // Only reduce holiday balance for CO type
        if (!"CO".equals(timeOffType)) {
            return true;
        }

        holidayLock.lock();
        try {
            // Get current holiday days from cache
            Optional<User> userOptional = statusCacheService.getUserAsUserObject(username);
            if (userOptional.isEmpty()) {
                LoggerUtil.error(this.getClass(), String.format("User not found for holiday days usage: %s", username));
                return false;
            }

            User user = userOptional.get();
            Integer currentDays = user.getPaidHolidayDays();
            if (currentDays == null) {
                currentDays = 0;
            }

            if (currentDays >= daysToUse) {
                // Calculate new days
                int newDays = currentDays - daysToUse;

                // Use dedicated user holiday method
                userDataService.updateUserHolidayDaysUser(username, userId, newDays);

                // Update cache
                user.setPaidHolidayDays(newDays);
                statusCacheService.updateUserInCache(user);

                LoggerUtil.info(this.getClass(), String.format("User %s used %d holiday days. Remaining: %d", username, daysToUse, newDays));
                return true;
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Insufficient holiday days for user %s. Required: %d, Available: %d", username, daysToUse, currentDays));
                return false;
            }
        } finally {
            holidayLock.unlock();
        }
    }

    // ============= Time Off History (UNCHANGED) =============

    /**
     * Get time off history for a user for the last 12 months
     */
    public List<WorkTimeTable> getUserTimeOffHistory(String username) {
        List<WorkTimeTable> allTimeOffs = new ArrayList<>();
        LocalDate now = LocalDate.now();

        // Get files for the last 12 months
        for (int i = 0; i < WorkCode.HISTORY_MONTHS; i++) {
            LocalDate date = now.minusMonths(i);
            YearMonth yearMonth = YearMonth.from(date);
            try {
                List<WorkTimeTable> monthEntries = loadMonthlyTimeoffs(username, yearMonth);
                allTimeOffs.addAll(monthEntries);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Could not load time offs for %s - %d/%d: %s", username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()));
            }
        }
        return allTimeOffs;
    }

    /**
     * Load time off entries for a specific month
     */
    private List<WorkTimeTable> loadMonthlyTimeoffs(String username, YearMonth yearMonth) {
        List<WorkTimeTable> monthEntries = worktimeDataService.readUserLocalReadOnly(username, yearMonth.getYear(), yearMonth.getMonthValue(), username);

        // Filter only time off entries (include all types)
        return monthEntries.stream().filter(entry -> entry.getTimeOffType() != null && (entry.getTimeOffType().equals(WorkCode.TIME_OFF_CODE) ||
                entry.getTimeOffType().equals(WorkCode.MEDICAL_LEAVE_CODE) || entry.getTimeOffType().equals(WorkCode.NATIONAL_HOLIDAY_CODE))).toList();
    }
}