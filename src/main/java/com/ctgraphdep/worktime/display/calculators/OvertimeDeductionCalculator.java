package com.ctgraphdep.worktime.display.calculators;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeDisplayDTO;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Centralized calculator for CR/ZS overtime deductions.
 * This class provides THE SINGLE SOURCE OF TRUTH for calculating how much overtime
 * should be deducted and moved to regular time for CR and ZS entries.
 * Previously duplicated in 2+ locations in WorktimeDisplayService.
 * Business Rules:
 * - CR (Recovery Leave): Deducts FULL schedule hours (e.g., 8h * 60 = 480 minutes)
 * - ZS (Short Day): Deducts the MISSING hours (e.g., "ZS-5" deducts 5h * 60 = 300 minutes)
 * - Deductions are MOVED from overtime → regular time (reclassification, not loss)
 * - USER_IN_PROCESS entries are skipped (not yet finalized)
 * Example:
 * - User has 32:00 overtime
 * - User takes CR (8h) + ZS-5 (5h) = 13h deduction
 * - Result: 19:00 overtime, 13:00 added to regular time
 */
@Component
public class OvertimeDeductionCalculator {

    /**
     * Calculate deductions from WorkTimeTable entries.
     * @param entries List of worktime entries
     * @param scheduleMinutes User's schedule in minutes (e.g., 8h = 480 minutes)
     * @return Deduction results with CR, ZS, and total deductions
     */
    public DeductionResult calculateFromEntries(List<WorkTimeTable> entries, int scheduleMinutes) {
        if (entries == null || entries.isEmpty()) {
            return new DeductionResult();
        }

        int crDeductions = 0;
        int zsDeductions = 0;
        int crCount = 0;
        int zsCount = 0;

        for (WorkTimeTable entry : entries) {
            // Skip in-process entries (not yet finalized)
            if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            // Calculate CR deductions: each CR deducts full schedule hours
            if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(entry.getTimeOffType())) {
                crDeductions += scheduleMinutes;
                crCount++;
            }

            // Calculate ZS deductions: parse hours from "ZS-X" format
            if (entry.getTimeOffType() != null && entry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
                int deduction = parseZSDeduction(entry.getTimeOffType());
                if (deduction > 0) {
                    zsDeductions += deduction;
                    zsCount++;
                }
            }
        }

        return new DeductionResult(crDeductions, zsDeductions, crCount, zsCount);
    }

    /**
     * Calculate deductions from WorkTimeDisplayDTO map.
     * @param dtos Map of date to display DTO
     * @param scheduleMinutes User's schedule in minutes
     * @return Deduction results with CR, ZS, and total deductions
     */
    public DeductionResult calculateFromDTOs(Map<LocalDate, WorkTimeDisplayDTO> dtos, int scheduleMinutes) {
        if (dtos == null || dtos.isEmpty()) {
            return new DeductionResult();
        }

        int crDeductions = 0;
        int zsDeductions = 0;
        int crCount = 0;
        int zsCount = 0;

        for (WorkTimeDisplayDTO dto : dtos.values()) {
            if (!dto.isHasEntry()) {
                continue;
            }

            WorkTimeTable entry = dto.getRawEntry();
            if (entry == null) {
                continue;
            }

            // Skip in-process entries
            if (MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            // Calculate CR deductions
            if (WorkCode.RECOVERY_LEAVE_CODE.equalsIgnoreCase(entry.getTimeOffType())) {
                crDeductions += scheduleMinutes;
                crCount++;
            }

            // Calculate ZS deductions
            if (entry.getTimeOffType() != null && entry.getTimeOffType().startsWith(WorkCode.SHORT_DAY_CODE + "-")) {
                int deduction = parseZSDeduction(entry.getTimeOffType());
                if (deduction > 0) {
                    zsDeductions += deduction;
                    zsCount++;
                }
            }
        }

        return new DeductionResult(crDeductions, zsDeductions, crCount, zsCount);
    }

    /**
     * Calculate deductions for a user with proper schedule lookup.
     * @param entries List of worktime entries
     * @param user User whose schedule to use
     * @return Deduction results
     */
    public DeductionResult calculateForUser(List<WorkTimeTable> entries, User user) {
        int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;
        int scheduleMinutes = userSchedule * 60;
        return calculateFromEntries(entries, scheduleMinutes);
    }

    /**
     * Calculate deductions from DTOs for a user.
     * @param dtos Map of date to display DTO
     * @param user User whose schedule to use
     * @return Deduction results
     */
    public DeductionResult calculateForUserFromDTOs(Map<LocalDate, WorkTimeDisplayDTO> dtos, User user) {
        int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8;
        int scheduleMinutes = userSchedule * 60;
        return calculateFromDTOs(dtos, scheduleMinutes);
    }

    /**
     * Parse ZS deduction from format "ZS-X" where X is missing hours.
     * Example: "ZS-5" → 300 minutes (5 hours * 60)
     * @param timeOffType Time-off type string (e.g., "ZS-5")
     * @return Deduction in minutes, or 0 if parsing fails
     */
    private int parseZSDeduction(String timeOffType) {
        try {
            // Parse "ZS-5" → extract "5"
            String[] parts = timeOffType.split("-");
            if (parts.length == 2) {
                int missingHours = Integer.parseInt(parts[1]);
                return missingHours * 60;  // Convert to minutes
            }
        } catch (NumberFormatException e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to parse ZS hours from %s: %s",
                    timeOffType, e.getMessage()));
        }
        return 0;
    }

    /**
     * Immutable data class for deduction calculation results.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeductionResult {
        private int crDeductions = 0;       // Total CR deductions in minutes
        private int zsDeductions = 0;       // Total ZS deductions in minutes
        private int crCount = 0;            // Number of CR entries
        private int zsCount = 0;            // Number of ZS entries

        /**
         * Get total deductions across both CR and ZS.
         */
        public int getTotalDeductions() {
            return crDeductions + zsDeductions;
        }

        /**
         * Get total count of deduction entries.
         */
        public int getTotalCount() {
            return crCount + zsCount;
        }

        /**
         * Check if there are any deductions.
         */
        public boolean hasDeductions() {
            return getTotalDeductions() > 0;
        }

        /**
         * Get formatted description of deductions for logging.
         */
        public String getDescription() {
            return String.format("CR: %d min (%d entries), ZS: %d min (%d entries), Total: %d min",
                    crDeductions, crCount, zsDeductions, zsCount, getTotalDeductions());
        }
    }
}
