package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.data.TimeOffDataService;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REFACTORED TimeOffManagementService - Pure Business Logic Orchestrator.
 * Key Principles:
 * - Central business logic for time-off operations
 * - Orchestrates between WorktimeManagementService, TimeOffDataService, and HolidayManagementService
 * - No direct file operations (uses TimeOffDataService)
 * - No caching (uses TimeOffCacheService when called from controller)
 * - Follows same pattern as register services
 * Responsibilities:
 * 1. Load tracker with worktime merge (main business operation)
 * 2. Process time-off requests
 * 3. Calculate summaries and statistics
 * 4. Manage holiday balances
 * 5. Validate business rules
 * Integration Pattern:
 * - WorktimeManagementService: Monthly worktime data (12 months)
 * - TimeOffDataService: Yearly tracker file operations
 * - HolidayManagementService: Holiday balance management
 */
@Service
public class TimeOffManagementService {

    private final TimeOffDataService timeOffDataService;
    private final WorktimeManagementService worktimeManagementService;
    private final HolidayManagementService holidayManagementService;


    public TimeOffManagementService(
            TimeOffDataService timeOffDataService,
            WorktimeManagementService worktimeManagementService,
            HolidayManagementService holidayManagementService) {
        this.timeOffDataService = timeOffDataService;
        this.worktimeManagementService = worktimeManagementService;
        this.holidayManagementService = holidayManagementService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // MAIN BUSINESS OPERATIONS
    // ========================================================================

    /**
     * Main method: Load tracker with complete worktime merge and synchronization.
     * This is the core business operation that orchestrates all the data sources.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return Complete time off tracker with merged worktime data
     */
    public TimeOffTracker getYearTracker(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Loading year tracker for %s - %d with worktime merge", username, year));

            String currentUsername = getCurrentUsername();

            if (currentUsername != null && currentUsername.equals(username)) {
                // Own data - perform full worktime merge
                return loadTrackerWithWorktimeMerge(username, userId, year);
            } else {
                // Other user's data - read-only from network
                return loadTrackerReadOnly(username, userId, year);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading year tracker for %s - %d: %s", username, year, e.getMessage()), e);
            return createEmptyTracker(username, userId, year);
        }
    }

    /**
     * Add time off request with business validation and processing.
     *
     * @param username Username
     * @param userId User ID
     * @param dates List of dates for time off
     * @param timeOffType Time off type (CO, SN, CM)
     * @return true if request was processed successfully
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

            // Load current tracker (with worktime merge)
            TimeOffTracker tracker = getYearTracker(username, userId, year);
            if (tracker == null) {
                tracker = createEmptyTracker(username, userId, year);
            }

            // Validate business rules
            if (!validateTimeOffRequest(dates, timeOffType, tracker)) {
                return false;
            }

            // Add requests to tracker
            if (!addRequestsToTracker(tracker, dates, timeOffType)) {
                return false;
            }

            // Update holiday balance if CO request
            if (!updateHolidayBalanceForNewRequests(tracker, dates, timeOffType)) {
                return false;
            }

            // Save updated tracker
            timeOffDataService.writeUserLocalTrackerWithSyncAndBackup(username, userId, tracker, year);

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
     * Calculate time off summary from tracker data.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return Time off summary
     */
    public TimeOffSummaryDTO calculateTimeOffSummary(String username, Integer userId, int year) {
        try {
            String currentUsername = getCurrentUsername();

            TimeOffTracker tracker;
            if (currentUsername != null && currentUsername.equals(username)) {
                // Own data - use full tracker with worktime merge
                tracker = getYearTracker(username, userId, year);
            } else {
                // Other user's data - read-only from network
                tracker = loadTrackerReadOnly(username, userId, year);
            }
            return calculateSummaryFromTracker(tracker);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error calculating time off summary for %s - %d: %s", username, year, e.getMessage()));
            return createEmptySummary();
        }
    }

    /**
     * Get upcoming time off entries from tracker.
     *
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @return List of upcoming time off entries
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
    // CORE BUSINESS LOGIC - WORKTIME MERGE
    // ========================================================================

    /**
     * Load tracker with complete worktime merge - the core business operation.
     * This method orchestrates all data sources to create a complete picture.
     */
    private TimeOffTracker loadTrackerWithWorktimeMerge(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Starting worktime merge and tracker sync for %s - %d", username, year));

            // Step 1: Scan all 12 months and extract time-off entries from worktime
            List<WorkTimeTable> allTimeOffEntries = scanAllMonthsForTimeOff(username, year);

            // Step 2: Load existing tracker or create new one
            String currentUsername = getCurrentUsername();
            TimeOffTracker tracker = timeOffDataService.readUserLocalTrackerReadOnly(username, userId, currentUsername, year);

            if (tracker == null) {
                tracker = createEmptyTracker(username, userId, year);
                LoggerUtil.info(this.getClass(), String.format(
                        "Created new tracker for %s - %d", username, year));
            }

            // Step 3: Sync tracker with worktime data (core business logic)
            syncTrackerWithWorktime(tracker, allTimeOffEntries);

            // Step 4: Update holiday balances
            updateHolidayBalanceFromTracker(tracker);

            // Step 5: Save updated tracker
            timeOffDataService.writeUserLocalTrackerWithSyncAndBackup(username, userId, tracker, year);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully completed worktime merge for %s - %d with %d total requests",
                    username, year, tracker.getRequests().size()));

            return tracker;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error in worktime merge for %s - %d: %s", username, year, e.getMessage()), e);
            return createEmptyTracker(username, userId, year);
        }
    }

    /**
     * Scan all 12 months for time-off entries using WorktimeManagementService.
     */
    private List<WorkTimeTable> scanAllMonthsForTimeOff(String username, int year) {
        List<WorkTimeTable> allTimeOffEntries = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            try {
                // Use WorktimeManagementService to get merged worktime data
                List<WorkTimeTable> monthWorktime = worktimeManagementService.loadMonthWorktime(username, year, month);

                if (monthWorktime != null && !monthWorktime.isEmpty()) {
                    // Extract only time-off entries (CO, SN, CM)
                    List<WorkTimeTable> timeOffEntries = monthWorktime.stream()
                            .filter(entry -> entry.getTimeOffType() != null)
                            .filter(entry -> isValidTimeOffType(entry.getTimeOffType()))
                            .toList();

                    allTimeOffEntries.addAll(timeOffEntries);

                    LoggerUtil.debug(this.getClass(), String.format(
                            "Extracted %d time off entries from %s - %d/%d",
                            timeOffEntries.size(), username, year, month));
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to load worktime for %s - %d/%d: %s", username, year, month, e.getMessage()));
                // Continue with other months
            }
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Scanned all months for %s - %d, found %d total time-off entries",
                username, year, allTimeOffEntries.size()));

        return allTimeOffEntries;
    }

    /**
     * Sync tracker with worktime data - core business logic.
     */
    private void syncTrackerWithWorktime(TimeOffTracker tracker, List<WorkTimeTable> timeOffEntries) {
        try {
            // Create map of existing requests for efficient lookup
            Map<LocalDate, TimeOffTracker.TimeOffRequest> existingRequests = tracker.getRequests().stream()
                    .collect(Collectors.toMap(
                            TimeOffTracker.TimeOffRequest::getDate,
                            request -> request,
                            (existing, replacement) -> existing));

            int newRequestsAdded = 0;
            int requestsUpdated = 0;

            // Process worktime entries
            for (WorkTimeTable entry : timeOffEntries) {
                LocalDate date = entry.getWorkDate();
                String type = entry.getTimeOffType();

                TimeOffTracker.TimeOffRequest existingRequest = existingRequests.get(date);

                if (existingRequest == null) {
                    // Create new request
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
                            "Added new time off request: %s - %s", date, type));

                } else if (!type.equals(existingRequest.getTimeOffType())) {
                    // Update existing request type
                    existingRequest.setTimeOffType(type);
                    existingRequest.setLastUpdated(LocalDateTime.now());
                    existingRequest.setNotes("Updated from worktime data");
                    requestsUpdated++;

                    LoggerUtil.debug(this.getClass(), String.format(
                            "Updated time off request: %s - %s", date, type));
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
    // BUSINESS VALIDATION AND RULES
    // ========================================================================

    /**
     * Validate time off request using business rules.
     */
    private boolean validateTimeOffRequest(List<LocalDate> dates, String timeOffType, TimeOffTracker tracker) {
        try {
            if (dates == null || dates.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "Cannot validate empty date list");
                return false;
            }

            // Validate time off type
            if (!isValidTimeOffType(timeOffType)) {
                LoggerUtil.warn(this.getClass(), "Invalid time off type: " + timeOffType);
                return false;
            }

            // For CO requests, validate holiday balance
            if ("CO".equals(timeOffType)) {
                int requiredDays = dates.size();
                int availableDays = tracker.getAvailableHolidayDays();

                if (requiredDays > availableDays) {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Insufficient holiday days: required %d, available %d", requiredDays, availableDays));
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
     * Add requests to tracker.
     */
    private boolean addRequestsToTracker(TimeOffTracker tracker, List<LocalDate> dates, String timeOffType) {
        try {
            for (LocalDate date : dates) {
                // Check if request already exists for this date
                boolean exists = tracker.getRequests().stream()
                        .anyMatch(r -> r.getDate().equals(date));

                if (!exists) {
                    TimeOffTracker.TimeOffRequest request = TimeOffTracker.TimeOffRequest.builder()
                            .requestId(UUID.randomUUID().toString())
                            .date(date)
                            .timeOffType(timeOffType)
                            .status("APPROVED")
                            .createdAt(LocalDateTime.now())
                            .lastUpdated(LocalDateTime.now())
                            .notes("Added via time off request")
                            .build();

                    tracker.getRequests().add(request);
                }
            }

            tracker.setLastSyncTime(LocalDateTime.now());
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error adding requests to tracker: %s", e.getMessage()));
            return false;
        }
    }

    /**
     * Update holiday balance for new CO requests.
     */
    private boolean updateHolidayBalanceForNewRequests(TimeOffTracker tracker, List<LocalDate> dates, String timeOffType) {
        try {
            if ("CO".equals(timeOffType)) {
                int daysUsed = dates.size();
                int currentUsed = tracker.getUsedHolidayDays();
                int currentAvailable = tracker.getAvailableHolidayDays();

                tracker.setUsedHolidayDays(currentUsed + daysUsed);
                tracker.setAvailableHolidayDays(currentAvailable - daysUsed);

                LoggerUtil.info(this.getClass(), String.format("Updated holiday balance: used %d days, new balance: %d available", daysUsed, tracker.getAvailableHolidayDays()));
            }
            return true;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating holiday balance: " + e.getMessage());
            return false;
        }
    }

    /**
     * Update holiday balance from tracker data using HolidayManagementService.
     */
    private void updateHolidayBalanceFromTracker(TimeOffTracker tracker) {
        try {
            // Count approved CO requests
            int approvedCoDays = (int) tracker.getRequests().stream()
                    .filter(r -> "APPROVED".equals(r.getStatus()))
                    .filter(r -> "CO".equals(r.getTimeOffType()))
                    .count();

            // Get total holiday days from holiday service
            int totalHolidayDays = holidayManagementService.getRemainingHolidayDays(tracker.getUserId());

            // Update tracker balance
            tracker.setUsedHolidayDays(approvedCoDays);
            tracker.setAvailableHolidayDays(Math.max(0, totalHolidayDays - approvedCoDays));

            LoggerUtil.debug(this.getClass(), String.format(
                    "Updated holiday balance for %s: used=%d, available=%d, total=%d",
                    tracker.getUsername(), approvedCoDays, tracker.getAvailableHolidayDays(), totalHolidayDays));

        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error updating holiday balance: %s", e.getMessage()));
        }
    }

    // ========================================================================
    // READ-ONLY OPERATIONS
    // ========================================================================

    /**
     * Load tracker in read-only mode (for other users or admin view).
     */
    private TimeOffTracker loadTrackerReadOnly(String username, Integer userId, int year) {
        try {
            // Read directly from network without any sync or modification
            return timeOffDataService.readTrackerFromNetworkReadOnly(username, userId, year);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading tracker read-only for %s - %d: %s", username, year, e.getMessage()));
            return null;
        }
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Calculate summary from tracker.
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
                    .availablePaidDays(tracker.getAvailableHolidayDays() + tracker.getUsedHolidayDays())
                    .build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating summary from tracker: " + e.getMessage());
            return createEmptySummary();
        }
    }

    /**
     * Create empty tracker.
     */
    private TimeOffTracker createEmptyTracker(String username, Integer userId, int year) {
        try {
            int availableDays = holidayManagementService.getRemainingHolidayDays(userId);

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
            return TimeOffTracker.builder()
                    .userId(userId)
                    .username(username)
                    .requests(new ArrayList<>())
                    .lastSyncTime(LocalDateTime.now())
                    .year(year)
                    .availableHolidayDays(0)
                    .usedHolidayDays(0)
                    .build();
        }
    }

    /**
     * Create empty summary.
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
     * Validate time off type.
     */
    private boolean isValidTimeOffType(String timeOffType) {
        return ("CO".equals(timeOffType) || "SN".equals(timeOffType) || "CM".equals(timeOffType));
    }

    /**
     * Get current username from security context.
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