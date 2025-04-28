package com.ctgraphdep.service;

import com.ctgraphdep.enums.CheckingStatus;
import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.fileOperations.DataAccessService;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing check register entries
 * Similar to UserRegisterService but for check entries
 */
@Service
@Getter
@PreAuthorize("hasAnyRole('ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class CheckRegisterService {

    private final DataAccessService dataAccessService;

    @Autowired
    public CheckRegisterService(DataAccessService dataAccessService) {
        this.dataAccessService = dataAccessService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Load check entries for a specific month
     * User can only access their own entries, admins and team leaders can access any entries
     */

    public List<RegisterCheckEntry> loadMonthEntries(String username, Integer userId, int year, int month) {
        try {
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

            // If accessing own data, use local path
            if (currentUsername.equals(username)) {
                List<RegisterCheckEntry> userEntries = dataAccessService.readUserCheckRegister(username, userId, year, month);
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

                // Write back to ensure files are in sync
                dataAccessService.writeUserCheckRegister(username, userId, mergedEntries, year, month);
                return mergedEntries;
            }

            // For admin/team leader accessing other users, read from network
            return dataAccessService.readUserCheckRegister(username, userId, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading check entries: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Merge user check entries with team lead entries
     */
    private List<RegisterCheckEntry> mergeEntries(List<RegisterCheckEntry> userEntries, List<RegisterCheckEntry> teamLeadEntries) {
        // Create map of team lead entries for quick lookup
        Map<Integer, RegisterCheckEntry> teamLeadEntriesMap = teamLeadEntries.stream()
                .collect(Collectors.toMap(RegisterCheckEntry::getEntryId, entry -> entry));

        // Apply merge rules to each user entry
        return userEntries.stream()
                .map(userEntry -> {
                    RegisterCheckEntry teamLeadEntry = teamLeadEntriesMap.get(userEntry.getEntryId());
                    return com.ctgraphdep.enums.CheckRegisterMergeRule.apply(userEntry, teamLeadEntry);
                })
                .collect(Collectors.toList());
    }

    /**
     * Save a new or updated check entry
     */
    public void saveEntry(String username, Integer userId, RegisterCheckEntry entry) {
        validateEntry(entry);

        // Set initial state for new entries
        entry.setAdminSync(CheckingStatus.CHECKING_INPUT.name());

        int year = entry.getDate().getYear();
        int month = entry.getDate().getMonthValue();

        try {
            // Load existing entries
            List<RegisterCheckEntry> entries = dataAccessService.readUserCheckRegister(username, userId, year, month);
            if (entries == null) entries = new ArrayList<>();

            // Update or add entry
            if (entry.getEntryId() == null) {
                entry.setEntryId(generateNextEntryId(entries));
                entries.add(entry);
            } else {
                entries.removeIf(e -> e.getEntryId().equals(entry.getEntryId()));
                entries.add(entry);
            }

            // Sort entries by date (descending) and ID (descending)
            entries.sort(Comparator.comparing(RegisterCheckEntry::getDate).reversed()
                    .thenComparing(RegisterCheckEntry::getEntryId, Comparator.reverseOrder()));

            // Save and sync
            dataAccessService.writeUserCheckRegister(username, userId, entries, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving check entry for user %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to save check entry", e);
        }
    }

    /**
     * Delete a check entry
     * User can only delete their own entries
     */
    public void deleteEntry(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Load existing entries
            List<RegisterCheckEntry> entries = dataAccessService.readUserCheckRegister(username, userId, year, month);
            if (entries != null) {
                // Verify entry exists and belongs to user
                boolean entryExists = entries.stream().anyMatch(e -> e.getEntryId().equals(entryId));

                if (!entryExists) {
                    throw new IllegalArgumentException("Check entry not found or access denied");
                }

                // Remove entry
                entries.removeIf(entry -> entry.getEntryId().equals(entryId));

                // Save and sync
                dataAccessService.writeUserCheckRegister(username, userId, entries, year, month);

                LoggerUtil.info(this.getClass(), String.format("Deleted check entry %d for user %s", entryId, username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error deleting check entry %d for user %s: %s", entryId, username, e.getMessage()));
            throw new RuntimeException("Failed to delete check entry", e);
        }
    }

    /**
     * Validate check entry for required fields
     */
    private void validateEntry(RegisterCheckEntry entry) {
        if (entry == null) {
            throw new RegisterValidationException("Entry cannot be null", "null_entry");
        }
        validateField(entry.getDate(), "Date", "missing_date");
        validateField(entry.getOmsId(), "OMS ID", "missing_oms_id");
        validateField(entry.getDesignerName(), "Designer name", "missing_designer_name");
        validateField(entry.getCheckType(), "Check type", "missing_check_type");
        validateField(entry.getArticleNumbers(), "Article numbers", "missing_article_numbers");
        validateField(entry.getFilesNumbers(), "File numbers", "missing_file_numbers");
        validateField(entry.getApprovalStatus(), "Approval status", "missing_approval_status");
    }

    private void validateField(Object field, String fieldName, String errorCode) {
        if (field == null) {
            throw new RegisterValidationException(fieldName + " is required", errorCode);
        }
    }

    private int generateNextEntryId(List<RegisterCheckEntry> entries) {
        return entries.stream()
                .mapToInt(entry -> entry.getEntryId() != null ? entry.getEntryId() : 0)
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

        // Once implemented in DataAccessService, the code would be:
        /*
        // First, retrieve all entries across all periods
        List<RegisterCheckEntry> allEntries = dataAccessService.findCheckRegisterFiles(username, userId);

        // If no query, return all entries sorted by date
        if (query == null || query.trim().isEmpty()) {
            return allEntries.stream()
                    .sorted(Comparator.comparing(RegisterCheckEntry::getDate).reversed())
                    .collect(Collectors.toList());
        }

        // Split query into search terms
        String[] searchTerms = query.toLowerCase().split("\\s+");

        // Filter entries based on all terms
        return allEntries.stream()
                .filter(entry ->
                        Arrays.stream(searchTerms).allMatch(term ->
                                (entry.getOrderId() != null && entry.getOrderId().toLowerCase().contains(term)) ||
                                (entry.getProductionId() != null && entry.getProductionId().toLowerCase().contains(term)) ||
                                (entry.getOmsId() != null && entry.getOmsId().toLowerCase().contains(term)) ||
                                (entry.getDesignerName() != null && entry.getDesignerName().toLowerCase().contains(term)) ||
                                (entry.getCheckType() != null && entry.getCheckType().toLowerCase().contains(term)) ||
                                (entry.getErrorDescription() != null && entry.getErrorDescription().toLowerCase().contains(term))
                        )
                )
                .sorted(Comparator.comparing(RegisterCheckEntry::getDate).reversed())
                .collect(Collectors.toList());
        */
    }
}