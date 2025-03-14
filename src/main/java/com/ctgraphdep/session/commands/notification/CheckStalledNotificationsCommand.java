package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.GetSessionTimeValuesQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class CheckStalledNotificationsCommand implements SessionCommand<Void> {
    @Override
    public Void execute(SessionContext context) {
        // Get standardized time values
        GetSessionTimeValuesQuery timeQuery = context.getCommandFactory().getSessionTimeValuesQuery();
        GetSessionTimeValuesQuery.SessionTimeValues timeValues = context.executeQuery(timeQuery);

        // Handle schedule end notifications
        context.getBackupService().getStalledScheduleEndNotifications().forEach((username, time) -> {
            // If notification is stalled (e.g., 15+ minutes old)
            if (isNotificationStalled(time, timeValues.getCurrentTime())) {
                try {
                    // Get user
                    User user = context.getUserService().getUserByUsername(username).orElse(null);
                    if (user != null) {
                        // Get session
                        WorkUsersSessionsStates session = context.getCurrentSession(username, user.getUserId());

                        // If session is active, handle it
                        if (isSessionActive(session)) {
                            LoggerUtil.warn(this.getClass(), "Detected stalled notification for user " + username);

                            // Remove stalled notification
                            context.getBackupService().removeScheduleEndNotification(username);
                        }
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error handling stalled notification: " + e.getMessage());
                }
            }
        });
        return null;
    }

    private boolean isNotificationStalled(LocalDateTime time, LocalDateTime currentTime) {
        return ChronoUnit.MINUTES.between(time, currentTime) >= 15;
    }

    private boolean isSessionActive(WorkUsersSessionsStates session) {
        return session != null && WorkCode.WORK_ONLINE.equals(session.getSessionStatus());
    }
}