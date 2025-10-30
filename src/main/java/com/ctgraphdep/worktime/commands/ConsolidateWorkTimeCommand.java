package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.worktime.service.WorktimeMergeService;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.accessor.WorktimeDataAccessor;
import com.ctgraphdep.worktime.accessor.NetworkOnlyAccessor;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.worktime.util.StatusCleanupUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

public class ConsolidateWorkTimeCommand extends WorktimeOperationCommand<Map<String, Object>> {
    private final WorktimeMergeService worktimeMergeService;
    private final int year;
    private final int month;

    private ConsolidateWorkTimeCommand(WorktimeOperationContext context, WorktimeMergeService worktimeMergeService, int year, int month) {
        super(context);
        this.worktimeMergeService = worktimeMergeService;
        this.year = year;
        this.month = month;
    }

    // FACTORY METHOD: Create command for worktime consolidation
    public static ConsolidateWorkTimeCommand forPeriod(WorktimeOperationContext context, WorktimeMergeService worktimeMergeService, int year, int month) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year for consolidation: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month for consolidation: " + month);
        }

        return new ConsolidateWorkTimeCommand(context, worktimeMergeService, year, month);
    }

    @Override
    protected void validate() {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        LoggerUtil.info(this.getClass(), String.format("Validating admin consolidation with Universal Merge for %d/%d", month, year));

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
        LoggerUtil.info(this.getClass(), String.format("Starting admin worktime consolidation with Universal Merge for %d/%d", month, year));

        try {
            // Use AdminOwnDataAccessor for admin operations
            WorktimeDataAccessor adminAccessor = context.getDataAccessor("admin");

            // STEP 1: Load current admin general file for optimization check
            List<WorkTimeTable> currentAdminGeneral = adminAccessor.readWorktime("admin", year, month);
            if (currentAdminGeneral == null) {
                currentAdminGeneral = new ArrayList<>();
            }

            // STEP 2: Get all non-admin users to process
            List<User> nonAdminUsers = context.getNonAdminUsers();
            if (nonAdminUsers.isEmpty()) {
                LoggerUtil.info(this.getClass(), "No non-admin users found - nothing to consolidate");
                return OperationResult.success("No non-admin users found - nothing to consolidate", getOperationType());
            }

            LoggerUtil.info(this.getClass(), String.format("Processing Universal Merge consolidation for %d non-admin users", nonAdminUsers.size()));

            // STEP 3: Calculate expected consolidation result using Universal Merge
            ConsolidationResult consolidationResult = calculateUniversalMergeConsolidationResult(nonAdminUsers, currentAdminGeneral);

            // STEP 3.5: POST-CONSOLIDATION ZS VALIDATION - Apply ZS logic to all consolidated entries
            boolean zsUpdated = validateAndApplyShortDayLogicForAllUsers(consolidationResult.consolidatedEntries, nonAdminUsers);
            if (zsUpdated) {
                LoggerUtil.info(this.getClass(), "POST-CONSOLIDATION: ZS validation updated entries");
            }

            // STEP 4: OPTIMIZATION - Check if consolidation is needed
            if (isConsolidationUpToDate(currentAdminGeneral, consolidationResult.consolidatedEntries)) {
                LoggerUtil.info(this.getClass(), String.format("Admin general file already up-to-date for %d/%d - skipping consolidation", month, year));
                return OperationResult.success(String.format("Admin worktime already consolidated for %d/%d", month, year), getOperationType());
            }

            // STEP 5: Perform actual consolidation (data has changed)
            LoggerUtil.info(this.getClass(), String.format("Universal Merge consolidation needed: %d users, %d total entries, %d merge operations",
                    nonAdminUsers.size(), consolidationResult.consolidatedEntries.size(), consolidationResult.totalMergeOperations));

            // Save consolidated result to admin general file using AdminOwnDataAccessor
            adminAccessor.writeWorktimeWithStatus("admin", consolidationResult.consolidatedEntries, year, month, context.getCurrentUser().getRole());

            // Create comprehensive result data
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("usersProcessed", nonAdminUsers.size());
            resultData.put("totalConsolidatedEntries", consolidationResult.consolidatedEntries.size());
            resultData.put("totalMergeOperations", consolidationResult.totalMergeOperations);
            resultData.put("universalMergeStatistics", consolidationResult.mergeStatistics);
            resultData.put("processedUsernames", nonAdminUsers.stream().map(User::getUsername).collect(Collectors.toList()));

            // Create side effects tracking
            OperationResult.OperationSideEffects sideEffects = OperationResult.OperationSideEffects.builder()
                    .fileUpdated(String.format("admin-general/%d/%d", year, month))
                    .build();

            String successMessage = String.format("Universal Merge consolidation completed for %d/%d: %d users processed, %d entries consolidated, %d merge operations",
                    month, year, nonAdminUsers.size(), consolidationResult.consolidatedEntries.size(), consolidationResult.totalMergeOperations);

            LoggerUtil.info(this.getClass(), successMessage);

            return OperationResult.successWithSideEffects(successMessage, getOperationType(), resultData, sideEffects);

        } catch (Exception e) {
            String errorMessage = String.format("Universal Merge consolidation failed for %d/%d: %s", month, year, e.getMessage());
            LoggerUtil.error(this.getClass(), errorMessage, e);
            return OperationResult.failure(errorMessage, getOperationType());
        }
    }

    // Calculate consolidation result using Universal Merge Engine with accessor pattern - ORIGINAL LOGIC
    private ConsolidationResult calculateUniversalMergeConsolidationResult(List<User> users, List<WorkTimeTable> adminLocalEntries) {
        List<WorkTimeTable> consolidatedEntries = new ArrayList<>();
        int totalMergeOperations = 0;
        Map<String, Integer> mergeStatistics = new HashMap<>();

        // Initialize statistics - ORIGINAL LOGIC
        mergeStatistics.put("usersProcessed", 0);
        mergeStatistics.put("userEntriesProcessed", 0);
        mergeStatistics.put("adminEntriesProcessed", 0);
        mergeStatistics.put("successfulMerges", 0);
        mergeStatistics.put("skippedInProcessEntries", 0);

        boolean adminCleanupNeeded = StatusCleanupUtil.cleanupStatuses(
                adminLocalEntries, String.format("admin file: %d/%d (consolidation)", year, month));

        if (adminCleanupNeeded) {
            mergeStatistics.put("adminFileCleanupNeeded", 1);
        }

        Map<String, WorkTimeTable> adminEntriesMap = createEntriesMap(adminLocalEntries);

        LoggerUtil.info(this.getClass(), String.format("Universal Merge consolidation: loaded %d admin local entries as base, cleanup needed: %s",
                adminLocalEntries.size(), adminCleanupNeeded));
        mergeStatistics.put("adminEntriesProcessed", adminLocalEntries.size());

        // Create NetworkOnlyAccessor for reading user network data
        NetworkOnlyAccessor networkAccessor = new NetworkOnlyAccessor(
                context.getWorktimeDataService(),
                context.getRegisterDataService(),
                context.getCheckRegisterDataService(),
                context.getTimeOffDataService()
        );

        // Process each user's network data with Universal Merge - ORIGINAL LOGIC
        for (User user : users) {
            try {
                UserConsolidationResult userResult = processUserForUniversalMergeConsolidation(user, adminEntriesMap, networkAccessor);

                consolidatedEntries.addAll(userResult.entries);
                totalMergeOperations += userResult.mergeOperations;

                // Update statistics
                mergeStatistics.put("usersProcessed", mergeStatistics.get("usersProcessed") + 1);
                mergeStatistics.put("userEntriesProcessed", mergeStatistics.get("userEntriesProcessed") + userResult.userEntriesCount);
                mergeStatistics.put("successfulMerges", mergeStatistics.get("successfulMerges") + userResult.mergeOperations);
                mergeStatistics.put("skippedInProcessEntries", mergeStatistics.get("skippedInProcessEntries") + userResult.skippedInProcessEntries);

                LoggerUtil.debug(this.getClass(), String.format("Universal Merge processed %d entries for user %s (%d merge operations)",
                        userResult.entries.size(), user.getUsername(), userResult.mergeOperations));

            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), String.format("Error processing user %s for Universal Merge consolidation: %s",
                        user.getUsername(), e.getMessage()), e);
                // Continue with other users - don't fail entire consolidation
            }
        }

        // Sort consolidated entries for consistency - ORIGINAL LOGIC
        consolidatedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));

        LoggerUtil.info(this.getClass(), String.format("Universal Merge consolidation calculated: %d total entries, %d merge operations, %d users processed",
                consolidatedEntries.size(), totalMergeOperations, mergeStatistics.get("usersProcessed")));

        return new ConsolidationResult(consolidatedEntries, totalMergeOperations, mergeStatistics);
    }

    // Process individual user using Universal Merge Engine with NetworkOnlyAccessor - ORIGINAL LOGIC
    private UserConsolidationResult processUserForUniversalMergeConsolidation(User user, Map<String, WorkTimeTable> adminEntriesMap, NetworkOnlyAccessor networkAccessor) {
        String username = user.getUsername();
        Integer userId = user.getUserId();

        LoggerUtil.debug(this.getClass(), String.format("Processing Universal Merge consolidation for user %s (ID: %d)", username, userId));

        try {
            // Load user NETWORK entries using NetworkOnlyAccessor
            List<WorkTimeTable> userNetworkEntries = networkAccessor.readWorktime(username, year, month);
            if (userNetworkEntries == null) {
                userNetworkEntries = new ArrayList<>();
            }

            boolean userCleanupNeeded = StatusCleanupUtil.cleanupStatuses(
                    userNetworkEntries, String.format("user file: %s-%d/%d (consolidation-readonly)", username, year, month));

            // Filter admin entries for this specific user - ORIGINAL LOGIC
            List<WorkTimeTable> userAdminEntries = adminEntriesMap.values().stream()
                    .filter(entry -> userId.equals(entry.getUserId()))
                    .collect(Collectors.toList());

            LoggerUtil.debug(this.getClass(), String.format("User %s Universal Merge input: %d network entries, %d admin entries",
                    username, userNetworkEntries.size(), userAdminEntries.size()));

            // Count USER_IN_PROCESS entries that will be skipped - ORIGINAL LOGIC
            int skippedInProcessEntries = (int) userNetworkEntries.stream()
                    .filter(entry -> MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync()))
                    .count();

            // Perform Universal Merge - ORIGINAL LOGIC
            List<WorkTimeTable> mergedEntries = worktimeMergeService.mergeEntries(userNetworkEntries, userAdminEntries, userId);

            int mergeOperations = mergedEntries.size();

            LoggerUtil.debug(this.getClass(), String.format("User %s Universal Merge result: %d merged entries, %d operations, %d skipped in-process",
                    username, mergedEntries.size(), mergeOperations, skippedInProcessEntries));

            return new UserConsolidationResult(mergedEntries, mergeOperations, userNetworkEntries.size(), skippedInProcessEntries, userCleanupNeeded);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in Universal Merge for user %s: %s", username, e.getMessage()), e);
            return new UserConsolidationResult(new ArrayList<>(), 0, 0, 0, false);
        }
    }

    // Check if consolidation is up to date - ORIGINAL LOGIC
    private boolean isConsolidationUpToDate(List<WorkTimeTable> currentAdminGeneral, List<WorkTimeTable> expectedConsolidation) {
        if (currentAdminGeneral.size() != expectedConsolidation.size()) {
            return false;
        }

        // Sort both lists for comparison
        currentAdminGeneral.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));
        expectedConsolidation.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparingInt(WorkTimeTable::getUserId));

        boolean isEqual = compareWorkTimeEntriesWithUniversalStatus(currentAdminGeneral, expectedConsolidation);
        LoggerUtil.debug(this.getClass(), String.format("Consolidation up-to-date check: %s", isEqual ? "YES" : "NO"));
        return isEqual;
    }

    // Compare work time entries with Universal Status - ORIGINAL LOGIC
    private boolean compareWorkTimeEntriesWithUniversalStatus(List<WorkTimeTable> list1, List<WorkTimeTable> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        for (int i = 0; i < list1.size(); i++) {
            WorkTimeTable entry1 = list1.get(i);
            WorkTimeTable entry2 = list2.get(i);

            // Compare key fields including Universal Status - ORIGINAL LOGIC
            if (!entry1.getUserId().equals(entry2.getUserId()) ||
                    !entry1.getWorkDate().equals(entry2.getWorkDate()) ||
                    !Objects.equals(entry1.getTimeOffType(), entry2.getTimeOffType()) ||
                    !Objects.equals(entry1.getTotalWorkedMinutes(), entry2.getTotalWorkedMinutes()) ||
                    !entry1.getAdminSync().equals(entry2.getAdminSync())) {

                LoggerUtil.debug(this.getClass(), String.format("Entry difference detected for %s: status1=%s, status2=%s",
                        entry1.getWorkDate(), entry1.getAdminSync(), entry2.getAdminSync()));
                return false;
            }
        }

        return true;
    }

    // Create a map of entries by user-date key for efficient lookup - ORIGINAL LOGIC
    private Map<String, WorkTimeTable> createEntriesMap(List<WorkTimeTable> entries) {
        if (entries == null) {
            return new HashMap<>();
        }

        return entries.stream().collect(Collectors.toMap(entry -> createEntryKey(entry.getUserId(), entry.getWorkDate()),
                entry -> entry, (existing, replacement) -> replacement));
    }

    // Create unique key for entry lookup - ORIGINAL LOGIC
    private String createEntryKey(Integer userId, LocalDate date) {
        return userId + "_" + date.toString();
    }

    // ========================================================================
    // POST-CONSOLIDATION ZS (SHORT DAY) VALIDATION
    // ========================================================================

    /**
     * Validate and apply ZS (Short Day) logic to ALL users' consolidated worktime entries.
     * This ensures that after consolidation, ZS markers are correctly applied based on actual work vs schedule.
     *
     * Similar to login merge validation, but processes ALL users at once.
     *
     * Logic for each entry:
     * 1. Skip if entry has no start/end time (incomplete day, in-process)
     * 2. Get user schedule for that user
     * 3. Calculate worked minutes vs schedule
     * 4. If complete AND has ZS → Remove ZS
     * 5. If incomplete AND has no other time-off → Create/Update ZS
     * 6. If has other time-off (CO, CM, SN, etc) → Don't touch it
     *
     * @param consolidatedEntries List of all consolidated worktime entries (all users)
     * @param users List of users being processed
     * @return true if any ZS was added/updated/removed, false otherwise
     */
    private boolean validateAndApplyShortDayLogicForAllUsers(List<WorkTimeTable> consolidatedEntries, List<User> users) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Starting post-consolidation ZS validation for %d users (%d entries)", users.size(), consolidatedEntries.size()));

            // Create a map of userId -> User schedule for quick lookup
            Map<Integer, Integer> userScheduleMap = new HashMap<>();
            for (User user : users) {
                userScheduleMap.put(user.getUserId(), user.getSchedule());
            }

            boolean anyUpdated = false;

            // Process each entry
            for (WorkTimeTable entry : consolidatedEntries) {
                // Skip entries without both start and end time (in-process or incomplete)
                if (entry.getDayStartTime() == null || entry.getDayEndTime() == null) {
                    continue;
                }

                // Get user schedule
                Integer userScheduleHours = userScheduleMap.get(entry.getUserId());
                if (userScheduleHours == null) {
                    LoggerUtil.warn(this.getClass(), String.format("Could not find schedule for userId %d during ZS validation", entry.getUserId()));
                    continue;
                }

                int scheduleMinutes = userScheduleHours * 60;
                String originalTimeOffType = entry.getTimeOffType();
                int workedMinutes = entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0;
                boolean isDayComplete = workedMinutes >= scheduleMinutes;
                boolean hasZS = originalTimeOffType != null && originalTimeOffType.startsWith("ZS-");

                if (isDayComplete) {
                    // Day is complete - remove ZS if it exists
                    if (hasZS) {
                        LoggerUtil.info(this.getClass(), String.format(
                                "POST-CONSOLIDATION ZS: Day complete for userId %d on %s (worked: %d min, schedule: %d min). Removing %s",
                                entry.getUserId(), entry.getWorkDate(), workedMinutes, scheduleMinutes, originalTimeOffType));
                        entry.setTimeOffType(null);
                        anyUpdated = true;
                    }
                } else {
                    // Day is incomplete - create/update ZS if no other time-off type
                    boolean hasOtherTimeOff = originalTimeOffType != null && !originalTimeOffType.trim().isEmpty() && !hasZS;

                    if (!hasOtherTimeOff) {
                        // Calculate missing hours
                        int missingMinutes = scheduleMinutes - workedMinutes;
                        int missingHours = (int) Math.ceil(missingMinutes / 60.0);
                        String newZS = "ZS-" + missingHours;

                        if (!newZS.equals(originalTimeOffType)) {
                            LoggerUtil.info(this.getClass(), String.format(
                                    "POST-CONSOLIDATION ZS: Day incomplete for userId %d on %s (worked: %d min, schedule: %d min). Updating ZS: %s → %s",
                                    entry.getUserId(), entry.getWorkDate(), workedMinutes, scheduleMinutes,
                                    originalTimeOffType != null ? originalTimeOffType : "none", newZS));
                            entry.setTimeOffType(newZS);
                            anyUpdated = true;
                        }
                    }
                }
            }

            if (anyUpdated) {
                LoggerUtil.info(this.getClass(), "POST-CONSOLIDATION ZS validation completed: entries were updated");
            } else {
                LoggerUtil.debug(this.getClass(), "POST-CONSOLIDATION ZS validation completed: no updates needed");
            }

            return anyUpdated;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error during post-consolidation ZS validation: %s", e.getMessage()), e);
            // Don't throw - ZS validation failure shouldn't break consolidation
            return false;
        }
    }

    @Override
    protected String getCommandName() {
        return String.format("UniversalMergeConsolidateWorkTime[%d/%d]", month, year);
    }

    @Override
    protected String getOperationType() {
        return OperationResult.OperationType.CONSOLIDATE_WORKTIME;
    }

    // ========================================================================
    // HELPER CLASSES FOR TRACKING CONSOLIDATION RESULTS - ORIGINAL LOGIC
    // ========================================================================

    // Result of overall consolidation operation
    private record ConsolidationResult(List<WorkTimeTable> consolidatedEntries, int totalMergeOperations, Map<String, Integer> mergeStatistics) {
    }

    // Result of processing single user for consolidation
    private record UserConsolidationResult(List<WorkTimeTable> entries, int mergeOperations, int userEntriesCount,
                                           int skippedInProcessEntries, boolean hadOldStatuses) {
    }
}