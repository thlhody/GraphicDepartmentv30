package com.ctgraphdep.service;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.enums.CheckRegisterMergeRule;
import com.ctgraphdep.enums.CheckingStatus;
import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified service for managing check register entries
 * Handles both user and team lead operations
 */
@Service
@Getter
public class CheckRegisterService {

    private final DataAccessService dataAccessService;
    private final UserService userService;

    @Autowired
    public CheckRegisterService(DataAccessService dataAccessService, UserService userService) {
        this.dataAccessService = dataAccessService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Get all users with checking-related roles
     * Used by team leads to select users
     */
    public List<User> getAllCheckUsers() {
        try {
            List<User> allUsers = userService.getAllUsers();
            return allUsers.stream().filter(user -> user.getRole().contains(SecurityConstants.ROLE_CHECKING)).collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting check users: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get the current authenticated user
     * @return Current user or null if not authenticated
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        String username = auth.getName(); // Get username correctly using getName()
        return userService.getUserByUsername(username).orElse(null); // Unwrap the Optional
    }


    /**
     * Load check entries for a specific month
     * User can only access their own entries, team leads can access any entries
     */
    public List<RegisterCheckEntry> loadMonthEntries(String username, Integer userId, int year, int month) {
        try {
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

            // Check if current user is accessing their own data
            boolean isAccessingOwnData = currentUsername.equals(username);

            // Check if current user is a team lead
            boolean isTeamLead = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals(SecurityConstants.ROLE_TL_CHECKING) || a.getAuthority().equals(SecurityConstants.ROLE_ADMIN));

            List<RegisterCheckEntry> result;

            // Regular user accessing their own data
            if (isAccessingOwnData) {
                result = loadAndMergeUserEntries(username, userId, year, month);
            }
            // Team lead accessing another user's data
            else if (isTeamLead) {
                result = loadTeamCheckRegister(username, userId, year, month);
            }
            // Unauthorized access attempt
            else {
                LoggerUtil.warn(this.getClass(), String.format("Unauthorized attempt by %s to access %s's check register", currentUsername, username));
                result = new ArrayList<>();
            }

            // Ensure entries are sorted with the newest first
            sortEntries(result);
            LoggerUtil.info(this.getClass(), "After sorting: " + (!result.isEmpty() ? "First entry date: " + result.get(0).getDate() : "No entries"));
            return result;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading check entries: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Load and merge user entries with team lead entries
     * This is what regular users see when they access their check register
     */
    private List<RegisterCheckEntry> loadAndMergeUserEntries(String username, Integer userId, int year, int month) {
        try {
            // First try to read local entries
            List<RegisterCheckEntry> userEntries = dataAccessService.readUserCheckRegister(username, userId, year, month);

            // Check if local entries are empty
            boolean localEntriesEmpty = (userEntries == null || userEntries.isEmpty());

            // If local is empty and network is available, check network first to prevent data loss
            if (localEntriesEmpty && dataAccessService.isNetworkAvailable()) {
                try {
                    // Check if there are entries on the network before we overwrite them
                    List<RegisterCheckEntry> networkEntries = dataAccessService.readCheckRegisterReadOnly(username, userId, year, month);
                    if (networkEntries != null && !networkEntries.isEmpty()) {
                        // If network has entries but local is empty, use network entries as base
                        LoggerUtil.info(this.getClass(), "Local check register entries missing but found entries on network. Restoring data.");
                        userEntries = networkEntries;
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Error reading network check register: " + e.getMessage());
                }
            }

            // Ensure userEntries is not null
            if (userEntries == null) {
                userEntries = new ArrayList<>();
            }

            // Try to read team lead entries if network is available
            List<RegisterCheckEntry> teamLeadEntries = new ArrayList<>();
            if (dataAccessService.isNetworkAvailable()) {
                try {
                    teamLeadEntries = dataAccessService.readLocalLeadCheckRegister(username, userId, year, month);
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Team lead check register not found: " + e.getMessage());
                }
            }

            // Merge entries based on status
            List<RegisterCheckEntry> mergedEntries = mergeEntries(userEntries, teamLeadEntries);

            // Sort entries before saving (newest first)
            sortEntries(mergedEntries);

            // Write back to ensure files are in sync
            dataAccessService.writeUserCheckRegister(username, userId, mergedEntries, year, month);

            return mergedEntries;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading and merging user entries: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Load team check register for a specific user
     * Enhanced to check for and incorporate newer user entries and repair null IDs
     */
    public List<RegisterCheckEntry> loadTeamCheckRegister(String username, Integer userId, int year, int month) {
        try {
            // First get the team lead entries
            List<RegisterCheckEntry> teamEntries = dataAccessService.readLocalLeadCheckRegister(username, userId, year, month);

            // Initialize if null
            if (teamEntries == null) {
                teamEntries = new ArrayList<>();
            }

            // Fix any null IDs in team entries first
            repairNullEntryIds(teamEntries);

            if (teamEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), "Team register for " + username + " appears outdated or empty, checking for user entries");

                // Check if user has entries that the team register doesn't reflect
                List<RegisterCheckEntry> userEntries = loadUserEntriesDirectly(username, userId, year, month);

                // Fix any null IDs in user entries
                if (userEntries != null && !userEntries.isEmpty()) {
                    repairNullEntryIds(userEntries);

                    // Check if we already have this initialization in progress
                    if (teamEntries.isEmpty()) {
                        // If team entries is empty, this is a new initialization
                        LoggerUtil.info(this.getClass(), "Found " + userEntries.size() + " user entries but team register is empty. Initializing.");

                        // Create copies of all entries with status set to CHECKING_INPUT initially
                        List<RegisterCheckEntry> initializedEntries = userEntries.stream().map(this::copyEntryWithCheckingInput).collect(Collectors.toList());

                        // Sort entries before saving (newest first)
                        sortEntries(initializedEntries);

                        // Save and return the initialized entries
                        saveTeamCheckRegister(username, userId, initializedEntries, year, month);
                        return initializedEntries;
                    } else {
                        // Try to merge user entries with team entries that don't already exist
                        LoggerUtil.info(this.getClass(), "Found user entries that might not be in team register. Attempting to merge.");

                        // Get the set of existing entry IDs in team entries
                        Set<Integer> existingIds = teamEntries.stream().map(RegisterCheckEntry::getEntryId).filter(Objects::nonNull).collect(Collectors.toSet());

                        // Add any user entries that don't already exist in team entries
                        for (RegisterCheckEntry userEntry : userEntries) {
                            if (userEntry.getEntryId() != null && !existingIds.contains(userEntry.getEntryId())) {
                                // This is a new entry from the user that isn't in team entries
                                RegisterCheckEntry copyEntry = copyEntryWithCheckingInput(userEntry);
                                teamEntries.add(copyEntry);
                                LoggerUtil.info(this.getClass(), "Added missing entry ID " + userEntry.getEntryId() + " to team register for " + username);
                            }
                        }

                        // Sort entries before saving (newest first)
                        sortEntries(teamEntries);

                        // Save the updated entries
                        saveTeamCheckRegister(username, userId, teamEntries, year, month);
                    }
                }
            }

            // Ensure entries are sorted with the newest first
            sortEntries(teamEntries);
            return teamEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading team check register for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Initialize a team check register from a user's check register
     * Copies entries and sets status to CHECKING_INPUT initially
     * If user register is empty, creates an empty team register
     */
    public List<RegisterCheckEntry> initializeTeamCheckRegister(String username, Integer userId, int year, int month) {
        try {
            // Get current authenticated user (the team lead)
            User teamLeadUser = getCurrentUser();
            if (teamLeadUser == null) {
                throw new IllegalStateException("No authenticated user found");
            }

            LoggerUtil.info(this.getClass(), String.format("Team lead %s initializing check register for %s", teamLeadUser.getUsername(), username));

            // First try to read the user's check register from network (preferred source)
            List<RegisterCheckEntry> userEntries = null;
            if (dataAccessService.isNetworkAvailable()) {
                try {
                    LoggerUtil.info(this.getClass(), "Reading network check register for user " + username + " by " + teamLeadUser.getUsername());
                    userEntries = dataAccessService.readCheckRegisterReadOnly(username, userId, year, month);
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Error reading network check register: " + e.getMessage());
                }
            }

            // If not found on network or network unavailable, try local file as fallback
            if (userEntries == null || userEntries.isEmpty()) {
                try {
                    LoggerUtil.info(this.getClass(), "Reading local check register for user " + username);
                    userEntries = dataAccessService.readUserCheckRegister(username, userId, year, month);
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Error reading local check register: " + e.getMessage());
                }
            }

            // If still no entries found, create an empty team register
            if (userEntries == null || userEntries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No entries found in user check register for %s - %d/%d, creating empty team register", username, year, month));
                // Create empty team register - crucially, this uses the team lead's credentials
                saveTeamCheckRegister(username, userId, new ArrayList<>(), year, month);
                return new ArrayList<>();
            }

            // Create copies of all entries with status set to CHECKING_INPUT initially
            List<RegisterCheckEntry> teamEntries = userEntries.stream().map(this::copyEntryWithCheckingInput).collect(Collectors.toList());

            // Sort entries (newest first)
            sortEntries(teamEntries);

            // Save the team check register
            saveTeamCheckRegister(username, userId, teamEntries, year, month);
            LoggerUtil.info(this.getClass(), String.format("Initialized team check register for %s with %d entries", username, teamEntries.size()));
            return teamEntries;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error initializing team check register for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Creates a copy of an entry with status set to CHECKING_INPUT
     */
    private RegisterCheckEntry copyEntryWithCheckingInput(RegisterCheckEntry original) {
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
                .adminSync(CheckingStatus.CHECKING_INPUT.name())
                .build();
    }

    /**
     * Mark all entries in the team check register as TL_CHECK_DONE
     * Used after team lead completes review
     */
    public List<RegisterCheckEntry> markAllEntriesAsChecked(String username, Integer userId, int year, int month) {
        try {
            // Load existing team register entries
            List<RegisterCheckEntry> entries = loadTeamCheckRegister(username, userId, year, month);

            if (entries.isEmpty()) {
                LoggerUtil.info(this.getClass(), String.format("No entries found to mark as checked for %s - %d/%d", username, year, month));
                return entries;
            }

            // Update status of all entries to TL_CHECK_DONE
            List<RegisterCheckEntry> updatedEntries = entries.stream().map(entry -> {
                        // Skip entries already marked as TL_EDITED, TL_BLANK, or ADMIN_DONE
                        if (entry.getAdminSync().equals(CheckingStatus.TL_EDITED.name()) || entry.getAdminSync().equals(CheckingStatus.TL_BLANK.name()) ||
                                entry.getAdminSync().equals(CheckingStatus.ADMIN_DONE.name())) {
                            return entry;
                        }

                        // Mark all other entries as TL_CHECK_DONE
                        RegisterCheckEntry updated = copyEntry(entry);
                        updated.setAdminSync(CheckingStatus.TL_CHECK_DONE.name());
                        return updated;
                    }).collect(Collectors.toList());

            // Save updated entries
            saveTeamCheckRegister(username, userId, updatedEntries, year, month);
            LoggerUtil.info(this.getClass(), String.format("Marked all entries as checked for %s - %d/%d", username, year, month));

            return updatedEntries;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error marking entries as checked for %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to mark entries as checked", e);
        }
    }

    /**
     * Saves the team check register
     * This method accesses team lead check register with the team lead's credentials
     */
    public void saveTeamCheckRegister(String username, Integer userId, List<RegisterCheckEntry> entries, int year, int month) {
        try {
            dataAccessService.writeLocalTeamCheckRegister(username, userId, entries, year, month);
            LoggerUtil.info(this.getClass(), String.format("Saved team check register for %s with %d entries", username, entries.size()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving team check register for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Updates a single entry in the team check register
     * Fixed to properly handle new entries created by team leads
     */
    public void updateTeamEntry(String username, Integer userId, RegisterCheckEntry entry, int year, int month) {
        try {
            // Load existing entries
            List<RegisterCheckEntry> entries = loadTeamCheckRegister(username, userId, year, month);

            // If no existing entries, create a new list
            if (entries == null) {
                entries = new ArrayList<>();
            }

            // First, repair any null IDs in existing entries
            repairNullEntryIds(entries);

            // Set status to TL_EDITED for team lead entries
            entry.setAdminSync(CheckingStatus.TL_EDITED.name());

            // If entry is new (has null ID), we need to assign an ID
            if (entry.getEntryId() == null) {
                // Generate next entry ID based on existing entries
                int maxId = entries.stream().filter(e -> e.getEntryId() != null).mapToInt(RegisterCheckEntry::getEntryId).max().orElse(0);

                int nextId = maxId + 1;
                entry.setEntryId(nextId);
                LoggerUtil.info(this.getClass(), String.format("Generated new ID %d for team lead entry for user %s", nextId, username));

                // For new entries, just add them to the list
                entries.add(entry);
            } else {
                // For existing entries, remove the old one first
                entries.removeIf(e -> e.getEntryId() != null && e.getEntryId().equals(entry.getEntryId()));
                // Then add the updated entry
                entries.add(entry);
            }

            // Save updated list
            saveTeamCheckRegister(username, userId, entries, year, month);
            LoggerUtil.info(this.getClass(), String.format("Updated entry %d in team check register for %s", entry.getEntryId(), username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating entry in team check register for %s: %s", username, e.getMessage()), e);
        }
    }

    /**
     * Repair any null entry IDs in the given list
     * @param entries The list of entries to repair
     */
    private void repairNullEntryIds(List<RegisterCheckEntry> entries) {
        if (entries == null || entries.isEmpty()) return;

        // Find entries with null IDs
        List<RegisterCheckEntry> nullIdEntries = entries.stream().filter(e -> e.getEntryId() == null).toList();
        if (nullIdEntries.isEmpty()) return;
        LoggerUtil.info(this.getClass(), "Found " + nullIdEntries.size() + " entries with null IDs, repairing...");

        // Find max ID in existing entries
        int maxId = entries.stream().filter(e -> e.getEntryId() != null).mapToInt(RegisterCheckEntry::getEntryId).max().orElse(0);

        // Assign new IDs to entries with null IDs
        int nextId = maxId + 1;
        for (RegisterCheckEntry entry : nullIdEntries) {
            entry.setEntryId(nextId++);
            LoggerUtil.info(this.getClass(), "Assigned ID " + entry.getEntryId() + " to entry with null ID");
        }
    }

    /**
     * Marks an entry for deletion by setting status to TL_BLANK
     */
    public void markEntryForDeletion(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Load existing entries
            List<RegisterCheckEntry> entries = loadTeamCheckRegister(username, userId, year, month);

            if (entries == null || entries.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("No entries found to mark for deletion for %s", username));
                return;
            }

            // Find the entry to mark
            RegisterCheckEntry entryToMark = entries.stream().filter(e -> e.getEntryId().equals(entryId)).findFirst().orElse(null);

            if (entryToMark == null) {
                // Create a blank entry with the ID and TL_BLANK status
                RegisterCheckEntry blankEntry = RegisterCheckEntry.builder().entryId(entryId).adminSync(CheckingStatus.TL_BLANK.name()).date(LocalDate.now()).build();
                entries.add(blankEntry);
            } else {
                // Set existing entry to TL_BLANK
                entryToMark.setAdminSync(CheckingStatus.TL_BLANK.name());
            }

            // Save updated list
            saveTeamCheckRegister(username, userId, entries, year, month);
            LoggerUtil.info(this.getClass(), String.format("Marked entry %d for deletion in team check register for %s", entryId, username));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error marking entry for deletion for %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Save a new or updated user check entry
     */
    public void saveUserEntry(String username, Integer userId, RegisterCheckEntry entry) {
        validateEntry(entry);

        // Set initial state for new entries
        entry.setAdminSync(CheckingStatus.CHECKING_INPUT.name());

        int year = entry.getDate().getYear();
        int month = entry.getDate().getMonthValue();

        try {
            // Load existing entries
            List<RegisterCheckEntry> entries = dataAccessService.readUserCheckRegister(username, userId, year, month);
            if (entries == null) entries = new ArrayList<>();

            // Fix any existing entries with null IDs first before adding/updating
            fixNullEntryIds(entries);

            // Update or add entry
            if (entry.getEntryId() == null) {
                entry.setEntryId(generateNextEntryId(entries));
                entries.add(entry);
            } else {
                entries.removeIf(e -> e.getEntryId().equals(entry.getEntryId()));
                entries.add(entry);
            }

            // Sort entries by date (descending) and ID (descending) with null-safety
            try {
                // First, remove any entries with null dates or IDs (shouldn't happen but just in case)
                entries.removeIf(e -> e.getDate() == null || e.getEntryId() == null);

                // Now sort the valid entries
                entries.sort(Comparator.comparing(RegisterCheckEntry::getDate).reversed().thenComparing(RegisterCheckEntry::getEntryId, Comparator.reverseOrder()));
            } catch (NullPointerException e) {
                // If sorting fails due to nulls, log the error and resort to a simpler sort
                LoggerUtil.warn(this.getClass(), "Error during sort, falling back to simple sort: " + e.getMessage());

                // Sort just by ID as fallback (safer)
                entries.sort((e1, e2) -> {
                    if (e1.getEntryId() == null || e2.getEntryId() == null) {
                        return 0; // Consider null IDs equal to avoid errors
                    }
                    return e2.getEntryId().compareTo(e1.getEntryId()); // Descending
                });
            }

            // Save and sync
            dataAccessService.writeUserCheckRegister(username, userId, entries, year, month);
            LoggerUtil.info(this.getClass(), String.format("Successfully saved check entry for user %s with ID %d", username, entry.getEntryId()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving check entry for user %s: %s", username, e.getMessage()));
        }
    }

    /**
     * Delete a user check entry
     * User can only delete their own entries
     */
    public void deleteUserEntry(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Load existing entries
            List<RegisterCheckEntry> entries = dataAccessService.readUserCheckRegister(username, userId, year, month);
            if (entries != null) {
                // Verify entry exists and belongs to user
                boolean entryExists = entries.stream().anyMatch(e -> e.getEntryId().equals(entryId));

                if (!entryExists) {
                    LoggerUtil.error(this.getClass(),"Check entry not found or access denied");
                }

                // Remove entry
                entries.removeIf(entry -> entry.getEntryId().equals(entryId));

                // Save and sync
                dataAccessService.writeUserCheckRegister(username, userId, entries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Deleted check entry %d for user %s", entryId, username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error deleting check entry %d for user %s: %s", entryId, username, e.getMessage()));
        }
    }

    /**
     * Merge user check entries with team lead entries
     */
    private List<RegisterCheckEntry> mergeEntries(List<RegisterCheckEntry> userEntries, List<RegisterCheckEntry> teamLeadEntries) {
        // Create maps for quick lookup
        Map<Integer, RegisterCheckEntry> teamLeadEntriesMap = teamLeadEntries.stream()
                .collect(Collectors.toMap(RegisterCheckEntry::getEntryId, entry -> entry, (e1, e2) -> e2));

        Map<Integer, RegisterCheckEntry> userEntriesMap = userEntries.stream()
                .collect(Collectors.toMap(RegisterCheckEntry::getEntryId, entry -> entry, (e1, e2) -> e2));

        // Collect all entry IDs from both sources
        Set<Integer> allEntryIds = new HashSet<>();
        userEntries.forEach(entry -> allEntryIds.add(entry.getEntryId()));
        teamLeadEntries.forEach(entry -> allEntryIds.add(entry.getEntryId()));

        // Apply merge rules to all entries
        List<RegisterCheckEntry> mergedEntries = new ArrayList<>();
        for (Integer entryId : allEntryIds) {
            RegisterCheckEntry userEntry = userEntriesMap.get(entryId);
            RegisterCheckEntry teamLeadEntry = teamLeadEntriesMap.get(entryId);
            RegisterCheckEntry mergedEntry = CheckRegisterMergeRule.apply(userEntry, teamLeadEntry);

            // Only add non-null entries (null means entry should be removed)
            if (mergedEntry != null) {
                mergedEntries.add(mergedEntry);
            }
        }

        return mergedEntries;
    }

    /**
     * Reads user check register entries directly without merging with team entries
     * Used to check if user has entries when initializing the team check register
     */
    public List<RegisterCheckEntry> loadUserEntriesDirectly(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.info(this.getClass(), "Reading user check register directly for " + username);
            List<RegisterCheckEntry> entries = dataAccessService.readUserCheckRegister(username, userId, year, month);

            // Ensure entries are sorted (newest first)
            if (entries != null && !entries.isEmpty()) {
                sortEntries(entries);
            }

            return entries;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading direct check register for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }


    /**
     * Validate check entry for required fields
     */
    private void validateEntry(RegisterCheckEntry entry) {
        if (entry == null) {
            LoggerUtil.error(this.getClass(),"Entry cannot be null, null_entry");
            throw new RegisterValidationException("Entry cannot be null", "null_entry");
        }
        validateField(entry.getDate(), "Date", "missing_date");
        validateField(entry.getOmsId(), "OMS ID", "missing_oms_id");
        validateField(entry.getDesignerName(), "Designer name", "missing_designer_name");
        validateField(entry.getCheckType(), "Check type", "missing_check_type");
        validateField(entry.getArticleNumbers(), "Article numbers", "missing_article_numbers");
        validateField(entry.getFilesNumbers(), "File numbers", "missing_file_numbers");
        validateField(entry.getApprovalStatus(), "Approval status", "missing_approval_status");

        // Log all values for debugging
        LoggerUtil.debug(this.getClass(), String.format("Validating entry - Date: %s, OmsId: %s, DesignerName: %s, CheckType: %s, " + "ArticleNumbers: %s, FilesNumbers: %s, ApprovalStatus: %s, EntryId: %s",
                entry.getDate(), entry.getOmsId(), entry.getDesignerName(), entry.getCheckType(), entry.getArticleNumbers(), entry.getFilesNumbers(), entry.getApprovalStatus(), entry.getEntryId()));
    }

    private void validateField(Object field, String fieldName, String errorCode) {
        if (field == null) {
            throw new RegisterValidationException(fieldName + " is required", errorCode);
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
     * Find and fix any entries with null IDs in the list
     * @param entries List of entries to fix
     */
    private void fixNullEntryIds(List<RegisterCheckEntry> entries) {
        if (entries == null || entries.isEmpty()) return;

        // Find the maximum ID currently in use
        int maxId = entries.stream().filter(e -> e.getEntryId() != null).mapToInt(RegisterCheckEntry::getEntryId).max().orElse(0);

        // Fix any entries with null IDs
        int nextId = maxId + 1;
        for (RegisterCheckEntry entry : entries) {
            if (entry.getEntryId() == null) {
                entry.setEntryId(nextId++);
                LoggerUtil.info(this.getClass(), String.format("Fixed null ID in existing entry, assigned ID %d", entry.getEntryId()));
            }
        }
    }


    /**
     * Create a new check entry with proper status based on user type
     * @param isTeamLead Whether the entry is being created by a team lead
     * @param entryId Entry ID (null for new entries)
     * @param date Entry date
     * @param omsId OMS ID
     * @param productionId Production ID
     * @param designerName Designer name
     * @param checkType Check type
     * @param articleNumbers Number of articles
     * @param filesNumbers Number of files
     * @param errorDescription Error description
     * @param approvalStatus Approval status
     * @param orderValue Order value
     * @return Created RegisterCheckEntry with appropriate status
     */
    public RegisterCheckEntry createEntry(boolean isTeamLead, Integer entryId, LocalDate date, String omsId, String productionId, String designerName,
                                          String checkType, Integer articleNumbers, Integer filesNumbers, String errorDescription, String approvalStatus, Double orderValue) {
        // Validate fields first
        validateField(date, "Date", "missing_date");
        validateField(omsId, "OMS ID", "missing_oms_id");
        validateField(designerName, "Designer name", "missing_designer_name");
        validateField(checkType, "Check type", "missing_check_type");
        validateField(articleNumbers, "Article numbers", "missing_article_numbers");
        validateField(filesNumbers, "File numbers", "missing_file_numbers");
        validateField(approvalStatus, "Approval status", "missing_approval_status");

        // Build entry with proper status
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
            entry.setAdminSync(CheckingStatus.TL_EDITED.name());
        } else {
            entry.setAdminSync(CheckingStatus.CHECKING_INPUT.name());
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
     * Save an entry - unified approach for both user and team lead entries
     */
    public void saveEntry(boolean isTeamLead, String username, Integer userId, RegisterCheckEntry entry) {
        try {
            int year = entry.getDate().getYear();
            int month = entry.getDate().getMonthValue();

            // Load appropriate entries based on user type
            List<RegisterCheckEntry> entries;
            if (isTeamLead) {
                entries = loadTeamCheckRegister(username, userId, year, month);
                if (entries == null) entries = new ArrayList<>();
                repairNullEntryIds(entries);
            } else {
                entries = dataAccessService.readUserCheckRegister(username, userId, year, month);
                if (entries == null) entries = new ArrayList<>();
                fixNullEntryIds(entries);
            }

            // Handle ID assignment for new entries
            if (entry.getEntryId() == null) {
                int nextId = generateNextEntryId(entries);
                entry.setEntryId(nextId);
                LoggerUtil.info(this.getClass(), String.format("Generated new ID %d for entry for user %s",
                        nextId, username));
                entries.add(entry);
            } else {
                // For existing entries, remove old one first
                entries.removeIf(e -> e.getEntryId() != null && e.getEntryId().equals(entry.getEntryId()));
                // Then add updated entry
                entries.add(entry);
            }

            // Sort entries
            sortEntries(entries);

            // Save to appropriate location
            if (isTeamLead) {
                saveTeamCheckRegister(username, userId, entries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Saved team entry %d for user %s",
                        entry.getEntryId(), username));
            } else {
                dataAccessService.writeUserCheckRegister(username, userId, entries, year, month);
                LoggerUtil.info(this.getClass(), String.format("Saved user entry %d for user %s",
                        entry.getEntryId(), username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving entry for user %s: %s",
                    username, e.getMessage()));
            throw new RuntimeException("Failed to save entry: " + e.getMessage(), e);
        }
    }

    /**
     * Sort entries consistently with the newest first
     * Uses a simpler approach like UserRegisterService
     */
    private void sortEntries(List<RegisterCheckEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        // Use the same simple approach as UserRegisterService
        // Sort by date (newest first) then by ID (highest ID first)
        try {
            entries.sort(Comparator.comparing(RegisterCheckEntry::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(RegisterCheckEntry::getEntryId, Comparator.nullsLast(Comparator.reverseOrder())));

            LoggerUtil.info(this.getClass(), "Sorted " + entries.size() + " entries with newest first");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error sorting entries: " + e.getMessage(), e);
            // Re-throw the exception to make failures visible
            throw new RuntimeException("Failed to sort entries: " + e.getMessage(), e);
        }
    }

    /**
     * Generate next entry ID consistently
     */
    public int generateNextEntryId(List<RegisterCheckEntry> entries) {
        return entries.stream()
                .filter(e -> e.getEntryId() != null)
                .mapToInt(RegisterCheckEntry::getEntryId)
                .max()
                .orElse(0) + 1;
    }

    /**
     * Perform full search across all check entries
     */
    public List<RegisterCheckEntry> performFullRegisterSearch(String username, Integer userId, String query) {
        // This would need to be implemented in DataAccessService first
        // For now we'll just return an empty list
        LoggerUtil.info(this.getClass(), "Full search for check entries not yet implemented");
        return new ArrayList<>();
    }
}