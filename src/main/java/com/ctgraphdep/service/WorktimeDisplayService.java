package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeCountsDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.WorkTimeEntryUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for handling the display of worktime data for both users and admins.
 * This consolidates functionality from AdminWorkTimeDisplayService and UserWorkTimeDisplayService.
 */
@Service
public class WorktimeDisplayService {

    private final HolidayManagementService holidayManagementService;

    @Autowired
    public WorktimeDisplayService(
            HolidayManagementService holidayManagementService) {
        this.holidayManagementService = holidayManagementService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Processes worktime data and converts it to DTOs for display for a single user.
     * Used by the user interface.
     */
    @PreAuthorize("#user.username == authentication.name or hasRole('ADMIN')")
    public Map<String, Object> prepareUserDisplayData(User user, List<WorkTimeTable> worktimeData, int year, int month) {

        validateInput(user, worktimeData, year, month);
        LoggerUtil.info(this.getClass(), String.format("Preparing display data for user %s, %d/%d", user.getUsername(), month, year));

        try {
            Map<String, Object> displayData = new HashMap<>();

            // Filter entries for display
            List<WorkTimeTable> displayableEntries = filterEntriesForDisplay(worktimeData);

            // Get paid holiday information
            int paidHolidayDays = getPaidHolidayDays(user.getUserId());

            // Calculate summary
            WorkTimeSummary summary = calculateMonthSummary(displayableEntries, year, month, paidHolidayDays);

            // Convert to DTOs with pre-calculated values
            int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8; // Default to 8 hours
            List<WorkTimeEntryDTO> entryDTOs = displayableEntries.stream().map(entry -> WorkTimeEntryDTO.fromWorkTimeTable(entry, userSchedule)).collect(Collectors.toList());

            WorkTimeSummaryDTO summaryDTO = WorkTimeSummaryDTO.fromWorkTimeSummary(summary);

            // Prepare display data
            displayData.put("worktimeData", entryDTOs);
            displayData.put("currentYear", year);
            displayData.put("currentMonth", month);
            displayData.put("user", sanitizeUserData(user));
            displayData.put("summary", summaryDTO);

            LoggerUtil.info(this.getClass(), String.format("Prepared display data with %d entries for user %s", entryDTOs.size(), user.getUsername()));

            return displayData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error preparing display data for user %s: %s", user.getUsername(), e.getMessage()));
            throw new RuntimeException("Failed to prepare display data", e);
        }
    }

    /**
     * Prepare day headers for display with Romanian day initials.
     * Used by the admin interface.
     */
    public List<Map<String, String>> prepareDayHeaders(YearMonth yearMonth) {
        // Validate the year month is reasonable
        WorkTimeEntryUtil.validateYearMonth(yearMonth);

        List<Map<String, String>> dayHeaders = new ArrayList<>();

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            Map<String, String> headerInfo = new HashMap<>();

            headerInfo.put("day", String.valueOf(day));
            headerInfo.put("initial", WorkCode.ROMANIAN_DAY_INITIALS.get(date.getDayOfWeek()));
            // Check if it's a weekend
            headerInfo.put("isWeekend", WorkTimeEntryUtil.isDateWeekend(date) ? "true" : "false");
            dayHeaders.add(headerInfo);
        }

        LoggerUtil.debug(this.getClass(), String.format("Prepared %d day headers for %s", dayHeaders.size(), yearMonth));

        return dayHeaders;
    }

    /**
     * Calculate work time summaries for each user.
     * Used by the admin interface.
     */
    public Map<Integer, WorkTimeSummary> calculateUserSummaries(Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap, List<User> users, Integer year, Integer month) {

        // Validate the year and month
        WorkTimeEntryUtil.validateYearMonth(YearMonth.of(year, month));
        Map<Integer, WorkTimeSummary> summaries = new HashMap<>();
        users.forEach(user -> {Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.getOrDefault(user.getUserId(), new HashMap<>());
            WorkTimeSummary summary = calculateUserSummary(userEntries, year, month, user.getSchedule());
            summaries.put(user.getUserId(), summary);
        });

        LoggerUtil.debug(this.getClass(), String.format("Calculated summaries for %d users for %d/%d", users.size(), month, year));

        return summaries;
    }

    // ============= Private Helper Methods =============

    private List<WorkTimeTable> filterEntriesForDisplay(List<WorkTimeTable> entries) {
        return entries.stream().filter(WorkTimeEntryUtil::isEntryDisplayable).map(this::prepareEntryForDisplay)
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate)).toList();
    }

    private WorkTimeTable prepareEntryForDisplay(WorkTimeTable entry) {
        WorkTimeTable displayEntry = WorkTimeEntryUtil.copyWorkTimeEntry(entry);

        // For USER_IN_PROCESS entries, show only partial information
        if (SyncStatusWorktime.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            // Keep information that is already available
            if (displayEntry.getTotalWorkedMinutes() == null || displayEntry.getTotalWorkedMinutes() == 0) {
                displayEntry.setTotalWorkedMinutes(null);
            }

            if (displayEntry.getTotalOvertimeMinutes() == null || displayEntry.getTotalOvertimeMinutes() == 0) {
                displayEntry.setTotalOvertimeMinutes(null);
            }

            // Always hide end time for in-process entries
            displayEntry.setDayEndTime(null);

            // Don't apply lunch break for in-process entries unless explicitly set
            if (!displayEntry.isLunchBreakDeducted()) {
                displayEntry.setLunchBreakDeducted(false);
            }

            return displayEntry;
        }

        return displayEntry;
    }

    private void validateInput(User user, List<WorkTimeTable> worktimeData, int year, int month) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (worktimeData == null) {
            throw new IllegalArgumentException("Worktime data cannot be null");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        if (worktimeData.stream().anyMatch(entry -> !entry.getUserId().equals(user.getUserId()))) {
            throw new SecurityException("Worktime data contains entries for other users");
        }
    }

    private int getPaidHolidayDays(Integer userId) {
        try {
            return holidayManagementService.getRemainingHolidayDays(userId);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading paid holiday days for user " + userId, e);
            return 0;
        }
    }

    private WorkTimeSummary calculateMonthSummary(List<WorkTimeTable> worktimeData, int year, int month, int paidHolidayDays) {

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
                    .availablePaidDays(paidHolidayDays)
                    .build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating month summary", e);
            throw e;
        }
    }

    private WorkTimeCountsDTO calculateWorkTimeCounts(List<WorkTimeTable> worktimeData) {
        WorkTimeCountsDTO counts = new WorkTimeCountsDTO();
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalDiscardedMinutes = 0;

        for (WorkTimeTable entry : worktimeData) {
            // Skip in-process entries
            if (SyncStatusWorktime.USER_IN_PROCESS.equals(entry.getAdminSync())) {
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

                // Get user schedule (default to 8 hours)
                int userSchedule = 8;

                // Use CalculateWorkHoursUtil for consistent calculation
                WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(entry.getTotalWorkedMinutes(), userSchedule);

                // Use the calculation results directly
                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += result.getOvertimeMinutes();

                // Calculate discarded minutes - partial hour not counted in processed minutes
                // Get adjusted minutes (after lunch deduction)
                int adjustedMinutes = CalculateWorkHoursUtil.calculateAdjustedMinutes(entry.getTotalWorkedMinutes(), userSchedule);

                // Discarded minutes are the remainder after dividing by 60 (partial hour)
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
     * Calculate summary for a single user for admin view
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

    private User sanitizeUserData(User user) {
        User sanitized = new User();
        sanitized.setUserId(user.getUserId());
        sanitized.setName(user.getName());
        sanitized.setUsername(user.getUsername());
        sanitized.setEmployeeId(user.getEmployeeId());
        sanitized.setSchedule(user.getSchedule());
        return sanitized;
    }
}