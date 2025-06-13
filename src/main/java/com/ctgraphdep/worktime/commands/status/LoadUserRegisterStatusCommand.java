package com.ctgraphdep.worktime.commands.status;

import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * COMPLETED: Command to load register data for status display (read-only).
 * Implements local → network → empty fallback strategy.
 * Handles both own data (local files) and other user data (network files).
 * Used by StatusController for cross-user register viewing.
 * Supports filtering by date range and other criteria.
 */
public class LoadUserRegisterStatusCommand extends WorktimeOperationCommand<LoadUserRegisterStatusCommand.RegisterStatusData> {
    private final String targetUsername;
    private final Integer targetUserId;
    private final Integer year;
    private final Integer month;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String searchTerm;
    private final String actionType;
    private final String printPrepTypes;
    private final String clientName;

    public LoadUserRegisterStatusCommand(WorktimeOperationContext context, String targetUsername,
                                         Integer targetUserId, Integer year, Integer month,
                                         LocalDate startDate, LocalDate endDate, String searchTerm,
                                         String actionType, String printPrepTypes, String clientName) {
        super(context);
        this.targetUsername = targetUsername;
        this.targetUserId = targetUserId;
        this.year = year;
        this.month = month;
        this.startDate = startDate;
        this.endDate = endDate;
        this.searchTerm = searchTerm;
        this.actionType = actionType;
        this.printPrepTypes = printPrepTypes;
        this.clientName = clientName;
    }

    @Override
    protected void validate() {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("Target username cannot be null or empty");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("Target user ID cannot be null");
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating register status load: target=%s, period=%s/%s, dateRange=%s to %s",
                targetUsername, year, month, startDate, endDate));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Loading register status for %s with filters", targetUsername));

        try {
            // Determine current user context (respects admin elevation)
            String currentUsername = context.getCurrentUsername();
            boolean isViewingOwnData = targetUsername.equals(currentUsername);

            // Load entries based on determined months
            List<RegisterEntry> entries = loadRegisterEntries(isViewingOwnData);

            // Apply filters
            List<RegisterEntry> filteredEntries = applyFilters(entries);

            // Extract unique clients for dropdown
            Set<String> uniqueClients = extractUniqueClients(entries);

            RegisterStatusData statusData = new RegisterStatusData(filteredEntries, uniqueClients);

            String message = String.format("Loaded %d register entries for %s (filtered from %d, source: %s)",
                    filteredEntries.size(), targetUsername, entries.size(),
                    isViewingOwnData ? "local/network" : "network");

            LoggerUtil.info(this.getClass(), message);

            return OperationResult.successWithSideEffects(message, getOperationType(), statusData, null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading register status for %s: %s", targetUsername, e.getMessage()), e);

            // Return empty data on error
            RegisterStatusData emptyData = new RegisterStatusData(new ArrayList<>(), new HashSet<>());
            return OperationResult.successWithSideEffects(
                    String.format("No register data available for %s", targetUsername),
                    getOperationType(), emptyData, null);
        }
    }

    /**
     * IMPLEMENTED: Load register entries with fallback strategy: local → network → empty
     */
    private List<RegisterEntry> loadRegisterEntries(boolean isViewingOwnData) {
        List<RegisterEntry> allEntries = new ArrayList<>();

        // Determine months to load based on criteria
        List<YearMonth> monthsToLoad = determineMonthsToLoad();

        LoggerUtil.debug(this.getClass(), String.format(
                "Loading register entries for %s across %d months", targetUsername, monthsToLoad.size()));

        for (YearMonth yearMonth : monthsToLoad) {
            List<RegisterEntry> monthEntries = loadMonthEntries(yearMonth, isViewingOwnData);
            allEntries.addAll(monthEntries);
        }

        LoggerUtil.debug(this.getClass(), String.format(
                "Loaded total of %d register entries for %s", allEntries.size(), targetUsername));

        return allEntries;
    }

    /**
     * Load register entries for a specific month with fallback strategy
     */
    private List<RegisterEntry> loadMonthEntries(YearMonth yearMonth, boolean isViewingOwnData) {
        int year = yearMonth.getYear();
        int month = yearMonth.getMonthValue();

        if (isViewingOwnData) {
            // Own data: try local first, then network fallback
            try {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Attempting to load local register for %s - %d/%d", targetUsername, year, month));

                List<RegisterEntry> localEntries = context.loadRegisterFromLocal(targetUsername, targetUserId, year, month);
                if (localEntries != null && !localEntries.isEmpty()) {
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Successfully loaded %d local register entries for %s - %d/%d",
                            localEntries.size(), targetUsername, year, month));
                    return localEntries;
                }
            } catch (Exception e) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Local register failed for %s - %d/%d: %s, trying network...",
                        targetUsername, year, month, e.getMessage()));
            }

            // Local failed, try network fallback
            try {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Attempting network fallback for register: %s - %d/%d", targetUsername, year, month));

                List<RegisterEntry> networkEntries = context.loadRegisterFromNetwork(targetUsername, targetUserId, year, month);
                if (networkEntries != null && !networkEntries.isEmpty()) {
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Successfully loaded %d network register entries for %s - %d/%d",
                            networkEntries.size(), targetUsername, year, month));
                    return networkEntries;
                }
            } catch (Exception e) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Network register failed for %s - %d/%d: %s, continuing with empty",
                        targetUsername, year, month, e.getMessage()));
            }
        } else {
            // Other user data: network only
            try {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Loading network register for other user %s - %d/%d", targetUsername, year, month));

                List<RegisterEntry> networkEntries = context.loadRegisterFromNetwork(targetUsername, targetUserId, year, month);
                if (networkEntries != null && !networkEntries.isEmpty()) {
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Successfully loaded %d network register entries for %s - %d/%d",
                            networkEntries.size(), targetUsername, year, month));
                    return networkEntries;
                }
            } catch (Exception e) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Network register failed for %s - %d/%d: %s, continuing with empty",
                        targetUsername, year, month, e.getMessage()));
            }
        }

        // Return empty list for this month
        return new ArrayList<>();
    }

    /**
     * Determine which months to load based on search criteria
     */
    private List<YearMonth> determineMonthsToLoad() {
        List<YearMonth> months = new ArrayList<>();

        // Case 1: Date range specified that spans multiple months
        if (startDate != null && endDate != null) {
            YearMonth start = YearMonth.from(startDate);
            YearMonth end = YearMonth.from(endDate);

            YearMonth current = start;
            while (!current.isAfter(end)) {
                months.add(current);
                current = current.plusMonths(1);
            }
            return months;
        }

        // Case 2: Specific year/month requested
        if (year != null && month != null) {
            months.add(YearMonth.of(year, month));
            return months;
        }

        // Case 3: Only year specified - load all months for that year
        if (year != null) {
            for (int m = 1; m <= 12; m++) {
                months.add(YearMonth.of(year, m));
            }
            return months;
        }

        // Case 4: Only start date specified - load from start date to current
        if (startDate != null) {
            YearMonth start = YearMonth.from(startDate);
            YearMonth now = YearMonth.now();

            YearMonth current = start;
            while (!current.isAfter(now)) {
                months.add(current);
                current = current.plusMonths(1);
            }
            return months;
        }

        // Default: load current month and previous month
        LocalDate currentDate = LocalDate.now();
        months.add(YearMonth.from(currentDate));
        months.add(YearMonth.from(currentDate.minusMonths(1)));

        return months;
    }

    /**
     * Apply search and filter criteria to entries
     */
    private List<RegisterEntry> applyFilters(List<RegisterEntry> entries) {
        List<RegisterEntry> filtered = new ArrayList<>(entries);

        // Filter by search term (search across multiple fields)
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String term = searchTerm.toLowerCase();
            filtered = filtered.stream()
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
            filtered = filtered.stream()
                    .filter(entry -> entry.getDate() != null && !entry.getDate().isBefore(startDate))
                    .collect(Collectors.toList());
        }

        if (endDate != null) {
            filtered = filtered.stream()
                    .filter(entry -> entry.getDate() != null && !entry.getDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Filter by action type
        if (actionType != null && !actionType.trim().isEmpty()) {
            filtered = filtered.stream()
                    .filter(entry -> actionType.equals(entry.getActionType()))
                    .collect(Collectors.toList());
        }

        // Filter by print prep type
        if (printPrepTypes != null && !printPrepTypes.trim().isEmpty()) {
            filtered = filtered.stream()
                    .filter(entry -> entry.getPrintPrepTypes() != null &&
                            entry.getPrintPrepTypes().contains(printPrepTypes))
                    .collect(Collectors.toList());
        }

        // Filter by client name
        if (clientName != null && !clientName.trim().isEmpty()) {
            filtered = filtered.stream()
                    .filter(entry -> clientName.equals(entry.getClientName()))
                    .collect(Collectors.toList());
        }

        // Sort by date (newest first)
        filtered.sort(Comparator.comparing(RegisterEntry::getDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return filtered;
    }

    /**
     * Extract unique clients for filter dropdown
     */
    private Set<String> extractUniqueClients(List<RegisterEntry> entries) {
        return entries.stream()
                .map(RegisterEntry::getClientName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    protected String getCommandName() {
        return String.format("LoadUserRegisterStatus[%s, %s/%s]", targetUsername, year, month);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.LOAD_USER_WORKTIME; // Reuse existing type
    }

    /**
     * Data container for register status information
     */
    @Getter
    public static class RegisterStatusData {
        private final List<RegisterEntry> entries;
        private final Set<String> uniqueClients;

        public RegisterStatusData(List<RegisterEntry> entries, Set<String> uniqueClients) {
            this.entries = entries;
            this.uniqueClients = uniqueClients;
        }
    }
}