package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.utils.LoggerUtil;

import java.time.LocalDate;

/**
 * Command to resolve continuation points for a user
 */
public class ResolveContinuationPointsCommand implements SessionCommand<Boolean> {
    private final String username;
    private final LocalDate sessionDate;
    private final String operatingUsername;
    private final Integer overtimeMinutes;

    public ResolveContinuationPointsCommand(String username, LocalDate sessionDate,
                                            String operatingUsername, Integer overtimeMinutes) {
        this.username = username;
        this.sessionDate = sessionDate;
        this.operatingUsername = operatingUsername;
        this.overtimeMinutes = overtimeMinutes;
    }

    @Override
    public Boolean execute(SessionContext context) {
        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Resolving continuation points for user %s on %s",
                            username, sessionDate));

            context.getContinuationTrackingService().resolveContinuationPoints(
                    username, sessionDate, operatingUsername, overtimeMinutes);

            return true;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error resolving continuation points: %s", e.getMessage()));
            return false;
        }
    }
}