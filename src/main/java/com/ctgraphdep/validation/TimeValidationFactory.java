package com.ctgraphdep.validation;

import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.validation.commands.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Factory for creating time validation commands.
 * Centralizes all time-related validation logic.
 */
@Component
public class TimeValidationFactory {

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
     * @param currentDate The current date for comparison
     * @return A validation command
     */
    public ValidatePeriodCommand createValidatePeriodCommand(
            int year, int month, int maxMonthsAhead, LocalDate currentDate) {
        return new ValidatePeriodCommand(year, month, maxMonthsAhead, currentDate);
    }

    /**
     * Creates a command to validate a period using the current date
     * @param year The year to validate
     * @param month The month to validate
     * @param maxMonthsAhead Maximum number of months into the future allowed
     * @return A validation command
     */
    public ValidatePeriodCommand createValidatePeriodCommand(int year, int month, int maxMonthsAhead) {
        return new ValidatePeriodCommand(year, month, maxMonthsAhead);
    }

    /**
     * Creates a command to validate a holiday date
     * @param date The date to validate
     * @param currentDate The current date for comparison
     * @return A validation command
     */
    public ValidateHolidayDateCommand createValidateHolidayDateCommand(LocalDate date, LocalDate currentDate) {
        return new ValidateHolidayDateCommand(date, currentDate);
    }

    /**
     * Creates a command to validate a holiday date using the current date
     * @param date The date to validate
     * @return A validation command
     */
    public ValidateHolidayDateCommand createValidateHolidayDateCommand(LocalDate date) {
        return new ValidateHolidayDateCommand(date);
    }

    /**
     * Creates a command to validate a time off request
     * @param startDate The start date of the time off request
     * @param endDate The end date of the time off request
     * @param maxMonthsAhead Maximum number of months into the future allowed
     * @param maxMonthsBehind Maximum number of months into the past allowed
     * @param currentDate The current date for comparison
     * @return A validation command
     */
    public ValidateTimeOffRequestCommand createValidateTimeOffRequestCommand(
            LocalDate startDate,
            LocalDate endDate,
            int maxMonthsAhead,
            int maxMonthsBehind,
            LocalDate currentDate) {
        return new ValidateTimeOffRequestCommand(startDate, endDate, maxMonthsAhead, maxMonthsBehind, currentDate);
    }

    /**
     * Creates a command to validate a time off request using the current date
     * @param startDate The start date of the time off request
     * @param endDate The end date of the time off request
     * @param maxMonthsAhead Maximum number of months into the future allowed
     * @param maxMonthsBehind Maximum number of months into the past allowed
     * @return A validation command
     */
    public ValidateTimeOffRequestCommand createValidateTimeOffRequestCommand(
            LocalDate startDate,
            LocalDate endDate,
            int maxMonthsAhead,
            int maxMonthsBehind) {
        return new ValidateTimeOffRequestCommand(startDate, endDate, maxMonthsAhead, maxMonthsBehind);
    }

    /**
     * Creates a command to check if a session is active
     * @param session The session to check
     * @return A command that returns true if the session is active
     */
    public IsActiveSessionCommand createIsActiveSessionCommand(WorkUsersSessionsStates session) {
        return new IsActiveSessionCommand(session);
    }

    /**
     * Creates a command to get standard date values based on standardized time values
     * @param timeValues The standard time values
     * @return A command that provides standard date values
     */
    public GetStandardDateValuesCommand createGetStandardDateValuesCommand(
            GetStandardTimeValuesCommand.StandardTimeValues timeValues) {
        return new GetStandardDateValuesCommand(timeValues);
    }
    /**
     * Creates a command to check if the current day is a weekday
     * @return A command that returns true if today is a weekday
     */
    public IsWeekdayCommand createIsWeekdayCommand() {
        return new IsWeekdayCommand();
    }

    /**
     * Creates a command to check if the current time is within working hours
     * @return A command that returns true if the current time is within working hours
     */
    public IsWorkingHoursCommand createIsWorkingHoursCommand() {
        return new IsWorkingHoursCommand();
    }

    /**
     * Creates a command to check if a session is active for the current day
     * @param sessionDate The date of the session to check
     * @return A command that returns true if the session is active today
     */
    public HasActiveSessionTodayCommand createHasActiveSessionTodayCommand(LocalDate sessionDate) {
        return new HasActiveSessionTodayCommand(sessionDate);
    }
}