package com.ctgraphdep.controller;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.PaidHolidayEntry;
import com.ctgraphdep.service.UserManagementService;
import com.ctgraphdep.service.HolidayManagementService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSettingsController {
    private final UserManagementService userService;
    private final HolidayManagementService holidayService;

    @Autowired
    public AdminSettingsController(
            UserManagementService userService,
            HolidayManagementService holidayService) {
        this.userService = userService;
        this.holidayService = holidayService;
        LoggerUtil.initialize(this.getClass(), "Initializing Admin Settings Controller");
    }

    @GetMapping
    public String settings(@RequestParam(required = false) Integer userId, Model model) {
        List<User> users = userService.getNonAdminUsers();
        List<PaidHolidayEntry> holidayEntries = holidayService.getHolidayList();

        model.addAttribute("users", users);
        model.addAttribute("holidayEntries", holidayEntries);  // Add this line to pass holiday entries

        // Handle user form
        if (userId != null) {
            userService.getUserById(userId).ifPresentOrElse(
                    user -> {
                        model.addAttribute("userForm", user);
                        model.addAttribute("isNewUser", false);

                        // Get holiday entry for the user
                        PaidHolidayEntry holidayEntry = holidayEntries.stream()
                                .filter(entry -> entry.getUserId().equals(userId))
                                .findFirst()
                                .orElse(null);
                        model.addAttribute("holidayEntry", holidayEntry);
                    },
                    () -> {
                        model.addAttribute("errorMessage", "User not found");
                        model.addAttribute("userForm", new User());
                        model.addAttribute("isNewUser", true);
                    }
            );
        } else {
            model.addAttribute("userForm", new User());
            model.addAttribute("isNewUser", true);
        }

        return "admin/settings";
    }

    @PostMapping("/user")
    public String saveUser(
            @ModelAttribute("userForm") User user,
            @RequestParam(required = false) Boolean isNewUser,
            @RequestParam(defaultValue = "21") Integer paidHolidayDays,
            RedirectAttributes redirectAttributes) {

        LoggerUtil.info(this.getClass(),
                String.format("%s user with %d holiday days",
                        isNewUser ? "Creating new" : "Updating",
                        paidHolidayDays));

        try {
            if (Boolean.TRUE.equals(isNewUser)) {
                userService.saveUser(user, paidHolidayDays);
                redirectAttributes.addFlashAttribute("successMessage", "User created successfully");
            } else {
                userService.updateUser(user, paidHolidayDays);
                redirectAttributes.addFlashAttribute("successMessage", "User updated successfully");
            }
        } catch (IllegalArgumentException e) {
            LoggerUtil.error(this.getClass(), "Validation error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/settings" +
                    (isNewUser ? "" : "?userId=" + user.getUserId());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving user: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving user");
            return "redirect:/admin/settings" +
                    (isNewUser ? "" : "?userId=" + user.getUserId());
        }

        return "redirect:/admin/settings";
    }

    @GetMapping("/user/delete/{userId}")
    public String deleteUser(@PathVariable Integer userId, RedirectAttributes redirectAttributes) {
        LoggerUtil.info(this.getClass(), "Deleting user with ID: " + userId);

        try {
            userService.deleteUser(userId);
            redirectAttributes.addFlashAttribute("successMessage", "User deleted successfully");
        } catch (IllegalArgumentException e) {
            LoggerUtil.error(this.getClass(), "Validation error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error deleting user: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting user");
        }

        return "redirect:/admin/settings";
    }

    @PostMapping("/change-password")
    public String changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 RedirectAttributes redirectAttributes) {
        try {
            User admin = userService.getUserByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("Admin not found"));

            boolean success = userService.changePassword(admin.getUserId(), currentPassword, newPassword);

            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Current password is incorrect");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error changing admin password: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error changing password");
        }

        return "redirect:/admin/settings";
    }
}