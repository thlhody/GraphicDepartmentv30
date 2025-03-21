package com.ctgraphdep.calculations;

import com.ctgraphdep.calculations.commands.*;
import com.ctgraphdep.calculations.queries.*;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Factory for creating calculation commands and queries.
 */
@Component
public class CalculationCommandFactory {

    //========
    // Core Calculation Commands
    //========

    /**
     * Creates a command to update session calculations
     */
    public UpdateSessionCalculationsCommand createUpdateSessionCalculationsCommand(
            WorkUsersSessionsStates session,
            LocalDateTime currentTime,
            int userSchedule) {
        return new UpdateSessionCalculationsCommand(session, currentTime, userSchedule);
    }

    /**
     * Creates a command to update temporary stop calculations
     */
    public UpdateTempStopCalculationsCommand createUpdateTempStopCalculationsCommand(
            WorkUsersSessionsStates session,
            LocalDateTime currentTime) {
        return new UpdateTempStopCalculationsCommand(session, currentTime);
    }

    /**
     * Creates a command to calculate end day values
     */
    public CalculateEndDayValuesCommand createCalculateEndDayValuesCommand(
            WorkUsersSessionsStates session,
            LocalDateTime endTime,
            Integer finalMinutes) {
        return new CalculateEndDayValuesCommand(session, endTime, finalMinutes);
    }

    //========
    // Temporary Stop Commands
    //========

    /**
     * Creates a command to update the last temporary stop
     */
    public UpdateLastTemporaryStopCommand createUpdateLastTemporaryStopCommand(
            WorkUsersSessionsStates session,
            LocalDateTime endTime) {
        return new UpdateLastTemporaryStopCommand(session, endTime);
    }

    /**
     * Creates a command to add a break as temporary stop
     */
    public AddBreakAsTempStopCommand createAddBreakAsTempStopCommand(
            WorkUsersSessionsStates session,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return new AddBreakAsTempStopCommand(session, startTime, endTime);
    }

    /**
     * Creates a command to process resuming from temporary stop
     */
    public ProcessResumeFromTempStopCommand createProcessResumeFromTempStopCommand(
            WorkUsersSessionsStates session,
            LocalDateTime resumeTime) {
        return new ProcessResumeFromTempStopCommand(session, resumeTime);
    }

    //========
    // Calculation Queries
    //========

    /**
     * Creates a query to calculate raw work minutes for a session
     */
    public CalculateRawWorkMinutesQuery createCalculateRawWorkMinutesQuery(
            WorkUsersSessionsStates session,
            LocalDateTime endTime) {
        return new CalculateRawWorkMinutesQuery(session, endTime);
    }

    /**
     * Creates a query to calculate raw work minutes for a work time entry
     */
    public CalculateRawWorkMinutesForEntryQuery createCalculateRawWorkMinutesForEntryQuery(
            WorkTimeTable entry,
            LocalDateTime endTime) {
        return new CalculateRawWorkMinutesForEntryQuery(entry, endTime);
    }

    /**
     * Creates a query to calculate raw work minutes between two times
     */
    public CalculateRawWorkMinutesBetweenQuery createCalculateRawWorkMinutesBetweenQuery(
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<TemporaryStop> stops) {
        return new CalculateRawWorkMinutesBetweenQuery(startTime, endTime, stops);
    }

    /**
     * Creates a query to calculate minutes between two times
     */
    public CalculateMinutesBetweenQuery createCalculateMinutesBetweenQuery(
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return new CalculateMinutesBetweenQuery(startTime, endTime);
    }

    /**
     * Creates a query to calculate the recommended end time
     */
    public CalculateRecommendedEndTimeQuery createCalculateRecommendedEndTimeQuery(
            WorkTimeTable entry,
            int userSchedule) {
        return new CalculateRecommendedEndTimeQuery(entry, userSchedule);
    }

    /**
     * Creates a query to calculate total temporary stop minutes
     */
    public CalculateTotalTempStopMinutesQuery createCalculateTotalTempStopMinutesQuery(
            WorkUsersSessionsStates session,
            LocalDateTime currentTime) {
        return new CalculateTotalTempStopMinutesQuery(session, currentTime);
    }

    /**
     * Creates a query to calculate work time
     */
    public CalculateWorkTimeQuery createCalculateWorkTimeQuery(
            int minutes,
            int schedule) {
        return new CalculateWorkTimeQuery(minutes, schedule);
    }

    /**
     * Creates a command to process temporary stop
     */
    public ProcessTemporaryStopCommand createProcessTemporaryStopCommand(
            WorkUsersSessionsStates session, LocalDateTime stopTime) {
        return new ProcessTemporaryStopCommand(session, stopTime);
    }
}