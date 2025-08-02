package com.ctgraphdep.worktime.commands;

import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.utils.LoggerUtil;

// Base class for worktime operation commands following the Command pattern.
// Provides consistent structure and error handling for all worktime operations.
public abstract class WorktimeOperationCommand<T> {

    protected final WorktimeOperationContext context;

    protected WorktimeOperationCommand(WorktimeOperationContext context) {
        this.context = context;
        LoggerUtil.initialize(this.getClass(), null);
    }

    // Execute the command with error handling and logging
    public final OperationResult execute() {
        try {
            LoggerUtil.info(this.getClass(), String.format("Executing command: %s", getCommandName()));

            // Pre-execution validation
            validate();

            // Execute the specific command logic
            OperationResult result = executeCommand();

            if (result.isSuccess()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Command %s executed successfully: %s", getCommandName(), result.getMessage()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Command %s failed: %s", getCommandName(), result.getMessage()));
            }

            return result;

        } catch (IllegalArgumentException e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Validation failed for command %s: %s", getCommandName(), e.getMessage()));
            return OperationResult.validationFailure(e.getMessage(), getOperationType());

        } catch (SecurityException e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Permission denied for command %s: %s", getCommandName(), e.getMessage()));
            return OperationResult.permissionFailure(getOperationType(), e.getMessage());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error executing command %s: %s", getCommandName(), e.getMessage()), e);
            return OperationResult.failure("Internal error: " + e.getMessage(), getOperationType());
        }
    }

    // Validate command parameters and business rules
    protected abstract void validate();

    // Execute the specific command logic
    protected abstract OperationResult executeCommand();

    // Get command name for logging
    protected abstract String getCommandName();

    // Get operation type for result
    protected abstract String getOperationType();
}


