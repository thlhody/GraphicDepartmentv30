package com.ctgraphdep.session.commands;

import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ResolveWorkTimeEntryCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final LocalDate entryDate;
    private final LocalDateTime explicitEndTime;

    public ResolveWorkTimeEntryCommand(String username, Integer userId, LocalDate entryDate, LocalDateTime endTime) {
        this.username = username;
        this.userId = userId;
        this.entryDate = entryDate;
        this.explicitEndTime = endTime;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            // Get standardized time values using the new validation system
            GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);

            // Use explicit end time if provided, otherwise use standardized current time
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : timeValues.getCurrentTime();

            // Load entries for the month of the entry date
            int year = entryDate.getYear();
            int month = entryDate.getMonthValue();

            List<WorkTimeTable> entries = context.getWorkTimeService()
                    .loadUserEntries(username, year, month, username);

            // Find the unresolved entry for this date
            WorkTimeTable entry = entries.stream()
                    .filter(e -> e.getWorkDate() != null && e.getWorkDate().equals(entryDate))
                    .filter(e -> e.getAdminSync() == SyncStatus.USER_IN_PROCESS)
                    .filter(e -> e.getDayStartTime() != null && e.getDayEndTime() == null)
                    .findFirst()
                    .orElse(null);

            if (entry == null) {
                LoggerUtil.warn(this.getClass(), "No unresolved work entry found for date: " + entryDate);
                return false;
            }

            // Get user schedule
            User user = context.getUserService().getUserById(userId).orElse(null);
            if (user == null) {
                LoggerUtil.warn(this.getClass(), "User not found: " + userId);
                return false;
            }

            int userSchedule = user.getSchedule();

            // Calculate raw work minutes using the appropriate calculation query
            int rawMinutes = calculateRawWorkMinutes(entry, endTime, context);

            // Calculate processed work time with lunch break rules
            WorkTimeCalculationResult result = context.calculateWorkTime(rawMinutes, userSchedule);

            // Update entry with all calculated values
            entry.setDayEndTime(endTime);
            entry.setTotalWorkedMinutes(rawMinutes);
            entry.setTotalOvertimeMinutes(result.getOvertimeMinutes());
            entry.setLunchBreakDeducted(result.isLunchDeducted());

            // Set status to USER_INPUT (resolved)
            entry.setAdminSync(SyncStatus.USER_INPUT);

            // Save the updated entry
            context.getWorkTimeService().saveWorkTimeEntry(
                    username, entry, year, month, username);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully resolved work entry for %s on %s. Raw minutes: %d, Overtime: %d",
                    username, entryDate, rawMinutes, result.getOvertimeMinutes()));

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resolving work time entry: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Calculates raw work minutes between start and end time, subtracting temporary stops
     * using the calculation context
     */
    private int calculateRawWorkMinutes(WorkTimeTable entry, LocalDateTime endTime, SessionContext context) {
        if (entry == null || entry.getDayStartTime() == null) {
            return 0;
        }

        // Use the dedicated method in SessionContext
        return context.calculateRawWorkMinutesForEntry(entry, endTime);
    }
}