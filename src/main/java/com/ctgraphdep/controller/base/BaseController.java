package com.ctgraphdep.controller.base;

import com.ctgraphdep.model.dto.SyncFolderStatusDTO;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;

@ControllerAdvice
public abstract class BaseController {
    private final UserService userService;
    private final FolderStatus folderStatus;
    private final TimeValidationService timeValidationService;
    protected final Class<?> loggerClass;

    protected BaseController(UserService userService,
                             FolderStatus folderStatus,
                             TimeValidationService timeValidationService) {  // Modified constructor
        this.userService = userService;
        this.folderStatus = folderStatus;
        this.timeValidationService = timeValidationService;
        this.loggerClass = this.getClass();
        LoggerUtil.initialize(loggerClass, null);
    }

    @ModelAttribute("syncStatus")
    public SyncFolderStatusDTO addSyncStatus() {
        return folderStatus.getStatus();
    }

    protected User getUser(UserDetails userDetails) {
        if (userDetails == null) {
            LoggerUtil.error(this.getClass(), "Attempt to get user with null userDetails");
            return null;
        }

        Optional<User> userOpt = userService.getUserByUsername(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            LoggerUtil.error(this.getClass(), String.format("User not found for username: %s", userDetails.getUsername()));
            return null;
        }

        return userOpt.get();
    }

    protected UserService getUserService() {
        return userService;
    }

    protected TimeValidationService getTimeValidationService() {
        return timeValidationService;
    }

    /**
     * Determines the appropriate dashboard URL based on user role
     * @param user The user
     * @return The dashboard URL for the user's role
     */
    protected String getDashboardUrlForUser(User user) {
        if (user == null) return "/login";

        // Normalize the role by removing any ROLE_ prefix
        String normalizedRole = user.getRole().replace("ROLE_", "");

        // Map each role to its specific dashboard URL
        return switch (normalizedRole) {
            case "ADMIN" -> "/admin";
            case "TEAM_LEADER" -> "/team-lead";
            case "TL_CHECKING" -> "/team-checking";
            case "USER_CHECKING" -> "/user-checking";
            case "CHECKING" -> "/checking";
            case "USER" -> "/user";
            default -> {
                // Default to user dashboard if role is not recognized
                LoggerUtil.warn(this.getClass(), "Unrecognized role for dashboard URL: " + normalizedRole);
                yield "/user";
            }
        };
    }

    /**
     * Gets the current user and adds common model attributes
     * @param userDetails Spring Security UserDetails
     * @param model Spring Model
     * @return The current user or null if authentication failed
     */
    protected User prepareUserAndCommonModelAttributes(UserDetails userDetails, Model model) {
        if (userDetails == null) return null;

        User currentUser = getUser(userDetails);
        if (currentUser == null) return null;

        // Add common attributes
        String dashboardUrl = getDashboardUrlForUser(currentUser);
        model.addAttribute("dashboardUrl", dashboardUrl);
        model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.addAttribute("user", currentUser);

        return currentUser;
    }


    /**
     * Gets standardized current date
     * @return Current date from standard time service or system date if service unavailable
     */
    protected LocalDate getStandardCurrentDate() {
        if (timeValidationService == null) {
            return LocalDate.now(); // Fallback
        }

        try {
            GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
            return timeValues.getCurrentDate();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting standard time values: " + e.getMessage());
            return LocalDate.now(); // Fallback
        }
    }

    /**
     * Gets standardized current date and time
     * @return Current date/time from standard time service or system date/time if service unavailable
     */
    protected LocalDateTime getStandardCurrentDateTime() {
        if (timeValidationService == null) {
            return LocalDateTime.now(); // Fallback
        }

        try {
            GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);
            return timeValues.getCurrentTime();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting standard time values: " + e.getMessage());
            return LocalDateTime.now(); // Fallback
        }
    }

    /**
     * Validate user access based on required roles
     * @param userDetails Spring Security UserDetails
     * @param requiredRoles Roles that are allowed to access the resource
     * @return User object if access is granted, null otherwise
     */
    protected User validateUserAccess(UserDetails userDetails, String... requiredRoles) {
        // Retrieve the user
        User currentUser = getUser(userDetails);

        // If user is not found, deny access
        if (currentUser == null) {
            LoggerUtil.warn(this.getClass(), "Attempted access by null user");
            return null;
        }

        // Check if user has any of the required roles
        boolean hasAccess = Arrays.stream(requiredRoles).anyMatch(currentUser::hasRole);

        if (!hasAccess) {
            LoggerUtil.warn(this.getClass(), String.format("User %s denied access. Required roles: %s, User role: %s", currentUser.getUsername(), Arrays.toString(requiredRoles), currentUser.getRole()));
            return null;
        }

        return currentUser;
    }

    /**
     * Convenience method to redirect unauthorized users
     * @param userDetails Spring Security UserDetails
     * @param requiredRoles Roles that are allowed to access the resource
     * @return Redirect string if unauthorized, null if access is granted
     */
    protected String checkUserAccess(UserDetails userDetails, String... requiredRoles) {
        User currentUser = validateUserAccess(userDetails, requiredRoles);
        return currentUser == null ? "redirect:/user" : null;
    }

    /**
     * Determine the year to use, defaulting to current year if not provided
     * @param year Input year (can be null)
     * @return Selected year
     */
    protected int determineYear(Integer year) {
        return year != null ? year : getStandardCurrentDate().getYear();
    }

    /**
     * Determine the month to use, defaulting to current month if not provided
     * @param month Input month (can be null)
     * @return Selected month
     */
    protected int determineMonth(Integer month) {
        return month != null ? month : getStandardCurrentDate().getMonthValue();
    }
}
