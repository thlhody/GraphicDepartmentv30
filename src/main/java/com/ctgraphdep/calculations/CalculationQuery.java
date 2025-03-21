package com.ctgraphdep.calculations;

/**
 * Base interface for calculation queries that retrieve calculation results
 */
public interface CalculationQuery<T> {
    /**
     * Executes the calculation query and returns a result
     */
    T execute(CalculationContext context);
}