package com.ctgraphdep.session.util;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Builder class for creating and updating session and worktime entities
 * with sensible defaults.
 */
public class SessionEntityBuilder {

    // Create a new session with default values
    public static WorkUsersSessionsStates createSession(String username, Integer userId) {
        return createSession(username, userId, LocalDateTime.now().minusMinutes(WorkCode.BUFFER_MINUTES));
    }

    // Create a new session with specified start time
    public static WorkUsersSessionsStates createSession(String username, Integer userId, LocalDateTime startTime) {
        WorkUsersSessionsStates session = new WorkUsersSessionsStates();
        session.setUserId(userId);
        session.setUsername(username);
        session.setSessionStatus(WorkCode.WORK_ONLINE);
        session.setDayStartTime(startTime);
        session.setCurrentStartTime(startTime);
        session.setTotalWorkedMinutes(0);
        session.setFinalWorkedMinutes(0);
        session.setTotalOvertimeMinutes(0);
        session.setLunchBreakDeducted(false);
        session.setWorkdayCompleted(false);
        session.setTemporaryStopCount(0);
        session.setTotalTemporaryStopMinutes(0);
        session.setTemporaryStops(new ArrayList<>());
        session.setLastTemporaryStopTime(null);
        session.setLastActivity(LocalDateTime.now());
        return session;
    }

    // Create a worktime entry from a session
    public static WorkTimeTable createWorktimeEntryFromSession(WorkUsersSessionsStates session) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(session.getUserId());
        entry.setWorkDate(session.getDayStartTime().toLocalDate());
        entry.setDayStartTime(session.getDayStartTime());
        entry.setDayEndTime(session.getDayEndTime());
        entry.setTotalWorkedMinutes(session.getTotalWorkedMinutes());
        entry.setTotalOvertimeMinutes(session.getTotalOvertimeMinutes() != null ? session.getTotalOvertimeMinutes() : 0);
        entry.setTemporaryStopCount(session.getTemporaryStopCount());
        entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes());
        entry.setLunchBreakDeducted(session.getLunchBreakDeducted() != null ? session.getLunchBreakDeducted() : false);
        entry.setAdminSync(SyncStatusWorktime.USER_IN_PROCESS);
        entry.setTimeOffType(null);
        return entry;
    }

    // Update an existing session using a builder pattern
    public static WorkUsersSessionsStates updateSession(WorkUsersSessionsStates session, Consumer<SessionUpdateBuilder> updates) {
        SessionUpdateBuilder builder = new SessionUpdateBuilder(session);
        updates.accept(builder);
        return builder.build();
    }


    // Create a new worktime entry with default values
    @Deprecated
    public static WorkTimeTable createWorktimeEntry(Integer userId, LocalDateTime startTime) {
        return createWorktimeEntry(userId, startTime, LocalDate.now());
    }

    // Create a new worktime entry with specified date
    @Deprecated
    public static WorkTimeTable createWorktimeEntry(Integer userId, LocalDateTime startTime, LocalDate workDate) {
        WorkTimeTable entry = new WorkTimeTable();
        entry.setUserId(userId);
        entry.setWorkDate(workDate);
        entry.setDayStartTime(startTime);
        entry.setTotalWorkedMinutes(0);
        entry.setTotalOvertimeMinutes(0);
        entry.setTemporaryStopCount(0);
        entry.setTotalTemporaryStopMinutes(0);
        entry.setLunchBreakDeducted(false);
        entry.setAdminSync(SyncStatusWorktime.USER_IN_PROCESS);
        entry.setTimeOffType(null);
        return entry;
    }

    // Inner builder class for flexible session updates
    @Deprecated
    public static class SessionUpdateBuilder {
        private final WorkUsersSessionsStates session;

        private SessionUpdateBuilder(WorkUsersSessionsStates session) {
            this.session = session;
        }

        public SessionUpdateBuilder status(String status) {
            session.setSessionStatus(status);
            return this;
        }

        public SessionUpdateBuilder dayEndTime(LocalDateTime time) {
            session.setDayEndTime(time);
            return this;
        }

        public SessionUpdateBuilder currentStartTime(LocalDateTime time) {
            session.setCurrentStartTime(time);
            return this;
        }

        public SessionUpdateBuilder totalWorkedMinutes(int minutes) {
            session.setTotalWorkedMinutes(minutes);
            return this;
        }

        public SessionUpdateBuilder finalWorkedMinutes(int minutes) {
            session.setFinalWorkedMinutes(minutes);
            return this;
        }

        public SessionUpdateBuilder totalOvertimeMinutes(int minutes) {
            session.setTotalOvertimeMinutes(minutes);
            return this;
        }

        public SessionUpdateBuilder lunchBreakDeducted(boolean deducted) {
            session.setLunchBreakDeducted(deducted);
            return this;
        }

        public SessionUpdateBuilder workdayCompleted(boolean completed) {
            session.setWorkdayCompleted(completed);
            return this;
        }

        public SessionUpdateBuilder temporaryStopCount(int count) {
            session.setTemporaryStopCount(count);
            return this;
        }

        public SessionUpdateBuilder totalTemporaryStopMinutes(int minutes) {
            session.setTotalTemporaryStopMinutes(minutes);
            return this;
        }

        public SessionUpdateBuilder addTemporaryStop(TemporaryStop stop) {
            if (session.getTemporaryStops() == null) {
                session.setTemporaryStops(new ArrayList<>());
            }
            session.getTemporaryStops().add(stop);
            return this;
        }

        public SessionUpdateBuilder lastTemporaryStopTime(LocalDateTime time) {
            session.setLastTemporaryStopTime(time);
            return this;
        }

        public SessionUpdateBuilder updateLastActivity() {
            session.setLastActivity(LocalDateTime.now());
            return this;
        }

        public WorkUsersSessionsStates build() {
            // Always update last activity on build
            session.setLastActivity(LocalDateTime.now());
            return session;
        }
    }

    // Update an existing worktime entry
    @Deprecated
    public static WorkTimeTable updateWorktimeEntry(WorkTimeTable entry, Consumer<WorktimeUpdateBuilder> updates) {

        WorktimeUpdateBuilder builder = new WorktimeUpdateBuilder(entry);
        updates.accept(builder);
        return builder.build();
    }

    // Inner builder class for worktime entry updates
    @Deprecated
    public static class WorktimeUpdateBuilder {
        private final WorkTimeTable entry;

        private WorktimeUpdateBuilder(WorkTimeTable entry) {
            this.entry = entry;
        }

        public WorktimeUpdateBuilder dayEndTime(LocalDateTime time) {
            entry.setDayEndTime(time);
            return this;
        }

        public WorktimeUpdateBuilder totalWorkedMinutes(int minutes) {
            entry.setTotalWorkedMinutes(minutes);
            return this;
        }

        public WorktimeUpdateBuilder totalOvertimeMinutes(int minutes) {
            entry.setTotalOvertimeMinutes(minutes);
            return this;
        }

        public WorktimeUpdateBuilder temporaryStopCount(int count) {
            entry.setTemporaryStopCount(count);
            return this;
        }

        public WorktimeUpdateBuilder totalTemporaryStopMinutes(int minutes) {
            entry.setTotalTemporaryStopMinutes(minutes);
            return this;
        }

        public WorktimeUpdateBuilder lunchBreakDeducted(boolean deducted) {
            entry.setLunchBreakDeducted(deducted);
            return this;
        }

        public WorktimeUpdateBuilder adminSync(SyncStatusWorktime status) {
            entry.setAdminSync(status);
            return this;
        }

        public WorktimeUpdateBuilder timeOffType(String type) {
            entry.setTimeOffType(type);
            return this;
        }

        public WorkTimeTable build() {
            return entry;
        }
    }
}