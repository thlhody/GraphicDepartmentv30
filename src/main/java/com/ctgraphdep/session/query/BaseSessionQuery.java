package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Base class for session queries that provides common validation and execution patterns.
public abstract class BaseSessionQuery<T> implements SessionQuery<T> {

    // Executes the query with standard error handling and logging.
    protected T executeWithErrorHandling(SessionContext context, QueryExecution<T> queryLogic) {
        try {
            return queryLogic.execute(context);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error executing command: " + this.getClass().getSimpleName(), e);
            throw e;
        }
    }

    //Executes the query with standard error handling and logging, returning a default value on error.
    protected T executeWithDefault(SessionContext context, QueryExecution<T> queryLogic, T defaultValue) {
        try {
            return queryLogic.execute(context);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error executing command: " + this.getClass().getSimpleName(), e);
            return defaultValue;
        }
    }

    // Validates that a username is not null or empty.
    protected void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            logAndThrow("Username cannot be null or empty");
        }
    }

    // Logs an error message and throws a RuntimeException.
    protected void logAndThrow(String message) {
        LoggerUtil.logAndThrow(this.getClass(), message, new IllegalArgumentException(message));
    }

    // Logs a debug message.
    protected void debug(String message) {
        LoggerUtil.debug(this.getClass(), message);
    }

    // Logs an info message.
    protected void info(String message) {
        LoggerUtil.info(this.getClass(), message);
    }

    // Logs a warning message.
    protected void warn(String message) {
        LoggerUtil.warn(this.getClass(), message);
    }

    // Logs an error message.
    protected void error(String message, Exception e) {
        LoggerUtil.error(this.getClass(), message, e);
    }

    protected void error(String message) {
        LoggerUtil.error(this.getClass(), message);
    }

    // Gets standardized time values using the validation service
    protected GetStandardTimeValuesCommand.StandardTimeValues getStandardTimeValues(SessionContext context) {
        GetStandardTimeValuesCommand timeCommand = context.getValidationService()
                .getValidationFactory()
                .createGetStandardTimeValuesCommand();
        return context.getValidationService().execute(timeCommand);
    }

    // Gets the current standardized date
    protected LocalDate getStandardCurrentDate(SessionContext context) {
        return getStandardTimeValues(context).getCurrentDate();
    }

    // Gets the current standardized time
    protected LocalDateTime getStandardCurrentTime(SessionContext context) {
        return getStandardTimeValues(context).getCurrentTime();
    }

    // Functional interface for query execution logic.
    @FunctionalInterface
    protected interface QueryExecution<R> {
        R execute(SessionContext context);
    }
}