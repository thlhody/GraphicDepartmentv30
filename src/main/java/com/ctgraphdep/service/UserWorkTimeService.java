package com.ctgraphdep.service;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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
    private final PathConfig pathConfig;

    public UserWorkTimeService(
            DataAccessService dataAccess,
            UserService userService, PathConfig pathConfig) {
        this.dataAccess = dataAccess;
        this.userService = userService;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Load month worktime - local with network sync
    @PreAuthorize("#username == authentication.name or hasAnyRole('ADMIN', 'TEAM_LEADER')")
    public List<WorkTimeTable> loadMonthWorktime(String username, int year, int month) {
        validatePeriod(year, month);
        Integer userId = getUserId(username);
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // If accessing own data, use normal local path flow
        if (currentUsername.equals(username)) {
            lock.readLock().lock();
            try {
                List<WorkTimeTable> adminEntries = loadAdminEntries(userId, year, month);
                List<WorkTimeTable> userEntries = loadUserEntries(username, year, month);
                List<WorkTimeTable> mergedEntries = mergeEntries(userEntries, adminEntries);

                if (!adminEntries.isEmpty()) {
                    dataAccess.writeUserWorktime(username, mergedEntries, year, month);
                }

                return mergedEntries.stream()
                        .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                        .collect(Collectors.toList());
            } finally {
                lock.readLock().unlock();
            }
        }

        // For admin/team leader viewing others, read directly from network
        if (pathConfig.isNetworkAvailable()) {
            return dataAccess.readNetworkUserWorktime(username, year, month);
        }
        throw new RuntimeException("Network access required to view other users' worktime");
    }

    // In UserWorkTimeService
    @PreAuthorize("hasAnyRole('ADMIN', 'TEAM_LEADER')")
    public List<WorkTimeTable> loadViewOnlyWorktime(String username, int year, int month) {
        validatePeriod(year, month);
        Integer userId = getUserId(username);

        // Only read from network for admins/team leaders viewing others
        List<WorkTimeTable> userEntries = dataAccess.readNetworkUserWorktime(username, year, month);
        List<WorkTimeTable> adminEntries = loadAdminEntries(userId, year, month);

        Map<LocalDate, WorkTimeTable> mergedMap = new HashMap<>();

        if (userEntries != null) {
            userEntries.forEach(entry -> mergedMap.put(entry.getWorkDate(), entry));
        }

        if (adminEntries != null) {
            adminEntries.forEach(adminEntry -> {
                if (adminEntry.getAdminSync() == SyncStatus.ADMIN_EDITED) {
                    adminEntry.setAdminSync(SyncStatus.USER_DONE);
                    mergedMap.put(adminEntry.getWorkDate(), adminEntry);
                }
            });
        }

        return new ArrayList<>(mergedMap.values()).stream()
                .sorted(Comparator.comparing(WorkTimeTable::getWorkDate))
                .collect(Collectors.toList());
    }

    private List<WorkTimeTable> mergeEntries(List<WorkTimeTable> userEntries, List<WorkTimeTable> adminEntries) {
        Map<LocalDate, WorkTimeTable> mergedMap = new HashMap<>();

        // Add user entries first, including USER_IN_PROCESS entries
        userEntries.forEach(entry -> {
            if (SyncStatus.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                // For in-process entries, add them with special handling
                mergedMap.put(entry.getWorkDate(), entry);
            } else {
                mergedMap.put(entry.getWorkDate(), entry);
            }
        });

        // Process admin entries (both edits and blanks)
        adminEntries.forEach(adminEntry -> {
            if (adminEntry.getAdminSync() == SyncStatus.ADMIN_BLANK) {
                // Remove entry if admin marked it as blank
                mergedMap.remove(adminEntry.getWorkDate());
            } else if (adminEntry.getAdminSync() == SyncStatus.ADMIN_EDITED) {
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
            entriesByMonth.forEach((yearMonth, monthEntries) -> processMonthEntries(username, userId, monthEntries, yearMonth.getYear(), yearMonth.getMonthValue()));

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

    // Process month entries - local with network sync
    private void processMonthEntries(String username, Integer userId, List<WorkTimeTable> newEntries, int year, int month) {
        try {
            List<WorkTimeTable> existingEntries = loadUserEntries(username, year, month);

            // Remove existing entries for these dates
            Set<LocalDate> newDates = newEntries.stream()
                    .map(WorkTimeTable::getWorkDate)
                    .collect(Collectors.toSet());

            List<WorkTimeTable> remainingEntries = existingEntries.stream()
                    .filter(entry -> !entry.getUserId().equals(userId) ||
                            !newDates.contains(entry.getWorkDate()))
                    .collect(Collectors.toList());

            remainingEntries.addAll(newEntries);
            remainingEntries.sort(Comparator
                    .comparing(WorkTimeTable::getWorkDate)
                    .thenComparing(WorkTimeTable::getUserId));

            dataAccess.writeUserWorktime(username, remainingEntries, year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error processing month entries for %s: %s", username, e.getMessage()));
            throw new RuntimeException("Failed to process month entries", e);
        }
    }


    public void saveWorkTimeEntry(String username, WorkTimeTable entry, int year, int month) {
        lock.writeLock().lock();
        try {
            // Initialize entries as empty list if null
            List<WorkTimeTable> entries = loadUserEntries(username, year, month);
            if (entries == null) {
                entries = new ArrayList<>();
                LoggerUtil.debug(this.getClass(),
                        String.format("Initializing new entries list for user %s - %d/%d",
                                username, year, month));
            }

            // Remove existing entry if present
            entries.removeIf(e ->
                    e.getUserId().equals(entry.getUserId()) &&
                            e.getWorkDate().equals(entry.getWorkDate()));

            // Add new entry
            entries.add(entry);

            // Sort entries
            entries.sort(Comparator
                    .comparing(WorkTimeTable::getWorkDate)
                    .thenComparing(WorkTimeTable::getUserId));

            // Save entries
            try {
                dataAccess.writeUserWorktime(username, entries, year, month);
                LoggerUtil.info(this.getClass(),
                        String.format("Saved worktime entry for user %s - %d/%d",
                                username, year, month));
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        String.format("Failed to save worktime entry for user %s: %s",
                                username, e.getMessage()));
                throw new RuntimeException("Failed to save worktime entry", e);
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    public void saveWorkTimeEntry(String username, WorkTimeTable entry,
                                  int year, int month, String operatingUsername) {
        lock.writeLock().lock();
        try {
            List<WorkTimeTable> entries = loadUserEntries(username, year, month);
            if (entries == null) {
                entries = new ArrayList<>();
            }

            // Remove existing entry if present
            entries.removeIf(e -> e.getUserId().equals(entry.getUserId()) && e.getWorkDate().equals(entry.getWorkDate()));

            entries.add(entry);
            entries.sort(Comparator.comparing(WorkTimeTable::getWorkDate).thenComparing(WorkTimeTable::getUserId));

            try {
                // Use the new overloaded method
                dataAccess.writeUserWorktime(username, entries, year, month, operatingUsername);
                LoggerUtil.info(this.getClass(),
                        String.format("Saved worktime entry for user %s - %d/%d using file-based auth",
                                username, year, month));
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(),
                        String.format("Failed to save worktime entry for user %s: %s",
                                username, e.getMessage()));
                throw new RuntimeException("Failed to save worktime entry", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Modify loadUserEntries to always return a List (never null)
    private List<WorkTimeTable> loadUserEntries(String username, int year, int month) {
        try {
            // Get current username from SecurityContext if available
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                // Use normal security context flow
                List<WorkTimeTable> entries = dataAccess.readUserWorktime(username, year, month);
                return entries != null ? entries : new ArrayList<>();
            } else {
                // If no security context (AWT thread), use username from session
                return loadUserEntries(username, year, month, username);
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading user entries for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    public List<WorkTimeTable> loadUserEntries(String username, int year, int month, String operatingUsername) {
        try {
            List<WorkTimeTable> entries = dataAccess.readUserWorktime(username, year, month, operatingUsername);
            return entries != null ? entries : new ArrayList<>();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading user entries for %s: %s", username, e.getMessage()));
            return new ArrayList<>();
        }
    }

    // Load admin entries from network only - username removed as not needed
    private List<WorkTimeTable> loadAdminEntries(Integer userId, int year, int month) {
        try {
            List<WorkTimeTable> adminEntries = dataAccess.readNetworkAdminWorktime(year, month);
            return adminEntries.stream()
                    .filter(entry -> entry.getUserId().equals(userId))
                    .filter(entry -> entry.getAdminSync() == SyncStatus.ADMIN_EDITED
                            || entry.getAdminSync() == SyncStatus.ADMIN_BLANK)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error loading admin entries for user %d: %s", userId, e.getMessage()));
            return new ArrayList<>();
        }
    }

   // Check national holiday - network path only
    public boolean isNationalHoliday(LocalDate date) {
        lock.readLock().lock();
        try {
            List<WorkTimeTable> entries = dataAccess.readNetworkAdminWorktime(date.getYear(), date.getMonthValue());

            return entries.stream()
                    .anyMatch(entry ->
                            entry.getWorkDate().equals(date) &&
                                    WorkCode.NATIONAL_HOLIDAY_CODE.equals(entry.getTimeOffType()) &&
                                    SyncStatus.ADMIN_EDITED.equals(entry.getAdminSync()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error checking national holiday for %s: %s", date, e.getMessage()));
            return false;
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