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
            LoggerUtil.debug(loggerClass, "Command executed successfully A: " + commandName);
            return result;
        } catch (Exception e) {
            LoggerUtil.error(loggerClass, "Error executing command " + commandName + ": " + e.getMessage(), e);
            throw e;
        }
    }
}