package com.ctgraphdep.register.service;

import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.merge.engine.UniversalMergeEngine;
import com.ctgraphdep.merge.enums.EntityType;
import com.ctgraphdep.merge.wrapper.GenericEntityWrapper;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.register.util.RegisterWrapperFactory;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.RegisterCacheService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REFACTORED RegisterMergeService using Universal Merge Engine.
 * Key Changes:
 * - Now uses UniversalMergeEngine with GenericEntityWrapper
 * - Supports new status system: [ROLE]_INPUT, [ROLE]_EDITED_[timestamp], [ROLE]_FINAL
 * - Removed dependency on deprecated RegisterMergeRule and SyncStatusMerge
 * - Proper timestamp-based conflict resolution
 * - Consistent with WorktimeMergeService implementation
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
    // CORE MERGE METHODS - Using UniversalMergeEngine
    // ========================================================================

    /**
     * Main merge method using Universal Merge Engine with GenericEntityWrapper
     */
    private RegisterEntry mergeRegisterEntries(RegisterEntry entry1, RegisterEntry entry2) {
        LoggerUtil.debug(this.getClass(), String.format("Universal merge: entry1=%s, entry2=%s",
                getEntryStatusString(entry1), getEntryStatusString(entry2)));

        if (entry1 == null && entry2 == null) {
            return null;
        }

        // Use GenericEntityWrapper via RegisterWrapperFactory
        GenericEntityWrapper<RegisterEntry> wrapper1 = RegisterWrapperFactory.createWrapperSafe(entry1);
        GenericEntityWrapper<RegisterEntry> wrapper2 = RegisterWrapperFactory.createWrapperSafe(entry2);

        GenericEntityWrapper<RegisterEntry> result = UniversalMergeEngine.merge(wrapper1, wrapper2, EntityType.REGISTER);

        RegisterEntry mergedEntry = result != null ? result.getEntity() : null;

        LoggerUtil.info(this.getClass(), String.format("Universal merge result: %s", getEntryStatusString(mergedEntry)));

        return mergedEntry;
    }

    // ========================================================================
    // 1. USER LOGIN MERGE (Admin file -> User file)
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
            LoggerUtil.error(this.getClass(), String.format("Unexpected error in user login merge for %s: %s",
                    username, e.getMessage()), e);
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
            LoggerUtil.info(this.getClass(), String.format("Starting user login merge for %s - %d/%d",
                    username, year, month));

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
                return ServiceResult.systemError("Failed to load user entries: " + userEntriesResult.getErrorMessage(),
                        "load_user_entries_failed");
            }

            ServiceResult<List<RegisterEntry>> adminEntriesResult = loadAdminNetworkEntries(username, userId, year, month);
            if (adminEntriesResult.isFailure()) {
                return ServiceResult.systemError("Failed to load admin entries: " + adminEntriesResult.getErrorMessage(),
                        "load_admin_entries_failed");
            }

            List<RegisterEntry> userEntries = userEntriesResult.getData();
            List<RegisterEntry> adminEntries = adminEntriesResult.getData();

            if (adminEntries == null || adminEntries.isEmpty()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "No admin register found for %s - %d/%d, no merge needed", username, year, month));
                return ServiceResult.success("No admin changes found - no merge needed");
            }

            // Perform Universal Merge (Admin → User direction)
            ServiceResult<List<RegisterEntry>> mergeResult = performUniversalMerge(
                    userEntries, adminEntries, userId, "USER_LOGIN");

            if (mergeResult.isFailure()) {
                return ServiceResult.businessError("Merge operation failed: " + mergeResult.getErrorMessage(),
                        "merge_failed");
            }

            List<RegisterEntry> mergedEntries = mergeResult.getData();

            // Normalize all merged entries to clean up old/invalid statuses (USER_DONE, etc.)
            mergedEntries = mergedEntries.stream()
                    .map(this::normalizeEntryStatus)
                    .collect(Collectors.toList());

            // Save result to user file and invalidate cache
            ServiceResult<Void> saveResult = saveUserEntries(username, userId, mergedEntries, year, month);
            if (saveResult.isFailure()) {
                return ServiceResult.systemError("Failed to save merged entries: " + saveResult.getErrorMessage(),
                        "save_failed");
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

    // ========================================================================
    // 2. ADMIN LOAD MERGE (User file -> Admin file)
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
                return ServiceResult.systemError("Failed to load user entries: " + userEntriesResult.getErrorMessage(),
                        "load_user_entries_failed");
            }

            ServiceResult<List<RegisterEntry>> adminEntriesResult = loadAdminLocalEntries(username, userId, year, month);
            if (adminEntriesResult.isFailure()) {
                return ServiceResult.systemError("Failed to load admin entries: " + adminEntriesResult.getErrorMessage(),
                        "load_admin_entries_failed");
            }

            List<RegisterEntry> userEntries = userEntriesResult.getData();
            List<RegisterEntry> adminEntries = adminEntriesResult.getData();

            if (userEntries == null) userEntries = new ArrayList<>();

            List<RegisterEntry> mergedEntries;
            List<String> warnings = new ArrayList<>();
            boolean needsSave = false;

            // Bootstrap logic: If admin file doesn't exist, create from user file
            if (adminEntries == null || adminEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Bootstrapping admin register for %s from %d user entries", username, userEntries.size()));

                mergedEntries = userEntries.stream()
                        .map(this::normalizeEntryStatus)
                        .collect(Collectors.toList());

                warnings.add("Admin register bootstrapped from " + userEntries.size() + " user entries");
                needsSave = true; // Bootstrap always needs save
            } else {
                // Perform Universal Merge (User → Admin direction)
                ServiceResult<List<RegisterEntry>> mergeResult = performUniversalMerge(
                        userEntries, adminEntries, userId, "ADMIN_LOAD");

                if (mergeResult.isFailure()) {
                    return ServiceResult.businessError("Merge operation failed: " + mergeResult.getErrorMessage(),
                            "merge_failed");
                }

                mergedEntries = mergeResult.getData();
                if (mergeResult.hasWarnings()) {
                    warnings.addAll(mergeResult.getWarnings());
                }

                // Normalize all merged entries to clean up old/invalid statuses (USER_DONE, etc.)
                mergedEntries = mergedEntries.stream()
                        .map(this::normalizeEntryStatus)
                        .collect(Collectors.toList());

                // Check if merge actually changed anything
                needsSave = hasEntriesChanged(adminEntries, mergedEntries);
                if (!needsSave) {
                    LoggerUtil.debug(this.getClass(), String.format(
                            "Admin load merge: No changes detected for %s - %d/%d, skipping save", username, year, month));
                } else {
                    LoggerUtil.info(this.getClass(), String.format(
                            "Admin load merge: Changes detected for %s - %d/%d, will save", username, year, month));
                }
            }

            // Only save if there are actual changes or bootstrap
            if (needsSave) {
                ServiceResult<Void> saveResult = saveAdminEntries(username, userId, mergedEntries, year, month);
                if (saveResult.isFailure()) {
                    return ServiceResult.systemError("Failed to save merged entries: " + saveResult.getErrorMessage(),
                            "save_failed");
                }
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

    // ========================================================================
    // 3. ADMIN SAVE PROCESSING
    // ========================================================================

    /**
     * Process admin register entries during save operation
     * Now uses Universal status system - no special transitions needed
     * Admin edits will automatically create ADMIN_EDITED_[timestamp] statuses
     *
     * @param adminEntries Admin entries to process
     * @return ServiceResult with processed entries
     */
    public ServiceResult<List<RegisterEntry>> performAdminSaveProcessing(List<RegisterEntry> adminEntries) {
        try {
            if (adminEntries == null || adminEntries.isEmpty()) {
                return ServiceResult.success(new ArrayList<>());
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Admin save processing: %d entries (using Universal status system)", adminEntries.size()));

            // Filter out any entries marked for deletion (if DELETE status is used)
            List<RegisterEntry> processedEntries = adminEntries.stream()
                    .filter(entry -> !"DELETE".equals(entry.getAdminSync()))
                    .collect(Collectors.toList());

            int removedCount = adminEntries.size() - processedEntries.size();

            String summary = String.format("Admin save processing completed: %d entries processed, %d removed",
                    processedEntries.size(), removedCount);

            LoggerUtil.info(this.getClass(), summary);

            return ServiceResult.success(processedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in admin save processing: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to process admin entries during save", "save_processing_failed");
        }
    }

    // ========================================================================
    // UNIVERSAL MERGE LOGIC
    // ========================================================================

    /**
     * Perform Universal Merge using UniversalMergeEngine
     * Works for both USER_LOGIN and ADMIN_LOAD directions
     */
    private ServiceResult<List<RegisterEntry>> performUniversalMerge(
            List<RegisterEntry> primaryEntries,
            List<RegisterEntry> secondaryEntries,
            Integer userId,
            String mergeType) {
        try {
            if (primaryEntries == null) primaryEntries = new ArrayList<>();
            if (secondaryEntries == null) secondaryEntries = new ArrayList<>();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Universal merge [%s]: %d primary entries, %d secondary entries",
                    mergeType, primaryEntries.size(), secondaryEntries.size()));

            // Create lookup maps by entryId
            Map<Integer, RegisterEntry> primaryMap = createEntriesMap(primaryEntries);
            Map<Integer, RegisterEntry> secondaryMap = createEntriesMap(secondaryEntries);

            // Get all unique entry IDs
            Set<Integer> allEntryIds = new HashSet<>();
            allEntryIds.addAll(primaryMap.keySet());
            allEntryIds.addAll(secondaryMap.keySet());

            List<RegisterEntry> mergedEntries = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int mergeCount = 0;
            int deleteCount = 0;

            // Process all entries using Universal Merge Engine
            for (Integer entryId : allEntryIds) {
                RegisterEntry primaryEntry = primaryMap.get(entryId);
                RegisterEntry secondaryEntry = secondaryMap.get(entryId);

                try {
                    // Apply Universal Merge logic
                    RegisterEntry mergedEntry = mergeRegisterEntries(primaryEntry, secondaryEntry);

                    if (mergedEntry != null) {
                        // Ensure userId is set
                        if (mergedEntry.getUserId() == null) {
                            mergedEntry.setUserId(userId);
                        }
                        mergedEntries.add(mergedEntry);
                        mergeCount++;
                    } else {
                        deleteCount++;
                        LoggerUtil.debug(this.getClass(), String.format("Entry %d deleted during merge", entryId));
                    }
                } catch (Exception e) {
                    warnings.add("Failed to merge entry " + entryId + ": " + e.getMessage());
                    LoggerUtil.warn(this.getClass(), String.format("Error merging entry %d: %s", entryId, e.getMessage()));
                }
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "Merge statistics [%s]: %d merged, %d deleted", mergeType, mergeCount, deleteCount));

            // Sort by date for consistency
            mergedEntries.sort(Comparator.comparing(RegisterEntry::getDate));

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(mergedEntries, warnings);
            }

            return ServiceResult.success(mergedEntries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in universal merge: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to perform merge operation", "merge_operation_failed");
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Create map of entries by entryId
     */
    private Map<Integer, RegisterEntry> createEntriesMap(List<RegisterEntry> entries) {
        if (entries == null) {
            return new HashMap<>();
        }

        return entries.stream()
                .collect(Collectors.toMap(
                        RegisterEntry::getEntryId,
                        entry -> entry,
                        (existing, replacement) -> existing // Keep first occurrence
                ));
    }

    /**
     * Normalize entry status to valid merge status
     * Old/unknown statuses become USER_INPUT
     */
    private RegisterEntry normalizeEntryStatus(RegisterEntry entry) {
        if (entry == null) return null;

        String status = entry.getAdminSync();

        // If status is null or not a valid new format, normalize to USER_INPUT
        if (status == null || !MergingStatusConstants.isValidStatus(status)) {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Normalizing entry %d status from '%s' to USER_INPUT",
                    entry.getEntryId(), status));
            entry.setAdminSync(MergingStatusConstants.USER_INPUT);
        }

        return entry;
    }

    /**
     * Helper method for readable status logging
     */
    private String getEntryStatusString(RegisterEntry entry) {
        if (entry == null) {
            return "null";
        }

        String status = entry.getAdminSync();
        if (status == null) {
            status = "null";
        }

        // Add timestamp info for timestamped statuses
        if (MergingStatusConstants.isTimestampedEditStatus(status)) {
            long timestamp = MergingStatusConstants.extractTimestamp(status);
            String editorType = MergingStatusConstants.getEditorType(status);
            return String.format("%s[%s:%d]", status, editorType, timestamp);
        }

        return String.format("%s[entry:%d]", status, entry.getEntryId());
    }

    /**
     * Check if two lists of entries have meaningful differences.
     * Compares entry count, entry IDs, and adminSync statuses.
     * Returns true if merge changed anything that matters.
     */
    private boolean hasEntriesChanged(List<RegisterEntry> originalEntries, List<RegisterEntry> mergedEntries) {
        if (originalEntries == null && mergedEntries == null) {
            return false;
        }
        if (originalEntries == null || mergedEntries == null) {
            return true; // One is null, other isn't
        }
        if (originalEntries.size() != mergedEntries.size()) {
            return true; // Different count
        }

        // Create maps for comparison
        Map<Integer, RegisterEntry> originalMap = createEntriesMap(originalEntries);
        Map<Integer, RegisterEntry> mergedMap = createEntriesMap(mergedEntries);

        // Check if entry IDs match
        if (!originalMap.keySet().equals(mergedMap.keySet())) {
            return true; // Different entry IDs
        }

        // Check if any adminSync status changed
        for (Integer entryId : originalMap.keySet()) {
            RegisterEntry original = originalMap.get(entryId);
            RegisterEntry merged = mergedMap.get(entryId);

            String originalStatus = original.getAdminSync();
            String mergedStatus = merged.getAdminSync();

            // Compare statuses
            if (!java.util.Objects.equals(originalStatus, mergedStatus)) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Entry %d status changed: %s → %s", entryId, originalStatus, mergedStatus));
                return true;
            }
        }

        // No meaningful changes detected
        return false;
    }

    // ========================================================================
    // DATA ACCESS METHODS
    // ========================================================================

    /**
     * Load user entries with error handling
     */
    private ServiceResult<List<RegisterEntry>> loadUserEntries(String username, Integer userId, int year, int month) {
        try {
            List<RegisterEntry> entries = registerDataService.readUserLocalReadOnly(username, userId, username, year, month);
            return ServiceResult.success(entries != null ? entries : new ArrayList<>());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading user entries for %s: %s",
                    username, e.getMessage()), e);
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
            LoggerUtil.error(this.getClass(), String.format("Error loading user network entries for %s: %s",
                    username, e.getMessage()), e);
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
            LoggerUtil.error(this.getClass(), String.format("Error loading admin network entries for %s: %s",
                    username, e.getMessage()), e);
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
            LoggerUtil.error(this.getClass(), String.format("Error loading admin local entries for %s: %s",
                    username, e.getMessage()), e);
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
            LoggerUtil.error(this.getClass(), String.format("Error saving user entries for %s: %s",
                    username, e.getMessage()), e);
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
            LoggerUtil.error(this.getClass(), String.format("Error saving admin entries for %s: %s",
                    username, e.getMessage()), e);
            return ServiceResult.systemError("Failed to save admin entries", "save_admin_entries_failed");
        }
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
}