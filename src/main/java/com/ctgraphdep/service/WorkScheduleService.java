package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.service.cache.MetricsCacheService;
import com.ctgraphdep.service.cache.WorktimeCacheService;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Service to calculate work schedule and standard work hours based on user schedule and work days.
 * Enhanced with cache integration for improved accuracy and performance.
 */
@Service
public class WorkScheduleService {

    private final UserService userService;
    private final WorktimeDataService worktimeDataService;
    private final WorktimeCacheService worktimeCacheService;
    private final MetricsCacheService metricsCacheService;
    private final CalculationService calculationService;

    public WorkScheduleService(
            UserService userService,
            WorktimeDataService worktimeDataService,
            WorktimeCacheService worktimeCacheService,
            MetricsCacheService metricsCacheService,
            CalculationService calculationService) {
        this.userService = userService;
        this.worktimeDataService = worktimeDataService;
        this.worktimeCacheService = worktimeCacheService;
        this.metricsCacheService = metricsCacheService;
        this.calculationService = calculationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Calculate standard work hours for a month based on work days and user schedule
     * @param username The user's username
     * @param year The year to calculate for
     * @param month The month to calculate for
     * @return The standard work hours for the month
     */
    public int calculateStandardWorkHours(String username, int year, int month) {
        try {
            // Get user information to determine schedule
            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            // Get user schedule (default to 8 hours if not set)
            int hoursPerDay = user.getSchedule() != null ? user.getSchedule() : 8;

            // Get worktime data to check for time off
            List<WorkTimeTable> worktimeData = worktimeDataService.readUserFromNetworkOnly(username, year, month);

            // Calculate work days excluding weekends and time off days
            int workDays = calculateWorkDays(year, month, worktimeData);

            // Calculate standard hours
            int standardHours = workDays * hoursPerDay;

            LoggerUtil.info(this.getClass(),
                    String.format("Calculated standard work hours for %s in %d/%d: %d work days × %d hours = %d total hours",
                            username, month, year, workDays, hoursPerDay, standardHours));

            return standardHours;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error calculating standard work hours for %s in %d/%d: %s",
                            username, month, year, e.getMessage()));

            // Return default value based on 20 work days and 8-hour schedule
            return 160;
        }
    }

    /**
     * Calculate work days in a month excluding weekends and time off days
     * @param year The year
     * @param month The month
     * @param worktimeData The worktime data containing time off information
     * @return The number of work days
     */
    private int calculateWorkDays(int year, int month, List<WorkTimeTable> worktimeData) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();

        // Create a list of dates that are marked as time off
        List<LocalDate> timeOffDates = worktimeData.stream()
                .filter(entry -> entry.getTimeOffType() != null)
                .map(WorkTimeTable::getWorkDate)
                .toList();

        int workDays = 0;
        LocalDate current = startOfMonth;

        while (!current.isAfter(endOfMonth)) {
            DayOfWeek dayOfWeek = current.getDayOfWeek();

            // Count as work day if not weekend and not marked as time off
            if (!(dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) &&
                    !timeOffDates.contains(current)) {
                workDays++;
            }

            current = current.plusDays(1);
        }

        return workDays;
    }

    /**
     * Calculate standard work hours using CACHED data (preferred method).
     * This method uses cached worktime data for better performance and accuracy.
     * Falls back to file-based calculation if cache is unavailable.
     *
     * @param username The user's username
     * @param userId The user's ID
     * @param year The year to calculate for
     * @param month The month to calculate for
     * @return The standard work hours for the month
     */
    public double calculateStandardWorkHoursWithCache(String username, Integer userId, int year, int month) {
        try {
            // Check cache first
            Double cachedValue = metricsCacheService.getCachedStandardHours(username, year, month);
            if (cachedValue != null) {
                LoggerUtil.debug(this.getClass(),
                        String.format("Using cached standard hours for %s (%d-%02d): %.2f",
                                username, year, month, cachedValue));
                return cachedValue;
            }

            // Get user information to determine schedule
            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            // Get user schedule (default to 8 hours if not set)
            int hoursPerDay = user.getSchedule() != null ? user.getSchedule() : 8;

            // Try to get worktime data from cache first, then fallback to file
            List<WorkTimeTable> worktimeData;
            try {
                worktimeData = worktimeCacheService.getMonthEntriesWithFallback(username, userId, year, month);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Could not get worktime from cache, using file: %s", e.getMessage()));
                worktimeData = worktimeDataService.readUserFromNetworkOnly(username, year, month);
            }

            // Handle null or empty worktime data gracefully
            if (worktimeData == null) {
                worktimeData = List.of();
            }

            // Calculate work days excluding weekends and time off days
            int workDays = calculateWorkDays(year, month, worktimeData);

            // Calculate standard hours
            double standardHours = workDays * hoursPerDay;

            // Cache the result
            metricsCacheService.cacheStandardHours(username, year, month, standardHours);

            LoggerUtil.info(this.getClass(),
                    String.format("Calculated standard work hours for %s in %d/%02d: %d work days × %d hours = %.2f total hours",
                            username, year, month, workDays, hoursPerDay, standardHours));

            return standardHours;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error calculating standard work hours for %s in %d/%02d: %s",
                            username, year, month, e.getMessage()), e);

            // Return default value based on 20 work days and 8-hour schedule
            return 160.0;
        }
    }

    /**
     * Calculate LIVE work hours from actual worktime entries (cached data preferred).
     * This sums up all actual worked minutes + overtime from worktime entries for the month.
     *
     * @param username The user's username
     * @param userId The user's ID
     * @param year The year to calculate for
     * @param month The month to calculate for
     * @return The live work hours (actual worked hours) for the month
     */
    public double calculateLiveWorkHours(String username, Integer userId, int year, int month) {
        try {
            // Check cache first
            Double cachedValue = metricsCacheService.getCachedLiveWorkHours(username, year, month);
            if (cachedValue != null) {
                LoggerUtil.debug(this.getClass(),
                        String.format("Using cached live work hours for %s (%d-%02d): %.2f",
                                username, year, month, cachedValue));
                return cachedValue;
            }

            // Get worktime data from cache (with fallback to file)
            List<WorkTimeTable> worktimeData;
            try {
                worktimeData = worktimeCacheService.getMonthEntriesWithFallback(username, userId, year, month);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Could not get worktime from cache, using file: %s", e.getMessage()));
                worktimeData = worktimeDataService.readUserFromNetworkOnly(username, year, month);
            }

            // Handle null or empty worktime data
            if (worktimeData == null || worktimeData.isEmpty()) {
                LoggerUtil.info(this.getClass(),
                        String.format("No worktime data found for %s (%d-%02d), returning 0 live hours. Refresh the page after adding worktime entries.",
                                username, year, month));
                metricsCacheService.cacheLiveWorkHours(username, year, month, 0.0);
                return 0.0;
            }

            // Get user to determine schedule for calculation
            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
            int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;

            // Calculate total worked minutes using the SAME logic as Time Management page
            int totalRegularMinutes = 0;
            int totalOvertimeMinutes = 0;
            int entriesWithWork = 0;
            int timeOffEntries = 0;
            int inProcessEntries = 0;

            LoggerUtil.debug(this.getClass(),
                    String.format("Processing %d worktime entries for %s (%d-%02d) with schedule %d hours",
                            worktimeData.size(), username, year, month, userSchedule));

            for (WorkTimeTable entry : worktimeData) {
                // SKIP IN-PROCESS entries (active sessions not yet stopped)
                if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                    inProcessEntries++;
                    LoggerUtil.debug(this.getClass(), "Skipping IN_PROCESS entry");
                    continue;
                }

                // HANDLE SPECIAL DAY TYPES WITH OVERTIME (SN/CO/CM/W with work) - Check FIRST
                if (isSpecialDayType(entry.getTimeOffType()) && entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
                    // Special day overtime goes directly to overtime totals (NO regular minutes)
                    totalOvertimeMinutes += entry.getTotalOvertimeMinutes();
                    LoggerUtil.debug(this.getClass(),
                            String.format("Special day (%s) with %d overtime mins",
                                    entry.getTimeOffType(), entry.getTotalOvertimeMinutes()));
                    continue; // Skip to next entry
                }

                // SKIP time-off entries without any work (no worked minutes AND no overtime)
                if (entry.getTimeOffType() != null &&
                    (entry.getTotalWorkedMinutes() == null || entry.getTotalWorkedMinutes() == 0) &&
                    (entry.getTotalOvertimeMinutes() == null || entry.getTotalOvertimeMinutes() == 0)) {
                    timeOffEntries++;
                    continue;
                }

                // HANDLE REGULAR WORK ENTRIES (no time off type, has worked minutes)
                if (entry.getTimeOffType() == null && entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                    entriesWithWork++;

                    // For regular days, totalWorkedMinutes is RAW time worked
                    // Process through CalculationService to split into regular + overtime
                    WorkTimeCalculationResultDTO result = calculationService.calculateWorkTime(
                            entry.getTotalWorkedMinutes(), userSchedule);

                    // Add BOTH regular and overtime (we want total hours actually worked)
                    totalRegularMinutes += result.getProcessedMinutes();
                    totalOvertimeMinutes += result.getOvertimeMinutes();

                    LoggerUtil.debug(this.getClass(),
                            String.format("Regular day: %d raw mins → %d regular + %d overtime = %d total (lunch: %s)",
                                    entry.getTotalWorkedMinutes(),
                                    result.getProcessedMinutes(),
                                    result.getOvertimeMinutes(),
                                    result.getProcessedMinutes() + result.getOvertimeMinutes(),
                                    result.isLunchDeducted() ? "yes" : "no"));
                }
            }

            // Total hours = regular + overtime (converted from minutes)
            double totalMinutes = totalRegularMinutes + totalOvertimeMinutes;
            double liveWorkHours = Math.round(totalMinutes / 60.0 * 100.0) / 100.0;

            // Cache the result
            metricsCacheService.cacheLiveWorkHours(username, year, month, liveWorkHours);

            LoggerUtil.info(this.getClass(),
                    String.format("Calculated live work hours for %s in %d/%02d: %d total entries (%d with work, %d time-off, %d in-process skipped), %d regular mins + %d overtime mins = %.2f hours",
                            username, year, month, worktimeData.size(), entriesWithWork, timeOffEntries, inProcessEntries, totalRegularMinutes, totalOvertimeMinutes, liveWorkHours));

            return liveWorkHours;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error calculating live work hours for %s in %d/%02d: %s",
                            username, year, month, e.getMessage()), e);

            // Return 0 on error (no work tracked)
            return 0.0;
        }
    }

    /**
     * Check if the time off type is a special day (SN, CO, CM, W)
     * that can have overtime work associated with it
     */
    private boolean isSpecialDayType(String timeOffType) {
        if (timeOffType == null) {
            return false;
        }
        return WorkCode.NATIONAL_HOLIDAY_CODE.equals(timeOffType) ||
                WorkCode.TIME_OFF_CODE.equals(timeOffType) ||
                WorkCode.MEDICAL_LEAVE_CODE.equals(timeOffType) ||
                WorkCode.WEEKEND_CODE.equals(timeOffType);  // Weekend work
    }
}