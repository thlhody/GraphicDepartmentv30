package com.ctgraphdep.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class VersionModelAttribute {

    @Value("${cttt.version:0.0.0}")
    private String currentVersion;

    @ModelAttribute("appVersion")
    public String getAppVersion() {
        return currentVersion;
    }
}