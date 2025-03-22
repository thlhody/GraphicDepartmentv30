package com.ctgraphdep.validation;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Standard implementation of TimeProvider that uses system clock
 */
@Component
public class StandardTimeProvider implements TimeProvider {

    @Override
    public LocalDate getCurrentDate() {
        try {
            return LocalDate.now();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting current date: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public LocalDateTime getCurrentDateTime() {
        try {
            return LocalDateTime.now();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting current date/time: " + e.getMessage());
            throw e;
        }
    }
}