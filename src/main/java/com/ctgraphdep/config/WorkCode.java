package com.ctgraphdep.config;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class WorkCode {

    //file format
    public static final String BACKUP_EXTENSION = ".bak";

    //time off codes
    public static final String NATIONAL_HOLIDAY_CODE = "SN";
    public static final String TIME_OFF_CODE = "CO";
    public static final String MEDICAL_LEAVE_CODE = "CM";
    public static final String ERROR_TAG = "ER";
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
    public static final Integer ON_FOR_TEN_SECONDS = 10000; // 10 seconds in milliseconds
    public static final Integer ONCE_PER_DAY_TIMER = 24 * 60;

    public static final Integer ONE_MINUTE_DELAY = 1;     // 8.5 hours in minutes (8 * 60 + 30)510
    public static final Integer CHECK_INTERVAL = 30; // checks every 30 minutes in order to see if the end time is reached
    public static final Integer HOURLY_INTERVAL = 60; //for hourly checks
    private static final Integer five_minutes = 5 * 60 * 1000;
    private static final Integer ten_minutes = 10 * 60 * 1000;
    private static final Integer twelve_hours = 12 * 60 * 60 * 1000; // 12 hours in milliseconds

    public static final Integer ON_FOR_FIVE_MINUTES = five_minutes; // Auto-timer set for 5 minutes (WorkCode.CHECK_EVERY_FIVE_MINUTES)
    public static final Integer ON_FOR_TEN_MINUTES = ten_minutes;  // Auto-timer set for 10 minutes (WorkCode.CHECK_EVERY_TEN_MINUTES)
    public static final Integer ON_FOR_TWELVE_HOURS = twelve_hours;   // Auto-timer set for 12 hours

    public static Integer MAX_TEMP_STOP_HOURS = 15;                 // 24 - MAX_WORK_HOURS

    // Constants for timing start notice
    public static final int WORK_START_HOUR = 5;  // 5:00 AM
    public static final int WORK_END_HOUR = 17;   // 5:00 PM

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
    public static final String START_DAY_TITLE = "WORK DAY START REMINDER";
    public static final String TEST_NOTICE = "SESSION MONITORING ACTIVE";
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

    public static final String START_DAY_MESSAGE = """
            It's time to start your work day.
            
            Would you like to start tracking your work time now?
            
            Note: If you choose to skip, this reminder will not appear
            again until tomorrow.""";

    public static final String TEST_MESSAGE = """
            The session monitoring system is now active and will track your work time.
            You will be notified when your scheduled work time is complete.""";

    public static final String TEST_MESSAGE_TRAY = "Session monitoring active. Click to open application.";
    public static final String SESSION_WARNING_TRAY = "Your work session has reached the scheduled time. Click to open app.";
    public static final String HOURLY_WARNING_TRAY = "You've completed another hour of overtime. Click to open app.";
    public static final String LONG_TEMP_STOP_WARNING_TRAY = "Temporary stop in progress for %d hours %d minutes. Click to open app.";
    public static final String START_DAY_MESSAGE_TRAY = "It's time to start your work day. Click to open app.";

    public static final String START_WORK = "Start Work";
    public static final String SKIP_BUTTON = "Skip";
    public static final String DISMISS_BUTTON = "Dismiss";
    public static final String CONTINUE_WORKING = "Continue Working";
    public static final String END_SESSION = "End Session" ;
    public static final String CONTINUE_BREAK = "Continue Break";
    public static final String RESUME_WORK = "Resume Work";
    public static final String OPEN_WEBSITE = "Open Website";

    public static final String SCHEDULE_END_TYPE = "SCHEDULE_END";
    public static final String OVERTIME_TYPE = "OVERTIME";
    public static final String TEMP_STOP_TYPE = "TEMP_STOP";
    public static final String START_DAY_TYPE = "START_DAY";
}













