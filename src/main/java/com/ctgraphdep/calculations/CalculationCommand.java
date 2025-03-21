package com.ctgraphdep.calculations;

/**
 * Base interface for calculation commands that perform operations and return results
 */
public interface CalculationCommand<T> {
    /**
     * Executes the calculation command and returns a result
     */
    T execute(CalculationContext context);
}