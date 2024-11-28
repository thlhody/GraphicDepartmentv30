package com.ctgraphdep.service;

import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

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
        LoggerUtil.initialize(this.getClass(), "Initializing Register Service");
    }

    /**
     * Load register entries for a specific month
     */
    @PreAuthorize("#username == authentication.name or hasAnyRole('ADMIN', 'TEAM_LEADER')")
    public List<RegisterEntry> loadMonthEntries(String username, Integer userId, int year, int month) {
        lock.readLock().lock();
        try {
            Path registerPath = dataAccess.getUserRegisterPath(username, userId, year, month);
            List<RegisterEntry> entries = dataAccess.readFile(registerPath, REGISTER_LIST_TYPE, true);

            // Filter entries for this user only
            return entries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .sorted(Comparator.comparing(RegisterEntry::getDate)
                            .thenComparing(RegisterEntry::getEntryId))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading register for user %s (ID: %d): %s",
                            username, userId, e.getMessage()));
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Save a new entry or update existing one
     */
    public void saveEntry(String username, Integer userId, RegisterEntry entry) {
        validateEntry(entry);

        // Ensure entry belongs to correct user
        entry.setUserId(userId);

        lock.writeLock().lock();
        try {
            int year = entry.getDate().getYear();
            int month = entry.getDate().getMonthValue();
            Path registerPath = dataAccess.getUserRegisterPath(username, userId, year, month);

            List<RegisterEntry> entries = dataAccess.readFile(registerPath, REGISTER_LIST_TYPE, true);

            // Filter out any entries that don't belong to this user
            entries = entries.stream()
                    .filter(e -> e.getUserId().equals(userId))
                    .collect(Collectors.toList());

            // Handle new entry
            if (entry.getEntryId() == null) {
                int nextId = generateNextEntryId(entries);
                entry.setEntryId(nextId);
                entries.add(entry);
            }
            // Handle update
            else {
                // Verify the entry being updated belongs to this user
                entries.stream()
                        .filter(e -> e.getEntryId().equals(entry.getEntryId()))
                        .findFirst()
                        .ifPresent(existingEntry -> {
                            if (!existingEntry.getUserId().equals(userId)) {
                                throw new IllegalArgumentException("Cannot modify entry belonging to another user");
                            }
                        });

                entries.removeIf(e -> e.getEntryId().equals(entry.getEntryId()));
                entries.add(entry);
            }

            // Sort entries
            entries.sort(Comparator.comparing(RegisterEntry::getDate)
                    .thenComparing(RegisterEntry::getEntryId));

            // Save updated list
            dataAccess.writeFile(registerPath, entries);

            LoggerUtil.info(this.getClass(),
                    String.format("%s register entry %d for user %s (ID: %d) on %s",
                            entry.getEntryId() == null ? "Saved new" : "Updated",
                            entry.getEntryId(), username, userId, entry.getDate()));

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get a specific entry by ID
     */
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

    /**
     * Delete an entry
     */
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
        validateField(entry.getPrintPrepType(), "Print prep type", "missing_print_prep_type");
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