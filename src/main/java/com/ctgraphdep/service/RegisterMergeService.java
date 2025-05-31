package com.ctgraphdep.service;

import com.ctgraphdep.enums.RegisterMergeRule;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.cache.RegisterCacheService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REFACTORED RegisterMergeService with ServiceResult pattern.
 * Key Changes:
 * - All methods now return ServiceResult<T> instead of throwing exceptions
 * - Proper error categorization (validation, business, system errors)
 * - Graceful error handling with detailed error messages
 * - Preserved existing merge logic with better error management
 */
@Service
public class RegisterMergeService {

    private final RegisterDataService registerDataService;
    private final RegisterCacheService registerCacheService;
    private final UserService userService;

    public RegisterMergeService(RegisterDataService registerDataService, RegisterCacheService registerCacheService, UserService userService) {
        this.registerDataService = registerDataService;
        this.registerCacheService = registerCacheService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // 1. USER LOGIN MERGE (Admin file -> User file) - REFACTORED
    // ========================================================================

    /**
     * User login merge: Apply admin decisions to user file
     *
     * @param username Username
     */
    public void performUserLoginMerge(String username) {
        try {
            LocalDate currentDate = LocalDate.now();
            int year = currentDate.getYear();
            int month = currentDate.getMonthValue();

            LoggerUtil.info(this.getClass(), String.format(
                    "Starting user login merge for %s - %d/%d", username, year, month));

            // Validate input parameters
            if (username == null || username.trim().isEmpty()) {
                ServiceResult.validationError("Username is required", "missing_username");
                return;
            }

            Integer userId = getUserIdFromUserService(username);
            if (userId == null) {
                ServiceResult.notFound("User not found in system: " + username, "user_not_found");
                return;
            }

            performUserLoginMerge(username, userId, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error in user login merge for %s: %s", username, e.getMessage()), e);
            ServiceResult.systemError("Unexpected error during user login merge", "merge_system_error");
        }
    }

    /**
     * User login merge for specific month
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return ServiceResult with merge summary
     */
    public ServiceResult<String> performUserLoginMerge(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting user login merge for %s - %d/%d", username, year, month));

            // Validate parameters
            if (username == null || username.trim().isEmpty()) {
                return ServiceResult.validationError("Username is required", "missing_username");
            }
            if (userId == null || userId <= 0) {
                return ServiceResult.validationError("Valid user ID is required", "invalid_user_id");
            }
            if (year < 2000 || year > 2100) {
                return ServiceResult.validationError("Invalid year provided", "invalid_year");
            }
            if (month < 1 || month > 12) {
                return ServiceResult.validationError("Invalid month provided", "invalid_month");
            }

            // Load files with error handling
            ServiceResult<List<RegisterEntry>> userEntriesResult = loadUserEntries(username, userId, year, month);
            if (userEntriesResult.isFailure()) {
                return ServiceResult.systemError("Failed to load user entries: " + userEntriesResult.getErrorMessage(), "load_user_entries_failed");
            }

            ServiceResult<List<RegisterEntry>> adminEntriesResult = loadAdminNetworkEntries(username, userId, year, month);
            if (adminEntriesResult.isFailure()) {
                return ServiceResult.systemError("Failed to load admin entries: " + adminEntriesResult.getErrorMessage(), "load_admin_entries_failed");
            }

            List<RegisterEntry> userEntries = userEntriesResult.getData();
            List<RegisterEntry> adminEntries = adminEntriesResult.getData();

            if (adminEntries == null || adminEntries.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No admin register found for %s - %d/%d, no merge needed", username, year, month));
                return ServiceResult.success("No admin changes found - no merge needed");
            }

            // Perform optimized merge
            ServiceResult<List<RegisterEntry>> mergeResult = performOptimizedUserLoginMerge(userEntries, adminEntries);
            if (mergeResult.isFailure()) {
                return ServiceResult.businessError("Merge operation failed: " + mergeResult.getErrorMessage(), "merge_failed");
            }

            List<RegisterEntry> mergedEntries = mergeResult.getData();

            // Save result to user file and invalidate cache
            ServiceResult<Void> saveResult = saveUserEntries(username, userId, mergedEntries, year, month);
            if (saveResult.isFailure()) {
                return ServiceResult.systemError("Failed to save merged entries: " + saveResult.getErrorMessage(), "save_failed");
            }

            invalidateUserCache(username, userId, year, month);

            String summary = String.format("User login merge completed for %s - %d/%d (%d user entries, %d admin entries, %d merged entries)",
                    username, year, month,
                    userEntries != null ? userEntries.size() : 0,
                    adminEntries.size(), mergedEntries.size());

            LoggerUtil.info(this.getClass(), summary);
            return ServiceResult.success(summary);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error in user login merge for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error during user login merge", "merge_system_error");
        }
    }

    /**
     * Optimized user login merge with two-pass approach
     */
    private ServiceResult<List<RegisterEntry>> performOptimizedUserLoginMerge(List<RegisterEntry> userEntries, List<RegisterEntry> adminEntries) {
        try {
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
            List<String> warnings = new ArrayList<>();

            // Process all entries using simplified rules
            for (Integer entryId : allEntryIds) {
                RegisterEntry userEntry = userMap.get(entryId);
                RegisterEntry adminEntry = adminMap.get(entryId);

                try {
                    // Apply user login merge rules
                    RegisterEntry mergedEntry = RegisterMergeRule.applyUserLoginMerge(adminEntry, userEntry);

                    if (mergedEntry != null) {
                        mergedEntries.add(mergedEntry);
                    } else {
                        warnings.add("Entry " + entryId + " was removed during merge");
                        LoggerUtil.debug(this.getClass(), String.format("Entry %d removed during user login merge", entryId));
                    }
                } catch (Exception e) {
                    warnings.add("Failed to merge entry " + entryId + ": " + e.getMessage());
                    LoggerUtil.warn(this.getClass(), String.format("Error merging entry %d: %s", entryId, e.getMessage()));
                }
            }

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(mergedEntries, warnings);
            }

            return ServiceResult.success(mergedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in optimized user login merge: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to perform merge operation", "merge_operation_failed");
        }
    }

    // ========================================================================
    // 2. ADMIN LOAD MERGE (User file -> Admin file) - REFACTORED
    // ========================================================================

    /**
     * Admin load merge: Incorporate user changes into admin file
     * @param username Username
     * @param userId User ID
     * @param year Year
     * @param month Month
     * @return ServiceResult with merged entries or error details
     */
    public ServiceResult<List<RegisterEntry>> performAdminLoadMerge(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Starting admin load merge for %s - %d/%d", username, year, month));

            // Validate parameters
            if (username == null || username.trim().isEmpty()) {
                return ServiceResult.validationError("Username is required", "missing_username");
            }
            if (userId == null || userId <= 0) {
                return ServiceResult.validationError("Valid user ID is required", "invalid_user_id");
            }
            if (year < 2000 || year > 2100) {
                return ServiceResult.validationError("Invalid year provided", "invalid_year");
            }
            if (month < 1 || month > 12) {
                return ServiceResult.validationError("Invalid month provided", "invalid_month");
            }

            // Load files with error handling
            ServiceResult<List<RegisterEntry>> userEntriesResult = loadUserNetworkEntries(username, userId, year, month);
            if (userEntriesResult.isFailure()) {
                return ServiceResult.systemError("Failed to load user entries: " + userEntriesResult.getErrorMessage(), "load_user_entries_failed");
            }

            ServiceResult<List<RegisterEntry>> adminEntriesResult = loadAdminLocalEntries(username, userId, year, month);
            if (adminEntriesResult.isFailure()) {
                return ServiceResult.systemError("Failed to load admin entries: " + adminEntriesResult.getErrorMessage(), "load_admin_entries_failed");
            }

            List<RegisterEntry> userEntries = userEntriesResult.getData();
            List<RegisterEntry> adminEntries = adminEntriesResult.getData();

            if (userEntries == null) userEntries = new ArrayList<>();

            List<RegisterEntry> mergedEntries;
            List<String> warnings = new ArrayList<>();

            // Bootstrap logic: If admin file doesn't exist, create from user file
            if (adminEntries == null || adminEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Bootstrapping admin register for %s from %d user entries", username, userEntries.size()));

                mergedEntries = userEntries.stream()
                        .map(this::bootstrapEntryForAdmin)
                        .collect(Collectors.toList());

                warnings.add("Admin register bootstrapped from " + userEntries.size() + " user entries");
            } else {
                // Perform optimized merge
                ServiceResult<List<RegisterEntry>> mergeResult = performOptimizedAdminLoadMerge(userEntries, adminEntries);
                if (mergeResult.isFailure()) {
                    return ServiceResult.businessError("Merge operation failed: " + mergeResult.getErrorMessage(), "merge_failed");
                }

                mergedEntries = mergeResult.getData();
                if (mergeResult.hasWarnings()) {
                    warnings.addAll(mergeResult.getWarnings());
                }
            }

            // Save merged result to admin file
            ServiceResult<Void> saveResult = saveAdminEntries(username, userId, mergedEntries, year, month);
            if (saveResult.isFailure()) {
                return ServiceResult.systemError("Failed to save merged entries: " + saveResult.getErrorMessage(), "save_failed");
            }

            String summary = String.format("Admin load merge completed for %s - %d/%d (%d user entries, %d admin entries, %d merged entries)",
                    username, year, month, userEntries.size(),
                    adminEntries != null ? adminEntries.size() : 0, mergedEntries.size());

            LoggerUtil.info(this.getClass(), summary);

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(mergedEntries, warnings);
            }

            return ServiceResult.success(mergedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error in admin load merge for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("Unexpected error during admin load merge", "merge_system_error");
        }
    }

    /**
     * Optimized admin load merge with two-pass approach
     */
    private ServiceResult<List<RegisterEntry>> performOptimizedAdminLoadMerge(List<RegisterEntry> userEntries,
                                                                              List<RegisterEntry> adminEntries) {
        try {
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
            List<String> warnings = new ArrayList<>();

            // Pass 1: Status screening and quick processing
            List<MergePair> conflictCandidates = new ArrayList<>();

            for (Integer entryId : allEntryIds) {
                RegisterEntry userEntry = userMap.get(entryId);
                RegisterEntry adminEntry = adminMap.get(entryId);

                try {
                    // Check if this pair needs content comparison
                    if (needsContentComparison(userEntry, adminEntry)) {
                        conflictCandidates.add(new MergePair(entryId, userEntry, adminEntry));
                    } else {
                        // Quick resolution using merge rules
                        RegisterEntry mergedEntry = RegisterMergeRule.applyAdminLoadMerge(userEntry, adminEntry);
                        if (mergedEntry != null) {
                            mergedEntries.add(mergedEntry);
                        } else {
                            warnings.add("Entry " + entryId + " was removed during merge");
                        }
                    }
                } catch (Exception e) {
                    warnings.add("Failed to process entry " + entryId + ": " + e.getMessage());
                    LoggerUtil.warn(this.getClass(), String.format("Error processing entry %d: %s", entryId, e.getMessage()));
                }
            }

            // Pass 2: Batch process conflict candidates with content comparison
            if (!conflictCandidates.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Processing %d conflict candidates with content comparison", conflictCandidates.size()));

                ServiceResult<List<RegisterEntry>> conflictResult = batchProcessAdminLoadConflicts(conflictCandidates);
                if (conflictResult.isFailure()) {
                    return ServiceResult.businessError("Failed to resolve conflicts: " + conflictResult.getErrorMessage(), "conflict_resolution_failed");
                }

                mergedEntries.addAll(conflictResult.getData());
                if (conflictResult.hasWarnings()) {
                    warnings.addAll(conflictResult.getWarnings());
                }
            }

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(mergedEntries, warnings);
            }

            return ServiceResult.success(mergedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in optimized admin load merge: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to perform merge operation", "merge_operation_failed");
        }
    }

    /**
     * Batch process conflict candidates with content comparison
     */
    private ServiceResult<List<RegisterEntry>> batchProcessAdminLoadConflicts(List<MergePair> conflictCandidates) {
        try {
            List<RegisterEntry> resolvedEntries = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (MergePair pair : conflictCandidates) {
                try {
                    RegisterEntry resolvedEntry = RegisterMergeRule.applyAdminLoadMerge(pair.userEntry, pair.adminEntry);
                    if (resolvedEntry != null) {
                        resolvedEntries.add(resolvedEntry);
                    } else {
                        warnings.add("Conflict candidate entry " + pair.entryId + " was removed during resolution");
                    }
                } catch (Exception e) {
                    warnings.add("Failed to resolve conflict for entry " + pair.entryId + ": " + e.getMessage());
                    LoggerUtil.warn(this.getClass(), String.format("Error resolving conflict for entry %d: %s", pair.entryId, e.getMessage()));
                }
            }

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(resolvedEntries, warnings);
            }

            return ServiceResult.success(resolvedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in batch processing conflicts: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to resolve entry conflicts", "conflict_processing_failed");
        }
    }

    // ========================================================================
    // 3. ADMIN SAVE PROCESSING - REFACTORED
    // ========================================================================

    /**
     * Process admin register entries during save operation
     * @param adminEntries Admin entries to process
     * @return ServiceResult with processed entries
     */
    public ServiceResult<List<RegisterEntry>> performAdminSaveProcessing(List<RegisterEntry> adminEntries) {
        try {
            if (adminEntries == null || adminEntries.isEmpty()) {
                return ServiceResult.success(new ArrayList<>());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Starting admin save processing for %d entries", adminEntries.size()));

            List<RegisterEntry> processedEntries = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int conflictResolutions = 0;
            int regularEntries = 0;

            for (RegisterEntry entry : adminEntries) {
                try {
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
                    } else {
                        warnings.add("Entry " + entry.getEntryId() + " was removed (marked as ADMIN_BLANK)");
                    }
                } catch (Exception e) {
                    warnings.add("Failed to process entry " + entry.getEntryId() + ": " + e.getMessage());
                    LoggerUtil.warn(this.getClass(), String.format("Error processing entry %d: %s", entry.getEntryId(), e.getMessage()));
                }
            }

            int removedCount = adminEntries.size() - processedEntries.size();

            String summary = String.format("Admin save processing completed: %d entries processed (%d conflict resolutions, %d regular), %d removed",
                    processedEntries.size(), conflictResolutions, regularEntries, removedCount);

            LoggerUtil.info(this.getClass(), summary);

            if (!warnings.isEmpty()) {
                warnings.add(0, summary);
                return ServiceResult.successWithWarnings(processedEntries, warnings);
            }

            return ServiceResult.success(processedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in admin save processing: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to process admin entries during save", "save_processing_failed");
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS - ENHANCED WITH ERROR HANDLING
    // ========================================================================

    /**
     * Load user entries with error handling
     */
    private ServiceResult<List<RegisterEntry>> loadUserEntries(String username, Integer userId, int year, int month) {
        try {
            List<RegisterEntry> entries = registerDataService.readUserLocalReadOnly(username, userId, username, year, month);
            return ServiceResult.success(entries != null ? entries : new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading user entries for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Failed to load user entries from local storage", "load_user_local_failed");
        }
    }

    /**
     * Load user network entries with error handling
     */
    private ServiceResult<List<RegisterEntry>> loadUserNetworkEntries(String username, Integer userId, int year, int month) {
        try {
            List<RegisterEntry> entries = registerDataService.readUserFromNetworkOnly(username, userId, year, month);
            return ServiceResult.success(entries != null ? entries : new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading user network entries for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Failed to load user entries from network", "load_user_network_failed");
        }
    }

    /**
     * Load admin network entries with error handling
     */
    private ServiceResult<List<RegisterEntry>> loadAdminNetworkEntries(String username, Integer userId, int year, int month) {
        try {
            List<RegisterEntry> entries = registerDataService.readAdminByUserNetworkReadOnly(username, userId, year, month);
            return ServiceResult.success(entries != null ? entries : new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading admin network entries for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Failed to load admin entries from network", "load_admin_network_failed");
        }
    }

    /**
     * Load admin local entries with error handling
     */
    private ServiceResult<List<RegisterEntry>> loadAdminLocalEntries(String username, Integer userId, int year, int month) {
        try {
            List<RegisterEntry> entries = registerDataService.readAdminLocalReadOnly(username, userId, year, month);
            return ServiceResult.success(entries != null ? entries : new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading admin local entries for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Failed to load admin entries from local storage", "load_admin_local_failed");
        }
    }

    /**
     * Save user entries with error handling
     */
    private ServiceResult<Void> saveUserEntries(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        try {
            registerDataService.writeUserLocalWithSyncAndBackup(username, userId, entries, year, month);
            return ServiceResult.success();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving user entries for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Failed to save user entries", "save_user_entries_failed");
        }
    }

    /**
     * Save admin entries with error handling
     */
    private ServiceResult<Void> saveAdminEntries(String username, Integer userId, List<RegisterEntry> entries, int year, int month) {
        try {
            registerDataService.writeAdminLocalWithSyncAndBackup(username, userId, entries, year, month);
            return ServiceResult.success();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving admin entries for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("Failed to save admin entries", "save_admin_entries_failed");
        }
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

        // Remove entries marked for deletion
        if (SyncStatusMerge.ADMIN_BLANK.name().equals(currentStatus)) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Entry %d: Removing entry marked as ADMIN_BLANK", currentEntry.getEntryId()));
            return null;
        }

        // All other statuses remain unchanged during admin save
        LoggerUtil.debug(this.getClass(), String.format(
                "Entry %d: Keeping status %s unchanged during admin save",
                currentEntry.getEntryId(), currentStatus));

        return currentEntry; // Return unchanged
    }

    /**
     * Get userId from UserService with error handling
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