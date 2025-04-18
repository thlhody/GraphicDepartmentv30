package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.AdminPaidHolidayService;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.TimeOffTrackerService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.UserTimeOffService;
import com.ctgraphdep.utils.LoggerUtil;
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
import java.util.List;

@Controller
@RequestMapping("/user/timeoff")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserTimeOffController extends BaseController {

    private final UserTimeOffService timeOffService;
    private final AdminPaidHolidayService holidayService;
    private final TimeOffRequestValidator timeOffValidator;
    private final TimeOffTrackerService timeOffTrackerService;

    public UserTimeOffController(
            UserService userService,
            FolderStatus folderStatus,
            UserTimeOffService timeOffService,
            AdminPaidHolidayService holidayService,
            TimeValidationService timeValidationService,
            TimeOffRequestValidator timeOffValidator, TimeOffTrackerService timeOffTrackerService) {
        super(userService, folderStatus, timeValidationService);
        this.timeOffService = timeOffService;
        this.holidayService = holidayService;
        this.timeOffValidator = timeOffValidator;
        this.timeOffTrackerService = timeOffTrackerService;
    }

    @GetMapping
    public String showTimeOffPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        try {
            LoggerUtil.info(this.getClass(), "Accessing time off page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes with one method call
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }
            // Ensure tracker exists and is up to date
            timeOffTrackerService.ensureTrackerExists(currentUser, currentUser.getUserId(), getStandardCurrentDate().getYear());
            // Trigger a sync of the time off tracker to ensure it's up to date
            // This ensures the user sees the most accurate data including future months
            timeOffService.syncTimeOffTracker(currentUser, getStandardCurrentDate().getYear());

            // Prepare time off data and add to model
            prepareTimeOffPageModel(model, currentUser);
            return "user/timeoff";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading time off page: " + e.getMessage(), e);
            model.addAttribute("errorMessage", "Error loading time off data");
            return "user/timeoff";
        }
    }

    @PostMapping
    public String processTimeOffRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String timeOffType,
            @RequestParam(defaultValue = "false") boolean isSingleDayRequest,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Processing time off request at " + getStandardCurrentDateTime());

            // Get the user - we only need to get the user here, not add model attributes
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Basic validation before going to the validator
            if (startDate == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Start date is required");
                return "redirect:/user/timeoff?error=date_required";
            }

            // Handle single day request
            if (isSingleDayRequest) {
                endDate = startDate;
            } else if (endDate == null) {
                // Additional null check for endDate when not a single day request
                redirectAttributes.addFlashAttribute("errorMessage", "End date is required");
                return "redirect:/user/timeoff?error=date_required";
            }

            // Get available paid days for validation
            int availableDays = holidayService.getRemainingHolidayDays(currentUser.getUserId());

            // Validate the time-off request
            TimeOffRequestValidator.ValidationResult validationResult = timeOffValidator.validateRequest(startDate, endDate, timeOffType, availableDays);

            if (!validationResult.isValid()) {
                redirectAttributes.addFlashAttribute("errorMessage", validationResult.getErrorMessage());
                return "redirect:/user/timeoff?error=validation_failed";
            }

            // Process the validated request
            timeOffService.processTimeOffRequest(currentUser, startDate, endDate, timeOffType);

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

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                LoggerUtil.error(this.getClass(), "Unauthorized access to upcoming time off data");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get all upcoming time off entries including future months
            List<WorkTimeTable> upcomingTimeOff = timeOffService.getUpcomingTimeOff(currentUser);
            return ResponseEntity.ok(upcomingTimeOff);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting upcoming time off: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create a success message for the time off request
     */
    private String createSuccessMessage(String timeOffType, LocalDate startDate, LocalDate endDate, int daysCount) {
        String dateInfo = startDate.equals(endDate) ? startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) :
                String.format("%s to %s", startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        String typeLabel = "CO".equals(timeOffType) ? "vacation" : "medical leave";

        return String.format("Successfully requested %s for %s (%d working day%s)", typeLabel, dateInfo, daysCount, daysCount > 1 ? "s" : "");
    }

    /**
     * Prepare the model for the time off page
     */
    private void prepareTimeOffPageModel(Model model, User user) {
        // Use standardized dates from BaseController
        LocalDate currentDate = getStandardCurrentDate();
        LocalDate twoMonthsAgo = currentDate.minusMonths(2).withDayOfMonth(1);
        LocalDate maxDate = currentDate.plusMonths(6);
        // Get current year for time off summary
        int currentYear = currentDate.getYear();

        // Get available paid days
        int availablePaidDays = timeOffTrackerService.loadTimeOffTracker(user.getUsername(),user.getUserId(),currentYear).getAvailableHolidayDays();

        // Get time off summary for the current year (using read-only method for better performance)
        // This now uses the tracker service which shows time-off across all months
        TimeOffSummaryDTO summary = timeOffService.calculateTimeOffSummaryReadOnly(user.getUsername(), currentYear);

        // Update with the correct available days from the holiday service
        summary.setAvailablePaidDays(availablePaidDays);
        summary.setRemainingPaidDays(Math.max(0, availablePaidDays - summary.getPaidDaysTaken()));

        // Get the user's upcoming time off requests - includes future months
        List<WorkTimeTable> upcomingTimeOff = timeOffService.getUpcomingTimeOff(user);

        // Add everything to the model
        model.addAttribute("user", user);
        model.addAttribute("summary", summary);
        model.addAttribute("maxDate", maxDate);
        model.addAttribute("minDate", twoMonthsAgo.format(DateTimeFormatter.ISO_DATE));
        model.addAttribute("today", currentDate.format(DateTimeFormatter.ISO_DATE));
        model.addAttribute("upcomingTimeOff", upcomingTimeOff);
    }
}