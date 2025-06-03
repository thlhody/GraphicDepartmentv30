package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.TimeManagementService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.TimeOffCacheService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
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
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ENHANCED Unified Time Management Controller - Integrated with comprehensive TimeManagementService
 * Handles both page display and AJAX field updates with full transformation support
 */
@Controller
@RequestMapping("/user/time-management")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserTimeManagementController extends BaseController {

    private final TimeManagementService timeManagementService;
    private final TimeOffCacheService timeOffCacheService;

    public UserTimeManagementController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService validationService,
            TimeManagementService timeManagementService,
            TimeOffCacheService timeOffCacheService) {
        super(userService, folderStatus, validationService);
        this.timeManagementService = timeManagementService;
        this.timeOffCacheService = timeOffCacheService;
    }

    // ========================================================================
    // MAIN PAGE ENDPOINT
    // ========================================================================

    /**
     * Display unified time management page
     */
    @GetMapping
    public String getTimeManagementPage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Loading unified time management page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Determine year and month (preserve URL parameters)
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Validate period
            try {
                var validateCommand = getTimeValidationService().getValidationFactory()
                        .createValidatePeriodCommand(selectedYear, selectedMonth, 24);
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                String userMessage = "The selected period is not valid. You can only view periods up to 24 months in the future.";
                redirectAttributes.addFlashAttribute("periodError", userMessage);

                LocalDate currentDate = getStandardCurrentDate();
                return "redirect:/user/time-management?year=" + currentDate.getYear() + "&month=" + currentDate.getMonthValue();
            }

            // Load combined page data using enhanced service
            TimeManagementService.TimeManagementPageData pageData = timeManagementService.loadPageData(
                    currentUser.getUsername(), currentUser.getUserId(), selectedYear, selectedMonth);

            // Add all data to model
            model.addAttribute("user", sanitizeUserData(currentUser));
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);
            model.addAttribute("worktimeData", pageData.getWorktimeData());
            model.addAttribute("summary", pageData.getWorkTimeSummary());
            model.addAttribute("timeOffSummary", pageData.getTimeOffSummary());

            // Add date constraints for time off requests
            LocalDate today = getStandardCurrentDate();
            model.addAttribute("today", today.toString());
            model.addAttribute("minDate", today.minusDays(7).toString());
            model.addAttribute("maxDate", today.plusMonths(6).toString());

            // Add current system time
            model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded time management page for %s - %d/%d with %d worktime entries",
                    currentUser.getUsername(), selectedYear, selectedMonth, pageData.getWorktimeData().size()));

            return "user/time-management";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading time management page: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error loading time management data. Please try again.");
            return "redirect:/user/dashboard";
        }
    }

    // ========================================================================
    // ENHANCED AJAX FIELD UPDATE ENDPOINTS
    // ========================================================================

    /**
     * ENHANCED: Update individual field with comprehensive transformation logic
     */
    @PostMapping("/update-field")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateField(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String date,
            @RequestParam String field,
            @RequestParam(required = false) String value) {

        Map<String, Object> response = new HashMap<>();

        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Enhanced field update request: date=%s, field=%s, value=%s", date, field, value));

            // Get current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return createErrorResponse("Authentication required", HttpStatus.UNAUTHORIZED);
            }

            // Parse date
            LocalDate workDate;
            try {
                workDate = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                return createErrorResponse("Invalid date format: " + date, HttpStatus.BAD_REQUEST);
            }

            // Basic client-side validation
            String validationError = performBasicValidation(workDate, field, value);
            if (validationError != null) {
                return createErrorResponse(validationError, HttpStatus.BAD_REQUEST);
            }

            // Use enhanced service for comprehensive field update
            TimeManagementService.FieldUpdateResult result = timeManagementService.updateField(
                    currentUser.getUsername(), currentUser.getUserId(), workDate, field, value);

            if (result.isSuccess()) {
                // Successful update
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("field", field);
                response.put("date", date);
                response.put("value", value);

                // Add transformation details for frontend handling
                response.put("transformation", determineTransformationType(field, value));

                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully updated %s for %s on %s: %s",
                        field, currentUser.getUsername(), workDate, result.getMessage()));

                return ResponseEntity.ok(response);
            } else {
                // Failed update with detailed error from service
                return createErrorResponse(result.getMessage(), HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating field %s on %s: %s", field, date, e.getMessage()), e);
            return createErrorResponse("Internal error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * ENHANCED: Check if field can be edited with improved validation
     */
    @GetMapping("/can-edit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> canEditField(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String date,
            @RequestParam String field) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Get current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return createErrorResponse("Authentication required", HttpStatus.UNAUTHORIZED);
            }

            // Parse date
            LocalDate workDate;
            try {
                workDate = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                return createErrorResponse("Invalid date format: " + date, HttpStatus.BAD_REQUEST);
            }

            // Check edit permissions using enhanced service
            TimeManagementService.FieldEditValidationResult validation = timeManagementService.canEditField(
                    currentUser.getUsername(), currentUser.getUserId(), workDate, field);

            response.put("canEdit", validation.isCanEdit());
            response.put("reason", validation.getReason());
            response.put("currentStatus", validation.getCurrentStatus());
            response.put("field", field);
            response.put("date", date);

            // Add additional context for frontend
            response.put("isWeekend", workDate.getDayOfWeek().getValue() >= 6);
            response.put("isToday", workDate.equals(LocalDate.now()));
            response.put("isFuture", workDate.isAfter(LocalDate.now()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error checking edit permissions for %s on %s: %s", field, date, e.getMessage()));
            return createErrorResponse("Error checking permissions", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ========================================================================
    // TIME OFF REQUEST ENDPOINT (Enhanced with better validation)
    // ========================================================================

    /**
     * ENHANCED: Process time off request with comprehensive validation
     */
    @PostMapping("/timeoff-request")
    public String submitTimeOffRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String timeOffType,
            @RequestParam(required = false) boolean isSingleDayRequest,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Processing time off request: %s to %s (%s, single: %s)",
                    startDate, endDate, timeOffType, isSingleDayRequest));

            // Get user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "Authentication required");
                return "redirect:/login";
            }

            // Parse and validate dates
            List<LocalDate> dates = parseDateRange(startDate, endDate, isSingleDayRequest);
            if (dates.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Invalid date range");
                return getRedirectUrl(startDate);
            }

            // Enhanced validation for time off type
            if (!isValidTimeOffType(timeOffType)) {
                redirectAttributes.addFlashAttribute("error", "Invalid time off type");
                return getRedirectUrl(startDate);
            }

            // Pre-validate holiday balance for CO requests
            if ("CO".equals(timeOffType)) {
                int year = dates.get(0).getYear();
                boolean hasBalance = validateHolidayBalanceForRequest(currentUser.getUsername(),
                        currentUser.getUserId(), dates.size(), year);
                if (!hasBalance) {
                    redirectAttributes.addFlashAttribute("error",
                            String.format("Insufficient vacation balance for %d day(s). Check your available days.",
                                    dates.size()));
                    return getRedirectUrl(startDate);
                }
            }

            // Process request via cache service (uses comprehensive transformation)
            boolean success = timeOffCacheService.addTimeOffRequest(
                    currentUser.getUsername(), currentUser.getUserId(), dates, timeOffType);

            if (success) {
                String message = String.format("Successfully submitted time off request for %d day(s) (%s)",
                        dates.size(), getTimeOffTypeDisplayName(timeOffType));
                redirectAttributes.addFlashAttribute("successMessage", message);

                LoggerUtil.info(this.getClass(), String.format(
                        "Time off request processed successfully for %s: %d days",
                        currentUser.getUsername(), dates.size()));
            } else {
                redirectAttributes.addFlashAttribute("error",
                        "Failed to submit time off request. Please check your available balance and try again.");
                LoggerUtil.warn(this.getClass(), String.format(
                        "Time off request failed for %s: %d days",
                        currentUser.getUsername(), dates.size()));
            }

            return getRedirectUrl(startDate);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing time off request: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "An error occurred while processing your request. Please try again.");
            return "redirect:/user/time-management";
        }
    }

    // ========================================================================
    // ENHANCED VALIDATION AND HELPER METHODS
    // ========================================================================

    /**
     * ENHANCED: Basic validation for field updates
     */
    private String performBasicValidation(LocalDate date, String field, String value) {
        // Enhanced date validation
        LocalDate today = LocalDate.now();

        if (date.equals(today)) {
            return "Cannot edit current day - edit tomorrow";
        }

        if (date.isAfter(today)) {
            return "Cannot edit future dates";
        }

        // Weekend validation for time off
        if ("timeOff".equals(field) && value != null && !value.trim().isEmpty()) {
            if (date.getDayOfWeek().getValue() >= 6) {
                return "Cannot add time off on weekends";
            }
        }

        // Field-specific validation
        switch (field.toLowerCase()) {
            case "starttime", "endtime":
                if (value != null && !value.trim().isEmpty()) {
                    // Validate time format (HH:mm)
                    if (!value.matches("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                        return "Invalid time format. Use HH:mm (e.g., 09:00)";
                    }
                }
                break;

            case "timeoff":
                if (value != null && !value.trim().isEmpty()) {
                    String timeOffType = value.trim().toUpperCase();
                    if (!"CO".equals(timeOffType) && !"CM".equals(timeOffType)) {
                        return "Invalid time off type. Use CO or CM";
                    }
                }
                break;

            default:
                return "Unknown field type: " + field;
        }

        return null; // No validation errors
    }

    /**
     * ENHANCED: Validate holiday balance before time off request
     */
    private boolean validateHolidayBalanceForRequest(String username, Integer userId, int daysRequested, int year) {
        try {
            var summary = timeOffCacheService.getTimeOffSummary(username, userId, year);
            int availableBalance = summary.getAvailablePaidDays();

            LoggerUtil.debug(this.getClass(), String.format(
                    "Balance check for %s: %d requested, %d available", username, daysRequested, availableBalance));

            return availableBalance >= daysRequested;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error checking holiday balance for %s: %s", username, e.getMessage()));
            return false; // Fail safe - don't allow request if can't verify balance
        }
    }

    /**
     * Determine transformation type for frontend handling
     */
    private String determineTransformationType(String field, String value) {
        if ("timeOff".equals(field)) {
            if (value == null || value.trim().isEmpty()) {
                return "TIME_OFF_CLEARED";
            } else {
                return "TIME_OFF_SET";
            }
        } else if ("startTime".equals(field) || "endTime".equals(field)) {
            return "TIME_FIELD_UPDATE";
        }
        return "OTHER";
    }

    /**
     * Get redirect URL with month preservation
     */
    private String getRedirectUrl(String startDate) {
        try {
            LocalDate date = LocalDate.parse(startDate);
            return String.format("redirect:/user/time-management?year=%d&month=%d",
                    date.getYear(), date.getMonthValue());
        } catch (Exception e) {
            return "redirect:/user/time-management";
        }
    }

    /**
     * Create error response for AJAX calls
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Parse date range (enhanced with better error handling)
     */
    private List<LocalDate> parseDateRange(String startDate, String endDate, boolean isSingleDay) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = isSingleDay ? start : LocalDate.parse(endDate);

            if (start.isAfter(end)) {
                LoggerUtil.warn(this.getClass(), "Start date is after end date: " + startDate + " > " + endDate);
                return new java.util.ArrayList<>();
            }

            List<LocalDate> dates = new java.util.ArrayList<>();
            LocalDate current = start;

            while (!current.isAfter(end)) {
                // Skip weekends
                if (current.getDayOfWeek().getValue() < 6) {
                    dates.add(current);
                }
                current = current.plusDays(1);
            }

            LoggerUtil.debug(this.getClass(), String.format(
                    "Parsed date range: %s to %s = %d business days", startDate, endDate, dates.size()));

            return dates;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error parsing date range: " + e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Validate time off type
     */
    private boolean isValidTimeOffType(String timeOffType) {
        return "CO".equals(timeOffType) || "CM".equals(timeOffType);
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