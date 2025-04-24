package com.ctgraphdep.service;

import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.dto.*;
import com.ctgraphdep.model.*;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized service for status-related operations.
 * Focuses on read-only operations for displaying user status, worktime, and register data.
 * Delegates to specialized services for domain-specific operations.
 */
@Service
public class StatusService {
    private final DataAccessService dataAccessService;
    private final UserRegisterService userRegisterService;
    private final WorktimeDisplayService worktimeDisplayService;
    private final TimeOffManagementService timeOffManagementService;
    private final TimeValidationService timeValidationService;

    public StatusService(DataAccessService dataAccessService, UserRegisterService userRegisterService,
                         WorktimeDisplayService worktimeDisplayService, TimeOffManagementService timeOffManagementService, TimeValidationService timeValidationService) {
        this.dataAccessService = dataAccessService;
        this.userRegisterService = userRegisterService;
        this.worktimeDisplayService = worktimeDisplayService;
        this.timeOffManagementService = timeOffManagementService;
        this.timeValidationService = timeValidationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Load worktime data for a user in view-only mode (optimized read-only operation)
    public List<WorkTimeTable> loadViewOnlyWorktime(String username, Integer userId, int year, int month) {

        try {
            // Use read-only method from DataAccessService
            List<WorkTimeTable> userEntries = dataAccessService.readWorktimeReadOnly(username, year, month);

            if (userEntries.isEmpty()) {
                return new ArrayList<>();
            }

            // Filter for requested user and sort by date
            return userEntries.stream().filter(entry -> entry.getUserId().equals(userId)).sorted(Comparator.comparing(WorkTimeTable::getWorkDate)).collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading view-only worktime for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Prepares worktime display data without modifying any files - now delegates to WorktimeDisplayService
    public Map<String, Object> prepareWorktimeDisplayData(User user, List<WorkTimeTable> worktimeData, int year, int month) {
        validateInput(user, worktimeData);

        try {
            // Delegate to WorktimeDisplayService for consistent display logic
            return worktimeDisplayService.prepareUserDisplayData(user, worktimeData, year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error preparing worktime display data for user %s: %s", user.getUsername(), e.getMessage()));
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
            return dataAccessService.readRegisterReadOnly(user.getUsername(), user.getUserId(), year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading register entries for user %s: %s", user.getUsername(), e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Loads all relevant entries for filtering and exporting.
    public List<RegisterEntry> loadAllRelevantEntries(User user, Integer year, Integer month,
                                                      LocalDate startDate, LocalDate endDate) {
        List<RegisterEntry> allEntries = new ArrayList<>();
        LocalDate currentDate = getStandardCurrentDate();

        // If specific year and month provided
        if (year != null && month != null) {
            List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(user.getUsername(), user.getUserId(), year, month);
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
                    List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(user.getUsername(), user.getUserId(), current.getYear(), current.getMonthValue());
                    if (monthEntries != null) {
                        allEntries.addAll(monthEntries);
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Error loading entries for %s - %d/%d: %s", user.getUsername(), current.getYear(), current.getMonthValue(), e.getMessage()));
                }
                current = current.plusMonths(1);
            }
            return allEntries;
        }

        // If only start date provided
        if (startDate != null) {
            YearMonth start = YearMonth.from(startDate);
            YearMonth now = YearMonth.from(currentDate);

            YearMonth current = start;
            while (!current.isAfter(now)) {
                try {
                    List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(user.getUsername(), user.getUserId(), current.getYear(), current.getMonthValue());
                    if (monthEntries != null) {
                        allEntries.addAll(monthEntries);
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Error loading entries for %s - %d/%d: %s", user.getUsername(), current.getYear(), current.getMonthValue(), e.getMessage()));
                }
                current = current.plusMonths(1);
            }
            return allEntries;
        }

        // If only year provided, load all months for that year
        if (year != null) {
            for (int m = 1; m <= 12; m++) {
                try {
                    List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(user.getUsername(), user.getUserId(), year, m);
                    if (monthEntries != null) {
                        allEntries.addAll(monthEntries);
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Error loading entries for %s - %d/%d: %s", user.getUsername(), year, m, e.getMessage()));
                }
            }
            return allEntries;
        }

        // Default: load current month and previous month

        try {
            List<RegisterEntry> currentMonthEntries = userRegisterService.loadMonthEntries(user.getUsername(), user.getUserId(), currentDate.getYear(), currentDate.getMonthValue());
            if (currentMonthEntries != null) {
                allEntries.addAll(currentMonthEntries);
            }

            // Previous month
            LocalDate prevMonth = currentDate.minusMonths(1);
            List<RegisterEntry> prevMonthEntries = userRegisterService.loadMonthEntries(user.getUsername(), user.getUserId(), prevMonth.getYear(), prevMonth.getMonthValue());
            if (prevMonthEntries != null) {
                allEntries.addAll(prevMonthEntries);
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format("Error loading recent entries for %s: %s", user.getUsername(), e.getMessage()));
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
            filteredEntries = filteredEntries.stream().filter(entry -> !entry.getDate().isBefore(startDate)).collect(Collectors.toList());
        }

        if (endDate != null) {
            filteredEntries = filteredEntries.stream().filter(entry -> !entry.getDate().isAfter(endDate)).collect(Collectors.toList());
        }

        // Filter by action type
        if (actionType != null && !actionType.isEmpty()) {
            filteredEntries = filteredEntries.stream().filter(entry -> actionType.equals(entry.getActionType())).collect(Collectors.toList());
        }

        // Filter by print prep type
        if (printPrepTypes != null && !printPrepTypes.isEmpty()) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> entry.getPrintPrepTypes() != null && entry.getPrintPrepTypes().contains(printPrepTypes)).collect(Collectors.toList());
        }

        // Filter by client name
        if (clientName != null && !clientName.isEmpty()) {
            filteredEntries = filteredEntries.stream().filter(entry -> clientName.equals(entry.getClientName())).collect(Collectors.toList());
        }

        return filteredEntries;
    }

    // Extracts unique clients from register entries.
    public Set<String> extractUniqueClients(List<RegisterEntry> entries) {
        return entries.stream().map(RegisterEntry::getClientName).filter(name -> name != null && !name.isEmpty()).collect(Collectors.toCollection(HashSet::new));
    }

    private List<RegisterEntry> loadRegisterEntriesForDateRange(User user, LocalDate startDate, LocalDate endDate) {
        List<RegisterEntry> allEntries = new ArrayList<>();

        YearMonth start = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);

        YearMonth current = start;
        while (!current.isAfter(end)) {
            try {
                List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(user.getUsername(), user.getUserId(), current.getYear(), current.getMonthValue());

                if (monthEntries != null) {
                    allEntries.addAll(monthEntries);
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Error loading entries for %s - %d/%d: %s", user.getUsername(), current.getYear(), current.getMonthValue(), e.getMessage()));
            }

            current = current.plusMonths(1);
        }

        return allEntries;
    }

    // Gets TimeOffTracker data for a user for a specific year in read-only mode
    public TimeOffTracker getTimeOffTrackerReadOnly(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Retrieving time off tracker for user %s for year %d in read-only mode", username, year));

            // Use the read-only method from TimeOffManagementService
            TimeOffTracker tracker = timeOffManagementService.loadTimeOffTrackerReadOnly(username, userId, year);

            if (tracker != null) {
                LoggerUtil.info(this.getClass(), String.format("Successfully retrieved time off tracker for %s with %d requests", username, tracker.getRequests().size()));
            } else {
                LoggerUtil.info(this.getClass(), String.format("No time off tracker found for user %s for year %d", username, year));
            }

            return tracker;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error retrieving time off tracker for %s (%d): %s", username, year, e.getMessage()));
            return null;
        }
    }

    // Gets time off data from TimeOffTracker file in a format compatible with the existing view. Only returns APPROVED records.
    public List<WorkTimeTable> getApprovedTimeOffFromTracker(String username, Integer userId, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Getting approved time off from tracker for user %s for year %d", username, year));

            // Get the tracker directly using read-only method
            TimeOffTracker tracker = timeOffManagementService.loadTimeOffTrackerReadOnly(username, userId, year);

            if (tracker == null || tracker.getRequests() == null || tracker.getRequests().isEmpty()) {
                return new ArrayList<>();
            }

            // Convert approved requests to WorkTimeTable entries
            return tracker.getRequests().stream()
                    .filter(request -> "APPROVED".equals(request.getStatus()))
                    .map(request -> {
                        WorkTimeTable entry = new WorkTimeTable();
                        entry.setUserId(userId);
                        entry.setWorkDate(request.getDate());
                        entry.setTimeOffType(request.getTimeOffType());
                        return entry;
                    })
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting approved time off from tracker for %s (%d): %s", username, year, e.getMessage()));
            return new ArrayList<>();
        }
    }
    // Gets time off summary directly from TimeOffTracker file. This only counts APPROVED requests.
    public TimeOffSummaryDTO getTimeOffSummaryFromTracker(String username, int year) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Calculating time off summary from tracker for user %s for year %d", username, year));

            // Delegate to TimeOffManagementService for calculation
            return timeOffManagementService.calculateTimeOffSummaryReadOnly(username, year);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error calculating time off summary from tracker for %s (%d): %s", username, year, e.getMessage()));

            // Return default summary on error
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

    // Loads check register entries for a user in view-only mode (optimized read-only operation)
    public List<RegisterCheckEntry> loadViewOnlyCheckRegister(String username, Integer userId, int year, int month) {

        try {
            // Use read-only method from DataAccessService (would need to be implemented)
            List<RegisterCheckEntry> entries = dataAccessService.readCheckRegisterReadOnly(username, userId, year, month);

            if (entries == null || entries.isEmpty()) {
                // Return empty list instead of null
                LoggerUtil.info(this.getClass(), String.format("No check register entries found for user %s (%d/%d)", username, month, year));
                return new ArrayList<>();
            }

            // Sort by date (newest first)
            return entries.stream().sorted(Comparator.comparing(RegisterCheckEntry::getDate).reversed()).collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading view-only check register for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Filter check register entries based on search criteria.
    public List<RegisterCheckEntry> filterCheckRegisterEntries(
            List<RegisterCheckEntry> entries,
            String searchTerm,
            LocalDate startDate,
            LocalDate endDate,
            String checkType,
            String designerName,
            String approvalStatus) {

        List<RegisterCheckEntry> filteredEntries = new ArrayList<>(entries);

        // Filter by search term (search across multiple fields)
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String term = searchTerm.toLowerCase();
            filteredEntries = filteredEntries.stream()
                    .filter(entry ->
                                    (entry.getProductionId() != null && entry.getProductionId().toLowerCase().contains(term)) ||
                                    (entry.getOmsId() != null && entry.getOmsId().toLowerCase().contains(term)) ||
                                    (entry.getDesignerName() != null && entry.getDesignerName().toLowerCase().contains(term)) ||
                                    (entry.getCheckType() != null && entry.getCheckType().toLowerCase().contains(term)) ||
                                    (entry.getErrorDescription() != null && entry.getErrorDescription().toLowerCase().contains(term))
                    )
                    .collect(Collectors.toList());
        }

        // Filter by date range
        if (startDate != null) {
            filteredEntries = filteredEntries.stream().filter(entry -> !entry.getDate().isBefore(startDate)).collect(Collectors.toList());
        }

        if (endDate != null) {
            filteredEntries = filteredEntries.stream().filter(entry -> !entry.getDate().isAfter(endDate)).collect(Collectors.toList());
        }

        // Filter by check type
        if (checkType != null && !checkType.isEmpty()) {
            filteredEntries = filteredEntries.stream().filter(entry -> checkType.equals(entry.getCheckType())).collect(Collectors.toList());
        }

        // Filter by designer name
        if (designerName != null && !designerName.isEmpty()) {
            filteredEntries = filteredEntries.stream().filter(entry -> designerName.equals(entry.getDesignerName())).collect(Collectors.toList());
        }

        // Filter by approval status
        if (approvalStatus != null && !approvalStatus.isEmpty()) {
            filteredEntries = filteredEntries.stream().filter(entry -> approvalStatus.equals(entry.getApprovalStatus())).collect(Collectors.toList());
        }

        return filteredEntries;
    }

    // Calculate check register summary statistics
    public Map<String, Object> calculateCheckRegisterSummary(List<RegisterCheckEntry> entries) {
        Map<String, Object> summary = new HashMap<>();

        if (entries == null || entries.isEmpty()) {
            // Return empty summary for no entries
            summary.put("totalEntries", 0);
            summary.put("checkTypeCounts", new HashMap<>());
            summary.put("avgArticles", 0.0);
            summary.put("avgFiles", 0.0);
            summary.put("totalOrderValue", 0.0);
            return summary;
        }

        // Count each check type
        Map<String, Long> checkTypeCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getCheckType() != null ? entry.getCheckType() : "UNKNOWN",
                        Collectors.counting()
                ));

        // Calculate totals and averages
        int totalEntries = entries.size();
        int totalArticles = entries.stream().mapToInt(e -> e.getArticleNumbers() != null ? e.getArticleNumbers() : 0).sum();
        int totalFiles = entries.stream().mapToInt(e -> e.getFilesNumbers() != null ? e.getFilesNumbers() : 0).sum();
        double totalOrderValue = entries.stream().mapToDouble(e -> e.getOrderValue() != null ? e.getOrderValue() : 0.0).sum();

        // Get average values
        double avgArticles = (double) totalArticles / totalEntries;
        double avgFiles = (double) totalFiles / totalEntries;

        // Add to summary map
        summary.put("totalEntries", totalEntries);
        summary.put("checkTypeCounts", checkTypeCounts);
        summary.put("totalArticles", totalArticles);
        summary.put("totalFiles", totalFiles);
        summary.put("avgArticles", avgArticles);
        summary.put("avgFiles", avgFiles);
        summary.put("totalOrderValue", totalOrderValue);

        return summary;
    }

    // Extract unique designer names from check register entries
    public Set<String> extractUniqueDesigners(List<RegisterCheckEntry> entries) {
        return entries.stream().map(RegisterCheckEntry::getDesignerName).filter(name -> name != null && !name.isEmpty()).collect(Collectors.toCollection(HashSet::new));
    }


    private void validateInput(User user, List<WorkTimeTable> worktimeData) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (worktimeData == null) {
            throw new IllegalArgumentException("Worktime data cannot be null");
        }
    }

    // And a convenience method for just the date:
    private LocalDate getStandardCurrentDate() {
        // Use TimeValidationService to get standardized time values
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory()
                .createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
        return timeValues.getCurrentDate();
    }
}