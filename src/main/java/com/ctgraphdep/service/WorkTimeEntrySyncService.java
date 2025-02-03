package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.enums.WorktimeMergeRule;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkTimeEntrySyncService {
    private final DataAccessService dataAccessService;

    public WorkTimeEntrySyncService(DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public List<WorkTimeTable> synchronizeEntries(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Starting entry synchronization for user %s (%d) - %d/%d",
                            username, userId, month, year));

            // Load both user and admin entries
            List<WorkTimeTable> userEntries = loadUserEntries(username, year, month);
            List<WorkTimeTable> adminEntries = loadAdminEntries(userId, year, month);

            // Create maps for efficient lookup
            Map<LocalDate, WorkTimeTable> adminEntriesMap = createEntriesMap(adminEntries);
            Map<LocalDate, WorkTimeTable> userEntriesMap = createEntriesMap(userEntries);

            // Merge entries according to new rules
            List<WorkTimeTable> mergedEntries = mergeEntries(userEntriesMap, adminEntriesMap, userId);

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

    private Map<LocalDate, WorkTimeTable> createEntriesMap(List<WorkTimeTable> entries) {
        return entries.stream()
                .collect(Collectors.toMap(
                        WorkTimeTable::getWorkDate,
                        entry -> entry,
                        (e1, e2) -> e2  // Keep the latest entry in case of duplicates
                ));
    }

    private List<WorkTimeTable> mergeEntries(
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

            try {
                WorkTimeTable mergedEntry = WorktimeMergeRule.apply(userEntry, adminEntry);
                if (mergedEntry != null) {
                    // Ensure userId is set
                    if (mergedEntry.getUserId() == null) {
                        mergedEntry.setUserId(userId);
                    }
                    mergedEntries.add(mergedEntry);

                    LoggerUtil.debug(this.getClass(),
                            String.format("Processed entry for date %s, final status: %s",
                                    date, mergedEntry.getAdminSync()));
                }
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        String.format("Error merging entries for date %s: %s", date, e.getMessage()));
                // Continue processing other entries
            }
        }

        mergedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));
        return mergedEntries;
    }

    private WorkTimeTable processSingleEntry(WorkTimeTable userEntry, WorkTimeTable adminEntry) {
        // Handle USER_IN_PROCESS entries
        if (userEntry != null && SyncStatus.USER_IN_PROCESS.equals(userEntry.getAdminSync())) {
            return userEntry;
        }

        // Handle USER_EDITED entries (cannot be overwritten by ADMIN_BLANK)
        if (userEntry != null && SyncStatus.USER_EDITED.equals(userEntry.getAdminSync())) {
            if (adminEntry != null && !SyncStatus.ADMIN_BLANK.equals(adminEntry.getAdminSync())) {
                // If admin entry exists and matches, convert to USER_DONE
                if (entriesMatch(userEntry, adminEntry)) {
                    userEntry.setAdminSync(SyncStatus.USER_DONE);
                }
            }
            return userEntry;
        }

        // Admin's blank entry always removes the user entry
        if (adminEntry != null && SyncStatus.ADMIN_BLANK.equals(adminEntry.getAdminSync())) {
            return null; // Completely remove the entry
        }

        // Admin-edited entry takes precedence
        if (adminEntry != null && SyncStatus.ADMIN_EDITED.equals(adminEntry.getAdminSync())) {
            WorkTimeTable result = copyWorkTimeEntry(adminEntry);
            result.setAdminSync(SyncStatus.USER_DONE);
            return result;
        }

        return WorktimeMergeRule.apply(userEntry, adminEntry);
    }  //?????

    private boolean entriesMatch(WorkTimeTable entry1, WorkTimeTable entry2) {
        if (entry1 == null || entry2 == null) return false;

        return Objects.equals(entry1.getWorkDate(), entry2.getWorkDate()) &&
                Objects.equals(entry1.getTotalWorkedMinutes(), entry2.getTotalWorkedMinutes()) &&
                Objects.equals(entry1.getTimeOffType(), entry2.getTimeOffType());
    }

    private WorkTimeTable copyWorkTimeEntry(WorkTimeTable source) {
        WorkTimeTable copy = new WorkTimeTable();
        copy.setUserId(source.getUserId());
        copy.setWorkDate(source.getWorkDate());
        copy.setDayStartTime(source.getDayStartTime());
        copy.setDayEndTime(source.getDayEndTime());
        copy.setTemporaryStopCount(source.getTemporaryStopCount());
        copy.setLunchBreakDeducted(source.isLunchBreakDeducted());
        copy.setTimeOffType(source.getTimeOffType());
        copy.setTotalWorkedMinutes(source.getTotalWorkedMinutes());
        copy.setTotalTemporaryStopMinutes(source.getTotalTemporaryStopMinutes());
        copy.setTotalOvertimeMinutes(source.getTotalOvertimeMinutes());
        copy.setAdminSync(source.getAdminSync());
        return copy;
    }

    private List<WorkTimeTable> loadUserEntries(String username, int year, int month) {
        try {
            // Use readUserWorktime with isAdmin=false to read from local
            List<WorkTimeTable> entries = dataAccessService.readUserWorktime(username, year, month);

            if (entries == null) {
                entries = new ArrayList<>();
            }

            LoggerUtil.info(this.getClass(),
                    String.format("Loaded %d entries from user worktime file for %s",
                            entries.size(), username));
            return entries;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading user entries for %s: %s",
                            username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private List<WorkTimeTable> loadAdminEntries(Integer userId, int year, int month) {
        try {
            List<WorkTimeTable> allEntries = dataAccessService.readNetworkAdminWorktime(year, month);
            // allEntries will never be null now due to changes above

            List<WorkTimeTable> userEntries = allEntries.stream()
                    .filter(entry -> entry.getUserId() != null &&
                            entry.getUserId().equals(userId))
                    .collect(Collectors.toList());

            LoggerUtil.debug(this.getClass(),
                    String.format("Loaded %d admin entries for user %d from %d/%d",
                            userEntries.size(), userId, year, month));

            return userEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading admin entries for user %d: %s",
                            userId, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private void saveUserEntries(String username, List<WorkTimeTable> entries, int year, int month) {
        try {
            // Use writeUserWorktime which handles local save and network sync
            dataAccessService.writeUserWorktime(username, entries, year, month);

            LoggerUtil.info(this.getClass(),
                    String.format("Saved %d merged entries to user worktime file",
                            entries.size()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error saving user entries for %s: %s",
                            username, e.getMessage()));
            throw new RuntimeException("Failed to save worktime entries", e);
        }
    }
}