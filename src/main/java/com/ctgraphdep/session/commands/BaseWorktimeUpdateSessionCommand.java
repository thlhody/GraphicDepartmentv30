package com.ctgraphdep.session.commands;

import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.model.DayType;
import com.ctgraphdep.session.util.SessionSpecialDayDetector;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Abstract base class for session commands that update worktime entries.
 * Eliminates duplication by providing common special day detection logic
 * while allowing each command to implement its specific customizations.
 * Common workflow:
 * 1. Extract work date from session
 * 2. Detect day type (existing timeOffType > weekend > regular)
 * 3. Find or create worktime entry (command-specific)
 * 4. Apply command-specific customizations
 * 5. Apply special day logic if needed
 * 6. Apply post-special-day customizations (re-apply fields that may have been modified)
 * 7. Save the updated entry
 */
public abstract class BaseWorktimeUpdateSessionCommand<T> extends BaseSessionCommand<T> {

    protected final String username;
    protected final Integer userId;

    protected BaseWorktimeUpdateSessionCommand(String username, Integer userId) {
        validateUsername(username);
        validateUserId(userId);
        this.username = username;
        this.userId = userId;
    }

    // Common logic for updating worktime entries with special day detection
    // This method orchestrates the entire workflow while delegating command-specific logic to subclasses
    protected final void updateWorktimeEntryWithSpecialDayLogic(WorkUsersSessionsStates session, SessionContext context) {
        try {
            // Step 1: Validate session and extract work date
            if (session.getDayStartTime() == null) {
                warn("Cannot update worktime entry: session has no start time");
                return;
            }

            LocalDate workDate = session.getDayStartTime().toLocalDate();
            debug(String.format("Updating worktime entry for date: %s (%s)", workDate, getCommandDescription()));

            // Step 2: Detect day type for special day logic
            DayType dayType = SessionSpecialDayDetector.detectDayType(workDate, username, userId, context);
            info(String.format("Detected day type: %s for %s", dayType, getCommandDescription()));

            // Step 3: Find or create worktime entry (command-specific implementation)
            WorkTimeTable entry = findOrCreateEntry(workDate, session, context);
            if (entry == null) {
                warn(String.format("Could not find or create worktime entry for %s", getCommandDescription()));
                return;
            }

            // Step 4: Update entry from session using SessionContext
            entry = context.updateEntryFromSession(entry, session);
            debug(String.format("Updated entry from session for %s", getCommandDescription()));

            // Step 5: Apply command-specific customizations BEFORE special day logic
            applyCommandSpecificCustomizations(entry, session, context);
            debug(String.format("Applied command-specific customizations for %s", getCommandDescription()));

            // Step 6: Apply special day logic if needed
            if (dayType.requiresSpecialOvertimeLogic()) {
                info(String.format("Applying special day logic for %s on %s day", getCommandDescription(), dayType));

                entry = SessionSpecialDayDetector.applySpecialDayLogic(entry, session, dayType);

                info(String.format("Special day logic applied for %s: timeOffType=%s, regularMinutes=%d, overtimeMinutes=%d",
                        getCommandDescription(), entry.getTimeOffType(), entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                        entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0));
            } else {
                debug(String.format("Regular day - no special logic needed for %s", getCommandDescription()));
            }

            // Step 7: Apply post-special-day customizations (re-apply command-specific fields that may have been modified)
            applyPostSpecialDayCustomizations(entry, session, context);
            debug(String.format("Applied post-special-day customizations for %s", getCommandDescription()));

            // Step 8: Save the updated entry
            context.saveSessionWorktime(username, entry, workDate.getYear(), workDate.getMonthValue());

            info(String.format("Successfully updated worktime entry for %s: date=%s, timeOffType=%s, totalMinutes=%d, overtimeMinutes=%d",
                    getCommandDescription(), workDate, entry.getTimeOffType(), entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                    entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0));

        } catch (Exception e) {
            error(String.format("Failed to update worktime entry for %s: %s", getCommandDescription(), e.getMessage()), e);
        }
    }

    // RESOLUTION WORKFLOW: Session-independent logic for resolving historical worktime entries
    // This method works directly with worktime entries without requiring active sessions
    // Used for resolving historical entries where sessions are no longer available (24+ hours old)
    protected final void resolveWorktimeEntryDirectly(LocalDate entryDate, LocalDateTime endTime, SessionContext context) {
        try {
            debug(String.format("Resolving worktime entry directly for date: %s (%s)", entryDate, getCommandDescription()));

            // Step 1: Find the existing worktime entry (no session needed)
            WorkTimeTable entry = context.findSessionEntry(username, userId, entryDate);
            if (entry == null) {
                warn(String.format("Could not find worktime entry for resolution: %s", getCommandDescription()));
                return;
            }

            // Step 2: Validate entry needs resolution
            if (!MergingStatusConstants.USER_IN_PROCESS.equals(entry.getAdminSync())) {
                info(String.format("Entry for %s is already resolved (status: %s)", entryDate, entry.getAdminSync()));
                return;
            }

            // Step 3: Set end time and calculate work minutes
            entry.setDayEndTime(endTime);
            int rawWorkMinutes = context.calculateRawWorkMinutesForEntry(entry, endTime);
            entry.setTotalWorkedMinutes(rawWorkMinutes);

            // Step 4: Detect day type for special day logic (same as session-based workflow)
            DayType dayType = SessionSpecialDayDetector.detectDayType(entryDate, username, userId, context);
            info(String.format("Detected day type: %s for %s", dayType, getCommandDescription()));

            // Step 5: Apply special day logic if needed (create minimal session for API compatibility)
            if (dayType.requiresSpecialOvertimeLogic()) {
                info(String.format("Applying special day logic for %s on %s day", getCommandDescription(), dayType));

                // Create minimal session object for special day logic API compatibility
                WorkUsersSessionsStates tempSession = getWorkUsersSessionsStates(entry);
                // Note: LunchBreakDeducted is calculated from worktime, not session

                // Apply special day logic using correct method signature
                entry = SessionSpecialDayDetector.applySpecialDayLogic(entry, tempSession, dayType);

                info(String.format("Special day logic applied for %s: timeOffType=%s, regularMinutes=%d, overtimeMinutes=%d",
                        getCommandDescription(), entry.getTimeOffType(), entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                        entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0));
            } else {
                debug(String.format("Regular day - no special logic needed for %s", getCommandDescription()));
            }

            // Step 6: Apply command-specific resolution customizations
            applyResolutionCustomizations(entry, endTime, context);
            debug(String.format("Applied resolution customizations for %s", getCommandDescription()));

            // Step 7: Mark as resolved
            entry.setAdminSync(MergingStatusConstants.USER_INPUT);

            // Step 8: Save the resolved entry
            context.saveSessionWorktime(username, entry, entryDate.getYear(), entryDate.getMonthValue());

            info(String.format("Successfully resolved worktime entry for %s: date=%s, timeOffType=%s, totalMinutes=%d, overtimeMinutes=%d",
                    getCommandDescription(), entryDate, entry.getTimeOffType(), entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                    entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0));

        } catch (Exception e) {
            error(String.format("Failed to resolve worktime entry for %s: %s", getCommandDescription(), e.getMessage()), e);
        }
    }

    private @NotNull WorkUsersSessionsStates getWorkUsersSessionsStates(WorkTimeTable entry) {
        WorkUsersSessionsStates tempSession = new WorkUsersSessionsStates();
        tempSession.setUsername(username);
        tempSession.setUserId(userId);
        tempSession.setDayStartTime(entry.getDayStartTime());
        tempSession.setDayEndTime(entry.getDayEndTime());
        tempSession.setTotalWorkedMinutes(entry.getTotalWorkedMinutes());
        tempSession.setTotalOvertimeMinutes(entry.getTotalOvertimeMinutes());
        tempSession.setTotalTemporaryStopMinutes(entry.getTotalTemporaryStopMinutes());
        tempSession.setTemporaryStopCount(entry.getTemporaryStopCount());
        tempSession.setTemporaryStops(entry.getTemporaryStops());
        return tempSession;
    }

    // Apply command-specific customizations during resolution
    // Default implementation does nothing - subclasses can override for specific resolution logic
    protected void applyResolutionCustomizations(WorkTimeTable entry, LocalDateTime endTime, SessionContext context) {
        // Default: no additional customizations
        // Subclasses can override for command-specific resolution logic
    }

    // ========================================================================
    // ABSTRACT METHODS - Command-specific implementations
    // ========================================================================

    // Find existing entry or create new entry based on command requirements.
    protected abstract WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context);

    //Apply command-specific customizations to the worktime entry.
    protected abstract void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context);

    // Apply post-special-day customizations to the worktime entry.This happens AFTER special day logic is applied.
    // Used to re-apply command-specific fields that might have been modified by special day logic.
    protected abstract void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context);

    // Get a description of this command for logging purposes
    protected abstract String getCommandDescription();

    // ========================================================================
    // HELPER METHODS for common patterns
    // ========================================================================

    // Helper method to find existing entry with proper error handling
    protected final WorkTimeTable findExistingEntry(LocalDate workDate, SessionContext context) {
        WorkTimeTable entry = context.findSessionEntry(username, userId, workDate);
        if (entry == null) {
            warn(String.format("No worktime entry found for user %s on %s for %s", username, workDate, getCommandDescription()));
        }
        return entry;
    }

    // Helper method to find existing entry or create new one
    protected final WorkTimeTable findOrCreateNewEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context) {
        WorkTimeTable entry = context.findSessionEntry(username, userId, workDate);

        if (entry == null) {
            debug(String.format("Creating new worktime entry from session for %s", getCommandDescription()));
            entry = context.createWorktimeEntryFromSession(session);
        } else {
            debug(String.format("Found existing worktime entry for %s", getCommandDescription()));
        }

        return entry;
    }

    // Helper method to log command-specific customizations
    protected final void logCustomization(String customization) {
        debug(String.format("Applying %s customization for %s", customization, getCommandDescription()));
    }
}