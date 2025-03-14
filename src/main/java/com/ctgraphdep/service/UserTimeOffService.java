package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.TimeOffSummary;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserTimeOffService {
    private final DataAccessService dataAccessService;
    private final HolidayManagementService holidayService;
    private final UserWorkTimeService userWorkTimeService;
    private final UserService userService;

    public UserTimeOffService(DataAccessService dataAccessService, HolidayManagementService holidayService,
                              UserWorkTimeService userWorkTimeService, UserService userService) {
        this.dataAccessService = dataAccessService;
        this.holidayService = holidayService;
        this.userWorkTimeService = userWorkTimeService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Transactional
    public void processTimeOffRequest(User user, LocalDate startDate, LocalDate endDate, String timeOffType) {
        LoggerUtil.info(this.getClass(),
                String.format("Processing time off request for user %s: %s to %s (%s)",
                        user.getUsername(), startDate, endDate, timeOffType));

        validateTimeOffRequest(startDate, endDate, timeOffType);

        // Calculate eligible workdays (excluding weekends, holidays, and existing time off)
        int workDays = calculateEligibleDays(user.getUserId(), startDate, endDate);

        // Verify and process CO requests
        if ("CO".equals(timeOffType)) {
            processCoRequest(user, workDays);
        }

        // Create and save time off entries
        List<WorkTimeTable> entries = createTimeOffEntries(user, startDate, endDate, timeOffType);
        saveTimeOffEntries(user.getUsername(), entries);

        LoggerUtil.info(this.getClass(),
                String.format("Processed %d time off entries for user %s", entries.size(), user.getUsername()));
    }

    // Gets all-time off records for a specific user for the entire year

    public List<WorkTimeTable> getUserTimeOffHistory(String username, Integer year) {
        LoggerUtil.info(this.getClass(),
                String.format("Fetching time off history for user %s for year %d", username, year));

        List<WorkTimeTable> allEntries = new ArrayList<>();

        // Load entries for all months in the specified year
        for (int month = 1; month <= 12; month++) {
            try {
                List<WorkTimeTable> monthEntries = userWorkTimeService.loadMonthWorktime(username, year, month);
                if (monthEntries != null) {
                    allEntries.addAll(monthEntries);
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Error loading entries for %s - %d/%d: %s",
                                username, year, month, e.getMessage()));
                // Continue with next month even if one fails
            }
        }

        // Filter only time off entries (where timeOffType is not null)
        return allEntries.stream()
                .filter(entry -> entry.getTimeOffType() != null)
                .sorted((a, b) -> b.getWorkDate().compareTo(a.getWorkDate())) // Sort descending by date
                .collect(Collectors.toList());
    }

    public TimeOffSummary calculateTimeOffSummary(String username, Integer year) {
        List<WorkTimeTable> timeOffEntries = getUserTimeOffHistory(username, year);

        // Count different time off types
        int snDays = 0, coDays = 0, cmDays = 0;

        for (WorkTimeTable entry : timeOffEntries) {
            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case "SN" -> snDays++;
                    case "CO" -> coDays++;
                    case "CM" -> cmDays++;
                }
            }
        }

        // Get available paid days and calculate remaining days
        int availablePaidDays;
        int remainingPaidDays;

        try {
            // Get the user ID from username
            Optional<User> userOpt = userService.getUserByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                availablePaidDays = holidayService.getRemainingHolidayDays(username, user.getUserId());

                // For historical years, just show the used days
                if (year < Year.now().getValue()) {
                    // For past years, the remaining days would be total - used
                    // Since we don't know the original allocation, just set the remaining to 0
                    // and available to the used amount for display purposes
                    remainingPaidDays = 0;
                    availablePaidDays = coDays;
                } else {
                    // For current year, use the actual remaining days
                    remainingPaidDays = availablePaidDays - coDays;
                    if (remainingPaidDays < 0) {
                        remainingPaidDays = 0;
                    }
                }
            } else {
                // If user not found, default values
                availablePaidDays = coDays;
                remainingPaidDays = 0;
                LoggerUtil.warn(this.getClass(), "User not found for username: " + username);
            }
        } catch (Exception e) {
            // Handle errors in fetching holiday data
            LoggerUtil.error(this.getClass(),
                    String.format("Error getting paid holiday days for %s: %s",
                            username, e.getMessage()));
            availablePaidDays = coDays;
            remainingPaidDays = 0;
        }

        // Build and return the summary
        return TimeOffSummary.builder()
                .snDays(snDays)
                .coDays(coDays)
                .cmDays(cmDays)
                .availablePaidDays(availablePaidDays)
                .paidDaysTaken(coDays)
                .remainingPaidDays(remainingPaidDays)
                .build();
    }

    private void validateTimeOffRequest(LocalDate startDate, LocalDate endDate, String timeOffType) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
        if (!Arrays.asList("CO", "CM").contains(timeOffType)) {
            throw new IllegalArgumentException("Invalid time off type: " + timeOffType);
        }
    }

    private int calculateEligibleDays(Integer userId, LocalDate startDate, LocalDate endDate) {
        int eligibleDays = 0;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            if (isEligibleForTimeOff(userId, currentDate)) {
                eligibleDays++;
            }
            currentDate = currentDate.plusDays(1);
        }

        return eligibleDays;
    }

    private boolean isEligibleForTimeOff(Integer userId, LocalDate date) {
        // Skip weekends
        if (date.getDayOfWeek().getValue() >= 6) {
            return false;
        }

        // Skip national holidays
        if (userWorkTimeService.isNationalHoliday(date)) {
            return false;
        }

        // Check existing entries
        WorkTimeTable existingEntry = getExistingEntry(userId, date);
        if (existingEntry != null) {
            // Cannot add time off if there's already a non-ADMIN_BLANK entry
            return SyncStatus.ADMIN_BLANK.equals(existingEntry.getAdminSync());
        }

        return true;
    }

    private void processCoRequest(User user, int workDays) {
        boolean success = holidayService.useHolidayDays(user.getUsername(), user.getUserId(), workDays);
        if (!success) {
            throw new RuntimeException("Failed to deduct holiday days from balance");
        }
        LoggerUtil.info(this.getClass(),
                String.format("Deducted %d paid holiday days for user %s", workDays, user.getUsername()));
    }

    private List<WorkTimeTable> createTimeOffEntries(User user, LocalDate startDate, LocalDate endDate, String timeOffType) {
        List<WorkTimeTable> entries = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            if (isEligibleForTimeOff(user.getUserId(), currentDate)) {
                WorkTimeTable existingEntry = getExistingEntry(user.getUserId(), currentDate);
                WorkTimeTable entry = createTimeOffEntry(user, currentDate, timeOffType, existingEntry);
                entries.add(entry);
            }
            currentDate = currentDate.plusDays(1);
        }

        return entries;
    }

    private WorkTimeTable createTimeOffEntry(User user, LocalDate date, String timeOffType, WorkTimeTable existingEntry) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(user.getUserId());
        entry.setWorkDate(date);
        entry.setTimeOffType(timeOffType);

        // Set appropriate status based on existing entry
        if (existingEntry != null && SyncStatus.ADMIN_BLANK.equals(existingEntry.getAdminSync())) {
            entry.setAdminSync(SyncStatus.USER_EDITED); // New entry over ADMIN_BLANK
        } else {
            entry.setAdminSync(SyncStatus.USER_INPUT); // Normal new entry
        }

        resetWorkFields(entry);
        return entry;
    }

    private WorkTimeTable getExistingEntry(Integer userId, LocalDate date) {
        YearMonth yearMonth = YearMonth.from(date);
        List<WorkTimeTable> existingEntries = loadMonthEntries(String.valueOf(userId), yearMonth.getYear(), yearMonth.getMonthValue());

        return existingEntries.stream()
                .filter(entry -> entry.getWorkDate().equals(date))
                .findFirst()
                .orElse(null);
    }

    private void saveTimeOffEntries(String username, List<WorkTimeTable> newEntries) {
        // Group entries by month for processing
        Map<YearMonth, List<WorkTimeTable>> entriesByMonth = newEntries.stream()
                .collect(Collectors.groupingBy(entry -> YearMonth.from(entry.getWorkDate())));

        entriesByMonth.forEach((yearMonth, monthEntries) -> {
            List<WorkTimeTable> existingEntries = loadMonthEntries(username, yearMonth.getYear(), yearMonth.getMonthValue());

            // Create a map of new entries by date
            Map<LocalDate, WorkTimeTable> newEntriesMap = monthEntries.stream()
                    .collect(Collectors.toMap(WorkTimeTable::getWorkDate, entry -> entry));

            // Filter out existing entries that will be replaced
            List<WorkTimeTable> remainingEntries = existingEntries.stream()
                    .filter(entry -> !shouldReplaceEntry(entry, newEntriesMap.get(entry.getWorkDate())))
                    .collect(Collectors.toList());

            // Add new entries
            remainingEntries.addAll(monthEntries);

            // Sort entries by date
            remainingEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));

            // Save updated entries
            saveMonthEntries(username, yearMonth.getYear(), yearMonth.getMonthValue(), remainingEntries);
        });
    }

    private boolean shouldReplaceEntry(WorkTimeTable existing, WorkTimeTable newEntry) {
        if (newEntry == null) return false;

        // Always replace ADMIN_BLANK entries
        if (SyncStatus.ADMIN_BLANK.equals(existing.getAdminSync())) {
            return true;
        }

        // Don't replace USER_EDITED entries
        if (SyncStatus.USER_EDITED.equals(existing.getAdminSync())) {
            return false;
        }

        // Replace if same date
        return existing.getWorkDate().equals(newEntry.getWorkDate());
    }

    private void resetWorkFields(WorkTimeTable entry) {
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);
    }

    private List<WorkTimeTable> loadMonthEntries(String username, int year, int month) {
        try {
            // Read local worktime entries, isAdmin=false because this is user context
            List<WorkTimeTable> entries = dataAccessService.readUserWorktime(username, year, month);

            // Handle null result from DataAccessService
            if (entries == null) {
                entries = new ArrayList<>();
            }

            return entries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading worktime entries for %s - %d/%d: %s",
                            username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    public List<WorkTimeTable> getUpcomingTimeOff(User user) {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();
        YearMonth nextMonth = currentMonth.plusMonths(1);

        // Get entries for current and next month - with null checks
        List<WorkTimeTable> currentMonthEntries = loadMonthEntries(user.getUsername(),
                currentMonth.getYear(), currentMonth.getMonthValue());
        List<WorkTimeTable> nextMonthEntries = loadMonthEntries(user.getUsername(),
                nextMonth.getYear(), nextMonth.getMonthValue());

        // Both lists are now guaranteed to not be null due to loadMonthEntries
        List<WorkTimeTable> allEntries = new ArrayList<>();
        allEntries.addAll(currentMonthEntries);
        allEntries.addAll(nextMonthEntries);

        return allEntries.stream()
                .filter(entry -> entry.getTimeOffType() != null)  // Only time off entries
                .filter(entry -> !entry.getTimeOffType().equals(WorkCode.NATIONAL_HOLIDAY_CODE))  // Exclude national holidays
                .filter(entry -> entry.getWorkDate().isAfter(today))  // Only future dates
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                .collect(Collectors.toList());
    }

    private void saveMonthEntries(String username, int year, int month, List<WorkTimeTable> entries) {
        try {
            // Write to local and sync to network using DataAccessService
            dataAccessService.writeUserWorktime(username, entries, year, month);

            LoggerUtil.info(this.getClass(),
                    String.format("Saved %d worktime entries for %s - %d/%d",
                            entries.size(), username, year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error saving worktime entries for %s - %d/%d: %s",
                            username, year, month, e.getMessage()));
            throw new RuntimeException("Failed to save time off entries", e);
        }
    }

    public List<WorkTimeTable> getUserTimeOffHistoryReadOnly(String username, Integer year) {
        LoggerUtil.info(this.getClass(),
                String.format("Fetching time off history for user %s for year %d", username, year));

        // Use read-only method from DataAccessService
        return dataAccessService.readTimeOffReadOnly(username, year);
    }

    //Gets time off records for status display using optimized read-only operations
    public List<WorkTimeTable> getUserTimeOffHistoryReadOnly(String username, int year) {
        LoggerUtil.info(this.getClass(),
                String.format("Fetching time off history for user %s for year %d", username, year));

        // Use read-only method from DataAccessService to get all worktime entries
        List<WorkTimeTable> timeOffEntries = dataAccessService.readTimeOffReadOnly(username, year);

        // Sort descending by date
        return timeOffEntries.stream()
                .sorted((a, b) -> b.getWorkDate().compareTo(a.getWorkDate()))
                .collect(Collectors.toList());
    }

    // Calculate time off summary for status display using optimized read-only operations
    public TimeOffSummary calculateTimeOffSummaryReadOnly(String username, int year) {
        List<WorkTimeTable> timeOffEntries = getUserTimeOffHistoryReadOnly(username, year);

        // Count different time off types
        int snDays = 0, coDays = 0, cmDays = 0;

        for (WorkTimeTable entry : timeOffEntries) {
            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case "SN" -> snDays++;
                    case "CO" -> coDays++;
                    case "CM" -> cmDays++;
                }
            }
        }

        // Get available paid days - use a simplified version without writing to holiday file
        int availablePaidDays = 21;  // Default value
        int remainingPaidDays = availablePaidDays - coDays;
        if (remainingPaidDays < 0) {
            remainingPaidDays = 0;
        }

        // Build and return the summary
        return TimeOffSummary.builder()
                .snDays(snDays)
                .coDays(coDays)
                .cmDays(cmDays)
                .availablePaidDays(availablePaidDays)
                .paidDaysTaken(coDays)
                .remainingPaidDays(remainingPaidDays)
                .build();
    }
}