package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserWorktimeExcelExporter;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.display.WorktimeDisplayService;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REFACTORED Unified Time Management Controller using the new Command System.
 * Key Changes:
 * - Replaced TimeManagementService with WorktimeOperationService
 * - Simplified field update logic using commands
 * - Uses OperationResult for standardized error handling
 * - Removed complex transformation logic (handled by commands)
 * - Better separation of concerns
 */
@Controller
@RequestMapping("/user/time-management")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserTimeManagementController extends BaseController {

    private final WorktimeOperationService worktimeOperationService;
    private final WorktimeDisplayService worktimeDisplayService;
    private final UserWorktimeExcelExporter userWorktimeExcelExporter;

    public UserTimeManagementController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService validationService,
            WorktimeOperationService worktimeOperationService,
            WorktimeDisplayService worktimeDisplayService, UserWorktimeExcelExporter userWorktimeExcelExporter) {
        super(userService, folderStatus, validationService);
        this.worktimeOperationService = worktimeOperationService;
        this.worktimeDisplayService = worktimeDisplayService;
        this.userWorktimeExcelExporter = userWorktimeExcelExporter;
    }

    // ========================================================================
    // MAIN PAGE ENDPOINT
    // ========================================================================

    /**
     * REFACTORED: Display unified time management page using command system
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

            // REFACTORED: Load combined page data using new command system
            Map<String, Object> combinedData = worktimeDisplayService.prepareCombinedDisplayData(
                    currentUser, selectedYear, selectedMonth);

            // Add all data to model
            model.addAllAttributes(combinedData);
            model.addAttribute("user", sanitizeUserData(currentUser));
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);

            // Add date constraints for time off requests
            LocalDate today = getStandardCurrentDate();
            model.addAttribute("today", today.toString());
            model.addAttribute("minDate", today.minusDays(7).toString());
            model.addAttribute("maxDate", today.plusMonths(6).toString());

            // Add current system time
            model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded time management page for %s - %d/%d",
                    currentUser.getUsername(), selectedYear, selectedMonth));

            return "user/time-management";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading time management page: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error loading time management data. Please try again.");
            return "redirect:/user/dashboard";
        }
    }

    // ========================================================================
    // REFACTORED AJAX FIELD UPDATE ENDPOINTS
    // ========================================================================

    /**
     * REFACTORED: Simplified field update using command system
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
                    "Field update request: date=%s, field=%s, value=%s", date, field, value));

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

            // REFACTORED: Use command system for field updates
            OperationResult result = executeFieldUpdate(currentUser, workDate, field, value);

            if (result.isSuccess()) {
                // Successful update
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("field", field);
                response.put("date", date);
                response.put("value", value);

                // Add side effects information for frontend
                if (result.hasSideEffects()) {
                    Map<String, Object> sideEffects = getStringObjectMap(result);

                    response.put("sideEffects", sideEffects);
                }

                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully updated %s for %s on %s: %s",
                        field, currentUser.getUsername(), workDate, result.getMessage()));

                return ResponseEntity.ok(response);
            } else {
                // Failed update with detailed error from command system
                return createErrorResponse(result.getMessage(), HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error updating field %s on %s: %s", field, date, e.getMessage()), e);
            return createErrorResponse("Internal error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static @NotNull Map<String, Object> getStringObjectMap(OperationResult result) {
        Map<String, Object> sideEffects = new HashMap<>();

        if (result.getSideEffects().isHolidayBalanceChanged()) {
            sideEffects.put("holidayBalanceChanged", true);
            sideEffects.put("oldBalance", result.getSideEffects().getOldHolidayBalance());
            sideEffects.put("newBalance", result.getSideEffects().getNewHolidayBalance());
        }

        if (result.getSideEffects().isCacheInvalidated()) {
            sideEffects.put("cacheInvalidated", true);
        }
        return sideEffects;
    }

    /**
     * REFACTORED: Check edit permissions using command system
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

            // REFACTORED: Use command system for edit validation
            boolean canEdit = worktimeOperationService.canUserEditField(
                    currentUser.getUsername(), currentUser.getUserId(), workDate, field);

            response.put("canEdit", canEdit);
            response.put("field", field);
            response.put("date", date);

            // Add additional context for frontend
            response.put("isWeekend", workDate.getDayOfWeek().getValue() >= 6);
            response.put("isToday", workDate.equals(LocalDate.now()));
            response.put("isFuture", workDate.isAfter(LocalDate.now()));

            if (!canEdit) {
                response.put("reason", "Field cannot be edited for this date");
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error checking edit permissions for %s on %s: %s", field, date, e.getMessage()));
            return createErrorResponse("Error checking permissions", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ========================================================================
    // REFACTORED TIME OFF REQUEST ENDPOINT
    // ========================================================================

    /**
     * REFACTORED: Process time off request using command system
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

            // REFACTORED: Use command system for time off requests
            LoggerUtil.info(this.getClass(), String.format(
                    "Submitting time off request for %s: %d days (%s)",
                    currentUser.getUsername(), dates.size(), timeOffType));

            OperationResult result = worktimeOperationService.addUserTimeOff(
                    currentUser.getUsername(), currentUser.getUserId(), dates, timeOffType);

            if (result.isSuccess()) {
                String message = String.format("Successfully submitted time off request for %d day(s) (%s)",
                        dates.size(), getTimeOffTypeDisplayName(timeOffType));

                // Add side effects information if holiday balance changed
                if (result.hasSideEffects() && result.getSideEffects().isHolidayBalanceChanged()) {
                    message += String.format(". Holiday balance: %d → %d",
                            result.getSideEffects().getOldHolidayBalance(),
                            result.getSideEffects().getNewHolidayBalance());
                }

                redirectAttributes.addFlashAttribute("successMessage", message);

                LoggerUtil.info(this.getClass(), String.format(
                        "Time off request processed successfully: %s", result.getMessage()));
            } else {
                redirectAttributes.addFlashAttribute("error", result.getMessage());
                LoggerUtil.warn(this.getClass(), String.format(
                        "Time off request failed: %s", result.getMessage()));
            }

            return getRedirectUrl(startDate);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing time off request: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "An error occurred while processing your request. Please try again.");
            return "redirect:/user/time-management";
        }
    }

    //needs fixing

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        try {
            LoggerUtil.info(this.getClass(), "Exporting worktime data at " + getStandardCurrentDateTime());

            // Get the user - don't need to add model attributes for API endpoints
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                LoggerUtil.error(this.getClass(), "Unauthorized access attempt to export worktime data");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Get worktime data using WorktimeManagementService for consistency
            List<WorkTimeTable> worktimeData = worktimeOperationService.loadUserWorktime(
                    currentUser.getUsername(), selectedYear, selectedMonth);

            // Log the data details
            LoggerUtil.info(this.getClass(), String.format("Exporting worktime data for %s (%d/%d). Total entries: %d",
                    currentUser.getUsername(), selectedMonth, selectedYear, worktimeData.size()));

            // Get display data which includes the summary with DTOs
            Map<String, Object> displayData = worktimeDisplayService.prepareWorktimeDisplayData(
                    currentUser, worktimeData, selectedYear, selectedMonth);

            // Extract DTO's for export in Excel
            @SuppressWarnings("unchecked")
            List<WorkTimeEntryDTO> entryDTOs = (List<WorkTimeEntryDTO>) displayData.get("worktimeData");
            WorkTimeSummaryDTO summaryDTO = (WorkTimeSummaryDTO) displayData.get("summary");

            // Pass DTOs to the updated Excel exporter
            byte[] excelData = userWorktimeExcelExporter.exportToExcel(currentUser, entryDTOs, summaryDTO, selectedYear, selectedMonth);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"worktime_%s_%d_%02d.xlsx\"",
                            currentUser.getUsername(), selectedYear, selectedMonth))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }


    // ========================================================================
    // REFACTORED HELPER METHODS
    // ========================================================================

    /**
     * REFACTORED: Execute field update using appropriate command
     */
    private OperationResult executeFieldUpdate(User currentUser, LocalDate workDate, String field, String value) {
        String username = currentUser.getUsername();
        Integer userId = currentUser.getUserId();

        return switch (field.toLowerCase()) {
            case "starttime" -> worktimeOperationService.updateUserStartTime(username, userId, workDate, value);
            case "endtime" -> worktimeOperationService.updateUserEndTime(username, userId, workDate, value);
            case "timeoff" -> {
                if (value == null || value.trim().isEmpty()) {
                    // Remove time off
                    yield worktimeOperationService.removeUserTimeOff(username, userId, workDate);
                } else {
                    // Transform to time off or add time off
                    yield worktimeOperationService.transformWorkToTimeOff(username, userId, workDate, value.trim().toUpperCase());
                }
            }
            default -> OperationResult.failure("Unknown field type: " + field, "FIELD_UPDATE");
        };
    }

    /**
     * Enhanced validation for field updates
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
                return new ArrayList<>();
            }

            List<LocalDate> dates = new ArrayList<>();
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
            return new ArrayList<>();
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