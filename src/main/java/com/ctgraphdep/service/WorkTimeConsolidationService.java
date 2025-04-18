package com.ctgraphdep.service;

import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.enums.WorktimeMergeRule;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class WorkTimeConsolidationService {

    private final DataAccessService dataAccessService;
    private final UserService userService;
    private final ReentrantLock consolidationLock = new ReentrantLock();

    public WorkTimeConsolidationService(DataAccessService dataAccessService, UserService userService) {
        this.dataAccessService = dataAccessService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void consolidateWorkTimeEntries(int year, int month) {
        consolidationLock.lock();
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting worktime consolidation for %d/%d", month, year));

            // Get all non-admin users
            List<User> users = userService.getAllUsers().stream().filter(user -> !user.isAdmin()).toList();

            LoggerUtil.info(this.getClass(), String.format("Found %d non-admin users to process", users.size()));

            // Load existing admin entries
            List<WorkTimeTable> adminEntries = loadAdminWorktime(year, month);

            LoggerUtil.info(this.getClass(), String.format("Loaded %d existing admin entries", adminEntries != null ? adminEntries.size() : 0));

            // Create a map of admin entries by user and date for efficient lookup
            assert adminEntries != null;
            Map<String, WorkTimeTable> adminEntriesMap = createAdminEntriesMap(adminEntries);

            // Process each user's entries
            List<WorkTimeTable> consolidatedEntries = new ArrayList<>();
            for (User user : users) {
                try {
                    List<WorkTimeTable> userProcessedEntries = processUserEntries(user, year, month, adminEntriesMap);
                    consolidatedEntries.addAll(userProcessedEntries);

                    LoggerUtil.info(this.getClass(), String.format("Processed %d entries for user %s", userProcessedEntries.size(), user.getUsername()));
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), String.format("Error processing user %s: %s", user.getUsername(), e.getMessage()));
                    // Continue with next user
                }
            }

            // Sort and save consolidated entries
            if (!consolidatedEntries.isEmpty()) {
                saveConsolidatedEntries(consolidatedEntries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Saved %d consolidated entries", consolidatedEntries.size()));
            } else {
                LoggerUtil.info(this.getClass(), "No entries to save after consolidation");
            }

        } finally {
            consolidationLock.unlock();
        }
    }

    private List<WorkTimeTable> loadAdminWorktime(int year, int month) {
        try {
            return dataAccessService.readLocalAdminWorktime(year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading admin worktime for %d/%d: %s", year, month, e.getMessage()));
            return new ArrayList<>();
        }
    }

    private Map<String, WorkTimeTable> createAdminEntriesMap(List<WorkTimeTable> adminEntries) {
        return adminEntries.stream()
                .collect(Collectors.toMap(
                        entry -> createEntryKey(entry.getUserId(), entry.getWorkDate()),
                        entry -> entry,
                        (existing, replacement) -> replacement
                ));
    }

    private List<WorkTimeTable> processUserEntries(User user, int year, int month, Map<String, WorkTimeTable> adminEntriesMap) {
        try {
            // Load user entries from network with null safety
            List<WorkTimeTable> userEntries = dataAccessService.readNetworkUserWorktimeReadOnly(user.getUsername(), year, month);

            if (userEntries == null) {
                userEntries = new ArrayList<>();
            }

            List<WorkTimeTable> processedEntries = new ArrayList<>();
            Map<String, WorkTimeTable> processedEntriesMap = new HashMap<>();

            // First, check for resolved entries that should replace unresolved admin entries
            for (WorkTimeTable userEntry : userEntries) {
                if (SyncStatusWorktime.USER_INPUT.equals(userEntry.getAdminSync())) {
                    // This is a resolved entry from the user
                    String entryKey = createEntryKey(userEntry.getUserId(), userEntry.getWorkDate());

                    // Check if admin has an unresolved entry for this date
                    WorkTimeTable adminEntry = adminEntriesMap != null ? adminEntriesMap.get(entryKey) : null;

                    if (adminEntry != null && SyncStatusWorktime.USER_IN_PROCESS.equals(adminEntry.getAdminSync())) {
                        LoggerUtil.info(this.getClass(),
                                String.format("Admin consolidation: Updating admin USER_IN_PROCESS entry with user's resolved entry for %s",
                                        userEntry.getWorkDate()));

                        // Use the resolved user entry instead of the unresolved admin entry
                        processedEntries.add(userEntry);
                        processedEntriesMap.put(entryKey, userEntry);
                    }
                }
            }

            // Next, process remaining admin entries (except those we just replaced)
            if (adminEntriesMap != null) {
                for (Map.Entry<String, WorkTimeTable> entry : adminEntriesMap.entrySet()) {
                    String entryKey = entry.getKey();
                    WorkTimeTable adminEntry = entry.getValue();

                    // Skip if we already processed this entry
                    if (processedEntriesMap.containsKey(entryKey)) {
                        continue;
                    }

                    // Only include admin entry if it's for this user
                    if (adminEntry.getUserId().equals(user.getUserId())) {
                        if (SyncStatusWorktime.ADMIN_BLANK.equals(adminEntry.getAdminSync())) {
                            // Keep ADMIN_BLANK entries to ensure they're properly handled
                            processedEntries.add(adminEntry);
                            processedEntriesMap.put(entryKey, adminEntry);
                        }
                        else if (!SyncStatusWorktime.USER_IN_PROCESS.equals(adminEntry.getAdminSync())) {
                            // Include all admin entries EXCEPT USER_IN_PROCESS
                            processedEntries.add(adminEntry);
                            processedEntriesMap.put(entryKey, adminEntry);
                        }
                    }
                }
            }

            // Finally, process remaining user entries
            for (WorkTimeTable userEntry : userEntries) {
                String entryKey = createEntryKey(userEntry.getUserId(), userEntry.getWorkDate());

                // Skip entries we already processed
                if (processedEntriesMap.containsKey(entryKey)) {
                    continue;
                }

                // Skip USER_IN_PROCESS entries - they should remain in user file only
                if (SyncStatusWorktime.USER_IN_PROCESS.equals(userEntry.getAdminSync())) {
                    LoggerUtil.debug(this.getClass(),
                            String.format("Admin consolidation: Skipping USER_IN_PROCESS entry for date %s",
                                    userEntry.getWorkDate()));
                    continue;
                }

                // For all other user entries, apply merge rules and add to result
                WorkTimeTable mergedEntry = WorktimeMergeRule.apply(userEntry, null);
                if (mergedEntry != null) {
                    processedEntries.add(mergedEntry);
                    processedEntriesMap.put(entryKey, mergedEntry);
                }
            }

            return processedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error processing entries for user %s: %s", user.getUsername(), e.getMessage()));
            return new ArrayList<>();  // Return empty list instead of throwing
        }
    }

    private void saveConsolidatedEntries(List<WorkTimeTable> entries, int year, int month) {
        try {
            // Sort entries
            entries.sort(Comparator
                    .comparing(WorkTimeTable::getWorkDate)
                    .thenComparing(WorkTimeTable::getUserId));

            // Save to admin worktime - this will handle local save and network sync
            dataAccessService.writeAdminWorktime(entries, year, month);

            LoggerUtil.info(this.getClass(), String.format("Saved %d consolidated entries for %d/%d", entries.size(), month, year));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving consolidated entries for %d/%d: %s", month, year, e.getMessage()));
            throw new RuntimeException("Failed to save consolidated entries", e);
        }
    }

    private String createEntryKey(Integer userId, LocalDate date) {
        return userId + "_" + date.toString();
    }

    public List<WorkTimeTable> getViewableEntries(int year, int month) {
        List<WorkTimeTable> allEntries = loadAdminWorktime(year, month);

        // Filter out USER_IN_PROCESS entries
        return allEntries.stream()
                .filter(entry -> !SyncStatusWorktime.USER_IN_PROCESS.equals(entry.getAdminSync()))
                .collect(Collectors.toList());
    }
}