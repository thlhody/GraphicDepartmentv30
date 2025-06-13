package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.service.UserManagementService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/admin/settings")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminSettingsController extends BaseController {

    private final UserManagementService userManagementService;
    private final WorktimeOperationService worktimeOperationService;

    @Autowired
    public AdminSettingsController(UserService userService, FolderStatus folderStatus, TimeValidationService timeValidationService,
            UserManagementService userManagementService, WorktimeOperationService worktimeOperationService) {
        super(userService, folderStatus, timeValidationService);
        this.userManagementService = userManagementService;
        this.worktimeOperationService = worktimeOperationService;
    }

    @GetMapping
    public String settings(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(required = false) Integer userId, Model model) {

        // Use checkUserAccess for admin role verification
        String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (accessCheck != null) {
            return accessCheck;
        }

        LoggerUtil.info(this.getClass(), "Accessing admin settings at " + getStandardCurrentDateTime());

        List<User> users = userManagementService.getNonAdminUsers();
        List<PaidHolidayEntryDTO> holidayEntries = createHolidayEntriesFromUsers(users);

        model.addAttribute("users", users);
        model.addAttribute("holidayEntries", holidayEntries);
        model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // Handle user form
        if (userId != null) {
            userManagementService.getUserById(userId).ifPresentOrElse(
                    user -> {
                        model.addAttribute("userForm", user);
                        model.addAttribute("isNewUser", false);

                        // Get holiday entry for the user
                        PaidHolidayEntryDTO holidayEntry = holidayEntries.stream()
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
    public String saveUser(@AuthenticationPrincipal UserDetails userDetails, @ModelAttribute("userForm") User user, @RequestParam(required = false) Boolean isNewUser,
            @RequestParam(defaultValue = "21") Integer paidHolidayDays, RedirectAttributes redirectAttributes) {

        // Use checkUserAccess for consistent access control
        String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (accessCheck != null) {
            return accessCheck;
        }

        LoggerUtil.info(this.getClass(), String.format("%s user with %d holiday days at %s", isNewUser ? "Creating new" : "Updating", paidHolidayDays, getStandardCurrentDateTime()));

        try {
            if (isNewUser) {
                userManagementService.saveUser(user, paidHolidayDays);
                redirectAttributes.addFlashAttribute("successMessage", "User created successfully");
            } else {
                userManagementService.updateUser(user, paidHolidayDays);
                redirectAttributes.addFlashAttribute("successMessage", "User updated successfully");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/settings" + (isNewUser ? "" : "?userId=" + user.getUserId());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving user: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving user");
            return "redirect:/admin/settings" + (isNewUser ? "" : "?userId=" + user.getUserId());
        }

        return "redirect:/admin/settings";
    }

    @GetMapping("/user/delete/{userId}")
    public String deleteUser(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Integer userId, RedirectAttributes redirectAttributes) {

        // Use checkUserAccess for consistent access control
        String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (accessCheck != null) {
            return accessCheck;
        }

        LoggerUtil.info(this.getClass(), "Deleting user with ID: " + userId + " at " + getStandardCurrentDateTime());

        try {
            userManagementService.deleteUser(userId);
            redirectAttributes.addFlashAttribute("successMessage", "User deleted successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error deleting user: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting user");
        }

        return "redirect:/admin/settings";
    }

    @PostMapping("/change-password")
    public String changePassword(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String currentPassword,
            @RequestParam String newPassword, RedirectAttributes redirectAttributes) {

        // Use checkUserAccess for consistent access control
        String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (accessCheck != null) {
            return accessCheck;
        }

        try {
            LoggerUtil.info(this.getClass(), "Changing admin password at " + getStandardCurrentDateTime());

            // Get the authenticated user using getUser method from BaseController
            User admin = getUser(userDetails);
            boolean success = userManagementService.changePassword(admin.getUserId(), currentPassword, newPassword);

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

    private List<PaidHolidayEntryDTO> createHolidayEntriesFromUsers(List<User> users) {
        return users.stream()
                .map(user -> {
                    try {
                        // Get holiday balance using the new command approach
                        Integer holidayBalance = worktimeOperationService.getHolidayBalance(user.getUsername());

                        PaidHolidayEntryDTO entry = PaidHolidayEntryDTO.fromUser(user);
                        entry.setPaidHolidayDays(holidayBalance != null ? holidayBalance : 0);
                        return entry;
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), String.format(
                                "Error getting holiday balance for user %s: %s", user.getUsername(), e.getMessage()));

                        // Return entry with 0 balance on error
                        PaidHolidayEntryDTO entry = PaidHolidayEntryDTO.fromUser(user);
                        entry.setPaidHolidayDays(0);
                        return entry;
                    }
                })
                .collect(java.util.stream.Collectors.toList());
    }
}