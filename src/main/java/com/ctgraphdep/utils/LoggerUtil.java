package com.ctgraphdep.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggerUtil {

    private LoggerUtil() {
        // Private constructor to prevent instantiation
    }

    public static void initialize(Class<?> clazz, String additionalInfo) {
        Logger logger = LoggerFactory.getLogger(clazz);
        String message = "Initializing " + clazz.getSimpleName();
        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            message += ": " + additionalInfo;
        }
        logger.info(message);
    }

    public static void info(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).info(message);
    }

    public static void warn(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).warn(message);
    }

    public static void error(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).error(message);
    }

    public static void error(Class<?> clazz, String message, Throwable throwable) {
        LoggerFactory.getLogger(clazz).error(message, throwable);
    }

    public static void debug(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).debug(message);
    }

    public static void logAndThrow(Class<?> clazz, String message, Exception e) {
        LoggerFactory.getLogger(clazz).error(message, e);
        throw new RuntimeException(message, e);
    }
}