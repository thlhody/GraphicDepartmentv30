package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
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
    private static final TypeReference<List<WorkTimeTable>> WORKTIME_LIST_TYPE = new TypeReference<>() {};

    public WorkTimeManagementService(
            DataAccessService dataAccess,
            AdminPaidHolidayService holidayService, UserService userService) {
        this.dataAccess = dataAccess;
        this.holidayService = holidayService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), "Initializing WorkTime Management Service");
    }

    //Process admin worktime update for a specific user and date
    public void processWorktimeUpdate(Integer userId, LocalDate date, String value) {
        adminLock.lock();
        try {
            validateUpdateRequest(userId, date, value);

            // Check existing entry before update for CO handling
            WorkTimeTable existingEntry = getExistingEntry(userId, date);
            boolean wasTimeOff = existingEntry != null &&
                    WorkCode.TIME_OFF_CODE.equals(existingEntry.getTimeOffType());

            // Create appropriate entry based on value
            WorkTimeTable entry;
            if (value == null || value.trim().isEmpty() || "REMOVE".equals(value)) {
                // Handle removal explicitly
                entry = createBlankEntry(userId, date);
                entry.setAdminSync(SyncStatus.ADMIN_BLANK);
                if (wasTimeOff) {
                    restorePaidHoliday(userId);
                }
            } else if (value.matches("^(SN|CO|CM)$")) {
                entry = createTimeOffEntry(userId, date, value);
                entry.setAdminSync(SyncStatus.ADMIN_EDITED);
                if (WorkCode.TIME_OFF_CODE.equals(value) && !wasTimeOff) {
                    processTimeOffUpdate(userId, date, value);
                }
            } else if (value.matches("^([1-9]|1\\d|2[0-4])$")) {
                entry = createWorkHoursEntry(userId, date, Integer.parseInt(value));
                entry.setAdminSync(SyncStatus.ADMIN_EDITED);
                if (wasTimeOff) {
                    restorePaidHoliday(userId);
                }
            } else {
                LoggerUtil.info(WorkTimeManagementService.class, "Invalid value format: " + value);
                throw new IllegalArgumentException("Invalid value format: " + value);
            }

            // Save entry with proper status handling
            saveAdminEntryWithStatusHandling(entry, date.getYear(), date.getMonthValue());

            LoggerUtil.info(this.getClass(),
                    String.format("Processed admin worktime update for user %d on %s: %s",
                            userId, date, value));

        } finally {
            adminLock.unlock();
        }
    }

    private WorkTimeTable getExistingEntry(Integer userId, LocalDate date) {
        List<WorkTimeTable> existingEntries = dataAccess.readFile(
                dataAccess.getAdminWorktimePath(date.getYear(), date.getMonthValue()),
                WORKTIME_LIST_TYPE,
                true
        );

        return existingEntries.stream()
                .filter(entry -> entry.getUserId().equals(userId) &&
                        entry.getWorkDate().equals(date))
                .findFirst()
                .orElse(null);
    }

    private void saveAdminEntryWithStatusHandling(WorkTimeTable newEntry, int year, int month) {
        List<WorkTimeTable> entries = dataAccess.readFile(
                dataAccess.getAdminWorktimePath(year, month),
                WORKTIME_LIST_TYPE,
                true
        );

        // Remove existing entry for this date and user
        entries.removeIf(e -> e.getUserId().equals(newEntry.getUserId()) &&
                e.getWorkDate().equals(newEntry.getWorkDate()));

        // Always add the entry - includes ADMIN_BLANK to signal removal to user
        entries.add(newEntry);

        // Sort entries
        entries.sort(Comparator
                .comparing(WorkTimeTable::getWorkDate)
                .thenComparing(WorkTimeTable::getUserId));

        // Save the entries
        dataAccess.writeFile(
                dataAccess.getAdminWorktimePath(year, month),
                entries
        );

        LoggerUtil.info(this.getClass(),
                String.format("Saved entry with status %s to general worktime",
                        newEntry.getAdminSync()));
    }

    //Add national holiday for all users
    public void addNationalHoliday(LocalDate date) {
        adminLock.lock();
        try {
            validateHolidayDate(date);

            // Get existing entries
            List<WorkTimeTable> entries = dataAccess.readFile(
                    dataAccess.getAdminWorktimePath(date.getYear(), date.getMonthValue()),
                    WORKTIME_LIST_TYPE,
                    true
            );

            // Get all non-admin users from user service
            List<User> nonAdminUsers = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin())
                    .toList();

            // Remove any existing entries for this date
            entries.removeIf(entry -> entry.getWorkDate().equals(date));

            // Create holiday entries for each user
            List<WorkTimeTable> holidayEntries = nonAdminUsers.stream()
                    .map(user -> createTimeOffEntry(user.getUserId(), date, WorkCode.NATIONAL_HOLIDAY_CODE))
                    .toList();

            if (!holidayEntries.isEmpty()) {
                entries.addAll(holidayEntries);
                saveAdminEntry(entries, date.getYear(), date.getMonthValue());

                LoggerUtil.info(this.getClass(),
                        String.format("Added national holiday for %s with %d entries",
                                date, holidayEntries.size()));
            } else {
                LoggerUtil.info(this.getClass(),
                        String.format("National holiday already exists for %s", date));
            }

        } finally {
            adminLock.unlock();
        }
    }

    private void processTimeOffUpdate(Integer userId, LocalDate date, String type) {
        if (WorkCode.TIME_OFF_CODE.equals(type)) { // "CO" is the time off code
            int availableDays = holidayService.getRemainingHolidayDays(null, userId);
            if (availableDays < 1) {
                throw new IllegalStateException("Insufficient paid holiday days available");
            }
            holidayService.updateUserHolidayDays(userId, availableDays - 1);
            LoggerUtil.info(this.getClass(),
                    String.format("Deducted 1 paid holiday day for user %d. New balance: %d",
                            userId, availableDays - 1));
        }
    }

    private void validateUpdateRequest(Integer userId, LocalDate date, String value) {
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

    private WorkTimeTable createBlankEntry(Integer userId, LocalDate date) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);
        entry.setTimeOffType(null);
        entry.setAdminSync(SyncStatus.ADMIN_BLANK);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setLunchBreakDeducted(false);
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

        // Set start time at 07:00
        LocalDateTime startTime = date.atTime(WorkCode.START_HOUR, 0);
        entry.setDayStartTime(startTime);

        // Calculate total minutes and end time
        int totalMinutes = (hours * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION;
        entry.setTotalWorkedMinutes(totalMinutes);

        // Calculate end time based on total minutes
        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);
        entry.setDayEndTime(endTime);

        // Set lunch break if more than 4 hours
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

    private void restorePaidHoliday(Integer userId) {
        try {
            int currentDays = holidayService.getRemainingHolidayDays(null, userId);
            holidayService.updateUserHolidayDays(userId, currentDays + 1);

            LoggerUtil.info(this.getClass(),
                    String.format("Restored paid holiday day for user %d. New balance: %d",
                            userId, currentDays + 1));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error restoring paid holiday: " + e.getMessage());
            throw new RuntimeException("Failed to restore paid holiday", e);
        }
    }

    private void saveAdminEntry(List<WorkTimeTable> newEntries, int year, int month) {
        // Load existing entries
        List<WorkTimeTable> existingEntries = dataAccess.readFile(
                dataAccess.getAdminWorktimePath(year, month),
                WORKTIME_LIST_TYPE,
                true
        );

        // Create a map of existing entries that we want to keep (those not affected by new entries)
        Map<String, WorkTimeTable> entriesMap = existingEntries.stream()
                .filter(existing -> newEntries.stream()
                        .noneMatch(newEntry ->
                                newEntry.getUserId().equals(existing.getUserId()) &&
                                        newEntry.getWorkDate().equals(existing.getWorkDate())))
                .collect(Collectors.toMap(
                        entry -> entry.getUserId() + "_" + entry.getWorkDate(),
                        entry -> entry
                ));

        // Add all new entries to the map
        newEntries.forEach(entry ->
                entriesMap.put(entry.getUserId() + "_" + entry.getWorkDate(), entry));

        // Convert back to sorted list
        List<WorkTimeTable> finalEntries = new ArrayList<>(entriesMap.values());
        finalEntries.sort(Comparator
                .comparing(WorkTimeTable::getWorkDate)
                .thenComparing(WorkTimeTable::getUserId));

        // Save the combined entries
        dataAccess.writeFile(
                dataAccess.getAdminWorktimePath(year, month),
                finalEntries
        );

        LoggerUtil.info(this.getClass(),
                String.format("Saved %d entries to general worktime", finalEntries.size()));
    }

    public int getWorkedDays(Integer userId, int year, int month) {
        LoggerUtil.info(this.getClass(),
                String.format("Getting worked days for userId: %d, year: %d, month: %d",
                        userId, year, month));

        Path worktimePath = dataAccess.getAdminWorktimePath(year, month);
        LoggerUtil.info(this.getClass(), "Loading from path: " + worktimePath);

        List<WorkTimeTable> entries = dataAccess.readFile(
                worktimePath,
                WORKTIME_LIST_TYPE,
                true
        );

        LoggerUtil.info(this.getClass(), String.format("Loaded %d total entries", entries.size()));

        long workedDays = entries.stream()
                .filter(entry -> {
                    boolean matches = entry.getUserId().equals(userId);
                    if (!matches) {
                        //LoggerUtil.debug(this.getClass(), String.format("Entry userId %d doesn't match requested %d", entry.getUserId(), userId));
                    }
                    return matches;
                })
                .filter(entry -> {
                    boolean isWorkDay = entry.getTimeOffType() == null &&
                            entry.getTotalWorkedMinutes() != null &&
                            entry.getTotalWorkedMinutes() > 0;
                    if (!isWorkDay) {
                        //LoggerUtil.debug(this.getClass(), String.format("Entry for date %s filtered out: timeOffType=%s, minutes=%d", entry.getWorkDate(), entry.getTimeOffType(), entry.getTotalWorkedMinutes()));
                    }
                    return isWorkDay;
                })
                .count();

        LoggerUtil.info(this.getClass(), String.format("Found %d worked days for user %d", workedDays, userId));

        return (int) workedDays;
    }
}