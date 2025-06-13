package com.ctgraphdep.worktime.display;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeCountsDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.WorkTimeEntryUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ENHANCED WorktimeDisplayService - Handles both worktime and timeoff display.
 * Consolidates display logic for the unified time management page.
 * Key Enhancements:
 * - Combined worktime + timeoff data preparation
 * - Uses new WorktimeOperationService for data loading
 * - Integrated holiday balance display
 * - Consistent display formatting
 */

@Service
public class WorktimeDisplayService {

    private final WorktimeOperationService worktimeOperationService;
    private final TimeOffCacheService timeOffCacheService;

    @Autowired
    public WorktimeDisplayService(
            WorktimeOperationService worktimeOperationService,
            TimeOffCacheService timeOffCacheService) {
        this.worktimeOperationService = worktimeOperationService;
        this.timeOffCacheService = timeOffCacheService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // COMBINED DISPLAY DATA PREPARATION
    // ========================================================================

    /**
     * NEW: Prepare combined worktime + timeoff display data for unified page
     */
    @PreAuthorize("#user.username == authentication.name or hasRole('ADMIN')")
    public Map<String, Object> prepareCombinedDisplayData(User user, int year, int month) {
        validateInput(user, year, month);

        LoggerUtil.info(this.getClass(), String.format(
                "Preparing combined display data for user %s, %d/%d", user.getUsername(), month, year));

        try {

            // Load worktime data using new service
            List<WorkTimeTable> worktimeData = worktimeOperationService.loadUserWorktime(
                    user.getUsername(), year, month);

            // Prepare worktime display data
            Map<String, Object> worktimeDisplayData = prepareWorktimeDisplayData(user, worktimeData, year, month);

            // Prepare timeoff display data
            TimeOffDisplayData timeOffDisplayData = prepareTimeOffDisplayData(user.getUsername(), user.getUserId(), year);

            // Combine all data
            Map<String, Object> displayData = new HashMap<>(worktimeDisplayData);
            displayData.put("timeOffSummary", timeOffDisplayData.getSummary());
            displayData.put("upcomingTimeOff", timeOffDisplayData.getUpcomingTimeOff());
            displayData.put("timeOffHistory", timeOffDisplayData.getRecentHistory());

            // Add current system time for UI
            displayData.put("currentSystemTime", LocalDate.now().atStartOfDay().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // Add date constraints for time off requests
            LocalDate today = LocalDate.now();
            displayData.put("today", today.toString());
            displayData.put("minDate", today.minusDays(7).toString());
            displayData.put("maxDate", today.plusMonths(6).toString());

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully prepared combined data for %s - %d worktime entries, %d available time off days",
                    user.getUsername(), worktimeData.size(), timeOffDisplayData.getSummary().getAvailablePaidDays()));

            return displayData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error preparing combined display data for user %s: %s", user.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to prepare combined display data", e);
        }
    }

    /**
     * EXISTING: Process worktime data for display (enhanced)
     */
    @PreAuthorize("#user.username == authentication.name or hasRole('ADMIN')")
    public Map<String, Object> prepareWorktimeDisplayData(User user, List<WorkTimeTable> worktimeData, int year, int month) {
        validateInput(user, worktimeData, year, month);

        LoggerUtil.info(this.getClass(), String.format(
                "Preparing worktime display data for user %s, %d/%d", user.getUsername(), month, year));

        try {
            Map<String, Object> displayData = new HashMap<>();

            // Filter entries for display
            List<WorkTimeTable> displayableEntries = filterEntriesForDisplay(worktimeData);

            // Get holiday balance using new service
            int holidayBalance = worktimeOperationService.getHolidayBalance(user.getUsername());

            // Calculate summary
            WorkTimeSummary summary = calculateMonthSummary(displayableEntries, year, month, holidayBalance);

            // Convert to DTOs with pre-calculated values
            int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8; // Default to 8 hours
            List<WorkTimeEntryDTO> entryDTOs = displayableEntries.stream()
                    .map(entry -> WorkTimeEntryDTO.fromWorkTimeTable(entry, userSchedule))
                    .collect(Collectors.toList());

            WorkTimeSummaryDTO summaryDTO = WorkTimeSummaryDTO.fromWorkTimeSummary(summary);

            // Prepare display data
            displayData.put("worktimeData", entryDTOs);
            displayData.put("currentYear", year);
            displayData.put("currentMonth", month);
            displayData.put("user", sanitizeUserData(user));
            displayData.put("summary", summaryDTO);

            LoggerUtil.info(this.getClass(), String.format(
                    "Prepared worktime display data with %d entries for user %s", entryDTOs.size(), user.getUsername()));

            return displayData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error preparing worktime display data for user %s: %s", user.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to prepare worktime display data", e);
        }
    }

    /**
     * NEW: Prepare time off display data
     */
    public TimeOffDisplayData prepareTimeOffDisplayData(String username, Integer userId, int year) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Preparing time off display data for %s - %d", username, year));

            // Get time off summary from cache
            TimeOffSummaryDTO summary = timeOffCacheService.getSummary(username, year);

            // Get upcoming time off
            List<WorkTimeTable> upcomingTimeOff = timeOffCacheService.getUpcomingTimeOff(username, userId, year);

            // Get recent history (last 6 months)
            List<WorkTimeTable> recentHistory = getRecentTimeOffHistory(username, userId, year);

            return new TimeOffDisplayData(summary, upcomingTimeOff, recentHistory);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error preparing time off display data for %s - %d: %s", username, year, e.getMessage()));

            // Return empty data on error
            TimeOffSummaryDTO emptySummary = TimeOffSummaryDTO.builder()
                    .coDays(0).snDays(0).cmDays(0)
                    .paidDaysTaken(0).remainingPaidDays(0).availablePaidDays(0)
                    .build();

            return new TimeOffDisplayData(emptySummary, new ArrayList<>(), new ArrayList<>());
        }
    }

    // ========================================================================
    // ADMIN DISPLAY METHODS (EXISTING, ENHANCED)
    // ========================================================================

    /**
     * EXISTING: Prepare day headers for admin display
     */
    public List<Map<String, String>> prepareDayHeaders(YearMonth yearMonth) {
        WorkTimeEntryUtil.validateYearMonth(yearMonth);

        List<Map<String, String>> dayHeaders = new ArrayList<>();

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            Map<String, String> headerInfo = new HashMap<>();

            headerInfo.put("day", String.valueOf(day));
            headerInfo.put("initial", WorkCode.ROMANIAN_DAY_INITIALS.get(date.getDayOfWeek()));
            headerInfo.put("isWeekend", WorkTimeEntryUtil.isDateWeekend(date) ? "true" : "false");
            dayHeaders.add(headerInfo);
        }

        LoggerUtil.debug(this.getClass(), String.format(
                "Prepared %d day headers for %s", dayHeaders.size(), yearMonth));

        return dayHeaders;
    }

    /**
     * EXISTING: Calculate work time summaries for admin view
     */
    public Map<Integer, WorkTimeSummary> calculateUserSummaries(
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap,
            List<User> users,
            Integer year,
            Integer month) {

        WorkTimeEntryUtil.validateYearMonth(YearMonth.of(year, month));
        Map<Integer, WorkTimeSummary> summaries = new HashMap<>();

        users.forEach(user -> {
            Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.getOrDefault(
                    user.getUserId(), new HashMap<>());
            WorkTimeSummary summary = calculateUserSummary(userEntries, year, month, user.getSchedule());
            summaries.put(user.getUserId(), summary);
        });

        LoggerUtil.debug(this.getClass(), String.format(
                "Calculated summaries for %d users for %d/%d", users.size(), month, year));

        return summaries;
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Filter entries for display (hide certain statuses)
     */
    private List<WorkTimeTable> filterEntriesForDisplay(List<WorkTimeTable> entries) {
        return entries.stream()
                .filter(WorkTimeEntryUtil::isEntryDisplayable)
                .map(this::prepareEntryForDisplay)
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                .toList();
    }

    /**
     * Prepare individual entry for display
     */
    private WorkTimeTable prepareEntryForDisplay(WorkTimeTable entry) {
        WorkTimeTable displayEntry = WorkTimeEntryUtil.copyWorkTimeEntry(entry);

        // For USER_IN_PROCESS entries, show limited information
        if (SyncStatusMerge.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            // Keep available information but hide incomplete data
            if (displayEntry.getTotalWorkedMinutes() == null || displayEntry.getTotalWorkedMinutes() == 0) {
                displayEntry.setTotalWorkedMinutes(null);
            }

            if (displayEntry.getTotalOvertimeMinutes() == null || displayEntry.getTotalOvertimeMinutes() == 0) {
                displayEntry.setTotalOvertimeMinutes(null);
            }

            // Always hide end time for in-process entries
            displayEntry.setDayEndTime(null);

            // Don't apply lunch break unless explicitly set
            if (!displayEntry.isLunchBreakDeducted()) {
                displayEntry.setLunchBreakDeducted(false);
            }
        }

        return displayEntry;
    }

    /**
     * Calculate monthly summary
     */
    private WorkTimeSummary calculateMonthSummary(List<WorkTimeTable> worktimeData, int year, int month, int holidayBalance) {
        try {
            int totalWorkDays = CalculateWorkHoursUtil.calculateWorkDays(year, month);
            WorkTimeCountsDTO counts = calculateWorkTimeCounts(worktimeData);

            return WorkTimeSummary.builder()
                    .totalWorkDays(totalWorkDays)
                    .daysWorked(counts.getDaysWorked())
                    .remainingWorkDays(totalWorkDays - (counts.getDaysWorked() + counts.getSnDays() + counts.getCoDays() + counts.getCmDays()))
                    .snDays(counts.getSnDays())
                    .coDays(counts.getCoDays())
                    .cmDays(counts.getCmDays())
                    .totalRegularMinutes(counts.getRegularMinutes())
                    .totalOvertimeMinutes(counts.getOvertimeMinutes())
                    .totalMinutes(counts.getRegularMinutes() + counts.getOvertimeMinutes())
                    .discardedMinutes(counts.getDiscardedMinutes())
                    .availablePaidDays(holidayBalance)
                    .build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating month summary", e);
            throw e;
        }
    }

    /**
     * Calculate work time counts for summary
     */
    private WorkTimeCountsDTO calculateWorkTimeCounts(List<WorkTimeTable> worktimeData) {
        WorkTimeCountsDTO counts = new WorkTimeCountsDTO();
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalDiscardedMinutes = 0;

        for (WorkTimeTable entry : worktimeData) {
            // Skip in-process entries
            if (SyncStatusMerge.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case "SN" -> counts.incrementSnDays();
                    case "CO" -> counts.incrementCoDays();
                    case "CM" -> counts.incrementCmDays();
                }
            } else if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                counts.incrementDaysWorked();

                // Use default schedule if not available
                int userSchedule = 8;

                // Use calculation utility for consistency
                WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(
                        entry.getTotalWorkedMinutes(), userSchedule);

                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += result.getOvertimeMinutes();

                // Calculate discarded minutes
                int adjustedMinutes = CalculateWorkHoursUtil.calculateAdjustedMinutes(
                        entry.getTotalWorkedMinutes(), userSchedule);
                int discardedMinutes = adjustedMinutes % 60;
                totalDiscardedMinutes += discardedMinutes;
            }
        }

        counts.setRegularMinutes(totalRegularMinutes);
        counts.setOvertimeMinutes(totalOvertimeMinutes);
        counts.setDiscardedMinutes(totalDiscardedMinutes);

        return counts;
    }

    /**
     * Calculate summary for single user (admin view)
     */
    private WorkTimeSummary calculateUserSummary(Map<LocalDate, WorkTimeTable> entries, Integer year, Integer month, Integer schedule) {
        int totalWorkDays = CalculateWorkHoursUtil.calculateWorkDays(year, month);

        int daysWorked = 0;
        int snDays = 0;
        int coDays = 0;
        int cmDays = 0;
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;

        // Process each entry
        for (WorkTimeTable entry : entries.values()) {
            if (entry.getTimeOffType() != null && !entry.getTimeOffType().equals("BLANK")) {
                switch (entry.getTimeOffType()) {
                    case WorkCode.NATIONAL_HOLIDAY_CODE -> snDays++;
                    case WorkCode.TIME_OFF_CODE -> coDays++;
                    case WorkCode.MEDICAL_LEAVE_CODE -> cmDays++;
                }
            } else if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                daysWorked++;
                var result = CalculateWorkHoursUtil.calculateWorkTime(entry.getTotalWorkedMinutes(), schedule);
                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += result.getOvertimeMinutes();
            }
        }

        // Calculate remaining work days
        int remainingWorkDays = totalWorkDays - (daysWorked + snDays + coDays + cmDays);

        return WorkTimeSummary.builder()
                .totalWorkDays(totalWorkDays)
                .daysWorked(daysWorked)
                .remainingWorkDays(remainingWorkDays)
                .snDays(snDays)
                .coDays(coDays)
                .cmDays(cmDays)
                .totalRegularMinutes(totalRegularMinutes)
                .totalOvertimeMinutes(totalOvertimeMinutes)
                .totalMinutes(totalRegularMinutes + totalOvertimeMinutes)
                .build();
    }

    /**
     * Get recent time off history
     */
    private List<WorkTimeTable> getRecentTimeOffHistory(String username, Integer userId, int year) {
        try {
            // Get last 6 months of time off data
            List<WorkTimeTable> history = new ArrayList<>();
            LocalDate startDate = LocalDate.of(year, 1, 1).minusMonths(6);
            LocalDate endDate = LocalDate.of(year, 12, 31);

            // This would typically load from worktime files for the date range
            // For now, just get current year data from cache
            List<WorkTimeTable> currentYearTimeOff = timeOffCacheService.getUpcomingTimeOff(username, userId, year);

            return currentYearTimeOff.stream()
                    .filter(entry -> !entry.getWorkDate().isAfter(LocalDate.now()))
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate).reversed())
                    .limit(10) // Last 10 entries
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error getting recent time off history for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Validate input parameters
     */
    private void validateInput(User user, int year, int month) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }
    }

    /**
     * Validate input with worktime data
     */
    private void validateInput(User user, List<WorkTimeTable> worktimeData, int year, int month) {
        validateInput(user, year, month);

        if (worktimeData == null) {
            throw new IllegalArgumentException("Worktime data cannot be null");
        }

        if (worktimeData.stream().anyMatch(entry -> !entry.getUserId().equals(user.getUserId()))) {
            throw new SecurityException("Worktime data contains entries for other users");
        }
    }

    /**
     * Sanitize user data for display
     */
    private User sanitizeUserData(User user) {
        User sanitized = new User();
        sanitized.setUserId(user.getUserId());
        sanitized.setName(user.getName());
        sanitized.setUsername(user.getUsername());
        sanitized.setEmployeeId(user.getEmployeeId());
        sanitized.setSchedule(user.getSchedule());
        sanitized.setPaidHolidayDays(user.getPaidHolidayDays());
        return sanitized;
    }

    // ========================================================================
    // INNER CLASSES
    // ========================================================================

    /**
     * Container for time off display data
     */
    @Getter
    public static class TimeOffDisplayData {
        private final TimeOffSummaryDTO summary;
        private final List<WorkTimeTable> upcomingTimeOff;
        private final List<WorkTimeTable> recentHistory;

        public TimeOffDisplayData(TimeOffSummaryDTO summary, List<WorkTimeTable> upcomingTimeOff, List<WorkTimeTable> recentHistory) {
            this.summary = summary;
            this.upcomingTimeOff = upcomingTimeOff;
            this.recentHistory = recentHistory;
        }
    }
}