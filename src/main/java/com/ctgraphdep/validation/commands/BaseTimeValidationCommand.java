package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.CommandExecutorUtil;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeProvider;
import com.ctgraphdep.validation.TimeValidationCommand;

/**
 * Base class for time validation commands to reduce redundant code.
 *
 * @param <T> Return type of the command
 */
public abstract class BaseTimeValidationCommand<T> implements TimeValidationCommand<T> {

    protected final TimeProvider timeProvider;

    /**
     * Creates a base validation command with time provider
     *
     * @param timeProvider Provider for obtaining current date/time
     */
    protected BaseTimeValidationCommand(TimeProvider timeProvider) {
        if (timeProvider == null) {
            LoggerUtil.logAndThrow(this.getClass(), "TimeProvider cannot be null",
                    new IllegalArgumentException("TimeProvider cannot be null"));
        }
        this.timeProvider = timeProvider;
    }

    /**
     * Template method for executing validation commands with standardized logging and exception handling.
     *
     * @param execution The execution logic
     * @return The execution result
     */
    protected T executeValidation(Class<?> loggerClass, String commandName, CommandExecution<T> execution) {
        return CommandExecutorUtil.executeCommand(commandName, loggerClass, execution::execute);
    }

    /**
     * Template method for executing validation commands with default value on error.
     *
     * @param loggerClass Class to use for logging
     * @param commandName Name of the command for logging
     * @param execution The execution logic
     * @param defaultValue Default value to return on error
     * @return The execution result or default value on error
     */
    protected T executeValidationWithDefault(Class<?> loggerClass, String commandName,
                                             CommandExecution<T> execution, T defaultValue) {
        return CommandExecutorUtil.executeCommandWithDefault(commandName, loggerClass, execution::execute, defaultValue);
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
     * Functional interface for command execution.
     *
     * @param <R> Return type
     */
    @FunctionalInterface
    protected interface CommandExecution<R> {
        R execute();
    }
}