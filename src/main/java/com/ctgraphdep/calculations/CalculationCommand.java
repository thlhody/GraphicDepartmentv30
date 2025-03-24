package com.ctgraphdep.calculations;

/**
 * Base interface for calculation commands that perform operations and return results
 */
public interface CalculationCommand<T> {
    /**
     * Executes the calculation command and returns a result
     */
    T execute(CalculationContext context);
    /**
     * Validates the command parameters
     * @throws IllegalArgumentException if validation fails
     */
    default void validate() {
        // Default implementation does nothing, subclasses can override
    }
}