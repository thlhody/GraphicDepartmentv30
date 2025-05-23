package com.ctgraphdep.validation;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.validation.commands.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Factory for creating time validation commands.
 * Centralizes all time-related validation logic.
 * Enhanced with additional command creation methods.
 */
@Getter
@Component
public class TimeValidationFactory {
    private final TimeProvider timeProvider;

    /**
     * Creates a time validation factory
     *
     * @param timeProvider Provider for obtaining current date/time
     */
    public TimeValidationFactory(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    /**
     * Creates a command to get standard time values
     * @return A command that provides standard time values
     */
    public GetStandardTimeValuesCommand createGetStandardTimeValuesCommand() {
        return new GetStandardTimeValuesCommand();
    }

    /**
     * Creates a command to validate a period (year/month)
     * @param year The year to validate
     * @param month The month to validate
     * @param maxMonthsAhead Maximum number of months into the future allowed
     * @return A validation command
     */
    public ValidatePeriodCommand createValidatePeriodCommand(int year, int month, int maxMonthsAhead) {
        return new ValidatePeriodCommand(year, month, maxMonthsAhead, timeProvider);
    }

    /**
     * Creates a command to validate a holiday date
     * @param date The date to validate
     * @return A validation command
     */
    public ValidateHolidayDateCommand createValidateHolidayDateCommand(LocalDate date) {
        return new ValidateHolidayDateCommand(date, timeProvider);
    }

    /**
     * Creates a command to validate a session before starting a new one
     * @param session The session to validate
     * @return A command that returns true if session needs reset
     */
    public ValidateSessionForStartCommand createValidateSessionForStartCommand(WorkUsersSessionsStates session) {
        return new ValidateSessionForStartCommand(session, timeProvider);
    }

    /**
     * Creates a command to check if a session is active
     * @param session The session to check
     * @return A command that returns true if the session is active
     */
    public IsActiveSessionCommand createIsActiveSessionCommand(WorkUsersSessionsStates session) {
        return new IsActiveSessionCommand(session,timeProvider);
    }

    /**
     * Creates a command to check if the current day is a weekday
     * @return A command that returns true if today is a weekday
     */
    public IsWeekdayCommand createIsWeekdayCommand() {
        return new IsWeekdayCommand(timeProvider);
    }

    /**
     * Creates a command to check if the current time is within working hours
     * @return A command that returns true if the current time is within working hours
     */
    public IsWorkingHoursCommand createIsWorkingHoursCommand() {
        return new IsWorkingHoursCommand(timeProvider);
    }

    /**
     * Creates a command to check if a date is a national holiday
     * @param date The date to check
     * @param entries to use the data retrieved
     * @return A command that returns true if the date is a national holiday
     */
    public IsNationalHolidayCommand createIsNationalHolidayCommand(LocalDate date, List<WorkTimeTable> entries) {
        return new IsNationalHolidayCommand(date, entries, timeProvider);
    }
}