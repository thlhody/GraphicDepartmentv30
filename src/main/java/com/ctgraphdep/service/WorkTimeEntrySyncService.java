package com.ctgraphdep.service;

import com.ctgraphdep.model.SyncStatus;
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

    /**
     * Synchronizes worktime entries between general and user-specific files
     *
     * @param username User's username
     * @param userId   User's ID
     * @param year     Year to sync
     * @param month    Month to sync
     * @return List of synchronized entries
     */
    public List<WorkTimeTable> synchronizeEntries(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Starting entry synchronization for user %s (%d) - %d/%d",
                            username, userId, month, year));

            // 1. Load entries from general worktime (from network path)
            List<WorkTimeTable> generalEntries = loadGeneralEntries(userId, year, month);

            // 2. Load user entries (from network path)
            List<WorkTimeTable> userEntries = loadUserEntries(username, year, month);

            // 3. Process and merge entries
            List<WorkTimeTable> mergedEntries = mergeEntries(generalEntries, userEntries);

            // 4. Save to network path if there were admin edits
            if (hasAdminEdits(generalEntries)) {
                saveUserEntries(username, mergedEntries, year, month);
                LoggerUtil.info(this.getClass(),
                        String.format("Updated user entries with admin edits for %s", username));
            }

            return mergedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error synchronizing entries for user %s: %s",
                            username, e.getMessage()));
            throw new RuntimeException("Failed to synchronize worktime entries", e);
        }
    }

    private List<WorkTimeTable> loadGeneralEntries(Integer userId, int year, int month) {
        Path generalPath = dataAccess.getAdminWorktimePath(year, month);

        List<WorkTimeTable> allEntries = dataAccess.readFile(generalPath, WORKTIME_LIST_TYPE, true);

        List<WorkTimeTable> userEntries = allEntries.stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .collect(Collectors.toList());

        LoggerUtil.info(this.getClass(),
                String.format("Loaded %d entries for user %d from general worktime",
                        userEntries.size(), userId));

        return userEntries;
    }

    private List<WorkTimeTable> loadUserEntries(String username, int year, int month) {
        Path userPath = dataAccess.getUserWorktimePath(username, year, month);

        List<WorkTimeTable> entries = dataAccess.readFile(userPath, WORKTIME_LIST_TYPE, true);

        LoggerUtil.info(this.getClass(),
                String.format("Loaded %d entries from user worktime file for %s",
                        entries.size(), username));

        return entries;
    }

    private List<WorkTimeTable> mergeEntries(List<WorkTimeTable> generalEntries, List<WorkTimeTable> userEntries) {
        Map<LocalDate, WorkTimeTable> mergedEntriesMap = new LinkedHashMap<>();

        // First, add all existing valid user entries
        userEntries.stream()
                .filter(entry -> entry.getAdminSync() == SyncStatus.USER_INPUT ||
                        entry.getAdminSync() == SyncStatus.USER_IN_PROCESS)
                .forEach(entry -> mergedEntriesMap.put(entry.getWorkDate(), entry));

        // Then overlay admin entries (both edited and blank)
        generalEntries.stream()
                .filter(entry -> entry.getAdminSync() == SyncStatus.ADMIN_EDITED
                        || entry.getAdminSync() == SyncStatus.ADMIN_BLANK)
                .forEach(entry -> {
                    if (entry.getAdminSync() == SyncStatus.ADMIN_BLANK) {
                        // Remove entry if admin marked it as blank
                        mergedEntriesMap.remove(entry.getWorkDate());
                        LoggerUtil.info(this.getClass(),
                                String.format("Removed blank entry for date: %s", entry.getWorkDate()));
                    } else {
                        WorkTimeTable mergedEntry = copyWorkTimeEntry(entry);
                        mergedEntry.setAdminSync(SyncStatus.USER_DONE);
                        mergedEntriesMap.put(entry.getWorkDate(), mergedEntry);
                        LoggerUtil.info(this.getClass(),
                                String.format("Updated entry with admin edits for date: %s", entry.getWorkDate()));
                    }
                });

        List<WorkTimeTable> mergedList = new ArrayList<>(mergedEntriesMap.values());
        mergedList.sort(Comparator.comparing(WorkTimeTable::getWorkDate));

        LoggerUtil.info(this.getClass(),
                String.format("Merged entries: %d total entries", mergedList.size()));

        return mergedList;
    }

    private WorkTimeTable copyWorkTimeEntry(WorkTimeTable source) {
        WorkTimeTable copy = new WorkTimeTable();
        // Basic user info
        copy.setUserId(source.getUserId());
        copy.setWorkDate(source.getWorkDate());
        // Time entries
        copy.setDayStartTime(source.getDayStartTime());
        copy.setDayEndTime(source.getDayEndTime());
        // Break information
        copy.setTemporaryStopCount(source.getTemporaryStopCount());
        copy.setLunchBreakDeducted(source.isLunchBreakDeducted());
        //Time of type
        copy.setTimeOffType(source.getTimeOffType());
        // Time calculations
        copy.setTotalWorkedMinutes(source.getTotalWorkedMinutes());
        copy.setTotalTemporaryStopMinutes(source.getTotalTemporaryStopMinutes());
        copy.setTotalOvertimeMinutes(source.getTotalOvertimeMinutes());
        // Status state
        copy.setAdminSync(source.getAdminSync());
        return copy;
    }

    private boolean hasAdminEdits(List<WorkTimeTable> generalEntries) {
        return generalEntries.stream()
                .anyMatch(entry -> SyncStatus.ADMIN_EDITED.equals(entry.getAdminSync()));
    }

    private void saveUserEntries(String username, List<WorkTimeTable> entries, int year, int month) {
        Path userPath = dataAccess.getUserWorktimePath(username, year, month);
        dataAccess.writeFile(userPath, entries);

        LoggerUtil.info(this.getClass(),
                String.format("Saved %d merged entries to user worktime file",
                        entries.size()));
    }
}