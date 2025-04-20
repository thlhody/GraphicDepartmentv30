package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user/settings")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_ADMIN', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
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

        // Use the new helper method instead of checkUserAccess
        User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
        if (currentUser == null) {
            return "redirect:/login";
        }

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
            // Get user directly
            User user = getUser(userDetails);
            if (user == null) {
                return "redirect:/login";
            }

            boolean success = getUserService().changePassword(user.getUserId(), currentPassword, newPassword);

            if (success) {
                LoggerUtil.info(this.getClass(), "Password changed successfully for user: " + user.getUsername());
                redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully");
            } else {
                LoggerUtil.warn(this.getClass(), "Failed password change attempt for user: " + user.getUsername() + " - incorrect current password");
                redirectAttributes.addFlashAttribute("errorMessage", "Current password is incorrect");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error changing password: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error changing password: " + e.getMessage());
        }

        return "redirect:/user/settings";
    }
}