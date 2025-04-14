package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserRegisterService {

    private final DataAccessService dataAccessService;
    @Getter
    private final PathConfig pathConfig;


    @Autowired
    public UserRegisterService(DataAccessService dataAccessService, PathConfig pathConfig) {
        this.dataAccessService = dataAccessService;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PreAuthorize("#username == authentication.name or hasAnyRole('ADMIN', 'TEAM_LEADER')")
    public List<RegisterEntry> loadMonthEntries(String username, Integer userId, int year, int month) {
        try {
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

            // If accessing own data, use local path and merge with admin entries
            if (currentUsername.equals(username)) {
                List<RegisterEntry> userEntries = dataAccessService.readUserRegister(username, userId, year, month);
                if (userEntries == null) {
                    userEntries = new ArrayList<>();
                }

                List<RegisterEntry> adminEntries = new ArrayList<>();
                if (pathConfig.isNetworkAvailable()) {
                    try {
                        adminEntries = dataAccessService.readLocalAdminRegister(username, userId, year, month);
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), "Admin register not found: " + e.getMessage());
                    }
                }

                List<RegisterEntry> mergedEntries = mergeEntries(userEntries, adminEntries);
                dataAccessService.writeUserRegister(username, userId, mergedEntries, year, month);
                return mergedEntries;
            }

            // For admin/team leader accessing other users, read directly from network
            return dataAccessService.readNetworkUserRegister(username, userId, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading entries: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    private List<RegisterEntry> mergeEntries(List<RegisterEntry> userEntries, List<RegisterEntry> adminEntries) {
        // Create map of admin entries for quick lookup
        Map<Integer, RegisterEntry> adminEntriesMap = adminEntries.stream().collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry));

        // Update user entries based on admin entries
        return userEntries.stream()
                .map(userEntry -> {
                    RegisterEntry adminEntry = adminEntriesMap.get(userEntry.getEntryId());
                    if (adminEntry != null && adminEntry.getAdminSync().equals(SyncStatusWorktime.ADMIN_EDITED.name())) {
                        adminEntry.setAdminSync(SyncStatusWorktime.USER_DONE.name());
                        return adminEntry;
                    }
                    return userEntry;
                })
                .collect(Collectors.toList());
    }

    public void saveEntry(String username, Integer userId, RegisterEntry entry) {
        validateEntry(entry);

        // Set initial state
        entry.setUserId(userId);
        entry.setAdminSync(SyncStatusWorktime.USER_INPUT.name());

        int year = entry.getDate().getYear();
        int month = entry.getDate().getMonthValue();

        try {
            // Load existing entries
            List<RegisterEntry> entries = dataAccessService.readUserRegister(username, userId, year, month);
            if (entries == null) entries = new ArrayList<>();

            // Update or add entry
            if (entry.getEntryId() == null) {
                entry.setEntryId(generateNextEntryId(entries));
                entries.add(entry);
            } else {
                entries.removeIf(e -> e.getEntryId().equals(entry.getEntryId()));
                entries.add(entry);
            }

            // Sort entries
            entries.sort(Comparator.comparing(RegisterEntry::getDate).reversed().thenComparing(RegisterEntry::getEntryId, Comparator.reverseOrder()));

            // Save and sync
            dataAccessService.writeUserRegister(username, userId, entries, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error saving entry for user %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to save entry", e);
        }
    }

    @PreAuthorize("#username == authentication.name")
    public void deleteEntry(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Load existing entries
            List<RegisterEntry> entries = dataAccessService.readUserRegister(username, userId, year, month);
            if (entries != null) {
                // Verify entry exists and belongs to user
                boolean entryExists = entries.stream().anyMatch(e -> e.getEntryId().equals(entryId) && e.getUserId().equals(userId));

                if (!entryExists) {
                    throw new IllegalArgumentException("Entry not found or access denied");
                }

                // Remove entry
                entries.removeIf(entry -> entry.getEntryId().equals(entryId));

                // Save and sync
                dataAccessService.writeUserRegister(username, userId, entries, year, month);

                LoggerUtil.info(this.getClass(), String.format("Deleted register entry %d for user %s", entryId, username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error deleting entry %d for user %s: %s", entryId, username, e.getMessage()));
            throw new RuntimeException("Failed to delete entry", e);
        }
    }

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

    public List<RegisterEntry> performFullRegisterSearch(String username, Integer userId, String query) {
        // First, retrieve all entries
        List<RegisterEntry> allEntries = dataAccessService.findRegisterFiles(username, userId);

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