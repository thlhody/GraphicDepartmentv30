package com.ctgraphdep.service;

import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeProvider;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Service for managing time off tracker files that store per-day time off requests.
 * This service handles only the tracker files, not the worktime files.
 */
@Service
public class TimeOffTrackerService {
    private final DataAccessService dataAccessService;
    private final TimeProvider timeProvider;
    private final TimeValidationService timeValidationService;
    private final HolidayManagementService holidayService;

    // Lock for file operations
    private final Map<String, ReentrantReadWriteLock> trackerLocks = new HashMap<>();

    public TimeOffTrackerService(
            DataAccessService dataAccessService,
            TimeProvider timeProvider,
            TimeValidationService timeValidationService,
            HolidayManagementService holidayService) {
        this.dataAccessService = dataAccessService;
        this.timeProvider = timeProvider;
        this.timeValidationService = timeValidationService;
        this.holidayService = holidayService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void ensureTrackerExists(User user, Integer userId, int year) {
        // Try to load existing tracker
        TimeOffTracker tracker = dataAccessService.readTimeOffTracker(user.getUsername(), userId, year);

        // If tracker doesn't exist, initialize it
        if (tracker == null) {
            int availableDays = holidayService.getRemainingHolidayDays(user.getUsername(),userId);

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
        List<WorkTimeTable> timeOffEntries = loadAllTimeOffEntries(user.getUsername(), year);
        // Sync with worktime files to ensure consistency
        syncWithWorktimeFiles(user, year, timeOffEntries);

    }

    // Helper method to load all time off entries for a year
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
     * Helper method to get standard time values
     */
    private GetStandardTimeValuesCommand.StandardTimeValues getStandardTimeValues() {
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
        return timeValidationService.execute(timeCommand);
    }

    /**
     * Get the lock for a user's tracker file
     */
    private ReentrantReadWriteLock getTrackerLock(String username) {
        return trackerLocks.computeIfAbsent(username, k -> new ReentrantReadWriteLock());
    }

    /**
     * Load the time off tracker for a user for a specific year
     */
    public TimeOffTracker loadTimeOffTracker(String username, Integer userId, int year) {
        ReentrantReadWriteLock.ReadLock readLock = getTrackerLock(username).readLock();
        readLock.lock();
        try {
            // Read the tracker using DataAccessService
            TimeOffTracker tracker = dataAccessService.readTimeOffTracker(username, userId, year);

            // If no tracker exists yet, create a new one
            if (tracker == null) {
                LocalDateTime currentTime = getStandardTimeValues().getCurrentTime();

                // Initialize with holiday days from the holiday service
                int availableDays = holidayService.getRemainingHolidayDays(username, userId);

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
            int currentAvailableDays = holidayService.getRemainingHolidayDays(username, userId);

            // Check if holiday allocation has changed
            int totalDays = tracker.getAvailableHolidayDays() + tracker.getUsedHolidayDays();
            if (currentAvailableDays != totalDays - tracker.getUsedHolidayDays()) {
                // Days have been updated by admin, update the tracker
                int newAvailableDays = currentAvailableDays;

                LoggerUtil.info(this.getClass(), String.format("Holiday allocation updated for user %s. Old: %d, New: %d", username, tracker.getAvailableHolidayDays(), newAvailableDays));

                tracker.setAvailableHolidayDays(newAvailableDays);

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
                availableDays = holidayService.getRemainingHolidayDays(username, userId);
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

        ReentrantReadWriteLock.WriteLock writeLock = getTrackerLock(tracker.getUsername()).writeLock();
        writeLock.lock();
        try {
            dataAccessService.writeTimeOffTracker(tracker, year);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving time off tracker for %s (year %d): %s", tracker.getUsername(), year, e.getMessage()));
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Create a new individual time off request
     */
    public TimeOffTracker.TimeOffRequest createTimeOffRequest(
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
     * Add time off requests for specific dates
     * This adds the requests to the tracker and updates the holiday balance
     * but DOES NOT modify any worktime files
     */
    public void addTimeOffRequests(User user, List<LocalDate> validDates, String timeOffType) {
        if (validDates.isEmpty()) {
            return;
        }

        // Extract year from first date (all dates should be in same year)
        int year = validDates.get(0).getYear();

        ReentrantReadWriteLock.WriteLock writeLock = getTrackerLock(user.getUsername()).writeLock();
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
                holidayService.updateUserHolidayDays(user.getUserId(), newAvailableDays);
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
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Synchronize the tracker with worktime files for a specific year.
     * This updates the tracker based on worktime files but DOES NOT modify worktime files.
     */
    public void syncWithWorktimeFiles(User user, int year, List<WorkTimeTable> timeOffEntries) {
        ReentrantReadWriteLock.WriteLock writeLock = getTrackerLock(user.getUsername()).writeLock();
        writeLock.lock();
        try {
            // Load or reload all time off entries for the year to ensure we have complete data
            List<WorkTimeTable> allTimeOffEntries = ensureCompleteTimeOffData(user.getUsername(), year, timeOffEntries);

            // Get the current tracker for the specified year
            TimeOffTracker tracker = loadTimeOffTracker(user.getUsername(), user.getUserId(), year);

            // Process tracker entries against worktime data
            List<TimeOffTracker.TimeOffRequest> updatedRequests = processTimeOffRequests(user, tracker, allTimeOffEntries, year);

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
     * Ensures we have a complete set of time off data for the year by loading any missing months
     */
    private List<WorkTimeTable> ensureCompleteTimeOffData(String username, int year, List<WorkTimeTable> providedEntries) {
        // Extract the months we already have data for
        Set<Integer> loadedMonths = providedEntries.stream()
                .map(entry -> entry.getWorkDate().getMonthValue())
                .collect(Collectors.toSet());

        // Start with the provided entries
        List<WorkTimeTable> completeEntries = new ArrayList<>(providedEntries);

        // Check if we need to load more months
        boolean needsMoreData = loadedMonths.size() < 12;

        // If we need more data we're in the first month of loading, load all months
        if (needsMoreData) {
            LoggerUtil.info(this.getClass(), String.format("Loading all months for complete time off data for user %s (year %d)", username, year));

            // Load data for all months we don't already have
            for (int month = 1; month <= 12; month++) {
                if (!loadedMonths.contains(month)) {
                    try {
                        List<WorkTimeTable> monthEntries = dataAccessService.readWorktimeReadOnly(username, year, month);
                        if (monthEntries != null && !monthEntries.isEmpty()) {
                            completeEntries.addAll(monthEntries);
                            LoggerUtil.debug(this.getClass(), String.format("Loaded %d entries for month %d/%d", monthEntries.size(), month, year));
                        }
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format("Could not load data for month %d/%d: %s", month, year, e.getMessage()));
                    }
                }
            }
        }

        return completeEntries;
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
                holidayService.updateUserHolidayDays(user.getUserId(), newAvailableDays);
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Failed to update holiday days in holiday service: %s", e.getMessage()));
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Updated CO balance for %s: Old=%d/%d, New=%d/%d",
                            user.getUsername(), oldUsedDays, oldAvailableDays, approvedCODays, newAvailableDays));
        }
    }

    /**
     * Get upcoming time off requests for a user
     */
    public List<TimeOffTracker.TimeOffRequest> getUpcomingTimeOffRequests(User user) {
        try {
            // Get current year
            int currentYear = timeProvider.getCurrentDate().getYear();

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
            LocalDate today = timeProvider.getCurrentDate();

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
}