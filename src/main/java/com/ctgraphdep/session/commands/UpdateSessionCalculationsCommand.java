package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Command to update session calculations
 */
public class UpdateSessionCalculationsCommand implements SessionCommand<WorkUsersSessionsStates> {
    private final WorkUsersSessionsStates session;
    private final LocalDateTime explicitEndTime; // Optional parameter

    public UpdateSessionCalculationsCommand(WorkUsersSessionsStates session, LocalDateTime explicitEndTime) {
        this.session = session;
        this.explicitEndTime = explicitEndTime;
    }

    @Override
    public WorkUsersSessionsStates execute(SessionContext context) {
        if (session == null) {
            return null;
        }

        try {
            // Get user's schedule
            int userSchedule = context.getUserService()
                    .getUserById(session.getUserId())
                    .map(User::getSchedule)
                    .orElse(8); // Default to 8 hours if not found

            // Use the explicit end time if provided, otherwise use current time
            LocalDateTime endTime = explicitEndTime != null ? explicitEndTime : LocalDateTime.now();

            // Use centralized calculation service with explicit end time and context
            WorkUsersSessionsStates updatedSession = context.updateSessionCalculations(session, endTime, userSchedule);

            // Save updated session
            context.saveSession(updatedSession);

            return updatedSession;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating session calculations: " + e.getMessage(), e);
            return session;
        }
    }
}