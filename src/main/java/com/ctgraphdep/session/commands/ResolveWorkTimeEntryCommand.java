package com.ctgraphdep.session.commands;

import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.worktime.WorkTimeCalculationResultDTO;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REFACTORED ResolveWorkTimeEntryCommand using new SessionContext adapter methods
 */
public class ResolveWorkTimeEntryCommand extends BaseSessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final LocalDate entryDate;
    private final LocalDateTime explicitEndTime;

    public ResolveWorkTimeEntryCommand(String username, Integer userId, LocalDate entryDate, LocalDateTime endTime) {
        validateUsername(username);
        validateUserId(userId);
        validateCondition(entryDate != null, "Entry date cannot be null");

        this.username = username;
        this.userId = userId;
        this.entryDate = entryDate;
        this.explicitEndTime = endTime;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithDefault(context, ctx -> {
            info(String.format("Resolving work time entry for user %s on %s", username, entryDate));

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = ctx.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = ctx.getValidationService().execute(timeCommand);

            // Use explicit end time if provided, otherwise use standardized current time
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : timeValues.getCurrentTime();
            debug(String.format("Using end time: %s", endTime));

            // REFACTORED: Load entries using new SessionContext adapter method
            int year = entryDate.getYear();
            int month = entryDate.getMonthValue();
            List<WorkTimeTable> entries = ctx.loadSessionWorktime(username, year, month);

            // Find the unresolved entry for this date
            WorkTimeTable entry = entries.stream()
                    .filter(e -> e.getWorkDate() != null && e.getWorkDate().equals(entryDate))
                    .filter(e -> e.getAdminSync() == SyncStatusMerge.USER_IN_PROCESS)
                    .filter(e -> e.getDayStartTime() != null && e.getDayEndTime() == null)
                    .findFirst()
                    .orElse(null);

            if (entry == null) {
                warn("No unresolved work entry found for date: " + entryDate);
                return false;
            }

            // Get user schedule
            User user = ctx.getUserService().getUserById(userId).orElse(null);
            if (user == null) {
                warn("User not found: " + userId);
                return false;
            }

            int userSchedule = user.getSchedule();
            debug(String.format("User schedule: %d hours", userSchedule));

            // Calculate raw work minutes using the appropriate calculation query
            int rawMinutes = calculateRawWorkMinutes(entry, endTime, ctx);
            debug(String.format("Calculated raw work minutes: %d", rawMinutes));

            // Calculate processed work time with lunch break rules
            WorkTimeCalculationResultDTO result = ctx.calculateWorkTime(rawMinutes, userSchedule);
            debug(String.format("Processed minutes: %d, Overtime: %d, Lunch deducted: %b",
                    result.getProcessedMinutes(), result.getOvertimeMinutes(), result.isLunchDeducted()));

            // Update entry with all calculated values
            updateWorkTimeEntry(entry, endTime, rawMinutes, result);

            // REFACTORED: Save entry using new SessionContext adapter method
            ctx.saveSessionWorktime(username, entry, year, month);

            info(String.format("Successfully resolved work entry for %s on %s. Raw minutes: %d, Overtime: %d",
                    username, entryDate, rawMinutes, result.getOvertimeMinutes()));

            return true;
        }, false);
    }

    /**
     * Updates work time entry with calculated values
     */
    private void updateWorkTimeEntry(WorkTimeTable entry, LocalDateTime endTime, int rawMinutes, WorkTimeCalculationResultDTO result) {
        entry.setDayEndTime(endTime);
        entry.setTotalWorkedMinutes(rawMinutes);
        entry.setTotalOvertimeMinutes(result.getOvertimeMinutes());
        entry.setLunchBreakDeducted(result.isLunchDeducted());
        entry.setAdminSync(SyncStatusMerge.USER_INPUT); // RESOLVED - no longer in process
    }

    /**
     * Calculates raw work minutes between start and end time, subtracting temporary stops
     * using the calculation context
     */
    private int calculateRawWorkMinutes(WorkTimeTable entry, LocalDateTime endTime, SessionContext context) {
        if (entry == null || entry.getDayStartTime() == null) {
            return 0;
        }
        return context.calculateRawWorkMinutesForEntry(entry, endTime);
    }
}