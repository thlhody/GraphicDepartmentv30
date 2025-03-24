package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class HolidayHistoryService {
    private final DataAccessService dataAccess;

    public HolidayHistoryService(DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Get time off history for a user for the last 12 months
    public List<WorkTimeTable> getUserTimeOffHistory(String username) {
        List<WorkTimeTable> allTimeOffs = new ArrayList<>();
        LocalDate now = LocalDate.now();

        // Get files for the last 12 months
        for (int i = 0; i < WorkCode.HISTORY_MONTHS; i++) {
            LocalDate date = now.minusMonths(i);
            YearMonth yearMonth = YearMonth.from(date);
            try {
                List<WorkTimeTable> monthEntries = loadMonthlyTimeoffs(username, yearMonth);
                allTimeOffs.addAll(monthEntries);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Could not load time offs for %s - %d/%d: %s", username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()));
            }
        }
        return allTimeOffs;
    }

    // Load time off entries for a specific month
    private List<WorkTimeTable> loadMonthlyTimeoffs(String username, YearMonth yearMonth) {
        List<WorkTimeTable> monthEntries = dataAccess.readNetworkUserWorktime(
                username,
                yearMonth.getYear(),
                yearMonth.getMonthValue()
        );

        // Filter only time off entries (include all types)
        return monthEntries.stream()
                .filter(entry -> entry.getTimeOffType() != null &&
                        (entry.getTimeOffType().equals(WorkCode.TIME_OFF_CODE) ||
                                entry.getTimeOffType().equals(WorkCode.MEDICAL_LEAVE_CODE) ||
                                entry.getTimeOffType().equals(WorkCode.NATIONAL_HOLIDAY_CODE)))
                .toList();
    }
}