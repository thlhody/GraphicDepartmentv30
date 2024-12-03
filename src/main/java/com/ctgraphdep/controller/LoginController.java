package com.ctgraphdep.controller;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


//Controller handling login-related requests and system availability checks.
@Controller
@RequestMapping("/login")
public class LoginController extends BaseController {

    private static final String VIEW_LOGIN = "login";

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
    private static final String MODE_ONLINE = "ONLINE";
    private static final String MODE_OFFLINE = "OFFLINE";

    private final PathConfig pathConfig;

    @Autowired
    public LoginController(
            UserService userService,
            FolderStatusService folderStatusService,
            PathConfig pathConfig) {
        super(userService, folderStatusService);
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), "Initializing Login Controller");
    }

    /**
     * Handles the login page request and checks system availability.
     * @param model Spring MVC Model for view attributes
     * @return login view name
     * @throws ResponseStatusException if critical system files are unavailable*/
    @GetMapping
    public String login(Model model) {
        LoggerUtil.info(this.getClass(), "Accessing login page");

        try {
            SystemAvailability availability = checkSystemAvailability();
            populateModelAttributes(model, availability);
            return VIEW_LOGIN;

        } catch (IOException e) {
            LoggerUtil.error(this.getClass(),
                    "Error checking system availability: " + e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "System configuration error",
                    e);
        }
    }

    // Checks the availability of network and offline modes.
    private SystemAvailability checkSystemAvailability() throws IOException {
        boolean networkAvailable = checkNetworkAvailability();
        boolean offlineModeAvailable = checkOfflineModeAvailability();

        // Validate at least one mode is available
        if (!networkAvailable && !offlineModeAvailable) {
            LoggerUtil.error(this.getClass(),
                    "No available operation modes - system cannot function");
            throw new IOException("No available operation modes");
        }

        return new SystemAvailability(networkAvailable, offlineModeAvailable);
    }

    // Checks if network mode is available.
    private boolean checkNetworkAvailability() {
        boolean available = pathConfig.isNetworkAvailable();
        LoggerUtil.debug(this.getClass(),
                String.format("Network mode availability: %s", available));
        return available;
    }

    // Checks if offline mode is available.
    private boolean checkOfflineModeAvailability() {
        Path usersFilePath = pathConfig.getUsersJsonPath();
        boolean available = Files.exists(usersFilePath);
        LoggerUtil.debug(this.getClass(),
                String.format("Offline mode availability: %s", available));
        return available;
    }

    // Populates model attributes based on system availability.
    private void populateModelAttributes(Model model, SystemAvailability availability) {
        model.addAttribute(ATTR_NETWORK_AVAILABLE, availability.networkAvailable());
        model.addAttribute(ATTR_OFFLINE_AVAILABLE, availability.offlineModeAvailable());
        model.addAttribute(ATTR_MODE, availability.networkAvailable() ? MODE_ONLINE : MODE_OFFLINE);
        model.addAttribute(ATTR_TITLE, appTitle);
    }

    // Record containing system availability status.
    private record SystemAvailability(boolean networkAvailable, boolean offlineModeAvailable) {}
}