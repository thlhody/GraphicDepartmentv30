package com.ctgraphdep.session;

import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ctgraphdep.utils.LoggerUtil;

/**
 * Central service for executing session commands and queries
 */
@Getter
@Service
public class SessionCommandService {
    /**
     * -- GETTER --
     *  Gets the session context providing access to services
     *  The session context
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
        try {
            LoggerUtil.info(this.getClass(),
                    "Executing command: " + command.getClass().getSimpleName());

            T result = command.execute(context);

            LoggerUtil.info(this.getClass(),
                    "Command executed successfully: " + command.getClass().getSimpleName());

            return result;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error executing command " + command.getClass().getSimpleName()
                            + ": " + e.getMessage(), e);

            throw e;
        }
    }

    /**
     * Executes a query (non-transactional read)
     */
    public <T> T executeQuery(SessionQuery<T> query) {
        try {
            LoggerUtil.debug(this.getClass(),
                    "Executing query: " + query.getClass().getSimpleName());

            return query.execute(context);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error executing query " + query.getClass().getSimpleName()
                            + ": " + e.getMessage(), e);

            throw e;
        }
    }
}