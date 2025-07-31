
package com.ctgraphdep.worktime.commands.status;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.TimeOffRequest;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.accessor.NetworkOnlyAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REFACTORED: Command to load time off data for status display using accessor pattern.
 * Always uses NetworkOnlyAccessor for consistent cross-user viewing.
 * Used by StatusController for time off status viewing.
 */
public class LoadUserTimeOffStatusCommand extends WorktimeOperationCommand<LoadUserTimeOffStatusCommand.TimeOffStatusData> {
    private final String targetUsername;
    private final Integer targetUserId;
    private final int year;

    public LoadUserTimeOffStatusCommand(WorktimeOperationContext context, String targetUsername,
                                        Integer targetUserId, int year) {
        super(context);
        this.targetUsername = targetUsername;
        this.targetUserId = targetUserId;
        this.year = year;
    }

    @Override
    protected void validate() {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Target username cannot be null or empty");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("Target user ID cannot be null");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating time off status load: target=%s, year=%d", targetUsername, year));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Loading time off status for %s - %d using NetworkOnlyAccessor", targetUsername, year));

        try {
            // FIXED: Always use NetworkOnlyAccessor for status viewing
            WorktimeDataAccessor accessor = new NetworkOnlyAccessor(
                    context.getWorktimeDataService(),
                    context.getRegisterDataService(),
                    context.getCheckRegisterDataService(),
                    context.getTimeOffDataService()
            );

            // Load time off tracker using accessor
            TimeOffTracker tracker = accessor.readTimeOffTracker(targetUsername, targetUserId, year);

            // Handle null tracker
            if (tracker == null) {
                tracker = createEmptyTracker(targetUsername, targetUserId, year);
            }

            // Convert tracker to status data
            TimeOffStatusData statusData = convertTrackerToStatusData(tracker);

            String message = String.format("Loaded time off data for %s - %d: %d requests",
                    targetUsername, year,
                    tracker.getRequests() != null ? tracker.getRequests().size() : 0);

            LoggerUtil.info(this.getClass(), message);

            return OperationResult.successWithSideEffects(message, getOperationType(), statusData, null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading time off status for %s - %d: %s", targetUsername, year, e.getMessage()), e);

            // Return empty data on error
            TimeOffTracker emptyTracker = createEmptyTracker(targetUsername, targetUserId, year);
            TimeOffStatusData emptyData = convertTrackerToStatusData(emptyTracker);

            return OperationResult.successWithSideEffects(
                    String.format("No time off data available for %s - %d", targetUsername, year),
                    getOperationType(), emptyData, null);
        }
    }

    /**
     * Create empty tracker for user
     */
    private TimeOffTracker createEmptyTracker(String username, Integer userId, int year) {
        TimeOffTracker tracker = new TimeOffTracker();
        tracker.setUsername(username);
        tracker.setUserId(userId);
        tracker.setYear(year);
        tracker.setRequests(new ArrayList<>());
        return tracker;
    }

    /**
     * Convert TimeOffTracker to status display data
     */
    private TimeOffStatusData convertTrackerToStatusData(TimeOffTracker tracker) {
        // Calculate summary
        TimeOffSummaryDTO summary = calculateSummaryFromTracker(tracker);

        // Get approved time off entries as WorkTimeTable for display compatibility
        List<WorkTimeTable> approvedEntries = getApprovedEntriesFromTracker(tracker, targetUserId);

        // Get upcoming time off
        List<WorkTimeTable> upcomingEntries = getUpcomingEntriesFromTracker(tracker, targetUserId);

        return new TimeOffStatusData(tracker, summary, approvedEntries, upcomingEntries);
    }

    /**
     * Calculate time off summary from tracker
     */
    private TimeOffSummaryDTO calculateSummaryFromTracker(TimeOffTracker tracker) {
        int coDays = 0;
        int cmDays = 0;
        int snDays = 0;

        if (tracker.getRequests() != null) {
            for (TimeOffRequest request : tracker.getRequests()) {
                if ("APPROVED".equals(request.getStatus())) {
                    switch (request.getTimeOffType()) {
                        case "CO" -> coDays++;
                        case "CM" -> cmDays++;
                        case "SN" -> snDays++;
                    }
                }
            }
        }

        // FIXED: Get holiday balance from user cache, NOT from tracker
        int availablePaidDays = getHolidayBalanceFromUserCache(targetUsername);
        int paidDaysTaken = coDays; // CO days are paid vacation days
        int remainingPaidDays = Math.max(0, availablePaidDays - paidDaysTaken);

        LoggerUtil.debug(this.getClass(), String.format(
                "Summary calculation for %s: availablePaidDays=%d (from user cache), paidDaysTaken=%d, remainingPaidDays=%d",
                targetUsername, availablePaidDays, paidDaysTaken, remainingPaidDays));

        return TimeOffSummaryDTO.builder()
                .coDays(coDays)
                .cmDays(cmDays)
                .snDays(snDays)
                .paidDaysTaken(paidDaysTaken)
                .remainingPaidDays(remainingPaidDays)
                .availablePaidDays(availablePaidDays)
                .build();
    }

    /**
     * FIXED: Get holiday balance from user cache (same logic as TimeOffCacheService)
     */
    private int getHolidayBalanceFromUserCache(String username) {
        try {
            // Get current user context
            String currentUsername = context.getCurrentUsername();

            if (username.equals(currentUsername)) {
                // For current user - use MainDefaultUserContextCache (authoritative)
                Integer balance = context.getCurrentHolidayBalance();
                LoggerUtil.debug(this.getClass(), String.format(
                        "Got holiday balance from MainDefaultUserContextCache for %s: %d",
                        username, balance != null ? balance : 0));
                return balance != null ? balance : 0;
            } else {
                // For other users - get from user cache via context
                Optional<User> userOpt = context.getUser(username);
                if (userOpt.isPresent()) {
                    Integer balance = userOpt.get().getPaidHolidayDays();
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Got holiday balance from user cache for %s: %d",
                            username, balance != null ? balance : 0));
                    return balance != null ? balance : 0;
                } else {
                    LoggerUtil.warn(this.getClass(), String.format(
                            "User not found in cache: %s", username));
                    return 0;
                }
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error getting holiday balance for %s: %s", username, e.getMessage()));
            return 0;
        }
    }

    /**
     * Get approved time off entries as WorkTimeTable for display
     */
    private List<WorkTimeTable> getApprovedEntriesFromTracker(TimeOffTracker tracker, Integer userId) {
        if (tracker.getRequests() == null) {
            return new ArrayList<>();
        }

        return tracker.getRequests().stream()
                .filter(request -> "APPROVED".equals(request.getStatus()))
                .map(request -> convertRequestToWorkTimeTable(request, userId))
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                .collect(Collectors.toList());
    }

    /**
     * Get upcoming time off entries
     */
    private List<WorkTimeTable> getUpcomingEntriesFromTracker(TimeOffTracker tracker, Integer userId) {
        if (tracker.getRequests() == null) {
            return new ArrayList<>();
        }

        LocalDate today = LocalDate.now();
        return tracker.getRequests().stream()
                .filter(request -> "APPROVED".equals(request.getStatus()))
                .filter(request -> request.getDate().isAfter(today))
                .map(request -> convertRequestToWorkTimeTable(request, userId))
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                .collect(Collectors.toList());
    }

    /**
     * Convert TimeOffRequest to WorkTimeTable for display compatibility
     */
    private WorkTimeTable convertRequestToWorkTimeTable(TimeOffRequest request, Integer userId) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(request.getDate());
        entry.setTimeOffType(request.getTimeOffType());
        entry.setAdminSync(MergingStatusConstants.USER_INPUT);

        // Clear work-related fields for time off
        entry.setDayStartTime(null);
        entry.setDayEndTime(null);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setTemporaryStopCount(0);
        entry.setLunchBreakDeducted(false);

        return entry;
    }

    @Override
    protected String getCommandName() {
        return String.format("LoadUserTimeOffStatus[%s, %d]", targetUsername, year);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.LOAD_USER_WORKTIME; // Reuse existing type
    }

    /**
     * Data container for time off status information
     */
    @Getter
    public static class TimeOffStatusData {
        private final TimeOffTracker tracker;
        private final TimeOffSummaryDTO summary;
        private final List<WorkTimeTable> approvedEntries;
        private final List<WorkTimeTable> upcomingEntries;

        public TimeOffStatusData(TimeOffTracker tracker, TimeOffSummaryDTO summary,
                                 List<WorkTimeTable> approvedEntries, List<WorkTimeTable> upcomingEntries) {
            this.tracker = tracker;
            this.summary = summary;
            this.approvedEntries = approvedEntries;
            this.upcomingEntries = upcomingEntries;
        }
    }
}