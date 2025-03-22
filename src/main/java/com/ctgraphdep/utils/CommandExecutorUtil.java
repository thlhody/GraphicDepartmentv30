package com.ctgraphdep.utils;

import java.util.function.Supplier;

/**
 * Utility class that provides template methods for executing commands with standardized
 * error handling and logging.
 */
public final class CommandExecutorUtil {

    private CommandExecutorUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Executes a command with standardized error handling and logging.
     *
     * @param commandName Name of the command being executed (for logging)
     * @param loggerClass Class to use for logging
     * @param execution Command execution logic
     * @param <T> Type of result
     * @return Command execution result
     */
    public static <T> T executeCommand(String commandName, Class<?> loggerClass, Supplier<T> execution) {
        try {
            LoggerUtil.debug(loggerClass, "Executing command: " + commandName);
            T result = execution.get();
            LoggerUtil.debug(loggerClass, "Command executed successfully: " + commandName);
            return result;
        } catch (Exception e) {
            LoggerUtil.error(loggerClass, "Error executing command " + commandName + ": " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Executes a command with standardized error handling and logging, returning a default value on error.
     *
     * @param commandName Name of the command being executed (for logging)
     * @param loggerClass Class to use for logging
     * @param execution Command execution logic
     * @param defaultValue Default value to return on error
     * @param <T> Type of result
     * @return Command execution result or default value on error
     */
    public static <T> T executeCommandWithDefault(String commandName, Class<?> loggerClass,
                                                  Supplier<T> execution, T defaultValue) {
        try {
            LoggerUtil.debug(loggerClass, "Executing command: " + commandName);
            T result = execution.get();
            LoggerUtil.debug(loggerClass, "Command executed successfully: " + commandName);
            return result;
        } catch (Exception e) {
            LoggerUtil.error(loggerClass, "Error executing command " + commandName + ": " + e.getMessage(), e);
            return defaultValue;
        }
    }
}