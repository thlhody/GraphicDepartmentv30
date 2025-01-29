package com.ctgraphdep.controller;

import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user/settings")
@Secured("ROLE_USER")
public class UserSettingsController {
    private final UserService userService;

    public UserSettingsController(UserService userService) {
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String settings(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing user settings");

        User user = userService.getUserByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        model.addAttribute("user", user);
        return "user/settings";
    }

    @PostMapping("/change-password")
    public String changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 RedirectAttributes redirectAttributes) {
        try {
            // Get user by username instead of trying to parse it as Long
            User user = userService.getUserByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            boolean success = userService.changePassword(user.getUserId(), currentPassword, newPassword);

            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Current password is incorrect");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/user/settings";
    }
}