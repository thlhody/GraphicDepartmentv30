package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.query.CanShowNotificationQuery;
import com.ctgraphdep.session.query.SessionStatusQuery;
import com.ctgraphdep.session.query.WorktimeResolutionQuery;

/**
 * Command to show start day reminder
 */
public class ShowStartDayReminderCommand extends BaseNotificationCommand<Boolean> {

    /**
     * Creates a new command to show start day reminder
     *
     * @param username The username
     * @param userId The user ID
     */
    public ShowStartDayReminderCommand(String username, Integer userId) {
        super(username, userId);
    }

    @Override
    public Boolean execute(SessionContext context) {
        return executeWithErrorHandling(context, ctx -> {
            // Log start of the operation
            info(String.format("Attempting to show start day reminder for user %s", username));

            // Check if notification can be shown (rate limiting)
            CanShowNotificationQuery canShowQuery = ctx.getCommandFactory().createCanShowNotificationQuery(username, WorkCode.START_DAY_TYPE, WorkCode.ONCE_PER_DAY_TIMER);

            if (!ctx.executeQuery(canShowQuery)) {
                info(String.format("Skipping start day reminder for user %s (already shown today)", username));
                return false;
            }

            // Check for unresolved worktime entries FIRST - this has priority
            WorktimeResolutionQuery resolutionQuery = ctx.getCommandFactory().createWorktimeResolutionQuery(username);
            WorktimeResolutionQuery.ResolutionStatus resolutionStatus = ctx.executeQuery(resolutionQuery);

            // If there are unresolved entries, show resolution notification regardless of session state
            if (resolutionStatus.isNeedsResolution()) {
                info(String.format("User %s has unresolved worktime entries - showing resolution notification", username));

                // Show resolution notification
                boolean success = ctx.getNotificationService().showResolutionReminder(username, userId,
                        WorkCode.RESOLUTION_TITLE, WorkCode.RESOLUTION_MESSAGE, WorkCode.RESOLUTION_MESSAGE_TRAY,
                        WorkCode.ON_FOR_TWELVE_HOURS
                );

                if (success) {
                    recordNotificationDisplay(ctx, WorkCode.RESOLUTION_REMINDER_TYPE);
                }

                return success;
            }

            // Check session status
            SessionStatusQuery statusQuery = ctx.getCommandFactory().createSessionStatusQuery(username, userId);
            SessionStatusQuery.SessionStatus status = ctx.executeQuery(statusQuery);

            // If session is active (online or in temporary stop), don't show start day reminder
            if (status.isOnline() || status.isInTemporaryStop()) {
                info(String.format("User %s has active session - skipping start day reminder", username));
                return false;
            }

            // Show regular start day notification
            info(String.format("User %s has offline session with no unresolved entries - showing start day reminder", username));

            boolean success = ctx.getNotificationService().showStartDayReminder(username, userId);

            if (success) {
                recordNotificationDisplay(ctx, WorkCode.START_DAY_TYPE);
            }

            return success;
        });
    }
}