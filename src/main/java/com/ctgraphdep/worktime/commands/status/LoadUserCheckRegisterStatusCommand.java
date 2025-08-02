package com.ctgraphdep.worktime.commands.status;

import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.worktime.commands.WorktimeOperationCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.accessor.NetworkOnlyAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REFACTORED: Command to load check register data for status display using accessor pattern.
 * Always uses NetworkOnlyAccessor for consistent cross-user viewing.
 * Used by StatusController for check register status viewing with filtering.
 * FIXED: Uses correct field names from RegisterCheckEntry model.
 */
public class LoadUserCheckRegisterStatusCommand extends WorktimeOperationCommand<LoadUserCheckRegisterStatusCommand.CheckRegisterStatusData> {
    private final String targetUsername;
    private final Integer targetUserId;
    private final int year;
    private final int month;
    private final String searchTerm;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String checkType;
    private final String designerName;
    private final String approvalStatus;

    public LoadUserCheckRegisterStatusCommand(WorktimeOperationContext context, String targetUsername,
                                              Integer targetUserId, int year, int month,
                                              String searchTerm, LocalDate startDate, LocalDate endDate,
                                              String checkType, String designerName, String approvalStatus) {
        super(context);
        this.targetUsername = targetUsername;
        this.targetUserId = targetUserId;
        this.year = year;
        this.month = month;
        this.searchTerm = searchTerm;
        this.startDate = startDate;
        this.endDate = endDate;
        this.checkType = checkType;
        this.designerName = designerName;
        this.approvalStatus = approvalStatus;
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
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating check register status load: target=%s, period=%d/%d", targetUsername, year, month));
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format("Loading check register status for %s - %d/%d using NetworkOnlyAccessor", targetUsername, year, month));

        try {
            // Always use NetworkOnlyAccessor for status viewing (consistent cross-user viewing)
            List<RegisterCheckEntry> entries = getRegisterCheckEntries();

            // Apply filters
            List<RegisterCheckEntry> filteredEntries = applyFilters(entries);

            // Calculate summary statistics
            CheckRegisterSummary summary = calculateSummary(filteredEntries);

            // Extract unique designers for filter dropdown
            List<String> uniqueDesigners = extractUniqueDesigners(entries);

            CheckRegisterStatusData statusData = new CheckRegisterStatusData(filteredEntries, summary, uniqueDesigners);

            String message = String.format("Loaded %d check register entries for %s (filtered from %d)", filteredEntries.size(), targetUsername, entries.size());

            LoggerUtil.info(this.getClass(), message);

            return OperationResult.successWithSideEffects(message, getOperationType(), statusData, null);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading check register status for %s - %d/%d: %s", targetUsername, year, month, e.getMessage()), e);

            // Return empty data on error
            CheckRegisterStatusData emptyData = new CheckRegisterStatusData(new ArrayList<>(), new CheckRegisterSummary(), new ArrayList<>());

            return OperationResult.successWithSideEffects(String.format("No check register data available for %s - %d/%d", targetUsername, year, month),
                    getOperationType(), emptyData, null);
        }
    }

    private @NotNull List<RegisterCheckEntry> getRegisterCheckEntries() {
        WorktimeDataAccessor accessor = new NetworkOnlyAccessor(
                context.getWorktimeDataService(),
                context.getRegisterDataService(),
                context.getCheckRegisterDataService(),
                context.getTimeOffDataService()
        );

        // Load check register entries using accessor
        List<RegisterCheckEntry> entries = accessor.readCheckRegister(targetUsername, targetUserId, year, month);
        if (entries == null) {
            entries = new ArrayList<>();
        }
        return entries;
    }

    // Apply search and filter criteria to entries
    private List<RegisterCheckEntry> applyFilters(List<RegisterCheckEntry> entries) {
        List<RegisterCheckEntry> filtered = new ArrayList<>(entries);

        // Filter by search term (search across multiple fields)
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String term = searchTerm.toLowerCase();
            filtered = filtered.stream()
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
            filtered = filtered.stream()
                    .filter(entry -> entry.getDate() != null && !entry.getDate().isBefore(startDate))
                    .collect(Collectors.toList());
        }

        if (endDate != null) {
            filtered = filtered.stream()
                    .filter(entry -> entry.getDate() != null && !entry.getDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Filter by check type
        if (checkType != null && !checkType.trim().isEmpty()) {
            filtered = filtered.stream()
                    .filter(entry -> checkType.equals(entry.getCheckType()))
                    .collect(Collectors.toList());
        }

        // Filter by designer name
        if (designerName != null && !designerName.trim().isEmpty()) {
            filtered = filtered.stream()
                    .filter(entry -> designerName.equals(entry.getDesignerName()))
                    .collect(Collectors.toList());
        }

        // Filter by approval status
        if (approvalStatus != null && !approvalStatus.trim().isEmpty()) {
            filtered = filtered.stream()
                    .filter(entry -> approvalStatus.equals(entry.getApprovalStatus()))
                    .collect(Collectors.toList());
        }

        // Sort by date (newest first)
        filtered.sort(Comparator.comparing(RegisterCheckEntry::getDate,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return filtered;
    }

    // FIXED: Calculate summary statistics for check register entries
    private CheckRegisterSummary calculateSummary(List<RegisterCheckEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return new CheckRegisterSummary();
        }

        int totalEntries = entries.size();
        int totalArticles = entries.stream().mapToInt(e -> e.getArticleNumbers() != null ? e.getArticleNumbers() : 0).sum();
        int totalFiles = entries.stream().mapToInt(e -> e.getFilesNumbers() != null ? e.getFilesNumbers() : 0).sum();
        double totalOrderValue = entries.stream().mapToDouble(e -> e.getOrderValue() != null ? e.getOrderValue() : 0.0).sum();

        // Calculate averages
        double avgArticles = (double) totalArticles / totalEntries;
        double avgFiles = (double) totalFiles / totalEntries;

        // Count by approval status
        long approvedCount = entries.stream().filter(e -> "APPROVED".equals(e.getApprovalStatus())).count();
        long pendingCount = entries.stream().filter(e -> "PENDING".equals(e.getApprovalStatus())).count();
        long rejectedCount = entries.stream().filter(e -> "REJECTED".equals(e.getApprovalStatus())).count();

        // Count by check type
        Map<String, Long> checkTypeCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getCheckType() != null ? entry.getCheckType() : "UNKNOWN",
                        Collectors.counting()
                ));

        // Count by approval status for template compatibility
        Map<String, Long> approvalStatusCounts = entries.stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getApprovalStatus() != null ? entry.getApprovalStatus() : "UNKNOWN",
                        Collectors.counting()
                ));

        return new CheckRegisterSummary(totalEntries, totalArticles, totalFiles, totalOrderValue,
                avgArticles, avgFiles, approvedCount, pendingCount, rejectedCount, checkTypeCounts,
                approvalStatusCounts);
    }

    // Extract unique designer names for filter dropdown
    private List<String> extractUniqueDesigners(List<RegisterCheckEntry> entries) {
        return entries.stream()
                .map(RegisterCheckEntry::getDesignerName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    protected String getCommandName() {
        return String.format("LoadUserCheckRegisterStatus[%s, %d/%d]", targetUsername, year, month);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.LOAD_USER_WORKTIME; // Reuse existing type
    }

    // Data container for check register status information
    @Getter
    public static class CheckRegisterStatusData {
        private final List<RegisterCheckEntry> entries;
        private final CheckRegisterSummary summary;
        private final List<String> uniqueDesigners;

        public CheckRegisterStatusData(List<RegisterCheckEntry> entries, CheckRegisterSummary summary,
                                       List<String> uniqueDesigners) {
            this.entries = entries;
            this.summary = summary;
            this.uniqueDesigners = uniqueDesigners;
        }
    }

    // Summary statistics for check register with approvalStatusCounts Map
    @Getter
    public static class CheckRegisterSummary {
        private final int totalEntries;
        private final int totalArticles;
        private final int totalFiles;
        private final double totalOrderValue;
        private final double avgArticles;
        private final double avgFiles;
        private final long approvedCount;
        private final long pendingCount;
        private final long rejectedCount;
        private final Map<String, Long> checkTypeCounts;
        private final Map<String, Long> approvalStatusCounts;

        // Empty constructor
        public CheckRegisterSummary() {
            this(0, 0, 0, 0.0, 0.0, 0.0, 0, 0, 0, new HashMap<>(), new HashMap<>());
        }

        // Full constructor
        public CheckRegisterSummary(int totalEntries, int totalArticles, int totalFiles, double totalOrderValue,
                                    double avgArticles, double avgFiles, long approvedCount, long pendingCount,
                                    long rejectedCount, Map<String, Long> checkTypeCounts,
                                    Map<String, Long> approvalStatusCounts) {
            this.totalEntries = totalEntries;
            this.totalArticles = totalArticles;
            this.totalFiles = totalFiles;
            this.totalOrderValue = totalOrderValue;
            this.avgArticles = avgArticles;
            this.avgFiles = avgFiles;
            this.approvedCount = approvedCount;
            this.pendingCount = pendingCount;
            this.rejectedCount = rejectedCount;
            this.checkTypeCounts = checkTypeCounts != null ? checkTypeCounts : new HashMap<>();
            this.approvalStatusCounts = approvalStatusCounts != null ? approvalStatusCounts : new HashMap<>();
        }
    }
}
