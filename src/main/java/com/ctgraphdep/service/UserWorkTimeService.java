package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.SyncStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class UserWorkTimeService {
    private final DataAccessService dataAccess;
    private final UserService userService;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final TypeReference<List<WorkTimeTable>> WORKTIME_LIST_TYPE = new TypeReference<>() {};

    public UserWorkTimeService(
            DataAccessService dataAccess,
            UserService userService) {
        this.dataAccess = dataAccess;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), "Initializing User WorkTime Service");
    }

    @PreAuthorize("#username == authentication.name")
    public List<WorkTimeTable> loadMonthWorktime(String username, int year, int month) {
        validatePeriod(year, month);
        Integer userId = getUserId(username);

        lock.readLock().lock();
        try {
            // First check admin entries for any edits or blanks
            List<WorkTimeTable> adminEntries = loadAdminEntries(username, userId, year, month);

            // Load user entries
            Path userPath = dataAccess.getUserWorktimePath(username, year, month);
            List<WorkTimeTable> userEntries = loadUserEntries(userPath);

            // Merge entries, giving priority to admin entries and handling ADMIN_BLANK
            List<WorkTimeTable> mergedEntries = mergeEntries(userEntries, adminEntries);

            // Save if there were any admin changes
            if (!adminEntries.isEmpty()) {
                saveEntriesWithBackup(username, mergedEntries, year, month);
            }

            return mergedEntries.stream()
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading worktime for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<WorkTimeTable> loadAdminEntries(String username, Integer userId, int year, int month) {
        Path adminPath = dataAccess.getAdminWorktimePath(year, month);
        if (!Files.exists(adminPath)) {
            return new ArrayList<>();
        }

        return dataAccess.readFile(adminPath, WORKTIME_LIST_TYPE, true).stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .filter(entry -> entry.getAdminSync() == SyncStatus.ADMIN_EDITED
                        || entry.getAdminSync() == SyncStatus.ADMIN_BLANK)
                .collect(Collectors.toList());
    }

    private List<WorkTimeTable> loadUserEntries(Path path) {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        return dataAccess.readFile(path, WORKTIME_LIST_TYPE, true);
    }

    private List<WorkTimeTable> mergeEntries(List<WorkTimeTable> userEntries, List<WorkTimeTable> adminEntries) {
        Map<LocalDate, WorkTimeTable> mergedMap = new HashMap<>();

        // Add user entries first
        userEntries.forEach(entry -> mergedMap.put(entry.getWorkDate(), entry));

        // Process admin entries (both edits and blanks)
        adminEntries.forEach(adminEntry -> {
            if (adminEntry.getAdminSync() == SyncStatus.ADMIN_BLANK) {
                // Remove entry if admin marked it as blank
                mergedMap.remove(adminEntry.getWorkDate());
            } else {
                // For ADMIN_EDITED entries, overlay them with USER_DONE status
                adminEntry.setAdminSync(SyncStatus.USER_DONE);
                mergedMap.put(adminEntry.getWorkDate(), adminEntry);
            }
        });

        return new ArrayList<>(mergedMap.values());
    }

    @PreAuthorize("#username == authentication.name")
    public void saveWorkTimeEntries(String username, List<WorkTimeTable> entries) {
        if (entries.isEmpty()) {
            return;
        }

        Integer userId = getUserId(username);
        lock.writeLock().lock();
        try {
            // Validate all entries
            validateEntries(entries, userId);

            // Set sync status for all entries
            entries.forEach(entry -> entry.setAdminSync(SyncStatus.USER_INPUT));

            // Group entries by month for processing
            Map<YearMonth, List<WorkTimeTable>> entriesByMonth = entries.stream()
                    .collect(Collectors.groupingBy(entry ->
                            YearMonth.from(entry.getWorkDate())));

            // Process each month's entries
            entriesByMonth.forEach((yearMonth, monthEntries) -> {
                processMonthEntries(username, userId, monthEntries, yearMonth.getYear(), yearMonth.getMonthValue());
            });

            LoggerUtil.info(this.getClass(),
                    String.format("Saved %d entries for user %s", entries.size(), username));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error saving worktime entries for %s: %s",
                            username, e.getMessage()));
            throw new RuntimeException("Failed to save worktime entries", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void processMonthEntries(
            String username,
            Integer userId,
            List<WorkTimeTable> newEntries,
            int year,
            int month) {

        Path userPath = dataAccess.getUserWorktimePath(username, year, month);

        // Create backup before processing
        createBackup(userPath);

        try {
            // Load existing entries
            List<WorkTimeTable> existingEntries = loadUserEntries(userPath);

            // Remove existing entries for these dates
            Set<LocalDate> newDates = newEntries.stream()
                    .map(WorkTimeTable::getWorkDate)
                    .collect(Collectors.toSet());

            List<WorkTimeTable> remainingEntries = existingEntries.stream()
                    .filter(entry -> !entry.getUserId().equals(userId) ||
                            !newDates.contains(entry.getWorkDate()))
                    .collect(Collectors.toList());

            // Add new entries
            remainingEntries.addAll(newEntries);

            // Sort entries
            remainingEntries.sort(Comparator
                    .comparing(WorkTimeTable::getWorkDate)
                    .thenComparing(WorkTimeTable::getUserId));

            // Save updated entries
            saveEntriesWithBackup(username, remainingEntries, year, month);

        } catch (Exception e) {
            // Try to restore from backup
            restoreFromBackup(userPath);
            throw e;
        }
    }

    private void createBackup(Path originalPath) {
        if (!Files.exists(originalPath)) {
            return;
        }

        try {
            Path backupPath = originalPath.resolveSibling(originalPath.getFileName() + ".bak");
            Files.copy(originalPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info(this.getClass(), "Created backup: " + backupPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to create backup: " + e.getMessage());
        }
    }

    private void restoreFromBackup(Path originalPath) {
        Path backupPath = originalPath.resolveSibling(originalPath.getFileName() + ".bak");
        if (!Files.exists(backupPath)) {
            return;
        }

        try {
            Files.copy(backupPath, originalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LoggerUtil.info(this.getClass(), "Restored from backup: " + originalPath);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to restore from backup: " + e.getMessage());
        }
    }

    private void saveEntriesWithBackup(String username, List<WorkTimeTable> entries, int year, int month) {
        Path userPath = dataAccess.getUserWorktimePath(username, year, month);

        try {
            // Create backup
            createBackup(userPath);

            // Save entries
            dataAccess.writeFile(userPath, entries);

            LoggerUtil.info(this.getClass(),
                    String.format("Saved %d entries for %s/%d", entries.size(), month, year));
        } catch (Exception e) {
            restoreFromBackup(userPath);
            throw new RuntimeException("Failed to save entries", e);
        }
    }

    @PreAuthorize("#username == authentication.name")
    public void saveWorkTimeEntry(String username, WorkTimeTable entry, int year, int month) {
        lock.writeLock().lock();
        try {
            // Get session file path
            Path filePath = dataAccess.getUserWorktimePath(username,year, month);

            // Read existing entries
            List<WorkTimeTable> entries = dataAccess.readFile(filePath, WORKTIME_LIST_TYPE, true);

            // Remove any existing entry for the same date and user
            entries.removeIf(e ->
                    e.getUserId().equals(entry.getUserId()) &&
                            e.getWorkDate().equals(entry.getWorkDate())
            );

            // Add new entry
            entries.add(entry);

            // Sort entries
            entries.sort(Comparator
                    .comparing(WorkTimeTable::getWorkDate)
                    .thenComparing(WorkTimeTable::getUserId));

            // Save updated entries
            dataAccess.writeFile(filePath, entries);

            LoggerUtil.info(this.getClass(),
                    String.format("Saved worktime entry for user %s on %s",
                            username, entry.getWorkDate()));

        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isNationalHoliday(LocalDate date) {
        lock.readLock().lock();
        try {
            // Use admin worktime path because national holidays are set by admins
            Path filePath = dataAccess.getAdminWorktimePath(date.getYear(), date.getMonthValue());
            List<WorkTimeTable> entries = dataAccess.readFile(filePath, WORKTIME_LIST_TYPE, true);

            LoggerUtil.debug(this.getClass(),
                    String.format("Checking national holiday for %s. Found %d admin entries",
                            date, entries.size()));

            // A day is a national holiday if there exists an admin synced SN entry
            boolean isHoliday = entries.stream()
                    .anyMatch(entry ->
                            entry.getWorkDate().equals(date) &&
                                    WorkCode.NATIONAL_HOLIDAY_CODE.equals(entry.getTimeOffType()) &&
                                    SyncStatus.ADMIN_EDITED.equals(entry.getAdminSync()));

            if (isHoliday) {
                LoggerUtil.debug(this.getClass(),
                        String.format("%s is marked as a national holiday", date));
            }

            return isHoliday;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking national holiday for %s: %s",
                            date, e.getMessage()));
            return false;  // Default to not a holiday in case of error
        } finally {
            lock.readLock().unlock();
        }
    }

    private void validatePeriod(int year, int month) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Invalid year: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        YearMonth requested = YearMonth.of(year, month);
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(1);

        if (requested.isBefore(previous)) {
            throw new IllegalArgumentException("Cannot access periods before previous month");
        }
    }

    private void validateEntries(List<WorkTimeTable> entries, Integer userId) {
        entries.forEach(entry -> {
            if (!userId.equals(entry.getUserId())) {
                throw new SecurityException("Invalid user ID in entry");
            }
            if (entry.getWorkDate() == null) {
                throw new IllegalArgumentException("Work date cannot be null");
            }
            validatePeriod(entry.getWorkDate().getYear(), entry.getWorkDate().getMonthValue());

            // Validate entry data
            validateWorkTimeEntry(entry);
        });
    }

    private void validateWorkTimeEntry(WorkTimeTable entry) {
        // Removed BLANK from valid time off types
        if (entry.getTimeOffType() != null &&
                !entry.getTimeOffType().matches("^(SN|CO|CM)$")) {
            throw new IllegalArgumentException("Invalid time off type: " + entry.getTimeOffType());
        }

        // Validate worked minutes
        if (entry.getTotalWorkedMinutes() < 0) {
            throw new IllegalArgumentException("Total worked minutes cannot be negative");
        }

        // Validate temporary stop count
        if (entry.getTemporaryStopCount() < 0) {
            throw new IllegalArgumentException("Temporary stop count cannot be negative");
        }

        // Validate overtime minutes
        if (entry.getTotalOvertimeMinutes() < 0) {
            throw new IllegalArgumentException("Overtime minutes cannot be negative");
        }
    }

    private Integer getUserId(String username) {
        return userService.getUserByUsername(username)
                .map(User::getUserId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
    }
}