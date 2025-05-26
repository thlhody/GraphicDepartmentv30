package com.ctgraphdep.controller;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController extends BaseController {

    public HomeController(UserService userService, FolderStatus folderStatus, TimeValidationService timeValidationService) {
        super(userService, folderStatus, timeValidationService);
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        LoggerUtil.info(this.getClass(), "Accessing home page at " + getStandardCurrentDateTime());

        // If user is authenticated, redirect to appropriate dashboard
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            LoggerUtil.info(this.getClass(), "User " + username + " authenticated, determining dashboard");

            // Check for specific roles in order of precedence
            if (hasRole(authentication, SecurityConstants.SPRING_ROLE_ADMIN)) {
                LoggerUtil.info(this.getClass(), "Redirecting to admin dashboard");
                return "redirect:/admin";
            }
            if (hasRole(authentication, SecurityConstants.SPRING_ROLE_TEAM_LEADER)) {
                LoggerUtil.info(this.getClass(), "Redirecting to team leader dashboard");
                return "redirect:/team-lead";
            }
            if (hasRole(authentication, SecurityConstants.SPRING_ROLE_TL_CHECKING)) {
                LoggerUtil.info(this.getClass(), "Redirecting to team leader dashboard");
                return "redirect:/team-checking";
            }
            if (hasRole(authentication, SecurityConstants.ROLE_USER_CHECKING)) {
                LoggerUtil.info(this.getClass(), "Redirecting to team leader dashboard");
                return "redirect:/user-checking";
            }
            if (hasRole(authentication, SecurityConstants.SPRING_ROLE_CHECKING)) {
                LoggerUtil.info(this.getClass(), "Redirecting to team leader dashboard");
                return "redirect:/checking";
            }

            LoggerUtil.info(this.getClass(), "Redirecting to user dashboard");
            return "redirect:/user";
        }

        LoggerUtil.info(this.getClass(), "No authentication, redirecting to login");
        return "redirect:/login";
    }

    // Helper method to check for specific roles
    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }
}