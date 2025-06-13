package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.service.WorktimeMergeService;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FIXED Command to consolidate worktime entries for admin view.
 * CORRECTED FLOW: network user + local admin → admin general
 * Key Fixes:
 * - Changed data sources: user NETWORK files (not local)
 * - Changed target: admin GENERAL file (not individual user files)
 * - Added optimization: equality check before consolidation
 * - Proper merge direction: user→admin consolidation (not admin→user)
 */
public class ConsolidateWorkTimeCommand extends WorktimeOperationCommand<Map<String, Object>> {
    private final WorktimeMergeService worktimeMergeService;
    private final int year;
    private final int month;

    public ConsolidateWorkTimeCommand(WorktimeOperationContext context, WorktimeMergeService worktimeMergeService, int year, int month) {
        super(context);
        this.worktimeMergeService = worktimeMergeService;
        this.year = year;
        this.month = month;
    }

    @Override
    protected void validate() {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Validating admin consolidation for %d/%d", month, year));

        // Validate admin permissions
        context.requireAdminPrivileges("consolidate worktime");

        // Validate month exists (not future month)
        YearMonth targetMonth = YearMonth.of(year, month);
        YearMonth currentMonth = YearMonth.now();
        if (targetMonth.isAfter(currentMonth)) {
            throw new IllegalArgumentException("Cannot consolidate future months");
        }

        LoggerUtil.debug(this.getClass(), "Admin consolidation validation completed successfully");
    }

    @Override
    protected OperationResult executeCommand() {
        LoggerUtil.info(this.getClass(), String.format(
                "Starting admin worktime consolidation for %d/%d", month, year));

        try {
            // STEP 1: Load current admin general file for optimization check
            List<WorkTimeTable> currentAdminGeneral = context.loadAdminWorktime(year, month);

            // STEP 2: Get all non-admin users to process
            List<com.ctgraphdep.model.User> nonAdminUsers = context.getNonAdminUsers();
            if (nonAdminUsers.isEmpty()) {
                LoggerUtil.info(this.getClass(), "No non-admin users found - nothing to consolidate");
                return OperationResult.success(
                        "No non-admin users found - nothing to consolidate",
                        getOperationType()
                );
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Processing consolidation for %d non-admin users", nonAdminUsers.size()));

            // STEP 3: Calculate expected consolidation result
            List<WorkTimeTable> expectedConsolidation = calculateConsolidationResult(nonAdminUsers);

            // STEP 4: OPTIMIZATION - Check if consolidation is needed
            if (isConsolidationUpToDate(currentAdminGeneral, expectedConsolidation)) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Admin general file already up-to-date for %d/%d - skipping consolidation", month, year));

                return OperationResult.success(
                        String.format("Admin worktime already consolidated for %d/%d", month, year),
                        getOperationType()
                );
            }

            // STEP 5: Perform actual consolidation (data has changed)
            LoggerUtil.info(this.getClass(), String.format(
                    "Data changed detected - performing consolidation: %d users, %d total entries",
                    nonAdminUsers.size(), expectedConsolidation.size()));

            // Save consolidated result to admin general file
            context.saveAdminWorktime(expectedConsolidation, year, month);

            // Create comprehensive result data
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("usersProcessed", nonAdminUsers.size());
            resultData.put("totalConsolidatedEntries", expectedConsolidation.size());
            resultData.put("processedUsernames", nonAdminUsers.stream()
                    .map(com.ctgraphdep.model.User::getUsername)
                    .collect(Collectors.toList()));

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(String.format("admin-general/%d/%d", year, month))
                    .build();

            String successMessage = String.format(
                    "Admin consolidation completed for %d/%d: %d users processed, %d entries consolidated",
                    month, year, nonAdminUsers.size(), expectedConsolidation.size());

            LoggerUtil.info(this.getClass(), successMessage);

            return OperationResult.successWithSideEffects(
                    successMessage,
                    getOperationType(),
                    resultData,
                    sideEffects
            );

        } catch (Exception e) {
            String errorMessage = String.format(
                    "Admin consolidation failed for %d/%d: %s", month, year, e.getMessage());
            LoggerUtil.error(this.getClass(), errorMessage, e);
            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    /**
     * FIXED: Calculate consolidation result using proper data sources
     * Flow: user NETWORK + admin LOCAL → consolidated result
     */
    private List<WorkTimeTable> calculateConsolidationResult(List<com.ctgraphdep.model.User> users) {
        List<WorkTimeTable> consolidatedEntries = new ArrayList<>();

        // Load current admin local entries (base for consolidation)
        List<WorkTimeTable> adminLocalEntries = context.loadAdminWorktime(year, month);
        Map<String, WorkTimeTable> adminEntriesMap = createEntriesMap(adminLocalEntries);

        LoggerUtil.debug(this.getClass(), String.format(
                "Loaded %d admin local entries as consolidation base", adminLocalEntries.size()));

        // Process each user's network data
        for (com.ctgraphdep.model.User user : users) {
            try {
                List<WorkTimeTable> userConsolidatedEntries = processUserForConsolidation(
                        user, adminEntriesMap);
                consolidatedEntries.addAll(userConsolidatedEntries);

                LoggerUtil.debug(this.getClass(), String.format(
                        "Processed %d entries for user %s", userConsolidatedEntries.size(), user.getUsername()));

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format(
                        "Error processing user %s for consolidation: %s", user.getUsername(), e.getMessage()), e);
                // Continue with other users - don't fail entire consolidation
            }
        }

        // Sort consolidated entries for consistency
        consolidatedEntries.sort((e1, e2) -> {
            int dateCompare = e1.getWorkDate().compareTo(e2.getWorkDate());
            if (dateCompare != 0) return dateCompare;
            return e1.getUserId().compareTo(e2.getUserId());
        });

        LoggerUtil.info(this.getClass(), String.format(
                "Calculated consolidation result: %d total entries", consolidatedEntries.size()));

        return consolidatedEntries;
    }

    /**
     * FIXED: Process individual user using NETWORK data (not local)
     * Flow: user NETWORK + admin entries for this user → user's consolidated entries
     */
    private List<WorkTimeTable> processUserForConsolidation(com.ctgraphdep.model.User user,
                                                            Map<String, WorkTimeTable> adminEntriesMap) {
        String username = user.getUsername();
        Integer userId = user.getUserId();

        LoggerUtil.debug(this.getClass(), String.format(
                "Processing consolidation for user %s (ID: %d)", username, userId));

        try {
            // FIXED: Load user NETWORK entries (original data, not merged)
            List<WorkTimeTable> userNetworkEntries = context.loadWorktimeFromNetwork(username, year, month);
            if (userNetworkEntries == null) {
                userNetworkEntries = new ArrayList<>();
            }

            // Filter admin entries for this specific user
            List<WorkTimeTable> userAdminEntries = adminEntriesMap.values().stream()
                    .filter(entry -> userId.equals(entry.getUserId()))
                    .collect(Collectors.toList());

            LoggerUtil.debug(this.getClass(), String.format(
                    "User %s: %d network entries, %d admin entries",
                    username, userNetworkEntries.size(), userAdminEntries.size()));

            // FIXED: Merge user network + admin entries (user→admin consolidation)
            List<WorkTimeTable> userConsolidatedEntries = worktimeMergeService.mergeEntries(
                    userNetworkEntries, userAdminEntries, userId);

            LoggerUtil.debug(this.getClass(), String.format(
                    "User %s consolidation result: %d entries", username, userConsolidatedEntries.size()));

            return userConsolidatedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error processing user %s network data: %s", username, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    /**
     * Optimization: Check if current admin general matches expected result
     */
    private boolean isConsolidationUpToDate(List<WorkTimeTable> currentAdminGeneral,
                                            List<WorkTimeTable> expectedConsolidation) {
        if (currentAdminGeneral == null) {
            currentAdminGeneral = new ArrayList<>();
        }

        // Quick size check first
        if (currentAdminGeneral.size() != expectedConsolidation.size()) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Size mismatch: current=%d, expected=%d",
                    currentAdminGeneral.size(), expectedConsolidation.size()));
            return false;
        }

        // Sort both lists for comparison
        List<WorkTimeTable> sortedCurrent = new ArrayList<>(currentAdminGeneral);
        List<WorkTimeTable> sortedExpected = new ArrayList<>(expectedConsolidation);

        sortedCurrent.sort((e1, e2) -> {
            int dateCompare = e1.getWorkDate().compareTo(e2.getWorkDate());
            if (dateCompare != 0) return dateCompare;
            return e1.getUserId().compareTo(e2.getUserId());
        });

        sortedExpected.sort((e1, e2) -> {
            int dateCompare = e1.getWorkDate().compareTo(e2.getWorkDate());
            if (dateCompare != 0) return dateCompare;
            return e1.getUserId().compareTo(e2.getUserId());
        });

        // Compare entries (simplified - could be enhanced with detailed comparison)
        boolean isEqual = compareWorkTimeEntries(sortedCurrent, sortedExpected);

        LoggerUtil.debug(this.getClass(), String.format(
                "Consolidation up-to-date check: %s", isEqual ? "YES" : "NO"));

        return isEqual;
    }

    /**
     * Compare two lists of WorkTimeTable entries for equality
     */
    private boolean compareWorkTimeEntries(List<WorkTimeTable> list1, List<WorkTimeTable> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        for (int i = 0; i < list1.size(); i++) {
            WorkTimeTable entry1 = list1.get(i);
            WorkTimeTable entry2 = list2.get(i);

            // Compare key fields (can be expanded as needed)
            if (!entry1.getUserId().equals(entry2.getUserId()) ||
                    !entry1.getWorkDate().equals(entry2.getWorkDate()) ||
                    !java.util.Objects.equals(entry1.getTimeOffType(), entry2.getTimeOffType()) ||
                    !java.util.Objects.equals(entry1.getTotalWorkedMinutes(), entry2.getTotalWorkedMinutes()) ||
                    !java.util.Objects.equals(entry1.getAdminSync(), entry2.getAdminSync())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Create a map of entries by user-date key for efficient lookup
     */
    private Map<String, WorkTimeTable> createEntriesMap(List<WorkTimeTable> entries) {
        if (entries == null) {
            return new HashMap<>();
        }

        return entries.stream()
                .collect(Collectors.toMap(
                        entry -> createEntryKey(entry.getUserId(), entry.getWorkDate()),
                        entry -> entry,
                        (existing, replacement) -> replacement // Keep latest in case of duplicates
                ));
    }

    /**
     * Create unique key for entry lookup
     */
    private String createEntryKey(Integer userId, java.time.LocalDate date) {
        return userId + "_" + date.toString();
    }

    @Override
    protected String getCommandName() {
        return String.format("AdminConsolidateWorkTime[%d/%d]", month, year);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.CONSOLIDATE_WORKTIME;
    }
}