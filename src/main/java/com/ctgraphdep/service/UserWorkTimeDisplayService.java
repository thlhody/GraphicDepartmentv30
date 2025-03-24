package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.*;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.*;

//changing

@Service
public class UserWorkTimeDisplayService {
    private final AdminPaidHolidayService holidayService;

    public UserWorkTimeDisplayService(AdminPaidHolidayService holidayService) {
        this.holidayService = holidayService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PreAuthorize("#user.username == authentication.name or hasRole('ADMIN')")
    public Map<String, Object> prepareDisplayData(
            User user,
            List<WorkTimeTable> worktimeData,
            int year,
            int month) {

        validateInput(user, worktimeData, year, month);

        LoggerUtil.info(this.getClass(),
                String.format("Preparing display data for user %s, %d/%d",
                        user.getUsername(), month, year));

        try {
            Map<String, Object> displayData = new HashMap<>();

            // Filter entries for display
            List<WorkTimeTable> displayableEntries = filterEntriesForDisplay(worktimeData);

            // Get paid holiday information
            int paidHolidayDays = getPaidHolidayDays(user.getUserId());

            // Calculate summary
            WorkTimeSummary summary = calculateMonthSummary(
                    displayableEntries,
                    year,
                    month,
                    paidHolidayDays
            );

            // Prepare display data
            displayData.put("worktimeData", displayableEntries);
            displayData.put("currentYear", year);
            displayData.put("currentMonth", month);
            displayData.put("user", sanitizeUserData(user));
            displayData.put("summary", summary);

            LoggerUtil.info(this.getClass(), String.format("Prepared display data with %d entries for user %s",
                    displayableEntries.size(), user.getUsername()));

            return displayData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error preparing display data for user %s: %s", user.getUsername(), e.getMessage()));
            throw new RuntimeException("Failed to prepare display data", e);
        }
    }

    private List<WorkTimeTable> filterEntriesForDisplay(List<WorkTimeTable> entries) {
        return entries.stream()
                .filter(this::isEntryDisplayable)
                .map(this::prepareEntryForDisplay)
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                .toList();
    }

    private boolean isEntryDisplayable(WorkTimeTable entry) {
        if (entry == null) return false;

        // Never display ADMIN_BLANK entries
        if (SyncStatusWorktime.ADMIN_BLANK.equals(entry.getAdminSync())) {
            return false;
        }

        // Display USER_IN_PROCESS entries with partial info
        if (SyncStatusWorktime.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            return true;
        }

        // Show all other valid entries
        return entry.getAdminSync() != null;
    }

    private WorkTimeTable prepareEntryForDisplay(WorkTimeTable entry) {
        WorkTimeTable displayEntry = copyWorkTimeEntry(entry);

        // For USER_IN_PROCESS entries, show only partial information
        if (SyncStatusWorktime.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            displayEntry.setTotalWorkedMinutes(null);
            displayEntry.setTotalOvertimeMinutes(null);
            displayEntry.setDayEndTime(null);
            displayEntry.setLunchBreakDeducted(false);
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
            int year,
            int month,
            int paidHolidayDays) {

        try {
            int totalWorkDays = CalculateWorkHoursUtil.calculateWorkDays(year, month);
            WorkTimeCounts counts = calculateWorkTimeCounts(worktimeData);

            return WorkTimeSummary.builder()
                    .totalWorkDays(totalWorkDays)
                    .daysWorked(counts.getDaysWorked())
                    .remainingWorkDays(totalWorkDays - (
                            counts.getDaysWorked() + counts.getSnDays() + counts.getCoDays() + counts.getCmDays()))
                    .snDays(counts.getSnDays())
                    .coDays(counts.getCoDays())
                    .cmDays(counts.getCmDays())
                    .totalRegularMinutes(counts.getRegularMinutes())
                    .totalOvertimeMinutes(counts.getOvertimeMinutes())
                    .totalMinutes(counts.getRegularMinutes() + counts.getOvertimeMinutes())
                    .availablePaidDays(paidHolidayDays)
                    .build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating month summary", e);
            throw e;
        }
    }

    private WorkTimeCounts calculateWorkTimeCounts(List<WorkTimeTable> worktimeData) {
        WorkTimeCounts counts = new WorkTimeCounts();
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;

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
            }  else if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                counts.incrementDaysWorked();

                // Use CalculateWorkHoursUtil for consistent calculation
                int userSchedule = 8; // Default to 8 hours if not available

                WorkTimeCalculationResult result = CalculateWorkHoursUtil.calculateWorkTime(
                        entry.getTotalWorkedMinutes(),
                        userSchedule
                );

                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += entry.getTotalOvertimeMinutes() != null ?
                        entry.getTotalOvertimeMinutes() : 0;
            }
        }
        counts.setRegularMinutes(totalRegularMinutes);
        counts.setOvertimeMinutes(totalOvertimeMinutes);

        return counts;
    }

    private WorkTimeTable copyWorkTimeEntry(WorkTimeTable source) {
        WorkTimeTable copy = new WorkTimeTable();
        copy.setUserId(source.getUserId());
        copy.setWorkDate(source.getWorkDate());
        copy.setDayStartTime(source.getDayStartTime());
        copy.setDayEndTime(source.getDayEndTime());
        copy.setTemporaryStopCount(source.getTemporaryStopCount());
        copy.setLunchBreakDeducted(source.isLunchBreakDeducted());
        copy.setTimeOffType(source.getTimeOffType());
        copy.setTotalWorkedMinutes(source.getTotalWorkedMinutes());
        copy.setTotalTemporaryStopMinutes(source.getTotalTemporaryStopMinutes());
        copy.setTotalOvertimeMinutes(source.getTotalOvertimeMinutes());
        copy.setAdminSync(source.getAdminSync());
        return copy;
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