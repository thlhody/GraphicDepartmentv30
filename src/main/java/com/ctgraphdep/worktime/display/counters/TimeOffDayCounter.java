package com.ctgraphdep.worktime.display.counters;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeDisplayDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Centralized counter for time-off days (SN, CO, CM, CE).
 * This class provides THE SINGLE SOURCE OF TRUTH for counting time-off days.
 * Previously duplicated in 4+ locations in WorktimeDisplayService.
 * Business Rules:
 * - SN (National Holiday) counts separately
 * - CO (Vacation) counts separately
 * - CM (Medical Leave) counts separately
 * - CE (Special Event) counts AS CO (not separately)
 * Usage:
 * - Pass entries or DTOs
 * - Get back structured counts
 * - Use counts for summaries, statistics, display
 */
@Component
public class TimeOffDayCounter {

    /**
     * Count time-off days from WorkTimeTable entries.
     * @param entries List of worktime entries to analyze
     * @return Structured counts of SN, CO (includes CE), and CM days
     */
    public TimeOffDayCounts countFromEntries(List<WorkTimeTable> entries) {
        if (entries == null || entries.isEmpty()) {
            return new TimeOffDayCounts();
        }

        int snDays = 0;
        int coDays = 0;
        int cmDays = 0;

        for (WorkTimeTable entry : entries) {
            if (entry.getTimeOffType() == null) {
                continue;
            }

            switch (entry.getTimeOffType()) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> snDays++;
                case WorkCode.TIME_OFF_CODE -> coDays++;
                case WorkCode.MEDICAL_LEAVE_CODE -> cmDays++;
                case WorkCode.SPECIAL_EVENT_CODE -> coDays++;  // CE counts as CO for display totals
            }
        }

        return new TimeOffDayCounts(snDays, coDays, cmDays);
    }

    /**
     * Count time-off days from WorkTimeDisplayDTO map.
     * @param dtos Map of date to display DTO
     * @return Structured counts of SN, CO (includes CE), and CM days
     */
    public TimeOffDayCounts countFromDTOs(Map<LocalDate, WorkTimeDisplayDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new TimeOffDayCounts();
        }

        int snDays = 0;
        int coDays = 0;
        int cmDays = 0;

        for (WorkTimeDisplayDTO dto : dtos.values()) {
            if (!dto.isHasEntry() || !dto.isTimeOff()) {
                continue;
            }

            WorkTimeTable rawEntry = dto.getRawEntry();
            if (rawEntry == null || rawEntry.getTimeOffType() == null) {
                continue;
            }

            switch (rawEntry.getTimeOffType()) {
                case WorkCode.NATIONAL_HOLIDAY_CODE -> snDays++;
                case WorkCode.TIME_OFF_CODE -> coDays++;
                case WorkCode.MEDICAL_LEAVE_CODE -> cmDays++;
                case WorkCode.SPECIAL_EVENT_CODE -> coDays++;  // CE counts as CO for display totals
            }
        }

        return new TimeOffDayCounts(snDays, coDays, cmDays);
    }

    /**
     * Count time-off days from flat list of entries across multiple users.
     * Used for statistics and aggregate reporting.
     * @param userEntriesMap Map of userId to (date to entry) map
     * @return Structured counts of SN, CO (includes CE), and CM days
     */
    public TimeOffDayCounts countFromUserEntriesMap(Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap) {
        if (userEntriesMap == null || userEntriesMap.isEmpty()) {
            return new TimeOffDayCounts();
        }

        List<WorkTimeTable> allEntries = userEntriesMap.values().stream()
                .flatMap(map -> map.values().stream())
                .toList();

        return countFromEntries(allEntries);
    }

    /**
     * Immutable data class for time-off day counts.
     * CE is included in coDays count.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeOffDayCounts {
        private int snDays = 0;      // National Holiday days
        private int coDays = 0;      // Vacation days (INCLUDES CE Special Event days)
        private int cmDays = 0;      // Medical Leave days

        /**
         * Get total time-off days across all types.
         */
        public int getTotalDays() {
            return snDays + coDays + cmDays;
        }

        /**
         * Check if there are any time-off days.
         */
        public boolean hasAnyDays() {
            return getTotalDays() > 0;
        }
    }
}
