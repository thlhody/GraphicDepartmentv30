package com.ctgraphdep.config;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class WorkCode {

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
    public static final Integer START_HOUR = 7;
    public static final Integer HOUR_DURATION = 60;
    public static final Integer BUFFER_MINUTES = 10;
    public static final Integer HALF_HOUR_DURATION = 30;
    public static final Integer INTERVAL_HOURS_A = 4;
    public static final Integer INTERVAL_HOURS_B = 11;
    public static final Integer INTERVAL_HOURS_C = 8;


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

    //work statuses
    public static final String WORK_ONLINE = "Online";
    public static final String WORK_TEMPORARY_STOP = "Temporary Stop";
    public static final String WORK_OFFLINE = "Offline";
    public static final String LAST_ACTIVE_NEVER = "Never";

    //status dialog codes
    public static final String STATUS_UNKNOWN = "Unknown";

}













