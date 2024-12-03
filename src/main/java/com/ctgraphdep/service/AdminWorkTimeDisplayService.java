package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminWorkTimeDisplayService {


    // Prepare day headers for display with Romanian day initials
    public List<Map<String, String>> prepareDayHeaders(YearMonth yearMonth) {
        List<Map<String, String>> dayHeaders = new ArrayList<>();

        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            Map<String, String> headerInfo = new HashMap<>();

            headerInfo.put("day", String.valueOf(day));
            headerInfo.put("initial", WorkCode.ROMANIAN_DAY_INITIALS.get(date.getDayOfWeek()));
            headerInfo.put("isWeekend", isWeekend(date) ? "true" : "false");

            dayHeaders.add(headerInfo);
        }

        LoggerUtil.debug(this.getClass(),
                String.format("Prepared %d day headers for %s",
                        dayHeaders.size(), yearMonth));

        return dayHeaders;
    }

    // Calculate work time summaries for each user
    public Map<Integer, WorkTimeSummary> calculateUserSummaries(
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap,
            List<User> users,
            int year,
            int month) {

        Map<Integer, WorkTimeSummary> summaries = new HashMap<>();

        users.forEach(user -> {
            Map<LocalDate, WorkTimeTable> userEntries = userEntriesMap.getOrDefault(
                    user.getUserId(),
                    new HashMap<>()
            );
            WorkTimeSummary summary = calculateUserSummary(userEntries, year, month, user.getSchedule());
            summaries.put(user.getUserId(), summary);
        });

        LoggerUtil.debug(this.getClass(),
                String.format("Calculated summaries for %d users for %d/%d",
                        users.size(), month, year));

        return summaries;
    }


    // Calculate summary for a single user
    private WorkTimeSummary calculateUserSummary(
            Map<LocalDate, WorkTimeTable> entries,
            int year,
            int month,
            int schedule) {

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
                var result = CalculateWorkHoursUtil.calculateWorkTime(
                        entry.getTotalWorkedMinutes(),
                        schedule
                );
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

    // Check if date is weekend
    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY ||
                date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}