package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * REFACTORED TimeOffController - Clean Architecture Implementation.
 * Key Changes:
 * 1. Uses TimeOffCacheService instead of direct file access
 * 2. Cache is built from final worktime files (merged at login)
 * 3. Write-through operations for time off requests
 * 4. Fast display using cached tracker data
 * Flow:
 * - Page Load: Cache builds tracker from final worktime → fast display
 * - Time Off Request: Write-through (worktime → balance → tracker → cache)
 */
@Controller
@RequestMapping("/user/timeoff")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserTimeOffController extends BaseController {

    private final TimeOffCacheService timeOffCacheService;

    public UserTimeOffController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService validationService,
            TimeOffCacheService timeOffCacheService) {
        super(userService, folderStatus, validationService);
        this.timeOffCacheService = timeOffCacheService;
    }

    /**
     * Display time off page - uses cache for fast display
     */
    @GetMapping
    public String getTimeOffPage(@AuthenticationPrincipal UserDetails userDetails, Model model, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Loading time off page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            String username = currentUser.getUsername();
            Integer userId = currentUser.getUserId();
            int currentYear = LocalDate.now().getYear();

            LoggerUtil.info(this.getClass(), String.format("Loading time off data for %s - %d (using cache)", username, currentYear));

            // Get time off summary from cache (builds from final worktime files if needed)
            TimeOffSummaryDTO summary = timeOffCacheService.getTimeOffSummary(username, userId, currentYear);

            // Prepare model for display
            model.addAttribute("user", sanitizeUserData(currentUser));
            model.addAttribute("summary", summary);
            model.addAttribute("currentYear", currentYear);

            // Add date constraints for form
            LocalDate today = getStandardCurrentDate();
            model.addAttribute("today", today.toString());
            model.addAttribute("minDate", today.minusDays(7).toString()); // Allow 1 week back
            model.addAttribute("maxDate", today.plusMonths(6).toString()); // 6 months ahead
            LoggerUtil.info(this.getClass(),"DEBUG - Summary availablePaidDays = " + summary.getAvailablePaidDays());


            LoggerUtil.info(this.getClass(), String.format("Successfully loaded time off page for %s with %d available days", username, summary.getAvailablePaidDays()));

            return "user/timeoff";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading time off page: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error loading time off data. Please try again.");
            return "redirect:/user/dashboard";
        }
    }

    /**
     * Process time off request - write-through to all layers
     */
    @PostMapping
    public String submitTimeOffRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String timeOffType,
            @RequestParam(required = false) boolean isSingleDayRequest,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), String.format("Processing time off request: %s to %s (%s, single: %s)", startDate, endDate, timeOffType, isSingleDayRequest));

            // Get user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                LoggerUtil.error(this.getClass(), "Unauthorized time off request attempt");
                redirectAttributes.addFlashAttribute("error", "Authentication required");
                return "redirect:/login";
            }

            String username = currentUser.getUsername();
            Integer userId = currentUser.getUserId();

            // Parse and validate dates
            List<LocalDate> dates = parseDateRange(startDate, endDate, isSingleDayRequest);
            if (dates.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Invalid date range");
                return "redirect:/user/timeoff";
            }

            // Validate time off type
            if (!isValidTimeOffType(timeOffType)) {
                redirectAttributes.addFlashAttribute("error", "Invalid time off type");
                return "redirect:/user/timeoff";
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Submitting time off request for %s: %d days (%s)", username, dates.size(), timeOffType));

            // Process request via cache service (write-through to all layers)
            boolean success = timeOffCacheService.addTimeOffRequest(username, userId, dates, timeOffType);

            if (success) {
                String message = String.format("Successfully submitted time off request for %d day(s) (%s)", dates.size(), getTimeOffTypeDisplayName(timeOffType));

                redirectAttributes.addFlashAttribute("successMessage", message);
                LoggerUtil.info(this.getClass(), String.format(
                        "Time off request processed successfully for %s: %d days", username, dates.size()));
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "Failed to submit time off request. Please check your available balance and try again.");
                LoggerUtil.warn(this.getClass(), String.format(
                        "Time off request failed for %s: %d days", username, dates.size()));
            }

            return "redirect:/user/timeoff";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing time off request: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "An error occurred while processing your request. Please try again.");
            return "redirect:/user/timeoff";
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Parse date range from form input
     */
    private List<LocalDate> parseDateRange(String startDate, String endDate, boolean isSingleDay) {
        try {
            LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate end = isSingleDay ? start : LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE);

            if (start.isAfter(end)) {
                LoggerUtil.warn(this.getClass(), "Start date is after end date: " + startDate + " > " + endDate);
                return new ArrayList<>();
            }

            List<LocalDate> dates = new ArrayList<>();
            LocalDate current = start;

            while (!current.isAfter(end)) {
                // Skip weekends (optional - depends on business rules)
                if (!isWeekend(current)) {
                    dates.add(current);
                }
                current = current.plusDays(1);
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "Parsed date range: %s to %s = %d business days", startDate, endDate, dates.size()));

            return dates;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error parsing date range: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Check if date is weekend
     */
    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek().getValue() >= 6; // Saturday = 6, Sunday = 7
    }

    /**
     * Validate time off type
     */
    private boolean isValidTimeOffType(String timeOffType) {
        List<String> validTypes = Arrays.asList("CO", "CM");
        return validTypes.contains(timeOffType);
    }

    /**
     * Get display name for time off type
     */
    private String getTimeOffTypeDisplayName(String timeOffType) {
        return switch (timeOffType) {
            case "CO" -> "Vacation";
            case "CM" -> "Medical Leave";
            case "SN" -> "National Holiday";
            default -> timeOffType;
        };
    }

    /**
     * Sanitize user data for display
     */
    private User sanitizeUserData(User user) {
        User sanitized = new User();
        sanitized.setUserId(user.getUserId());
        sanitized.setName(user.getName());
        sanitized.setUsername(user.getUsername());
        sanitized.setEmployeeId(user.getEmployeeId());
        sanitized.setSchedule(user.getSchedule());
        sanitized.setPaidHolidayDays(user.getPaidHolidayDays());
        return sanitized;
    }
}