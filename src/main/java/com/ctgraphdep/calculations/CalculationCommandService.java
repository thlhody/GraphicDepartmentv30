package com.ctgraphdep.calculations;

import com.ctgraphdep.utils.CommandExecutorUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for executing calculation commands and queries.
 */
@Getter
@Service
public class CalculationCommandService {

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
        return CommandExecutorUtil.executeCommand(command.getClass().getSimpleName(), this.getClass(), () -> command.execute(context));
    }

    /**
     * Execute a calculation query
     *
     * @param query The query to execute
     * @return The query result
     */
    @Transactional(readOnly = true)
    public <T> T executeQuery(CalculationQuery<T> query) {
        return CommandExecutorUtil.executeCommand(query.getClass().getSimpleName(), this.getClass(), () -> query.execute(context));
    }
}