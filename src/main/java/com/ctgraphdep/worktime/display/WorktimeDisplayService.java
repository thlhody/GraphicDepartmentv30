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
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.WorkTimeEntryUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    private final AllUsersCacheService allUsersCacheService;

    @Autowired
    public WorktimeDisplayService(
            WorktimeOperationService worktimeOperationService,
            TimeOffCacheService timeOffCacheService, AllUsersCacheService allUsersCacheService) {
        this.worktimeOperationService = worktimeOperationService;
        this.timeOffCacheService = timeOffCacheService;
        this.allUsersCacheService = allUsersCacheService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // COMBINED DISPLAY DATA PREPARATION
    // ========================================================================

    /**
     * ENHANCED: Prepare combined worktime + timeoff display data for unified page
     */
    @PreAuthorize("#user.username == authentication.name or hasRole('ADMIN')")
    public Map<String, Object> prepareCombinedDisplayData(User user, int year, int month) {
        validateInput(user, year, month);

        LoggerUtil.info(this.getClass(), String.format(
                "Preparing combined display data for user %s, %d/%d", user.getUsername(), month, year));

        try {
            // Get month summary with SN overtime support
            WorkTimeSummaryDTO monthSummary = prepareMonthSummary(user, year, month);

            // Get recent time off history
            List<WorkTimeTable> recentTimeOff = getRecentTimeOffHistory(user.getUsername(), user.getUserId(), year);

            // Prepare display data map
            Map<String, Object> displayData = new HashMap<>();
            displayData.put("user", user);
            displayData.put("currentYear", year);
            displayData.put("currentMonth", month);
            displayData.put("monthSummary", monthSummary);
            displayData.put("recentTimeOff", recentTimeOff);

            LoggerUtil.info(this.getClass(), String.format(
                    "Combined display data prepared successfully: %d entries, %d overtime minutes total",
                    monthSummary.getEntries().size(), monthSummary.getTotalOvertimeMinutes()));

            return displayData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing combined display data", e);
            throw e;
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
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap, List<User> users, Integer year, Integer month) {

        WorkTimeEntryUtil.validateYearMonth(YearMonth.of(year, month));
        Map<Integer, WorkTimeSummary> summaries = new HashMap<>();

        users.forEach(user -> {
            Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.getOrDefault(user.getUserId(), new HashMap<>());
            WorkTimeSummary summary = calculateUserSummary(userEntries, year, month, user.getSchedule());
            summaries.put(user.getUserId(), summary);
        });

        LoggerUtil.debug(this.getClass(), String.format("Calculated summaries for %d users for %d/%d", users.size(), month, year));

        return summaries;
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================
    /**
     * ENHANCED: Prepare month summary with proper SN overtime inclusion
     */
    public WorkTimeSummaryDTO prepareMonthSummary(User user, int year, int month) {
        validateInput(user, year, month);

        LoggerUtil.info(this.getClass(), String.format(
                "Preparing month summary for user %s, %d/%d with SN overtime support",
                user.getUsername(), month, year));

        try {
            // Load worktime data using new service
            List<WorkTimeTable> worktimeData = worktimeOperationService.loadUserWorktime(
                    user.getUsername(), year, month);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Loaded %d worktime entries for processing", worktimeData.size()));

            // Calculate work time counts (includes SN overtime)
            WorkTimeCountsDTO counts = calculateWorkTimeCounts(worktimeData);

            // Get holiday balance
            Integer holidayBalance = worktimeOperationService.getHolidayBalance(user.getUsername());

            // Convert to display entries with proper SN handling
            List<WorkTimeEntryDTO> displayEntries = convertToDisplayEntries(worktimeData);

            LoggerUtil.info(this.getClass(), String.format(
                    "Month summary completed: %d entries, %d regular minutes, %d overtime minutes (includes SN)",
                    displayEntries.size(), counts.getRegularMinutes(), counts.getOvertimeMinutes()));

            return WorkTimeSummaryDTO.createWithEntries(displayEntries, counts, holidayBalance);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing month summary", e);
            throw e;
        }
    }

    /**
     * ENHANCED: Convert WorkTimeTable entries to display DTOs with proper SN overtime handling
     */
    private List<WorkTimeEntryDTO> convertToDisplayEntries(List<WorkTimeTable> worktimeData) {
        LoggerUtil.debug(this.getClass(), "Converting worktime entries to display DTOs");

        return worktimeData.stream()
                .map(this::convertToDisplayEntry)
                .collect(Collectors.toList());
    }

    /**
     * ENHANCED: Convert single WorkTimeTable to WorkTimeEntryDTO with SN overtime support
     */
    private WorkTimeEntryDTO convertToDisplayEntry(WorkTimeTable entry) {
        // Use default user schedule (8 hours) - could be enhanced to use actual user schedule
        int userSchedule = 8;

        // Use the enhanced DTO conversion method
        WorkTimeEntryDTO dto = WorkTimeEntryDTO.fromWorkTimeTable(entry, userSchedule);

        LoggerUtil.debug(this.getClass(), String.format(
                "Converted entry for %s: timeOff=%s, workedMinutes=%d, overtimeMinutes=%d, isSNWork=%b",
                entry.getWorkDate(), entry.getTimeOffType(),
                entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0,
                dto.isSNWorkDay()));

        return dto;
    }
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
     * ENHANCED: Calculate summary for single user (admin view) with SN overtime
     */
    private WorkTimeSummary calculateUserSummary(Map<LocalDate, WorkTimeTable> entries, Integer year, Integer month, Integer schedule) {
        int totalWorkDays = CalculateWorkHoursUtil.calculateWorkDays(year, month);

        int daysWorked = 0;
        int snDays = 0;
        int coDays = 0;
        int cmDays = 0;
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;

        LoggerUtil.debug(this.getClass(), String.format(
                "Calculating user summary for %d/%d with %d entries", month, year, entries.size()));

        // Process each entry
        for (WorkTimeTable entry : entries.values()) {
            // Handle time off types first (for day counting)
            if (entry.getTimeOffType() != null && !entry.getTimeOffType().equals("BLANK")) {
                switch (entry.getTimeOffType()) {
                    case WorkCode.NATIONAL_HOLIDAY_CODE -> snDays++;
                    case WorkCode.TIME_OFF_CODE -> coDays++;
                    case WorkCode.MEDICAL_LEAVE_CODE -> cmDays++;
                }
            }

            // Handle regular work entries (separate logic from time off)
            if (entry.getTimeOffType() == null && entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                daysWorked++;
                var result = CalculateWorkHoursUtil.calculateWorkTime(entry.getTotalWorkedMinutes(), schedule);
                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += result.getOvertimeMinutes();

                LoggerUtil.debug(this.getClass(), String.format(
                        "Regular work entry %s: %d minutes processed, %d overtime",
                        entry.getWorkDate(), result.getProcessedMinutes(), result.getOvertimeMinutes()));
            }

            // CRITICAL: Handle SN entries with overtime work
            if (WorkCode.NATIONAL_HOLIDAY_CODE.equals(entry.getTimeOffType()) &&
                    entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {

                // SN overtime goes directly to overtime totals (no regular minutes)
                totalOvertimeMinutes += entry.getTotalOvertimeMinutes();

                LoggerUtil.info(this.getClass(), String.format(
                        "Added SN overtime: %d minutes for date %s (total overtime: %d)",
                        entry.getTotalOvertimeMinutes(), entry.getWorkDate(), totalOvertimeMinutes));
            }
        }

        // Calculate remaining work days
        int remainingWorkDays = totalWorkDays - (daysWorked + snDays + coDays + cmDays);

        LoggerUtil.info(this.getClass(), String.format(
                "User summary calculated: totalWorkDays=%d, daysWorked=%d, snDays=%d, coDays=%d, cmDays=%d, remainingWorkDays=%d, totalRegular=%d, totalOvertime=%d",
                totalWorkDays, daysWorked, snDays, coDays, cmDays, remainingWorkDays, totalRegularMinutes, totalOvertimeMinutes));

        return WorkTimeSummary.builder()
                .totalWorkDays(totalWorkDays)
                .daysWorked(daysWorked)
                .remainingWorkDays(remainingWorkDays)
                .snDays(snDays)
                .coDays(coDays)
                .cmDays(cmDays)
                .totalRegularMinutes(totalRegularMinutes)
                .totalOvertimeMinutes(totalOvertimeMinutes) // Includes SN overtime
                .build();
    }

    /**
     * ENHANCED: Calculate work time counts with proper SN overtime inclusion
     */
    private WorkTimeCountsDTO calculateWorkTimeCounts(List<WorkTimeTable> worktimeData) {
        WorkTimeCountsDTO counts = new WorkTimeCountsDTO();
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalDiscardedMinutes = 0;

        LoggerUtil.debug(this.getClass(), String.format(
                "Calculating work time counts for %d entries", worktimeData.size()));

        for (WorkTimeTable entry : worktimeData) {
            // Skip in-process entries
            if (SyncStatusMerge.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Skipping in-process entry for %s", entry.getWorkDate()));
                continue;
            }

            // Handle time off types (for day counting)
            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case WorkCode.NATIONAL_HOLIDAY_CODE -> {
                        counts.incrementSnDays();
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Found SN day: %s", entry.getWorkDate()));
                    }
                    case WorkCode.TIME_OFF_CODE -> {
                        counts.incrementCoDays();
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Found CO day: %s", entry.getWorkDate()));
                    }
                    case WorkCode.MEDICAL_LEAVE_CODE -> {
                        counts.incrementCmDays();
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Found CM day: %s", entry.getWorkDate()));
                    }
                }
            }

            // ENHANCED: Handle regular work entries (separate from time off logic)
            if (entry.getTimeOffType() == null && entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
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

                LoggerUtil.debug(this.getClass(), String.format(
                        "Regular work day %s: worked=%d, regular=%d, overtime=%d, discarded=%d",
                        entry.getWorkDate(), entry.getTotalWorkedMinutes(),
                        result.getProcessedMinutes(), result.getOvertimeMinutes(), discardedMinutes));
            }

            // CRITICAL: Handle SN entries with overtime work
            if (WorkCode.NATIONAL_HOLIDAY_CODE.equals(entry.getTimeOffType()) &&
                    entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {

                // SN overtime goes directly to overtime totals (no regular minutes)
                totalOvertimeMinutes += entry.getTotalOvertimeMinutes();

                LoggerUtil.info(this.getClass(), String.format(
                        "Added SN overtime to totals: %d minutes for date %s (total overtime now: %d)",
                        entry.getTotalOvertimeMinutes(), entry.getWorkDate(), totalOvertimeMinutes));
            }
        }

        counts.setRegularMinutes(totalRegularMinutes);
        counts.setOvertimeMinutes(totalOvertimeMinutes);
        counts.setDiscardedMinutes(totalDiscardedMinutes);

        LoggerUtil.info(this.getClass(), String.format(
                "Work time counts calculated: daysWorked=%d, snDays=%d, coDays=%d, cmDays=%d, regular=%dmin, overtime=%dmin, discarded=%dmin",
                counts.getDaysWorked(), counts.getSnDays(), counts.getCoDays(), counts.getCmDays(),
                totalRegularMinutes, totalOvertimeMinutes, totalDiscardedMinutes));

        return counts;
    }
    /**
     * Get recent time off history
     */
    private List<WorkTimeTable> getRecentTimeOffHistory(String username, Integer userId, int year) {
        try {
            // Get current year data from cache
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

    /**
     * Get detailed entry information for a specific user and date
     * Used by admin worktime API to provide rich entry details
     */
    public Map<String, Object> getEntryDetails(Integer userId, Integer year, Integer month, Integer day) {
        // Validate date parameters
        LocalDate date;
        try {
            date = LocalDate.of(year, month, day);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date parameters: " + e.getMessage());
        }

        // Validate user exists
        Optional<User> userOpt = allUsersCacheService.getUserByIdAsUserObject(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found with ID: " + userId);
        }

        User user = userOpt.get();

        // FIXED: Use the SAME admin worktime file loading as the admin view
        List<WorkTimeTable> viewableEntries = worktimeOperationService.getViewableEntries(year, month);
        Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = convertToUserEntriesMap(viewableEntries);

        // Get the specific user's entries from the admin worktime data
        Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.get(userId);
        WorkTimeTable entry = null;

        if (userEntries != null) {
            entry = userEntries.get(date);
        }

        Map<String, Object> response = new HashMap<>();

        if (entry == null) {
            // No entry exists for this date
            buildNoEntryResponse(response, user, date);
            LoggerUtil.debug(this.getClass(), String.format(
                    "No entry found for user %d (%s) on %s in admin worktime file", userId, user.getName(), date));
        } else {
            // Entry exists - build detailed response
            buildDetailedEntryResponse(response, user, date, entry);
            LoggerUtil.debug(this.getClass(), String.format(
                    "Retrieved entry details for user %d (%s) on %s from admin worktime file: %s",
                    userId, user.getName(), date,
                    entry.getTimeOffType() != null ? entry.getTimeOffType() : "work"));
        }

        return response;
    }

    private Map<Integer, Map<LocalDate, WorkTimeTable>> convertToUserEntriesMap(List<WorkTimeTable> viewableEntries) {
        Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = new HashMap<>();

        for (WorkTimeTable entry : viewableEntries) {
            Integer userId = entry.getUserId();
            LocalDate workDate = entry.getWorkDate();

            // Get or create user's entry map
            Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.computeIfAbsent(
                    userId, k -> new HashMap<>());

            // Add entry to user's map
            userEntries.put(workDate, entry);
        }

        LoggerUtil.debug(this.getClass(), String.format(
                "Converted %d admin worktime entries to user entries map for %d users",
                viewableEntries.size(), userEntriesMap.size()));

        return userEntriesMap;
    }

    /**
     * Build response for cases where no entry exists
     */
    private void buildNoEntryResponse(Map<String, Object> response, User user, LocalDate date) {
        response.put("hasEntry", false);
        response.put("date", date.toString());
        response.put("userId", user.getUserId());
        response.put("userName", user.getName());
        response.put("employeeId", user.getEmployeeId());
        response.put("displayFormat", "-");
        response.put("isTimeOff", false);
        response.put("isHolidayWithWork", false);
    }

    /**
     * Build detailed response for existing entries
     */
    private void buildDetailedEntryResponse(Map<String, Object> response, User user, LocalDate date, WorkTimeTable entry) {
        // Basic information
        response.put("hasEntry", true);
        response.put("date", date.toString());
        response.put("userId", user.getUserId());
        response.put("userName", user.getName());
        response.put("employeeId", user.getEmployeeId());

        // Add all entry details
        addTimeInformation(response, entry);
        addWorkCalculations(response, entry);
        addBreakInformation(response, entry);
        addTimeOffInformation(response, entry);
        addStatusInformation(response, entry);
        addMetadata(response, entry);
    }

    /**
     * Add time-related information (start/end times, elapsed time)
     */
    private void addTimeInformation(Map<String, Object> response, WorkTimeTable entry) {
        // Start time
        if (entry.getDayStartTime() != null) {
            response.put("dayStartTime", entry.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            response.put("startDateTime", entry.getDayStartTime().toString());
        }

        // End time
        if (entry.getDayEndTime() != null) {
            response.put("dayEndTime", entry.getDayEndTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            response.put("endDateTime", entry.getDayEndTime().toString());
        }

        // Calculate total elapsed time if both start and end exist
        if (entry.getDayStartTime() != null && entry.getDayEndTime() != null) {
            long elapsedMinutes = Duration.between(entry.getDayStartTime(), entry.getDayEndTime()).toMinutes();
            response.put("totalElapsedMinutes", elapsedMinutes);
            response.put("formattedElapsedTime", CalculateWorkHoursUtil.minutesToHH((int) elapsedMinutes));
        }
    }

    /**
     * Add work time calculations and formatting
     */
    private void addWorkCalculations(Map<String, Object> response, WorkTimeTable entry) {
        // Regular work minutes
        response.put("totalWorkedMinutes", entry.getTotalWorkedMinutes());
        if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
            response.put("formattedWorkTime", CalculateWorkHoursUtil.minutesToHH(entry.getTotalWorkedMinutes()));
        }

        // Overtime minutes
        response.put("totalOvertimeMinutes", entry.getTotalOvertimeMinutes());
        if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
            response.put("formattedOvertimeTime", CalculateWorkHoursUtil.minutesToHH(entry.getTotalOvertimeMinutes()));
        }
    }

    /**
     * Add break and temporary stop information
     */
    private void addBreakInformation(Map<String, Object> response, WorkTimeTable entry) {
        // Temporary stops
        response.put("temporaryStopCount", entry.getTemporaryStopCount());
        response.put("totalTemporaryStopMinutes", entry.getTotalTemporaryStopMinutes());

        if (entry.getTotalTemporaryStopMinutes() != null && entry.getTotalTemporaryStopMinutes() > 0) {
            response.put("formattedTempStopTime", CalculateWorkHoursUtil.minutesToHH(entry.getTotalTemporaryStopMinutes()));
        }

        // Lunch break
        response.put("lunchBreakDeducted", entry.isLunchBreakDeducted());
    }

    /**
     * Add time off type information and display formatting
     */
    private void addTimeOffInformation(Map<String, Object> response, WorkTimeTable entry) {
        response.put("timeOffType", entry.getTimeOffType());

        if (entry.getTimeOffType() != null) {
            response.put("timeOffLabel", getTimeOffLabel(entry.getTimeOffType()));
            response.put("isTimeOff", true);

            // Special handling for SN with work (SN/4h format)
            if ("SN".equals(entry.getTimeOffType()) && entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
                response.put("isHolidayWithWork", true);
                response.put("displayFormat", "SN/" + CalculateWorkHoursUtil.minutesToHH(entry.getTotalOvertimeMinutes()));
            } else {
                response.put("isHolidayWithWork", false);
                response.put("displayFormat", entry.getTimeOffType());
            }
        } else {
            response.put("isTimeOff", false);
            response.put("isHolidayWithWork", false);

            // Regular work display
            if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                response.put("displayFormat", CalculateWorkHoursUtil.minutesToHH(entry.getTotalWorkedMinutes()));
            } else {
                response.put("displayFormat", "-");
            }
        }
    }

    /**
     * Add administrative status information
     */
    private void addStatusInformation(Map<String, Object> response, WorkTimeTable entry) {
        response.put("adminSync", entry.getAdminSync() != null ? entry.getAdminSync().toString() : null);

        if (entry.getAdminSync() != null) {
            response.put("statusLabel", getStatusLabel(entry.getAdminSync().toString()));
            response.put("statusClass", getStatusClass(entry.getAdminSync().toString()));
        }
    }

    /**
     * Add metadata flags for frontend convenience
     */
    private void addMetadata(Map<String, Object> response, WorkTimeTable entry) {
        response.put("hasWorkTime", entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0);
        response.put("hasOvertime", entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0);
        response.put("hasTempStops", entry.getTotalTemporaryStopMinutes() != null && entry.getTotalTemporaryStopMinutes() > 0);
        response.put("isComplete", entry.getDayStartTime() != null && entry.getDayEndTime() != null);
        response.put("hasLunchBreak", entry.isLunchBreakDeducted());
    }

    /**
     * Get human-readable time off label
     */
    private String getTimeOffLabel(String timeOffType) {
        if (timeOffType == null) return null;

        return switch (timeOffType) {
            case "SN" -> "National Holiday";
            case "CO" -> "Vacation";
            case "CM" -> "Medical Leave";
            default -> timeOffType;
        };
    }

    /**
     * Get human-readable status label
     */
    private String getStatusLabel(String adminSync) {
        if (adminSync == null) return null;

        return switch (adminSync) {
            case "USER_DONE" -> "User Completed";
            case "ADMIN_EDITED" -> "Admin Modified";
            case "USER_IN_PROCESS" -> "In Progress";
            case "ADMIN_BLANK" -> "Admin Blank";
            default -> adminSync;
        };
    }

    /**
     * Get CSS class for status display (for frontend styling)
     */
    private String getStatusClass(String adminSync) {
        if (adminSync == null) return "text-muted";

        return switch (adminSync) {
            case "USER_DONE" -> "text-success";
            case "ADMIN_EDITED" -> "text-warning";
            case "USER_IN_PROCESS" -> "text-info";
            case "ADMIN_BLANK" -> "text-secondary";
            default -> "text-muted";
        };
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