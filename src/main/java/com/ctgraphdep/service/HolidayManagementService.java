package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class HolidayManagementService {
    private final UserService userService;
    private final DataAccessService dataAccessService;
    private final ReentrantLock holidayLock = new ReentrantLock();

    public HolidayManagementService(UserService userService, DataAccessService dataAccessService) {
        this.userService = userService;
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ============= Admin Operations =============

    /**
     * Loads the list of holiday entries from user data.
     */
    public List<PaidHolidayEntryDTO> loadHolidayList() {
        try {
            List<PaidHolidayEntryDTO> entries = dataAccessService.getUserHolidayEntries();
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading holiday list: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Saves the entire holiday list by updating individual user files.
     * Used only by admin to batch update holiday days.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void saveHolidayList(List<PaidHolidayEntryDTO> entries) {
        holidayLock.lock();
        try {
            // Process each entry separately to update the corresponding user
            for (PaidHolidayEntryDTO entry : entries) {
                try {
                    // Admin directly writes to network, so we use admin version
                    dataAccessService.updateUserHolidayDaysAdmin(entry.getUsername(), entry.getUserId(), entry.getPaidHolidayDays());
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error updating holiday days for user %s: %s", entry.getUsername(), e.getMessage()));
                }
            }

            LoggerUtil.info(this.getClass(), String.format("Saved holiday list with %d entries", entries.size()));
        } finally {
            holidayLock.unlock();
        }
    }

    /**
     * Updates a user's holiday days balance
     * This method will use the admin-specific update when called from an admin context
     */
    public void updateUserHolidayDays(Integer userId, Integer holidayDays) {
        try {
            // Get the user
            Optional<User> userOpt = userService.getUserById(userId);
            if (userOpt.isEmpty()) {
                throw new IllegalArgumentException("User not found with ID: " + userId);
            }

            User user = userOpt.get();
            String username = user.getUsername();

            // Determine if called from admin context
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean isAdminContext = !currentUsername.equals(username) &&
                    userService.getUserByUsername(currentUsername)
                            .map(User::isAdmin)
                            .orElse(false);

            // Use the appropriate method based on context
            if (isAdminContext) {
                // Admin update - network only
                dataAccessService.updateUserHolidayDaysAdmin(username, userId, holidayDays);
            } else {
                // User update - local with network sync
                dataAccessService.updateUserHolidayDays(username, userId, holidayDays);
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Updated holiday days for user %s to %d", username, holidayDays));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error updating holiday days for user %d: %s", userId, e.getMessage()));
            throw new RuntimeException("Failed to update holiday days", e);
        }
    }

    /**
     * Creates or updates the holiday list with all users. Used only by admin.
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
     * Initializes holiday entry for a new user. Used only by admin.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void initializeUserHolidays(User user, Integer initialDays) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntryDTO> entries = loadHolidayList();
            // Check if entry already exists
            boolean exists = entries.stream().anyMatch(entry -> entry.getUserId().equals(user.getUserId()));

            if (!exists) {
                PaidHolidayEntryDTO newEntry = PaidHolidayEntryDTO.fromUser(user);
                newEntry.setPaidHolidayDays(initialDays);
                entries.add(newEntry);
                saveHolidayList(entries);
                LoggerUtil.info(this.getClass(), String.format("Initialized holiday entry for user %s with %d days", user.getUsername(), initialDays));
            }
        } finally {
            holidayLock.unlock();
        }
    }

    /**
     * Restores one holiday day for a user. Used only by admin.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void restoreHolidayDay(Integer userId) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntryDTO> entries = loadHolidayList();


            Optional<PaidHolidayEntryDTO> userEntry = entries.stream().filter(entry -> entry.getUserId().equals(userId)).findFirst();

            if (userEntry.isPresent()) {
                PaidHolidayEntryDTO entry = userEntry.get();
                entry.setPaidHolidayDays(entry.getPaidHolidayDays() + 1);
                saveHolidayList(entries);
                LoggerUtil.info(this.getClass(), String.format("Restored holiday day for user %d. New balance: %d", userId, entry.getPaidHolidayDays()));
            }
        } finally {
            holidayLock.unlock();
        }
    }

    // ============= User Operations =============

    /**
     * Gets the remaining holiday days for a user.
     */
    @PreAuthorize("#username == authentication.name or hasRole('ADMIN')")
    public int getRemainingHolidayDays(String username, Integer userId) {
        return dataAccessService.getUserHolidayDays(username, userId);
    }

    /**
     * Gets the remaining holiday days for a user by ID (without username check).
     * Used internally by services.
     */
    public int getRemainingHolidayDays(Integer userId) {
        holidayLock.lock();
        try {
            return loadHolidayList().stream().filter(entry -> entry.getUserId().equals(userId)).findFirst().map(PaidHolidayEntryDTO::getPaidHolidayDays).orElse(0);
        } finally {
            holidayLock.unlock();
        }
    }

    public int getRemainingHolidayDaysReadOnly(Integer userId) {
        holidayLock.lock();
        try {
            return loadHolidayListReadOnly().stream().filter(entry -> entry.getUserId().equals(userId)).findFirst().map(PaidHolidayEntryDTO::getPaidHolidayDays).orElse(0);
        } finally {
            holidayLock.unlock();
        }
    }

    /**
     * Use holiday days for a time-off request.
     * This can only be called by the user themselves.
     */
    @PreAuthorize("#username == authentication.name")
    public boolean useHolidayDays(String username, Integer userId, Integer daysToUse, String timeOffType) {
        // Only reduce holiday balance for CO type
        if (!"CO".equals(timeOffType)) {
            return true;
        }

        holidayLock.lock();
        try {
            int remainingDays = dataAccessService.getUserHolidayDays(username, userId);

            if (remainingDays >= daysToUse) {
                // Update with reduced days
                dataAccessService.updateUserHolidayDays(username, userId, remainingDays - daysToUse);
                LoggerUtil.info(this.getClass(), String.format("User %s used %d holiday days. Remaining: %d", username, daysToUse, remainingDays - daysToUse));
                return true;
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Insufficient holiday days for user %s. Required: %d, Available: %d", username, daysToUse, remainingDays));
                return false;
            }
        } finally {
            holidayLock.unlock();
        }
    }

    public List<PaidHolidayEntryDTO> loadHolidayListWithoutAdmins() {
        return loadHolidayList().stream()
                .filter(entry -> {
                    Optional<User> userOpt = dataAccessService.getUserById(entry.getUserId());
                    return userOpt.isPresent() && !userOpt.get().isAdmin();
                })
                .toList();
    }


    // ============= Time Off History =============

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
        List<WorkTimeTable> monthEntries = dataAccessService.readNetworkUserWorktimeReadOnly(username, yearMonth.getYear(), yearMonth.getMonthValue());

        // Filter only time off entries (include all types)
        return monthEntries.stream().filter(entry -> entry.getTimeOffType() != null &&
                        (entry.getTimeOffType().equals(WorkCode.TIME_OFF_CODE) || entry.getTimeOffType().equals(WorkCode.MEDICAL_LEAVE_CODE) ||
                                entry.getTimeOffType().equals(WorkCode.NATIONAL_HOLIDAY_CODE))).toList();
    }

    /**
     * Loads the list of holiday entries from the data source.
     * This method can be used by both admin and regular users.
     */
    public List<PaidHolidayEntryDTO> loadHolidayListReadOnly() {
        try {
            List<PaidHolidayEntryDTO> entries = readHolidayEntriesReadOnly();
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading holiday list: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Read holiday entries in read-only mode.
     * This is used by the time off tracker to avoid locking the file.
     */
    public List<PaidHolidayEntryDTO> readHolidayEntriesReadOnly() {
        try {
            return dataAccessService.getUserHolidayEntriesReadOnly();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading holiday entries in read-only mode: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}