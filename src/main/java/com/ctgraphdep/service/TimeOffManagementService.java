package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.data.TimeOffDataService;
import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.security.UserContextCache;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.enums.SyncStatusMerge;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REFACTORED TimeOffManagementService - Clean Architecture Implementation.
 * Key Principles:
 * 1. Worktime files are monthly-based, merged once at login (source of truth)
 * 2. TimeOff tracker is yearly-based (display/summary layer)
 * 3. User files are persistent (only admin changes network, user works with local)
 * Flow:
 * - Login: Merge admin network + user local worktime → final local worktime
 * - Page Load: Build tracker from final worktime files
 * - Time Off Request: Update worktime → update balance → update tracker
 */
@Service
public class TimeOffManagementService {

    private final TimeOffDataService timeOffDataService;
    private final WorktimeDataService worktimeDataService;
    private final UserContextCache userContextCache;

    public TimeOffManagementService(
            TimeOffDataService timeOffDataService,
            WorktimeDataService worktimeDataService,
            UserContextCache userContextCache) {
        this.timeOffDataService = timeOffDataService;
        this.worktimeDataService = worktimeDataService;
        this.userContextCache = userContextCache;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // MAIN BUSINESS OPERATIONS - CLEAN ARCHITECTURE
    // ========================================================================

    /**
     * Get year tracker - builds from final worktime files (already merged at login)
     * This is the main entry point for timeoff page display
     */
    public TimeOffTracker getYearTracker(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Building year tracker for %s - %d from final worktime files", username, year));

            String currentUsername = getCurrentUsername();

            // Read all 12 months of final worktime files (already merged at login)
            List<WorkTimeTable> allTimeOffEntries = scanAllMonthsForTimeOff(username, year, currentUsername);

            // Load or create tracker
            TimeOffTracker tracker = timeOffDataService.readUserLocalTrackerReadOnly(username, userId, currentUsername, year);
            if (tracker == null) {
                tracker = createEmptyTracker(username, userId, year);
            }

            // Build tracker from worktime data (source of truth)
            syncTrackerWithWorktime(tracker, allTimeOffEntries);

            // Update holiday balances from user file
            updateHolidayBalanceFromUserFile(tracker);

            // Save updated tracker
            timeOffDataService.writeUserLocalTrackerWithSyncAndBackup(username, userId, tracker, year);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully built tracker for %s - %d with %d total requests",
                    username, year, tracker.getRequests().size()));

            return tracker;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error building year tracker for %s - %d: %s", username, year, e.getMessage()), e);
            return createEmptyTracker(username, userId, year);
        }
    }

    /**
     * Add time off request - CLEAN FLOW: worktime → balance → tracker
     */
    public boolean addTimeOffRequest(String username, Integer userId, List<LocalDate> dates, String timeOffType) {
        try {
            if (dates == null || dates.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Cannot add time off request with empty dates");
                return false;
            }

            int year = dates.get(0).getYear();

            LoggerUtil.info(this.getClass(), String.format(
                    "Adding time off request for %s - %d dates, type %s", username, dates.size(), timeOffType));

            // STEP 1: Validate business rules first
            if (!validateTimeOffRequest(dates, timeOffType)) {
                return false;
            }

            // STEP 2: Update worktime files (source of truth)
            if (!updateWorktimeFiles(username, userId, dates, timeOffType)) {
                LoggerUtil.error(this.getClass(), "Failed to update worktime files");
                return false;
            }

            // STEP 3: Update user balance via UserContextCache (write-through)
            if (!updateUserBalance(username, dates, timeOffType)) {
                LoggerUtil.error(this.getClass(), "Failed to update user balance");
                return false;
            }

            // STEP 4: Update tracker from updated worktime files
            if (!updateTrackerFromWorktime(username, userId, year)) {
                LoggerUtil.error(this.getClass(), "Failed to update tracker");
                return false;
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully added %d time off requests for %s - %d (%s)",
                    dates.size(), username, year, timeOffType));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error adding time off request for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Calculate time off summary from tracker
     */
    public TimeOffSummaryDTO calculateTimeOffSummary(String username, Integer userId, int year) {
        try {
            TimeOffTracker tracker = getYearTracker(username, userId, year);
            return calculateSummaryFromTracker(tracker);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error calculating time off summary for %s - %d: %s", username, year, e.getMessage()));
            return createEmptySummary();
        }
    }

    /**
     * Get upcoming time off entries from tracker
     */
    public List<WorkTimeTable> getUpcomingTimeOff(String username, Integer userId, int year) {
        try {
            TimeOffTracker tracker = getYearTracker(username, userId, year);
            if (tracker == null || tracker.getRequests() == null) {
                return new ArrayList<>();
            }

            LocalDate today = LocalDate.now();

            return tracker.getRequests().stream()
                    .filter(r -> "APPROVED".equals(r.getStatus()))
                    .filter(r -> r.getDate().isAfter(today) || r.getDate().equals(today))
                    .map(r -> {
                        WorkTimeTable entry = new WorkTimeTable();
                        entry.setUserId(userId);
                        entry.setWorkDate(r.getDate());
                        entry.setTimeOffType(r.getTimeOffType());
                        return entry;
                    })
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting upcoming time off for %s - %d: %s", username, year, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // ========================================================================
    // STEP IMPLEMENTATIONS - CLEAN SEPARATION
    // ========================================================================

    /**
     * STEP 1: Validate time off request
     */
    private boolean validateTimeOffRequest(List<LocalDate> dates, String timeOffType) {
        try {
            // Validate time off type
            if (!isValidTimeOffType(timeOffType)) {
                LoggerUtil.warn(this.getClass(), "Invalid time off type: " + timeOffType);
                return false;
            }

            // For CO requests, validate holiday balance
            if ("CO".equals(timeOffType)) {
                Integer currentBalance = userContextCache.getCurrentPaidHolidayDays();
                if (currentBalance == null || currentBalance < dates.size()) {
                    LoggerUtil.warn(this.getClass(), String.format("Insufficient holiday days: required %d, available %d", dates.size(), currentBalance));
                    return false;
                }
            }

            // Validate dates are not in the past (with some tolerance)
            LocalDate earliestAllowed = LocalDate.now().minusDays(7); // Allow 1 week back
            for (LocalDate date : dates) {
                if (date.isBefore(earliestAllowed)) {
                    LoggerUtil.warn(this.getClass(), "Cannot add time off for date in the past: " + date);
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error validating time off request: " + e.getMessage());
            return false;
        }
    }

    /**
     * STEP 2: Update worktime files with CO/CM entries
     */
    private boolean updateWorktimeFiles(String username, Integer userId, List<LocalDate> dates, String timeOffType) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Creating %d worktime entries for %s (%s)", dates.size(), username, timeOffType));

            // Group dates by month for efficient file operations
            Map<YearMonth, List<LocalDate>> datesByMonth = dates.stream().collect(Collectors.groupingBy(YearMonth::from));

            // Process each month
            for (Map.Entry<YearMonth, List<LocalDate>> entry : datesByMonth.entrySet()) {
                YearMonth yearMonth = entry.getKey();
                List<LocalDate> monthDates = entry.getValue();

                if (!updateMonthWorktimeFile(username, userId, yearMonth, monthDates, timeOffType)) {
                    LoggerUtil.error(this.getClass(), String.format("Failed to update worktime file for %s - %d/%d", username, yearMonth.getYear(), yearMonth.getMonthValue()));
                    return false;
                }
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully updated worktime files for %s (%d months)", username, datesByMonth.size()));
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating worktime files for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * Update specific month's worktime file
     */
    private boolean updateMonthWorktimeFile(String username, Integer userId, YearMonth yearMonth, List<LocalDate> dates, String timeOffType) {
        try {
            int year = yearMonth.getYear();
            int month = yearMonth.getMonthValue();

            // Read existing worktime entries for this month (final, merged data)
            String currentUsername = getCurrentUsername();
            List<WorkTimeTable> existingEntries = worktimeDataService.readUserLocalReadOnly(username, year, month, currentUsername);

            // Create new CO/CM entries
            List<WorkTimeTable> newEntries = new ArrayList<>();
            for (LocalDate date : dates) {
                // Check if entry already exists for this date
                boolean exists = existingEntries.stream().anyMatch(e -> e.getUserId().equals(userId) && e.getWorkDate().equals(date));

                if (!exists) {
                    WorkTimeTable timeOffEntry = createTimeOffEntry(userId, date, timeOffType);
                    newEntries.add(timeOffEntry);
                } else {
                    LoggerUtil.warn(this.getClass(), String.format("Entry already exists for %s on %s, skipping", username, date));
                }
            }

            if (newEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No new entries to add for %s - %d/%d", username, year, month));
                return true;
            }

            // Add new entries to existing ones
            existingEntries.addAll(newEntries);
            existingEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));

            // Write back to worktime file
            worktimeDataService.writeUserLocalWithSyncAndBackup(username, existingEntries, year, month);

            LoggerUtil.info(this.getClass(), String.format("Added %d time off entries to %s - %d/%d", newEntries.size(), username, year, month));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating month worktime file for %s - %d/%d: %s", username, yearMonth.getYear(), yearMonth.getMonthValue(), e.getMessage()), e);
            return false;
        }
    }

    /**
     * Create time off entry for worktime file
     */
    private WorkTimeTable createTimeOffEntry(Integer userId, LocalDate date, String timeOffType) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(date);
        entry.setTimeOffType(timeOffType);
        entry.setAdminSync(SyncStatusMerge.USER_INPUT);

        // Reset work-related fields for time off
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);

        return entry;
    }

    /**
     * STEP 3: Update user balance via UserContextCache (write-through)
     */
    private boolean updateUserBalance(String username, List<LocalDate> dates, String timeOffType) {
        try {
            // Only update balance for CO (paid vacation)
            if (!"CO".equals(timeOffType)) {
                LoggerUtil.debug(this.getClass(), String.format("No balance update needed for %s time off", timeOffType));
                return true;
            }

            int daysToDeduct = dates.size();

            // Use UserContextCache write-through to update balance
            boolean success = userContextCache.reducePaidHolidayDays(daysToDeduct);

            if (success) {
                LoggerUtil.info(this.getClass(), String.format("Successfully reduced holiday balance by %d days for %s", daysToDeduct, username));
                return true;
            } else {
                LoggerUtil.error(this.getClass(), String.format("Failed to reduce holiday balance for %s", username));
                return false;
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating user balance for %s: %s", username, e.getMessage()), e);
            return false;
        }
    }

    /**
     * STEP 4: Update tracker from updated worktime files
     */
    private boolean updateTrackerFromWorktime(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Updating tracker from worktime files for %s - %d", username, year));

            // This will rebuild the tracker from the updated worktime files
            TimeOffTracker updatedTracker = getYearTracker(username, userId, year);

            return updatedTracker != null;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating tracker from worktime for %s - %d: %s", username, year, e.getMessage()), e);
            return false;
        }
    }

    // ========================================================================
    // CORE BUSINESS LOGIC - WORKTIME SCANNING
    // ========================================================================

    /**
     * Scan all 12 months for time-off entries from final worktime files
     */
    private List<WorkTimeTable> scanAllMonthsForTimeOff(String username, int year, String currentUsername) {
        List<WorkTimeTable> allTimeOffEntries = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            try {
                // Read final worktime file (already merged at login)
                List<WorkTimeTable> monthWorktime = worktimeDataService.readUserLocalReadOnly(
                        username, year, month, currentUsername);

                if (monthWorktime != null && !monthWorktime.isEmpty()) {
                    // Extract only time-off entries (CO, SN, CM)
                    List<WorkTimeTable> timeOffEntries = monthWorktime.stream()
                            .filter(entry -> entry.getTimeOffType() != null)
                            .filter(entry -> isValidTimeOffType(entry.getTimeOffType()))
                            .toList();

                    allTimeOffEntries.addAll(timeOffEntries);

                    LoggerUtil.debug(this.getClass(), String.format("Extracted %d time off entries from %s - %d/%d", timeOffEntries.size(), username, year, month));
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to load worktime for %s - %d/%d: %s", username, year, month, e.getMessage()));
                // Continue with other months
            }
        }

        LoggerUtil.info(this.getClass(), String.format("Scanned all months for %s - %d, found %d total time-off entries", username, year, allTimeOffEntries.size()));

        return allTimeOffEntries;
    }

    /**
     * Sync tracker with worktime data (build display layer)
     */
    private void syncTrackerWithWorktime(TimeOffTracker tracker, List<WorkTimeTable> timeOffEntries) {
        try {
            // Create map of existing requests for efficient lookup
            Map<LocalDate, TimeOffTracker.TimeOffRequest> existingRequests = tracker.getRequests().stream().collect(Collectors.toMap(TimeOffTracker.TimeOffRequest::getDate,
                    request -> request, (existing, replacement) -> existing));

            int newRequestsAdded = 0;
            int requestsUpdated = 0;

            // Process worktime entries to build/update tracker
            for (WorkTimeTable entry : timeOffEntries) {
                LocalDate date = entry.getWorkDate();
                String type = entry.getTimeOffType();

                TimeOffTracker.TimeOffRequest existingRequest = existingRequests.get(date);

                if (existingRequest == null) {
                    // Create new request in tracker
                    TimeOffTracker.TimeOffRequest newRequest = TimeOffTracker.TimeOffRequest.builder()
                            .requestId(UUID.randomUUID().toString())
                            .date(date)
                            .timeOffType(type)
                            .status("APPROVED")
                            .createdAt(LocalDateTime.now())
                            .lastUpdated(LocalDateTime.now())
                            .notes("Synced from worktime data")
                            .build();

                    tracker.getRequests().add(newRequest);
                    existingRequests.put(date, newRequest);
                    newRequestsAdded++;

                    LoggerUtil.debug(this.getClass(), String.format(
                            "Added new tracker request: %s - %s", date, type));

                } else if (!type.equals(existingRequest.getTimeOffType())) {
                    // Update existing request type
                    existingRequest.setTimeOffType(type);
                    existingRequest.setLastUpdated(LocalDateTime.now());
                    existingRequest.setNotes("Updated from worktime data");
                    requestsUpdated++;

                    LoggerUtil.debug(this.getClass(), String.format(
                            "Updated tracker request: %s - %s", date, type));
                }
            }

            // Update sync time
            tracker.setLastSyncTime(LocalDateTime.now());

            LoggerUtil.info(this.getClass(), String.format(
                    "Tracker sync completed: %d new requests added, %d requests updated",
                    newRequestsAdded, requestsUpdated));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error syncing tracker with worktime: %s", e.getMessage()), e);
            throw new RuntimeException("Failed to sync tracker with worktime data", e);
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Update holiday balance from user file (not tracker)
     */
    private void updateHolidayBalanceFromUserFile(TimeOffTracker tracker) {
        try {
            // Get current balance from UserContextCache (reads from user file)
            Integer currentBalance = userContextCache.getCurrentPaidHolidayDays();
            if (currentBalance == null) {
                currentBalance = 0;
            }

            // Count approved CO requests in tracker
            int usedCoDays = (int) tracker.getRequests().stream()
                    .filter(r -> "APPROVED".equals(r.getStatus()))
                    .filter(r -> "CO".equals(r.getTimeOffType()))
                    .count();

            // Update tracker balance (for display)
            tracker.setUsedHolidayDays(usedCoDays);
            tracker.setAvailableHolidayDays(Math.max(0, currentBalance));

            LoggerUtil.debug(this.getClass(), String.format(
                    "Updated tracker balance: used=%d CO days, available=%d days",
                    usedCoDays, currentBalance));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error updating holiday balance: %s", e.getMessage()));
        }
    }

    /**
     * Create empty tracker
     */
    private TimeOffTracker createEmptyTracker(String username, Integer userId, int year) {
        try {
            Integer availableDays = userContextCache.getCurrentPaidHolidayDays();
            if (availableDays == null) {
                availableDays = 0;
            }

            return TimeOffTracker.builder()
                    .userId(userId)
                    .username(username)
                    .requests(new ArrayList<>())
                    .lastSyncTime(LocalDateTime.now())
                    .year(year)
                    .availableHolidayDays(availableDays)
                    .usedHolidayDays(0)
                    .build();

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error creating empty tracker for %s: %s", username, e.getMessage()));
            return new TimeOffTracker();
        }
    }

    /**
     * Calculate summary from tracker
     */
    private TimeOffSummaryDTO calculateSummaryFromTracker(TimeOffTracker tracker) {
        if (tracker == null || tracker.getRequests() == null) {
            return createEmptySummary();
        }

        try {
            int coDays = (int) tracker.getRequests().stream()
                    .filter(r -> "APPROVED".equals(r.getStatus()))
                    .filter(r -> "CO".equals(r.getTimeOffType()))
                    .count();

            int snDays = (int) tracker.getRequests().stream()
                    .filter(r -> "APPROVED".equals(r.getStatus()))
                    .filter(r -> "SN".equals(r.getTimeOffType()))
                    .count();

            int cmDays = (int) tracker.getRequests().stream()
                    .filter(r -> "APPROVED".equals(r.getStatus()))
                    .filter(r -> "CM".equals(r.getTimeOffType()))
                    .count();

            return TimeOffSummaryDTO.builder()
                    .coDays(coDays)
                    .snDays(snDays)
                    .cmDays(cmDays)
                    .paidDaysTaken(tracker.getUsedHolidayDays())
                    .remainingPaidDays(tracker.getAvailableHolidayDays())
                    .availablePaidDays(tracker.getAvailableHolidayDays())
                    .build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating summary from tracker: " + e.getMessage());
            return createEmptySummary();
        }
    }

    /**
     * Create empty summary
     */
    private TimeOffSummaryDTO createEmptySummary() {
        return TimeOffSummaryDTO.builder()
                .coDays(0)
                .snDays(0)
                .cmDays(0)
                .paidDaysTaken(0)
                .remainingPaidDays(0)
                .availablePaidDays(0)
                .build();
    }

    /**
     * Validate time off type
     */
    private boolean isValidTimeOffType(String timeOffType) {
        return ("CO".equals(timeOffType) || "SN".equals(timeOffType) || "CM".equals(timeOffType));
    }

    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}