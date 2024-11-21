package com.ctgraphdep.config;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class WorkCode {

    //time off codes
    public static final String NATIONAL_HOLIDAY_CODE = "SN";
    public static final String TIME_OFF_CODE = "CO";
    public static final String MEDICAL_LEAVE_CODE = "CM";
    public static final String NATIONAL_HOLIDAY_CODE_LONG = "SN - National Holiday";
    public static final String TIME_OFF_CODE_LONG = "CO - Holiday";
    public static final String MEDICAL_LEAVE_CODE_LONG = "CM - Medical Leave";

    //work hours codes
    public static final Integer FULL_WORKDAY_MINUTES = 480;
    public static final Integer START_HOUR = 7;
    public static final Integer HOUR_DURATION = 60;
    public static final Integer BUFFER_MINUTES = 10;
    public static final Integer HALF_HOUR_DURATION = 30;
    public static final Integer INTERVAL_HOURS_A = 4;
    public static final Integer INTERVAL_HOURS_B = 11;
    public static final Integer INTERVAL_HOURS_C = 8;
    public static final Integer SAVE_INTERVAL_MINUTES = 10;


    //date format
    public static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm");
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";

    //days format
    public static final Map<DayOfWeek, String> ROMANIAN_DAY_INITIALS = Map.of(
            DayOfWeek.MONDAY, "L", DayOfWeek.TUESDAY, "M", DayOfWeek.WEDNESDAY, "M",
            DayOfWeek.THURSDAY, "J", DayOfWeek.FRIDAY, "V", DayOfWeek.SATURDAY, "S",
            DayOfWeek.SUNDAY, "D"
    );

    //work statuses
    public static final String WORK_ONLINE = "Online";
    public static final String WORK_TEMPORARY_STOP = "Temporary Stop";
    public static final String WORK_RESUME = "Resume";
    public static final String WORK_OFFLINE = "Offline";
    public static final String NO_ACTIVE_SESSION = "No active session";

    //status dialog codes
    public static final String STATUS_NAME = "name";
    public static final String STATUS_UNKNOWN = "Unknown";
    public static final String STATUS_SESSION = "sessionStatus";
    public static final String STATUS_DAY_START_TIME = "dayStartTime";

    public static final String STATUS_NO_INFO = "N/A";
    public static final String STATUS_USER_ID = "userId";
    public static final String STATUS_LAST_ACTIVITY = "lastActivity";

    //standard password
    public static final String GENERIC_PASSWORD = "cottontex123";
}













