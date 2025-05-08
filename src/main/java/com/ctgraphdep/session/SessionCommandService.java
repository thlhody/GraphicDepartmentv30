package com.ctgraphdep.session;

import com.ctgraphdep.utils.CommandExecutorUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central service for executing session commands and queries
 */
@Getter
@Service
public class SessionCommandService {
    /**
     * Gets the session context providing access to services
     */
    private final SessionContext context;

    public SessionCommandService(SessionContext context) {
        this.context = context;
    }

    /**
     * Executes a command with transactional support
     */
    @Transactional
    public <T> T executeCommand(SessionCommand<T> command) {
        return CommandExecutorUtil.executeCommand(command.getClass().getSimpleName(), this.getClass(), () -> command.execute(context));
    }

    /**
     * Executes a query (non-transactional read)
     */
    public <T> T executeQuery(SessionQuery<T> query) {
        return CommandExecutorUtil.executeCommand(
                query.getClass().getSimpleName(),
                this.getClass(),
                () -> query.execute(context)
        );
    }
}