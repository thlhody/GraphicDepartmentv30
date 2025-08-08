package com.ctgraphdep.session.commands;

import com.ctgraphdep.session.SessionCommand;
import com.ctgraphdep.session.SessionContext;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Enhanced base class for session commands with built-in deduplication support.
public abstract class BaseSessionCommand<T> implements SessionCommand<T> {

    // ========================================================================
    // COMMAND DEDUPLICATION SYSTEM
    // ========================================================================

    // Global command execution tracking (shared across all command types)
    private static final Map<String, Long> activeCommands = new ConcurrentHashMap<>();

    // Default cooldown period (can be overridden by specific commands)
    private static final long DEFAULT_COMMAND_COOLDOWN_MS = 2000; // 2 seconds

    // Execute command with custom cooldown period.
    protected T executeWithDeduplication(SessionContext context, String username, CommandExecution<T> commandLogic, T defaultValue, long cooldownMs) {

        String commandKey = getCommandKey(username);

        // Check if command can be executed (deduplication)
        if (!canExecuteCommand(commandKey, cooldownMs)) {
            warn(String.format("Command %s already in progress for user %s, ignoring duplicate",
                    this.getClass().getSimpleName(), username));
            return defaultValue;
        }

        // Register command execution
        registerCommandExecution(commandKey);

        try {
            // Execute the actual command logic
            return executeWithErrorHandling(context, commandLogic);

        } finally {
            // Always cleanup command registration
            cleanupCommandExecution(commandKey);
        }
    }

    // Standard error handling execution - FIXED to wrap checked exceptions
    protected T executeWithErrorHandling(SessionContext context, CommandExecution<T> commandLogic) {
        try {
            return commandLogic.execute(context);
        } catch (RuntimeException e) {
            // Re-throw runtime exceptions as-is
            LoggerUtil.error(this.getClass(), "Runtime error executing command: " + this.getClass().getSimpleName(), e);
            throw e;
        } catch (Exception e) {
            // Wrap checked exceptions in RuntimeException
            LoggerUtil.error(this.getClass(), "Error executing command: " + this.getClass().getSimpleName(), e);
            throw new RuntimeException("Command execution failed: " + e.getMessage(), e);
        }
    }

    // Error handling with default value (existing method).
    protected T executeWithDefault(SessionContext context, CommandExecution<T> commandLogic, T defaultValue) {
        try {
            return commandLogic.execute(context);
        } catch (RuntimeException e) {
            // Log runtime exceptions but return default value
            LoggerUtil.error(this.getClass(), "Runtime error executing command: " + this.getClass().getSimpleName(), e);
            return defaultValue;
        } catch (Exception e) {
            // Log checked exceptions but return default value
            LoggerUtil.error(this.getClass(), "Error executing command: " + this.getClass().getSimpleName(), e);
            return defaultValue;
        }
    }

    // ========================================================================
    // DEDUPLICATION HELPER METHODS
    // ========================================================================

    // Generate unique command key for deduplication.
    private String getCommandKey(String username) {
        return username + ":" + this.getClass().getSimpleName();
    }

    // Check if command can be executed (not already running).
    private boolean canExecuteCommand(String commandKey, long cooldownMs) {
        Long lastExecution = activeCommands.get(commandKey);
        if (lastExecution == null) {
            return true;
        }

        long timeSinceLastExecution = System.currentTimeMillis() - lastExecution;
        return timeSinceLastExecution >= cooldownMs;
    }

    // Register command execution start.
    private void registerCommandExecution(String commandKey) {
        activeCommands.put(commandKey, System.currentTimeMillis());
    }

    // Cleanup command execution record.
    private void cleanupCommandExecution(String commandKey) {
        activeCommands.remove(commandKey);

        // Periodic cleanup to prevent memory leaks
        if (activeCommands.size() > 100) {
            cleanupOldCommands();
        }
    }

    // Cleanup old command records.
    private static void cleanupOldCommands() {
        long cutoff = System.currentTimeMillis() - (DEFAULT_COMMAND_COOLDOWN_MS * 5); // 10 seconds ago
        activeCommands.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    // ========================================================================
    // EXISTING VALIDATION AND UTILITY METHODS
    // ========================================================================

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

    protected void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            logAndThrow("Username cannot be null or empty");
        }
    }

    protected void validateUserId(Integer userId) {
        if (userId == null) {
            logAndThrow("User ID cannot be null");
        }
    }

    protected void validateCondition(boolean condition, String message) {
        if (!condition) {
            logAndThrow(message);
        }
    }

    protected void logAndThrow(String message) {
        LoggerUtil.logAndThrow(this.getClass(), message, new IllegalArgumentException(message));
    }

    protected void debug(String message) {
        LoggerUtil.debug(this.getClass(), message);
    }

    protected void info(String message) {
        LoggerUtil.info(this.getClass(), message);
    }

    protected void warn(String message) {
        LoggerUtil.warn(this.getClass(), message);
    }

    protected void error(String message, Exception e) {
        LoggerUtil.error(this.getClass(), message, e);
    }

    @FunctionalInterface
    protected interface CommandExecution<R> {
        R execute(SessionContext context) throws Exception;
    }
}