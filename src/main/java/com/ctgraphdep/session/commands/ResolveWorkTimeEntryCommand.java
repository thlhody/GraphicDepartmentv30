package com.ctgraphdep.session.commands;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.config.CommandConstants;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ResolveWorkTimeEntryCommand extends BaseWorktimeUpdateSessionCommand<Boolean> {

    private final LocalDate entryDate;
    private final LocalDateTime explicitEndTime;

    public ResolveWorkTimeEntryCommand(String username, Integer userId, LocalDate entryDate, LocalDateTime endTime) {
        super(username, userId);
        validateCondition(entryDate != null, "Entry date cannot be null");
        this.entryDate = entryDate;
        this.explicitEndTime = endTime;
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithDefault(context, ctx -> {
            info(String.format("Resolving work time entry for user %s on %s", username, entryDate));

            // Get standardized time values
            LocalDate todayDate = getStandardCurrentDate(context);

            // Check if we're trying to resolve today's active session
            if (entryDate.equals(todayDate)) {
                WorkUsersSessionsStates currentSession = ctx.getCurrentSession(username, userId);

                // If there's an active session for today, don't allow resolution
                if (currentSession != null && currentSession.getDayStartTime() != null && currentSession.getDayStartTime().toLocalDate().equals(todayDate)) {
                    warn(String.format("Cannot resolve active session for current day (%s)", entryDate));
                    return false;
                }
            }

            // Determine end time for resolution
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : getStandardCurrentTime(context);

            // Use the base class session-independent resolution method
            resolveWorktimeEntryDirectly(entryDate, endTime, ctx);

            info(String.format("Successfully resolved work time entry for user %s on %s", username, entryDate));
            return true;

        }, false);
    }

    // ========================================================================
    // ABSTRACT METHOD IMPLEMENTATIONS - ResolveWorkTimeEntryCommand specific logic
    // ========================================================================

    @Override
    protected WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context) {
        // Resolve should only work with existing entries
        return findExistingEntry(workDate, context);
    }

    @Override
    protected void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization(CommandConstants.SPECIAL_WORKTIME_COMMAND);

        // Set resolved end time
        LocalDateTime endTime = explicitEndTime;
        if (endTime == null) {
            endTime = getStandardCurrentTime(context);
        }

        entry.setDayEndTime(endTime);
        entry.setAdminSync(MergingStatusConstants.USER_INPUT); // Resolved state
    }

    @Override
    protected void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization("post-special-day resolve work time entry");

        // Re-apply resolved sync status
        entry.setAdminSync(MergingStatusConstants.USER_INPUT);
    }

    @Override
    protected String getCommandDescription() {
        return CommandConstants.WORKTIME_COMMAND;
    }

    //Override to apply resolution-specific customizations
    @Override
    protected void applyResolutionCustomizations(WorkTimeTable entry, LocalDateTime endTime, SessionContext context) {
        logCustomization("resolution-specific customizations");

        // Ensure end time is set correctly
        entry.setDayEndTime(endTime);

        // Any additional resolution-specific logic can be added here
        debug(String.format("Applied resolution end time: %s", endTime));
    }
}
