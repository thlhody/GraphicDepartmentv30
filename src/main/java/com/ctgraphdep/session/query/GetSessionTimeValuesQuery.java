package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Query to get standardized time values for session operations
 * Centralizes time-related logic for consistency across commands
 */
public class GetSessionTimeValuesQuery implements SessionQuery<GetSessionTimeValuesQuery.SessionTimeValues> {

    @Override
    public SessionTimeValues execute(SessionContext context) {
        try {
            // Calculate standard time values
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startTime = now.minusMinutes(WorkCode.BUFFER_MINUTES);
            LocalDate today = now.toLocalDate();

            // Add specialized notification time values
            LocalDateTime notificationTimestamp = now; // For recording notification displays
            LocalDateTime continuationTimestamp = now; // For recording continuation points

            // Calculate scheduled notification times based on current time
            LocalDateTime nextHourlyCheck = now.plusMinutes(WorkCode.HOURLY_INTERVAL);

            LoggerUtil.debug(this.getClass(),
                    String.format("Generated session time values - Now: %s, Start time: %s, Today: %s",
                            now, startTime, today));

            return new SessionTimeValues(
                    now,
                    startTime,
                    today,
                    notificationTimestamp,
                    continuationTimestamp,
                    nextHourlyCheck);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error generating session time values: " + e.getMessage());
            // Fallback to basic values if something goes wrong
            LocalDateTime now = LocalDateTime.now();
            return new SessionTimeValues(
                    now,
                    now,
                    now.toLocalDate(),
                    now,
                    now,
                    now.plusMinutes(WorkCode.HOURLY_INTERVAL));
        }
    }

    /**
     * Immutable class to hold standardized time values
     */
    @Getter
    public static class SessionTimeValues {
        private final LocalDateTime currentTime;
        private final LocalDateTime startTime;
        private final LocalDate currentDate;
        private final LocalDateTime notificationTimestamp;
        private final LocalDateTime continuationTimestamp;
        private final LocalDateTime nextHourlyCheck;

        public SessionTimeValues(
                LocalDateTime currentTime,
                LocalDateTime startTime,
                LocalDate currentDate,
                LocalDateTime notificationTimestamp,
                LocalDateTime continuationTimestamp,
                LocalDateTime nextHourlyCheck) {
            this.currentTime = currentTime;
            this.startTime = startTime;
            this.currentDate = currentDate;
            this.notificationTimestamp = notificationTimestamp;
            this.continuationTimestamp = continuationTimestamp;
            this.nextHourlyCheck = nextHourlyCheck;
        }

        /**
         * Gets a timestamp for recording events like continuation points
         */
        public LocalDateTime getEventTimestamp() {
            return continuationTimestamp;
        }

        /**
         * Gets the appropriate time for the next hourly check
         */
        public LocalDateTime getNextHourlyCheckTime() {
            return nextHourlyCheck;
        }
    }
}