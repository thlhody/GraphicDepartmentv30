package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.WorktimeManagementService;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.WorkTimeEntryUtil;
import com.ctgraphdep.validation.TimeOffRequestValidator;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.List;

@Controller
@RequestMapping("/user/timeoff")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserTimeOffController extends BaseController {

    private final TimeOffCacheService timeOffCacheService;
    private final TimeOffRequestValidator timeOffValidator;
    private final WorktimeManagementService worktimeManagementService;

    public UserTimeOffController(UserService userService, FolderStatus folderStatus, TimeOffCacheService timeOffCacheService,
                                 TimeValidationService timeValidationService, TimeOffRequestValidator timeOffValidator, WorktimeManagementService worktimeManagementService) {
        super(userService, folderStatus, timeValidationService);
        this.timeOffCacheService = timeOffCacheService;
        this.timeOffValidator = timeOffValidator;
        this.worktimeManagementService = worktimeManagementService;
    }

    @GetMapping
    public String showTimeOffPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        try {
            LoggerUtil.info(this.getClass(), "Accessing time off page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Load time off data from cache (this triggers worktime merge if needed)
            int currentYear = getStandardCurrentDate().getYear();
            timeOffCacheService.getYearTracker(currentUser.getUsername(), currentUser.getUserId(), currentYear);

            // Prepare time off page model
            prepareTimeOffPageModel(model, currentUser, currentYear);
            return "user/timeoff";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading time off page: " + e.getMessage(), e);
            model.addAttribute("errorMessage", "Error loading time off data");
            return "user/timeoff";
        }
    }

    @PostMapping
    public String processTimeOffRequest(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate, @RequestParam(required = false) String timeOffType,
                                        @RequestParam(defaultValue = "false") boolean isSingleDayRequest, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Processing time off request at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Basic validation
            if (startDate == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Start date is required");
                return "redirect:/user/timeoff?error=date_required";
            }

            // Handle single day request
            if (isSingleDayRequest) {
                endDate = startDate;
            } else if (endDate == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "End date is required");
                return "redirect:/user/timeoff?error=date_required";
            }

            // Get available days for validation from cache
            TimeOffSummaryDTO summary = timeOffCacheService.getTimeOffSummary(currentUser.getUsername(), currentUser.getUserId(), startDate.getYear());
            int availableDays = summary.getRemainingPaidDays();

            // Validate the time-off request
            TimeOffRequestValidator.ValidationResult validationResult = timeOffValidator.validateRequest(startDate, endDate, timeOffType, availableDays);

            if (!validationResult.isValid()) {
                redirectAttributes.addFlashAttribute("errorMessage", validationResult.getErrorMessage());
                return "redirect:/user/timeoff?error=validation_failed";
            }

            List<LocalDate> validDates = calculateValidWorkDays(startDate, endDate);

            // Process the validated request using cache service
            boolean success = timeOffCacheService.addTimeOffRequest(currentUser.getUsername(), currentUser.getUserId(), validDates, timeOffType);

            if (!success) {
                redirectAttributes.addFlashAttribute("errorMessage", "Failed to process time off request");
                return "redirect:/user/timeoff?error=submit_failed";
            }

            // Create success message and redirect
            String successMessage = createSuccessMessage(timeOffType, startDate, endDate, validationResult.getEligibleDays());
            redirectAttributes.addFlashAttribute("successMessage", successMessage);

            LoggerUtil.info(this.getClass(), String.format("Time off request processed successfully for user %s (%s to %s)", currentUser.getUsername(), startDate, endDate));

            return "redirect:/user/timeoff";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing time off request: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to process time off request: " + e.getMessage());
            return "redirect:/user/timeoff?error=submit_failed";
        }
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<WorkTimeTable>> getUpcomingTimeOff(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            LoggerUtil.info(this.getClass(), "Fetching upcoming time off at " + getStandardCurrentDateTime());

            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                LoggerUtil.error(this.getClass(), "Unauthorized access to upcoming time off data");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get from cache (instant!)
            List<WorkTimeTable> upcomingTimeOff = timeOffCacheService.getUpcomingTimeOff(currentUser.getUsername(), currentUser.getUserId(), getStandardCurrentDate().getYear());

            return ResponseEntity.ok(upcomingTimeOff);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting upcoming time off: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/cache/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> clearCache(@RequestParam String username, @RequestParam int year) {
        try {
            timeOffCacheService.clearYear(username, year);
            return ResponseEntity.ok("Cache cleared for " + username + " - " + year);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error clearing cache: " + e.getMessage());
        }
    }

    /**
     * Prepare the model for the time off page using cache service
     */
    private void prepareTimeOffPageModel(Model model, User user, int currentYear) {
        LocalDate currentDate = getStandardCurrentDate();
        LocalDate twoMonthsAgo = currentDate.minusMonths(2).withDayOfMonth(1);
        LocalDate maxDate = currentDate.plusMonths(6);

        // Get data from cache (all instant reads!)
        TimeOffSummaryDTO summary = timeOffCacheService.getTimeOffSummary(user.getUsername(), user.getUserId(), currentYear);
        List<WorkTimeTable> upcomingTimeOff = timeOffCacheService.getUpcomingTimeOff(user.getUsername(), user.getUserId(), currentYear);

        // Add to model
        model.addAttribute("user", user);
        model.addAttribute("summary", summary);
        model.addAttribute("maxDate", maxDate);
        model.addAttribute("minDate", twoMonthsAgo.format(DateTimeFormatter.ISO_DATE));
        model.addAttribute("today", currentDate.format(DateTimeFormatter.ISO_DATE));
        model.addAttribute("upcomingTimeOff", upcomingTimeOff);

        LoggerUtil.debug(this.getClass(), String.format("Model prepared from cache: %d upcoming days, %d CO days taken",
                upcomingTimeOff.size(), summary.getCoDays()));
    }

    /**
     * Calculate valid work days (same logic as before)
     */
    private List<LocalDate> calculateValidWorkDays(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> validDays = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            // Skip weekends and holidays (reuse existing logic)
            if (!isWeekend(current) && !isHoliday(current)) {
                validDays.add(current);
            }
            current = current.plusDays(1);
        }

        return validDays;
    }

    private boolean isWeekend(LocalDate date) {
        return WorkTimeEntryUtil.isDateWeekend(date); // Use existing utility
    }

    private boolean isHoliday(LocalDate date) {
        return !worktimeManagementService.isNotHoliday(date); // Use existing service
    }

    /**
     * Create success message
     */
    private String createSuccessMessage(String timeOffType, LocalDate startDate, LocalDate endDate, int daysCount) {
        String dateInfo = startDate.equals(endDate) ? startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) :
                String.format("%s to %s", startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        String typeLabel = "CO".equals(timeOffType) ? "vacation" : "medical leave";

        return String.format("Successfully requested %s for %s (%d working day%s)", typeLabel, dateInfo, daysCount, daysCount > 1 ? "s" : "");
    }
}