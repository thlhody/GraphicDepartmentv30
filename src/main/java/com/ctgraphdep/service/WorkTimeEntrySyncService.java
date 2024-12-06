package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.enums.WorktimeMergeRule;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkTimeEntrySyncService {
    private final DataAccessService dataAccess;
    private static final TypeReference<List<WorkTimeTable>> WORKTIME_LIST_TYPE = new TypeReference<>() {
    };

    public WorkTimeEntrySyncService(DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), "Initializing WorkTime Entry Sync Service");
    }

    public List<WorkTimeTable> synchronizeEntries(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Starting entry synchronization for user %s (%d) - %d/%d",
                            username, userId, month, year));

            // Load user entries
            List<WorkTimeTable> userEntries = loadUserEntries(username, year, month);
            LoggerUtil.debug(this.getClass(),
                    String.format("Loaded %d user entries", userEntries.size()));

            // Load admin entries for this user
            List<WorkTimeTable> adminEntries = loadAdminEntries(userId, year, month);
            LoggerUtil.debug(this.getClass(),
                    String.format("Loaded %d admin entries", adminEntries.size()));

            // Create maps for efficient lookup
            Map<LocalDate, WorkTimeTable> adminEntriesMap = adminEntries.stream()
                    .collect(Collectors.toMap(
                            WorkTimeTable::getWorkDate,
                            entry -> entry,
                            (e1, e2) -> e2  // Keep the latest entry in case of duplicates
                    ));

            Map<LocalDate, WorkTimeTable> userEntriesMap = userEntries.stream()
                    .collect(Collectors.toMap(
                            WorkTimeTable::getWorkDate,
                            entry -> entry,
                            (e1, e2) -> e2
                    ));

            // Process and merge entries
            List<WorkTimeTable> mergedEntries = mergeEntries(userEntriesMap, adminEntriesMap);

            // Save merged entries back to user file
            saveUserEntries(username, mergedEntries, year, month);

            return mergedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error synchronizing entries for user %s: %s",
                            username, e.getMessage()));
            throw new RuntimeException("Failed to synchronize worktime entries", e);
        }
    }

    private List<WorkTimeTable> mergeEntries(
            Map<LocalDate, WorkTimeTable> userEntriesMap,
            Map<LocalDate, WorkTimeTable> adminEntriesMap) {

        List<WorkTimeTable> mergedEntries = new ArrayList<>();

        Set<LocalDate> allDates = new HashSet<>();
        allDates.addAll(userEntriesMap.keySet());
        allDates.addAll(adminEntriesMap.keySet());

        for (LocalDate date : allDates) {
            WorkTimeTable userEntry = userEntriesMap.get(date);
            WorkTimeTable adminEntry = adminEntriesMap.get(date);

            if (adminEntry != null && SyncStatus.ADMIN_BLANK.equals(adminEntry.getAdminSync())) {
                if (userEntry != null) {
                    LoggerUtil.debug(this.getClass(),
                            String.format("Removing entry for date %s due to ADMIN_BLANK status", date));
                }
                continue;
            }

            // Remove the redundant condition as it's already handled above

            WorkTimeTable mergedEntry = WorktimeMergeRule.apply(userEntry != null ? userEntry : createDefaultUserEntry(date), adminEntry);
            mergedEntries.add(mergedEntry);
            LoggerUtil.debug(this.getClass(),
                    String.format("Added merged entry for date %s with status %s",
                            date, mergedEntry.getAdminSync()));
        }

        mergedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));

        LoggerUtil.info(this.getClass(),
                String.format("Merged entries: %d total entries", mergedEntries.size()));

        return mergedEntries;
    }

    private WorkTimeTable createDefaultUserEntry(LocalDate date) {
        WorkTimeTable defaultEntry = new WorkTimeTable();
        defaultEntry.setUserId(null);
        defaultEntry.setWorkDate(date);
        defaultEntry.setAdminSync(SyncStatus.ADMIN_EDITED);
        return defaultEntry;
    }

    private List<WorkTimeTable> loadUserEntries(String username, int year, int month) {
        Path userPath = dataAccess.getUserWorktimePath(username, year, month);
        List<WorkTimeTable> entries = dataAccess.readFile(userPath, WORKTIME_LIST_TYPE, true);

        LoggerUtil.info(this.getClass(),
                String.format("Loaded %d entries from user worktime file for %s",
                        entries.size(), username));
        return entries;
    }

    private List<WorkTimeTable> loadAdminEntries(Integer userId, int year, int month) {
        Path adminPath = dataAccess.getAdminWorktimePath(year, month);
        List<WorkTimeTable> allEntries = dataAccess.readFile(adminPath, WORKTIME_LIST_TYPE, true);

        List<WorkTimeTable> userEntries = allEntries.stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .collect(Collectors.toList());

        LoggerUtil.info(this.getClass(),
                String.format("Loaded %d admin entries for user %d",
                        userEntries.size(), userId));
        return userEntries;
    }

    private void saveUserEntries(String username, List<WorkTimeTable> entries, int year, int month) {
        Path userPath = dataAccess.getUserWorktimePath(username, year, month);
        dataAccess.writeFile(userPath, entries);
        LoggerUtil.info(this.getClass(),
                String.format("Saved %d merged entries to user worktime file", entries.size()));
    }
}