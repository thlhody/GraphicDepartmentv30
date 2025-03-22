package com.ctgraphdep.validation;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Interface for providing standardized date and time values
 * Centralizes time-related logic and facilitates testing by allowing time to be mocked
 */
public interface TimeProvider {

    /**
     * Get the current date
     * @return Current date
     */
    LocalDate getCurrentDate();

    /**
     * Get the current date and time
     * @return Current date and time
     */
    LocalDateTime getCurrentDateTime();
}
