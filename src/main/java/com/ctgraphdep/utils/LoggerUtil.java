// src/main/java/com/ctgraphdep/utils/LoggerUtil.java
package com.ctgraphdep.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

    public static void logUserAction(String action, String details) {
        Logger logger = LoggerFactory.getLogger("UserActions");
        String username = getCurrentUsername();
        MDC.put("username", username);
        try {
            logger.info("{} - {}", action, details);
        } finally {
            MDC.remove("username");
        }
    }

    public static void logControllerSwitch(Class<?> fromController, Class<?> toController) {
        Logger logger = LoggerFactory.getLogger("ControllerSwitches");
        String username = getCurrentUsername();
        MDC.put("username", username);
        try {
            logger.info("Switching from {} to {}", fromController.getSimpleName(), toController.getSimpleName());
        } finally {
            MDC.remove("username");
        }
    }

    private static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "anonymous";
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

    public static void trace(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).trace(message);
    }

    public static void logException(Class<?> clazz, String message, Exception e) {
        LoggerFactory.getLogger(clazz).error(message, e);
    }

    public static void logAndThrow(Class<?> clazz, String message, Exception e) throws RuntimeException {
        LoggerFactory.getLogger(clazz).error(message, e);
        throw new RuntimeException(message, e);
    }
}