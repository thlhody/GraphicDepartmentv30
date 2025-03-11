package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Command to record a continuation point when a user continues working beyond schedule
 */
public class RecordContinuationPointCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final LocalDateTime continuationTime;
    private final boolean isHourly;

    /**
     * Creates a new command to record a continuation point
     *
     * @param username The username
     * @param userId The user ID
     * @param continuationTime The time of continuation
     * @param isHourly Whether this is from an hourly notification
     */
    public RecordContinuationPointCommand(String username, Integer userId, LocalDateTime continuationTime, boolean isHourly) {
        this.username = username;
        this.userId = userId;
        this.continuationTime = continuationTime;
        this.isHourly = isHourly;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Recording continuation point for user %s (hourly: %b)", username, isHourly));
            // Use the provided continuation time to record exactly when the event occurred
            context.getContinuationTrackingService().recordContinuationPoint(username, userId, continuationTime, isHourly);
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error recording continuation point for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}