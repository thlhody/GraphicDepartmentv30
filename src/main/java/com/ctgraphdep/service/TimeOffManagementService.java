package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.WorktimeEntryUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeOffRequestValidator;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Comprehensive service for managing time-off related operations.
 * This consolidated service combines functionality from:
 * - TimeOffTrackerService
 * - UserTimeOffService
 * - HolidayManagementService (partial)
 */
@Service
public class TimeOffManagementService {

    private final DataAccessService dataAccessService;
    private final HolidayManagementService holidayManagementService;
    private final TimeValidationService timeValidationService;
    private final WorktimeManagementService worktimeManagementService;
    private final TimeOffRequestValidator timeOffValidator;
    private final UserService userService;

    private final Map<String, ReentrantReadWriteLock> userTrackerLocks = new HashMap<>();

    public TimeOffManagementService(
            DataAccessService dataAccessService,
            HolidayManagementService holidayManagementService,
            TimeValidationService timeValidationService,
            WorktimeManagementService worktimeManagementService,
            TimeOffRequestValidator timeOffValidator, UserService userService) {
        this.dataAccessService = dataAccessService;
        this.holidayManagementService = holidayManagementService;
        this.timeValidationService = timeValidationService;
        this.worktimeManagementService = worktimeManagementService;
        this.timeOffValidator = timeOffValidator;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ============= TRACKER MANAGEMENT =============

    /**
     * Ensures a tracker exists for a user for a specific year
     */
    public void ensureTrackerExists(User user, Integer userId, int year) {
        // Try to load existing tracker
        TimeOffTracker tracker = dataAccessService.readTimeOffTracker(user.getUsername(), userId, year);

        // If tracker doesn't exist, initialize it
        if (tracker == null) {
            int availableDays = holidayManagementService.getRemainingHolidayDays(user.getUsername(), userId);

            // Create new tracker with holiday balance
            tracker = TimeOffTracker.builder()
                    .userId(userId)
                    .username(user.getUsername())
                    .requests(new ArrayList<>())
                    .lastSyncTime(getStandardTimeValues().getCurrentTime())
                    .year(year)
                    .availableHolidayDays(availableDays)
                    .usedHolidayDays(0)
                    .build();

            // Save the new tracker
            dataAccessService.writeTimeOffTracker(tracker, year);
        }

        // Sync tracker with worktime files
        List<WorkTimeTable> timeOffEntries = loadAllTimeOffEntries(user.getUsername(), year);
        syncTimeOffTracker(user, year, timeOffEntries);
    }

    /**
     * Load the time off tracker for a user for a specific year
     */
    public TimeOffTracker loadTimeOffTracker(String username, Integer userId, int year) {
        ReentrantReadWriteLock.ReadLock readLock = getUserTrackerLock(username).readLock();
        readLock.lock();
        try {
            // Read the tracker using DataAccessService
            TimeOffTracker tracker = dataAccessService.readTimeOffTracker(username, userId, year);

            // If no tracker exists yet, create a new one
            if (tracker == null) {
                LocalDateTime currentTime = getStandardTimeValues().getCurrentTime();

                // Initialize with holiday days from the holiday service
                int availableDays = holidayManagementService.getRemainingHolidayDays(username, userId);

                TimeOffTracker newTracker = TimeOffTracker.builder()
                        .userId(userId)
                        .username(username)
                        .requests(new ArrayList<>())
                        .lastSyncTime(currentTime)
                        .year(year)
                        .availableHolidayDays(availableDays)
                        .usedHolidayDays(0)
                        .build();

                // Save the new tracker
                dataAccessService.writeTimeOffTracker(newTracker, year);
                return newTracker;
            }

            // Get the latest available days from holiday service
            int currentAvailableDays = holidayManagementService.getRemainingHolidayDays(username, userId);

            // Check if holiday allocation has changed
            int totalDays = tracker.getAvailableHolidayDays() + tracker.getUsedHolidayDays();
            if (currentAvailableDays != totalDays - tracker.getUsedHolidayDays()) {
                // Days have been updated by admin, update the tracker
                LoggerUtil.info(this.getClass(), String.format("Holiday allocation updated for user %s. Old: %d, New: %d", username, tracker.getAvailableHolidayDays(), currentAvailableDays));
                tracker.setAvailableHolidayDays(currentAvailableDays);

                // Save the updated tracker
                dataAccessService.writeTimeOffTracker(tracker, year);
            }

            return tracker;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading time off tracker for %s (year %d): %s", username, year, e.getMessage()));

            // Create default tracker on error
            LocalDateTime currentTime = getStandardTimeValues().getCurrentTime();
            int availableDays = 0;
            try {
                availableDays = holidayManagementService.getRemainingHolidayDays(username, userId);
            } catch (Exception ex) {
                LoggerUtil.error(this.getClass(), "Failed to get holiday days: " + ex.getMessage());
            }

            return TimeOffTracker.builder()
                    .userId(userId)
                    .username(username)
                    .requests(new ArrayList<>())
                    .lastSyncTime(currentTime)
                    .year(year)
                    .availableHolidayDays(availableDays)
                    .usedHolidayDays(0)
                    .build();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Save the time off tracker for a specific year
     */
    public void saveTimeOffTracker(TimeOffTracker tracker, int year) {
        if (tracker == null || tracker.getUsername() == null) {
            LoggerUtil.error(this.getClass(), "Cannot save null tracker or tracker without username");
            return;
        }

        ReentrantReadWriteLock.WriteLock writeLock = getUserTrackerLock(tracker.getUsername()).writeLock();
        writeLock.lock();
        try {
            dataAccessService.writeTimeOffTracker(tracker, year);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving time off tracker for %s (year %d): %s",
                    tracker.getUsername(), year, e.getMessage()));
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Get the lock for a user's tracker file
     */
    private ReentrantReadWriteLock getUserTrackerLock(String username) {
        return userTrackerLocks.computeIfAbsent(username, k -> new ReentrantReadWriteLock());
    }

    // ============= TIME OFF REQUEST HANDLING =============

    /**
     * Process a time-off request for a user
     */
    @PreAuthorize("#user.username == authentication.name")
    public void processTimeOffRequest(User user, LocalDate startDate, LocalDate endDate, String timeOffType) {
        if (startDate == null || endDate == null || timeOffType == null) {
            throw new IllegalArgumentException("Start date, end date, and time-off type are required");
        }

        int availableDays = holidayManagementService.getRemainingHolidayDays(user.getUserId());

        // Validate request using validator
        TimeOffRequestValidator.ValidationResult validationResult =
                timeOffValidator.validateRequest(startDate, endDate, timeOffType, availableDays);

        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.getErrorMessage());
        }

        // Get valid workdays in the range (excluding weekends and holidays)
        List<LocalDate> validWorkDays = calculateValidWorkDays(startDate, endDate);

        // Add time off entries to the tracker
        addTimeOffRequests(user, validWorkDays, timeOffType);

        // Update worktime files for each day
        updateWorktimeWithTimeOff(user, validWorkDays, timeOffType);

        LoggerUtil.info(this.getClass(),
                String.format("Time off request processed for %s from %s to %s (%d days)",
                        user.getUsername(), startDate, endDate, validWorkDays.size()));
    }

    /**
     * Add time off requests to the tracker
     */
    private void addTimeOffRequests(User user, List<LocalDate> validDates, String timeOffType) {
        if (validDates.isEmpty()) {
            return;
        }

        // Extract year from first date (all dates should be in same year)
        int year = validDates.get(0).getYear();

        ReentrantReadWriteLock.WriteLock writeLock = getUserTrackerLock(user.getUsername()).writeLock();
        writeLock.lock();
        try {
            // Load current tracker for the relevant year
            TimeOffTracker tracker = loadTimeOffTracker(user.getUsername(), user.getUserId(), year);

            // Calculate how many CO days we're adding
            int coDaysToAdd = 0;
            if ("CO".equals(timeOffType)) {
                coDaysToAdd = validDates.size();
            }

            // Update holiday balance if this is a CO request
            if ("CO".equals(timeOffType)) {
                // Update holiday days
                int newUsedDays = tracker.getUsedHolidayDays() + coDaysToAdd;
                int newAvailableDays = tracker.getAvailableHolidayDays() - coDaysToAdd;

                tracker.setUsedHolidayDays(newUsedDays);
                tracker.setAvailableHolidayDays(newAvailableDays);

                // Also update the holiday service (will write to holiday.json)
                holidayManagementService.updateUserHolidayDays(user.getUserId(), newAvailableDays);
            }

            // Create individual requests for each day
            for (LocalDate date : validDates) {
                TimeOffTracker.TimeOffRequest request = createTimeOffRequest(date, timeOffType);
                tracker.getRequests().add(request);

                LoggerUtil.info(this.getClass(),
                        String.format("Added time off request for %s: %s (%s)",
                                user.getUsername(), date, timeOffType));
            }

            // Update last sync time
            tracker.setLastSyncTime(getStandardTimeValues().getCurrentTime());

            // Save the updated tracker
            saveTimeOffTracker(tracker, year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error adding time off requests for %s: %s",
                            user.getUsername(), e.getMessage()));
            throw new RuntimeException("Failed to process time off requests", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Update worktime files with time off entries
     */
    private void updateWorktimeWithTimeOff(User user, List<LocalDate> dates, String timeOffType) {
        // Group dates by month for efficient processing
        Map<YearMonth, List<LocalDate>> datesByMonth = dates.stream()
                .collect(Collectors.groupingBy(YearMonth::from));

        // Process each month
        datesByMonth.forEach((yearMonth, monthDates) -> {
            try {
                int year = yearMonth.getYear();
                int month = yearMonth.getMonthValue();

                // Load existing worktime entries for the month
                List<WorkTimeTable> entries = dataAccessService.readUserWorktime(user.getUsername(), year, month);
                if (entries == null) {
                    entries = new ArrayList<>();
                }

                // Create map for efficient lookup
                Map<LocalDate, WorkTimeTable> entriesMap = entries.stream()
                        .collect(Collectors.toMap(
                                WorkTimeTable::getWorkDate,
                                entry -> entry,
                                (e1, e2) -> e2 // Keep the latest in case of duplicates
                        ));

                // Create or update entries for each date
                for (LocalDate date : monthDates) {
                    WorkTimeTable entry = entriesMap.getOrDefault(date, new WorkTimeTable());

                    // Update or create time off entry
                    entry.setUserId(user.getUserId());
                    entry.setWorkDate(date);
                    entry.setTimeOffType(timeOffType);
                    entry.setAdminSync(SyncStatusWorktime.USER_INPUT);

                    // Reset work-related fields for time off
                    WorktimeEntryUtil.resetWorkFields(entry);

                    // Update map
                    entriesMap.put(date, entry);
                }

                // Convert back to list and save
                List<WorkTimeTable> updatedEntries = new ArrayList<>(entriesMap.values());
                dataAccessService.writeUserWorktime(user.getUsername(), updatedEntries, year, month);

                LoggerUtil.info(this.getClass(),
                        String.format("Updated worktime entries for %s - %d/%d with %d time off days",
                                user.getUsername(), year, month, monthDates.size()));

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        String.format("Error updating worktime for %s - %s: %s",
                                user.getUsername(), yearMonth, e.getMessage()));
            }
        });
    }

    /**
     * Create a new individual time off request
     */
    private TimeOffTracker.TimeOffRequest createTimeOffRequest(
            LocalDate date,
            String timeOffType) {

        // Use helper method for standardized time
        LocalDateTime now = getStandardTimeValues().getCurrentTime();
        String requestId = UUID.randomUUID().toString();

        return TimeOffTracker.TimeOffRequest.builder()
                .requestId(requestId)
                .date(date)
                .timeOffType(timeOffType)
                .status("APPROVED") // Auto-approve for now
                .createdAt(now)
                .lastUpdated(now)
                .build();
    }
    /**
     * Calculate valid workdays between start and end date, excluding weekends and holidays
     * Updated to use the new isNotHoliday method to fix the inverted logic issue
     */
    private List<LocalDate> calculateValidWorkDays(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> validDays = new ArrayList<>();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            // Skip weekends
            if (!WorktimeEntryUtil.isDateWeekend(current)) {
                // Skip national holidays
                if (worktimeManagementService.isNotHoliday(current)) {
                    validDays.add(current);
                }
            }
            current = current.plusDays(1);
        }

        return validDays;
    }

    // ============= TIME OFF TRACKER SYNCHRONIZATION =============

    /**
     * Synchronize the tracker with worktime files for a specific year
     */
    public void syncTimeOffTracker(User user, int year) {
        // Load all time off entries from worktime files
        List<WorkTimeTable> timeOffEntries = loadAllTimeOffEntries(user.getUsername(), year);
        syncTimeOffTracker(user, year, timeOffEntries);
    }

    /**
     * Synchronize the tracker with provided worktime entries
     */
    public void syncTimeOffTracker(User user, int year, List<WorkTimeTable> timeOffEntries) {
        ReentrantReadWriteLock.WriteLock writeLock = getUserTrackerLock(user.getUsername()).writeLock();
        writeLock.lock();
        try {
            // Get the current tracker for the specified year
            TimeOffTracker tracker = loadTimeOffTracker(user.getUsername(), user.getUserId(), year);

            // Process tracker entries against worktime data
            List<TimeOffTracker.TimeOffRequest> updatedRequests = processTimeOffRequests(user, tracker, timeOffEntries, year);

            // Update the tracker with the processed requests
            tracker.setRequests(updatedRequests);

            // Update CO balance if needed
            updateHolidayBalance(user, tracker);

            // Update last sync time
            tracker.setLastSyncTime(getStandardTimeValues().getCurrentTime());

            // Save the updated tracker
            saveTimeOffTracker(tracker, year);

            LoggerUtil.info(this.getClass(), String.format("Synchronized time off tracker for %s (year %d) with worktime files", user.getUsername(), year));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error synchronizing time off tracker for %s (year %d): %s", user.getUsername(), year, e.getMessage()));
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Processes time off requests against worktime data
     */
    private List<TimeOffTracker.TimeOffRequest> processTimeOffRequests(
            User user, TimeOffTracker tracker, List<WorkTimeTable> timeOffEntries, int year) {

        // Create maps and sets for efficient lookups
        Map<LocalDate, String> worktimeOffDates = createWorktimeOffDatesMap(timeOffEntries, year);
        Set<LocalDate> allWorktimeDates = createAllWorktimeDatesSet(timeOffEntries);

        // Process existing requests
        List<TimeOffTracker.TimeOffRequest> updatedRequests = new ArrayList<>();

        // Keep track of which months we have data for
        Set<Integer> loadedMonths = timeOffEntries.stream()
                .map(entry -> entry.getWorkDate().getMonthValue())
                .collect(Collectors.toSet());

        // Process each request
        for (TimeOffTracker.TimeOffRequest request : tracker.getRequests()) {
            TimeOffTracker.TimeOffRequest processedRequest = processTimeOffRequest(user, request, worktimeOffDates, allWorktimeDates, loadedMonths, year);

            if (processedRequest != null) {
                updatedRequests.add(processedRequest);

                // Remove from worktime map to track what's left for new entries
                worktimeOffDates.remove(processedRequest.getDate());
            }
        }

        // Add new requests that were found in worktime but not in tracker
        for (Map.Entry<LocalDate, String> entry : worktimeOffDates.entrySet()) {
            TimeOffTracker.TimeOffRequest newRequest = createNewTimeOffRequest(entry.getKey(), entry.getValue(), user.getUsername());
            updatedRequests.add(newRequest);
        }

        // Sort requests by date
        updatedRequests.sort(Comparator.comparing(TimeOffTracker.TimeOffRequest::getDate));

        return updatedRequests;
    }

    /**
     * Process a single time off request against worktime data
     */
    private TimeOffTracker.TimeOffRequest processTimeOffRequest(
            User user, TimeOffTracker.TimeOffRequest request, Map<LocalDate, String> worktimeOffDates,
            Set<LocalDate> allWorktimeDates, Set<Integer> loadedMonths, int year) {

        LocalDate requestDate = request.getDate();

        // Skip requests from other years
        if (requestDate.getYear() != year) {
            return request;
        }

        // Check if this date's month has been loaded
        boolean monthIsLoaded = loadedMonths.contains(requestDate.getMonthValue());

        // Check if this date exists in worktime files AT ALL
        boolean dateExistsInWorktime = allWorktimeDates.contains(requestDate);

        // Check if this date exists as time off in worktime files
        String worktimeType = worktimeOffDates.get(requestDate);

        // Create a mutable copy of the request
        TimeOffTracker.TimeOffRequest processedRequest = copyTimeOffRequest(request);

        if (!dateExistsInWorktime) {
            // Date no longer exists in worktime or is not marked as time off
            // Only mark as canceled if this month was actually loaded
            if (monthIsLoaded && "APPROVED".equals(processedRequest.getStatus())) {
                return markRequestAsCanceled(processedRequest, user.getUsername(), "Removed from worktime files during sync");
            }
        } else if (worktimeType != null) {
            // Date exists in worktime as time off
            if (processedRequest.getTimeOffType().equals(worktimeType)) {
                // Type matches - ensure it's approved
                if (!"APPROVED".equals(processedRequest.getStatus())) {
                    return markRequestAsApproved(processedRequest, user.getUsername());
                }
            } else {
                // Type changed
                if ("APPROVED".equals(processedRequest.getStatus())) {
                    return markRequestAsCanceled(
                            processedRequest,
                            user.getUsername(),
                            "Type changed in worktime files during sync");
                }
            }
        }

        return processedRequest;
    }

    /**
     * Creates a copy of a TimeOffRequest (since it's immutable)
     */
    private TimeOffTracker.TimeOffRequest copyTimeOffRequest(TimeOffTracker.TimeOffRequest original) {
        return TimeOffTracker.TimeOffRequest.builder()
                .requestId(original.getRequestId())
                .date(original.getDate())
                .timeOffType(original.getTimeOffType())
                .status(original.getStatus())
                .eligibleDays(original.getEligibleDays())
                .createdAt(original.getCreatedAt())
                .lastUpdated(original.getLastUpdated())
                .notes(original.getNotes())
                .build();
    }

    /**
     * Mark a request as canceled and log the change
     */
    private TimeOffTracker.TimeOffRequest markRequestAsCanceled(TimeOffTracker.TimeOffRequest request, String username, String reason) {
        request.setStatus("CANCELED");
        request.setNotes(reason);
        request.setLastUpdated(getStandardTimeValues().getCurrentTime());

        if ("CO".equals(request.getTimeOffType())) {
            LoggerUtil.info(this.getClass(),
                    String.format("Canceled CO day for %s on %s (%s)",
                            username, request.getDate(), reason));
        }

        return request;
    }

    /**
     * Mark a request as approved and log the change
     */
    private TimeOffTracker.TimeOffRequest markRequestAsApproved(TimeOffTracker.TimeOffRequest request, String username) {
        request.setStatus("APPROVED");
        request.setNotes("Restored from worktime files during sync");
        request.setLastUpdated(getStandardTimeValues().getCurrentTime());

        if ("CO".equals(request.getTimeOffType())) {
            LoggerUtil.info(this.getClass(),
                    String.format("Restored CO day for %s on %s (%s)",
                            username, request.getDate(), "Restored from worktime files during sync"));
        }

        return request;
    }

    /**
     * Create a new TimeOffRequest for a worktime entry that doesn't have a corresponding tracker entry
     */
    private TimeOffTracker.TimeOffRequest createNewTimeOffRequest(LocalDate date, String timeOffType, String username) {
        TimeOffTracker.TimeOffRequest newRequest = TimeOffTracker.TimeOffRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .date(date)
                .timeOffType(timeOffType)
                .status("APPROVED")
                .createdAt(getStandardTimeValues().getCurrentTime())
                .lastUpdated(getStandardTimeValues().getCurrentTime())
                .notes("Auto-created from worktime files during sync")
                .build();

        if ("CO".equals(timeOffType)) {
            LoggerUtil.info(this.getClass(),
                    String.format("Added new CO day for %s on %s (found in worktime)",
                            username, date));
        }

        return newRequest;
    }

    /**
     * Update holiday balance based on changes to CO days
     */
    private void updateHolidayBalance(User user, TimeOffTracker tracker) {
        // Count approved CO days
        int approvedCODays = (int) tracker.getRequests().stream()
                .filter(r -> "CO".equals(r.getTimeOffType()))
                .filter(r -> "APPROVED".equals(r.getStatus()))
                .count();

        // If different from current used days, update
        if (approvedCODays != tracker.getUsedHolidayDays()) {
            int oldUsedDays = tracker.getUsedHolidayDays();
            int oldAvailableDays = tracker.getAvailableHolidayDays();

            // Calculate new values
            int newAvailableDays = oldAvailableDays + (oldUsedDays - approvedCODays);

            // Update tracker
            tracker.setUsedHolidayDays(approvedCODays);
            tracker.setAvailableHolidayDays(newAvailableDays);

            // Update holiday service
            try {
                holidayManagementService.updateUserHolidayDays(user.getUserId(), newAvailableDays);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Failed to update holiday days in holiday service: %s", e.getMessage()));
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Updated CO balance for %s: Old=%d/%d, New=%d/%d",
                            user.getUsername(), oldUsedDays, oldAvailableDays, approvedCODays, newAvailableDays));
        }
    }

    // ============= TIME OFF QUERIES =============

    /**
     * Get upcoming time off requests for a user
     */
    public List<TimeOffTracker.TimeOffRequest> getUpcomingTimeOffRequests(User user) {
        try {
            // Get current year
            int currentYear = getStandardCurrentDate().getYear();

            // Load tracker for current year (don't sync here)
            TimeOffTracker tracker = loadTimeOffTracker(user.getUsername(), user.getUserId(), currentYear);
            List<TimeOffTracker.TimeOffRequest> requests = new ArrayList<>(tracker.getRequests());

            // Also check next year if available
            try {
                TimeOffTracker nextYearTracker = loadTimeOffTracker(user.getUsername(), user.getUserId(), currentYear + 1);
                requests.addAll(nextYearTracker.getRequests());
            } catch (Exception e) {
                // Ignore if next year tracker doesn't exist
                LoggerUtil.debug(this.getClass(), "Next year tracker not available, continuing with current year only");
            }

            // Get standardized date
            LocalDate today = getStandardCurrentDate();

            // Filter for upcoming and active requests
            return requests.stream()
                    .filter(request -> "APPROVED".equals(request.getStatus()))
                    .filter(request -> request.getDate().isAfter(today) || request.getDate().isEqual(today))
                    .sorted(Comparator.comparing(TimeOffTracker.TimeOffRequest::getDate))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error getting upcoming time off for %s: %s",
                            user.getUsername(), e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Get all time off entries for a user
     */
    public List<WorkTimeTable> getUpcomingTimeOff(User user) {
        try {

            // First get approved requests from tracker for current year
            List<TimeOffTracker.TimeOffRequest> approvedRequests = getUpcomingTimeOffRequests(user);

            // Convert approved requests to WorkTimeTable format
            // Sort by date
            return approvedRequests.stream()
                    .map(request -> {
                        WorkTimeTable entry = new WorkTimeTable();
                        entry.setUserId(user.getUserId());
                        entry.setWorkDate(request.getDate());
                        entry.setTimeOffType(request.getTimeOffType());
                        return entry;
                    }).sorted(Comparator.comparing(WorkTimeTable::getWorkDate)).collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting upcoming time off for %s: %s", user.getUsername(), e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Calculate time off summary from worktime entries
     */
    public TimeOffSummaryDTO calculateTimeOffSummaryReadOnly(String username, int year) {
        try {
            // Get all time off entries for the year
            List<WorkTimeTable> timeOffEntries = dataAccessService.readTimeOffReadOnly(username, year);

            // Count by type
            int snDays = 0, coDays = 0, cmDays = 0;

            for (WorkTimeTable entry : timeOffEntries) {
                if (entry.getTimeOffType() == null) {
                    continue;
                }

                switch (entry.getTimeOffType()) {
                    case WorkCode.NATIONAL_HOLIDAY_CODE:
                        snDays++;
                        break;
                    case WorkCode.TIME_OFF_CODE:
                        coDays++;
                        break;
                    case WorkCode.MEDICAL_LEAVE_CODE:
                        cmDays++;
                        break;
                }
            }

            // Get available days from tracker if possible
            int availablePaidDays = 0;
            int paidDaysTaken = coDays;

            try {
                // Try to get from tracker for most accurate data
                Integer userId = getUserId(username);
                TimeOffTracker tracker = dataAccessService.readTimeOffTrackerReadOnly(username, userId, year);

                if (tracker != null) {
                    availablePaidDays = tracker.getAvailableHolidayDays();
                    paidDaysTaken = tracker.getUsedHolidayDays();
                } else {
                    // Fall back to holiday service
                    availablePaidDays = holidayManagementService.getRemainingHolidayDays(username, getUserId(username));
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Error getting holiday days for %s: %s", username, e.getMessage()));
            }

            // Build and return the summary
            return TimeOffSummaryDTO.builder()
                    .snDays(snDays)
                    .coDays(coDays)
                    .cmDays(cmDays)
                    .availablePaidDays(availablePaidDays + paidDaysTaken)
                    .paidDaysTaken(paidDaysTaken)
                    .remainingPaidDays(availablePaidDays)
                    .build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error calculating time off summary for %s: %s",
                            username, e.getMessage()));

            // Return empty summary on error
            return TimeOffSummaryDTO.builder()
                    .snDays(0)
                    .coDays(0)
                    .cmDays(0)
                    .availablePaidDays(0)
                    .paidDaysTaken(0)
                    .remainingPaidDays(0)
                    .build();
        }
    }

    // ============= HELPER METHODS =============

    /**
     * Helper method to load all time off entries for a year
     */
    private List<WorkTimeTable> loadAllTimeOffEntries(String username, int year) {
        List<WorkTimeTable> allTimeOffEntries = new ArrayList<>();

        // Process each month in the year
        for (int month = 1; month <= 12; month++) {
            try {
                // Load worktime entries for the month - use read-only approach
                List<WorkTimeTable> entries = dataAccessService.readWorktimeReadOnly(username, year, month);

                if (entries != null && !entries.isEmpty()) {
                    // Filter for time off entries only
                    List<WorkTimeTable> timeOffEntries = entries.stream()
                            .filter(entry -> entry.getTimeOffType() != null)
                            .toList();

                    allTimeOffEntries.addAll(timeOffEntries);
                }
            } catch (Exception e) {
                // Just log warnings - don't fail the whole operation
                LoggerUtil.warn(this.getClass(), String.format("Error loading worktime for %s - %d/%d: %s", username, year, month, e.getMessage()));
            }
        }

        return allTimeOffEntries;
    }

    /**
     * Creates a map of dates to time off types from worktime entries
     */
    private Map<LocalDate, String> createWorktimeOffDatesMap(List<WorkTimeTable> entries, int year) {
        return entries.stream()
                .filter(entry -> entry.getTimeOffType() != null)
                .filter(entry -> entry.getWorkDate().getYear() == year)
                .collect(Collectors.toMap(
                        WorkTimeTable::getWorkDate,
                        WorkTimeTable::getTimeOffType,
                        (existing, replacement) -> replacement // Keep latest if duplicate
                ));
    }

    /**
     * Creates a set of all dates from worktime entries
     */
    private Set<LocalDate> createAllWorktimeDatesSet(List<WorkTimeTable> entries) {
        return entries.stream()
                .map(WorkTimeTable::getWorkDate)
                .collect(Collectors.toSet());
    }

    /**
     * Helper method to get standard time values
     */
    private GetStandardTimeValuesCommand.StandardTimeValues getStandardTimeValues() {
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
        return timeValidationService.execute(timeCommand);
    }

    /**
     * Get the standard current date from the time validation service
     */
    private LocalDate getStandardCurrentDate() {
        return getStandardTimeValues().getCurrentDate();
    }

    /**
     * Get user ID from username
     */
    private Integer getUserId(String username) {
        try {
            return userService.getUserByUsername(username)
                    .map(User::getUserId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        } catch (Exception e) {
            throw new IllegalArgumentException("Error getting user ID: " + e.getMessage());
        }
    }
}