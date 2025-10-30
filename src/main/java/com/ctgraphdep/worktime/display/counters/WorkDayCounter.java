package com.ctgraphdep.worktime.display.counters;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeDisplayDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Centralized counter for work days.
 * This class provides THE SINGLE SOURCE OF TRUTH for counting work days.
 * Previously duplicated in 3+ locations in WorktimeDisplayService.
 * Business Rules - What Counts as a Work Day:
 * 1. Regular work entry (no time-off type, has worked minutes > 0)
 * 2. ZS (Short Day) - user worked but less than schedule, filled from overtime
 * 3. CR (Recovery Leave) - full day paid from overtime balance
 * 4. D (Delegation) - normal work day at different location
 * What DOES NOT Count as Work Day:
 * - SN (National Holiday) without work
 * - CO (Vacation) without work
 * - CM (Medical Leave) without work
 * - CN (Unpaid Leave)
 * - CE (Special Event) without work
 * - W (Weekend) without work
 * Note: SN/CO/CM/CE/W WITH work hours (e.g., SN:5, CO:6) do NOT count as work days
 * in the traditional sense - they're special overtime days.
 */
@Component
public class WorkDayCounter {

    /**
     * Count work days from WorkTimeTable entries.
     *
     * @param entries List of worktime entries to analyze
     * @return Number of work days
     */
    public int countFromEntries(List<WorkTimeTable> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        int daysWorked = 0;

        for (WorkTimeTable entry : entries) {
            if (isWorkDay(entry)) {
                daysWorked++;
            }
        }

        return daysWorked;
    }

    /**
     * Count work days from WorkTimeDisplayDTO map.
     *
     * @param dtos Map of date to display DTO
     * @return Number of work days
     */
    public int countFromDTOs(Map<LocalDate, WorkTimeDisplayDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return 0;
        }

        int daysWorked = 0;

        for (WorkTimeDisplayDTO dto : dtos.values()) {
            if (!dto.isHasEntry()) {
                continue;
            }

            WorkTimeTable rawEntry = dto.getRawEntry();
            if (rawEntry != null && isWorkDay(rawEntry)) {
                daysWorked++;
            }
        }

        return daysWorked;
    }

    /**
     * Determine if a single entry represents a work day.
     *
     * @param entry Worktime entry to check
     * @return true if this entry represents a work day
     */
    public boolean isWorkDay(WorkTimeTable entry) {
        if (entry == null) {
            return false;
        }

        String timeOffType = entry.getTimeOffType();

        // Case 1: Regular work entry (no time off type, has worked minutes)
        if (timeOffType == null) {
            return entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0;
        }

        // Case 2: ZS (Short Day) - work day paid partially from overtime
        // Format: "ZS-5" means worked but missing 5 hours
        if (timeOffType.startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
            return true;
        }

        // Case 3: CR (Recovery Leave) - work day paid from overtime
        if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(timeOffType)) {
            return true;
        }

        // Case 4: D (Delegation) - normal work day with special form
        if (WorkCode.DELEGATION_CODE.equalsIgnoreCase(timeOffType)) {
            return true;
        }

        // All other time-off types are NOT work days
        return false;
    }

}
