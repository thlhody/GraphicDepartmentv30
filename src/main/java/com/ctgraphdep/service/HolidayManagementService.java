package com.ctgraphdep.service;

import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class HolidayManagementService {
    private final DataAccessService dataAccess;
    private final ReentrantLock holidayLock = new ReentrantLock();

    public HolidayManagementService(
            DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

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

    @PreAuthorize("hasRole('ADMIN')")
    public void updateUserHolidayDays(Integer userId, int days) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntryDTO> entries = getHolidayList();

            entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst()
                    .ifPresent(entry -> {
                        entry.setPaidHolidayDays(days);
                        saveHolidayList(entries);
                        LoggerUtil.info(this.getClass(), String.format("Updated holiday days for user %d to %d days", userId, days));
                    });
        } finally {
            holidayLock.unlock();
        }
    }

    @PreAuthorize("#username == authentication.name or hasRole('ADMIN')")
    public int getRemainingHolidayDays(String username, Integer userId) {
        List<PaidHolidayEntryDTO> entries = dataAccess.readHolidayEntries();

        return entries.stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .findFirst()
                .map(PaidHolidayEntryDTO::getPaidHolidayDays)
                .orElse(0);
    }


    @PreAuthorize("#username == authentication.name")
    public boolean useHolidayDays(String username, Integer userId, int daysToUse) {
        holidayLock.lock();
        try {
            List<PaidHolidayEntryDTO> entries = dataAccess.readHolidayEntries();

            Optional<PaidHolidayEntryDTO> userEntry = entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst();

            if (userEntry.isPresent()) {
                PaidHolidayEntryDTO entry = userEntry.get();
                int remainingDays = entry.getPaidHolidayDays();

                if (remainingDays >= daysToUse) {
                    entry.setPaidHolidayDays(remainingDays - daysToUse);
                    dataAccess.writeHolidayEntries(entries);

                    LoggerUtil.info(this.getClass(), String.format("User %s used %d holiday days", username, daysToUse));
                    return true;
                }
            }
            return false;
        } finally {
            holidayLock.unlock();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<PaidHolidayEntryDTO> getHolidayList() {
        try {
            List<PaidHolidayEntryDTO> entries = dataAccess.readHolidayEntries();
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error reading holiday list: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}