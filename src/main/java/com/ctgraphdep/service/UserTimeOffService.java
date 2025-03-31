package com.ctgraphdep.service;

import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing user time off requests.
 * This service orchestrates both worktime files and the time off tracker.
 */
@Service
public class UserTimeOffService {
    private final DataAccessService dataAccessService;
    private final HolidayManagementService holidayService;
    private final UserWorkTimeService userWorkTimeService;
    private final UserService userService;
    private final TimeOffTrackerService timeOffTrackerService;

    public UserTimeOffService(DataAccessService dataAccessService,
                              HolidayManagementService holidayService,
                              UserWorkTimeService userWorkTimeService,
                              UserService userService,
                              TimeOffTrackerService timeOffTrackerService) {
        this.dataAccessService = dataAccessService;
        this.holidayService = holidayService;
        this.userWorkTimeService = userWorkTimeService;
        this.userService = userService;
        this.timeOffTrackerService = timeOffTrackerService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Process a time-off request for a user.
     * Creates individual time-off entries for each eligible day.
     */
    @Transactional
    public void processTimeOffRequest(User user, LocalDate startDate, LocalDate endDate, String timeOffType) {
        LoggerUtil.info(this.getClass(),
                String.format("Processing time off request for user %s: %s to %s (%s)",
                        user.getUsername(), startDate, endDate, timeOffType));

        validateTimeOffRequest(startDate, endDate, timeOffType);

        // Get a list of all eligible days (excluding weekends, holidays, and existing time off)
        List<LocalDate> eligibleDays = getEligibleDays(user.getUserId(), startDate, endDate);

        if (eligibleDays.isEmpty()) {
            throw new IllegalArgumentException("No eligible days found in the selected date range");
        }

        // Step 1: Create and save time off entries in worktime files
        List<WorkTimeTable> entries = createTimeOffEntries(user, eligibleDays, timeOffType);
        saveTimeOffEntries(user.getUsername(), entries);

        // Step 2: Update the tracker (which also updates the holiday balance)
        timeOffTrackerService.addTimeOffRequests(user, eligibleDays, timeOffType);

        LoggerUtil.info(this.getClass(),
                String.format("Processed %d time off entries for user %s",
                        entries.size(), user.getUsername()));
    }

    /**
     * Sync the time off tracker with worktime files for a given year
     */
    public void syncTimeOffTracker(User user, int year) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Syncing time off tracker for user %s (year %d)",
                            user.getUsername(), year));

            // Get all time off entries from worktime files
            List<WorkTimeTable> allTimeOffEntries = loadAllTimeOffEntries(user.getUsername(), year);

            // Pass these entries to the tracker service for syncing
            timeOffTrackerService.syncWithWorktimeFiles(user, year, allTimeOffEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error syncing time off tracker for user %s (year %d): %s",
                            user.getUsername(), year, e.getMessage()));
        }
    }

    /**
     * Gets time-off entries for the entire year using read-only operations.
     * More efficient than getUserTimeOffHistory for display purposes.
     */
    public List<WorkTimeTable> getUserTimeOffHistoryReadOnly(String username, int year) {
        LoggerUtil.info(this.getClass(),
                String.format("Fetching time off history for user %s for year %d (read-only)",
                        username, year));

        // Use read-only method from DataAccessService to get all worktime entries
        List<WorkTimeTable> timeOffEntries = dataAccessService.readTimeOffReadOnly(username, year);

        // Sort descending by date
        return timeOffEntries.stream()
                .sorted((a, b) -> b.getWorkDate().compareTo(a.getWorkDate()))
                .collect(Collectors.toList());
    }

    /**
     * Gets upcoming time-off entries for a user.
     * Uses the tracker to determine upcoming days and converts them to WorkTimeTable entries.
     */
    public List<WorkTimeTable> getUpcomingTimeOff(User user) {
        // Get tracker requests
        List<TimeOffTracker.TimeOffRequest> upcomingRequests =
                timeOffTrackerService.getUpcomingTimeOffRequests(user);

        // Convert to WorkTimeTable entries for compatibility with existing UI
        List<WorkTimeTable> result = new ArrayList<>();

        for (TimeOffTracker.TimeOffRequest request : upcomingRequests) {
            WorkTimeTable entry = new WorkTimeTable();
            entry.setUserId(user.getUserId());
            entry.setWorkDate(request.getDate());
            entry.setTimeOffType(request.getTimeOffType());
            entry.setAdminSync(SyncStatusWorktime.USER_INPUT);

            // Reset work-related fields
            resetWorkFields(entry);

            result.add(entry);
        }

        // Sort by date
        return result.stream()
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                .collect(Collectors.toList());
    }

    /**
     * Calculate time-off summary using read-only operations.
     * More efficient than calculateTimeOffSummary for display purposes.
     */
    public TimeOffSummaryDTO calculateTimeOffSummaryReadOnly(String username, int year) {
        // First try to get user information
        Optional<User> userOpt = userService.getUserByUsername(username);
        if (userOpt.isEmpty()) {
            LoggerUtil.warn(this.getClass(), "User not found for summary calculation: " + username);
            return createDefaultSummary();
        }

        User user = userOpt.get();

        try {
            // Try to get from tracker first (most accurate)
            TimeOffTracker tracker = dataAccessService.readTimeOffTracker(username, user.getUserId(), year);

            if (tracker != null) {
                // Count by type from approved requests
                int snDays = 0, coDays = 0, cmDays = 0;

                for (TimeOffTracker.TimeOffRequest request : tracker.getRequests()) {
                    if (!"APPROVED".equals(request.getStatus())) {
                        continue;
                    }

                    switch (request.getTimeOffType()) {
                        case "SN" -> snDays++;
                        case "CO" -> coDays++;
                        case "CM" -> cmDays++;
                    }
                }

                int availablePaidDays = tracker.getAvailableHolidayDays();
                int usedPaidDays = tracker.getUsedHolidayDays();

                return TimeOffSummaryDTO.builder()
                        .snDays(snDays)
                        .coDays(coDays)
                        .cmDays(cmDays)
                        .availablePaidDays(availablePaidDays + usedPaidDays)
                        .paidDaysTaken(usedPaidDays)
                        .remainingPaidDays(availablePaidDays)
                        .build();
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    String.format("Error reading tracker, falling back to worktime files: %s",
                            e.getMessage()));
        }

        // Fallback to worktime files if tracker not available
        List<WorkTimeTable> timeOffEntries = getUserTimeOffHistoryReadOnly(username, year);
        TimeOffTypeCounts counts = countTimeOffTypes(timeOffEntries);

        int availablePaidDays;
        int remainingPaidDays = 0;

        try {
            // Get available paid days from holiday service
            availablePaidDays = holidayService.getRemainingHolidayDays(username, user.getUserId());

            // For historical years, just show the used days
            if (year < Year.now().getValue()) {
                availablePaidDays = counts.coDays;
            } else {
                // For current year, use the actual remaining days
                remainingPaidDays = Math.max(0, availablePaidDays - counts.coDays);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error getting paid holiday days for %s: %s",
                            username, e.getMessage()));
            availablePaidDays = counts.coDays;
        }

        return TimeOffSummaryDTO.builder()
                .snDays(counts.snDays)
                .coDays(counts.coDays)
                .cmDays(counts.cmDays)
                .availablePaidDays(availablePaidDays)
                .paidDaysTaken(counts.coDays)
                .remainingPaidDays(remainingPaidDays)
                .build();
    }

    /**
     * Creates a default summary with zeroed values
     */
    private TimeOffSummaryDTO createDefaultSummary() {
        return TimeOffSummaryDTO.builder()
                .snDays(0)
                .coDays(0)
                .cmDays(0)
                .availablePaidDays(21)
                .paidDaysTaken(0)
                .remainingPaidDays(21)
                .build();
    }

    /**
     * Count the number of days of each time-off type in a list of entries.
     */
    private TimeOffTypeCounts countTimeOffTypes(List<WorkTimeTable> entries) {
        int snDays = 0, coDays = 0, cmDays = 0;

        for (WorkTimeTable entry : entries) {
            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case "SN" -> snDays++;
                    case "CO" -> coDays++;
                    case "CM" -> cmDays++;
                }
            }
        }

        return new TimeOffTypeCounts(snDays, coDays, cmDays);
    }

    /**
     * Validate a time-off request.
     */
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

    /**
     * Get a list of eligible days for time off in a date range.
     */
    private List<LocalDate> getEligibleDays(Integer userId, LocalDate startDate, LocalDate endDate) {
        List<LocalDate> eligibleDays = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            if (isEligibleForTimeOff(userId, currentDate)) {
                eligibleDays.add(currentDate);
            }
            currentDate = currentDate.plusDays(1);
        }

        return eligibleDays;
    }

    /**
     * Check if a specific date is eligible for time-off.
     */
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
            return SyncStatusWorktime.ADMIN_BLANK.equals(existingEntry.getAdminSync());
        }

        return true;
    }

    /**
     * Load all time off entries from worktime files for a specific year
     */
    private List<WorkTimeTable> loadAllTimeOffEntries(String username, int year) {
        List<WorkTimeTable> allTimeOffEntries = new ArrayList<>();

        // Get current date to determine which months to process
        LocalDate currentDate = LocalDate.now();
        int currentYear = currentDate.getYear();
        int currentMonth = currentDate.getMonthValue();

        // Process each month in the year
        for (int month = 1; month <= 12; month++) {
            // Skip future months (except current month)
            if (year > currentYear || (year == currentYear && month > currentMonth)) {
                continue;
            }

            try {
                // Load worktime entries for the month
                List<WorkTimeTable> entries = userWorkTimeService.loadMonthWorktime(username, year, month);

                if (entries != null) {
                    // Filter for time off entries only
                    List<WorkTimeTable> timeOffEntries = entries.stream()
                            .filter(entry -> entry.getTimeOffType() != null)
                            .toList();

                    allTimeOffEntries.addAll(timeOffEntries);
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Error loading worktime for %s - %d/%d: %s",
                                username, year, month, e.getMessage()));
            }
        }

        return allTimeOffEntries;
    }

    /**
     * Create time-off entries for the provided eligible days.
     */
    private List<WorkTimeTable> createTimeOffEntries(User user, List<LocalDate> eligibleDays, String timeOffType) {
        List<WorkTimeTable> entries = new ArrayList<>();

        for (LocalDate date : eligibleDays) {
            WorkTimeTable existingEntry = getExistingEntry(user.getUserId(), date);
            WorkTimeTable entry = createTimeOffEntry(user, date, timeOffType, existingEntry);
            entries.add(entry);
        }

        return entries;
    }

    /**
     * Get an existing entry for a specific date.
     */
    private WorkTimeTable getExistingEntry(Integer userId, LocalDate date) {
        try {
            // First get username from userId
            Optional<User> userOpt = userService.getUserById(userId);
            if (userOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(),
                        String.format("User not found for ID: %d when checking existing entry", userId));
                return null;
            }

            String username = userOpt.get().getUsername();
            YearMonth yearMonth = YearMonth.from(date);

            // Now use the username instead of the userId as string
            List<WorkTimeTable> existingEntries = loadMonthEntries(
                    username, yearMonth.getYear(), yearMonth.getMonthValue());

            return existingEntries.stream()
                    .filter(entry -> entry.getWorkDate().equals(date))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error getting existing entry for user ID %d on date %s: %s",
                            userId, date, e.getMessage()));
            return null;
        }
    }

    /**
     * Create a single time-off entry.
     */
    private WorkTimeTable createTimeOffEntry(User user, LocalDate date, String timeOffType, WorkTimeTable existingEntry) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(user.getUserId());
        entry.setWorkDate(date);
        entry.setTimeOffType(timeOffType);

        // Set appropriate status based on existing entry
        if (existingEntry != null && SyncStatusWorktime.ADMIN_BLANK.equals(existingEntry.getAdminSync())) {
            entry.setAdminSync(SyncStatusWorktime.USER_EDITED); // New entry over ADMIN_BLANK
        } else {
            entry.setAdminSync(SyncStatusWorktime.USER_INPUT); // Normal new entry
        }

        resetWorkFields(entry);
        return entry;
    }

    /**
     * Reset work-related fields for a time-off entry.
     */
    private void resetWorkFields(WorkTimeTable entry) {
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTotalOvertimeMinutes(0);
    }

    /**
     * Save time-off entries, handling month boundaries correctly.
     */
    private void saveTimeOffEntries(String username, List<WorkTimeTable> newEntries) {
        // Group entries by month for processing
        Map<YearMonth, List<WorkTimeTable>> entriesByMonth = newEntries.stream()
                .collect(Collectors.groupingBy(entry -> YearMonth.from(entry.getWorkDate())));

        entriesByMonth.forEach((yearMonth, monthEntries) -> {
            // Get existing entries for this month
            List<WorkTimeTable> existingEntries = loadMonthEntries(
                    username, yearMonth.getYear(), yearMonth.getMonthValue());

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


    /**
     * Determine if an existing entry should be replaced by a new one.
     */
    private boolean shouldReplaceEntry(WorkTimeTable existing, WorkTimeTable newEntry) {
        if (newEntry == null) return false;

        // Always replace ADMIN_BLANK entries
        if (SyncStatusWorktime.ADMIN_BLANK.equals(existing.getAdminSync())) {
            return true;
        }

        // Don't replace USER_EDITED entries
        if (SyncStatusWorktime.USER_EDITED.equals(existing.getAdminSync())) {
            return false;
        }

        // Replace if same date
        return existing.getWorkDate().equals(newEntry.getWorkDate());
    }

    /**
     * Load worktime entries for a specific month.
     */
    private List<WorkTimeTable> loadMonthEntries(String username, int year, int month) {
        try {
            // Read worktime entries using UserWorkTimeService for proper merging of admin and user entries
            List<WorkTimeTable> entries = userWorkTimeService.loadMonthWorktime(username, year, month);

            // Handle null result
            if (entries == null) {
                entries = new ArrayList<>();
            }

            return entries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading worktime entries for %s - %d/%d: %s", username, year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Save worktime entries for a specific month.
     */
    private void saveMonthEntries(String username, int year, int month, List<WorkTimeTable> entries) {
        try {
            // Use UserWorkTimeService to handle proper updates including admin entries
            userWorkTimeService.saveWorkTimeEntries(username, entries);

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

    /**
     * Helper class to hold time-off type counts.
     */
    private record TimeOffTypeCounts(int snDays, int coDays, int cmDays) {
    }
}