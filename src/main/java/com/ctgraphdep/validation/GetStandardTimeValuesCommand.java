package com.ctgraphdep.validation;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Command to get standardized time values
 * Centralizes time-related logic for consistency across the application
 */
public class GetStandardTimeValuesCommand implements TimeValidationCommand<GetStandardTimeValuesCommand.StandardTimeValues> {

    @Override
    public StandardTimeValues execute() {
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

            //LoggerUtil.debug(this.getClass(), String.format("Generated standard time values - Now: %s, Start time: %s, Today: %s", now, startTime, today));

            return new StandardTimeValues(now, startTime, today, notificationTimestamp, continuationTimestamp, nextHourlyCheck);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error generating standard time values: " + e.getMessage());
            // Fallback to basic values if something goes wrong
            LocalDateTime now = LocalDateTime.now();
            return new StandardTimeValues(now, now, now.toLocalDate(), now, now, now.plusMinutes(WorkCode.HOURLY_INTERVAL));
        }
    }

    /**
     * Immutable class to hold standardized time values
     */
    @Getter
    public static class StandardTimeValues {
        private final LocalDateTime currentTime;
        private final LocalDateTime startTime;
        private final LocalDate currentDate;
        private final LocalDateTime notificationTimestamp;
        private final LocalDateTime continuationTimestamp;
        private final LocalDateTime nextHourlyCheck;

        public StandardTimeValues(
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