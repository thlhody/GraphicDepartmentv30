package com.ctgraphdep.service;

import com.ctgraphdep.enums.RegisterMergeRule;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.service.cache.RegisterCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserRegisterService {

    private final RegisterDataService registerDataService;
    private final RegisterCacheService registerCacheService;
    private final RegisterMergeService registerMergeService;

    @Autowired
    public UserRegisterService(RegisterDataService registerDataService, RegisterCacheService registerCacheService, RegisterMergeService registerMergeService) {
        this.registerDataService = registerDataService;
        this.registerCacheService = registerCacheService;
        this.registerMergeService = registerMergeService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public List<RegisterEntry> loadMonthEntries(String username, Integer userId, int year, int month) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        if (currentUsername.equals(username)) {
            // Check if this is NOT the current month
            LocalDate currentDate = LocalDate.now();
            boolean isCurrentMonth = (year == currentDate.getYear() && month == currentDate.getMonthValue());

            if (!isCurrentMonth) {
                // For non-current months, perform merge first to get admin changes
                LoggerUtil.info(this.getClass(), String.format("Performing on-demand merge for %s - %d/%d (non-current month)", username, year, month));
                registerMergeService.performUserLoginMerge(username, userId, year, month);
            }

            // Use cache - will load fresh merged data
            return registerCacheService.getMonthEntries(username, userId, year, month);
        }

        // Admin accessing other users - direct network read
        return registerDataService.readUserFromNetworkOnly(username, userId, year, month);
    }
    public void saveEntry(String username, Integer userId, RegisterEntry entry) {
        validateEntry(entry);

        // Set initial state
        entry.setUserId(userId);

        int year = entry.getDate().getYear();
        int month = entry.getDate().getMonthValue();

        try {
            // Ensure cache is loaded for this month (loads from file if cache empty)
            LoggerUtil.debug(this.getClass(), String.format("Ensuring cache is loaded for %s - %d/%d", username, month, year));
            registerCacheService.getMonthEntries(username, userId, year, month);

            // Generate entry ID if needed (new entry)
            if (entry.getEntryId() == null) {
                // Get current entries from cache to determine next ID
                List<RegisterEntry> currentEntries = registerCacheService.getMonthEntries(username, userId, year, month);
                entry.setEntryId(generateNextEntryId(currentEntries));

                // NEW ENTRY: Always USER_INPUT
                entry.setAdminSync(SyncStatusMerge.USER_INPUT.name());

                LoggerUtil.info(this.getClass(), String.format("New entry %d created with USER_INPUT status", entry.getEntryId()));
            } else {
                // EXISTING ENTRY: Check current status to determine new status
                RegisterEntry existingEntry = registerCacheService.getEntry(username, userId, entry.getEntryId(), year, month);

                if (existingEntry != null) {
                    String currentStatus = existingEntry.getAdminSync();

                    // Step 4: User edits approved entry: USER_DONE → USER_EDITED
                    // OR: User edits admin-modified entry: ADMIN_EDITED → USER_EDITED
                    if (SyncStatusMerge.USER_DONE.name().equals(currentStatus) ||
                            SyncStatusMerge.ADMIN_EDITED.name().equals(currentStatus)) {

                        entry.setAdminSync(SyncStatusMerge.USER_EDITED.name());
                        LoggerUtil.info(this.getClass(), String.format(
                                "Entry %d status changed: %s → USER_EDITED (user modified approved entry)",
                                entry.getEntryId(), currentStatus));

                    } else {
                        // Keep existing status for other cases (USER_INPUT, USER_EDITED, etc.)
                        entry.setAdminSync(currentStatus);
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Entry %d keeping existing status: %s", entry.getEntryId(), currentStatus));
                    }
                } else {
                    // Entry not found in cache, treat as new
                    entry.setAdminSync(SyncStatusMerge.USER_INPUT.name());
                    LoggerUtil.warn(this.getClass(), String.format(
                            "Entry %d not found in cache, treating as new with USER_INPUT status", entry.getEntryId()));
                }
            }

            // Add/update entry using cache (which will write-through to file)
            boolean success;

            // Check if entry exists in cache
            RegisterEntry existingEntry = registerCacheService.getEntry(username, userId, entry.getEntryId(), year, month);

            if (existingEntry != null) {
                LoggerUtil.debug(this.getClass(), String.format("Updating existing entry %d in cache", entry.getEntryId()));
                success = registerCacheService.updateEntry(username, userId, entry);
            } else {
                LoggerUtil.debug(this.getClass(), String.format("Adding new entry %d to cache", entry.getEntryId()));
                success = registerCacheService.addEntry(username, userId, entry);
            }

            if (!success) {
                throw new RuntimeException("Failed to save entry to cache");
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully saved entry %d for user %s with status %s", entry.getEntryId(), username, entry.getAdminSync()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving entry for user %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to save entry: " + e.getMessage(), e);
        }
    }

    public void deleteEntry(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Verify entry exists in cache (this will load from file if needed)
            RegisterEntry existingEntry = registerCacheService.getEntry(username, userId, entryId, year, month);

            if (existingEntry == null) {
                throw new IllegalArgumentException("Entry not found");
            }

            // Verify entry belongs to user
            if (!existingEntry.getUserId().equals(userId)) {
                throw new IllegalArgumentException("Access denied - entry belongs to different user");
            }

            // Delete entry using cache (which will write-through to file)
            boolean success = registerCacheService.deleteEntry(username, userId, entryId, year, month);

            if (!success) {
                throw new RuntimeException("Failed to delete entry from cache");
            }

            LoggerUtil.info(this.getClass(), String.format("Deleted register entry %d for user %s", entryId, username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error deleting entry %d for user %s: %s", entryId, username, e.getMessage()));
            throw new RuntimeException("Failed to delete entry", e);
        }
    }

    // === UNCHANGED METHODS ===


    private void validateEntry(RegisterEntry entry) {
        if (entry == null) {
            throw new RegisterValidationException("Entry cannot be null", "null_entry");
        }

        validateField(entry.getDate(), "Date", "missing_date");
        validateField(entry.getOrderId(), "Order ID", "missing_order_id");
        validateField(entry.getOmsId(), "OMS ID", "missing_oms_id");
        validateField(entry.getClientName(), "Client name", "missing_client_name");
        validateField(entry.getActionType(), "Action type", "missing_action_type");
        validateField(entry.getPrintPrepTypes(), "Print prep types", "missing_print_prep_type");
        validateField(entry.getArticleNumbers(), "Article numbers", "missing_article_numbers");
    }

    private void validateField(Object field, String fieldName, String errorCode) {
        if (field == null) {
            throw new RegisterValidationException(fieldName + " is required", errorCode);
        }
    }

    private int generateNextEntryId(List<RegisterEntry> entries) {
        return entries.stream()
                .mapToInt(entry -> entry.getEntryId() != null ? entry.getEntryId() : 0)
                .max()
                .orElse(0) + 1;
    }

    // === SEARCH METHOD - KEEPING FILE-BASED FOR NOW ===
    // Note: This method searches across multiple months, which doesn't fit well with month-based cache.
    // For now, keeping the file-based search. Could be optimized later if needed.
    public List<RegisterEntry> performFullRegisterSearch(String username, Integer userId, String query) {
        LoggerUtil.info(this.getClass(), "Performing full register search across multiple months (file-based)");

        // First, retrieve all entries
        List<RegisterEntry> allEntries = registerDataService.findRegisterFiles(username, userId);

        // If no query, return all entries sorted by date
        if (query == null || query.trim().isEmpty()) {
            return allEntries.stream()
                    .sorted(Comparator.comparing(RegisterEntry::getDate).reversed())
                    .collect(Collectors.toList());
        }

        // Split query into search terms
        String[] searchTerms = query.toLowerCase().split("\\s+");

        // Filter entries
        return allEntries.stream()
                .filter(entry ->
                        Arrays.stream(searchTerms).allMatch(term ->
                                (entry.getOrderId() != null && entry.getOrderId().toLowerCase().contains(term)) ||
                                        (entry.getProductionId() != null && entry.getProductionId().toLowerCase().contains(term)) ||
                                        (entry.getOmsId() != null && entry.getOmsId().toLowerCase().contains(term)) ||
                                        (entry.getClientName() != null && entry.getClientName().toLowerCase().contains(term)) ||
                                        (entry.getActionType() != null && entry.getActionType().toLowerCase().contains(term)) ||
                                        (entry.getPrintPrepTypes() != null &&
                                                entry.getPrintPrepTypes().stream().anyMatch(type ->
                                                        type.toLowerCase().contains(term))) ||
                                        (entry.getObservations() != null && entry.getObservations().toLowerCase().contains(term))
                        )
                )
                .sorted(Comparator.comparing(RegisterEntry::getDate).reversed())
                .collect(Collectors.toList());
    }
}