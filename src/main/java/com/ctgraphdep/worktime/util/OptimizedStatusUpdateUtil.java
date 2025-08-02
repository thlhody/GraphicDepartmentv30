package com.ctgraphdep.worktime.util;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class OptimizedStatusUpdateUtil {

    private static final String LOGGER_CLASS = OptimizedStatusUpdateUtil.class.getSimpleName();

    // Update statuses only for changed entries using batch operations
    public static StatusUpdateResult updateChangedEntriesOnly(List<WorkTimeTable> newEntries,
            List<WorkTimeTable> existingEntries, String operationContext) {

        long startTime = System.currentTimeMillis();
        LoggerUtil.info(LOGGER_CLASS.getClass(), String.format(
                "Starting OPTIMIZED status update: %d new entries, %d existing entries (%s)",
                newEntries.size(), existingEntries.size(), operationContext));

        // OPTIMIZATION 1: Create lookup maps (O(n) instead of O(n²))
        Map<String, WorkTimeTable> existingEntriesMap = createEntryLookupMap(existingEntries);

        // OPTIMIZATION 2: Track actual changes
        ChangeTracker changeTracker = identifyChangedEntries(newEntries, existingEntriesMap);

        // OPTIMIZATION 3: Batch process entries
        List<WorkTimeTable> processedEntries = batchProcessEntries(newEntries);

        long endTime = System.currentTimeMillis();
        StatusUpdateResult result = new StatusUpdateResult(processedEntries, changeTracker.getChangedDates().size(),
                newEntries.size(), endTime - startTime, changeTracker.getChangeStatistics());

        LoggerUtil.info(LOGGER_CLASS.getClass(), String.format("OPTIMIZED status update completed: %d total entries, %d changed, %d preserved, %dms (%s)",
                result.getTotalEntries(), result.getChangedEntries(), result.getPreservedEntries(), result.getProcessingTimeMs(), operationContext));

        return result;
    }

    //Create efficient lookup map (O(n) creation, O(1) lookup)
    private static Map<String, WorkTimeTable> createEntryLookupMap(List<WorkTimeTable> entries) {
        if (entries == null || entries.isEmpty()) {
            return new HashMap<>();
        }

        return entries.stream().collect(Collectors.toMap(entry -> createEntryKey(entry.getUserId(), entry.getWorkDate()),
                        entry -> entry, (existing, replacement) -> replacement));
    }

    // Identify actually changed entries using efficient comparison
    private static ChangeTracker identifyChangedEntries(List<WorkTimeTable> newEntries, Map<String, WorkTimeTable> existingEntriesMap) {

        ChangeTracker tracker = new ChangeTracker();

        for (WorkTimeTable newEntry : newEntries) {
            String entryKey = createEntryKey(newEntry.getUserId(), newEntry.getWorkDate());
            WorkTimeTable existingEntry = existingEntriesMap.get(entryKey);

            if (existingEntry == null) {
                // New entry
                tracker.markAsNew(newEntry.getWorkDate());
                LoggerUtil.debug(LOGGER_CLASS.getClass(), String.format("New entry detected: user %d on %s", newEntry.getUserId(), newEntry.getWorkDate()));
            } else if (hasContentChanged(newEntry, existingEntry)) {
                // Changed entry
                tracker.markAsChanged(newEntry.getWorkDate(), existingEntry);
                LoggerUtil.debug(LOGGER_CLASS.getClass(), String.format("Changed entry detected: user %d on %s", newEntry.getUserId(), newEntry.getWorkDate()));
            } else {
                // Unchanged entry
                tracker.markAsUnchanged(newEntry.getWorkDate(), existingEntry);
            }
        }

        return tracker;
    }

    // Batch process entries with smart status handling
    private static List<WorkTimeTable> batchProcessEntries(List<WorkTimeTable> newEntries) {

        List<WorkTimeTable> processedEntries = new ArrayList<>();

        for (WorkTimeTable newEntry : newEntries) {
            WorkTimeTable processedEntry = cloneEntry(newEntry);

            LoggerUtil.debug(LOGGER_CLASS.getClass(), String.format("PRESERVING existing status '%s' for user %d on %s",
                    processedEntry.getAdminSync(), newEntry.getUserId(), newEntry.getWorkDate()));

            processedEntries.add(processedEntry);
        }

        return processedEntries;
    }

    // Efficient content change detection (business fields only)
    private static boolean hasContentChanged(WorkTimeTable newEntry, WorkTimeTable existingEntry) {
        boolean workedMinutesChanged = !Objects.equals(newEntry.getTotalWorkedMinutes(), existingEntry.getTotalWorkedMinutes());
        boolean overtimeChanged = !Objects.equals(newEntry.getTotalOvertimeMinutes(), existingEntry.getTotalOvertimeMinutes());
        boolean tempStopChanged = !Objects.equals(newEntry.getTotalTemporaryStopMinutes(), existingEntry.getTotalTemporaryStopMinutes());
        boolean tempStopCountChanged = !Objects.equals(newEntry.getTemporaryStopCount(), existingEntry.getTemporaryStopCount());
        boolean timeOffChanged = !Objects.equals(newEntry.getTimeOffType(), existingEntry.getTimeOffType());
        boolean startTimeChanged = !Objects.equals(newEntry.getDayStartTime(), existingEntry.getDayStartTime());
        boolean endTimeChanged = !Objects.equals(newEntry.getDayEndTime(), existingEntry.getDayEndTime());
        boolean lunchChanged = newEntry.isLunchBreakDeducted() != existingEntry.isLunchBreakDeducted();

        boolean hasChanged = workedMinutesChanged || overtimeChanged || tempStopChanged || tempStopCountChanged ||
                timeOffChanged || startTimeChanged || endTimeChanged || lunchChanged;

        if (hasChanged) {
            LoggerUtil.debug(LOGGER_CLASS.getClass(), String.format(
                    "Content change detected for user %d on %s: worked=%s, overtime=%s, tempStop=%s, tempCount=%s, timeOff=%s, start=%s, end=%s, lunch=%s",
                    newEntry.getUserId(), newEntry.getWorkDate(),
                    workedMinutesChanged ? "CHANGED" : "same",
                    overtimeChanged ? "CHANGED" : "same",
                    tempStopChanged ? "CHANGED" : "same",
                    tempStopCountChanged ? "CHANGED" : "same",
                    timeOffChanged ? "CHANGED" : "same",
                    startTimeChanged ? "CHANGED" : "same",
                    endTimeChanged ? "CHANGED" : "same",
                    lunchChanged ? "CHANGED" : "same"));

            LoggerUtil.debug(LOGGER_CLASS.getClass(), String.format(
                    "Field values - TempStop: %d → %d, TempCount: %d → %d, Worked: %d → %d",
                    existingEntry.getTotalTemporaryStopMinutes() != null ? existingEntry.getTotalTemporaryStopMinutes() : 0,
                    newEntry.getTotalTemporaryStopMinutes() != null ? newEntry.getTotalTemporaryStopMinutes() : 0,
                    existingEntry.getTemporaryStopCount() != null ? existingEntry.getTemporaryStopCount() : 0,
                    newEntry.getTemporaryStopCount() != null ? newEntry.getTemporaryStopCount() : 0,
                    existingEntry.getTotalWorkedMinutes() != null ? existingEntry.getTotalWorkedMinutes() : 0,
                    newEntry.getTotalWorkedMinutes() != null ? newEntry.getTotalWorkedMinutes() : 0));
        }

        return hasChanged;
    }

    // ════════════════════════════════════════════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ════════════════════════════════════════════════════════════════════════════════════════════════════════════════

    private static String createEntryKey(Integer userId, LocalDate date) {
        return userId + "_" + date.toString();
    }

    private static WorkTimeTable cloneEntry(WorkTimeTable original) {
        WorkTimeTable clone = new WorkTimeTable();
        clone.setUserId(original.getUserId());
        clone.setWorkDate(original.getWorkDate());
        clone.setDayStartTime(original.getDayStartTime());
        clone.setDayEndTime(original.getDayEndTime());
        clone.setTotalWorkedMinutes(original.getTotalWorkedMinutes());
        clone.setTotalOvertimeMinutes(original.getTotalOvertimeMinutes());
        clone.setTotalTemporaryStopMinutes(original.getTotalTemporaryStopMinutes());
        clone.setTemporaryStopCount(original.getTemporaryStopCount());
        clone.setLunchBreakDeducted(original.isLunchBreakDeducted());
        clone.setTimeOffType(original.getTimeOffType());
        clone.setAdminSync(original.getAdminSync());
        return clone;
    }

    // ════════════════════════════════════════════════════════════════════════════════════════════════════════════════
    // CHANGE TRACKING CLASSES
    // ════════════════════════════════════════════════════════════════════════════════════════════════════════════════

    // Efficient change tracking with performance metrics
    @Getter
    private static class ChangeTracker {
        private final Set<LocalDate> newDates = new HashSet<>();
        private final Set<LocalDate> changedDates = new HashSet<>();
        private final Set<LocalDate> unchangedDates = new HashSet<>();
        private final Map<LocalDate, WorkTimeTable> existingEntryMap = new HashMap<>();

        public void markAsNew(LocalDate date) {
            newDates.add(date);
        }

        public void markAsChanged(LocalDate date, WorkTimeTable existingEntry) {
            changedDates.add(date);
            existingEntryMap.put(date, existingEntry);
        }

        public void markAsUnchanged(LocalDate date, WorkTimeTable existingEntry) {
            unchangedDates.add(date);
            existingEntryMap.put(date, existingEntry);
        }

        public boolean isNew(LocalDate date) {
            return newDates.contains(date);
        }

        public boolean isChanged(LocalDate date) {
            return changedDates.contains(date);
        }

        public WorkTimeTable getExistingEntry(LocalDate date) {
            return existingEntryMap.get(date);
        }

        public Map<String, Integer> getChangeStatistics() {
            Map<String, Integer> stats = new HashMap<>();
            stats.put("newEntries", newDates.size());
            stats.put("changedEntries", changedDates.size());
            stats.put("unchangedEntries", unchangedDates.size());
            return stats;
        }
    }

    // Result tracking with performance metrics
    @Getter
    public static class StatusUpdateResult {
        private final List<WorkTimeTable> processedEntries;
        private final int changedEntries;
        private final int totalEntries;
        private final long processingTimeMs;
        private final Map<String, Integer> changeStatistics;

        public StatusUpdateResult(List<WorkTimeTable> processedEntries, int changedEntries,
                                  int totalEntries, long processingTimeMs, Map<String, Integer> changeStatistics) {
            this.processedEntries = processedEntries;
            this.changedEntries = changedEntries;
            this.totalEntries = totalEntries;
            this.processingTimeMs = processingTimeMs;
            this.changeStatistics = changeStatistics;
        }

        public int getPreservedEntries() {
            return totalEntries - changedEntries;
        }

        public double getOptimizationRatio() {
            return totalEntries > 0 ? (double) getPreservedEntries() / totalEntries : 0.0;
        }

        public String getPerformanceSummary() {
            return String.format("Optimization: %.1f%% entries preserved (%d/%d), %dms processing",
                    getOptimizationRatio() * 100, getPreservedEntries(), totalEntries, processingTimeMs);
        }
    }
}