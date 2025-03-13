package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.*;
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
    private final UserStatusDbService userStatusDbService; // Updated to use UserStatusDbService
    private final UserRegisterService userRegisterService;
    private final UserTimeOffService userTimeOffService;

    public StatusService(DataAccessService dataAccessService,
                         UserStatusDbService userStatusDbService, // Updated to use UserStatusDbService
                         UserRegisterService userRegisterService,
                         UserTimeOffService userTimeOffService) {
        this.dataAccessService = dataAccessService;
        this.userStatusDbService = userStatusDbService; // Updated to use UserStatusDbService
        this.userRegisterService = userRegisterService;
        this.userTimeOffService = userTimeOffService;

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

    // Prepares worktime display data without modifying any files.
    public Map<String, Object> prepareWorktimeDisplayData(User user, List<WorkTimeTable> worktimeData, int year, int month) {
        validateInput(user, worktimeData, year, month);

        try {
            Map<String, Object> displayData = new HashMap<>();

            // Filter entries for display
            List<WorkTimeTable> displayableEntries = filterWorktimeEntriesForDisplay(worktimeData);

            // Calculate summary
            WorkTimeSummary summary = calculateMonthSummary(
                    displayableEntries,
                    year,
                    month,
                    user.getSchedule()
            );

            // Prepare display data
            displayData.put("worktimeData", displayableEntries);
            displayData.put("currentYear", year);
            displayData.put("currentMonth", month);
            displayData.put("user", sanitizeUserData(user));
            displayData.put("summary", summary);

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

    // Gets time off summary for a user using read-only operations.
    public TimeOffSummary getTimeOffSummary(String username, int year) {
        return userTimeOffService.calculateTimeOffSummaryReadOnly(username, year);
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
        if (SyncStatus.ADMIN_BLANK.equals(entry.getAdminSync())) {
            return false;
        }

        // Display USER_IN_PROCESS entries with partial info
        if (SyncStatus.USER_IN_PROCESS.equals(entry.getAdminSync())) {
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
        if (SyncStatus.USER_IN_PROCESS.equals(entry.getAdminSync())) {
            displayEntry.setTotalWorkedMinutes(null);
            displayEntry.setTotalOvertimeMinutes(null);
            displayEntry.setDayEndTime(null);
            displayEntry.setLunchBreakDeducted(false);
        } else {
            displayEntry.setTotalWorkedMinutes(entry.getTotalWorkedMinutes());
            displayEntry.setTotalOvertimeMinutes(entry.getTotalOvertimeMinutes());
        }

        return displayEntry;
    }

    private WorkTimeSummary calculateMonthSummary(
            List<WorkTimeTable> worktimeData,
            int year,
            int month,
            int schedule) {

        try {
            int totalWorkDays = calculateWorkDays(year, month);
            WorkTimeCounts counts = calculateWorkTimeCounts(worktimeData);

            return WorkTimeSummary.builder()
                    .totalWorkDays(totalWorkDays)
                    .daysWorked(counts.getDaysWorked())
                    .remainingWorkDays(totalWorkDays - (
                            counts.getDaysWorked() + counts.getSnDays() + counts.getCoDays() + counts.getCmDays()))
                    .snDays(counts.getSnDays())
                    .coDays(counts.getCoDays())
                    .cmDays(counts.getCmDays())
                    .totalRegularMinutes(counts.getRegularMinutes())
                    .totalOvertimeMinutes(counts.getOvertimeMinutes())
                    .totalMinutes(counts.getRegularMinutes() + counts.getOvertimeMinutes())
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

        for (WorkTimeTable entry : worktimeData) {
            // Skip in-process entries
            if (SyncStatus.USER_IN_PROCESS.equals(entry.getAdminSync())) {
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
                // Add this day's regular minutes to total
                totalRegularMinutes += entry.getTotalWorkedMinutes();
                if (entry.getTotalOvertimeMinutes() != null && entry.getTotalOvertimeMinutes() > 0) {
                    totalOvertimeMinutes += entry.getTotalOvertimeMinutes();
                }
            }
        }
        counts.setRegularMinutes(totalRegularMinutes);
        counts.setOvertimeMinutes(totalOvertimeMinutes);

        return counts;
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