package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.enums.WorktimeMergeRule;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.WorkTimeEntryUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for merging worktime entries from different sources.
 * Centralizes merging logic that was previously duplicated across multiple services.
 */
@Service
public class WorktimeMergeService {

    public WorktimeMergeService() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Merges entries from user and admin sources, applying worktime merge rules.
     *
     * @param userEntries User entries from their worktime file
     * @param adminEntries Admin entries from the admin worktime file
     * @param userId ID of the user whose entries are being merged
     * @return List of merged entries
     */
    public List<WorkTimeTable> mergeEntries(
            List<WorkTimeTable> userEntries,
            List<WorkTimeTable> adminEntries,
            Integer userId) {

        // Create maps for efficient lookup
        Map<LocalDate, WorkTimeTable> adminEntriesMap = createEntriesMap(adminEntries);
        Map<LocalDate, WorkTimeTable> userEntriesMap = createEntriesMap(userEntries);

        return mergeEntriesMaps(userEntriesMap, adminEntriesMap, userId);
    }

    /**
     * Merges entries using date-mapped collections
     *
     * @param userEntriesMap Map of user entries by date
     * @param adminEntriesMap Map of admin entries by date
     * @param userId ID of the user whose entries are being merged
     * @return List of merged entries
     */
    public List<WorkTimeTable> mergeEntriesMaps(
            Map<LocalDate, WorkTimeTable> userEntriesMap,
            Map<LocalDate, WorkTimeTable> adminEntriesMap,
            Integer userId) {

        List<WorkTimeTable> mergedEntries = new ArrayList<>();
        Set<LocalDate> allDates = new HashSet<>();

        if (userEntriesMap != null) {
            allDates.addAll(userEntriesMap.keySet());
        }

        if (adminEntriesMap != null) {
            allDates.addAll(adminEntriesMap.keySet());
        }

        for (LocalDate date : allDates) {
            WorkTimeTable userEntry = userEntriesMap != null ? userEntriesMap.get(date) : null;
            WorkTimeTable adminEntry = adminEntriesMap != null ? adminEntriesMap.get(date) : null;

            // Add specific handling for USER_INPUT vs USER_IN_PROCESS conflict
            if (userEntry != null && adminEntry != null &&
                    SyncStatusWorktime.USER_INPUT.equals(userEntry.getAdminSync()) &&
                    SyncStatusWorktime.USER_IN_PROCESS.equals(adminEntry.getAdminSync())) {

                LoggerUtil.warn(this.getClass(), String.format("Conflict detected: User has resolved entry (USER_INPUT) for %s, " +
                                "but admin has unresolved entry (USER_IN_PROCESS). Keeping user's resolved entry.", date));

                // Always keep the user's resolved entry in this case
                mergedEntries.add(userEntry);
                continue;  // Skip the normal merge process for this entry
            }

            boolean isUserInProcess = userEntry != null && SyncStatusWorktime.USER_IN_PROCESS.equals(userEntry.getAdminSync());
            boolean isAdminBlank = adminEntry != null && SyncStatusWorktime.ADMIN_BLANK.equals(adminEntry.getAdminSync());

            try {
                WorkTimeTable mergedEntry = WorktimeMergeRule.apply(userEntry, adminEntry);

                if (mergedEntry != null) {
                    // Ensure userId is set
                    if (mergedEntry.getUserId() == null) {
                        mergedEntry.setUserId(userId);
                    }
                    mergedEntries.add(mergedEntry);

                    if (isUserInProcess) {
                        LoggerUtil.debug(this.getClass(), String.format("Preserved USER_IN_PROCESS entry for date %s", date));
                    } else {
                        LoggerUtil.debug(this.getClass(), String.format("Processed entry for date %s, final status: %s", date, mergedEntry.getAdminSync()));
                    }
                } else if (isAdminBlank) {
                    LoggerUtil.info(this.getClass(), String.format("Entry for date %s removed due to ADMIN_BLANK", date));
                }
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error merging entries for date %s: %s", date, e.getMessage()));
            }
        }

        mergedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));
        return mergedEntries;
    }

    /**
     * Merge admin entries for consolidation.
     * This method handles merging slightly differently for the consolidation process.
     *
     * @param user The entry from the user's worktime file
     * @param admin The entry from the admin worktime file
     * @param userEntriesMap Map of processed entries to check for duplicates
     * @return The merged entry
     */
    public WorkTimeTable mergeForConsolidation(
            WorkTimeTable user,
            WorkTimeTable admin,
            Map<String, WorkTimeTable> userEntriesMap) {

        if (user == null) {
            return admin;
        }

        String entryKey = WorkTimeEntryUtil.createEntryKey(user.getUserId(), user.getWorkDate());

        // Skip if we already processed this entry
        if (userEntriesMap.containsKey(entryKey)) {
            return null;
        }

        // Special case: Resolved user entries take precedence over unresolved admin entries
        if (SyncStatusWorktime.USER_INPUT.equals(user.getAdminSync()) &&
                admin != null && SyncStatusWorktime.USER_IN_PROCESS.equals(admin.getAdminSync())) {

            LoggerUtil.info(this.getClass(), String.format("Admin consolidation: Updating admin USER_IN_PROCESS entry with user's resolved entry for %s", user.getWorkDate()));
            return user;
        }

        // Skip USER_IN_PROCESS entries - they should remain in user file only
        if (SyncStatusWorktime.USER_IN_PROCESS.equals(user.getAdminSync())) {
            LoggerUtil.debug(this.getClass(), String.format("Admin consolidation: Skipping USER_IN_PROCESS entry for date %s", user.getWorkDate()));
            return null;
        }

        // For all other user entries, apply merge rules
        return WorktimeMergeRule.apply(user, admin);
    }

    /**
     * Creates a map of entries by date for efficient lookup
     */
    public Map<LocalDate, WorkTimeTable> createEntriesMap(List<WorkTimeTable> entries) {
        if (entries == null) {
            return new HashMap<>();
        }

        return entries.stream()
                .collect(Collectors.toMap(
                        WorkTimeTable::getWorkDate,
                        entry -> entry,
                        (e1, e2) -> e2  // Keep the latest entry in case of duplicates
                ));
    }
}