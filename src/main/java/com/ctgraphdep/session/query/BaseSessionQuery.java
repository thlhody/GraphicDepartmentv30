package com.ctgraphdep.session.query;

import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.session.SessionQuery;
import com.ctgraphdep.utils.CommandExecutorUtil;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Base class for session queries that provides common validation
 * and execution patterns.
 *
 * @param <T> The query result type
 */
public abstract class BaseSessionQuery<T> implements SessionQuery<T> {

    /**
     * Executes the query with standard error handling and logging.
     *
     * @param context The session context
     * @param queryLogic The query execution logic
     * @return The query result
     */
    protected T executeWithErrorHandling(SessionContext context, QueryExecution<T> queryLogic) {
        return CommandExecutorUtil.executeCommand(
                this.getClass().getSimpleName(),
                this.getClass(),
                () -> queryLogic.execute(context)
        );
    }

    /**
     * Executes the query with standard error handling and logging,
     * returning a default value on error.
     *
     * @param context The session context
     * @param queryLogic The query execution logic
     * @param defaultValue The default value to return on error
     * @return The query result or default value on error
     */
    protected T executeWithDefault(SessionContext context, QueryExecution<T> queryLogic, T defaultValue) {
        return CommandExecutorUtil.executeCommandWithDefault(
                this.getClass().getSimpleName(),
                this.getClass(),
                () -> queryLogic.execute(context),
                defaultValue
        );
    }

    /**
     * Validates that a username is not null or empty.
     *
     * @param username The username to validate
     * @throws RuntimeException if username is null or empty
     */
    protected void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            logAndThrow("Username cannot be null or empty");
        }
    }

    /**
     * Logs an error message and throws a RuntimeException.
     *
     * @param message The error message
     * @throws RuntimeException with the specified message
     */
    protected void logAndThrow(String message) {
        LoggerUtil.logAndThrow(this.getClass(), message, new IllegalArgumentException(message));
    }

    /**
     * Logs a debug message.
     *
     * @param message The message to log
     */
    protected void debug(String message) {
        LoggerUtil.debug(this.getClass(), message);
    }

    /**
     * Logs an info message.
     *
     * @param message The message to log
     */
    protected void info(String message) {
        LoggerUtil.info(this.getClass(), message);
    }

    /**
     * Logs a warning message.
     *
     * @param message The message to log
     */
    protected void warn(String message) {
        LoggerUtil.warn(this.getClass(), message);
    }

    /**
     * Logs an error message.
     *
     * @param message The message to log
     * @param e The exception that caused the error
     */
    protected void error(String message, Exception e) {
        LoggerUtil.error(this.getClass(), message, e);
    }

    protected void error(String message) {
        LoggerUtil.error(this.getClass(), message);
    }


    /**
     * Functional interface for query execution logic.
     *
     * @param <R> The query result type
     */
    @FunctionalInterface
    protected interface QueryExecution<R> {
        R execute(SessionContext context);
    }
}