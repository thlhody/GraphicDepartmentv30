package com.ctgraphdep.calculations;

import com.ctgraphdep.utils.LoggerUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for executing calculation commands and queries.
 */
@Service
public class CalculationCommandService {

    @Getter
    private final CalculationContext context;

    /**
     * Create a new calculation command service
     *
     * @param context The calculation context
     */
    public CalculationCommandService(CalculationContext context) {
        this.context = context;
    }

    /**
     * Execute a calculation command
     *
     * @param command The command to execute
     * @return The command result
     */
    @Transactional(readOnly = true) // Most calculations are read-only
    public <T> T executeCommand(CalculationCommand<T> command) {
        try {
            LoggerUtil.debug(this.getClass(), "Executing calculation command: " + command.getClass().getSimpleName());

            T result = command.execute(context);

            LoggerUtil.debug(this.getClass(), "Calculation command executed successfully: " + command.getClass().getSimpleName());

            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error executing calculation command " + command.getClass().getSimpleName() + ": " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Execute a calculation query
     *
     * @param query The query to execute
     * @return The query result
     */
    @Transactional(readOnly = true)
    public <T> T executeQuery(CalculationQuery<T> query) {
        try {
            LoggerUtil.debug(this.getClass(), "Executing calculation query: " + query.getClass().getSimpleName());

            return query.execute(context);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error executing calculation query " + query.getClass().getSimpleName() + ": " + e.getMessage(), e);
            throw e;
        }
    }
}