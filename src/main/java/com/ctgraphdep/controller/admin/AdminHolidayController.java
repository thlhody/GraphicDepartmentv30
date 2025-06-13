package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.dto.PaidHolidayEntryDTO;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.FolderStatus;
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
    public String updateHolidays(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer userId,
            @RequestParam Integer days,
            RedirectAttributes redirectAttributes) {

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
    public String viewUserHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer userId,
            Model model,
            RedirectAttributes redirectAttributes) {

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
                    "Loading time off history for user %s (ID: %d)", username, userId));

            // REFACTORED: Load time off history using available methods
            // Note: The command system doesn't have a specific command for history loading
            // since this is a display operation. We could extend it or use existing services.
            List<WorkTimeTable> timeOffs = loadUserTimeOffHistory(username, userId);

            model.addAttribute("user", user);
            model.addAttribute("timeOffs", timeOffs);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded %d time off entries for user %s", timeOffs.size(), username));

            return "admin/holiday-history";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error viewing holiday history: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading holiday history");
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

            LoggerUtil.debug(this.getClass(), String.format(
                    "Retrieved %d users from cache", users.size()));

            // Convert to DTOs, filtering out admin users
            List<PaidHolidayEntryDTO> entries = users.stream()
                    .filter(user -> !user.isAdmin())
                    .map(user -> {
                        LoggerUtil.debug(this.getClass(), String.format(
                                "Processing user %s (ID: %d) - holidayDays: %s",
                                user.getUsername(), user.getUserId(), user.getPaidHolidayDays()));
                        return PaidHolidayEntryDTO.fromUser(user);
                    })
                    .collect(Collectors.toList());

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully converted %d users to holiday entries", entries.size()));

            return entries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading holiday list from cache: %s", e.getMessage()), e);
            return List.of(); // Return empty list on error
        }
    }

    /**
     * Load user time off history
     * This functionality could be added to the command system in the future,
     * but for now we implement a simple version
     */
    private List<WorkTimeTable> loadUserTimeOffHistory(String username, Integer userId) {
        try {
            LoggerUtil.debug(this.getClass(), String.format(
                    "Loading time off history for user %s", username));

            // For now, load recent worktime data and filter time off entries
            // This could be enhanced by adding a specific command for history loading
            int currentYear = java.time.LocalDate.now().getYear();
            List<WorkTimeTable> currentYearData = worktimeOperationService.loadUserWorktime(username, currentYear, 12);

            // Filter only time off entries
            List<WorkTimeTable> timeOffEntries = currentYearData.stream()
                    .filter(entry -> entry.getTimeOffType() != null)
                    .filter(entry -> entry.getUserId().equals(userId))
                    .sorted((e1, e2) -> e2.getWorkDate().compareTo(e1.getWorkDate())) // Newest first
                    .collect(Collectors.toList());

            LoggerUtil.debug(this.getClass(), String.format(
                    "Found %d time off entries for user %s", timeOffEntries.size(), username));

            return timeOffEntries;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading time off history for user %s: %s", username, e.getMessage()), e);
            return List.of(); // Return empty list on error
        }
    }
}