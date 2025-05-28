package com.ctgraphdep.service;

import com.ctgraphdep.enums.RegisterMergeRule;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.cache.RegisterCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Refactored RegisterMergeService with optimized two-pass merge approach:
 * Pass 1: Status screening (fast filtering)
 * Pass 2: Batch content comparison (only for conflicts)
 * Handles:
 * - New entry propagation (user->admin, admin->user)
 * - Entry removal detection and propagation
 * - Admin superiority with CG precedence
 * - Efficient processing for large entry sets (300+ entries)
 */
@Service
public class RegisterMergeService {

    private final RegisterDataService registerDataService;
    private final RegisterCacheService registerCacheService;
    private final UserService userService;

    public RegisterMergeService(RegisterDataService registerDataService,
                                RegisterCacheService registerCacheService,
                                UserService userService) {
        this.registerDataService = registerDataService;
        this.registerCacheService = registerCacheService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // 1. USER LOGIN MERGE (Admin file -> User file)
    // ========================================================================

    /**
     * User login merge: Apply admin decisions to user file
     * Flow: Admin file changes -> User file (with cache invalidation)
     */
    public void performUserLoginMerge(String username) {
        try {
            LocalDate currentDate = LocalDate.now();
            int year = currentDate.getYear();
            int month = currentDate.getMonthValue();

            LoggerUtil.info(this.getClass(), String.format(
                    "Starting user login merge for %s - %d/%d", username, year, month));

            Integer userId = getUserIdFromUserService(username);
            if (userId == null) {
                LoggerUtil.warn(this.getClass(), String.format(
                        "User %s not found in UserService, skipping merge", username));
                return;
            }

            // Load files
            List<RegisterEntry> userEntries = registerDataService.readUserLocalReadOnly(
                    username, userId, username, year, month);
            List<RegisterEntry> adminEntries = registerDataService.readAdminByUserNetworkReadOnly(
                    username, userId, year, month);

            if (adminEntries == null || adminEntries.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No admin register found for %s - %d/%d, no merge needed", username, year, month));
                return;
            }

            // Perform optimized merge
            List<RegisterEntry> mergedEntries = performOptimizedUserLoginMerge(userEntries, adminEntries);

            // Save result to user file and invalidate cache
            registerDataService.writeUserLocalWithSyncAndBackup(username, userId, mergedEntries, year, month);
            invalidateUserCache(username, userId, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "User login merge completed for %s - %d/%d (%d user entries, %d admin entries, %d merged entries)",
                    username, year, month,
                    userEntries != null ? userEntries.size() : 0,
                    adminEntries.size(), mergedEntries.size()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in user login merge for %s: %s", username, e.getMessage()), e);
        }
    }

    public void performUserLoginMerge(String username, Integer userId, int year, int month) {
        try {


            LoggerUtil.info(this.getClass(), String.format("Starting user login merge for %s - %d/%d", username, year, month));

            if (userId == null) {
                LoggerUtil.warn(this.getClass(), String.format("User %s not found in UserService, skipping merge", username));
                return;
            }

            // Load files
            List<RegisterEntry> userEntries = registerDataService.readUserLocalReadOnly(username, userId, username, year, month);
            List<RegisterEntry> adminEntries = registerDataService.readAdminByUserNetworkReadOnly(username, userId, year, month);

            if (adminEntries == null || adminEntries.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No admin register found for %s - %d/%d, no merge needed", username, year, month));
                return;
            }

            // Perform optimized merge
            List<RegisterEntry> mergedEntries = performOptimizedUserLoginMerge(userEntries, adminEntries);

            // Save result to user file and invalidate cache
            registerDataService.writeUserLocalWithSyncAndBackup(username, userId, mergedEntries, year, month);
            invalidateUserCache(username, userId, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "User login merge completed for %s - %d/%d (%d user entries, %d admin entries, %d merged entries)",
                    username, year, month,
                    userEntries != null ? userEntries.size() : 0,
                    adminEntries.size(), mergedEntries.size()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error in user login merge for %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Optimized user login merge with two-pass approach
     */
    private List<RegisterEntry> performOptimizedUserLoginMerge(List<RegisterEntry> userEntries, List<RegisterEntry> adminEntries) {

        if (userEntries == null) userEntries = new ArrayList<>();

        // Create lookup maps
        Map<Integer, RegisterEntry> userMap = userEntries.stream()
                .collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry, (a, b) -> a));
        Map<Integer, RegisterEntry> adminMap = adminEntries.stream()
                .collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry, (a, b) -> a));

        // Get all unique entry IDs
        Set<Integer> allEntryIds = new HashSet<>();
        allEntryIds.addAll(userMap.keySet());
        allEntryIds.addAll(adminMap.keySet());

        List<RegisterEntry> mergedEntries = new ArrayList<>();

        // Pass 1: Process all entries using simplified rules
        for (Integer entryId : allEntryIds) {
            RegisterEntry userEntry = userMap.get(entryId);
            RegisterEntry adminEntry = adminMap.get(entryId);

            // Apply user login merge rules
            RegisterEntry mergedEntry = RegisterMergeRule.applyUserLoginMerge(adminEntry, userEntry);

            if (mergedEntry != null) {
                mergedEntries.add(mergedEntry);
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Entry %d removed during user login merge", entryId));
            }
        }

        return mergedEntries;
    }

    // ========================================================================
    // 2. ADMIN LOAD MERGE (User file -> Admin file)
    // ========================================================================

    /**
     * Admin load merge: Incorporate user changes into admin file
     * Flow: User file changes -> Admin file (with conflict detection)
     */
    public List<RegisterEntry> performAdminLoadMerge(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Starting admin load merge for %s - %d/%d", username, year, month));

            // Load files
            List<RegisterEntry> userEntries = registerDataService.readUserFromNetworkOnly(username, userId, year, month);
            List<RegisterEntry> adminEntries = registerDataService.readAdminLocalReadOnly(username, userId, year, month);

            if (userEntries == null) userEntries = new ArrayList<>();

            List<RegisterEntry> mergedEntries;

            // Bootstrap logic: If admin file doesn't exist, create from user file
            if (adminEntries == null || adminEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Bootstrapping admin register for %s from %d user entries", username, userEntries.size()));

                mergedEntries = userEntries.stream()
                        .map(this::bootstrapEntryForAdmin)
                        .collect(Collectors.toList());
            } else {
                // Perform optimized merge
                mergedEntries = performOptimizedAdminLoadMerge(userEntries, adminEntries);
            }

            // Save merged result to admin file
            registerDataService.writeAdminLocalWithSyncAndBackup(username, userId, mergedEntries, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "Admin load merge completed for %s - %d/%d (%d user entries, %d admin entries, %d merged entries)",
                    username, year, month, userEntries.size(),
                    adminEntries != null ? adminEntries.size() : 0, mergedEntries.size()));

            return mergedEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error in admin load merge for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    /**
     * Optimized admin load merge with two-pass approach
     */
    private List<RegisterEntry> performOptimizedAdminLoadMerge(List<RegisterEntry> userEntries,
                                                               List<RegisterEntry> adminEntries) {

        // Create lookup maps
        Map<Integer, RegisterEntry> userMap = userEntries.stream()
                .collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry, (a, b) -> a));
        Map<Integer, RegisterEntry> adminMap = adminEntries.stream()
                .collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry, (a, b) -> a));

        // Get all unique entry IDs
        Set<Integer> allEntryIds = new HashSet<>();
        allEntryIds.addAll(userMap.keySet());
        allEntryIds.addAll(adminMap.keySet());

        List<RegisterEntry> mergedEntries = new ArrayList<>();

        // Pass 1: Status screening and quick processing
        List<MergePair> conflictCandidates = new ArrayList<>();

        for (Integer entryId : allEntryIds) {
            RegisterEntry userEntry = userMap.get(entryId);
            RegisterEntry adminEntry = adminMap.get(entryId);

            // Check if this pair needs content comparison
            if (needsContentComparison(userEntry, adminEntry)) {
                conflictCandidates.add(new MergePair(entryId, userEntry, adminEntry));
            } else {
                // Quick resolution using merge rules
                RegisterEntry mergedEntry = RegisterMergeRule.applyAdminLoadMerge(userEntry, adminEntry);
                if (mergedEntry != null) {
                    mergedEntries.add(mergedEntry);
                }
            }
        }

        // Pass 2: Batch process conflict candidates with content comparison
        if (!conflictCandidates.isEmpty()) {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing %d conflict candidates with content comparison", conflictCandidates.size()));

            List<RegisterEntry> resolvedConflicts = batchProcessAdminLoadConflicts(conflictCandidates);
            mergedEntries.addAll(resolvedConflicts);
        }

        return mergedEntries;
    }

    /**
     * Determine if a pair of entries needs content comparison
     */
    private boolean needsContentComparison(RegisterEntry userEntry, RegisterEntry adminEntry) {
        if (userEntry == null || adminEntry == null) return false;

        String userStatus = userEntry.getAdminSync();
        String adminStatus = adminEntry.getAdminSync();

        // These combinations require content comparison
        return "ADMIN_EDITED".equals(adminStatus) || "USER_EDITED".equals(userStatus);
    }

    /**
     * Batch process conflict candidates with content comparison
     */
    private List<RegisterEntry> batchProcessAdminLoadConflicts(List<MergePair> conflictCandidates) {
        return conflictCandidates.stream()
                .map(pair -> RegisterMergeRule.applyAdminLoadMerge(pair.userEntry, pair.adminEntry))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Bootstrap a user entry for admin file
     */
    private RegisterEntry bootstrapEntryForAdmin(RegisterEntry userEntry) {
        String adminStatus = userEntry.getAdminSync();

        // If user entry has null or empty status, default to USER_INPUT
        if (adminStatus == null || adminStatus.trim().isEmpty()) {
            adminStatus = SyncStatusMerge.USER_INPUT.name();
        }

        return RegisterEntry.builder()
                .entryId(userEntry.getEntryId())
                .userId(userEntry.getUserId())
                .date(userEntry.getDate())
                .orderId(userEntry.getOrderId())
                .productionId(userEntry.getProductionId())
                .omsId(userEntry.getOmsId())
                .clientName(userEntry.getClientName())
                .actionType(userEntry.getActionType())
                .printPrepTypes(userEntry.getPrintPrepTypes() != null ?
                        List.copyOf(userEntry.getPrintPrepTypes()) : null)
                .colorsProfile(userEntry.getColorsProfile())
                .articleNumbers(userEntry.getArticleNumbers())
                .graphicComplexity(userEntry.getGraphicComplexity())
                .observations(userEntry.getObservations())
                .adminSync(adminStatus)
                .build();
    }

    // ========================================================================
    // 3. ADMIN SAVE PROCESSING (Same as before)
    // ========================================================================

    /**
     * Process admin register entries during save operation
     * Handles automatic status transitions when admin saves the register
     */
    public List<RegisterEntry> performAdminSaveProcessing(List<RegisterEntry> adminEntries) {
        if (adminEntries == null || adminEntries.isEmpty()) {
            return new ArrayList<>();
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Starting admin save processing for %d entries", adminEntries.size()));

        List<RegisterEntry> processedEntries = new ArrayList<>();
        int conflictResolutions = 0;
        int regularEntries = 0;

        for (RegisterEntry entry : adminEntries) {
            RegisterEntry processedEntry = applyAdminSaveStatusTransitions(entry);

            if (processedEntry != null) {
                processedEntries.add(processedEntry);

                // Track what happened for logging
                if (!entry.getAdminSync().equals(processedEntry.getAdminSync())) {
                    if (SyncStatusMerge.ADMIN_CHECK.name().equals(entry.getAdminSync())) {
                        conflictResolutions++;
                        LoggerUtil.info(this.getClass(), String.format(
                                "Entry %d: ADMIN_CHECK → ADMIN_EDITED (conflict resolved by admin)",
                                entry.getEntryId()));
                    }
                } else {
                    regularEntries++;
                }
            }
        }

        int removedCount = adminEntries.size() - processedEntries.size();

        LoggerUtil.info(this.getClass(), String.format(
                "Admin save processing completed: %d entries processed (%d conflict resolutions, %d regular), %d removed",
                processedEntries.size(), conflictResolutions, regularEntries, removedCount));

        return processedEntries;
    }

    /**
     * Apply status transitions that happen automatically when admin saves
     */
    private RegisterEntry applyAdminSaveStatusTransitions(RegisterEntry currentEntry) {
        if (currentEntry == null) return null;

        String currentStatus = currentEntry.getAdminSync();

        // Step 6: Admin saves after reviewing conflict: ADMIN_CHECK → ADMIN_EDITED
        if (SyncStatusMerge.ADMIN_CHECK.name().equals(currentStatus)) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Entry %d: Admin resolved conflict - ADMIN_CHECK → ADMIN_EDITED",
                    currentEntry.getEntryId()));

            return RegisterEntry.builder()
                    .entryId(currentEntry.getEntryId())
                    .userId(currentEntry.getUserId())
                    .date(currentEntry.getDate())
                    .orderId(currentEntry.getOrderId())
                    .productionId(currentEntry.getProductionId())
                    .omsId(currentEntry.getOmsId())
                    .clientName(currentEntry.getClientName())
                    .actionType(currentEntry.getActionType())
                    .printPrepTypes(currentEntry.getPrintPrepTypes() != null ?
                            List.copyOf(currentEntry.getPrintPrepTypes()) : null)
                    .colorsProfile(currentEntry.getColorsProfile())
                    .articleNumbers(currentEntry.getArticleNumbers())
                    .graphicComplexity(currentEntry.getGraphicComplexity())
                    .observations(currentEntry.getObservations())
                    .adminSync(SyncStatusMerge.ADMIN_EDITED.name()) // ADMIN_CHECK → ADMIN_EDITED
                    .build();
        }

        // Remove entries marked for deletion (keep this for other parts of system)
        if (SyncStatusMerge.ADMIN_BLANK.name().equals(currentStatus)) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Entry %d: Removing entry marked as ADMIN_BLANK", currentEntry.getEntryId()));
            return null;
        }

        // All other statuses remain unchanged during admin save
        // USER_INPUT, ADMIN_EDITED, USER_DONE, USER_EDITED stay as-is
        // The actual status transitions happen during merge operations, not save
        LoggerUtil.debug(this.getClass(), String.format(
                "Entry %d: Keeping status %s unchanged during admin save",
                currentEntry.getEntryId(), currentStatus));

        return currentEntry; // Return unchanged
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    /**
     * Get userId from UserService
     */
    private Integer getUserIdFromUserService(String username) {
        try {
            Optional<User> userOpt = userService.getUserByUsername(username);
            if (userOpt.isPresent()) {
                Integer userId = userOpt.get().getUserId();
                LoggerUtil.debug(this.getClass(), String.format(
                        "Found userId %d for username %s in UserService", userId, username));
                return userId;
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "User %s not found in UserService", username));
                return null;
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting userId for %s from UserService: %s", username, e.getMessage()), e);
            return null;
        }
    }

    /**
     * Invalidate user cache after merge operations
     */
    private void invalidateUserCache(String username, Integer userId, int year, int month) {
        try {
            registerCacheService.clearMonth(username, year, month);
            LoggerUtil.debug(this.getClass(), String.format(
                    "Invalidated cache for %s/%s - %d/%d", username, userId, year, month));
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Failed to invalidate cache for %s - %d/%d: %s",
                    username, year, month, e.getMessage()));
        }
    }

    /**
         * Helper class for merge processing
         */
        private record MergePair(Integer entryId, RegisterEntry userEntry, RegisterEntry adminEntry) {
    }
}