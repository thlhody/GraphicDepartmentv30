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
        try {
            LoggerUtil.debug(this.getClass(), "Executing time validation command: " + command.getClass().getSimpleName());
            return command.execute();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error executing time validation command " + command.getClass().getSimpleName() + ": " + e.getMessage(),
                    e);
            throw e;
        }
    }
}