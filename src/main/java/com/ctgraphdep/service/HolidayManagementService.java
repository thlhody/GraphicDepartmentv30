package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class HolidayManagementService {
    private final DataAccessService dataAccess;
    private final ReentrantLock holidayLock = new ReentrantLock();

    public HolidayManagementService(DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ============= Admin Operations =============

    /**
     * Loads the list of holiday entries from the data source.
     * This method can be used by both admin and regular users.
     */
    public List<PaidHolidayEntryDTO> loadHolidayList() {
        try {
            List<PaidHolidayEntryDTO> entries = dataAccess.readHolidayEntries();
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading holiday list: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Saves the entire holiday list. Used only by admin.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void saveHolidayList(List<PaidHolidayEntryDTO> entries) {
        holidayLock.lock();
        try {
            dataAccess.writeHolidayEntries(entries);
            LoggerUtil.info(this.getClass(), String.format("Saved holiday list with %d entries", entries.size()));
        } finally {
            holidayLock.unlock();
        }
    }

    /**
     * Updates holiday days for a specific user. Used only by admin.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void updateUserHolidayDays(Integer userId, Integer days) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntryDTO> entries = loadHolidayList();

            Optional<PaidHolidayEntryDTO> userEntry = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst();

            if (userEntry.isPresent()) {
                PaidHolidayEntryDTO entry = userEntry.get();
                entry.setPaidHolidayDays(days);
                saveHolidayList(entries);

                LoggerUtil.info(this.getClass(),
                        String.format("Updated holiday days for user %d to %d days",
                                userId, days));
            } else {
                String error = String.format("No holiday entry found for user %d", userId);
                LoggerUtil.error(this.getClass(), error);
                throw new IllegalStateException(error);
            }
        } finally {
            holidayLock.unlock();
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
            List<PaidHolidayEntryDTO> newEntries = users.stream()
                    .filter(user -> !user.isAdmin()) // Exclude admin users
                    .filter(user -> currentEntries.stream()
                            .noneMatch(entry -> entry.getUserId().equals(user.getUserId())))
                    .map(PaidHolidayEntryDTO::fromUser)
                    .toList();

            if (!newEntries.isEmpty()) {
                currentEntries.addAll(newEntries);
                saveHolidayList(currentEntries);

                LoggerUtil.info(this.getClass(),
                        String.format("Added %d new holiday entries", newEntries.size()));
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
            boolean exists = entries.stream()
                    .anyMatch(entry -> entry.getUserId().equals(user.getUserId()));

            if (!exists) {
                PaidHolidayEntryDTO newEntry = PaidHolidayEntryDTO.fromUser(user);
                newEntry.setPaidHolidayDays(initialDays);
                entries.add(newEntry);
                saveHolidayList(entries);

                LoggerUtil.info(this.getClass(),
                        String.format("Initialized holiday entry for user %s with %d days",
                                user.getUsername(), initialDays));
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

            Optional<PaidHolidayEntryDTO> userEntry = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst();

            if (userEntry.isPresent()) {
                PaidHolidayEntryDTO entry = userEntry.get();
                entry.setPaidHolidayDays(entry.getPaidHolidayDays() + 1);
                saveHolidayList(entries);

                LoggerUtil.info(this.getClass(),
                        String.format("Restored holiday day for user %d. New balance: %d",
                                userId, entry.getPaidHolidayDays()));
            }
        } finally {
            holidayLock.unlock();
        }
    }

    // ============= User Operations =============

    /**
     * Gets the remaining holiday days for a user.
     * Can be accessed by both the user themselves and admins.
     */
    @PreAuthorize("#username == authentication.name or hasRole('ADMIN')")
    public int getRemainingHolidayDays(String username, Integer userId) {
        List<PaidHolidayEntryDTO> entries = loadHolidayList();

        return entries.stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .findFirst()
                .map(PaidHolidayEntryDTO::getPaidHolidayDays)
                .orElse(0);
    }

    /**
     * Gets the remaining holiday days for a user by ID (without username check).
     * Used internally by services.
     */
    public int getRemainingHolidayDays(Integer userId) {
        holidayLock.lock();
        try {
            return loadHolidayList().stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst()
                    .map(PaidHolidayEntryDTO::getPaidHolidayDays)
                    .orElse(0);
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
            List<PaidHolidayEntryDTO> entries = loadHolidayList();

            Optional<PaidHolidayEntryDTO> userEntry = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst();

            if (userEntry.isPresent()) {
                PaidHolidayEntryDTO entry = userEntry.get();
                int remainingDays = entry.getPaidHolidayDays();

                if (remainingDays >= daysToUse) {
                    entry.setPaidHolidayDays(remainingDays - daysToUse);
                    saveHolidayList(entries);

                    LoggerUtil.info(this.getClass(),
                            String.format("User %s used %d holiday days. Remaining: %d",
                                    username, daysToUse, entry.getPaidHolidayDays()));
                    return true;
                } else {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Insufficient holiday days for user %s. Required: %d, Available: %d",
                                    username, daysToUse, remainingDays));
                    return false;
                }
            }
            return false;
        } finally {
            holidayLock.unlock();
        }
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
                LoggerUtil.warn(this.getClass(),
                        String.format("Could not load time offs for %s - %d/%d: %s",
                                username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()));
            }
        }
        return allTimeOffs;
    }

    /**
     * Load time off entries for a specific month
     */
    private List<WorkTimeTable> loadMonthlyTimeoffs(String username, YearMonth yearMonth) {
        List<WorkTimeTable> monthEntries = dataAccess.readNetworkUserWorktimeReadOnly(
                username,
                yearMonth.getYear(),
                yearMonth.getMonthValue()
        );

        // Filter only time off entries (include all types)
        return monthEntries.stream()
                .filter(entry -> entry.getTimeOffType() != null &&
                        (entry.getTimeOffType().equals(WorkCode.TIME_OFF_CODE) || entry.getTimeOffType().equals(WorkCode.MEDICAL_LEAVE_CODE) ||
                                entry.getTimeOffType().equals(WorkCode.NATIONAL_HOLIDAY_CODE)))
                .toList();
    }

    /**
     * Read holiday entries in read-only mode.
     * This is used by the time off tracker to avoid locking the file.
     * This method does not update the local cache.
     */
    public List<PaidHolidayEntryDTO> readHolidayEntriesReadOnly() {
        try {
            return dataAccess.readHolidayEntriesReadOnly();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading holiday entries in read-only mode: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}