package com.ctgraphdep.enums;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public enum SessionEndRule {
    // Rule 5: Previous Day Session - Highest Priority
    PREVIOUS_DAY_SESSION(5) {
        @Override
        public boolean applies(WorkUsersSessionsStates session, Integer schedule) {
            return session != null &&
                    session.getDayStartTime() != null &&
                    !session.getDayStartTime().toLocalDate().equals(LocalDateTime.now().toLocalDate());
        }

        @Override
        public boolean requiresNotification() {
            return false;
        }

        @Override
        public String getNotificationMessage() {
            return null;
        }

        @Override
        public int getNotificationTimeout() {
            return 0;
        }

        @Override
        public boolean requiresHourlyMonitoring() {
            return false;
        }
    },

    // Rule 4: Maximum Temporary Stop - Second Priority
    MAX_TEMP_STOP_REACHED(4) {
        @Override
        public boolean applies(WorkUsersSessionsStates session, Integer schedule) {
            if (session == null || !WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                return false;
            }

            int totalTempStopMinutes = session.getTotalTemporaryStopMinutes() != null ?
                    session.getTotalTemporaryStopMinutes() : 0;

            if (session.getLastTemporaryStopTime() != null) {
                totalTempStopMinutes += CalculateWorkHoursUtil.calculateMinutesBetween(
                        session.getLastTemporaryStopTime(),
                        LocalDateTime.now()
                );
            }

            return totalTempStopMinutes >= (WorkCode.MAX_TEMP_STOP_HOURS * WorkCode.HOUR_DURATION);
        }

        @Override
        public boolean requiresNotification() {
            return false;
        }

        @Override
        public String getNotificationMessage() {
            return null;
        }

        @Override
        public int getNotificationTimeout() {
            return 0;
        }

        @Override
        public boolean requiresHourlyMonitoring() {
            return false;
        }
    },

    // Rule 3: Long Temporary Stop - Third Priority
    LONG_TEMP_STOP(3) {
        @Override
        public boolean applies(WorkUsersSessionsStates session, Integer schedule) {
            if (session == null ||
                    !WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) ||
                    session.getLastTemporaryStopTime() == null ||
                    session.getFinalWorkedMinutes() == null ||
                    schedule == null) {
                return false;
            }

            int scheduleMinutes = schedule * WorkCode.HOUR_DURATION;
            boolean isOverSchedule = session.getFinalWorkedMinutes() < scheduleMinutes;

            long tempStopDuration = ChronoUnit.MINUTES.between(
                    session.getLastTemporaryStopTime(),
                    LocalDateTime.now()
            );

            return isOverSchedule && tempStopDuration >= WorkCode.TEMP_STOP_WARNING_INTERVAL;
        }

        @Override
        public boolean requiresNotification() {
            return true;
        }

        @Override
        public String getNotificationMessage() {
            return WorkCode.LONG_TEMP_STOP_WARNING;
        }

        @Override
        public int getNotificationTimeout() {
            return WorkCode.ON_FOR_FIVE_MINUTES;
        }

        @Override
        public boolean requiresHourlyMonitoring() {
            return true;
        }
    },

    // Rule 2: Overtime Schedule - Fourth Priority
    OVERTIME_REACHED(2) {
        @Override
        public boolean applies(WorkUsersSessionsStates session, Integer schedule) {
            if (session == null || session.getFinalWorkedMinutes() == null || schedule == null) {
                return false;
            }

            int fullDayMinutes = WorkCode.calculateFullDayDuration(schedule);
            int overtimeThreshold = fullDayMinutes + WorkCode.HOUR_DURATION; // One hour after full day

            return session.getFinalWorkedMinutes() >= overtimeThreshold;
        }

        @Override
        public boolean requiresNotification() {
            return true;
        }

        @Override
        public String getNotificationMessage() {
            return WorkCode.HOURLY_WARNING_MESSAGE;
        }

        @Override
        public int getNotificationTimeout() {
            return WorkCode.ON_FOR_FIVE_MINUTES;
        }

        @Override
        public boolean requiresHourlyMonitoring() {
            return true;
        }
    },

    // Rule 1: Normal Schedule End - Lowest Priority
    SCHEDULE_END_REACHED(1) {
        @Override
        public boolean applies(WorkUsersSessionsStates session, Integer schedule) {
            if (session == null || session.getFinalWorkedMinutes() == null || schedule == null) {
                return false;
            }

            int fullDayMinutes = WorkCode.calculateFullDayDuration(schedule);
            int currentMinutes = session.getFinalWorkedMinutes();

            // Check if exactly at full day duration (e.g., 8.5 hours for standard schedule)
            return currentMinutes >= fullDayMinutes &&
                    currentMinutes < (fullDayMinutes + WorkCode.HOUR_DURATION); // Less than 1 hour overtime
        }

        @Override
        public boolean requiresNotification() {
            return true;
        }

        @Override
        public String getNotificationMessage() {
            return WorkCode.SESSION_WARNING_MESSAGE;
        }

        @Override
        public int getNotificationTimeout() {
            return WorkCode.ON_FOR_TEN_MINUTES;
        }

        @Override
        public boolean requiresHourlyMonitoring() {
            return true;
        }
    };

    private final int priority;

    SessionEndRule(int priority) {
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public abstract boolean applies(WorkUsersSessionsStates session, Integer schedule);
    public abstract boolean requiresNotification();
    public abstract String getNotificationMessage();
    public abstract int getNotificationTimeout();
    public abstract boolean requiresHourlyMonitoring();

    // Helper method to find the highest priority applicable rule
    public static SessionEndRule findApplicableRule(WorkUsersSessionsStates session, Integer schedule) {
        return java.util.Arrays.stream(SessionEndRule.values())
                .filter(rule -> rule.applies(session, schedule))
                .max(java.util.Comparator.comparingInt(SessionEndRule::getPriority))
                .orElse(null);
    }
}