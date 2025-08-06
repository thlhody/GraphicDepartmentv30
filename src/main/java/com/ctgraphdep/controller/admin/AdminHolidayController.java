package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.AllUsersCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import com.ctgraphdep.worktime.model.OperationResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REFACTORED AdminHolidayController using the new Command System.
 * Key Changes:
 * - Replaced HolidayManagementService with WorktimeOperationService
 * - Uses OperationResult for standardized error handling
 * - Simplified holiday balance operations through commands
 * - Maintains display functionality through AllUsersCacheService
 */
@Controller
@RequestMapping("/admin/holidays")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminHolidayController extends BaseController {

    private final WorktimeOperationService worktimeOperationService;
    private final AllUsersCacheService allUsersCacheService;

    public AdminHolidayController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            WorktimeOperationService worktimeOperationService,
            AllUsersCacheService allUsersCacheService) {
        super(userService, folderStatus, timeValidationService);
        this.worktimeOperationService = worktimeOperationService;
        this.allUsersCacheService = allUsersCacheService;
    }

    @GetMapping
    public String viewHolidays(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Loading holiday list using command system");

        // Use checkUserAccess utility method from BaseController
        String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (accessCheck != null) {
            return accessCheck;
        }

        try {
            // REFACTORED: Load holiday list from AllUsersCacheService (display data)
            List<PaidHolidayEntryDTO> entries = loadHolidayListFromCache();

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded %d holiday entries from cache", entries.size()));

            model.addAttribute("entries", entries);

            // Add debug information to the model
            if (entries.isEmpty()) {
                model.addAttribute("debugInfo", "No entries found in holiday list");
                LoggerUtil.warn(this.getClass(), "No holiday entries found - cache may be empty");
            } else {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Holiday entries loaded: %s",
                        entries.stream()
                                .map(e -> String.format("%s=%d days", e.getUsername(), e.getPaidHolidayDays()))
                                .collect(Collectors.joining(", "))));
            }

            return "admin/holidays";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading holiday list: " + e.getMessage(), e);
            model.addAttribute("errorMessage", "Error loading holiday data");
            model.addAttribute("entries", List.of()); // Empty list to prevent template errors
            return "admin/holidays";
        }
    }

    @PostMapping("/update")
    public String updateHolidays(@AuthenticationPrincipal UserDetails userDetails, @RequestParam Integer userId,
            @RequestParam Integer days, RedirectAttributes redirectAttributes) {

        // Use checkUserAccess utility method
        String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (accessCheck != null) {
            return accessCheck;
        }

        LoggerUtil.info(this.getClass(), String.format(
                "Processing holiday balance update - User: %d, Days: %d", userId, days));

        try {
            // Validate input
            if (days < 0) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Holiday days cannot be negative");
                return "redirect:/admin/holidays";
            }

            if (days > 365) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Holiday days cannot exceed 365 days");
                return "redirect:/admin/holidays";
            }

            // Get user information for logging
            Optional<User> userOpt = getUserService().getUserById(userId);
            String username = userOpt.map(User::getUsername).orElse("Unknown");

            // REFACTORED: Use command system for holiday balance update
            LoggerUtil.info(this.getClass(), String.format(
                    "Updating holiday balance for user %s (ID: %d) to %d days", username, userId, days));

            OperationResult result = worktimeOperationService.updateHolidayBalance(userId, days);

            if (result.isSuccess()) {
                String successMessage = String.format("Holiday days updated successfully for user %s", username);

                // Add side effects information if available
                if (result.hasSideEffects() && result.getSideEffects().isHolidayBalanceChanged()) {
                    successMessage += String.format(" (%d → %d days)",
                            result.getSideEffects().getOldHolidayBalance(),
                            result.getSideEffects().getNewHolidayBalance());
                }

                redirectAttributes.addFlashAttribute("successMessage", successMessage);

                LoggerUtil.info(this.getClass(), String.format(
                        "Holiday balance update successful: %s", result.getMessage()));
            } else {
                String errorMessage = String.format("Error updating holiday days for user %s: %s",
                        username, result.getMessage());
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);

                LoggerUtil.warn(this.getClass(), String.format(
                        "Holiday balance update failed: %s", result.getMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating holiday days: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error updating holiday days: " + e.getMessage());
        }

        return "redirect:/admin/holidays";
    }

    @GetMapping("/history/{userId}")
    public String viewUserHistory(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Integer userId,
            Model model, RedirectAttributes redirectAttributes) {

        // Use checkUserAccess utility method
        String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (accessCheck != null) {
            return accessCheck;
        }

        try {
            // Get user information
            Optional<User> userOpt = getUserService().getUserById(userId);
            if (userOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(), String.format("User not found with ID: %d", userId));
                redirectAttributes.addFlashAttribute("errorMessage", "User not found");
                return "redirect:/admin/holidays";
            }

            User user = userOpt.get();
            String username = user.getUsername();

            LoggerUtil.info(this.getClass(), String.format(
                    "Admin redirecting to time-off history for user %s (ID: %d)", username, userId));

            // REDIRECT to StatusController's time-off history page with admin source parameter
            // This uses the same logic and displays the same data as status controller
            return "redirect:/status/timeoff-history?userId=" + userId + "&from=admin";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error redirecting to time-off history: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading time-off history");
            return "redirect:/admin/holidays";
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * REFACTORED: Load holiday list from AllUsersCacheService
     * This replaces the deprecated HolidayManagementService.loadHolidayList()
     */
    private List<PaidHolidayEntryDTO> loadHolidayListFromCache() {
        try {
            LoggerUtil.debug(this.getClass(), "Loading holiday list from AllUsersCacheService");

            // Get all users from cache as User objects
            List<User> users = allUsersCacheService.getAllUsersAsUserObjects();

            LoggerUtil.debug(this.getClass(), String.format("Retrieved %d users from cache", users.size()));

            // Convert to DTOs, filtering out admin users
            List<PaidHolidayEntryDTO> entries = users.stream()
                    .filter(user -> !user.isAdmin())
                    .map(user -> {
                        LoggerUtil.debug(this.getClass(), String.format("Processing user %s (ID: %d) - holidayDays: %s",
                                user.getUsername(), user.getUserId(), user.getPaidHolidayDays()));
                        return PaidHolidayEntryDTO.fromUser(user);
                    })
                    .collect(Collectors.toList());

            LoggerUtil.info(this.getClass(), String.format("Successfully converted %d users to holiday entries", entries.size()));

            return entries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error loading holiday list from cache: %s", e.getMessage()), e);
            return List.of(); // Return empty list on error
        }
    }
}