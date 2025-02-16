package com.ctgraphdep.config;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class WorkCode {

    //file format
    public static final String BACKUP_EXTENSION = ".bak";
    public static final String HOURLY = "_hourly";

    //time off codes
    public static final String NATIONAL_HOLIDAY_CODE = "SN";
    public static final String TIME_OFF_CODE = "CO";
    public static final String MEDICAL_LEAVE_CODE = "CM";
    public static final String NATIONAL_HOLIDAY_CODE_LONG = "National Holiday(SN)";
    public static final String TIME_OFF_CODE_LONG = "Holiday(CO)";
    public static final String MEDICAL_LEAVE_CODE_LONG = "Medical Leave(CM)";
    public static final String AVAILABLE_PAID_DAYS = "Available Paid Days";

    public static final String WORK_DAYS = "Work Days";
    public static final String DAYS_WORKED = "Days Worked";
    public static final String DAYS_REMAINING = "Remaining";

    public static final String REGULAR_HOURS = "Regular Hours";
    public static final String OVERTIME = "Overtime";
    public static final String TOTAL_HOURS = "Total Hours";

    //work hours codes
    public static final Integer HOUR_DURATION = 60;           // Minutes in an hour
    public static final Integer BUFFER_MINUTES = 10;          // Buffer time in minutes
    public static final Integer START_HOUR = 7;               // Start hour
    public static final Integer HALF_HOUR_DURATION = 30;      // Half hour duration
    public static final Integer INTERVAL_HOURS_A = 4;         // Interval working hours 4 hours
    public static final Integer INTERVAL_HOURS_B = 11;        // Interval working hours 11 hours
    public static final Integer INTERVAL_HOURS_C = 8;         // Interval working hours 8 hours


    //Notification Timer timers and
    public static int calculateFullDayDuration(Integer schedule) {
        // If schedule is null or invalid, default to 8 hours
        if (schedule == null || schedule <= 0) {
            schedule = INTERVAL_HOURS_C;
        }
        // For 8-hour schedule: 8.5 hours (510 minutes)
        // For others: schedule hours + lunch break if applicable
        if (schedule.equals(INTERVAL_HOURS_C)) {
            return (schedule * HOUR_DURATION) + HALF_HOUR_DURATION;
        } else {
            return schedule * HOUR_DURATION;
        }
    }

    public static final Integer ONE_MINUTE_DELAY = 1;     // 8.5 hours in minutes (8 * 60 + 30)510
    public static final Integer CHECK_INTERVAL = 30; // checks every 30 minutes in order to see if the end time is reached
    public static final Integer HOURLY_INTERVAL = 60; //for hourly checks
    private static final Integer five_minutes = 5 * 60 * 1000;
    private static final Integer ten_minutes = 10 * 60 * 1000;
    public static final Integer ON_FOR_FIVE_MINUTES = five_minutes; // Auto-timer set for 5 minutes (WorkCode.CHECK_EVERY_FIVE_MINUTES)
    public static final Integer ON_FOR_TEN_MINUTES = ten_minutes;   // Auto-timer set for 10 minutes (WorkCode.CHECK_EVERY_TEN_MINUTES)

    public static Integer MAX_TEMP_STOP_HOURS = 15;                 // 24 - MAX_WORK_HOURS

    //date format
    public static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    //days format
    public static final Map<DayOfWeek, String> ROMANIAN_DAY_INITIALS = Map.of(
            DayOfWeek.MONDAY, "L", DayOfWeek.TUESDAY, "M", DayOfWeek.WEDNESDAY, "M",
            DayOfWeek.THURSDAY, "J", DayOfWeek.FRIDAY, "V", DayOfWeek.SATURDAY, "S",
            DayOfWeek.SUNDAY, "D"
    );
    public static final Integer HISTORY_MONTHS = 12;
    public static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    //work statuses
    public static final String WORK_ONLINE = "Online";
    public static final String WORK_TEMPORARY_STOP = "Temporary Stop";
    public static final String WORK_OFFLINE = "Offline";
    public static final String LAST_ACTIVE_NEVER = "Never";

    //status dialog codes
    public static final String STATUS_UNKNOWN = "Unknown";

    //work messages
    public static final String NOTICE_TITLE = "END SCHEDULE NOTICE";
    public static final String SESSION_WARNING_MESSAGE = """
            Your work session has reached 8 hours and 30 minutes.
            
            If you wish to work overtime, please press 'Continue'.
            If you want to end your workday, you can press the 'Close' button.
            
            Note: If no button is pressed, your work session will automatically
            close the session after 10 minutes.""";

    public static final String HOURLY_WARNING_MESSAGE = """
            You have completed another hour of overtime work.
            
            Please decide whether to continue working or end your session.
            For your wellbeing, we recommend considering ending your workday.
            
            Note: If no selection is made within 5 minutes, your session will
            automatically end for your protection.""";

    public static final String LONG_TEMP_STOP_WARNING = """
            Your work session has been in temporary stop for an extended period.
            
            Current temporary stop duration: %d hours %d minutes
            
            Options:
            - 'Continue Break': Maintain temporary stop status
            - 'Resume Work': Return to active work session
            - 'End Session': Close work day
            
            Note: If no selection is made within 5 minutes, temporary stop will continue.
            Maximum allowed temporary stop is 15 hours.""";
}













