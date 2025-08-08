package com.ctgraphdep.session.config;

public class CommandConstants {

    //monitoring states
    public static final String START = "start";
    public static final String CLEAR = "clear";
    public static final String STOP = "stop";
    public static final String ACTIVATE_HOURLY = "activate";
    public static final String PAUSE = "pause";
    public static final String DEACTIVATE = "deactivate";

    //commands codes
    public static final String START_DAY = "start day";
    public static final String SPECIAL_START_DAY = "post-special-day start day";
    public static final String END_DAY = "end day";
    public static final String SPECIAL_END_DAY = "post-special-day end day";
    public static final String WORKTIME_COMMAND= "resolve work time entry";
    public static final String SPECIAL_WORKTIME_COMMAND ="resolve work time entry";
    public static final String RESUME_TEMP_STOP_COMMAND= "resume from temporary stop";
    public static final String SPECIAL_RESUME_TEMP_STOP_COMMAND= "post-special-day resume from temporary stop";
    public static final String RESUME_PREVIOUS_SESSION = "resume previous session";
    public static final String SPECIAL_RESUME_PREVIOUS_SESSION = "post-special-day resume previous session";
    public static final String START_TEMP_STOP_COMMAND = "start temporary stop";
    public static final String SPECIAL_START_TEMP_STOP_COMMAND = "post-special-day start temporary stop";
    public static final String AUTO_END_SESSION = "auto end session";
    public static final String SPECIAL_AUTO_END_SESSION = "post-special-day auto end session";

    //validation codes
    public static final String END_SESSION = "end session";
    public static final String SESSION_RESUME = "resume session";
}
