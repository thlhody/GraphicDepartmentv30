package com.ctgraphdep.controller;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;

//Controller handling login-related requests and system availability checks.
@Controller
@RequestMapping("/login")
public class LoginController extends BaseController {

    private static final String VIEW_LOGIN = "login";
    private static final String VIEW_ERROR = "error/system-error";

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
    private static final String MODE_ONLINE = "ONLINE";
    private static final String MODE_OFFLINE = "OFFLINE";
    private static final String MODE_EMERGENCY = "EMERGENCY";

    private final PathConfig pathConfig;

    @Autowired
    public LoginController(
            UserService userService,
            FolderStatusService folderStatusService,
            PathConfig pathConfig) {
        super(userService, folderStatusService);
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Handles the login page request and checks system availability.
     * @param model Spring MVC Model for view attributes
     * @return login view name or error page if critical system files are unavailable
     */
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
            return VIEW_ERROR;
        }
    }

    // Checks the availability of network and offline modes.
    private SystemAvailability checkSystemAvailability() {
        boolean networkAvailable = checkNetworkAvailability();
        boolean offlineModeAvailable = checkOfflineModeAvailability();

        // Handle when no mode is available
        if (!networkAvailable && !offlineModeAvailable) {
            LoggerUtil.error(this.getClass(),
                    "No available operation modes - system is in emergency mode");
            // Instead of throwing IOException, return a special emergency status
            return new SystemAvailability(false, false, true);
        }

        return new SystemAvailability(networkAvailable, offlineModeAvailable, false);
    }

    // Checks if network mode is available.
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

    // Checks if offline mode is available.
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

    // Populates model attributes based on system availability.
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
    }

    // Record containing system availability status.
    private record SystemAvailability(boolean networkAvailable, boolean offlineModeAvailable, boolean emergencyMode) {}
}