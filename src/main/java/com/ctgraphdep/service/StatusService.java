package com.ctgraphdep.service;

import com.ctgraphdep.model.WorkTimeSummary;
import com.ctgraphdep.model.dto.*;
import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized service for status-related operations.
 * Focuses on read-only operations for displaying user status, worktime, and register data.
 */
@Service
public class StatusService {
    private final DataAccessService dataAccessService;
    private final UserStatusDbService userStatusDbService;
    private final UserRegisterService userRegisterService;

    public StatusService(DataAccessService dataAccessService,
                         UserStatusDbService userStatusDbService,
                         UserRegisterService userRegisterService) {
        this.dataAccessService = dataAccessService;
        this.userStatusDbService = userStatusDbService;
        this.userRegisterService = userRegisterService;

        LoggerUtil.initialize(this.getClass(), null);
    }

    // Gets user statuses for display on the status page.
    public List<UserStatusDTO> getUserStatuses() {
        return userStatusDbService.getAllUserStatuses(); // Updated to use new service
    }

    // Gets user status count based on status type.
    public int getUserStatusCount(String statusType) {
        if ("online".equalsIgnoreCase(statusType)) {
            return userStatusDbService.getOnlineUserCount(); // Updated to use new service
        } else if ("active".equalsIgnoreCase(statusType)) {
            return userStatusDbService.getActiveUserCount(); // Updated to use new service
        }
        return 0;
    }

    // Load worktime data for a user in view-only mode (optimized read-only operation)
    public List<WorkTimeTable> loadViewOnlyWorktime(String username, Integer userId, int year, int month) {
        validatePeriod(year, month);

        try {
            // Use read-only method from DataAccessService
            List<WorkTimeTable> userEntries = dataAccessService.readWorktimeReadOnly(username, year, month);

            if (userEntries.isEmpty()) {
                return new ArrayList<>();
            }

            // Filter for requested user and sort by date
            return userEntries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading view-only worktime for user %s: %s",
                            username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Prepares worktime display data without modifying any files, now using DTOs
    public Map<String, Object> prepareWorktimeDisplayData(User user, List<WorkTimeTable> worktimeData, int year, int month) {
        validateInput(user, worktimeData, year, month);

        try {
            Map<String, Object> displayData = new HashMap<>();

            // Filter entries for display
            List<WorkTimeTable> displayableEntries = filterWorktimeEntriesForDisplay(worktimeData);

            // Calculate summary using domain model
            WorkTimeSummary summary = calculateMonthSummary(
                    displayableEntries,
                    year,
                    month
            );

            // Convert to DTOs
            int userSchedule = user.getSchedule() != null ? user.getSchedule() : 8; // Default to 8 hours
            List<WorkTimeEntryDTO> entryDTOs = displayableEntries.stream()
                    .map(entry -> WorkTimeEntryDTO.fromWorkTimeTable(entry, userSchedule))
                    .collect(Collectors.toList());

            // Convert summary to DTO
            WorkTimeSummaryDTO summaryDTO = WorkTimeSummaryDTO.fromWorkTimeSummary(summary);

            // Prepare display data with DTOs
            displayData.put("worktimeData", entryDTOs);
            displayData.put("currentYear", year);
            displayData.put("currentMonth", month);
            displayData.put("user", sanitizeUserData(user));
            displayData.put("summary", summaryDTO);

            return displayData;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error preparing worktime display data for user %s: %s",
                            user.getUsername(), e.getMessage()));
            throw new RuntimeException("Failed to prepare worktime display data", e);
        }
    }


    // Loads register entries for search functionality.
    public List<RegisterEntry> loadRegisterEntriesForSearch(User user, String searchTerm, LocalDate startDate,
                                                            LocalDate endDate, String actionType, String printPrepTypes,
                                                            String clientName, Integer requestedYear, Integer requestedMonth,
                                                            int displayYear, int displayMonth) {
        List<RegisterEntry> entriesToSearch;

        // Case 1: Date range specified that spans multiple months
        if (startDate != null && endDate != null && !YearMonth.from(startDate).equals(YearMonth.from(endDate))) {
            entriesToSearch = loadRegisterEntriesForDateRange(user, startDate, endDate);
        }
        // Case 2: Specific year/month requested different from display defaults
        else if (requestedYear != null || requestedMonth != null) {
            int searchYear = requestedYear != null ? requestedYear : displayYear;
            int searchMonth = requestedMonth != null ? requestedMonth : displayMonth;
            entriesToSearch = loadRegisterEntriesForPeriod(user, searchYear, searchMonth);
        }
        // Case 3: Use the current display period
        else {
            entriesToSearch = loadRegisterEntriesForPeriod(user, displayYear, displayMonth);
        }

        // Apply all filters
        return filterRegisterEntries(
                entriesToSearch,
                searchTerm,
                startDate,
                endDate,
                actionType,
                printPrepTypes,
                clientName);
    }


    // Load register entries for a period using an optimized read-only approach
    public List<RegisterEntry> loadRegisterEntriesForPeriod(User user, int year, int month) {
        try {
            // Use read-only method from DataAccessService
            return dataAccessService.readRegisterReadOnly(
                    user.getUsername(), user.getUserId(), year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading register entries for user %s: %s",
                            user.getUsername(), e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Loads all relevant entries for filtering and exporting.
    public List<RegisterEntry> loadAllRelevantEntries(User user, Integer year, Integer month,
                                                      LocalDate startDate, LocalDate endDate) {
        List<RegisterEntry> allEntries = new ArrayList<>();

        // If specific year and month provided
        if (year != null && month != null) {
            List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(
                    user.getUsername(), user.getUserId(), year, month);
            if (monthEntries != null) {
                allEntries.addAll(monthEntries);
            }
            return allEntries;
        }

        // If date range provided, load all months in range
        if (startDate != null && endDate != null) {
            YearMonth start = YearMonth.from(startDate);
            YearMonth end = YearMonth.from(endDate);

            YearMonth current = start;
            while (!current.isAfter(end)) {
                try {
                    List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(
                            user.getUsername(), user.getUserId(), current.getYear(), current.getMonthValue());
                    if (monthEntries != null) {
                        allEntries.addAll(monthEntries);
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Error loading entries for %s - %d/%d: %s",
                                    user.getUsername(), current.getYear(), current.getMonthValue(), e.getMessage()));
                }
                current = current.plusMonths(1);
            }
            return allEntries;
        }

        // If only start date provided
        if (startDate != null) {
            YearMonth start = YearMonth.from(startDate);
            YearMonth now = YearMonth.now();

            YearMonth current = start;
            while (!current.isAfter(now)) {
                try {
                    List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(
                            user.getUsername(), user.getUserId(), current.getYear(), current.getMonthValue());
                    if (monthEntries != null) {
                        allEntries.addAll(monthEntries);
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Error loading entries for %s - %d/%d: %s",
                                    user.getUsername(), current.getYear(), current.getMonthValue(), e.getMessage()));
                }
                current = current.plusMonths(1);
            }
            return allEntries;
        }

        // If only year provided, load all months for that year
        if (year != null) {
            for (int m = 1; m <= 12; m++) {
                try {
                    List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(
                            user.getUsername(), user.getUserId(), year, m);
                    if (monthEntries != null) {
                        allEntries.addAll(monthEntries);
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Error loading entries for %s - %d/%d: %s",
                                    user.getUsername(), year, m, e.getMessage()));
                }
            }
            return allEntries;
        }

        // Default: load current month and previous month
        LocalDate now = LocalDate.now();
        try {
            List<RegisterEntry> currentMonthEntries = userRegisterService.loadMonthEntries(
                    user.getUsername(), user.getUserId(), now.getYear(), now.getMonthValue());
            if (currentMonthEntries != null) {
                allEntries.addAll(currentMonthEntries);
            }

            // Previous month
            LocalDate prevMonth = now.minusMonths(1);
            List<RegisterEntry> prevMonthEntries = userRegisterService.loadMonthEntries(
                    user.getUsername(), user.getUserId(), prevMonth.getYear(), prevMonth.getMonthValue());
            if (prevMonthEntries != null) {
                allEntries.addAll(prevMonthEntries);
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    String.format("Error loading recent entries for %s: %s",
                            user.getUsername(), e.getMessage()));
        }

        return allEntries;
    }


    // Filter register entries based on search criteria.
    public List<RegisterEntry> filterRegisterEntries(List<RegisterEntry> entries,
                                                     String searchTerm,
                                                     LocalDate startDate,
                                                     LocalDate endDate,
                                                     String actionType,
                                                     String printPrepTypes,
                                                     String clientName) {
        List<RegisterEntry> filteredEntries = new ArrayList<>(entries);

        // Filter by search term (search across multiple fields)
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String term = searchTerm.toLowerCase();
            filteredEntries = filteredEntries.stream()
                    .filter(entry ->
                            (entry.getOrderId() != null && entry.getOrderId().toLowerCase().contains(term)) ||
                                    (entry.getProductionId() != null && entry.getProductionId().toLowerCase().contains(term)) ||
                                    (entry.getOmsId() != null && entry.getOmsId().toLowerCase().contains(term)) ||
                                    (entry.getClientName() != null && entry.getClientName().toLowerCase().contains(term)) ||
                                    (entry.getActionType() != null && entry.getActionType().toLowerCase().contains(term)) ||
                                    (entry.getObservations() != null && entry.getObservations().toLowerCase().contains(term))
                    )
                    .collect(Collectors.toList());
        }

        // Filter by date range
        if (startDate != null) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> !entry.getDate().isBefore(startDate))
                    .collect(Collectors.toList());
        }

        if (endDate != null) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> !entry.getDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Filter by action type
        if (actionType != null && !actionType.isEmpty()) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> actionType.equals(entry.getActionType()))
                    .collect(Collectors.toList());
        }

        // Filter by print prep type
        if (printPrepTypes != null && !printPrepTypes.isEmpty()) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> entry.getPrintPrepTypes() != null &&
                            entry.getPrintPrepTypes().contains(printPrepTypes))
                    .collect(Collectors.toList());
        }

        // Filter by client name
        if (clientName != null && !clientName.isEmpty()) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> clientName.equals(entry.getClientName()))
                    .collect(Collectors.toList());
        }

        return filteredEntries;
    }

    // Extracts unique clients from register entries.
    public Set<String> extractUniqueClients(List<RegisterEntry> entries) {
        return entries.stream()
                .map(RegisterEntry::getClientName)
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private List<RegisterEntry> loadRegisterEntriesForDateRange(User user, LocalDate startDate, LocalDate endDate) {
        List<RegisterEntry> allEntries = new ArrayList<>();

        YearMonth start = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);

        YearMonth current = start;
        while (!current.isAfter(end)) {
            try {
                List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(
                        user.getUsername(), user.getUserId(), current.getYear(), current.getMonthValue());

                if (monthEntries != null) {
                    allEntries.addAll(monthEntries);
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Error loading entries for %s - %d/%d: %s",
                                user.getUsername(), current.getYear(), current.getMonthValue(), e.getMessage()));
            }

            current = current.plusMonths(1);
        }

        return allEntries;
    }

    private List<WorkTimeTable> filterWorktimeEntriesForDisplay(List<WorkTimeTable> entries) {
        return entries.stream()
                .filter(this::isWorktimeEntryDisplayable)
                .map(this::prepareWorktimeEntryForDisplay)
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                .collect(Collectors.toList());
    }

    private boolean isWorktimeEntryDisplayable(WorkTimeTable entry) {
        if (entry == null) return false;

        // Never display ADMIN_BLANK entries
        if (SyncStatusWorktime.ADMIN_BLANK.equals(entry.getAdminSync())) {
            return false;
        }

        // Display USER_IN_PROCESS entries with partial info
        if (SyncStatusWorktime.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            return true;
        }

        // Show all other valid entries
        return entry.getAdminSync() != null;
    }

    private WorkTimeTable prepareWorktimeEntryForDisplay(WorkTimeTable entry) {
        WorkTimeTable displayEntry = new WorkTimeTable();
        displayEntry.setUserId(entry.getUserId());
        displayEntry.setWorkDate(entry.getWorkDate());
        displayEntry.setDayStartTime(entry.getDayStartTime());
        displayEntry.setDayEndTime(entry.getDayEndTime());
        displayEntry.setTemporaryStopCount(entry.getTemporaryStopCount());
        displayEntry.setTotalTemporaryStopMinutes(entry.getTotalTemporaryStopMinutes());
        displayEntry.setLunchBreakDeducted(entry.isLunchBreakDeducted());
        displayEntry.setTimeOffType(entry.getTimeOffType());
        displayEntry.setAdminSync(entry.getAdminSync());

        // For USER_IN_PROCESS entries, show only partial information
        if (SyncStatusWorktime.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            // Keep information that is already available
            if (displayEntry.getTotalWorkedMinutes() == null || displayEntry.getTotalWorkedMinutes() == 0) {
                displayEntry.setTotalWorkedMinutes(null);
            }

            if (displayEntry.getTotalOvertimeMinutes() == null || displayEntry.getTotalOvertimeMinutes() == 0) {
                displayEntry.setTotalOvertimeMinutes(null);
            }

            // Always hide end time for in-process entries
            displayEntry.setDayEndTime(null);

            // Don't apply lunch break for in-process entries unless explicitly set
            if (!displayEntry.isLunchBreakDeducted()) {
                displayEntry.setLunchBreakDeducted(false);
            }
        } else {
            displayEntry.setTotalWorkedMinutes(entry.getTotalWorkedMinutes());
            displayEntry.setTotalOvertimeMinutes(entry.getTotalOvertimeMinutes());
        }

        return displayEntry;
    }

    private WorkTimeSummary calculateMonthSummary(
            List<WorkTimeTable> worktimeData,
            int year,
            int month) {

        try {
            int totalWorkDays = calculateWorkDays(year, month);
            WorkTimeCounts counts = calculateWorkTimeCounts(worktimeData);

            return WorkTimeSummary.builder()
                    .totalWorkDays(totalWorkDays)
                    .daysWorked(counts.getDaysWorked())
                    .remainingWorkDays(totalWorkDays - (counts.getDaysWorked() + counts.getSnDays() + counts.getCoDays() + counts.getCmDays()))
                    .snDays(counts.getSnDays())
                    .coDays(counts.getCoDays())
                    .cmDays(counts.getCmDays())
                    .totalRegularMinutes(counts.getRegularMinutes())
                    .totalOvertimeMinutes(counts.getOvertimeMinutes())
                    .totalMinutes(counts.getRegularMinutes() + counts.getOvertimeMinutes())
                    .discardedMinutes(counts.getDiscardedMinutes()) // Now including discarded minutes
                    .availablePaidDays(21) // Default value to avoid file reads
                    .build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating month summary", e);
            throw e;
        }
    }

    private WorkTimeCounts calculateWorkTimeCounts(List<WorkTimeTable> worktimeData) {
        WorkTimeCounts counts = new WorkTimeCounts();
        int totalRegularMinutes = 0;
        int totalOvertimeMinutes = 0;
        int totalDiscardedMinutes = 0;

        for (WorkTimeTable entry : worktimeData) {
            // Skip in-process entries
            if (SyncStatusWorktime.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                continue;
            }

            if (entry.getTimeOffType() != null) {
                switch (entry.getTimeOffType()) {
                    case "SN" -> counts.incrementSnDays();
                    case "CO" -> counts.incrementCoDays();
                    case "CM" -> counts.incrementCmDays();
                }
            } else if (entry.getTotalWorkedMinutes() != null && entry.getTotalWorkedMinutes() > 0) {
                counts.incrementDaysWorked();

                // Get user schedule (default to 8 hours)
                int userSchedule = 8;

                // Use CalculateWorkHoursUtil for consistent calculation
                WorkTimeCalculationResultDTO result = CalculateWorkHoursUtil.calculateWorkTime(
                        entry.getTotalWorkedMinutes(),
                        userSchedule
                );

                // Use the calculation results directly
                totalRegularMinutes += result.getProcessedMinutes();
                totalOvertimeMinutes += result.getOvertimeMinutes();

                // Calculate discarded minutes - partial hour not counted in processed minutes
                // Get adjusted minutes (after lunch deduction)
                int adjustedMinutes = CalculateWorkHoursUtil.calculateAdjustedMinutes(
                        entry.getTotalWorkedMinutes(), userSchedule);

                // Discarded minutes are the remainder after dividing by 60 (partial hour)
                int discardedMinutes = adjustedMinutes % 60;
                totalDiscardedMinutes += discardedMinutes;
            }
        }

        counts.setRegularMinutes(totalRegularMinutes);
        counts.setOvertimeMinutes(totalOvertimeMinutes);
        counts.setDiscardedMinutes(totalDiscardedMinutes);

        return counts;
    }

    /**
     * Gets TimeOffTracker data for a user for a specific year in read-only mode.
     * This method reads directly from the network or local storage without any data manipulation.
     *
     * @param username The username
     * @param userId   The user ID
     * @param year     The year to retrieve data for
     * @return TimeOffTracker data or null if not found
     */
    public TimeOffTracker getTimeOffTrackerReadOnly(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Retrieving time off tracker for user %s for year %d in read-only mode",
                            username, year));

            // Directly use DataAccessService to read the tracker
            TimeOffTracker tracker = dataAccessService.readTimeOffTrackerReadOnly(username, userId, year);

            if (tracker != null) {
                LoggerUtil.info(this.getClass(),
                        String.format("Successfully retrieved time off tracker for %s with %d requests",
                                username, tracker.getRequests().size()));
            } else {
                LoggerUtil.info(this.getClass(),
                        String.format("No time off tracker found for user %s for year %d",
                                username, year));
            }

            return tracker;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error retrieving time off tracker for %s (%d): %s",
                            username, year, e.getMessage()));
            return null;
        }
    }

    /**
     * Gets time off data from TimeOffTracker file in a format compatible with the existing view.
     * Only returns APPROVED records.
     *
     * @param username The username
     * @param userId   The user ID
     * @param year     The year to retrieve data for
     * @return List of WorkTimeTable entries created from tracker data
     */
    public List<WorkTimeTable> getApprovedTimeOffFromTracker(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Getting approved time off from tracker for user %s for year %d",
                            username, year));

            // Read tracker directly in read-only mode
            TimeOffTracker tracker = dataAccessService.readTimeOffTrackerReadOnly(username, userId, year);

            if (tracker == null || tracker.getRequests() == null || tracker.getRequests().isEmpty()) {
                LoggerUtil.info(this.getClass(),
                        String.format("No time off tracker found for user %s for year %d",
                                username, year));
                return new ArrayList<>();
            }

            // Convert approved entries to WorkTimeTable format for compatibility with view
            return tracker.getRequests().stream()
                    .filter(request -> "APPROVED".equals(request.getStatus()))
                    .map(request -> {
                        WorkTimeTable entry = new WorkTimeTable();
                        entry.setUserId(userId);
                        entry.setWorkDate(request.getDate());
                        entry.setTimeOffType(request.getTimeOffType());
                        // Additional fields could be set here if needed
                        return entry;
                    })
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error getting approved time off from tracker for %s (%d): %s",
                            username, year, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Gets time off summary directly from TimeOffTracker file.
     * This only counts APPROVED requests.
     *
     * @param username The username
     * @param userId   The user ID
     * @param year     The year to retrieve data for
     * @return TimeOffSummaryDTO calculated from tracker data
     */
    public TimeOffSummaryDTO getTimeOffSummaryFromTracker(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Calculating time off summary from tracker for user %s for year %d",
                            username, year));

            // Read tracker directly in read-only mode
            TimeOffTracker tracker = dataAccessService.readTimeOffTrackerReadOnly(username, userId, year);

            if (tracker != null) {
                LoggerUtil.info(this.getClass(),
                        String.format("Successfully retrieved time off tracker for %s with %d requests",
                                username, tracker.getRequests().size()));

                // Count by type from approved requests
                int snDays = 0, coDays = 0, cmDays = 0;

                for (TimeOffTracker.TimeOffRequest request : tracker.getRequests()) {
                    if (!"APPROVED".equals(request.getStatus())) {
                        continue;
                    }

                    switch (request.getTimeOffType()) {
                        case "SN" -> snDays++;
                        case "CO" -> coDays++;
                        case "CM" -> cmDays++;
                    }
                }

                int availablePaidDays = tracker.getAvailableHolidayDays();
                int usedPaidDays = tracker.getUsedHolidayDays();

                return TimeOffSummaryDTO.builder()
                        .snDays(snDays)
                        .coDays(coDays)
                        .cmDays(cmDays)
                        .availablePaidDays(availablePaidDays + usedPaidDays)
                        .paidDaysTaken(usedPaidDays)
                        .remainingPaidDays(availablePaidDays)
                        .build();
            } else {
                // If no tracker found, return default summary
                LoggerUtil.info(this.getClass(),
                        String.format("No time off tracker found for user %s for year %d, returning default summary",
                                username, year));

                return TimeOffSummaryDTO.builder()
                        .snDays(0)
                        .coDays(0)
                        .cmDays(0)
                        .availablePaidDays(21) // Default value
                        .paidDaysTaken(0)
                        .remainingPaidDays(21) // Default value
                        .build();
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error calculating time off summary from tracker for %s (%d): %s",
                            username, year, e.getMessage()));

            // Return default summary on error
            return TimeOffSummaryDTO.builder()
                    .snDays(0)
                    .coDays(0)
                    .cmDays(0)
                    .availablePaidDays(21) // Default value
                    .paidDaysTaken(0)
                    .remainingPaidDays(21) // Default value
                    .build();
        }
    }

    private int calculateWorkDays(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startOfMonth = yearMonth.atDay(1);
        LocalDate endOfMonth = yearMonth.atEndOfMonth();

        int workDays = 0;
        LocalDate current = startOfMonth;
        while (!current.isAfter(endOfMonth)) {
            DayOfWeek dayOfWeek = current.getDayOfWeek();
            if (!(dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
                workDays++;
            }
            current = current.plusDays(1);
        }
        return workDays;
    }

    private void validatePeriod(int year, int month) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }
    }

    private void validateInput(User user, List<WorkTimeTable> worktimeData, int year, int month) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (worktimeData == null) {
            throw new IllegalArgumentException("Worktime data cannot be null");
        }
        validatePeriod(year, month);
    }

    private User sanitizeUserData(User user) {
        User sanitized = new User();
        sanitized.setUserId(user.getUserId());
        sanitized.setName(user.getName());
        sanitized.setUsername(user.getUsername());
        sanitized.setEmployeeId(user.getEmployeeId());
        sanitized.setSchedule(user.getSchedule());
        return sanitized;
    }


    //Helper class to track work time counts.
    @Getter
    @Setter
    private static class WorkTimeCounts {
        private int daysWorked = 0;
        private int snDays = 0;
        private int coDays = 0;
        private int cmDays = 0;
        private int regularMinutes = 0;
        private int overtimeMinutes = 0;
        private int discardedMinutes = 0;

        public void incrementDaysWorked() {
            daysWorked++;
        }

        public void incrementSnDays() {
            snDays++;
        }

        public void incrementCoDays() {
            coDays++;
        }

        public void incrementCmDays() {
            cmDays++;
        }
    }
}