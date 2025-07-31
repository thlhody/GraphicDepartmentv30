package com.ctgraphdep.validation.commands;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.validation.TimeProvider;

import java.time.LocalDate;
import java.util.List;

/**
 * Command to check if a date is a national holiday
 */
public class IsNationalHolidayCommand extends BaseTimeValidationCommand<Boolean> {
    private final LocalDate date;
    private final List<WorkTimeTable> entries;
    private final boolean defaultOnError;

    public IsNationalHolidayCommand(LocalDate date, List<WorkTimeTable> entries, TimeProvider timeProvider, boolean defaultOnError) {
        super(timeProvider);
        if (date == null) {
            warn("Date cannot be null");
        }
        this.date = date;
        this.entries = entries;
        this.defaultOnError = defaultOnError;
    }

    public IsNationalHolidayCommand(LocalDate date, List<WorkTimeTable> entries, TimeProvider timeProvider) {
        this(date, entries, timeProvider, false);
    }

    @Override
    public Boolean execute() {
        return executeValidationWithDefault(() -> {
            if (entries == null || entries.isEmpty()) {
                return false;
            }

//            boolean isHoliday = entries.stream().anyMatch(entry ->
//                    entry.getWorkDate().equals(date) && WorkCode.NATIONAL_HOLIDAY_CODE.equals(entry.getTimeOffType()) &&
//                            WorktimeUniversalStatus.ADMIN_INPUT.equals(entry.getAdminSync()));

            boolean isHoliday = entries.stream().anyMatch(entry ->
                    entry.getWorkDate().equals(date) && WorkCode.NATIONAL_HOLIDAY_CODE.equals(entry.getTimeOffType()));

            debug("Checked national holiday for " + date + ": " + isHoliday);
            return isHoliday;
        }, defaultOnError);
    }
}
