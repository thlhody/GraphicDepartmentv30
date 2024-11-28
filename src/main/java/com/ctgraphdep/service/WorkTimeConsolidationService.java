package com.ctgraphdep.service;

import com.ctgraphdep.model.SyncStatus;
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

    public WorkTimeConsolidationService(
            DataAccessService dataAccess,
            UserService userService) {
        this.dataAccess = dataAccess;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), "Initializing WorkTime Consolidation Service");
    }

    /**
     * Consolidate all user worktime entries into general worktime
     *
     * @param year  Year to consolidate
     * @param month Month to consolidate
     */
    public void consolidateWorkTimeEntries(int year, int month) {
        consolidationLock.lock();
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Starting worktime consolidation for %d/%d", month, year));

            // Get all non-admin users
            List<User> users = userService.getAllUsers().stream()
                    .filter(user -> !user.isAdmin())
                    .toList();

            // Load existing general worktime entries
            List<WorkTimeTable> generalEntries = loadGeneralWorktime(year, month);

            // Create map of admin-edited entries by user and date
            Map<Integer, Map<LocalDate, WorkTimeTable>> adminEditedEntries =
                    extractAdminEditedEntries(generalEntries);

            // Process each user's entries
            List<WorkTimeTable> consolidatedEntries = new ArrayList<>();
            for (User user : users) {
                List<WorkTimeTable> userEntries = processUserEntries(
                        user,
                        year,
                        month,
                        adminEditedEntries.getOrDefault(user.getUserId(), new HashMap<>())
                );
                consolidatedEntries.addAll(userEntries);
            }

            // Sort entries
            consolidatedEntries.sort(Comparator
                    .comparing(WorkTimeTable::getWorkDate)
                    .thenComparing(WorkTimeTable::getUserId));

            // Save consolidated entries
            saveConsolidatedEntries(consolidatedEntries, year, month);

        } finally {
            consolidationLock.unlock();
        }
    }

    private List<WorkTimeTable> loadGeneralWorktime(int year, int month) {
        Path generalPath = dataAccess.getAdminWorktimePath(year, month);
        return dataAccess.readFile(generalPath, WORKTIME_LIST_TYPE, true);
    }

    private Map<Integer, Map<LocalDate, WorkTimeTable>> extractAdminEditedEntries(
            List<WorkTimeTable> generalEntries) {
        return generalEntries.stream()
                .filter(entry -> SyncStatus.ADMIN_EDITED.equals(entry.getAdminSync()))
                .collect(Collectors.groupingBy(
                        WorkTimeTable::getUserId,
                        Collectors.toMap(
                                WorkTimeTable::getWorkDate,
                                entry -> entry
                        )
                ));
    }

    private List<WorkTimeTable> processUserEntries(
            User user,
            int year,
            int month,
            Map<LocalDate, WorkTimeTable> adminEditedEntries) {

        // Load user's entries
        Path userPath = dataAccess.getUserWorktimePath(user.getUsername(), year, month);
        List<WorkTimeTable> userEntries = dataAccess.readFile(userPath, WORKTIME_LIST_TYPE, true);

        // Create map for existing general entries
        Path generalPath = dataAccess.getAdminWorktimePath(year, month);
        List<WorkTimeTable> existingGeneralEntries = dataAccess.readFile(generalPath, WORKTIME_LIST_TYPE, true);

        // Get admin entries (both edited and blank)
        List<WorkTimeTable> adminEntries = existingGeneralEntries.stream()
                .filter(entry -> entry.getUserId().equals(user.getUserId()))
                .filter(entry -> SyncStatus.ADMIN_EDITED.equals(entry.getAdminSync())
                        || SyncStatus.ADMIN_BLANK.equals(entry.getAdminSync()))
                .toList();

        // Get dates with admin actions (excluding ADMIN_BLANK)
        Set<LocalDate> adminEditDates = adminEntries.stream()
                .filter(entry -> SyncStatus.ADMIN_EDITED.equals(entry.getAdminSync()))
                .map(WorkTimeTable::getWorkDate)
                .collect(Collectors.toSet());

        // Get dates with ADMIN_BLANK status
        Set<LocalDate> adminBlankDates = adminEntries.stream()
                .filter(entry -> SyncStatus.ADMIN_BLANK.equals(entry.getAdminSync()))
                .map(WorkTimeTable::getWorkDate)
                .collect(Collectors.toSet());

        // Process user entries:
        // 1. Exclude dates with admin edits
        // 2. Exclude dates that are marked as ADMIN_BLANK
        List<WorkTimeTable> processedUserEntries = userEntries.stream()
                .filter(entry -> !adminEditDates.contains(entry.getWorkDate()))
                .filter(entry -> !adminBlankDates.contains(entry.getWorkDate()))
                .filter(entry -> !SyncStatus.USER_IN_PROCESS.equals(entry.getAdminSync()))  // Skip in-process entries
                .peek(entry -> entry.setAdminSync(SyncStatus.USER_DONE))
                .toList();

        // Combine entries:
        // 1. Keep all admin entries (both EDITED and BLANK)
        // 2. Add processed user entries
        List<WorkTimeTable> result = new ArrayList<>(adminEntries);
        result.addAll(processedUserEntries);

        // Sort final result
        result.sort(Comparator
                .comparing(WorkTimeTable::getWorkDate)
                .thenComparing(WorkTimeTable::getUserId));

        LoggerUtil.info(this.getClass(), String.format(
                "Processed entries for user %s - Admin entries: %d (Edited: %d, Blank: %d), User entries: %d",
                user.getUsername(),
                adminEntries.size(),
                adminEditDates.size(),
                adminBlankDates.size(),
                processedUserEntries.size()
        ));

        return result;
    }

    private void saveConsolidatedEntries(List<WorkTimeTable> newEntries, int year, int month) {
        // First filter out any USER_IN_PROCESS entries before saving
        List<WorkTimeTable> filteredEntries = newEntries.stream()
                .filter(entry -> !SyncStatus.USER_IN_PROCESS.equals(entry.getAdminSync()))
                .toList();

        // Load existing entries
        List<WorkTimeTable> existingEntries = dataAccess.readFile(
                dataAccess.getAdminWorktimePath(year, month),
                WORKTIME_LIST_TYPE,
                true
        );

        // Preserve existing ADMIN_EDITED entries that aren't in new entries
        List<WorkTimeTable> adminEntries = existingEntries.stream()
                .filter(entry -> SyncStatus.ADMIN_EDITED.equals(entry.getAdminSync()))
                .filter(existing -> newEntries.stream()
                        .noneMatch(newEntry ->
                                newEntry.getUserId().equals(existing.getUserId()) &&
                                        newEntry.getWorkDate().equals(existing.getWorkDate())))
                .toList();

        // Combine preserved admin entries with new entries
        List<WorkTimeTable> finalEntries = new ArrayList<>(adminEntries);
        finalEntries.addAll(newEntries);

        // Sort entries
        finalEntries.sort(Comparator
                .comparing(WorkTimeTable::getWorkDate)
                .thenComparing(WorkTimeTable::getUserId));

        // Save entries
        dataAccess.writeFile(
                dataAccess.getAdminWorktimePath(year, month),
                finalEntries
        );

        LoggerUtil.info(this.getClass(),
                String.format("Saved %d consolidated entries for %d/%d",
                        finalEntries.size(), month, year));
    }

    /**
     * Get viewable entries (ADMIN_EDITED or USER_DONE only)
     */
    public List<WorkTimeTable> getViewableEntries(int year, int month) {
        List<WorkTimeTable> allEntries = loadGeneralWorktime(year, month);

        // Modified to include ADMIN_BLANK entries
        return allEntries.stream()
                .filter(entry ->
                        SyncStatus.ADMIN_EDITED.equals(entry.getAdminSync()) ||
                                SyncStatus.USER_DONE.equals(entry.getAdminSync()) ||
                                SyncStatus.ADMIN_BLANK.equals(entry.getAdminSync()))
                .map(entry -> {
                    // If entry is ADMIN_BLANK, return a cleared entry
                    if (SyncStatus.ADMIN_BLANK.equals(entry.getAdminSync())) {
                        WorkTimeTable blankEntry = new WorkTimeTable();
                        blankEntry.setUserId(entry.getUserId());
                        blankEntry.setWorkDate(entry.getWorkDate());
                        blankEntry.setAdminSync(SyncStatus.ADMIN_BLANK);
                        return blankEntry;
                    }
                    return entry;
                })
                .collect(Collectors.toList());
    }
}