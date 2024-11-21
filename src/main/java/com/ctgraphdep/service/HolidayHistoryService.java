package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class HolidayHistoryService {
    private static final TypeReference<List<WorkTimeTable>> WORKTIME_LIST_TYPE =
            new TypeReference<>() {};
    private static final int HISTORY_MONTHS = 12;

    private final DataAccessService dataAccess;
    private final UserService userService;

    public HolidayHistoryService(DataAccessService dataAccess, UserService userService) {
        this.dataAccess = dataAccess;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), "Initializing Holiday History Service");
    }

    /**
     * Get time off history for a user for the last 12 months
     * @param username User's username
     * @return List of time off entries
     */
    public List<WorkTimeTable> getUserTimeOffHistory(String username) {
        LoggerUtil.info(this.getClass(),
                String.format("Fetching time off history for user: %s", username));

        List<WorkTimeTable> allTimeOffs = new ArrayList<>();
        LocalDate now = LocalDate.now();

        // Get files for the last 12 months
        for (int i = 0; i < HISTORY_MONTHS; i++) {
            LocalDate date = now.minusMonths(i);
            YearMonth yearMonth = YearMonth.from(date);

            try {
                List<WorkTimeTable> monthEntries = loadMonthlyTimeoffs(username, yearMonth);
                allTimeOffs.addAll(monthEntries);

                LoggerUtil.debug(this.getClass(),
                        String.format("Loaded %d time off entries for %s - %d/%d",
                                monthEntries.size(), username, yearMonth.getYear(), yearMonth.getMonthValue()));

            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Could not load time offs for %s - %d/%d: %s",
                                username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()));
            }
        }

        LoggerUtil.info(this.getClass(),
                String.format("Found total %d time off entries for user %s",
                        allTimeOffs.size(), username));

        return allTimeOffs;
    }

    /**
     * Load time off entries for a specific month
     */
    private List<WorkTimeTable> loadMonthlyTimeoffs(String username, YearMonth yearMonth) {
        Path filePath = dataAccess.getUserWorktimePath(
                username,
                yearMonth.getYear(),
                yearMonth.getMonthValue()
        );

        List<WorkTimeTable> monthEntries = dataAccess.readFile(
                filePath,
                WORKTIME_LIST_TYPE,
                true
        );

        // Filter only time off entries
        return monthEntries.stream()
                .filter(entry -> WorkCode.TIME_OFF_CODE.equals(entry.getTimeOffType()))
                .toList();
    }

    /**
     * Get time off history by date range
     */
    public List<WorkTimeTable> getUserTimeOffHistoryByRange(
            String username, LocalDate startDate, LocalDate endDate) {

        LoggerUtil.info(this.getClass(),
                String.format("Fetching time off history for user %s between %s and %s",
                        username, startDate, endDate));

        List<WorkTimeTable> allTimeOffs = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            YearMonth yearMonth = YearMonth.from(currentDate);

            try {
                List<WorkTimeTable> monthEntries = loadMonthlyTimeoffs(username, yearMonth);

                // Filter by date range
                List<WorkTimeTable> rangeEntries = monthEntries.stream()
                        .filter(entry -> !entry.getWorkDate().isBefore(startDate)
                                && !entry.getWorkDate().isAfter(endDate))
                        .toList();

                allTimeOffs.addAll(rangeEntries);

                LoggerUtil.debug(this.getClass(),
                        String.format("Loaded %d time off entries for %s - %d/%d",
                                rangeEntries.size(), username, yearMonth.getYear(), yearMonth.getMonthValue()));

            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Could not load time offs for %s - %d/%d: %s",
                                username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()));
            }

            currentDate = currentDate.plusMonths(1);
        }

        LoggerUtil.info(this.getClass(),
                String.format("Found total %d time off entries for user %s in date range",
                        allTimeOffs.size(), username));

        return allTimeOffs;
    }
}