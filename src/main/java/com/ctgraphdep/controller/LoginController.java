package com.ctgraphdep.controller;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/login")
public class LoginController extends BaseController {

    private static final String VIEW_LOGIN = "login";
    private static final String VIEW_ERROR = "error/system-error";
    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Value("${dbj.login}")
    private String loginPath;

    @Value("${dbj.users.filename}")
    private String usersFilename;

    @Value("${app.title:CTTT}")
    private String appTitle;

    private static final String ATTR_NETWORK_AVAILABLE = "networkAvailable";
    private static final String ATTR_OFFLINE_AVAILABLE = "offlineModeAvailable";
    private static final String ATTR_MODE = "mode";
    private static final String ATTR_TITLE = "title";
    private static final String ATTR_ERROR = "error";
    private static final String ATTR_SYSTEM_TIME = "systemTime";
    private static final String MODE_ONLINE = "ONLINE";
    private static final String MODE_OFFLINE = "OFFLINE";
    private static final String MODE_EMERGENCY = "EMERGENCY";

    private final PathConfig pathConfig;

    @Autowired
    public LoginController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            PathConfig pathConfig) {
        super(userService, folderStatus, timeValidationService);
        this.pathConfig = pathConfig;
    }

    @GetMapping
    public String login(Model model) {
        LoggerUtil.info(this.getClass(), "Accessing login page");

        try {
            SystemAvailability availability = checkSystemAvailability();
            populateModelAttributes(model, availability);
            return VIEW_LOGIN;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Critical error checking system availability: " + e.getMessage(), e);
            model.addAttribute(ATTR_ERROR, "System configuration error: " + e.getMessage());
            model.addAttribute(ATTR_TITLE, appTitle + " - System Error");

            // Add current system time using standardized time
            addSystemTimeToModel(model);

            return VIEW_ERROR;
        }
    }

    private SystemAvailability checkSystemAvailability() {
        boolean networkAvailable = checkNetworkAvailability();
        boolean offlineModeAvailable = checkOfflineModeAvailability();

        // Handle when no mode is available
        if (!networkAvailable && !offlineModeAvailable) {
            LoggerUtil.error(this.getClass(),
                    "No available operation modes - system is in emergency mode");
            return new SystemAvailability(false, false, true);
        }

        return new SystemAvailability(networkAvailable, offlineModeAvailable, false);
    }

    private boolean checkNetworkAvailability() {
        try {
            boolean available = pathConfig.isNetworkAvailable();
            LoggerUtil.debug(this.getClass(),
                    String.format("Network mode availability: %s", available));
            return available;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking network availability: " + e.getMessage(), e);
            return false;
        }
    }

    private boolean checkOfflineModeAvailability() {
        try {
            Path usersFilePath = pathConfig.getLocalUsersPath();
            boolean available = Files.exists(usersFilePath);
            LoggerUtil.debug(this.getClass(),
                    String.format("Offline mode availability: %s", available));
            return available;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking offline mode availability: " + e.getMessage(), e);
            return false;
        }
    }

    private void populateModelAttributes(Model model, SystemAvailability availability) {
        model.addAttribute(ATTR_NETWORK_AVAILABLE, availability.networkAvailable());
        model.addAttribute(ATTR_OFFLINE_AVAILABLE, availability.offlineModeAvailable());

        String mode;
        if (availability.emergencyMode()) {
            mode = MODE_EMERGENCY;
            model.addAttribute(ATTR_ERROR, "System is operating in emergency mode. Please contact administrator.");
        } else {
            mode = availability.networkAvailable() ? MODE_ONLINE : MODE_OFFLINE;
        }

        model.addAttribute(ATTR_MODE, mode);
        model.addAttribute(ATTR_TITLE, appTitle);

        // Add current system time using standardized time
        addSystemTimeToModel(model);
    }

    private void addSystemTimeToModel(Model model) {
        try {
            // Use standardized time from base controller
            LocalDateTime currentTime = getStandardCurrentDateTime();
            String formattedTime = currentTime.format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN));
            model.addAttribute(ATTR_SYSTEM_TIME, formattedTime);

            LoggerUtil.debug(this.getClass(), "Added system time to model: " + formattedTime);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Failed to add system time to model: " + e.getMessage());
            // Add fallback time if standard time fails
            model.addAttribute(ATTR_SYSTEM_TIME,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN)));
        }
    }

    private record SystemAvailability(boolean networkAvailable, boolean offlineModeAvailable, boolean emergencyMode) {}
}