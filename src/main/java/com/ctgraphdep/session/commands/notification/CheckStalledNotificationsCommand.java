package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

public class CheckStalledNotificationsCommand implements SessionCommand<Void> {

    // Constant for stalled notification threshold (in minutes)
    private static final int STALLED_NOTIFICATION_THRESHOLD = 15;

    @Override
    public Void execute(SessionContext context) {
        // Get standardized time values using the validation system
        GetStandardTimeValuesCommand timeCommand = context.getValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = context.getValidationService().execute(timeCommand);

        // Handle schedule end notifications
        context.getBackupService().getStalledScheduleEndNotifications().forEach((username, time) -> {
            // If notification is stalled (e.g., 15+ minutes old)
            if (isNotificationStalled(context, time, timeValues.getCurrentTime())) {
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

    /**
     * Determines if a notification is stalled based on the time threshold
     * @param context The session context
     * @param notificationTime The notification timestamp
     * @param currentTime The current time
     * @return true if the notification is stalled, false otherwise
     */
    private boolean isNotificationStalled(SessionContext context, LocalDateTime notificationTime, LocalDateTime currentTime) {
        int minutesSince = context.calculateMinutesBetween(notificationTime, currentTime);
        return minutesSince >= STALLED_NOTIFICATION_THRESHOLD;
    }

    /**
     * Checks if a session is in active status
     * @param session The session to check
     * @return true if the session is active, false otherwise
     */
    private boolean isSessionActive(WorkUsersSessionsStates session) {
        return session != null && WorkCode.WORK_ONLINE.equals(session.getSessionStatus());
    }
}