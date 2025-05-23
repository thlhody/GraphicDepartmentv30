package com.ctgraphdep.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class VersionModelAttribute {

    @Value("${cttt.version:0.0.0}")
    private String currentVersion;

    // Add static version storage
    private static String appVersion = "0.0.0";

    @ModelAttribute("appVersion")
    public String getAppVersion() {
        appVersion = currentVersion; // Update the static variable
        return currentVersion;
    }

    // Add static getter for use in services
    public static String getCurrentVersion() {
        return appVersion;
    }
}