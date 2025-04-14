package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.enums.WorktimeMergeRule;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkTimeEntrySyncService {
    private final DataAccessService dataAccessService;
    private final UserService userService;
    private final TimeOffTrackerService timeOffTrackerService;

    public WorkTimeEntrySyncService(DataAccessService dataAccessService, UserService userService, TimeOffTrackerService timeOffTrackerService) {
        this.dataAccessService = dataAccessService;
        this.userService = userService;
        this.timeOffTrackerService = timeOffTrackerService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public List<WorkTimeTable> synchronizeEntries(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting entry synchronization for user %s (%d) - %d/%d", username, userId, month, year));

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

            try {
                Optional<User> userOpt = userService.getUserByUsername(username);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    // Call the timeOffTrackerService to sync the tracker with the merged entries
                    timeOffTrackerService.syncWithWorktimeFiles(user, year, mergedEntries);
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(),
                        String.format("Failed to sync time off tracker during worktime sync: %s", e.getMessage()));
            }

            return mergedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error synchronizing entries for user %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to synchronize worktime entries", e);
        }
    }

    private Map<LocalDate, WorkTimeTable> createEntriesMap(List<WorkTimeTable> entries) {
        return entries.stream()
                .collect(Collectors.toMap(WorkTimeTable::getWorkDate, entry -> entry, (e1, e2) -> e2  // Keep the latest entry in case of duplicates
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

            // Add specific handling for USER_INPUT vs USER_IN_PROCESS conflict
            if (userEntry != null && adminEntry != null &&
                    SyncStatusWorktime.USER_INPUT.equals(userEntry.getAdminSync()) &&
                    SyncStatusWorktime.USER_IN_PROCESS.equals(adminEntry.getAdminSync())) {

                LoggerUtil.warn(this.getClass(),
                        String.format("Conflict detected: User has resolved entry (USER_INPUT) for %s, " +
                                "but admin has unresolved entry (USER_IN_PROCESS). " +
                                "Keeping user's resolved entry.", date));

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
                        LoggerUtil.debug(this.getClass(),
                                String.format("Preserved USER_IN_PROCESS entry for date %s", date));
                    } else {
                        LoggerUtil.debug(this.getClass(),
                                String.format("Processed entry for date %s, final status: %s",
                                        date, mergedEntry.getAdminSync()));
                    }
                } else if (isAdminBlank) {
                    LoggerUtil.info(this.getClass(),
                            String.format("Entry for date %s removed due to ADMIN_BLANK", date));
                }
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        String.format("Error merging entries for date %s: %s", date, e.getMessage()));
            }
        }

        mergedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate));
        return mergedEntries;
    }

    private List<WorkTimeTable> loadUserEntries(String username, int year, int month) {
        try {
            List<WorkTimeTable> entries = dataAccessService.readUserWorktime(username, year, month);

            if (entries == null) {
                entries = new ArrayList<>();
            }

            LoggerUtil.info(this.getClass(), String.format("Loaded %d entries from user worktime file for %s", entries.size(), username));
            return entries;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading user entries for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private List<WorkTimeTable> loadAdminEntries(Integer userId, int year, int month) {
        try {
            List<WorkTimeTable> allEntries = dataAccessService.readNetworkAdminWorktime(year, month);
            // allEntries will never be null now due to changes above

            List<WorkTimeTable> userEntries = allEntries.stream().filter(entry -> entry.getUserId() != null &&
                            entry.getUserId().equals(userId)).collect(Collectors.toList());

            LoggerUtil.debug(this.getClass(), String.format("Loaded %d admin entries for user %d from %d/%d", userEntries.size(), userId, year, month));

            return userEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading admin entries for user %d: %s", userId, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private void saveUserEntries(String username, List<WorkTimeTable> entries, int year, int month) {
        try {
            // Use writeUserWorktime which handles local save and network sync
            dataAccessService.writeUserWorktime(username, entries, year, month);

            LoggerUtil.info(this.getClass(), String.format("Saved %d merged entries to user worktime file", entries.size()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving user entries for %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to save worktime entries", e);
        }
    }
}