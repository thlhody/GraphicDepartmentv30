package com.ctgraphdep.session.commands;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.config.CommandConstants;
import com.ctgraphdep.session.model.DayType;
import com.ctgraphdep.session.query.WorkScheduleQuery;
import com.ctgraphdep.session.util.SessionSpecialDayDetector;
import com.ctgraphdep.config.WorkCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class EndDayCommand extends BaseWorktimeUpdateSessionCommand<WorkUsersSessionsStates> {

    private final Integer finalMinutes;
    private final LocalDateTime explicitEndTime;
    private static final long END_COMMAND_COOLDOWN_MS = 2000; // 2 seconds

    // Freshness threshold - refresh calculations if last activity is more than this many minutes before end time
    private static final long CALCULATION_FRESHNESS_THRESHOLD_MINUTES = 2;

    public EndDayCommand(String username, Integer userId, Integer finalMinutes, LocalDateTime endTime) {
        super(username, userId);

        if (finalMinutes != null) {
            validateCondition(finalMinutes >= 0, "Final minutes cannot be negative");
        }

        this.finalMinutes = finalMinutes;
        this.explicitEndTime = endTime;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        return executeWithDeduplication(context, username, this::executeEndDayLogic, null, END_COMMAND_COOLDOWN_MS);
    }

    public WorkUsersSessionsStates executeEndDayLogic(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            info(String.format("Executing EndDayCommand with automatic calculation refresh for user %s", username));

            LocalDateTime today = getStandardCurrentTime(ctx);

            // Get and validate session
            WorkUsersSessionsStates session = ctx.getCurrentSession(username, userId);
            validateSessionExists(session, CommandConstants.END_SESSION);

            // Use explicit end time if provided, otherwise use standardized current time
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : today;

            // ENHANCED: Freshness check and calculation refresh
            if (needsCalculationRefresh(session, endTime)) {
                info(String.format("Session calculations are stale for user %s, refreshing before end", username));
                session = refreshSessionCalculations(session, endTime, ctx);
            } else {
                debug(String.format("Session calculations are fresh for user %s, no refresh needed", username));
            }

            // If user is in temporary stop, close the last temp stop
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                debug("Session is in temporary stop state, processing resume from temp stop");
                // Use CalculationService directly instead of command
                session = context.getCalculationService().processResumeFromTempStop(session, endTime);
            }

            // Calculate final worked minutes (now using refreshed session data)
            Integer effectiveFinalMinutes = calculateEffectiveFinalMinutes(session, endTime, ctx);

            // Get user schedule for proper end day processing
            int userSchedule = getUserSchedule(ctx);

            // Process end session operation using CalculationService
            session = processEndSession(session, endTime, ctx, effectiveFinalMinutes, userSchedule);

            // ENHANCED: Update worktime entry with special day detection using abstract base class
            updateWorktimeEntryWithSpecialDayLogic(session, ctx);

            // Save the updated session
            SaveSessionCommand saveCommand = ctx.getCommandFactory().createSaveSessionCommand(session);
            ctx.executeCommand(saveCommand);

            // Clean up monitoring
            manageMonitoringState(context, CommandConstants.DEACTIVATE, username);

            info(String.format("Successfully ended session for user %s with final minutes: %d (refreshed: %s)",
                    username, effectiveFinalMinutes != null ? effectiveFinalMinutes : 0,
                    needsCalculationRefresh(ctx.getCurrentSession(username, userId), endTime) ? "yes" : "no"));

            return session;
        });
    }

    // ========================================================================
    // ENHANCED CALCULATION REFRESH METHODS
    // ========================================================================

    /**
     * Determines if session calculations need to be refreshed based on staleness.
     * Checks if lastActivity is significantly before the end time, indicating stale calculations.
     */
    private boolean needsCalculationRefresh(WorkUsersSessionsStates session, LocalDateTime endTime) {
        if (session == null || endTime == null) {
            debug("Session or endTime is null - defaulting to refresh calculations");
            return true; // Safe default - refresh if uncertain
        }

        if (session.getLastActivity() == null) {
            debug("Session has no lastActivity timestamp - refreshing calculations");
            return true; // No activity timestamp means we should refresh
        }

        // Calculate time between last activity and end time
        long minutesSinceLastActivity = ChronoUnit.MINUTES.between(session.getLastActivity(), endTime);

        boolean needsRefresh = minutesSinceLastActivity > CALCULATION_FRESHNESS_THRESHOLD_MINUTES;

        debug(String.format("Calculation freshness check: lastActivity=%s, endTime=%s, gap=%d minutes, needsRefresh=%s",
                session.getLastActivity(), endTime, minutesSinceLastActivity, needsRefresh));

        return needsRefresh;
    }

    /**
     * Refreshes session calculations using the current end time.
     * This ensures we have accurate work time calculations for the end operation.
     */
    private WorkUsersSessionsStates refreshSessionCalculations(WorkUsersSessionsStates session,
                                                               LocalDateTime endTime,
                                                               SessionContext context) {
        try {
            debug(String.format("Refreshing session calculations for user %s with end time %s", username, endTime));

            // Use cache-only update to refresh calculations without file I/O conflicts
            UpdateSessionCalculationsCommand updateCommand =
                    context.getCommandFactory().createUpdateSessionCalculationsCacheOnlyCommand(session, endTime);

            WorkUsersSessionsStates refreshedSession = context.executeCommand(updateCommand);

            if (refreshedSession != null) {
                info(String.format("Successfully refreshed calculations for user %s: totalWorked=%d, finalWorked=%d",
                        username,
                        refreshedSession.getTotalWorkedMinutes() != null ? refreshedSession.getTotalWorkedMinutes() : 0,
                        refreshedSession.getFinalWorkedMinutes() != null ? refreshedSession.getFinalWorkedMinutes() : 0));
                return refreshedSession;
            } else {
                warn(String.format("Calculation refresh returned null for user %s, using original session", username));
                return session; // Fallback to original session
            }

        } catch (Exception e) {
            warn(String.format("Failed to refresh calculations for user %s: %s - using existing values", username, e.getMessage()));
            return session; // Fallback to existing session on error
        }
    }

    // ========================================================================
    // ABSTRACT METHOD IMPLEMENTATIONS - EndDayCommand specific logic
    // ========================================================================

    @Override
    protected WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context) {
        // EndDayCommand should find existing entry or create new one if needed
        return findOrCreateNewEntry(workDate, session, context);
    }

    @Override
    protected void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization(CommandConstants.END_DAY);

        // Set final end time - this is critical for end day
        LocalDateTime endTime = explicitEndTime;
        if (endTime == null) {
            // Get standardized current time using correct pattern
            endTime = getStandardCurrentTime(context);
        }

        entry.setDayEndTime(endTime);

        // Transfer temporaryStops data from session to worktime
        entry.setTemporaryStops(session.getTemporaryStops());

        // Mark as completed (this may be modified by special day logic, but we'll re-apply it later)
        entry.setAdminSync(MergingStatusConstants.USER_INPUT); // Final state, not in-process

        debug(String.format("Set end time to %s and marked as completed", endTime));
    }

    @Override
    protected void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        logCustomization(CommandConstants.SPECIAL_END_DAY);

        // Re-apply temporaryStops data that might have been modified by special day logic
        entry.setTemporaryStops(session.getTemporaryStops());

        // Re-apply final sync status (may have been modified by special day logic)
        entry.setAdminSync(MergingStatusConstants.USER_INPUT); // Final state, not in-process

        // For regular days, apply normal overtime calculation if special day logic wasn't applied
        LocalDate workDate = session.getDayStartTime().toLocalDate();
        DayType dayType = SessionSpecialDayDetector.detectDayType(workDate, username, userId, context);

        if (!dayType.requiresSpecialOvertimeLogic()) {
            debug("Applying regular day overtime calculation");
            applyRegularDayOvertimeLogic(entry, session, context, workDate);

            // ENHANCED: Auto-detect short days (ZS) after overtime calculation
            detectAndHandleShortDay(entry, session, context);
        }

        debug("Ensured final end day state is properly set");
    }

    @Override
    protected String getCommandDescription() {
        return CommandConstants.END_DAY;
    }

    // ========================================================================
    // UPDATED HELPER METHODS - Using CalculationService instead of command pattern
    // ========================================================================

    /**
     * Calculate effective final minutes using CalculationService
     * UPDATED: Uses direct service call instead of command pattern
     */
    private Integer calculateEffectiveFinalMinutes(WorkUsersSessionsStates session, LocalDateTime endTime, SessionContext context) {
        Integer effectiveFinalMinutes = finalMinutes;
        if (effectiveFinalMinutes == null) {
            try {
                // Use direct call to CalculationService instead of command pattern
                effectiveFinalMinutes = context.calculateRawWorkMinutes(session, endTime);
                debug(String.format("Calculated final minutes from refreshed session: %d", effectiveFinalMinutes));
            } catch (Exception e) {
                warn(String.format("Error calculating final minutes, using session value: %s", e.getMessage()));
                effectiveFinalMinutes = session.getTotalWorkedMinutes();
            }
        } else {
            debug(String.format("Using provided final minutes: %d", effectiveFinalMinutes));
        }
        return effectiveFinalMinutes;
    }

    /**
     * Process end session using CalculationService
     * UPDATED: Passes user schedule to the calculation service
     */
    private WorkUsersSessionsStates processEndSession(WorkUsersSessionsStates session, LocalDateTime endTime, SessionContext context, Integer effectiveFinalMinutes, int userSchedule) {
        // Use direct call to CalculationService with user schedule
        return context.calculateEndDayValues(session, endTime, effectiveFinalMinutes, userSchedule);
    }

    /**
     * Get user schedule for this command
     * PRESERVED: Same logic as original EndDayCommand
     */
    private int getUserSchedule(SessionContext context) {
        try {
            return context.getUserService().getUserById(userId).map(User::getSchedule).orElse(WorkCode.INTERVAL_HOURS_C);
        } catch (Exception e) {
            warn(String.format("Error getting user schedule for %s, using default 8 hours: %s", username, e.getMessage()));
            return WorkCode.INTERVAL_HOURS_C;
        }
    }

    /**
     * Apply regular day overtime logic using schedule information
     * PRESERVED: Same logic but updated to use direct query execution
     */
    private void applyRegularDayOvertimeLogic(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context, LocalDate workDate) {
        try {
            Integer userSchedule = context.getUserService().getUserById(session.getUserId())
                    .map(User::getSchedule)
                    .orElse(WorkCode.INTERVAL_HOURS_C);

            WorkScheduleQuery query = context.getCommandFactory().createWorkScheduleQuery(workDate, userSchedule);
            WorkScheduleQuery.ScheduleInfo scheduleInfo = context.executeQuery(query);

            if (scheduleInfo != null && session.getTotalWorkedMinutes() != null) {
                int overtimeMinutes = scheduleInfo.calculateOvertimeMinutes(session.getTotalWorkedMinutes());
                if (overtimeMinutes > 0 && (entry.getTotalOvertimeMinutes() == null || entry.getTotalOvertimeMinutes() == 0)) {
                    entry.setTotalOvertimeMinutes(overtimeMinutes);
                    debug(String.format("Applied regular day overtime: %d minutes", overtimeMinutes));
                }
            }
        } catch (Exception e) {
            warn(String.format("Error calculating regular day overtime: %s", e.getMessage()));
        }
    }

    /**
     * AUTO-DETECT short days (ZS) - User-level automation for production
     * If user worked less than schedule AND has no time off type, mark as ZS
     * ZS entries will be filled from overtime during month-end processing
     */
    private void detectAndHandleShortDay(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context) {
        try {
            // Skip if entry already has a time off type
            if (entry.getTimeOffType() != null && !entry.getTimeOffType().trim().isEmpty()) {
                debug("Entry already has time off type, skipping ZS detection");
                return;
            }

            // Get user schedule
            int userSchedule = getUserSchedule(context);
            int scheduleMinutes = userSchedule * 60;

            // Get worked minutes
            Integer workedMinutes = entry.getTotalWorkedMinutes();
            if (workedMinutes == null || workedMinutes <= 0) {
                debug("No worked minutes, skipping ZS detection");
                return;
            }

            // Check if short day (worked < schedule)
            if (workedMinutes < scheduleMinutes) {
                int missingMinutes = scheduleMinutes - workedMinutes;
                int missingHours = (int) Math.ceil(missingMinutes / 60.0);

                // Store as "ZS-5" format (display format with missing hours)
                // This avoids recalculating from start/end times every time
                entry.setTimeOffType(WorkCode.SHORT_DAY_CODE + "-" + missingHours);

                // ZS entries don't store overtime (it's deducted from pool)
                entry.setTotalOvertimeMinutes(0);

                info(String.format("AUTO-DETECTED short day for user %s on %s: worked %d/%d minutes (missing %d hours). Marked as %s.",
                        username, entry.getWorkDate(), workedMinutes, scheduleMinutes, missingHours, entry.getTimeOffType()));
            } else {
                debug(String.format("Full day worked: %d/%d minutes, no ZS marking needed", workedMinutes, scheduleMinutes));
            }

        } catch (Exception e) {
            warn(String.format("Error detecting short day for user %s: %s", username, e.getMessage()));
        }
    }
}