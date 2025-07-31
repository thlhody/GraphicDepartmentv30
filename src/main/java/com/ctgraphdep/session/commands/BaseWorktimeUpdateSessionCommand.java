package com.ctgraphdep.session.commands;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.model.DayType;
import com.ctgraphdep.session.util.SessionSpecialDayDetector;

import java.time.LocalDate;

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
 *
 * @param <T> The command result type
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

    /**
     * MAIN WORKFLOW: Common logic for updating worktime entries with special day detection
     * This method orchestrates the entire workflow while delegating command-specific logic to subclasses
     */
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
                        getCommandDescription(),
                        entry.getTimeOffType(),
                        entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
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
                    getCommandDescription(), workDate, entry.getTimeOffType(),
                    entry.getTotalWorkedMinutes() != null ? entry.getTotalWorkedMinutes() : 0,
                    entry.getTotalOvertimeMinutes() != null ? entry.getTotalOvertimeMinutes() : 0));

        } catch (Exception e) {
            error(String.format("Failed to update worktime entry for %s: %s", getCommandDescription(), e.getMessage()), e);
        }
    }

    // ========================================================================
    // ABSTRACT METHODS - Command-specific implementations
    // ========================================================================

    /**
     * Find existing entry or create new entry based on command requirements.
     * Examples:
     * - StartDayCommand: Find existing or create new
     * - EndDayCommand: Find existing or create new
     * - TempStopCommands: Find existing only (warn if not found)
     * - ResumeCommands: Find existing only (warn if not found)
     * @param workDate The work date for the entry
     * @param session The current session
     * @param context The session context
     * @return The worktime entry to update, or null if not found/created
     */
    protected abstract WorkTimeTable findOrCreateEntry(LocalDate workDate, WorkUsersSessionsStates session, SessionContext context);

    /**
     * Apply command-specific customizations to the worktime entry.
     * This happens BEFORE special day logic is applied.
     * Examples:
     * - StartDayCommand: Set initial values, preserve existing timeOffType
     * - EndDayCommand: Set final end time, mark as completed
     * - TempStopCommands: Update temp stop counts and minutes
     * - ResumeCommands: Update resume-specific fields
     * @param entry The worktime entry to customize
     * @param session The current session
     * @param context The session context
     */
    protected abstract void applyCommandSpecificCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context);

    /**
     * Apply post-special-day customizations to the worktime entry.
     * This happens AFTER special day logic is applied.
     * Used to re-apply command-specific fields that might have been modified by special day logic.
     * Examples:
     * - TempStopCommands: Re-apply temp stop fields and sync status
     * - ResumeCommands: Re-apply resume-specific fields
     * - EndDayCommand: Re-apply final sync status
     * @param entry The worktime entry to customize
     * @param session The current session
     * @param context The session context
     */
    protected abstract void applyPostSpecialDayCustomizations(WorkTimeTable entry, WorkUsersSessionsStates session, SessionContext context);

    /**
     * Get a description of this command for logging purposes
     * Examples: "start day", "end day", "temp stop", "resume from temp stop", etc.
     * @return A human-readable description of the command
     */
    protected abstract String getCommandDescription();

    // ========================================================================
    // HELPER METHODS for common patterns
    // ========================================================================

    /**
     * Helper method to find existing entry with proper error handling
     */
    protected final WorkTimeTable findExistingEntry(LocalDate workDate, SessionContext context) {
        WorkTimeTable entry = context.findSessionEntry(username, userId, workDate);
        if (entry == null) {
            warn(String.format("No worktime entry found for user %s on %s for %s", username, workDate, getCommandDescription()));
        }
        return entry;
    }

    /**
     * Helper method to find existing entry or create new one
     */
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

    /**
     * Helper method to log command-specific customizations
     */
    protected final void logCustomization(String customization) {
        debug(String.format("Applying %s customization for %s", customization, getCommandDescription()));
    }
}