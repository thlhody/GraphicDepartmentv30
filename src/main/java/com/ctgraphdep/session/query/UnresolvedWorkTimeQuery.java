package com.ctgraphdep.session.query;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REFACTORED UnresolvedWorkTimeQuery using new SessionContext adapter methods
 * instead of deprecated WorktimeManagementService
 */
public class UnresolvedWorkTimeQuery implements SessionQuery<List<WorkTimeTable>> {

    private final String username;

    public UnresolvedWorkTimeQuery(String username) {
        this.username = username;
    }

    @Override
    public List<WorkTimeTable> execute(SessionContext context) {
        try {


            LocalDate currentDate = context.getCurrentStandardDate();
            LocalDate previousMonth = currentDate.minusMonths(1);

            List<WorkTimeTable> unresolvedEntries = new ArrayList<>();

            // REFACTORED: Load current month entries using new SessionContext adapter method
            List<WorkTimeTable> currentMonthEntries = context.loadSessionWorktime(username, currentDate.getYear(), currentDate.getMonthValue());

            if (currentMonthEntries != null) {
                unresolvedEntries.addAll(currentMonthEntries);
            }

            // REFACTORED: Also check previous month using new SessionContext adapter method
            List<WorkTimeTable> previousMonthEntries = context.loadSessionWorktime(username, previousMonth.getYear(), previousMonth.getMonthValue());

            if (previousMonthEntries != null) {
                unresolvedEntries.addAll(previousMonthEntries);
            }

            // Filter for unresolved entries (USER_IN_PROCESS status from before today)
            List<WorkTimeTable> result = unresolvedEntries.stream()
                    .filter(entry -> Objects.equals(entry.getAdminSync(), MergingStatusConstants.USER_IN_PROCESS))
                    .filter(entry -> entry.getWorkDate() != null && entry.getWorkDate().isBefore(currentDate))
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