package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserRegisterService {

    private final DataAccessService dataAccessService;
    private final PathConfig pathConfig;

    public UserRegisterService(DataAccessService dataAccessService, PathConfig pathConfig) {
        this.dataAccessService = dataAccessService;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @PreAuthorize("#username == authentication.name or hasAnyRole('ADMIN', 'TEAM_LEADER')")
    public List<RegisterEntry> loadMonthEntries(String username, Integer userId, int year, int month) {
        try {
            // First get local user register
            List<RegisterEntry> userEntries = dataAccessService.readUserRegister(username, userId, year, month, false);
            if (userEntries == null) {
                userEntries = new ArrayList<>();
            }

            // Check admin register on network - with null check
            List<RegisterEntry> adminEntries = new ArrayList<>(); // Initialize with empty list
            if (pathConfig.isNetworkAvailable()) {
                try {
                    List<RegisterEntry> tempAdminEntries = dataAccessService.readLocalAdminRegister(username, userId, year, month);
                    if (tempAdminEntries != null) {
                        adminEntries = tempAdminEntries;
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Admin register not found for %s: %s", username, e.getMessage()));
                }
            }

            // Update user entries based on admin entries
            List<RegisterEntry> updatedEntries = mergeEntries(userEntries, adminEntries);

            // Save updated entries locally
            dataAccessService.writeUserRegister(username, userId, updatedEntries, year, month);

            return updatedEntries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .sorted(Comparator.comparing(RegisterEntry::getDate).reversed())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading entries for user %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }
    private List<RegisterEntry> mergeEntries(List<RegisterEntry> userEntries, List<RegisterEntry> adminEntries) {
        // Create map of admin entries for quick lookup
        Map<Integer, RegisterEntry> adminEntriesMap = adminEntries.stream()
                .collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry));

        // Update user entries based on admin entries
        return userEntries.stream()
                .map(userEntry -> {
                    RegisterEntry adminEntry = adminEntriesMap.get(userEntry.getEntryId());
                    if (adminEntry != null && adminEntry.getAdminSync().equals(SyncStatus.ADMIN_EDITED.name())) {
                        adminEntry.setAdminSync(SyncStatus.USER_DONE.name());
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
        entry.setAdminSync(SyncStatus.USER_INPUT.name());

        int year = entry.getDate().getYear();
        int month = entry.getDate().getMonthValue();

        try {
            // Load existing entries
            List<RegisterEntry> entries = dataAccessService.readUserRegister(username, userId, year, month, false);
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
            entries.sort(Comparator.comparing(RegisterEntry::getDate).reversed()
                    .thenComparing(RegisterEntry::getEntryId, Comparator.reverseOrder()));

            // Save and sync
            dataAccessService.writeUserRegister(username, userId, entries, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error saving entry for user %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to save entry", e);
        }
    }

    @PreAuthorize("#username == authentication.name")
    public void deleteEntry(String username, Integer userId, Integer entryId, int year, int month) {
        try {
            // Load existing entries
            List<RegisterEntry> entries = dataAccessService.readUserRegister(username, userId, year, month, false);
            if (entries != null) {
                // Verify entry exists and belongs to user
                boolean entryExists = entries.stream()
                        .anyMatch(e -> e.getEntryId().equals(entryId) && e.getUserId().equals(userId));

                if (!entryExists) {
                    throw new IllegalArgumentException("Entry not found or access denied");
                }

                // Remove entry
                entries.removeIf(entry -> entry.getEntryId().equals(entryId));

                // Save and sync
                dataAccessService.writeUserRegister(username, userId, entries, year, month);

                LoggerUtil.info(this.getClass(),
                        String.format("Deleted register entry %d for user %s", entryId, username));
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error deleting entry %d for user %s: %s",
                            entryId, username, e.getMessage()));
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
}