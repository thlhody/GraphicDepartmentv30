package com.ctgraphdep.worktime.service;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.merge.engine.UniversalMergeEngine;
import com.ctgraphdep.merge.enums.EntityType;
import com.ctgraphdep.merge.wrapper.GenericEntityWrapper;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.util.WorktimeWrapperFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REFACTORED WorktimeMergeService - Now uses GenericEntityWrapper for universal merge support.
 * Key Changes:
 * - Removed complex WorkTimeUniversalWrapper completely
 * - Uses GenericEntityWrapper from merge package
 * - Simplified merge logic with factory pattern
 * - Maintains same public API for backward compatibility
 * - All merge decisions now use Universal Merge Engine with consistent behavior
 */
@Service
public class WorktimeMergeService {

    public WorktimeMergeService() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // CORE MERGE METHODS - Using GenericEntityWrapper
    // ========================================================================

    /**
     * REFACTORED: Main merge method using GenericEntityWrapper
     */
    private WorkTimeTable mergeWorkTimeEntries(WorkTimeTable entry1, WorkTimeTable entry2) {
        LoggerUtil.debug(this.getClass(), String.format("Universal merge: entry1=%s, entry2=%s",
                getEntryStatusString(entry1), getEntryStatusString(entry2)));

        if (entry1 == null && entry2 == null) {
            return null;
        }

        // Use GenericEntityWrapper instead of WorkTimeUniversalWrapper
        GenericEntityWrapper<WorkTimeTable> wrapper1 = WorktimeWrapperFactory.createWrapperSafe(entry1);
        GenericEntityWrapper<WorkTimeTable> wrapper2 = WorktimeWrapperFactory.createWrapperSafe(entry2);

        GenericEntityWrapper<WorkTimeTable> result = UniversalMergeEngine.merge(wrapper1, wrapper2, EntityType.WORKTIME);

        WorkTimeTable mergedEntry = result != null ? result.getEntity() : null;

        LoggerUtil.info(this.getClass(), String.format("Universal merge result: %s", getEntryStatusString(mergedEntry)));

        return mergedEntry;
    }

    /**
     * Merges entries from user and admin sources, applying Universal Merge rules.
     * PUBLIC API - unchanged for backward compatibility
     *
     * @param userEntries  User entries from their worktime file
     * @param adminEntries Admin entries from the admin worktime file
     * @param userId       ID of the user whose entries are being merged
     * @return List of merged entries
     */
    public List<WorkTimeTable> mergeEntries(List<WorkTimeTable> userEntries, List<WorkTimeTable> adminEntries, Integer userId) {

        LoggerUtil.info(this.getClass(), String.format("Starting Universal Merge: %d user entries, %d admin entries for user %d",
                userEntries != null ? userEntries.size() : 0, adminEntries != null ? adminEntries.size() : 0, userId));

        // Create maps for efficient lookup
        Map<LocalDate, WorkTimeTable> adminEntriesMap = createEntriesMap(adminEntries);
        Map<LocalDate, WorkTimeTable> userEntriesMap = createEntriesMap(userEntries);

        List<WorkTimeTable> result = mergeEntriesMaps(userEntriesMap, adminEntriesMap, userId);

        LoggerUtil.info(this.getClass(), String.format("Universal Merge completed: %d entries result", result.size()));

        return result;
    }

    /**
     * Merges entries using date-mapped collections with Universal Merge Engine.
     * PUBLIC API - unchanged for backward compatibility
     *
     * @param userEntriesMap  Map of user entries by date
     * @param adminEntriesMap Map of admin entries by date
     * @param userId          ID of the user whose entries are being merged
     * @return List of merged entries
     */
    public List<WorkTimeTable> mergeEntriesMaps(Map<LocalDate, WorkTimeTable> userEntriesMap, Map<LocalDate, WorkTimeTable> adminEntriesMap, Integer userId) {

        List<WorkTimeTable> mergedEntries = new ArrayList<>();
        Set<LocalDate> allDates = new HashSet<>();

        if (userEntriesMap != null) {
            allDates.addAll(userEntriesMap.keySet());
        }

        if (adminEntriesMap != null) {
            allDates.addAll(adminEntriesMap.keySet());
        }

        LoggerUtil.debug(this.getClass(), String.format("Processing %d unique dates for merge", allDates.size()));

        for (LocalDate date : allDates) {
            WorkTimeTable userEntry = userEntriesMap != null ? userEntriesMap.get(date) : null;
            WorkTimeTable adminEntry = adminEntriesMap != null ? adminEntriesMap.get(date) : null;

            try {
                // Use Universal Merge Engine with GenericEntityWrapper
                WorkTimeTable mergedEntry = mergeWorkTimeEntries(userEntry, adminEntry);

                if (mergedEntry != null) {
                    // Ensure userId is set
                    if (mergedEntry.getUserId() == null) {
                        mergedEntry.setUserId(userId);
                    }
                    mergedEntries.add(mergedEntry);

                    LoggerUtil.debug(this.getClass(), String.format("Merged entry for date %s: %s",
                            date, getEntryStatusString(mergedEntry)));
                } else {
                    LoggerUtil.debug(this.getClass(), String.format("Entry for date %s was deleted by merge", date));
                }
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error merging entries for date %s: %s", date, e.getMessage()), e);
                // Continue with other dates
            }
        }

        mergedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));
        return mergedEntries;
    }



    // ========================================================================
    // HELPER METHODS - Simplified without complex wrapper logic
    // ========================================================================

    /**
     * PUBLIC API - unchanged for backward compatibility
     */
    public Map<LocalDate, WorkTimeTable> createEntriesMap(List<WorkTimeTable> entries) {
        if (entries == null) {
            return new HashMap<>();
        }

        return entries.stream().collect(Collectors.toMap(
                WorkTimeTable::getWorkDate,
                entry -> entry,
                (e1, e2) -> e2  // Keep the latest entry in case of duplicates
        ));
    }

    /**
     * SIMPLIFIED: Helper method for readable status logging
     */
    private String getEntryStatusString(WorkTimeTable entry) {
        if (entry == null) {
            return "null";
        }

        String status = entry.getAdminSync();
        if (status == null) {
            status = "null";
        }

        // Add timestamp info for timestamped statuses
        if (MergingStatusConstants.isTimestampedEditStatus(status)) {
            long timestamp = MergingStatusConstants.extractTimestamp(status);
            String editorType = MergingStatusConstants.getEditorType(status);
            return String.format("%s[%s:%d]", status, editorType, timestamp);
        }

        return String.format("%s[%s]", status, entry.getWorkDate());
    }

    /**
     * Log merge statistics for monitoring
     */
    public void logMergeStatistics(List<WorkTimeTable> userEntries, List<WorkTimeTable> adminEntries, List<WorkTimeTable> mergedEntries) {

        LoggerUtil.info(this.getClass(), String.format("Universal Merge Statistics - Input: %d user + %d admin = %d merged",
                userEntries != null ? userEntries.size() : 0,
                adminEntries != null ? adminEntries.size() : 0,
                mergedEntries != null ? mergedEntries.size() : 0));

        // Enhanced logging for timestamped statuses
        if (mergedEntries != null && !mergedEntries.isEmpty()) {
            long timestampedCount = mergedEntries.stream()
                    .filter(entry -> MergingStatusConstants.isTimestampedEditStatus(entry.getAdminSync()))
                    .count();

            if (timestampedCount > 0) {
                LoggerUtil.info(this.getClass(), String.format("Universal Merge Statistics - %d entries have timestamped edit statuses", timestampedCount));
            }
        }
    }

}