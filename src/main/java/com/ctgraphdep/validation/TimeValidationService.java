package com.ctgraphdep.validation;

import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

/**
 * Service for executing time validation commands
 */
@Getter
@Service
public class TimeValidationService {

    private final TimeValidationFactory validationFactory;

    public TimeValidationService(TimeValidationFactory validationFactory) {
        this.validationFactory = validationFactory;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Execute a time validation command
     * @param command The command to execute
     * @return The command result
     */
    public <T> T execute(TimeValidationCommand<T> command) {
        String commandName = command.getClass().getSimpleName();
        try {
            LoggerUtil.debug(this.getClass(), "Executing time validation command: " + commandName);
            T result = command.execute();
            LoggerUtil.debug(this.getClass(), "Command executed successfully: " + commandName);
            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error executing time validation command " + commandName + ": " + e.getMessage(), e);
            throw e;
        }
    }
}