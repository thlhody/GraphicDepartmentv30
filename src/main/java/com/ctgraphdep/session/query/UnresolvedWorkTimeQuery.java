package com.ctgraphdep.session.query;

import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class UnresolvedWorkTimeQuery implements SessionQuery<List<WorkTimeTable>> {
    private final String username;
    private final Integer userId;

    public UnresolvedWorkTimeQuery(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public List<WorkTimeTable> execute(SessionContext context) {
        try {
            LocalDate today = LocalDate.now();
            List<WorkTimeTable> unresolvedEntries = new ArrayList<>();

            // Get standardized time values using the new validation system
            GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);
            // Only check current month and previous month (for entries from the end of previous month)
            LocalDate currentMonth = timeValues.getCurrentDate();
            LocalDate previousMonth = today.minusMonths(1);

            // Load current month entries
            List<WorkTimeTable> currentMonthEntries = context.getWorkTimeService().loadUserEntries(username, currentMonth.getYear(), currentMonth.getMonthValue(), username);

            if (currentMonthEntries != null) {
                unresolvedEntries.addAll(currentMonthEntries);
            }

            // Also check previous month
            List<WorkTimeTable> previousMonthEntries = context.getWorkTimeService().loadUserEntries(username, previousMonth.getYear(), previousMonth.getMonthValue(), username);

            if (previousMonthEntries != null) {
                unresolvedEntries.addAll(previousMonthEntries);
            }

            // Filter for unresolved entries (USER_IN_PROCESS status from before today)
            List<WorkTimeTable> result = unresolvedEntries.stream()
                    .filter(entry -> entry.getAdminSync() == SyncStatusWorktime.USER_IN_PROCESS)
                    .filter(entry -> entry.getWorkDate() != null && entry.getWorkDate().isBefore(today))
                    .filter(entry -> entry.getDayStartTime() != null && entry.getDayEndTime() == null)
                    .sorted(Comparator.comparing(WorkTimeTable::getWorkDate).reversed())
                    .collect(Collectors.toList());

            LoggerUtil.info(this.getClass(), String.format("Found %d unresolved work entries for user %s", result.size(), username));

            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error finding unresolved work time entries: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}