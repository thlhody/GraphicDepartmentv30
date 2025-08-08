package com.ctgraphdep.session;

// Base interface for session queries that don't change state
public interface SessionQuery<T> {
    // Executes the query and returns a result
    T execute(SessionContext context);

}
