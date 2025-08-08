package com.ctgraphdep.session.util;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.function.Consumer;

// Builder class for creating and updating session and worktime entities
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
        session.setLastActivity(session.getLastActivity());
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
        entry.setAdminSync(MergingStatusConstants.USER_IN_PROCESS);
        // CRITICAL FIX: DO NOT automatically set timeOffType to null
        // Let the session commands handle timeOffType based on day type detection

        return entry;
    }

    // Update an existing session using a builder pattern
    public static WorkUsersSessionsStates updateSession(WorkUsersSessionsStates session, Consumer<SessionUpdateBuilder> updates) {
        SessionUpdateBuilder builder = new SessionUpdateBuilder(session);
        updates.accept(builder);
        return builder.build();
    }

    public record SessionUpdateBuilder(WorkUsersSessionsStates session) {

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

            public SessionUpdateBuilder lastTemporaryStopTime(LocalDateTime time) {
                session.setLastTemporaryStopTime(time);
                return this;
            }

            public SessionUpdateBuilder addTemporaryStop(TemporaryStop tempStop) {
                if (session.getTemporaryStops() == null) {
                    session.setTemporaryStops(new ArrayList<>());
                }
                session.getTemporaryStops().add(tempStop);
                return this;
            }

            public WorkUsersSessionsStates build() {
                return session;
            }
        }
}