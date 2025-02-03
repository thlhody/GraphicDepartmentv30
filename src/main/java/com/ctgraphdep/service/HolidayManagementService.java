package com.ctgraphdep.service;

import com.ctgraphdep.model.PaidHolidayEntry;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class HolidayManagementService {
    private static final TypeReference<List<PaidHolidayEntry>> HOLIDAY_LIST_TYPE = new TypeReference<>() {};
    private final DataAccessService dataAccess;
    private final ReentrantLock holidayLock = new ReentrantLock();

    public HolidayManagementService(
            DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void saveHolidayList(List<PaidHolidayEntry> entries) {
        holidayLock.lock();
        try {
            dataAccess.writeHolidayEntries(entries);
            LoggerUtil.info(this.getClass(),
                    String.format("Saved holiday list with %d entries", entries.size()));
        } finally {
            holidayLock.unlock();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void updateUserHolidayDays(Integer userId, int days) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntry> entries = getHolidayList();

            entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst()
                    .ifPresent(entry -> {
                        entry.setPaidHolidayDays(days);
                        saveHolidayList(entries);
                        LoggerUtil.info(this.getClass(),
                                String.format("Updated holiday days for user %d to %d days",
                                        userId, days));
                    });
        } finally {
            holidayLock.unlock();
        }
    }

    @PreAuthorize("#username == authentication.name or hasRole('ADMIN')")
    public int getRemainingHolidayDays(String username, Integer userId) {
        List<PaidHolidayEntry> entries = dataAccess.readHolidayEntries();

        return entries.stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .findFirst()
                .map(PaidHolidayEntry::getPaidHolidayDays)
                .orElse(0);
    }


    @PreAuthorize("#username == authentication.name")
    public boolean useHolidayDays(String username, Integer userId, int daysToUse) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntry> entries = dataAccess.readHolidayEntries();

            Optional<PaidHolidayEntry> userEntry = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst();

            if (userEntry.isPresent()) {
                PaidHolidayEntry entry = userEntry.get();
                int remainingDays = entry.getPaidHolidayDays();

                if (remainingDays >= daysToUse) {
                    entry.setPaidHolidayDays(remainingDays - daysToUse);
                    dataAccess.writeHolidayEntries(entries);

                    LoggerUtil.info(this.getClass(),
                            String.format("User %s used %d holiday days", username, daysToUse));
                    return true;
                }
            }
            return false;
        } finally {
            holidayLock.unlock();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<PaidHolidayEntry> getHolidayList() {
        try {
            List<PaidHolidayEntry> entries = dataAccess.readHolidayEntries();
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error reading holiday list: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}