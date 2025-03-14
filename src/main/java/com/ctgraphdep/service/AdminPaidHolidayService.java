package com.ctgraphdep.service;

import com.ctgraphdep.model.PaidHolidayEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
@PreAuthorize("hasRole('ADMIN') or #username == authentication.name")
public class AdminPaidHolidayService {
    private final DataAccessService dataAccess;
    private final ReentrantLock holidayLock = new ReentrantLock();

    public AdminPaidHolidayService(DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void createOrUpdateHolidayList(List<User> users) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntry> currentEntries = loadHolidayList();

            // Create entries for new users
            List<PaidHolidayEntry> newEntries = users.stream()
                    .filter(user -> !user.isAdmin()) // Exclude admin users
                    .filter(user -> currentEntries.stream()
                            .noneMatch(entry -> entry.getUserId().equals(user.getUserId())))
                    .map(PaidHolidayEntry::fromUser)
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

    @PreAuthorize("hasRole('ADMIN')")
    public void updateUserHolidayDays(Integer userId, Integer days) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntry> entries = loadHolidayList();

            Optional<PaidHolidayEntry> userEntry = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst();

            if (userEntry.isPresent()) {
                PaidHolidayEntry entry = userEntry.get();
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

    public int getRemainingHolidayDays(Integer userId) {
        holidayLock.lock();
        try {
            return loadHolidayList().stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst()
                    .map(PaidHolidayEntry::getPaidHolidayDays)
                    .orElse(0);
        } finally {
            holidayLock.unlock();
        }
    }

    @PreAuthorize("#username == authentication.name")
    public boolean useHolidayDays(String username, Integer userId, Integer daysToUse, String timeOffType) {
        // Only reduce holiday balance for CO type
        if (!"CO".equals(timeOffType)) {
            return true;
        }

        holidayLock.lock();
        try {
            List<PaidHolidayEntry> entries = loadHolidayList();

            Optional<PaidHolidayEntry> userEntry = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst();

            if (userEntry.isPresent()) {
                PaidHolidayEntry entry = userEntry.get();
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

    @PreAuthorize("hasRole('ADMIN')")
    public void restoreHolidayDay(Integer userId) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntry> entries = loadHolidayList();

            Optional<PaidHolidayEntry> userEntry = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst();

            if (userEntry.isPresent()) {
                PaidHolidayEntry entry = userEntry.get();
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

    public List<PaidHolidayEntry> loadHolidayList() {
        try {
            return dataAccess.readHolidayEntries();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error loading holiday list: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveHolidayList(List<PaidHolidayEntry> entries) {
        try {
            dataAccess.writeHolidayEntries(entries);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error saving holiday list: " + e.getMessage());
            throw new RuntimeException("Failed to save holiday list", e);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void initializeUserHolidays(User user, Integer initialDays) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntry> entries = loadHolidayList();

            // Check if entry already exists
            boolean exists = entries.stream()
                    .anyMatch(entry -> entry.getUserId().equals(user.getUserId()));

            if (!exists) {
                PaidHolidayEntry newEntry = PaidHolidayEntry.fromUser(user);
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
}