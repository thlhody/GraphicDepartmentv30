package com.ctgraphdep.validation.commands;

import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationCommand;
import com.ctgraphdep.validation.TimeProvider;
import com.ctgraphdep.config.ValidationConfig;

import java.time.LocalDate;

/**
 * Command to check if a session is active for the current day
 */
public class HasActiveSessionTodayCommand implements TimeValidationCommand<Boolean> {
    private final LocalDate sessionDate;
    private final TimeProvider timeProvider;
    private final boolean defaultOnError;

    /**
     * Creates a command to check if a session is active for the current day
     *
     * @param sessionDate The session date to check
     * @param timeProvider Provider for obtaining current date/time
     * @param defaultOnError Value to return if an error occurs (default: false as per security policy)
     */
    public HasActiveSessionTodayCommand(LocalDate sessionDate, TimeProvider timeProvider, boolean defaultOnError) {
        if (timeProvider == null) {
            throw new IllegalArgumentException("TimeProvider cannot be null");
        }

        this.sessionDate = sessionDate;
        this.timeProvider = timeProvider;
        this.defaultOnError = defaultOnError;
    }

    /**
     * Creates a command to check if a session is active for the current day
     * Uses the default error policy value
     *
     * @param sessionDate The session date to check
     * @param timeProvider Provider for obtaining current date/time
     */
    public HasActiveSessionTodayCommand(LocalDate sessionDate, TimeProvider timeProvider) {
        this(sessionDate, timeProvider, ValidationConfig.DEFAULT_ACTIVE_SESSION_ON_ERROR);
    }

    @Override
    public Boolean execute() {
        try {
            if (sessionDate == null) {
                return false;
            }

            LocalDate currentDate = timeProvider.getCurrentDate();
            return sessionDate.equals(currentDate);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking if session is active today: " + e.getMessage());
            return defaultOnError;
        }
    }
}