package com.ctgraphdep.calculations;

import com.ctgraphdep.session.SessionContext;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * Context for calculation commands and queries, providing access to
 * calculation services and utilities.
 */
@Getter
@Component
public class CalculationContext {
    private final SessionContext sessionContext;
    private final CalculationCommandFactory commandFactory;

    /**
     * Creates a new calculation context
     *
     * @param sessionContext The parent session context
     * @param commandFactory The calculation command factory
     */
    public CalculationContext(SessionContext sessionContext, CalculationCommandFactory commandFactory) {
        this.sessionContext = sessionContext;
        this.commandFactory = commandFactory;
    }

    /**
     * Execute a calculation command
     *
     * @param command The command to execute
     * @return The command result
     */
    public <T> T executeCommand(CalculationCommand<T> command) {
        return command.execute(this);
    }

    /**
     * Execute a calculation query
     *
     * @param query The query to execute
     * @return The query result
     */
    public <T> T executeQuery(CalculationQuery<T> query) {
        return query.execute(this);
    }
}