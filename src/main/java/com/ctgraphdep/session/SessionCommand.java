package com.ctgraphdep.session;

/**
 * Base interface for session commands that change state
 */
public interface SessionCommand<T> {
    /**
     * Executes the command and returns a result
     */
    T execute(SessionContext context);
}
