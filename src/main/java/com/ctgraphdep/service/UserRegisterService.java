package com.ctgraphdep.service;

import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class UserRegisterService {
    private static final TypeReference<List<RegisterEntry>> REGISTER_LIST_TYPE = new TypeReference<>() {};
    private final DataAccessService dataAccess;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public UserRegisterService(DataAccessService dataAccess) {
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Load register entries for a specific month
    @PreAuthorize("#username == authentication.name or hasAnyRole('ADMIN', 'TEAM_LEADER')")
    public List<RegisterEntry> loadMonthEntries(String username, Integer userId, int year, int month) {
        // First load user entries
        Path userRegisterPath = dataAccess.getUserRegisterPath(username, userId, year, month);
        List<RegisterEntry> userEntries = dataAccess.readFile(userRegisterPath, REGISTER_LIST_TYPE, true);

        // Check if admin file exists - don't create it if missing
        Path adminRegisterPath = dataAccess.getAdminRegisterPath(username, userId, year, month);
        List<RegisterEntry> adminEntries = Collections.emptyList();

        if (Files.exists(adminRegisterPath)) {
            adminEntries = dataAccess.readFile(adminRegisterPath, REGISTER_LIST_TYPE, false);
        }

        // Create map for admin entries lookup
        Map<Integer, RegisterEntry> adminEntriesMap = adminEntries.stream()
                .collect(Collectors.toMap(RegisterEntry::getEntryId, entry -> entry));

        // Update user entries based on admin entries
        List<RegisterEntry> updatedEntries = userEntries.stream()
                .map(userEntry -> {
                    RegisterEntry adminEntry = adminEntriesMap.get(userEntry.getEntryId());
                    if (adminEntry != null && adminEntry.getAdminSync().equals(SyncStatus.ADMIN_EDITED.name())) {
                        adminEntry.setAdminSync(SyncStatus.USER_DONE.name());
                        return adminEntry;
                    }
                    return userEntry;
                })
                .collect(Collectors.toList());

        // Save updated entries to user file
        dataAccess.writeFile(userRegisterPath, updatedEntries);

        return updatedEntries.stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .sorted(Comparator.comparing(RegisterEntry::getDate).reversed())
                .collect(Collectors.toList());
    }
    // Save a new entry or update existing one
    public void saveEntry(String username, Integer userId, RegisterEntry entry) {
        // Validate entry
        validateEntry(entry);

        // Set initial state
        entry.setUserId(userId);
        entry.setAdminSync("USER_INPUT");  // Always USER_INPUT for new/updated entries

        int year = entry.getDate().getYear();
        int month = entry.getDate().getMonthValue();
        Path registerPath = dataAccess.getUserRegisterPath(username, userId, year, month);

        // Load existing entries
        List<RegisterEntry> entries = dataAccess.readFile(registerPath, REGISTER_LIST_TYPE, true)
                .stream()
                .filter(e -> e.getUserId().equals(userId))
                .collect(Collectors.toList());

        if (entry.getEntryId() == null) {
            entry.setEntryId(generateNextEntryId(entries));
            entries.add(entry);
        } else {
            entries.removeIf(e -> e.getEntryId().equals(entry.getEntryId()));
            entries.add(entry);
        }

        // Sort and save only to user file
        entries.sort(Comparator.comparing(RegisterEntry::getDate).reversed()
                .thenComparing(RegisterEntry::getEntryId, Comparator.reverseOrder()));
        dataAccess.writeFile(registerPath, entries);

        // Remove the sync with admin - this should only happen when loading/viewing
    }

    // Get a specific entry by ID
    public RegisterEntry getEntry(String username, Integer userId, Integer id, int year, int month) {
        lock.readLock().lock();
        try {
            Path registerPath = dataAccess.getUserRegisterPath(username, userId, year, month);
            List<RegisterEntry> entries = dataAccess.readFile(registerPath, REGISTER_LIST_TYPE, true);

            return entries.stream()
                    .filter(e -> e.getEntryId().equals(id))
                    .filter(e -> e.getUserId().equals(userId)) // Ensure entry belongs to user
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + id));

        } finally {
            lock.readLock().unlock();
        }
    }

    // Delete an entry
    @PreAuthorize("#username == authentication.name")
    public void deleteEntry(String username, Integer userId, Integer entryId, int year, int month) {
        lock.writeLock().lock();
        try {
            Path registerPath = dataAccess.getUserRegisterPath(username, userId, year, month);
            List<RegisterEntry> entries = dataAccess.readFile(registerPath, REGISTER_LIST_TYPE, true);

            // Verify the entry exists and belongs to this user before deleting
            Optional<RegisterEntry> entryToDelete = entries.stream()
                    .filter(e -> e.getEntryId().equals(entryId))
                    .filter(e -> e.getUserId().equals(userId))
                    .findFirst();

            if (entryToDelete.isEmpty()) {
                throw new IllegalArgumentException("Entry not found or access denied");
            }

            // Remove the entry
            entries.removeIf(entry ->
                    entry.getEntryId().equals(entryId) &&
                            entry.getUserId().equals(userId)
            );

            // Save the updated list
            dataAccess.writeFile(registerPath, entries);

            LoggerUtil.info(this.getClass(),
                    String.format("Deleted register entry %d for user %s (ID: %d)",
                            entryId, username, userId));

        } finally {
            lock.writeLock().unlock();
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