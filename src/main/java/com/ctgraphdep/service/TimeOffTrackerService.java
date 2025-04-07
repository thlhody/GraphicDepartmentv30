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

            // Ensure backward compatibility - older tracker might not have the holiday fields
            if (tracker.getAvailableHolidayDays() <= 0) {
                int availableDays = holidayService.getRemainingHolidayDays(username, userId);
                tracker.setAvailableHolidayDays(availableDays);

                // Calculate used days by counting approved CO requests
                int usedDays = (int) tracker.getRequests().stream()
                        .filter(r -> "CO".equals(r.getTimeOffType()))
                        .filter(r -> "APPROVED".equals(r.getStatus()))
                        .count();

                tracker.setUsedHolidayDays(usedDays);

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
            // Get the current tracker for the specified year
            TimeOffTracker tracker = loadTimeOffTracker(user.getUsername(), user.getUserId(), year);

            // Create a map of all dates in worktime files with time off
            Map<LocalDate, String> worktimeOffDates = timeOffEntries.stream()
                    .filter(entry -> entry.getTimeOffType() != null)
                    .filter(entry -> entry.getWorkDate().getYear() == year)
                    .collect(Collectors.toMap(
                            WorkTimeTable::getWorkDate,
                            WorkTimeTable::getTimeOffType,
                            (existing, replacement) -> replacement // Keep latest if duplicate
                    ));

            // Get all dates from worktime files (regardless of time off status)
            Set<LocalDate> allWorktimeDates = timeOffEntries.stream()
                    .map(WorkTimeTable::getWorkDate)
                    .collect(Collectors.toSet());

            // Process existing requests - mark as canceled if they no longer exist in worktime
            List<TimeOffTracker.TimeOffRequest> updatedRequests = new ArrayList<>();

            // Track changes to CO days for holiday balance update
            int coAddedCount = 0;
            int coRemovedCount = 0;

            for (TimeOffTracker.TimeOffRequest request : tracker.getRequests()) {
                LocalDate requestDate = request.getDate();

                // Skip requests from other years
                if (requestDate.getYear() != year) {
                    updatedRequests.add(request);
                    continue;
                }

                // Check if this date exists in worktime files AT ALL
                boolean dateExistsInWorktime = allWorktimeDates.contains(requestDate);

                // Check if this date exists as time off in worktime files
                String worktimeType = worktimeOffDates.get(requestDate);

                if (!dateExistsInWorktime) {
                    // Date no longer exists in worktime or is not marked as time off
                    // Only count as removed if it was previously approved
                    if ("APPROVED".equals(request.getStatus())) {
                        request.setStatus("CANCELED");
                        request.setNotes("Removed from worktime files during sync");
                        request.setLastUpdated(getStandardTimeValues().getCurrentTime());

                        // Track CO changes for balance adjustment
                        if ("CO".equals(request.getTimeOffType())) {
                            coRemovedCount++;
                            LoggerUtil.info(this.getClass(),
                                    String.format("Canceled CO day for %s on %s (removed from worktime)",
                                            user.getUsername(), requestDate));
                        }
                    }
                } else if (worktimeType != null) {
                    // Date exists in worktime as time off
                    if (request.getTimeOffType().equals(worktimeType)) {
                        // Type matches - ensure it's approved
                        if (!"APPROVED".equals(request.getStatus())) {
                            // Was previously canceled but now exists again
                            request.setStatus("APPROVED");
                            request.setNotes("Restored from worktime files during sync");
                            request.setLastUpdated(getStandardTimeValues().getCurrentTime());

                            // Track CO changes
                            if ("CO".equals(request.getTimeOffType())) {
                                coAddedCount++;
                                LoggerUtil.info(this.getClass(),
                                        String.format("Restored CO day for %s on %s (found in worktime)",
                                                user.getUsername(), requestDate));
                            }
                        }
                    } else {
                        // Type changed
                        if ("APPROVED".equals(request.getStatus())) {
                            request.setStatus("CANCELED");
                            request.setNotes("Type changed in worktime files during sync");
                            request.setLastUpdated(getStandardTimeValues().getCurrentTime());

                            // Track CO changes if old type was CO
                            if ("CO".equals(request.getTimeOffType())) {
                                coRemovedCount++;
                                LoggerUtil.info(this.getClass(),
                                        String.format("Canceled CO day for %s on %s (type changed to %s)",
                                                user.getUsername(), requestDate, worktimeType));
                            }
                        }
                    }
                }

                // Add to updated list
                updatedRequests.add(request);

                // Remove from worktime map to track what's left for new entries
                worktimeOffDates.remove(requestDate);
            }

            // Create new requests for worktime entries that don't have a tracker entry
            for (Map.Entry<LocalDate, String> entry : worktimeOffDates.entrySet()) {
                LocalDate date = entry.getKey();
                String timeOffType = entry.getValue();

                TimeOffTracker.TimeOffRequest newRequest = TimeOffTracker.TimeOffRequest.builder()
                        .requestId(UUID.randomUUID().toString())
                        .date(date)
                        .timeOffType(timeOffType)
                        .status("APPROVED")
                        .createdAt(getStandardTimeValues().getCurrentTime())
                        .lastUpdated(getStandardTimeValues().getCurrentTime())
                        .notes("Auto-created from worktime files during sync")
                        .build();

                updatedRequests.add(newRequest);

                // Track CO changes for new entries
                if ("CO".equals(timeOffType)) {
                    coAddedCount++;
                    LoggerUtil.info(this.getClass(),
                            String.format("Added new CO day for %s on %s (found in worktime)",
                                    user.getUsername(), date));
                }
            }

            // Sort requests by date for easier readability
            updatedRequests.sort(Comparator.comparing(TimeOffTracker.TimeOffRequest::getDate));

            // Update the tracker with the processed requests
            tracker.setRequests(updatedRequests);

            // Update CO balance if needed
            if (coAddedCount > 0 || coRemovedCount > 0) {
                // Calculate the change in CO usage
                int netChange = coAddedCount - coRemovedCount;

                // Current balance values
                int oldUsedDays = tracker.getUsedHolidayDays();
                int oldAvailableDays = tracker.getAvailableHolidayDays();

                // Calculate new balance values
                int newUsedDays = oldUsedDays + netChange;
                int newAvailableDays = oldAvailableDays - netChange;

                // Update tracker
                tracker.setUsedHolidayDays(newUsedDays);
                tracker.setAvailableHolidayDays(newAvailableDays);

                // Update holiday service to ensure the holiday.json file is updated
                try {
                    holidayService.updateUserHolidayDays(user.getUserId(), newAvailableDays);
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Failed to update holiday days in holiday service: %s", e.getMessage()));
                }

                LoggerUtil.info(this.getClass(),
                        String.format("Updated CO balance for %s: Added=%d, Removed=%d, Net=%d, New Balance=%d/%d",
                                user.getUsername(), coAddedCount, coRemovedCount, netChange,
                                newAvailableDays, newUsedDays));
            }

            // Update last sync time
            tracker.setLastSyncTime(getStandardTimeValues().getCurrentTime());

            // Save the updated tracker
            saveTimeOffTracker(tracker, year);

            LoggerUtil.info(this.getClass(),
                    String.format("Synchronized time off tracker for %s (year %d) with worktime files",
                            user.getUsername(), year));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error synchronizing time off tracker for %s (year %d): %s",
                            user.getUsername(), year, e.getMessage()));
        } finally {
            writeLock.unlock();
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