package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.DialogComponents;
import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.session.query.SessionStatusQuery;
import com.ctgraphdep.session.query.WorktimeResolutionQuery;
import com.ctgraphdep.utils.LoggerUtil;

import java.awt.*;

/**
 * Command to show start day reminder
 */
public class ShowStartDayReminderCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;

    /**
     * Creates a new command to show start day reminder
     *
     * @param username The username
     * @param userId The user ID
     */
    public ShowStartDayReminderCommand(String username, Integer userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Attempting to show start day reminder for user %s", username));

            // Check if notification can be shown based on rate limiting
            CanShowNotificationQuery canShowQuery = context.getCommandFactory().createCanShowNotificationQuery(
                    username, WorkCode.START_DAY_TYPE,
                    WorkCode.ONCE_PER_DAY_TIMER, context.getNotificationService().getLastNotificationTimes());

            if (!context.executeQuery(canShowQuery)) {
                LoggerUtil.info(this.getClass(), String.format("Skipping start day reminder for user %s (already shown today)", username));
                return false;
            }

            // Check for unresolved worktime entries FIRST - this has priority
            WorktimeResolutionQuery resolutionQuery = context.getCommandFactory().createWorktimeResolutionQuery(username, userId);
            WorktimeResolutionQuery.ResolutionStatus resolutionStatus = context.executeQuery(resolutionQuery);

            // If there are unresolved entries, show resolution notification regardless of session state
            if (resolutionStatus.isNeedsResolution()) {
                LoggerUtil.info(this.getClass(), String.format("User %s has unresolved worktime entries - showing resolution notification", username));

                // Show resolution notification
                return context.getNotificationService().showResolutionReminder(
                        username, userId,
                        WorkCode.RESOLUTION_TITLE,
                        WorkCode.RESOLUTION_MESSAGE,
                        WorkCode.RESOLUTION_MESSAGE_TRAY,
                        WorkCode.ON_FOR_TWELVE_HOURS);
            }

            // Use the new SessionStatusQuery to get comprehensive session status
            SessionStatusQuery statusQuery = context.getCommandFactory().createSessionStatusQuery(username, userId);
            SessionStatusQuery.SessionStatus status = context.executeQuery(statusQuery);

            // If session is active (online or in temporary stop), don't show start day reminder
            if (status.isOnline() || status.isInTemporaryStop()) {
                LoggerUtil.info(this.getClass(),
                        String.format("User %s has active session - skipping start day reminder", username));
                return false;
            }

            // If we get here, session is offline and there are no unresolved entries
            // Show regular start day notification
            LoggerUtil.info(this.getClass(),
                    String.format("User %s has offline session with no unresolved entries - showing start day reminder", username));

            return context.getNotificationService().showNotificationWithFallback(
                    username, userId,
                    WorkCode.START_DAY_TITLE,
                    WorkCode.START_DAY_MESSAGE,
                    WorkCode.START_DAY_MESSAGE_TRAY,
                    WorkCode.ON_FOR_TWELVE_HOURS,
                    false, false, null,
                    (DialogComponents components, String u, Integer id, Integer minutes) ->
                            context.getNotificationService().addStartDayButtons(components, u, id),
                    TrayIcon.MessageType.INFO
            );
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error showing start day reminder for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}