package com.ctgraphdep.worktime.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.WorktimeCacheService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for consolidating monthly overtime to fill CR (Recovery Leave) and ZS (Short Day) entries.
 * Logic:
 * 1. Load month entries from cache (no I/O)
 * 2. Calculate total overtime pool for the month
 * 3. Find CR and ZS entries that need to be filled
 * 4. Distribute overtime to complete these entries (CR gets full schedule, ZS gets missing hours)
 * 5. Deduct distributed overtime from regular work entries
 * 6. Write back consolidated entries
 */
@Service
public class MonthlyOvertimeConsolidationService {

    private final WorktimeCacheService worktimeCacheService;
    private final UserService userService;

    public MonthlyOvertimeConsolidationService(WorktimeCacheService worktimeCacheService,
                                               UserService userService) {
        this.worktimeCacheService = worktimeCacheService;
        this.userService = userService;
    }

    /**
     * Detect and mark short days (ZS) without consolidating
     * This should be run BEFORE consolidation to identify entries that need overtime fill
     */
    public ServiceResult<DetectionResult> detectShortDays(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format("=== Starting ZS detection for %s - %d/%d ===", username, year, month));

            // 1. Get user schedule
            int userSchedule = getUserSchedule(userId);
            int scheduleMinutes = userSchedule * 60;
            LoggerUtil.info(this.getClass(), String.format("User schedule: %d hours (%d minutes)", userSchedule, scheduleMinutes));

            // 2. Load entries from cache
            List<WorkTimeTable> entries = loadMonthEntries(username, year, month);
            if (entries.isEmpty()) {
                return ServiceResult.failure("No entries found for this month");
            }
            LoggerUtil.info(this.getClass(), String.format("Loaded %d entries for ZS detection", entries.size()));

            // 3. Find short days (worked < schedule AND no timeOffType set)
            List<WorkTimeTable> shortDays = new ArrayList<>();
            for (WorkTimeTable entry : entries) {
                // Skip if already has a time off type
                if (entry.getTimeOffType() != null && !entry.getTimeOffType().trim().isEmpty()) {
                    continue;
                }

                // Check if short day
                Integer workedMinutes = entry.getTotalWorkedMinutes();
                if (workedMinutes != null && workedMinutes > 0 && workedMinutes < scheduleMinutes) {
                    // Mark as ZS
                    entry.setTimeOffType(WorkCode.SHORT_DAY_CODE);
                    shortDays.add(entry);

                    LoggerUtil.info(this.getClass(), String.format("Detected ZS: %s worked %d/%d minutes",
                        entry.getWorkDate(), workedMinutes, scheduleMinutes));
                }
            }

            if (shortDays.isEmpty()) {
                LoggerUtil.info(this.getClass(), "No short days detected");
                return ServiceResult.success(new DetectionResult(0, "No short days found in this month"));
            }

            // 4. Save updated entries (now with ZS markers)
            saveConsolidatedEntries(username, entries, year, month);

            LoggerUtil.info(this.getClass(), String.format("=== Completed ZS detection: marked %d short days ===", shortDays.size()));

            return ServiceResult.success(new DetectionResult(
                shortDays.size(),
                String.format("Detected and marked %d short days as ZS. Run consolidation to fill with overtime.", shortDays.size())
            ));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error detecting short days for %s - %d/%d: %s",
                username, year, month, e.getMessage()), e);
            return ServiceResult.failure("Failed to detect short days: " + e.getMessage());
        }
    }

    /**
     * Consolidate overtime for a specific month
     * Cache-based operation with write-back
     */
    public ServiceResult<ConsolidationResult> consolidateMonth(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format("=== Starting overtime consolidation for %s - %d/%d ===", username, year, month));

            // 1. Get user schedule
            int userSchedule = getUserSchedule(userId);
            LoggerUtil.info(this.getClass(), String.format("User schedule: %d hours", userSchedule));

            // 2. Load entries from cache (no I/O)
            List<WorkTimeTable> entries = loadMonthEntries(username, year, month);
            if (entries.isEmpty()) {
                return ServiceResult.failure("No entries found for this month");
            }
            LoggerUtil.info(this.getClass(), String.format("Loaded %d entries from cache", entries.size()));

            // 2.5. AUTO-DETECT short days and mark as ZS (if not already marked)
            int autoDetectedZS = autoDetectAndMarkShortDays(entries, userSchedule);
            if (autoDetectedZS > 0) {
                LoggerUtil.info(this.getClass(), String.format("Auto-detected and marked %d short days as ZS", autoDetectedZS));
            }

            // 3. Calculate total overtime pool
            int totalOvertimeMinutes = calculateTotalOvertimePool(entries);
            LoggerUtil.info(this.getClass(), String.format("Total overtime pool: %d minutes (%s)",
                totalOvertimeMinutes, CalculateWorkHoursUtil.minutesToHHmm(totalOvertimeMinutes)));

            // 4. Find CR and ZS entries
            List<WorkTimeTable> crEntries = findEntriesByType(entries, WorkCode.RECOVERY_LEAVE_CODE);
            List<WorkTimeTable> zsEntries = findEntriesByType(entries, WorkCode.SHORT_DAY_CODE);
            LoggerUtil.info(this.getClass(), String.format("Found %d CR entries and %d ZS entries (including %d auto-detected)",
                crEntries.size(), zsEntries.size(), autoDetectedZS));

            if (crEntries.isEmpty() && zsEntries.isEmpty()) {
                return ServiceResult.success(new ConsolidationResult(0, 0, 0, totalOvertimeMinutes, "No CR or ZS entries to consolidate"));
            }

            // 5. Calculate required overtime
            int requiredForCR = calculateRequiredOvertimeForCR(crEntries, userSchedule);
            int requiredForZS = calculateRequiredOvertimeForZS(zsEntries, userSchedule);
            int totalRequired = requiredForCR + requiredForZS;

            LoggerUtil.info(this.getClass(), String.format("Required overtime: CR=%d, ZS=%d, Total=%d minutes",
                requiredForCR, requiredForZS, totalRequired));

            // 6. Check if we have enough overtime
            if (totalOvertimeMinutes < totalRequired) {
                return ServiceResult.failure(String.format(
                    "Insufficient overtime balance. Required: %s, Available: %s",
                    CalculateWorkHoursUtil.minutesToHHmm(totalRequired),
                    CalculateWorkHoursUtil.minutesToHHmm(totalOvertimeMinutes)
                ));
            }

            // 7. Distribute overtime to CR entries
            int distributedToCR = distributeToCREntries(crEntries, userSchedule);

            // 8. Distribute overtime to ZS entries
            int distributedToZS = distributeToZSEntries(zsEntries, userSchedule);

            int totalDistributed = distributedToCR + distributedToZS;
            LoggerUtil.info(this.getClass(), String.format("Distributed overtime: CR=%d, ZS=%d, Total=%d minutes",
                distributedToCR, distributedToZS, totalDistributed));

            // 9. Deduct distributed overtime from regular work entries
            deductDistributedOvertime(entries, totalDistributed);

            // 10. Recalculate final overtime pool
            int finalOvertimePool = calculateTotalOvertimePool(entries);
            LoggerUtil.info(this.getClass(), String.format("Final overtime pool: %d minutes (%s)",
                finalOvertimePool, CalculateWorkHoursUtil.minutesToHHmm(finalOvertimePool)));

            // 11. Save consolidated entries (write-back)
            saveConsolidatedEntries(username, entries, year, month);

            LoggerUtil.info(this.getClass(), String.format("=== Completed overtime consolidation for %s - %d/%d ===", username, year, month));

            return ServiceResult.success(new ConsolidationResult(
                crEntries.size(), zsEntries.size(), totalDistributed, finalOvertimePool,
                String.format("Successfully consolidated %d CR and %d ZS entries. Distributed %s from overtime pool.",
                    crEntries.size(), zsEntries.size(), CalculateWorkHoursUtil.minutesToHHmm(totalDistributed))
            ));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error consolidating overtime for %s - %d/%d: %s",
                username, year, month, e.getMessage()), e);
            return ServiceResult.failure("Failed to consolidate overtime: " + e.getMessage());
        }
    }

    // ========================================================================
    // CORE LOGIC METHODS
    // ========================================================================

    /**
     * Auto-detect short days and mark them as ZS
     * This runs during consolidation to catch entries that weren't explicitly marked
     */
    private int autoDetectAndMarkShortDays(List<WorkTimeTable> entries, int userSchedule) {
        int scheduleMinutes = userSchedule * 60;
        int markedCount = 0;

        for (WorkTimeTable entry : entries) {
            // Skip if already has a time off type
            if (entry.getTimeOffType() != null && !entry.getTimeOffType().trim().isEmpty()) {
                continue;
            }

            // Check if short day
            Integer workedMinutes = entry.getTotalWorkedMinutes();
            if (workedMinutes != null && workedMinutes > 0 && workedMinutes < scheduleMinutes) {
                // Mark as ZS
                entry.setTimeOffType(WorkCode.SHORT_DAY_CODE);
                markedCount++;

                LoggerUtil.debug(this.getClass(), String.format("Auto-detected ZS: %s worked %d/%d minutes",
                    entry.getWorkDate(), workedMinutes, scheduleMinutes));
            }
        }

        return markedCount;
    }

    /**
     * Load month entries from cache (no I/O)
     * Uses WorktimeCacheService's fallback mechanism: cache → file → emergency
     */
    private List<WorkTimeTable> loadMonthEntries(String username, int year, int month) {
        // Get user ID
        Integer userId = getUserId(username);

        // Use WorktimeCacheService's comprehensive fallback strategy
        // This handles: cache first, file fallback, emergency direct read
        List<WorkTimeTable> entries = worktimeCacheService.getMonthEntriesWithFallback(username, userId, year, month);

        if (entries.isEmpty()) {
            LoggerUtil.warn(this.getClass(), String.format("No entries loaded for %s - %d/%d, returning empty list", username, year, month));
            return new ArrayList<>();
        }

        LoggerUtil.debug(this.getClass(), String.format("Loaded %d entries for %s - %d/%d", entries.size(), username, year, month));
        return new ArrayList<>(entries); // Return mutable copy
    }

    /**
     * Calculate total overtime pool from all regular work entries
     * Only count positive overtime from entries WITHOUT CR/ZS/CN types
     */
    private int calculateTotalOvertimePool(List<WorkTimeTable> entries) {
        return entries.stream()
            .filter(e -> isRegularWorkEntry(e.getTimeOffType()))
            .filter(e -> e.getTotalOvertimeMinutes() != null && e.getTotalOvertimeMinutes() > 0)
            .mapToInt(WorkTimeTable::getTotalOvertimeMinutes)
            .sum();
    }

    /**
     * Check if entry is regular work (NOT CR/ZS/CN) - these contribute to overtime pool
     */
    private boolean isRegularWorkEntry(String timeOffType) {
        if (timeOffType == null || timeOffType.trim().isEmpty()) {
            return true; // No time off type = regular work
        }
        String type = timeOffType.trim().toUpperCase();
        return !WorkCode.RECOVERY_LEAVE_CODE.equals(type) &&
               !WorkCode.SHORT_DAY_CODE.equals(type) &&
               !WorkCode.UNPAID_LEAVE_CODE.equals(type);
    }

    /**
     * Find entries by time off type
     */
    private List<WorkTimeTable> findEntriesByType(List<WorkTimeTable> entries, String timeOffType) {
        return entries.stream()
            .filter(e -> timeOffType.equalsIgnoreCase(e.getTimeOffType()))
            .collect(Collectors.toList());
    }

    /**
     * Calculate required overtime for CR entries (full schedule per entry)
     */
    private int calculateRequiredOvertimeForCR(List<WorkTimeTable> crEntries, int userSchedule) {
        int scheduleMinutes = userSchedule * 60;
        int totalRequired = crEntries.size() * scheduleMinutes;

        LoggerUtil.debug(this.getClass(), String.format("CR required: %d entries × %d min/entry = %d minutes",
            crEntries.size(), scheduleMinutes, totalRequired));

        return totalRequired;
    }

    /**
     * Calculate required overtime for ZS entries (missing hours per entry)
     */
    private int calculateRequiredOvertimeForZS(List<WorkTimeTable> zsEntries, int userSchedule) {
        int scheduleMinutes = userSchedule * 60;
        int totalRequired = 0;

        for (WorkTimeTable entry : zsEntries) {
            int workedMinutes = entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0;
            int missingMinutes = Math.max(0, scheduleMinutes - workedMinutes);
            totalRequired += missingMinutes;

            LoggerUtil.debug(this.getClass(), String.format("ZS %s: worked=%d, schedule=%d, missing=%d",
                entry.getWorkDate(), workedMinutes, scheduleMinutes, missingMinutes));
        }

        return totalRequired;
    }

    /**
     * Distribute overtime to CR entries (fill full schedule)
     * CR = Day off paid from overtime balance
     */
    private int distributeToCREntries(List<WorkTimeTable> crEntries, int userSchedule) {
        int totalDistributed = 0;
        int scheduleMinutes = userSchedule * 60;

        for (WorkTimeTable entry : crEntries) {
            // Set work times (8:00 - schedule end)
            LocalDateTime startTime = entry.getWorkDate().atTime(8, 0);

            // Use CalculateWorkHoursUtil to get proper work time with lunch break
            WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(scheduleMinutes, userSchedule);

            // Calculate end time (start + schedule + lunch break if applicable)
            int totalMinutesIncludingBreak = result.isLunchDeducted() ?
                scheduleMinutes + WorkCode.HALF_HOUR_DURATION : scheduleMinutes;
            LocalDateTime endTime = startTime.plusMinutes(totalMinutesIncludingBreak);

            entry.setDayStartTime(startTime);
            entry.setDayEndTime(endTime);
            entry.setTotalWorkedMinutes(result.getProcessedMinutes());  // Schedule hours (e.g., 8h or 7.5h)
            entry.setLunchBreakDeducted(result.isLunchDeducted());
            entry.setTotalOvertimeMinutes(0);  // CR uses overtime, doesn't generate it
            entry.setTemporaryStopCount(0);
            entry.setTotalTemporaryStopMinutes(0);

            totalDistributed += scheduleMinutes;

            LoggerUtil.info(this.getClass(), String.format("Filled CR entry %s: %s - %s, worked=%d, lunch=%s",
                entry.getWorkDate(), startTime.toLocalTime(), endTime.toLocalTime(),
                entry.getTotalWorkedMinutes(), result.isLunchDeducted()));
        }

        return totalDistributed;
    }

    /**
     * Distribute overtime to ZS entries (fill missing hours)
     * ZS = Shortday where overtime completes the schedule
     */
    private int distributeToZSEntries(List<WorkTimeTable> zsEntries, int userSchedule) {
        int totalDistributed = 0;
        int scheduleMinutes = userSchedule * 60;

        for (WorkTimeTable entry : zsEntries) {
            int workedMinutes = entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0;
            int missingMinutes = Math.max(0, scheduleMinutes - workedMinutes);

            if (missingMinutes > 0) {
                // Calculate what the full schedule work time should be
                WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(scheduleMinutes, userSchedule);

                // Update entry to reflect full schedule (short hours + overtime fill)
                entry.setTotalWorkedMinutes(result.getProcessedMinutes());  // Full schedule now
                entry.setLunchBreakDeducted(result.isLunchDeducted());

                // Store negative overtime to show it was taken from pool
                int currentOvertime = entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0;
                entry.setTotalOvertimeMinutes(currentOvertime - missingMinutes);  // Negative = used from pool

                totalDistributed += missingMinutes;

                LoggerUtil.info(this.getClass(), String.format("Filled ZS entry %s: was %d min, added %d min from overtime, now %d min (schedule complete)",
                    entry.getWorkDate(), workedMinutes, missingMinutes, entry.getTotalWorkedMinutes()));
            }
        }

        return totalDistributed;
    }

    /**
     * Deduct distributed overtime from regular work entries (proportionally)
     * This ensures the overtime pool reflects the amount used for CR/ZS
     */
    private void deductDistributedOvertime(List<WorkTimeTable> allEntries, int totalDistributed) {

        if (totalDistributed <= 0) {
            return;
        }

        // Get entries with overtime (excluding CR/ZS/CN)
        List<WorkTimeTable> overtimeEntries = allEntries.stream()
            .filter(e -> isRegularWorkEntry(e.getTimeOffType()))
            .filter(e -> e.getTotalOvertimeMinutes() != null && e.getTotalOvertimeMinutes() > 0)
            .toList();

        if (overtimeEntries.isEmpty()) {
            LoggerUtil.warn(this.getClass(), "No overtime entries found to deduct from!");
            return;
        }

        int currentPool = overtimeEntries.stream()
            .mapToInt(WorkTimeTable::getTotalOvertimeMinutes)
            .sum();

        LoggerUtil.info(this.getClass(), String.format("Deducting %d minutes from %d overtime entries (pool: %d)",
            totalDistributed, overtimeEntries.size(), currentPool));

        int remaining = totalDistributed;

        // Deduct proportionally from entries with overtime
        for (WorkTimeTable entry : overtimeEntries) {
            if (remaining <= 0) break;

            int entryOvertime = entry.getTotalOvertimeMinutes();
            int deduction = Math.min(entryOvertime, remaining);

            entry.setTotalOvertimeMinutes(entryOvertime - deduction);
            remaining -= deduction;

            LoggerUtil.debug(this.getClass(), String.format("Deducted %d minutes from %s (was: %d, now: %d)",
                deduction, entry.getWorkDate(), entryOvertime, entry.getTotalOvertimeMinutes()));
        }

        if (remaining > 0) {
            LoggerUtil.warn(this.getClass(), String.format("Could not fully deduct overtime! Remaining: %d minutes", remaining));
        }
    }

    /**
     * Save consolidated entries (write-back to file)
     * Uses WorktimeCacheService's write-through mechanism: file first → cache second
     */
    private void saveConsolidatedEntries(String username, List<WorkTimeTable> entries, int year, int month) {
        try {
            // Get user ID
            Integer userId = getUserId(username);
            if (userId == null) {
                throw new RuntimeException("User ID not found for username: " + username);
            }

            // Use WorktimeCacheService's write-through pattern
            // This handles: write to file first, then update cache, with fallback
            boolean saved = worktimeCacheService.saveMonthEntriesWithWriteThrough(username, userId, year, month, entries);

            if (!saved) {
                throw new RuntimeException("Failed to save entries via WorktimeCacheService");
            }

            LoggerUtil.info(this.getClass(), String.format("Saved consolidated entries for %s - %d/%d", username, year, month));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Failed to save consolidated entries: %s", e.getMessage()), e);
            throw new RuntimeException("Failed to save consolidated entries", e);
        }
    }

    /**
     * Get user schedule (hours per day)
     */
    private int getUserSchedule(Integer userId) {
        try {
            Optional<User> userOpt = userService.getUserById(userId);
            if (userOpt.isPresent()) {
                Integer schedule = userOpt.get().getSchedule();
                return schedule != null ? schedule : WorkCode.INTERVAL_HOURS_C;
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error getting user schedule: %s", e.getMessage()));
        }
        return WorkCode.INTERVAL_HOURS_C; // Default 8 hours
    }

    /**
     * Get user ID from username
     */
    private Integer getUserId(String username) {
        try {
            Optional<User> userOpt = userService.getUserByUsername(username);
            if (userOpt.isPresent()) {
                return userOpt.get().getUserId();
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error getting user ID for %s: %s", username, e.getMessage()));
        }
        return null;
    }

    // ========================================================================
    // RESULT CLASSES
    // ========================================================================

    @Getter
    public static class ConsolidationResult {
        private final int crEntriesProcessed;
        private final int zsEntriesProcessed;
        private final int overtimeDistributed;
        private final int remainingOvertimePool;
        private final String message;

        public ConsolidationResult(int crEntriesProcessed, int zsEntriesProcessed, int overtimeDistributed,
                                  int remainingOvertimePool, String message) {
            this.crEntriesProcessed = crEntriesProcessed;
            this.zsEntriesProcessed = zsEntriesProcessed;
            this.overtimeDistributed = overtimeDistributed;
            this.remainingOvertimePool = remainingOvertimePool;
            this.message = message;
        }
    }

    @Getter
    public static class DetectionResult {
        private final int shortDaysDetected;
        private final String message;

        public DetectionResult(int shortDaysDetected, String message) {
            this.shortDaysDetected = shortDaysDetected;
            this.message = message;
        }
    }
}