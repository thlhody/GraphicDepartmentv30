package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.utils.LoggerUtil;

/**
 * Base class for session commands that provides common validation
 * and execution patterns.
 *
 * @param <T> The command result type
 */
public abstract class BaseSessionCommand<T> implements SessionCommand<T> {

    /**
     * Executes the command with standard error handling and logging.
     *
     * @param context The session context
     * @param commandLogic The command execution logic
     * @return The command result
     */
    protected T executeWithErrorHandling(SessionContext context, CommandExecution<T> commandLogic) {
        try {
            return commandLogic.execute(context);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error executing command: " + this.getClass().getSimpleName(), e);
            throw e;
        }
    }

    /**
     * Executes the command with standard error handling and logging,
     * returning a default value on error.
     * @param context The session context
     * @param commandLogic The command execution logic
     * @param defaultValue The default value to return on error
     * @return The command result or default value on error
     */
    protected T executeWithDefault(SessionContext context, CommandExecution<T> commandLogic, T defaultValue) {
        try {
            return commandLogic.execute(context);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error executing command: " + this.getClass().getSimpleName(), e);
            return defaultValue;
        }
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
     * Validates that a user ID is not null.
     *
     * @param userId The user ID to validate
     * @throws RuntimeException if user ID is null
     */
    protected void validateUserId(Integer userId) {
        if (userId == null) {
            logAndThrow("User ID cannot be null");
        }
    }

    /**
     * Validates a condition and throws an exception with the specified message if false.
     *
     * @param condition The condition to validate
     * @param message The error message if condition is false
     * @throws RuntimeException if condition is false
     */
    protected void validateCondition(boolean condition, String message) {
        if (!condition) {
            logAndThrow(message);
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

    /**
     * Functional interface for command execution logic.
     *
     * @param <R> The command result type
     */
    @FunctionalInterface
    protected interface CommandExecution<R> {
        R execute(SessionContext context);
    }
}