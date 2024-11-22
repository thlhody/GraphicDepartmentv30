package com.ctgraphdep.service;

import com.ctgraphdep.model.*;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserWorkTimeDisplayService {
    private final AdminPaidHolidayService holidayService;

    public UserWorkTimeDisplayService(AdminPaidHolidayService holidayService) {
        this.holidayService = holidayService;
        LoggerUtil.initialize(this.getClass(), "Initializing User Worktime Display Service");
    }

    @PreAuthorize("#user.username == authentication.name or hasRole('ADMIN')")
    public Map<String, Object> prepareDisplayData(
            User user,
            List<WorkTimeTable> worktimeData,
            int year,
            int month) {

        // Validate inputs
        validateInput(user, worktimeData, year, month);

        LoggerUtil.info(this.getClass(),
                String.format("Preparing display data for user %s, %d/%d",
                        user.getUsername(), month, year));

        try {
            Map<String, Object> displayData = new HashMap<>();

            // Process worktime calculations
            List<WorkTimeCalculationResult> processedWorktime = processWorktimeData(
                    worktimeData, user.getSchedule());

            // Get paid holiday information
            int paidHolidayDays = getPaidHolidayDays(user.getUserId());

            // Calculate summary
            WorkTimeSummary summary = calculateMonthSummary(
                    worktimeData,
                    processedWorktime,
                    year,
                    month,
                    user.getSchedule(),
                    paidHolidayDays
            );

            // Prepare display data
            displayData.put("worktimeData", worktimeData);
            displayData.put("currentYear", year);
            displayData.put("currentMonth", month);
            displayData.put("user", sanitizeUserData(user));
            displayData.put("summary", summary);

            LoggerUtil.info(this.getClass(),
                    String.format("Display data prepared successfully for user %s", user.getUsername()));

            return displayData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error preparing display data for user %s: %s",
                            user.getUsername(), e.getMessage()));
            throw new RuntimeException("Failed to prepare display data", e);
        }
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

        // Validate that all entries belong to the specified user
        if (worktimeData.stream()
                .anyMatch(entry -> !entry.getUserId().equals(user.getUserId()))) {
            throw new SecurityException("Worktime data contains entries for other users");
        }
    }

    private List<WorkTimeCalculationResult> processWorktimeData(
            List<WorkTimeTable> worktimeData,
            int schedule) {

        List<WorkTimeCalculationResult> processedWorktime = new ArrayList<>();

        for (WorkTimeTable day : worktimeData) {
            // Skip processing for in-process entries
            if (SyncStatus.USER_IN_PROCESS.equals(day.getAdminSync())) {
                continue;
            }

            if (day.getTotalWorkedMinutes() != null) {
                try {
                    WorkTimeCalculationResult result = CalculateWorkHoursUtil.calculateWorkTime(
                            day.getTotalWorkedMinutes(),
                            schedule
                    );
                    processedWorktime.add(result);

                    // Update the WorkTimeTable with processed values
                    updateWorkTimeTableWithResult(day, result);
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(),
                            "Error processing worktime entry for date " + day.getWorkDate(), e);
                }
            }
        }

        return processedWorktime;
    }

    private void updateWorkTimeTableWithResult(WorkTimeTable day, WorkTimeCalculationResult result) {
        day.setTotalWorkedMinutes(result.getProcessedMinutes());
        day.setTotalOvertimeMinutes(result.getOvertimeMinutes());
        day.setLunchBreakDeducted(result.isLunchDeducted());
    }

    private int getPaidHolidayDays(Integer userId) {
        try {
            List<PaidHolidayEntry> holidayEntries = holidayService.loadHolidayList();
            return holidayEntries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .findFirst()
                    .map(PaidHolidayEntry::getPaidHolidayDays)
                    .orElse(0);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error loading paid holiday days for user " + userId, e);
            return 0;
        }
    }

    private WorkTimeSummary calculateMonthSummary(
            List<WorkTimeTable> worktimeData,
            List<WorkTimeCalculationResult> processedWorktime,
            int year,
            int month,
            int schedule,
            int paidHolidayDays) {

        try {
            int totalWorkDays = CalculateWorkHoursUtil.calculateWorkDays(year, month);
            WorkTimeCounts counts = calculateWorkTimeCounts(worktimeData);

            return WorkTimeSummary.builder()
                    .totalWorkDays(totalWorkDays)
                    .daysWorked(counts.daysWorked)
                    .remainingWorkDays(totalWorkDays - (
                            counts.daysWorked + counts.snDays + counts.coDays + counts.cmDays))
                    .snDays(counts.snDays)
                    .coDays(counts.coDays)
                    .cmDays(counts.cmDays)
                    .totalRegularMinutes(counts.regularMinutes)
                    .totalOvertimeMinutes(counts.overtimeMinutes)
                    .totalMinutes(counts.regularMinutes + counts.overtimeMinutes)
                    .availablePaidDays(paidHolidayDays)
                    .build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating month summary", e);
            throw e;
        }
    }

    private static class WorkTimeCounts {
        int daysWorked = 0;
        int snDays = 0;
        int coDays = 0;
        int cmDays = 0;
        int regularMinutes = 0;
        int overtimeMinutes = 0;
    }

    private WorkTimeCounts calculateWorkTimeCounts(List<WorkTimeTable> worktimeData) {
        WorkTimeCounts counts = new WorkTimeCounts();

        for (WorkTimeTable entry : worktimeData) {
            // Skip in-process entries
            if (SyncStatus.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case "SN" -> counts.snDays++;
                    case "CO" -> counts.coDays++;
                    case "CM" -> counts.cmDays++;
                }
            } else if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                counts.daysWorked++;
                counts.regularMinutes += entry.getTotalWorkedMinutes();
                counts.overtimeMinutes += entry.getTotalOvertimeMinutes();
            }
        }

        return counts;
    }

    private User sanitizeUserData(User user) {
        // Create a copy with only necessary display data
        User sanitized = new User();
        sanitized.setUserId(user.getUserId());
        sanitized.setName(user.getName());
        sanitized.setUsername(user.getUsername());
        sanitized.setEmployeeId(user.getEmployeeId());
        sanitized.setSchedule(user.getSchedule());
        // Explicitly exclude password and other sensitive data
        return sanitized;
    }
}