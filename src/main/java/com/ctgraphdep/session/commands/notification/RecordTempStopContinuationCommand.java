package com.ctgraphdep.session.commands.notification;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDateTime;

/**
 * Command to record a temporary stop continuation
 */
public class RecordTempStopContinuationCommand implements SessionCommand<Boolean> {
    private final String username;
    private final Integer userId;
    private final LocalDateTime continuationTime;

    /**
     * Creates a new command to record a temporary stop continuation
     *
     * @param username The username
     * @param userId The user ID
     * @param continuationTime The time of continuation
     */
    public RecordTempStopContinuationCommand(String username, Integer userId,
                                             LocalDateTime continuationTime) {
        this.username = username;
        this.userId = userId;
        this.continuationTime = continuationTime;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(), String.format("Recording temporary stop continuation for user %s", username));
            context.getContinuationTrackingService().recordTempStopContinuation(username, userId, continuationTime);
            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error recording temporary stop continuation for user %s: %s", username, e.getMessage()));
            return false;
        }
    }
}