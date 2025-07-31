package com.ctgraphdep.worktime.accessor;

import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Admin own data accessor - ADMIN FILES ONLY.
 * Used when admin accesses admin files (special admin worktime data).
 * Only supports worktime operations on admin files.
 * Does NOT support register/checkregister/timeoff (admin files don't contain these).
 */
public class AdminOwnDataAccessor implements WorktimeDataAccessor {

    private final WorktimeDataService worktimeDataService;

    public AdminOwnDataAccessor(WorktimeDataService worktimeDataService) {
        this.worktimeDataService = worktimeDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public List<WorkTimeTable> readWorktime(String username, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Admin reading own admin worktime files: %d/%d", year, month));

            // Read from admin local files with network fallback
            List<WorkTimeTable> entries = worktimeDataService.readAdminLocalReadOnly(year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading admin worktime for %d/%d: %s", year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void writeWorktimeWithStatus(String username, List<WorkTimeTable> entries,
                                        int year, int month, String userRole) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Admin writing %d worktime entries with status management: %d/%d (role: %s)",
                    entries.size(), year, month, userRole));

            // ADMIN STATUS MANAGEMENT: All admin file operations get ADMIN_INPUT status
            List<WorkTimeTable> processedEntries = new ArrayList<>();
            for (WorkTimeTable entry : entries) {
                WorkTimeTable processedEntry = cloneEntry(entry);

                // Admin operations always set ADMIN_INPUT status
                processedEntry.setAdminSync(MergingStatusConstants.ADMIN_INPUT);

                LoggerUtil.debug(this.getClass(), String.format(
                        "Set ADMIN_INPUT status for user %d on %s",
                        processedEntry.getUserId(), processedEntry.getWorkDate()));

                processedEntries.add(processedEntry);
            }

            // Sort entries for consistency
            processedEntries.sort(Comparator.comparing(WorkTimeTable::getWorkDate)
                    .thenComparingInt(WorkTimeTable::getUserId));

            // Write to admin files with sync and backup
            worktimeDataService.writeAdminLocalWithSyncAndBackup(processedEntries, year, month);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully wrote %d admin worktime entries with ADMIN_INPUT status to %d/%d",
                    processedEntries.size(), year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error writing admin worktime with status for %d/%d: %s", year, month, e.getMessage()), e);
            throw new RuntimeException("Failed to write admin worktime entries with status", e);
        }
    }

    @Override
    public void writeWorktimeEntryWithStatus(String username, WorkTimeTable entry, String userRole) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Admin writing single worktime entry with status: user %d on %s (role: %s)",
                    entry.getUserId(), entry.getWorkDate(), userRole));

            LocalDate date = entry.getWorkDate();
            int year = date.getYear();
            int month = date.getMonthValue();

            // Load existing entries
            List<WorkTimeTable> existingEntries = readWorktime(username, year, month);

            // Remove existing entry for same user/date if any
            existingEntries.removeIf(existing ->
                    existing.getUserId().equals(entry.getUserId()) &&
                            existing.getWorkDate().equals(date));

            // Add new entry with admin status
            WorkTimeTable processedEntry = cloneEntry(entry);
            processedEntry.setAdminSync(MergingStatusConstants.ADMIN_INPUT);
            existingEntries.add(processedEntry);

            // Write updated entries
            writeWorktimeWithStatus(username, existingEntries, year, month, userRole);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Successfully wrote single admin entry with ADMIN_INPUT status for user %d on %s",
                    entry.getUserId(), date));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error writing single admin entry with status for user %d on %s: %s",
                    entry.getUserId(), entry.getWorkDate(), e.getMessage()), e);
            throw new RuntimeException("Failed to write admin worktime entry with status", e);
        }
    }

    @Override
    public List<RegisterEntry> readRegister(String username, Integer userId, int year, int month) {
        throw new UnsupportedOperationException("Admin files don't contain register data. Use NetworkOnlyAccessor to view user register data.");
    }

    @Override
    public List<RegisterCheckEntry> readCheckRegister(String username, Integer userId, int year, int month) {
        throw new UnsupportedOperationException("Admin files don't contain check register data. Use NetworkOnlyAccessor to view user check register data.");
    }

    @Override
    public TimeOffTracker readTimeOffTracker(String username, Integer userId, int year) {
        throw new UnsupportedOperationException("Admin files don't contain time off data. Use NetworkOnlyAccessor to view user time off data.");
    }

    @Override
    public String getAccessType() {
        return "ADMIN_OWN_FILES";
    }

    @Override
    public boolean supportsWrite() {
        return true;
    }

    /**
     * Clone entry to avoid modifying the original
     */
    private WorkTimeTable cloneEntry(WorkTimeTable original) {
        WorkTimeTable clone = new WorkTimeTable();

        // Copy all fields
        clone.setUserId(original.getUserId());
        clone.setWorkDate(original.getWorkDate());
        clone.setDayStartTime(original.getDayStartTime());
        clone.setDayEndTime(original.getDayEndTime());
        clone.setTotalWorkedMinutes(original.getTotalWorkedMinutes());
        clone.setTotalOvertimeMinutes(original.getTotalOvertimeMinutes());
        clone.setTotalTemporaryStopMinutes(original.getTotalTemporaryStopMinutes());
        clone.setTemporaryStopCount(original.getTemporaryStopCount());
        clone.setLunchBreakDeducted(original.isLunchBreakDeducted());
        clone.setTimeOffType(original.getTimeOffType());
        clone.setAdminSync(original.getAdminSync()); // Will be overridden with admin status

        return clone;
    }
}


