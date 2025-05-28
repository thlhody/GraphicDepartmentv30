package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Service to calculate work schedule and standard work hours based on user schedule and work days
 */
@Service
public class WorkScheduleService {

    private final UserService userService;
    private final WorktimeDataService worktimeDataService;

    // Default target work units per hour
    private static final double DEFAULT_TARGET_WORK_UNITS_PER_HOUR = 4.0;

    public WorkScheduleService(UserService userService, WorktimeDataService worktimeDataService) {
        this.userService = userService;
        this.worktimeDataService = worktimeDataService;
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
     * Get the target work units per hour
     * @return The target work units per hour
     */
    public double getTargetWorkUnitsPerHour() {
        return DEFAULT_TARGET_WORK_UNITS_PER_HOUR;
    }
}