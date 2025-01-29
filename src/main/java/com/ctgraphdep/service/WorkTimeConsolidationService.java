package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.enums.WorktimeMergeRule;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class WorkTimeConsolidationService {
    private static final TypeReference<List<WorkTimeTable>> WORKTIME_LIST_TYPE = new TypeReference<>() {};
    private final DataAccessService dataAccess;
    private final UserService userService;
    private final ReentrantLock consolidationLock = new ReentrantLock();

    public WorkTimeConsolidationService(DataAccessService dataAccess, UserService userService) {
        this.dataAccess = dataAccess;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void consolidateWorkTimeEntries(int year, int month) {
        consolidationLock.lock();
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Starting worktime consolidation for %d/%d", month, year));

            // Get all non-admin users
            List<User> users = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin())
                    .toList();

            // Load existing admin entries
            List<WorkTimeTable> adminEntries = loadAdminWorktime(year, month);

            // Create a map of admin entries by user and date for efficient lookup
            Map<String, WorkTimeTable> adminEntriesMap = createAdminEntriesMap(adminEntries);

            // Process each user's entries
            List<WorkTimeTable> consolidatedEntries = new ArrayList<>();
            for (User user : users) {
                List<WorkTimeTable> userEntries = processUserEntries(user, year, month, adminEntriesMap);
                consolidatedEntries.addAll(userEntries);
            }

            // Sort and save consolidated entries
            saveConsolidatedEntries(consolidatedEntries, year, month);

        } finally {
            consolidationLock.unlock();
        }
    }

    private List<WorkTimeTable> loadAdminWorktime(int year, int month) {
        Path adminPath = dataAccess.getAdminWorktimePath(year, month);
        return dataAccess.readFile(adminPath, WORKTIME_LIST_TYPE, true);
    }

    private Map<String, WorkTimeTable> createAdminEntriesMap(List<WorkTimeTable> adminEntries) {
        return adminEntries.stream()
                .collect(Collectors.toMap(
                        entry -> createEntryKey(entry.getUserId(), entry.getWorkDate()),
                        entry -> entry,
                        (existing, replacement) -> replacement
                ));
    }

    private List<WorkTimeTable> processUserEntries(
            User user,
            int year,
            int month,
            Map<String, WorkTimeTable> adminEntriesMap) {

        // Load user's entries
        Path userPath = dataAccess.getUserWorktimePath(user.getUsername(), year, month);
        List<WorkTimeTable> userEntries = dataAccess.readFile(userPath, WORKTIME_LIST_TYPE, true);
        Map<LocalDate, WorkTimeTable> userEntriesMap = userEntries.stream()
                .collect(Collectors.toMap(
                        WorkTimeTable::getWorkDate,
                        entry -> entry,
                        (e1, e2) -> e2));

        List<WorkTimeTable> processedEntries = new ArrayList<>();

        // Process all admin entries first
        for (WorkTimeTable adminEntry : adminEntriesMap.values()) {
            if (adminEntry.getUserId().equals(user.getUserId())) {
                // Remove ADMIN_BLANK status if no corresponding user entry exists
                if (SyncStatus.ADMIN_BLANK.equals(adminEntry.getAdminSync())
                        && !userEntriesMap.containsKey(adminEntry.getWorkDate())) {
                    continue; // Skip this entry entirely
                }
                processedEntries.add(adminEntry);
            }
        }

        // Then process user entries
        for (WorkTimeTable userEntry : userEntries) {
            String entryKey = createEntryKey(userEntry.getUserId(), userEntry.getWorkDate());
            // Only add user entry if no admin entry exists for that date
            if (!adminEntriesMap.containsKey(entryKey)) {
                WorkTimeTable mergedEntry = WorktimeMergeRule.apply(userEntry, null);
                if (mergedEntry != null) {
                    processedEntries.add(mergedEntry);
                }
            }
        }

        return processedEntries;
    }

    private void saveConsolidatedEntries(List<WorkTimeTable> entries, int year, int month) {
        // Sort entries by date and user ID
        entries.sort(Comparator
                .comparing(WorkTimeTable::getWorkDate)
                .thenComparing(WorkTimeTable::getUserId));

        // Save to admin worktime file
        dataAccess.writeFile(
                dataAccess.getAdminWorktimePath(year, month),
                entries
        );

        LoggerUtil.info(this.getClass(),
                String.format("Saved %d consolidated entries for %d/%d",
                        entries.size(), month, year));
    }

    private String createEntryKey(Integer userId, LocalDate date) {
        return userId + "_" + date.toString();
    }

    public List<WorkTimeTable> getViewableEntries(int year, int month) {
        List<WorkTimeTable> allEntries = loadAdminWorktime(year, month);

        return allEntries.stream()
                .filter(entry ->
                        !SyncStatus.USER_IN_PROCESS.name().equals(entry.getAdminSync()))
                .collect(Collectors.toList());
    }
}