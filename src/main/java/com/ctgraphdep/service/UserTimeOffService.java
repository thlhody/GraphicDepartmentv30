package com.ctgraphdep.service;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserTimeOffService {
    private final DataAccessService dataAccess;
    private final HolidayManagementService holidayService;
    private final UserWorkTimeService userWorkTimeService;
    private static final TypeReference<List<WorkTimeTable>> WORKTIME_LIST_TYPE = new TypeReference<>() {};

    public UserTimeOffService(DataAccessService dataAccess, HolidayManagementService holidayService, UserWorkTimeService userWorkTimeService) {
        this.dataAccess = dataAccess;
        this.holidayService = holidayService;
        this.userWorkTimeService = userWorkTimeService;
        LoggerUtil.initialize(this.getClass(), "Initializing User Time Off Service");
    }

    @Transactional
    public void processTimeOffRequest(User user, LocalDate startDate, LocalDate endDate, String timeOffType) {
        LoggerUtil.info(this.getClass(),
                String.format("Processing time off request for user %s: %s to %s (%s)",
                        user.getUsername(), startDate, endDate, timeOffType));

        // Calculate workdays between dates
        int workDays = CalculateWorkHoursUtil.calculateWorkDays(startDate, endDate, userWorkTimeService);

        // For CO (paid holiday) requests, verify and deduct from holiday balance
        if ("CO".equals(timeOffType)) {
            boolean success = holidayService.useHolidayDays(user.getUsername(), user.getUserId(), workDays);
            if (!success) {
                throw new RuntimeException("Failed to deduct holiday days from balance");
            }
            LoggerUtil.info(this.getClass(),
                    String.format("Deducted %d holiday days for user %s", workDays, user.getUsername()));
        }

        // Create time off entries
        List<WorkTimeTable> entries = createTimeOffEntries(user, startDate, endDate, timeOffType);

        // Save entries for each affected month
        saveTimeOffEntries(user.getUsername(), entries);

        LoggerUtil.info(this.getClass(),
                String.format("Saved %d time off entries for user %s", entries.size(), user.getUsername()));
    }

    public List<WorkTimeTable> getUpcomingTimeOff(User user) {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();
        YearMonth nextMonth = currentMonth.plusMonths(1);

        // Get entries for current and next month
        List<WorkTimeTable> currentMonthEntries = loadMonthEntries(user.getUsername(),
                currentMonth.getYear(), currentMonth.getMonthValue());
        List<WorkTimeTable> nextMonthEntries = loadMonthEntries(user.getUsername(),
                nextMonth.getYear(), nextMonth.getMonthValue());

        // Combine and filter entries
        List<WorkTimeTable> allEntries = new ArrayList<>();
        allEntries.addAll(currentMonthEntries);
        allEntries.addAll(nextMonthEntries);

        return allEntries.stream()
                .filter(entry -> entry.getTimeOffType() != null)  // Only time off entries
                .filter(entry -> !entry.getTimeOffType().equals("SN"))  // Exclude national holidays
                .filter(entry -> entry.getWorkDate().isAfter(today))    // Only future dates
                .sorted((a, b) -> a.getWorkDate().compareTo(b.getWorkDate()))
                .collect(Collectors.toList());
    }

    private List<WorkTimeTable> createTimeOffEntries(User user, LocalDate startDate, LocalDate endDate, String timeOffType) {
        List<WorkTimeTable> entries = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            // Skip weekends
            if (currentDate.getDayOfWeek().getValue() >= 6) {
                currentDate = currentDate.plusDays(1);
                continue;
            }

            // Create entry
            WorkTimeTable entry = new WorkTimeTable();
            entry.setUserId(user.getUserId());
            entry.setWorkDate(currentDate);
            entry.setTimeOffType(timeOffType);
            entry.setAdminSync(SyncStatus.USER_INPUT);

            // Set default values
            entry.setDayStartTime(null);
            entry.setDayEndTime(null);
            entry.setTemporaryStopCount(0);
            entry.setLunchBreakDeducted(false);
            entry.setTotalWorkedMinutes(0);
            entry.setTotalTemporaryStopMinutes(0);
            entry.setTotalOvertimeMinutes(0);

            entries.add(entry);
            currentDate = currentDate.plusDays(1);
        }

        return entries;
    }

    private void saveTimeOffEntries(String username, List<WorkTimeTable> newEntries) {
        // Group entries by month
        Map<YearMonth, List<WorkTimeTable>> entriesByMonth = newEntries.stream()
                .collect(Collectors.groupingBy(entry ->
                        YearMonth.from(entry.getWorkDate())));

        // Process each month's entries
        entriesByMonth.forEach((yearMonth, monthEntries) -> {
            // Load existing entries for the month
            List<WorkTimeTable> existingEntries = loadMonthEntries(username,
                    yearMonth.getYear(), yearMonth.getMonthValue());

            // Remove any existing entries for these dates
            Set<LocalDate> newDates = monthEntries.stream()
                    .map(WorkTimeTable::getWorkDate)
                    .collect(Collectors.toSet());

            existingEntries.removeIf(entry -> newDates.contains(entry.getWorkDate()));

            // Add new entries
            existingEntries.addAll(monthEntries);

            // Sort entries by date
            existingEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));

            // Save updated entries
            saveMonthEntries(username, yearMonth.getYear(), yearMonth.getMonthValue(), existingEntries);
        });
    }

    private List<WorkTimeTable> loadMonthEntries(String username, int year, int month) {
        try {
            return dataAccess.readFile(
                    dataAccess.getUserWorktimePath(username, year, month),
                    WORKTIME_LIST_TYPE,
                    true
            );
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading entries for %s - %d/%d: %s",
                            username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private void saveMonthEntries(String username, int year, int month, List<WorkTimeTable> entries) {
        try {
            dataAccess.writeFile(
                    dataAccess.getUserWorktimePath(username, year, month),
                    entries
            );
            LoggerUtil.info(this.getClass(),
                    String.format("Saved %d entries for %s - %d/%d",
                            entries.size(), username, year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error saving entries for %s - %d/%d: %s",
                            username, year, month, e.getMessage()));
            throw new RuntimeException("Failed to save time off entries", e);
        }
    }
}