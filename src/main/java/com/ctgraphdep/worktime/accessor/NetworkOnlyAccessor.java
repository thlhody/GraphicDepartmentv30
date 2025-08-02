package com.ctgraphdep.worktime.accessor;

import com.ctgraphdep.fileOperations.data.CheckRegisterDataService;
import com.ctgraphdep.fileOperations.data.RegisterDataService;
import com.ctgraphdep.fileOperations.data.TimeOffDataService;
import com.ctgraphdep.fileOperations.data.WorktimeDataService;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.utils.LoggerUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Network-only accessor - READ-ONLY NETWORK ACCESS.
 * Used when anyone (admin or user) views other user data.
 * All data types: worktime/register/checkregister/timeoff.
 * Network only, no backup, no cache, no sync - just for display.
 */
public class NetworkOnlyAccessor implements WorktimeDataAccessor {

    private final WorktimeDataService worktimeDataService;
    private final RegisterDataService registerDataService;
    private final CheckRegisterDataService checkRegisterDataService;
    private final TimeOffDataService timeOffDataService;

    public NetworkOnlyAccessor(WorktimeDataService worktimeDataService,
                               RegisterDataService registerDataService,
                               CheckRegisterDataService checkRegisterDataService,
                               TimeOffDataService timeOffDataService) {
        this.worktimeDataService = worktimeDataService;
        this.registerDataService = registerDataService;
        this.checkRegisterDataService = checkRegisterDataService;
        this.timeOffDataService = timeOffDataService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @Override
    public List<WorkTimeTable> readWorktime(String username, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Reading worktime from network-only (no backup/cache) for %s: %d/%d", username, year, month));

            // Direct network access only, no backup, no cache
            List<WorkTimeTable> entries = worktimeDataService.readUserFromNetworkOnly(username, year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error reading worktime from network for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void writeWorktimeWithStatus(String username, List<WorkTimeTable> entries, int year, int month, String userRole) {
        throw new UnsupportedOperationException("Network-only accessor is read-only for viewing other user data");

    }

    @Override
    public void writeWorktimeEntryWithStatus(String username, WorkTimeTable entry, String userRole) {
        throw new UnsupportedOperationException("Network-only accessor is read-only for viewing other user data");

    }

    @Override
    public List<RegisterEntry> readRegister(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Reading register from network-only (no backup/cache) for %s: %d/%d", username, year, month));

            // Direct network access only, no backup, no cache
            List<RegisterEntry> entries = registerDataService.readUserFromNetworkOnly(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading register from network for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<RegisterCheckEntry> readCheckRegister(String username, Integer userId, int year, int month) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Reading check register from network-only (no backup/cache) for %s: %d/%d", username, year, month));

            // Direct network access only, no backup, no cache
            List<RegisterCheckEntry> entries = checkRegisterDataService.readUserCheckRegisterFromNetworkOnly(username, userId, year, month);
            return entries != null ? entries : new ArrayList<>();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading check register from network for %s - %d/%d: %s", username, year, month, e.getMessage()), e);
            return new ArrayList<>();
        }
    }

    @Override
    public TimeOffTracker readTimeOffTracker(String username, Integer userId, int year) {
        try {
            LoggerUtil.debug(this.getClass(), String.format("Reading time off from network-only (no backup/cache) for %s: %d", username, year));

            // Direct network access only, no backup, no cache
            return timeOffDataService.readTrackerFromNetworkReadOnly(username, userId, year);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error reading time off from network for %s - %d: %s", username, year, e.getMessage()), e);
            return null;
        }
    }

    @Override
    public String getAccessType() {
        return "NETWORK_ONLY_READ";
    }

    @Override
    public boolean supportsWrite() {
        return false;
    }
}