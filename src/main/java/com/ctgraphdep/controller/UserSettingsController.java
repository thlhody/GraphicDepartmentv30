package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("isAuthenticated()")
@RequestMapping("/user/settings")
@Secured("USER")
public class UserSettingsController extends BaseController {

    public UserSettingsController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService) {
        super(userService, folderStatus, timeValidationService);
    }

    @GetMapping
    public String settings(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing user settings page at " + getStandardCurrentDateTime());

        String accessCheck = checkUserAccess(userDetails, "USER", "ADMIN", "TEAM_LEADER");
        if (accessCheck != null) {
            return accessCheck;
        }

        User user = getUser(userDetails);

        // Determine dashboard URL based on user role
        String dashboardUrl = user.hasRole("TEAM_LEADER") ? "/team-lead" : user.hasRole("ADMIN") ? "/admin" : "/user";

        model.addAttribute("dashboardUrl", dashboardUrl);
        model.addAttribute("user", user);
        model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return "user/settings";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            RedirectAttributes redirectAttributes) {

        LoggerUtil.info(this.getClass(), "Password change attempt at " + getStandardCurrentDateTime());

        try {
            // Use validateUserAccess for more thorough role checking
            User user = validateUserAccess(userDetails, "USER", "ADMIN", "TEAM_LEADER");
            if (user == null) {
                return "redirect:/login";
            }

            boolean success = getUserService().changePassword(user.getUserId(), currentPassword, newPassword);

            if (success) {
                LoggerUtil.info(this.getClass(),
                        "Password changed successfully for user: " + user.getUsername());
                redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully");
            } else {
                LoggerUtil.warn(this.getClass(),
                        "Failed password change attempt for user: " + user.getUsername() + " - incorrect current password");
                redirectAttributes.addFlashAttribute("errorMessage", "Current password is incorrect");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error changing password: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error changing password: " + e.getMessage());
        }

        return "redirect:/user/settings";
    }
}