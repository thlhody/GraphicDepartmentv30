package com.ctgraphdep.calculations.commands;

import com.ctgraphdep.calculations.CalculationCommand;
import com.ctgraphdep.calculations.CalculationContext;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.ValidationUtil;

import java.time.LocalDateTime;

/**
 * Base class for calculation commands with standard validation and error handling
 *
 * @param <T> Return type of the command
 */
public abstract class BaseCalculationCommand<T> implements CalculationCommand<T> {

    @Override
    public T execute(CalculationContext context) {
        try {
            // Validate before execution
            validate();
            // Execute command logic
            return executeCommand(context);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error executing " + this.getClass().getSimpleName() + ": " + e.getMessage(), e);
            // Use standardized error handling
            return handleError(e);
        }
    }

    /**
     * Executes the command logic
     * @param context The calculation context
     * @return The command result
     */
    protected abstract T executeCommand(CalculationContext context);

    /**
     * Handles errors that occur during command execution
     * @param e The exception that was thrown
     * @return A fallback return value
     */
    protected abstract T handleError(Exception e);

    /**
     * Validates that a session is not null
     * @param session The session to validate
     * @throws IllegalArgumentException if session is null
     */
    protected void validateSession(WorkUsersSessionsStates session) {
        ValidationUtil.validateSessionNotNull(session, "Session");
    }

    /**
     * Validates that a date/time is not null
     * @param dateTime The date/time to validate
     * @param paramName The parameter name for error messages
     * @throws IllegalArgumentException if date/time is null
     */
    protected void validateDateTime(LocalDateTime dateTime, String paramName) {
        ValidationUtil.validateDateTimeNotNull(dateTime, paramName);
    }
}