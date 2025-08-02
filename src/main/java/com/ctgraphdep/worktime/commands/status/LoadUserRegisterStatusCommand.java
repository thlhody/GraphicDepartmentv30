package com.ctgraphdep.worktime.commands.status;

import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.accessor.NetworkOnlyAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        LoggerUtil.info(this.getClass(), String.format("Loading register status for %s using NetworkOnlyAccessor", targetUsername));

        try {
            // Always use NetworkOnlyAccessor for status viewing
            WorktimeDataAccessor accessor = new NetworkOnlyAccessor(
                    context.getWorktimeDataService(),
                    context.getRegisterDataService(),
                    context.getCheckRegisterDataService(),
                    context.getTimeOffDataService()
            );

            List<RegisterEntry> allEntries = new ArrayList<>();

            // Load data based on determined months
            List<YearMonth> monthsToLoad = determineMonthsToLoad();
            for (YearMonth monthToLoad : monthsToLoad) {
                List<RegisterEntry> monthEntries = accessor.readRegister(targetUsername, targetUserId, monthToLoad.getYear(), monthToLoad.getMonthValue());
                if (monthEntries != null) {
                    allEntries.addAll(monthEntries);
                }
            }

            // Apply filters
            List<RegisterEntry> filteredEntries = applyFilters(allEntries);

            // Extract unique clients for dropdown
            Set<String> uniqueClients = extractUniqueClients(allEntries);

            RegisterStatusData statusData = new RegisterStatusData(filteredEntries, uniqueClients);

            String message = String.format("Loaded %d register entries (%d after filtering) for %s", allEntries.size(), filteredEntries.size(), targetUsername);

            return OperationResult.success(message, getOperationType(), statusData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading register status for %s: %s", targetUsername, e.getMessage()), e);
            return OperationResult.failure("Failed to load register data: " + e.getMessage(), getOperationType());
        }
    }

    // Determine which months to load based on filters
    private List<YearMonth> determineMonthsToLoad() {
        List<YearMonth> months = new ArrayList<>();

        // If specific year/month provided, use that
        if (year != null && month != null) {
            months.add(YearMonth.of(year, month));
            return months;
        }

        // If date range provided, use that
        if (startDate != null && endDate != null) {
            YearMonth current = YearMonth.from(startDate);
            YearMonth end = YearMonth.from(endDate);
            while (!current.isAfter(end)) {
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

    // Apply search and filter criteria to entries - FIXED with correct field names
    private List<RegisterEntry> applyFilters(List<RegisterEntry> entries) {
        List<RegisterEntry> filtered = new ArrayList<>(entries);

        // Filter by search term (search across multiple fields) - FIXED field names
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

        // Filter by date range - FIXED field name
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

        // Sort by date (newest first) - FIXED field name
        filtered.sort(Comparator.comparing(RegisterEntry::getDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return filtered;
    }

    // Extract unique clients for filter dropdown
    private Set<String> extractUniqueClients(List<RegisterEntry> entries) {
        return entries.stream()
                .map(RegisterEntry::getClientName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    protected String getCommandName() {
        return String.format("LoadUserRegisterStatus[target=%s, period=%s/%s]", targetUsername, year, month);
    }

    @Override
    protected String getOperationType() {
        return "LOAD_REGISTER_STATUS";
    }

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