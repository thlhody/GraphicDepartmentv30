package com.ctgraphdep.service;

import com.ctgraphdep.model.SyncStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserTimeOffService {
    private final UserWorkTimeService userWorkTimeService;
    private final AdminPaidHolidayService holidayService;
    private final DataAccessService dataAccessService;
    private static final TypeReference<List<WorkTimeTable>> WORKTIME_LIST_TYPE = new TypeReference<>() {};

    public UserTimeOffService(
            UserWorkTimeService userWorkTimeService, AdminPaidHolidayService holidayService, DataAccessService dataAccessService) {
        this.userWorkTimeService = userWorkTimeService;
        this.holidayService = holidayService;
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), "Initializing Time Off Service");
    }

    @PreAuthorize("#user.username == authentication.name")
    public void processTimeOffRequest(User user, LocalDate startDate, LocalDate endDate, String timeOffType) {
        LoggerUtil.info(this.getClass(),
                String.format("Processing time off request for user %s: %s to %s (%s)",
                        user.getUsername(), startDate, endDate, timeOffType));

        // Calculate work days between dates
        int workDays = CalculateWorkHoursUtil.calculateWorkDays(startDate, endDate, userWorkTimeService);
        LoggerUtil.info(this.getClass(),
                String.format("Calculated %d eligible workdays for time off request", workDays));

        if (workDays == 0) {
            LoggerUtil.warn(this.getClass(), "No eligible workdays found in the selected date range");
            throw new IllegalArgumentException("No eligible workdays in selected date range");
        }

        // Handle paid holiday (CO) deduction
        if ("CO".equals(timeOffType)) {
            boolean deducted = holidayService.useHolidayDays(
                    user.getUsername(), user.getUserId(), workDays, timeOffType);
            if (!deducted) {
                throw new IllegalStateException("Failed to deduct paid holiday days");
            }
            LoggerUtil.info(this.getClass(),
                    String.format("Updated paid holiday balance for user %s. Used %d days",
                            user.getUsername(), workDays));
        }

        // Create time off entries
        List<WorkTimeTable> entries = createTimeOffEntries(user, startDate, endDate, timeOffType);

        if (!entries.isEmpty()) {
            // Save to user worktime
            LoggerUtil.info(this.getClass(),
                    String.format("Saving %d time off entries for user %s",
                            entries.size(), user.getUsername()));
            saveUserWorkTimeEntries(user.getUsername(), entries);
        } else {
            LoggerUtil.error(this.getClass(),
                    "No entries were created for time off request");
            throw new IllegalStateException("Failed to create time off entries");
        }

        LoggerUtil.info(this.getClass(),
                String.format("Successfully processed %s request for user %s: %d days",
                        timeOffType, user.getUsername(), workDays));
    }

    private List<WorkTimeTable> createTimeOffEntries(User user, LocalDate startDate, LocalDate endDate,
                                                     String timeOffType) {
        List<WorkTimeTable> entries = new ArrayList<>();
        LocalDate currentDate = startDate;

        LoggerUtil.info(this.getClass(),
                String.format("Starting time off entry creation for dates %s to %s", startDate, endDate));

        while (!currentDate.isAfter(endDate)) {
            LoggerUtil.debug(this.getClass(),
                    String.format("Processing date: %s (%s)", currentDate, currentDate.getDayOfWeek()));

            // Skip weekends but continue processing
            if (currentDate.getDayOfWeek() == DayOfWeek.SATURDAY ||
                    currentDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                LoggerUtil.debug(this.getClass(),
                        String.format("Skipping weekend day: %s", currentDate));
                currentDate = currentDate.plusDays(1);
                continue;
            }

            // Skip holidays but continue processing
            if (userWorkTimeService.isNationalHoliday(currentDate)) {
                LoggerUtil.debug(this.getClass(),
                        String.format("Skipping national holiday: %s", currentDate));
                currentDate = currentDate.plusDays(1);
                continue;
            }

            // Create entry for weekday
            WorkTimeTable entry = new WorkTimeTable();
            entry.setUserId(user.getUserId());
            entry.setWorkDate(currentDate);
            entry.setTimeOffType(timeOffType);
            entry.setAdminSync(SyncStatus.USER_INPUT);  // Important: This should be false for user entries

            // Set default values
            entry.setDayStartTime(null);
            entry.setDayEndTime(null);
            entry.setTemporaryStopCount(0);
            entry.setLunchBreakDeducted(false);
            entry.setTotalWorkedMinutes(0);
            entry.setTotalTemporaryStopMinutes(0);
            entry.setTotalOvertimeMinutes(0);

            entries.add(entry);
            LoggerUtil.info(this.getClass(),
                    String.format("Created time off entry for date: %s, type: %s", currentDate, timeOffType));

            currentDate = currentDate.plusDays(1);
        }

        LoggerUtil.info(this.getClass(),
                String.format("Created %d time off entries for date range %s to %s",
                        entries.size(), startDate, endDate));

        if (entries.isEmpty()) {
            LoggerUtil.warn(this.getClass(),
                    String.format("No entries were created for date range %s to %s", startDate, endDate));
        }

        return entries;
    }

    private void saveUserWorkTimeEntries(String username, List<WorkTimeTable> entries) {
        if (entries.isEmpty()) {
            LoggerUtil.warn(this.getClass(), "No entries to save for user " + username);
            return;
        }

        // Group by month for processing
        Map<YearMonth, List<WorkTimeTable>> entriesByMonth = entries.stream()
                .collect(Collectors.groupingBy(entry ->
                        YearMonth.from(entry.getWorkDate())));

        LoggerUtil.info(this.getClass(),
                String.format("Saving time off entries for %d months", entriesByMonth.size()));

        // Process each month's entries
        entriesByMonth.forEach((yearMonth, monthEntries) -> {
            try {
                // Get the correct path for this month's entries
                Path userWorktimePath = dataAccessService.getUserWorktimePath(
                        username, yearMonth.getYear(), yearMonth.getMonthValue());

                LoggerUtil.info(this.getClass(),
                        String.format("Processing month %s/%d at path: %s",
                                yearMonth.getMonth(), yearMonth.getYear(), userWorktimePath));

                // Read existing entries or create new list
                List<WorkTimeTable> existingEntries = new ArrayList<>();
                if (Files.exists(userWorktimePath)) {
                    existingEntries = dataAccessService.readFile(userWorktimePath, WORKTIME_LIST_TYPE, true);
                    LoggerUtil.info(this.getClass(),
                            String.format("Found %d existing entries", existingEntries.size()));
                }

                // Remove any existing entries for these dates
                Set<LocalDate> newDates = monthEntries.stream()
                        .map(WorkTimeTable::getWorkDate)
                        .collect(Collectors.toSet());

                int beforeSize = existingEntries.size();
                existingEntries.removeIf(entry -> newDates.contains(entry.getWorkDate()));
                LoggerUtil.info(this.getClass(),
                        String.format("Removed %d existing entries", beforeSize - existingEntries.size()));

                // Add new entries
                existingEntries.addAll(monthEntries);
                LoggerUtil.info(this.getClass(),
                        String.format("Added %d new entries", monthEntries.size()));

                // Sort entries
                existingEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));

                // Save updated entries
                dataAccessService.writeFile(userWorktimePath, existingEntries);
                LoggerUtil.info(this.getClass(),
                        String.format("Successfully saved %d total entries to %s",
                                existingEntries.size(), userWorktimePath));

            } catch (Exception e) {
                String error = String.format("Failed to save time off entries for user %s in %s: %s",
                        username, yearMonth, e.getMessage());
                LoggerUtil.error(this.getClass(), error);
                throw new RuntimeException(error, e);
            }
        });
    }
}