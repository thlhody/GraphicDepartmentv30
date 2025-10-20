package com.ctgraphdep.register.service;

import com.ctgraphdep.register.util.CheckRegisterWrapperFactory;
import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.merge.engine.UniversalMergeEngine;
import com.ctgraphdep.merge.enums.EntityType;
import com.ctgraphdep.merge.wrapper.GenericEntityWrapper;
import com.ctgraphdep.fileOperations.data.CheckRegisterDataService;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.RegisterCheckCacheService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.service.result.ValidationServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified service for managing check register entries - FULLY REFACTORED
 * Handles both user and team lead operations with comprehensive ServiceResult usage
 * All methods now return ServiceResult for consistent error handling
 */
@Service
@Getter
public class CheckRegisterService {

    private final UserService userService;
    private final CheckRegisterDataService checkRegisterDataService;
    private final RegisterCheckCacheService registerCheckCacheService;

    @Autowired
    public CheckRegisterService(UserService userService, CheckRegisterDataService checkRegisterDataService, RegisterCheckCacheService registerCheckCacheService) {

        this.userService = userService;
        this.checkRegisterDataService = checkRegisterDataService;
        this.registerCheckCacheService = registerCheckCacheService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // ========================================================================
    // PUBLIC API METHODS - ALL RETURN ServiceResult
    // ========================================================================

    /**
     * Get all users with checking-related roles
     * Used by team leads to select users
     */
    public ServiceResult<List<User>> getAllCheckUsers() {
        try {
            List<User> allUsers = userService.getAllUsers();
            List<User> checkUsers = allUsers.stream().filter(user -> user.getRole().contains(SecurityConstants.ROLE_CHECKING)).collect(Collectors.toList());

            return ServiceResult.success(checkUsers);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting check users: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to retrieve checking users", "get_check_users_failed");
        }
    }

    /**
     * Load check entries for a specific month - REFACTORED
     * User can only access their own entries, team leads can access any entries
     */
    public ServiceResult<List<RegisterCheckEntry>> loadMonthEntries(String username, Integer userId, int year, int month) {
        try {
            // Validate inputs
            ServiceResult<Void> inputValidation = validateLoadEntriesInputs(username, userId, year, month);
            if (inputValidation.isFailure()) {
                return ServiceResult.validationError(inputValidation.getErrorMessage(), inputValidation.getErrorCode());
            }

            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            boolean isAccessingOwnData = currentUsername.equals(username);
            boolean isTeamLead = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals(SecurityConstants.ROLE_TL_CHECKING));

            ServiceResult<List<RegisterCheckEntry>> result;

            // Regular user accessing their own data
            if (isAccessingOwnData) {
                LoggerUtil.info(this.getClass(), String.format("Loading own check entries for %s - %d/%d (using cache)", username, year, month));

                // Use cache for own data
                List<RegisterCheckEntry> entries = registerCheckCacheService.getMonthEntries(username, userId, year, month);
                result = ServiceResult.success(entries);
            }
            // Team lead accessing another user's data - DIRECT NETWORK READ
            else if (isTeamLead) {
                LoggerUtil.info(this.getClass(), String.format("Team lead accessing %s's check entries - %d/%d (direct network read)", username, year, month));
                result = loadTeamCheckRegister(username, userId, year, month);
            }
            // Unauthorized access attempt
            else {
                LoggerUtil.warn(this.getClass(), String.format("Unauthorized attempt by %s to access %s's check register", currentUsername, username));
                return ServiceResult.unauthorized("You are not authorized to access this user's check register", "unauthorized_access");
            }

            if (result.isSuccess()) {
                List<RegisterCheckEntry> entries = result.getData();

                // Filter out deleted entries (tombstones)
                entries = filterDeletedEntries(entries);

                // Ensure entries are sorted with the newest first
                ServiceResult<Void> sortResult = sortEntriesGracefully(entries);
                if (sortResult.isFailure()) {
                    List<String> warnings = new ArrayList<>();
                    warnings.add("Entries loaded but sorting failed: " + sortResult.getErrorMessage());
                    return ServiceResult.successWithWarnings(entries, warnings);
                }

                LoggerUtil.info(this.getClass(), String.format("Successfully loaded %d entries for %s - %d/%d (tombstones filtered)", entries.size(), username, year, month));
                return ServiceResult.success(entries);
            }

            return result;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading check entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while loading entries", "load_entries_system_error");
        }
    }

    /**
     * Load team check register for a specific user - ENHANCED
     * This is what team leads see when reviewing a user's check register
     */
    public ServiceResult<List<RegisterCheckEntry>> loadTeamCheckRegister(String username, Integer userId, int year, int month) {
        try {
            // Validate inputs
            ServiceResult<Void> inputValidation = validateLoadEntriesInputs(username, userId, year, month);
            if (inputValidation.isFailure()) {
                return ServiceResult.validationError(inputValidation.getErrorMessage(), inputValidation.getErrorCode());
            }

            // Step 1: Read team lead entries (smart fallback already built-in)
            List<RegisterCheckEntry> teamEntries = checkRegisterDataService.readTeamLeadCheckRegisterLocalReadOnly(username, userId, year, month);

            // Step 2: If team register is empty, check if we need to initialize from user entries
            if (teamEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("Team register for %s - %d/%d is empty, checking for user entries to initialize", username, year, month));

                // Read user entries from network to check if initialization is needed
                List<RegisterCheckEntry> userEntries = checkRegisterDataService.readUserCheckRegisterFromNetworkOnly(username, userId, year, month);

                if (!userEntries.isEmpty()) {
                    LoggerUtil.info(this.getClass(), String.format("Found %d user entries for %s - %d/%d. Team register needs initialization.", userEntries.size(), username, year, month));
                    return ServiceResult.successWithWarning(teamEntries, "Team register is empty but user entries exist. Initialization required.");
                } else {
                    LoggerUtil.info(this.getClass(), String.format("No user entries found for %s - %d/%d. Team register remains empty.", username, year, month));
                }
            }

            // Step 3: Fix any null IDs and sort entries
            repairNullEntryIds(teamEntries);

            // Step 4: Filter out deleted entries (tombstones)
            teamEntries = filterDeletedEntries(teamEntries);

            ServiceResult<Void> sortResult = sortEntriesGracefully(teamEntries);

            List<String> warnings = new ArrayList<>();
            if (sortResult.isFailure()) {
                warnings.add("Team register loaded but sorting failed: " + sortResult.getErrorMessage());
            }

            LoggerUtil.info(this.getClass(), String.format("Loaded team check register for %s - %d/%d: %d entries (tombstones filtered)", username, year, month, teamEntries.size()));

            if (warnings.isEmpty()) {
                return ServiceResult.success(teamEntries);
            } else {
                return ServiceResult.successWithWarnings(teamEntries, warnings);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading team check register for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while loading team register", "load_team_register_system_error");
        }
    }

    /**
     * Initialize team check register with ServiceResult pattern - ENHANCED
     */
    public ServiceResult<List<RegisterCheckEntry>> initializeTeamCheckRegister(String username, Integer userId, int year, int month) {
        try {
            // Validate inputs
            ServiceResult<Void> inputValidation = validateLoadEntriesInputs(username, userId, year, month);
            if (inputValidation.isFailure()) {
                return ServiceResult.validationError(inputValidation.getErrorMessage(), inputValidation.getErrorCode());
            }

            LoggerUtil.info(this.getClass(), String.format("Initializing check register for %s - %d/%d", username, year, month));

            // Step 1: Read user entries from network (preferred source for team leads)
            List<RegisterCheckEntry> userEntries = checkRegisterDataService.readUserCheckRegisterFromNetworkOnly(username, userId, year, month);

            // Step 2: If no network entries, fallback to local as last resort
            if (userEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No network entries found for %s - %d/%d, trying local as fallback", username, year, month));
                userEntries = checkRegisterDataService.readUserCheckRegisterLocalReadOnly(username, userId, year, month);
            }

            // Step 3: Create team register entries
            List<RegisterCheckEntry> teamEntries;
            List<String> warnings = new ArrayList<>();

            if (userEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No user entries found for %s - %d/%d, creating empty team register", username, year, month));
                teamEntries = new ArrayList<>();
                warnings.add("No user entries found - initialized empty team register");
            } else {
                // Create copies of all entries preserving their original status
                teamEntries = userEntries.stream().map(this::copyEntry).collect(Collectors.toList());

                LoggerUtil.info(this.getClass(), String.format("Initialized team register for %s - %d/%d with %d entries from user register (statuses preserved)", username, year, month, teamEntries.size()));
            }

            // Step 4: Fix any null IDs and sort entries
            repairNullEntryIds(teamEntries);
            ServiceResult<Void> sortResult = sortEntriesGracefully(teamEntries);
            if (sortResult.isFailure()) {
                warnings.add("Initialization completed but sorting failed: " + sortResult.getErrorMessage());
            }

            // Step 5: Save the team check register
            try {
                checkRegisterDataService.writeTeamLeadCheckRegisterWithSyncAndBackup(username, userId, teamEntries, year, month);

                LoggerUtil.info(this.getClass(), String.format("Successfully initialized team check register for %s - %d/%d with %d entries", username, year, month, teamEntries.size()));

                if (warnings.isEmpty()) {
                    return ServiceResult.success(teamEntries);
                } else {
                    return ServiceResult.successWithWarnings(teamEntries, warnings);
                }

            } catch (Exception saveException) {
                LoggerUtil.error(this.getClass(), String.format("Failed to save initialized team register for %s: %s", username, saveException.getMessage()), saveException);
                return ServiceResult.systemError("Failed to save initialized team register", "save_failed");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error initializing team check register for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred during initialization", "system_error");
        }
    }

    /**
     * Mark all entries in the team check register as TL_CHECK_DONE - ENHANCED
     * Returns ServiceResult for graceful error handling with partial success support
     */
    public ServiceResult<List<RegisterCheckEntry>> markAllEntriesAsChecked(String username, Integer userId, int year, int month) {
        try {
            // Validate inputs
            ServiceResult<Void> inputValidation = validateLoadEntriesInputs(username, userId, year, month);
            if (inputValidation.isFailure()) {
                return ServiceResult.validationError(inputValidation.getErrorMessage(), inputValidation.getErrorCode());
            }

            // Load existing team register entries
            ServiceResult<List<RegisterCheckEntry>> loadResult = loadTeamCheckRegister(username, userId, year, month);
            if (loadResult.isFailure()) {
                return ServiceResult.systemError("Failed to load team register for marking entries", "load_failed_for_marking");
            }

            List<RegisterCheckEntry> entries = loadResult.getData();
            if (entries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No entries found to mark as checked for %s - %d/%d", username, year, month));
                return ServiceResult.successWithWarning(entries, "No entries found to mark as checked");
            }

            // Update status of all entries to TL_CHECK_DONE
            List<RegisterCheckEntry> updatedEntries = new ArrayList<>();
            int successCount = 0;
            int skipCount = 0;
            List<String> warnings = new ArrayList<>();

            for (RegisterCheckEntry entry : entries) {
                try {
                    // Only skip ADMIN_FINAL entries (truly immutable)
                    // Mark ALL other entries (TE, UI, UE, TI, etc.) as TEAM_FINAL
                    if (MergingStatusConstants.ADMIN_FINAL.equals(entry.getAdminSync())) {
                        updatedEntries.add(entry);
                        skipCount++;
                        LoggerUtil.debug(this.getClass(), String.format("Skipped entry %d with ADMIN_FINAL status", entry.getEntryId()));
                    } else {
                        // Mark entry as TEAM_FINAL (team lead approval)
                        RegisterCheckEntry updated = copyEntry(entry);
                        updated.setAdminSync(MergingStatusConstants.TEAM_FINAL);
                        updatedEntries.add(updated);
                        successCount++;
                        LoggerUtil.debug(this.getClass(), String.format("Marked entry %d as TEAM_FINAL (was: %s)", entry.getEntryId(), entry.getAdminSync()));
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), String.format("Failed to update entry %d: %s", entry.getEntryId(), e.getMessage()));
                    warnings.add("Failed to update entry " + entry.getEntryId());
                    updatedEntries.add(entry); // Keep original entry
                }
            }

            // Save updated entries
            try {
                checkRegisterDataService.writeTeamLeadCheckRegisterWithSyncAndBackup(username, userId, updatedEntries, year, month);

                LoggerUtil.info(this.getClass(), String.format("Marked %d entries as checked for %s - %d/%d (%d skipped)", successCount, username, year, month, skipCount));

                // Determine result type based on success/skip counts
                if (successCount == 0 && skipCount > 0) {
                    return ServiceResult.successWithWarning(updatedEntries, String.format("All %d entries were already in final state (skipped)", skipCount));
                } else if (warnings.isEmpty()) {
                    return ServiceResult.success(updatedEntries);
                } else {
                    warnings.add(0, String.format("Marked %d entries successfully, %d warnings", successCount, warnings.size()));
                    return ServiceResult.successWithWarnings(updatedEntries, warnings);
                }

            } catch (Exception saveException) {
                LoggerUtil.error(this.getClass(), String.format("Failed to save marked entries for %s: %s", username, saveException.getMessage()), saveException);
                return ServiceResult.systemError("Failed to save marked entries", "save_failed");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error marking entries as checked for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while marking entries", "system_error");
        }
    }

    /**
     * Mark a single entry as TEAM_FINAL
     * Allows team lead to mark individual entries as checked
     */
    public ServiceResult<RegisterCheckEntry> markSingleEntryAsTeamFinal(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Validate inputs
            ServiceResult<Void> inputValidation = validateMarkForDeletionInputs(username, userId, entryId, year, month);
            if (inputValidation.isFailure()) {
                return ServiceResult.validationError(inputValidation.getErrorMessage(), inputValidation.getErrorCode());
            }

            // Load existing entries - need to load from file directly to access all including tombstones
            List<RegisterCheckEntry> entries = checkRegisterDataService.readTeamLeadCheckRegisterLocalReadOnly(username, userId, year, month);

            if (entries == null || entries.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("No entries found for marking single entry %d for %s", entryId, username));
                return ServiceResult.businessError("No entries found", "no_entries_found");
            }

            // Find the entry to mark
            RegisterCheckEntry entryToMark = entries.stream()
                    .filter(e -> e.getEntryId().equals(entryId))
                    .findFirst()
                    .orElse(null);

            if (entryToMark == null) {
                LoggerUtil.warn(this.getClass(), String.format("Entry %d not found for marking as TEAM_FINAL for %s", entryId, username));
                return ServiceResult.businessError("Entry not found", "entry_not_found");
            }

            // Check if already ADMIN_FINAL (immutable)
            if (MergingStatusConstants.ADMIN_FINAL.equals(entryToMark.getAdminSync())) {
                LoggerUtil.info(this.getClass(), String.format("Entry %d is ADMIN_FINAL, cannot be marked as TEAM_FINAL", entryId));
                return ServiceResult.businessError("Entry is ADMIN_FINAL and cannot be modified", "entry_is_admin_final");
            }

            // Mark with TEAM_FINAL status
            String previousStatus = entryToMark.getAdminSync();
            entryToMark.setAdminSync(MergingStatusConstants.TEAM_FINAL);
            LoggerUtil.info(this.getClass(), String.format("Marking entry %d as TEAM_FINAL (was: %s)", entryId, previousStatus));

            // Save updated list
            try {
                checkRegisterDataService.writeTeamLeadCheckRegisterWithSyncAndBackup(username, userId, entries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Successfully marked entry %d as TEAM_FINAL for %s", entryId, username));
                return ServiceResult.success(entryToMark);

            } catch (Exception saveException) {
                LoggerUtil.error(this.getClass(), String.format("Failed to save entry marked as TEAM_FINAL for %s: %s", username, saveException.getMessage()), saveException);
                return ServiceResult.systemError("Failed to save marked entry", "save_failed");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error marking entry %d as TEAM_FINAL for %s: %s", entryId, username, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while marking entry", "system_error");
        }
    }

    /**
     * Update team entry with ServiceResult pattern - ENHANCED
     */
    public ServiceResult<RegisterCheckEntry> updateTeamEntry(String username, Integer userId, RegisterCheckEntry entry, int year, int month) {
        // Validate entry first
        ServiceResult<Void> validation = validateEntryComprehensive(entry);
        if (validation.isFailure()) {
            return ServiceResult.validationError(validation.getErrorMessage(), validation.getErrorCode());
        }

        try {
            // Load existing entries
            ServiceResult<List<RegisterCheckEntry>> loadResult = loadTeamCheckRegister(username, userId, year, month);
            if (loadResult.isFailure()) {
                return ServiceResult.systemError("Failed to load team register for update", "load_failed_for_update");
            }

            List<RegisterCheckEntry> entries = loadResult.getData();
            if (entries == null) {
                entries = new ArrayList<>();
            }

            // Fix any null IDs in existing entries
            repairNullEntryIds(entries);

            // Set status to TEAM_EDITED_{timestamp} for team lead entries
            entry.setAdminSync(MergingStatusConstants.createTeamEditedStatus());

            // Handle ID assignment for new entries
            if (entry.getEntryId() == null) {
                int nextId = generateNextEntryId(entries);
                entry.setEntryId(nextId);
                LoggerUtil.info(this.getClass(), String.format("Generated new ID %d for team lead entry for user %s", nextId, username));
                entries.add(entry);
            } else {
                // For existing entries, remove the old one and add updated
                entries.removeIf(e -> e.getEntryId() != null && e.getEntryId().equals(entry.getEntryId()));
                entries.add(entry);
            }

            // Sort entries gracefully
            ServiceResult<Void> sortResult = sortEntriesGracefully(entries);
            List<String> warnings = new ArrayList<>();
            if (sortResult.isFailure()) {
                warnings.add("Entry updated but sorting failed: " + sortResult.getErrorMessage());
            }

            // Save updated list
            try {
                checkRegisterDataService.writeTeamLeadCheckRegisterWithSyncAndBackup(username, userId, entries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Updated entry %d in team check register for %s", entry.getEntryId(), username));

                if (warnings.isEmpty()) {
                    return ServiceResult.success(entry);
                } else {
                    return ServiceResult.successWithWarnings(entry, warnings);
                }

            } catch (Exception saveException) {
                LoggerUtil.error(this.getClass(), String.format("Failed to save updated team entry for %s: %s", username, saveException.getMessage()), saveException);
                return ServiceResult.systemError("Failed to save updated entry", "save_failed");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating entry in team check register for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while updating entry", "system_error");
        }
    }

    /**
     * Mark entry for deletion with ServiceResult pattern - REFACTORED TO USE TOMBSTONE DELETION
     * Team leads mark entries with TEAM_DELETED_{timestamp} status
     * Tombstone deletion ensures deletions persist across merges
     */
    public ServiceResult<Void> markEntryForDeletion(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Validate inputs
            ServiceResult<Void> inputValidation = validateMarkForDeletionInputs(username, userId, entryId, year, month);
            if (inputValidation.isFailure()) {
                return ServiceResult.validationError(inputValidation.getErrorMessage(), inputValidation.getErrorCode());
            }

            // Load existing entries - note: loadTeamCheckRegister already filters tombstones
            // So we need to load from file directly to access all entries including tombstones
            List<RegisterCheckEntry> entries = checkRegisterDataService.readTeamLeadCheckRegisterLocalReadOnly(username, userId, year, month);

            if (entries == null || entries.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("No entries found to mark for deletion for %s", username));
                return ServiceResult.businessError("No entries found to mark for deletion", "no_entries_found");
            }

            // Find the entry to mark
            RegisterCheckEntry entryToMark = entries.stream()
                    .filter(e -> e.getEntryId().equals(entryId))
                    .findFirst()
                    .orElse(null);

            if (entryToMark == null) {
                LoggerUtil.warn(this.getClass(), String.format("Entry %d not found for deletion for %s", entryId, username));
                return ServiceResult.businessError("Entry not found for deletion", "entry_not_found");
            }

            // Mark with TEAM_DELETED_{timestamp} tombstone status
            entryToMark.setAdminSync(MergingStatusConstants.createTeamDeletedStatus());
            LoggerUtil.info(this.getClass(), String.format("Marked entry %d with deletion status: %s", entryId, entryToMark.getAdminSync()));

            // Save updated list (including tombstone)
            try {
                checkRegisterDataService.writeTeamLeadCheckRegisterWithSyncAndBackup(username, userId, entries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Marked entry %d for deletion in team check register for %s (tombstone: %s)",
                        entryId, username, entryToMark.getAdminSync()));
                return ServiceResult.success();

            } catch (Exception saveException) {
                LoggerUtil.error(this.getClass(), String.format("Failed to save entry marked for deletion for %s: %s", username, saveException.getMessage()), saveException);
                return ServiceResult.systemError("Failed to save deletion mark", "save_failed");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error marking entry for deletion for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while marking for deletion", "system_error");
        }
    }

    /**
     * Save a new or updated user check entry - REFACTORED TO USE ServiceResult
     */
    public ServiceResult<RegisterCheckEntry> saveUserEntry(String username, Integer userId, RegisterCheckEntry entry) {
        // Comprehensive validation
        ServiceResult<Void> validation = validateEntryComprehensive(entry);
        if (validation.isFailure()) {
            return ServiceResult.validationError(validation.getErrorMessage(), validation.getErrorCode());
        }

        // Business rule validation
        ServiceResult<Void> businessValidation = validateUserCanCreateEntry(username, userId);
        if (businessValidation.isFailure()) {
            return ServiceResult.businessError(businessValidation.getErrorMessage(), businessValidation.getErrorCode());
        }

        int year = entry.getDate().getYear();
        int month = entry.getDate().getMonthValue();

        try {
            // Ensure cache is loaded for this month
            LoggerUtil.debug(this.getClass(), String.format("Ensuring cache is loaded for %s - %d/%d", username, month, year));
            registerCheckCacheService.getMonthEntries(username, userId, year, month);

            // Determine if this is a new entry or an update, and set appropriate status
            if (entry.getEntryId() == null) {
                // NEW ENTRY: Generate ID and set USER_INPUT status
                List<RegisterCheckEntry> currentEntries = registerCheckCacheService.getMonthEntries(username, userId, year, month);
                entry.setEntryId(generateNextEntryId(currentEntries));
                entry.setAdminSync(MergingStatusConstants.USER_INPUT);
                LoggerUtil.info(this.getClass(), String.format("New check entry %d created with USER_INPUT status", entry.getEntryId()));
            } else {
                // EXISTING ENTRY: Set USER_EDITED_{timestamp} status for updates
                entry.setAdminSync(MergingStatusConstants.createUserEditedStatus());
                LoggerUtil.info(this.getClass(), String.format("Updating existing check entry %d with USER_EDITED status: %s", entry.getEntryId(), entry.getAdminSync()));
            }

            // Add/update entry using cache (which will write-through to file)
            boolean success;
            RegisterCheckEntry existingEntry = registerCheckCacheService.getEntry(username, userId, entry.getEntryId(), year, month);

            if (existingEntry != null) {
                LoggerUtil.debug(this.getClass(), String.format("Updating existing check entry %d in cache", entry.getEntryId()));
                success = registerCheckCacheService.updateEntry(username, userId, entry);
            } else {
                LoggerUtil.debug(this.getClass(), String.format("Adding new check entry %d to cache", entry.getEntryId()));
                success = registerCheckCacheService.addEntry(username, userId, entry);
            }

            if (!success) {
                return ServiceResult.systemError("Failed to save entry to cache", "cache_save_failed");
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully saved check entry %d for user %s with status %s",
                    entry.getEntryId(), username, entry.getAdminSync()));

            return ServiceResult.success(entry);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving user check entry for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while saving entry", "system_error");
        }
    }

    /**
     * Delete a user check entry - REFACTORED TO USE TOMBSTONE DELETION
     * User can delete their own entries - marks them with USER_DELETED_{timestamp} status
     * Tombstone deletion ensures deletions persist across merges
     */
    public ServiceResult<Void> deleteUserEntry(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Validate inputs
            ServiceResult<Void> inputValidation = validateDeleteUserEntryInputs(username, userId, entryId, year, month);
            if (inputValidation.isFailure()) {
                return ServiceResult.validationError(inputValidation.getErrorMessage(), inputValidation.getErrorCode());
            }

            // Verify entry exists in cache (this will load from file if needed)
            RegisterCheckEntry existingEntry = registerCheckCacheService.getEntry(username, userId, entryId, year, month);

            if (existingEntry == null) {
                return ServiceResult.businessError("Entry not found for deletion", "entry_not_found");
            }

            // Users can now delete any entry (including team lead entries)
            // Mark with USER_DELETED_{timestamp} tombstone status
            RegisterCheckEntry deletedEntry = copyEntry(existingEntry);
            deletedEntry.setAdminSync(MergingStatusConstants.createUserDeletedStatus());

            // Update entry with deletion status using cache (which will write-through to file)
            boolean success = registerCheckCacheService.updateEntry(username, userId, deletedEntry);

            if (!success) {
                return ServiceResult.systemError("Failed to mark entry as deleted in cache", "cache_delete_failed");
            }

            LoggerUtil.info(this.getClass(), String.format("Marked user check entry %d for %s as deleted (tombstone: %s)",
                    entryId, username, deletedEntry.getAdminSync()));
            return ServiceResult.success();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error deleting user check entry %d for %s: %s", entryId, username, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while deleting entry", "system_error");
        }
    }

    /**
     * Reads user check register entries directly without merging with team entries - REFACTORED
     * Used to check if user has entries when initializing the team check register
     */
    public ServiceResult<List<RegisterCheckEntry>> loadUserEntriesDirectly(String username, Integer userId, int year, int month) {
        try {
            // Validate inputs
            ServiceResult<Void> inputValidation = validateLoadEntriesInputs(username, userId, year, month);
            if (inputValidation.isFailure()) {
                return ServiceResult.validationError(inputValidation.getErrorMessage(), inputValidation.getErrorCode());
            }

            LoggerUtil.info(this.getClass(), "Reading user check register directly for " + username);
            List<RegisterCheckEntry> entries = checkRegisterDataService.readUserCheckRegisterLocalReadOnly(username, userId, year, month);

            if (entries == null) {
                entries = new ArrayList<>();
            }

            // Filter out deleted entries (tombstones)
            entries = filterDeletedEntries(entries);

            // Ensure entries are sorted (newest first)
            if (!entries.isEmpty()) {
                ServiceResult<Void> sortResult = sortEntriesGracefully(entries);
                if (sortResult.isFailure()) {
                    List<String> warnings = new ArrayList<>();
                    warnings.add("Entries loaded but sorting failed: " + sortResult.getErrorMessage());
                    return ServiceResult.successWithWarnings(entries, warnings);
                }
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully loaded %d user entries directly for %s - %d/%d (tombstones filtered)",
                    entries.size(), username, year, month));
            return ServiceResult.success(entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading direct check register for user %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while loading user entries", "load_user_entries_system_error");
        }
    }

    /**
     * Save an entry - unified approach with ServiceResult pattern - ENHANCED
     */
    public ServiceResult<RegisterCheckEntry> saveEntry(boolean isTeamLead, String username, Integer userId, RegisterCheckEntry entry) {
        // Early validation
        ServiceResult<Void> validation = validateEntryComprehensive(entry);
        if (validation.isFailure()) {
            return ServiceResult.validationError(validation.getErrorMessage(), validation.getErrorCode());
        }

        try {
            int year = entry.getDate().getYear();
            int month = entry.getDate().getMonthValue();

            if (isTeamLead) {
                // For team leads, use the team entry update method
                return updateTeamEntry(username, userId, entry, year, month);
            } else {
                // For regular users, use the user entry save method
                return saveUserEntry(username, userId, entry);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error saving entry for user %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while saving", "system_error");
        }
    }

    /**
     * Perform full search across all check entries - REFACTORED
     */
    public ServiceResult<List<RegisterCheckEntry>> performFullRegisterSearch(String username, Integer userId, String query) {
        try {
            // Validate inputs
            if (username == null || username.trim().isEmpty()) {
                return ServiceResult.validationError("Username is required for search", "missing_username");
            }
            if (userId == null) {
                return ServiceResult.validationError("User ID is required for search", "missing_user_id");
            }
            if (query == null || query.trim().isEmpty()) {
                return ServiceResult.validationError("Search query is required", "missing_query");
            }

            // This would need to be implemented in SystemAvailabilityService first
            // For now we'll just return an empty list
            LoggerUtil.info(this.getClass(), "Full search for check entries not yet implemented");
            return ServiceResult.successWithWarning(new ArrayList<>(), "Full search functionality not yet implemented");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error performing search for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred during search", "search_system_error");
        }
    }

    // ========================================================================
    // COMPREHENSIVE VALIDATION METHODS
    // ========================================================================

    /**
     * Comprehensive entry validation using ValidationServiceResult
     */
    private ServiceResult<Void> validateEntryComprehensive(RegisterCheckEntry entry) {
        ValidationServiceResult validation = ValidationServiceResult.create()
                .requireNotNull(entry, "Entry", "null_entry");

        if (entry != null) {
            validation
                    .requireNotNull(entry.getDate(), "Date", "missing_date")
                    .validate(() -> entry.getDate() == null || !entry.getDate().isAfter(LocalDate.now()),
                            "Date cannot be in the future", "future_date")
                    .requireNotEmpty(entry.getOmsId(), "OMS ID", "missing_oms_id")
                    .validate(() -> entry.getOmsId() == null || entry.getOmsId().trim().length() <= 50,
                            "OMS ID cannot exceed 50 characters", "oms_id_too_long")
                    .requireNotEmpty(entry.getDesignerName(), "Designer name", "missing_designer_name")
                    .validate(() -> entry.getDesignerName() == null || entry.getDesignerName().trim().length() <= 100,
                            "Designer name cannot exceed 100 characters", "designer_name_too_long")
                    .requireNotNull(entry.getCheckType(), "Check type", "missing_check_type")
                    .requirePositive(entry.getArticleNumbers(), "Article numbers", "missing_article_numbers")
                    .validate(() -> entry.getArticleNumbers() == null || entry.getArticleNumbers() <= 10000,
                            "Article numbers cannot exceed 10,000", "article_numbers_too_high")
                    .requirePositive(entry.getFilesNumbers(), "File numbers", "missing_file_numbers")
                    .validate(() -> entry.getFilesNumbers() == null || entry.getFilesNumbers() <= 10000,
                            "File numbers cannot exceed 10,000", "file_numbers_too_high")
                    .requireNotEmpty(entry.getApprovalStatus(), "Approval status", "missing_approval_status")
                    .validate(() -> entry.getOrderValue() == null || entry.getOrderValue() >= 0,
                            "Order value cannot be negative", "negative_order_value")
                    .validate(() -> entry.getErrorDescription() == null || entry.getErrorDescription().length() <= 500,
                            "Error description cannot exceed 500 characters", "error_description_too_long")
                    .validate(() -> entry.getProductionId() == null || entry.getProductionId().length() <= 50,
                            "Production ID cannot exceed 50 characters", "production_id_too_long");
        }

        return validation.toServiceResult();
    }

    /**
     * Validate inputs for loading entries
     */
    private ServiceResult<Void> validateLoadEntriesInputs(String username, Integer userId, int year, int month) {
        ValidationServiceResult validation = ValidationServiceResult.create()
                .requireNotEmpty(username, "Username", "missing_username")
                .requireNotNull(userId, "User ID", "missing_user_id")
                .validate(year >= 2020 && year <= 2050, "Year must be between 2020 and 2050", "invalid_year")
                .validate(month >= 1 && month <= 12, "Month must be between 1 and 12", "invalid_month");

        return validation.toServiceResult();
    }

    /**
     * Validate inputs for marking entry for deletion
     */
    private ServiceResult<Void> validateMarkForDeletionInputs(String username, Integer userId, Integer entryId, int year, int month) {
        ValidationServiceResult validation = ValidationServiceResult.create()
                .requireNotEmpty(username, "Username", "missing_username")
                .requireNotNull(userId, "User ID", "missing_user_id")
                .requireNotNull(entryId, "Entry ID", "missing_entry_id")
                .requirePositive(entryId, "Entry ID", "invalid_entry_id")
                .validate(year >= 2020 && year <= 2050, "Year must be between 2020 and 2050", "invalid_year")
                .validate(month >= 1 && month <= 12, "Month must be between 1 and 12", "invalid_month");

        return validation.toServiceResult();
    }

    /**
     * Validate inputs for deleting user entry
     */
    private ServiceResult<Void> validateDeleteUserEntryInputs(String username, Integer userId, Integer entryId, int year, int month) {
        return validateMarkForDeletionInputs(username, userId, entryId, year, month);
    }

    // ========================================================================
    // BUSINESS RULE VALIDATION METHODS
    // ========================================================================

    /**
     * Validate that user can create an entry
     */
    private ServiceResult<Void> validateUserCanCreateEntry(String username, Integer userId) {
        try {
            // Check if user exists
            Optional<User> userOpt = userService.getUserById(userId);
            if (userOpt.isEmpty()) {
                return ServiceResult.businessError("User not found", "user_not_found");
            }

            User user = userOpt.get();
            if (!user.getUsername().equals(username)) {
                return ServiceResult.businessError("Username and user ID do not match", "username_userid_mismatch");
            }

            // Additional business rules can be added here
            // For example: check if user has checking permissions, check rate limits, etc.

            return ServiceResult.success();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error validating user can create entry for %s: %s", username, e.getMessage()), e);
            return ServiceResult.systemError("System error during validation", "validation_system_error");
        }
    }

    // ========================================================================
    // HELPER METHODS (UNCHANGED)
    // ========================================================================

    /**
     * Filter out deleted entries (tombstones) from the list
     * Tombstones exist in JSON for merge conflict resolution but should not be displayed or counted
     */
    private List<RegisterCheckEntry> filterDeletedEntries(List<RegisterCheckEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return entries;
        }

        int originalSize = entries.size();
        List<RegisterCheckEntry> filtered = entries.stream()
                .filter(entry -> entry.getAdminSync() == null || !MergingStatusConstants.isDeletedStatus(entry.getAdminSync()))
                .collect(Collectors.toList());

        int deletedCount = originalSize - filtered.size();
        if (deletedCount > 0) {
            LoggerUtil.debug(this.getClass(), String.format("Filtered out %d deleted entries (tombstones)", deletedCount));
        }

        return filtered;
    }

    /**
     * Load and merge user check entries with team lead entries AT LOGIN
     * This merges team lead decisions into user's check register file
     * Renamed from loadAndMergeUserEntries to be more explicit about login usage
     */
    public ServiceResult<List<RegisterCheckEntry>> loadAndMergeUserLoginEntries(String username, Integer userId, int year, int month) {
        try {
            // Step 1: Read user entries (smart fallback already built-in)
            List<RegisterCheckEntry> userEntries = checkRegisterDataService.readUserCheckRegisterLocalReadOnly(username, userId, year, month);

            // Step 2: Read team lead entries from network
            List<RegisterCheckEntry> teamLeadEntries = checkRegisterDataService.readTeamLeadCheckRegisterFromNetworkOnly(username, userId, year, month);

            LoggerUtil.debug(this.getClass(), String.format("Merging check register for %s - %d/%d: %d user entries + %d team lead entries", username, year, month, userEntries.size(), teamLeadEntries.size()));

            // Step 3: Merge entries based on status using merge rules
            List<RegisterCheckEntry> mergedEntries = mergeEntries(userEntries, teamLeadEntries);

            // Step 4: Filter out deleted entries (tombstones) from the merged result
            List<RegisterCheckEntry> activeEntries = filterDeletedEntries(mergedEntries);

            // Step 5: Sort entries before saving (newest first)
            ServiceResult<Void> sortResult = sortEntriesGracefully(mergedEntries); // Sort all including tombstones for file
            List<String> warnings = new ArrayList<>();
            if (sortResult.isFailure()) {
                warnings.add("Entries merged but sorting failed: " + sortResult.getErrorMessage());
            }

            // Step 6: Write back to file - THIS PREPARES DATA FOR CACHE (includes tombstones for merge resolution)
            try {
                checkRegisterDataService.writeUserCheckRegisterWithSyncAndBackup(username, userId, mergedEntries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Successfully merged and saved %d check register entries for %s - %d/%d (%d active, %d tombstones)",
                        mergedEntries.size(), username, year, month, activeEntries.size(), mergedEntries.size() - activeEntries.size()));

                // Clear cache so it loads fresh merged data on next access
                try {
                    registerCheckCacheService.clearMonth(username, year, month);
                    LoggerUtil.debug(this.getClass(), String.format("Cleared check register cache for %s - %d/%d after login merge", username, year, month));
                } catch (Exception cacheException) {
                    LoggerUtil.warn(this.getClass(), String.format("Failed to clear cache after login merge: %s", cacheException.getMessage()));
                    // Not critical - cache will eventually refresh
                }

                // Return only active entries (tombstones filtered out)
                if (warnings.isEmpty()) {
                    return ServiceResult.success(activeEntries);
                } else {
                    return ServiceResult.successWithWarnings(activeEntries, warnings);
                }

            } catch (Exception saveException) {
                LoggerUtil.error(this.getClass(), String.format("Failed to save merged entries for %s: %s", username, saveException.getMessage()), saveException);
                // Return the active entries even if save failed, but with warning
                warnings.add("Entries merged but save failed: " + saveException.getMessage());
                return ServiceResult.successWithWarnings(activeEntries, warnings);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading and merging user entries for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return ServiceResult.systemError("System error occurred while merging entries", "merge_system_error");
        }
    }

    // Add a simple wrapper method for current month (like RegisterMergeService):
    public void performCheckRegisterLoginMerge(String username) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Starting check register login merge for %s", username));

            // Get user ID
            Optional<User> userOpt = userService.getUserByUsername(username);
            if (userOpt.isEmpty()) {
                ServiceResult.notFound("User not found: " + username, "user_not_found");
                return;
            }

            User user = userOpt.get();
            LocalDate currentDate = LocalDate.now();
            int year = currentDate.getYear();
            int month = currentDate.getMonthValue();

            // Call the main merge method
            ServiceResult<List<RegisterCheckEntry>> result = loadAndMergeUserLoginEntries(username, user.getUserId(), year, month);

            if (result.isSuccess()) {
                List<RegisterCheckEntry> mergedEntries = result.getData();
                String summary = String.format("Check register login merge completed for %s - %d/%d (%d entries processed)", username, year, month, mergedEntries.size());
                LoggerUtil.info(this.getClass(), summary);
                ServiceResult.success(summary);
            } else {
                ServiceResult.businessError("Check register login merge failed: " + result.getErrorMessage(), "merge_failed");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error in check register login merge for %s: %s", username, e.getMessage()), e);
            ServiceResult.systemError("Unexpected error during check register login merge", "merge_system_error");
        }
    }

    /**
     * Creates a copy of an entry with status set to USER_INPUT
     */
    private RegisterCheckEntry copyEntryWithUserInput(RegisterCheckEntry original) {
        return RegisterCheckEntry.builder()
                .entryId(original.getEntryId())
                .date(original.getDate())
                .omsId(original.getOmsId())
                .designerName(original.getDesignerName())
                .productionId(original.getProductionId())
                .checkType(original.getCheckType())
                .articleNumbers(original.getArticleNumbers())
                .filesNumbers(original.getFilesNumbers())
                .errorDescription(original.getErrorDescription())
                .approvalStatus(original.getApprovalStatus())
                .orderValue(original.getOrderValue())
                .adminSync(MergingStatusConstants.USER_INPUT)
                .build();
    }

    /**
     * Helper method to determine if an entry should be skipped during marking
     */
    private boolean shouldSkipEntry(RegisterCheckEntry entry) {
        if (entry.getAdminSync() == null) {
            return false; // Null status entries should be processed
        }

        // Skip entries with team edited status (timestamped), admin final, or team final
        return MergingStatusConstants.isTeamEditedStatus(entry.getAdminSync()) ||
                MergingStatusConstants.isFinalStatus(entry.getAdminSync());
    }

    /**
     * Repair any null entry IDs in the given list
     */
    private void repairNullEntryIds(List<RegisterCheckEntry> entries) {
        if (entries == null || entries.isEmpty()) return;

        List<RegisterCheckEntry> nullIdEntries = entries.stream().filter(e -> e.getEntryId() == null).toList();

        if (nullIdEntries.isEmpty()) return;

        LoggerUtil.info(this.getClass(), "Found " + nullIdEntries.size() + " entries with null IDs, repairing...");

        int maxId = entries.stream().filter(e -> e.getEntryId() != null).mapToInt(RegisterCheckEntry::getEntryId).max().orElse(0);

        int nextId = maxId + 1;
        for (RegisterCheckEntry entry : nullIdEntries) {
            entry.setEntryId(nextId++);
            LoggerUtil.info(this.getClass(), "Assigned ID " + entry.getEntryId() + " to entry with null ID");
        }
    }

    /**
     * Helper method to create a deep copy of an entry
     */
    private RegisterCheckEntry copyEntry(RegisterCheckEntry source) {
        if (source == null) return null;

        return RegisterCheckEntry.builder()
                .entryId(source.getEntryId())
                .date(source.getDate())
                .omsId(source.getOmsId())
                .designerName(source.getDesignerName())
                .productionId(source.getProductionId())
                .checkType(source.getCheckType())
                .articleNumbers(source.getArticleNumbers())
                .filesNumbers(source.getFilesNumbers())
                .errorDescription(source.getErrorDescription())
                .approvalStatus(source.getApprovalStatus())
                .orderValue(source.getOrderValue())
                .adminSync(source.getAdminSync())
                .build();
    }

    /**
     * Create a new check entry with proper status based on user type
     */
    public RegisterCheckEntry createEntry(boolean isTeamLead, Integer entryId, LocalDate date, String omsId, String productionId, String designerName,
                                          String checkType, Integer articleNumbers, Integer filesNumbers, String errorDescription, String approvalStatus, Double orderValue) {

        RegisterCheckEntry entry = RegisterCheckEntry.builder()
                .entryId(entryId) // Will be set later if null
                .date(date)
                .omsId(trimIfNotNull(omsId))
                .productionId(trimIfNotNull(productionId))
                .designerName(trimIfNotNull(designerName))
                .checkType(checkType)
                .articleNumbers(articleNumbers)
                .filesNumbers(filesNumbers)
                .errorDescription(trimIfNotNull(errorDescription))
                .approvalStatus(approvalStatus)
                .orderValue(orderValue)
                .build();

        // Set status based on user type
        if (isTeamLead) {
            entry.setAdminSync(MergingStatusConstants.createTeamEditedStatus());
        } else {
            entry.setAdminSync(MergingStatusConstants.USER_INPUT);
        }
        return entry;
    }

    /**
     * Trim string if not null
     */
    private String trimIfNotNull(String value) {
        return value != null ? value.trim() : null;
    }

    /**
     * Merge user check entries with team lead entries using Universal Merge Engine
     * REFACTORED to use UniversalMergeEngine with timestamp-based conflict resolution
     */
    private List<RegisterCheckEntry> mergeEntries(List<RegisterCheckEntry> userEntries, List<RegisterCheckEntry> teamLeadEntries) {
        LoggerUtil.debug(this.getClass(), String.format(
                "Universal merge for check register: %d user entries, %d team lead entries",
                userEntries.size(), teamLeadEntries.size()));

        // Create maps for quick lookup
        Map<Integer, RegisterCheckEntry> teamLeadEntriesMap = teamLeadEntries.stream()
                .collect(Collectors.toMap(RegisterCheckEntry::getEntryId, entry -> entry, (e1, e2) -> e2));

        Map<Integer, RegisterCheckEntry> userEntriesMap = userEntries.stream()
                .collect(Collectors.toMap(RegisterCheckEntry::getEntryId, entry -> entry, (e1, e2) -> e2));

        // Collect all entry IDs from both sources
        Set<Integer> allEntryIds = new HashSet<>();
        userEntries.forEach(entry -> allEntryIds.add(entry.getEntryId()));
        teamLeadEntries.forEach(entry -> allEntryIds.add(entry.getEntryId()));

        // Apply Universal Merge Engine to all entries
        List<RegisterCheckEntry> mergedEntries = new ArrayList<>();
        int mergeCount = 0;
        int deleteCount = 0;

        for (Integer entryId : allEntryIds) {
            RegisterCheckEntry userEntry = userEntriesMap.get(entryId);
            RegisterCheckEntry teamLeadEntry = teamLeadEntriesMap.get(entryId);

            try {
                // Use GenericEntityWrapper via CheckRegisterWrapperFactory
                GenericEntityWrapper<RegisterCheckEntry> userWrapper = CheckRegisterWrapperFactory.createWrapperSafe(userEntry);
                GenericEntityWrapper<RegisterCheckEntry> teamLeadWrapper = CheckRegisterWrapperFactory.createWrapperSafe(teamLeadEntry);

                // Apply Universal Merge Engine
                GenericEntityWrapper<RegisterCheckEntry> resultWrapper = UniversalMergeEngine.merge(
                        userWrapper, teamLeadWrapper, EntityType.CHECK_REGISTER);

                RegisterCheckEntry mergedEntry = resultWrapper != null ? resultWrapper.getEntity() : null;

                // Only add non-null entries (null means entry should be removed)
                if (mergedEntry != null) {
                    mergedEntries.add(mergedEntry);
                    mergeCount++;
                } else {
                    deleteCount++;
                    LoggerUtil.debug(this.getClass(), String.format("Entry %d deleted during merge", entryId));
                }
            } catch (Exception e) {
                LoggerUtil.warn(this.getClass(), String.format("Error merging entry %d: %s", entryId, e.getMessage()));
                // Fallback: keep user entry if merge fails
                if (userEntry != null) {
                    mergedEntries.add(userEntry);
                }
            }
        }

        LoggerUtil.debug(this.getClass(), String.format(
                "Merge statistics: %d merged, %d deleted", mergeCount, deleteCount));

        return mergedEntries;
    }

    /**
     * Sort entries consistently with the newest first
     * Returns ServiceResult for graceful error handling
     */
    private ServiceResult<Void> sortEntriesGracefully(List<RegisterCheckEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return ServiceResult.success();
        }

        try {
            entries.sort(Comparator.comparing(RegisterCheckEntry::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(RegisterCheckEntry::getEntryId, Comparator.nullsLast(Comparator.reverseOrder())));

            LoggerUtil.debug(this.getClass(), "Successfully sorted " + entries.size() + " entries with newest first");
            return ServiceResult.success();
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Failed to sort entries: " + e.getMessage());
            return ServiceResult.systemError("Failed to sort entries", "sort_failed");
        }
    }

    /**
     * Generate next entry ID consistently
     */
    public int generateNextEntryId(List<RegisterCheckEntry> entries) {
        return entries.stream().filter(e -> e.getEntryId() != null).mapToInt(RegisterCheckEntry::getEntryId).max().orElse(0) + 1;
    }
}