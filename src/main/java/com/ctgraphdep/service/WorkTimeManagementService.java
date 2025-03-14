package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class WorkTimeManagementService {
    private final AdminPaidHolidayService holidayService;
    private final DataAccessService dataAccess;
    private final UserService userService;
    private final ReentrantLock adminLock = new ReentrantLock();

    public WorkTimeManagementService(
            DataAccessService dataAccess,
            AdminPaidHolidayService holidayService,
            UserService userService) {
        this.dataAccess = dataAccess;
        this.holidayService = holidayService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void processWorktimeUpdate(Integer userId, LocalDate date, String value) {
        adminLock.lock();
        try {
            validateUpdateRequest(userId, date);

            // Get existing entry for comparison
            WorkTimeTable existingEntry = getExistingEntry(userId, date);

            // Create new entry based on input value
            WorkTimeTable newEntry = createEntryFromValue(userId, date, value);

            // Handle paid holiday balance updates
            handlePaidHolidayBalance(existingEntry, newEntry);

            // Save the entry
            saveAdminEntry(newEntry, date.getYear(), date.getMonthValue());

            LoggerUtil.info(this.getClass(), String.format("Processed admin worktime update for user %d on %s: %s", userId, date, value));

        } finally {
            adminLock.unlock();
        }
    }

    private boolean isTimeOffEntry(WorkTimeTable entry) {
        return entry != null && WorkCode.TIME_OFF_CODE.equals(entry.getTimeOffType());
    }

    private WorkTimeTable createEntryFromValue(Integer userId, LocalDate date, String value) {
        if (isRemoveValue(value)) {
            return createAdminBlankEntry(userId, date);
        }

        if (isTimeOffValue(value)) {
            return createTimeOffEntry(userId, date, value);
        }

        if (isWorkHoursValue(value)) {
            return createWorkHoursEntry(userId, date, Integer.parseInt(value));
        }

        throw new IllegalArgumentException("Invalid value format: " + value);
    }

    private boolean isRemoveValue(String value) {
        return value == null || value.trim().isEmpty() || "REMOVE".equals(value);
    }

    private boolean isTimeOffValue(String value) {
        return value != null && value.matches("^(SN|CO|CM)$");
    }

    private boolean isWorkHoursValue(String value) {
        return value != null && value.matches("^([1-9]|1\\d|2[0-4])$");
    }

    private void handlePaidHolidayBalance(WorkTimeTable existingEntry, WorkTimeTable newEntry) {
        boolean wasTimeOff = isTimeOffEntry(existingEntry);
        boolean isTimeOff = isTimeOffEntry(newEntry);

        if (wasTimeOff && !isTimeOff) {
            // Restore paid holiday day when removing CO
            restorePaidHoliday(existingEntry.getUserId());
        } else if (!wasTimeOff && isTimeOff && WorkCode.TIME_OFF_CODE.equals(newEntry.getTimeOffType())) {
            // Deduct paid holiday day when adding CO
            processTimeOffUpdate(newEntry.getUserId(), newEntry.getTimeOffType());
        }
    }

    public void addNationalHoliday(LocalDate date) {
        adminLock.lock();
        try {
            validateHolidayDate(date);

            List<WorkTimeTable> entries = loadAdminEntries(date.getYear(), date.getMonthValue());
            List<User> nonAdminUsers = getNonAdminUsers();

            // Remove existing entries for this date
            entries.removeIf(entry -> entry.getWorkDate().equals(date));

            // Create holiday entries for each user
            List<WorkTimeTable> holidayEntries = createHolidayEntriesForUsers(nonAdminUsers, date);

            if (!holidayEntries.isEmpty()) {
                entries.addAll(holidayEntries);
                saveAdminEntry(entries, date.getYear(), date.getMonthValue());

                LoggerUtil.info(this.getClass(), String.format("Added national holiday for %s with %d entries", date, holidayEntries.size()));
            }
        } finally {
            adminLock.unlock();
        }
    }

    private List<User> getNonAdminUsers() {
        return userService.getAllUsers().stream().filter(user -> !user.isAdmin()).toList();
    }

    private List<WorkTimeTable> createHolidayEntriesForUsers(List<User> users, LocalDate date) {
        return users.stream()
                .map(user -> createTimeOffEntry(user.getUserId(), date, WorkCode.NATIONAL_HOLIDAY_CODE))
                .collect(Collectors.toList());
    }

    private WorkTimeTable createAdminBlankEntry(Integer userId, LocalDate date) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);
        entry.setTimeOffType(null);
        entry.setAdminSync(SyncStatus.ADMIN_BLANK);
        resetEntryValues(entry);
        return entry;
    }

    private WorkTimeTable createTimeOffEntry(Integer userId, LocalDate date, String type) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);
        entry.setTimeOffType(type.toUpperCase());
        entry.setAdminSync(SyncStatus.ADMIN_EDITED);
        resetEntryValues(entry);
        return entry;
    }

    private WorkTimeTable createWorkHoursEntry(Integer userId, LocalDate date, int hours) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);

        LocalDateTime startTime = date.atTime(WorkCode.START_HOUR, 0);
        entry.setDayStartTime(startTime);

        int totalMinutes = (hours * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION;
        entry.setTotalWorkedMinutes(totalMinutes);

        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);
        entry.setDayEndTime(endTime);

        entry.setLunchBreakDeducted(hours > WorkCode.INTERVAL_HOURS_A);
        entry.setTimeOffType(null);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setAdminSync(SyncStatus.ADMIN_EDITED);

        return entry;
    }

    private void resetEntryValues(WorkTimeTable entry) {
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);
    }

    private WorkTimeTable getExistingEntry(Integer userId, LocalDate date) {
        return loadAdminEntries(date.getYear(), date.getMonthValue()).stream()
                .filter(entry -> entry.getUserId().equals(userId) && entry.getWorkDate().equals(date))
                .findFirst()
                .orElse(null);
    }

    private List<WorkTimeTable> loadAdminEntries(int year, int month) {
        try {
            // Load from local since we're using local-primary approach
            List<WorkTimeTable> entries = dataAccess.readLocalAdminWorktime(year, month);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading admin entries for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private void saveAdminEntry(List<WorkTimeTable> entries, int year, int month) {
        try {
            // Sort entries before saving
            entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparing(WorkTimeTable::getUserId));

            // Write using DataAccessService which handles local save and network sync
            dataAccess.writeAdminWorktime(entries, year, month);

            LoggerUtil.info(this.getClass(), String.format("Saved %d entries to admin worktime", entries.size()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving admin entries for %d/%d: %s", year, month, e.getMessage()));
            throw new RuntimeException("Failed to save admin entries", e);
        }
    }

    private void saveAdminEntry(WorkTimeTable entry, int year, int month) {
        List<WorkTimeTable> entries = loadAdminEntries(year, month);

        // Remove existing entry
        entries.removeIf(e -> e.getUserId().equals(entry.getUserId()) && e.getWorkDate().equals(entry.getWorkDate()));

        // Add new entry
        entries.add(entry);

        // Sort and save
        saveAdminEntry(entries, year, month);
    }

    private void processTimeOffUpdate(Integer userId, String type) {
        if (WorkCode.TIME_OFF_CODE.equals(type)) {
            int availableDays = holidayService.getRemainingHolidayDays(userId);
            if (availableDays < 1) {
                throw new IllegalStateException("Insufficient paid holiday days available");
            }
            holidayService.updateUserHolidayDays(userId, availableDays - 1);
            LoggerUtil.info(this.getClass(), String.format("Deducted 1 paid holiday day for user %d. New balance: %d", userId, availableDays - 1));
        }
    }

    private void restorePaidHoliday(Integer userId) {
        try {
            int currentDays = holidayService.getRemainingHolidayDays(userId);
            holidayService.updateUserHolidayDays(userId, currentDays + 1);
            LoggerUtil.info(this.getClass(), String.format("Restored paid holiday day for user %d. New balance: %d", userId, currentDays + 1));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error restoring paid holiday: " + e.getMessage());
            throw new RuntimeException("Failed to restore paid holiday", e);
        }
    }

    private void validateUpdateRequest(Integer userId, LocalDate date) {
        if (userId == null || date == null) {
            throw new IllegalArgumentException("Invalid update parameters");
        }

        YearMonth requested = YearMonth.from(date);
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(4);

        if (requested.isBefore(previous) || requested.isAfter(current)) {
            throw new IllegalArgumentException("Can only update current or previous month");
        }

        if (requested.equals(previous) && LocalDate.now().getDayOfMonth() > 15) {
            throw new IllegalArgumentException("Previous month no longer editable after 15th");
        }
    }

    private void validateHolidayDate(LocalDate date) {
        YearMonth requested = YearMonth.from(date);
        YearMonth current = YearMonth.now();

        if (requested.isBefore(current)) {
            throw new IllegalArgumentException("Cannot add holidays for past months");
        }

        if (date.getDayOfWeek().getValue() >= 6) {
            throw new IllegalArgumentException("Cannot add holidays on weekends");
        }
    }

    public int getWorkedDays(Integer userId, int year, int month) {
        return (int) loadAdminEntries(year, month).stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .filter(entry -> entry.getTimeOffType() == null &&
                        entry.getTotalWorkedMinutes() != null &&
                        entry.getTotalWorkedMinutes() > 0)
                .count();
    }
}