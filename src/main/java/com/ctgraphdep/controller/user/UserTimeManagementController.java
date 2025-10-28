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
import com.ctgraphdep.validation.ValidationResult;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.display.WorktimeDisplayService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/user/time-management")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserTimeManagementController extends BaseController {

    private final WorktimeOperationService worktimeOperationService;
    private final WorktimeDisplayService worktimeDisplayService;
    private final UserWorktimeExcelExporter userWorktimeExcelExporter;

    public UserTimeManagementController(UserService userService, FolderStatus folderStatus, TimeValidationService validationService, WorktimeOperationService worktimeOperationService,
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
     * Display unified time management page using command system
     */
    @GetMapping
    public String getTimeManagementPage(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
                                        Model model, RedirectAttributes redirectAttributes) {

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

            // Load combined page data using new command system
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
    //  AJAX ENDPOINTS
    // ========================================================================

    @PostMapping("/update-field")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateField(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String date,
                                                           @RequestParam String field, @RequestParam(required = false) String value) {

        try {
            // 1. AUTHENTICATION VALIDATION
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return createErrorResponse("Authentication required", HttpStatus.UNAUTHORIZED);
            }

            // 2. DATE PARSING VALIDATION
            LocalDate workDate;
            try {
                workDate = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                return createErrorResponse("Invalid date format: " + date, HttpStatus.BAD_REQUEST);
            }

            // 3. VALIDATION CALL
            String existingTimeOffType = getExistingTimeOffTypeForDate(workDate, currentUser);

            ValidationResult validationResult = getTimeValidationService().validateUserFieldUpdate(workDate, field, value, currentUser,existingTimeOffType);
            if (validationResult.isInvalid()) {
                return createErrorResponse(validationResult.getErrorMessage(), HttpStatus.BAD_REQUEST);
            }

            // 4. EXECUTE UPDATE (service receives clean data)
            OperationResult result = executeFieldUpdate(currentUser, workDate, field, value);

            // 5. PROCESS RESULT using helper method
            return processUpdateResult(result, field, date, value);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating field %s on %s: %s", field, date, e.getMessage()), e);
            return createErrorResponse("Internal error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/can-edit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> canEditField(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String date, @RequestParam String field) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Get current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return createErrorResponse("Authentication required", HttpStatus.UNAUTHORIZED);
            }

            // Parse date
            LocalDate workDate = LocalDate.parse(date);

            // SINGLE VALIDATION CALL - All permission checking replaced with 1 line!
            String existingTimeOffType = getExistingTimeOffTypeForDate(workDate, currentUser);
            ValidationResult validationResult = getTimeValidationService().validateUserFieldUpdate(workDate, field, null, currentUser,existingTimeOffType);

            response.put("canEdit", validationResult.isValid());
            if (validationResult.isInvalid()) {
                response.put("reason", validationResult.getErrorMessage());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error checking edit permissions: %s", e.getMessage()), e);
            response.put("canEdit", false);
            response.put("reason", "Permission check failed");
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/time-off/add")
    public String addTimeOffRequest(@AuthenticationPrincipal UserDetails userDetails,
                                    @RequestParam String startDate,
                                    @RequestParam(required = false) String endDate,
                                    @RequestParam String timeOffType,
                                    @RequestParam(required = false, defaultValue = "false") Boolean singleDay,
                                    RedirectAttributes redirectAttributes) {

        LoggerUtil.info(this.getClass(), String.format(
                "=== TIME OFF REQUEST START === User: %s, StartDate: %s, EndDate: %s, Type: %s, SingleDay: %s",
                userDetails.getUsername(), startDate, endDate, timeOffType, singleDay));

        try {
            // Get current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                LoggerUtil.error(this.getClass(), "User not found during time off request");
                redirectAttributes.addFlashAttribute("error", "Authentication required");
                return "redirect:/login";
            }

            LoggerUtil.info(this.getClass(), String.format("User found: %s (ID: %d)", currentUser.getUsername(), currentUser.getUserId()));

            // PART 1: Light validation (weekends, basic rules only)
            LoggerUtil.info(this.getClass(), "=== PART 1: Light validation (weekends, date ranges) ===");
            ValidationResult validationResult = getTimeValidationService().validateTimeOffRequestLight(startDate, endDate, timeOffType, singleDay);

            LoggerUtil.info(this.getClass(), String.format("Light validation result: valid=%s, error=%s",
                    validationResult.isValid(), validationResult.getErrorMessage()));

            if (validationResult.isInvalid()) {
                LoggerUtil.warn(this.getClass(), "Light validation failed: " + validationResult.getErrorMessage());
                redirectAttributes.addFlashAttribute("error", validationResult.getErrorMessage());
                return getRedirectUrl(startDate);
            }

            LoggerUtil.info(this.getClass(), "Light validation passed, parsing dates for command...");

            // Parse validated dates (we know they're valid now)
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = singleDay ? start : LocalDate.parse(endDate);

            List<LocalDate> dates = start.datesUntil(end.plusDays(1))
                    .filter(date -> date.getDayOfWeek().getValue() < 6) // Skip weekends
                    .toList();

            LoggerUtil.info(this.getClass(), String.format("Parsed %d weekday dates for command processing", dates.size()));

            // PART 2: Execute command (which will do file-based conflict resolution)
            LoggerUtil.info(this.getClass(), "=== PART 2: Command execution with file-based conflict resolution ===");
            OperationResult result = worktimeOperationService.addUserTimeOff(currentUser.getUsername(), currentUser.getUserId(), dates, timeOffType.toUpperCase());
            LoggerUtil.info(this.getClass(), String.format("Command result: success=%s, message=%s", result.isSuccess(), result.getMessage()));

            // Process result
            if (result.isSuccess()) {
                String message = result.getMessage();
                if (result.getSideEffects() != null && result.getSideEffects().getOldHolidayBalance() != null) {
                    message += String.format(" Holiday balance: %d → %d", result.getSideEffects().getOldHolidayBalance(), result.getSideEffects().getNewHolidayBalance());
                }
                redirectAttributes.addFlashAttribute("successMessage", message);
                // NEW: Add flag to open holiday modal
                redirectAttributes.addFlashAttribute("openHolidayModal", true);
                redirectAttributes.addFlashAttribute("holidayStartDate", startDate);
                redirectAttributes.addFlashAttribute("holidayEndDate", endDate);
                redirectAttributes.addFlashAttribute("holidayTimeOffType", timeOffType.toUpperCase());

                LoggerUtil.info(this.getClass(), String.format("Time off request processed successfully: %s", result.getMessage()));
            } else {
                redirectAttributes.addFlashAttribute("error", result.getMessage());
                LoggerUtil.warn(this.getClass(), String.format("Time off request failed: %s", result.getMessage()));
            }

            return getRedirectUrl(startDate);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Exception in addTimeOffRequest: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to process time off request: " + e.getMessage());
            return getRedirectUrl(startDate);
        }
    }


    /**
     * Get time management content fragment for embedded display
     * This endpoint returns just the content without the layout wrapper
     */
    @GetMapping("/fragment")
    public String getTimeManagementFragment(@AuthenticationPrincipal UserDetails userDetails,
                                            @RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) Integer month,
                                            Model model) {

        try {
            LoggerUtil.info(this.getClass(), "Loading time management fragment at " + getStandardCurrentDateTime());

            // Get user and add common model attributes
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                model.addAttribute("error", "Authentication required");
                return "fragments/time-management-fragment";
            }

            // Determine year and month (preserve URL parameters)
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            try {
                var validateCommand = getTimeValidationService().getValidationFactory()
                        .createValidatePeriodCommand(selectedYear, selectedMonth, 24);
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                model.addAttribute("error", "The selected period is not valid. You can only view periods up to 24 months in the future.");
                return "fragments/time-management-fragment";
            }

            // Load combined page data using existing service
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
                    "Successfully loaded time management fragment for %s - %d/%d",
                    currentUser.getUsername(), selectedYear, selectedMonth));

            return "user/fragments/time-management-fragment";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading time management fragment: " + e.getMessage(), e);
            model.addAttribute("error", "Error loading time management data: " + e.getMessage());
            return "user/fragments/time-management-fragment";
        }
    }

    // ========================================================================
    // EXCEL EXPORT ENDPOINT (UNCHANGED)
    // ========================================================================

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month) {

        try {
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            List<WorkTimeTable> worktimeData = worktimeOperationService.loadUserWorktime(
                    currentUser.getUsername(), selectedYear, selectedMonth);

            LoggerUtil.info(this.getClass(), String.format("Exporting worktime data for %s (%d/%d). Total entries: %d", currentUser.getUsername(), selectedMonth, selectedYear, worktimeData.size()));

            Map<String, Object> displayData = worktimeDisplayService.prepareWorktimeDisplayData(currentUser, worktimeData, selectedYear, selectedMonth);

            @SuppressWarnings("unchecked")
            List<WorkTimeEntryDTO> entryDTOs = (List<WorkTimeEntryDTO>) displayData.get("worktimeData");
            WorkTimeSummaryDTO summaryDTO = (WorkTimeSummaryDTO) displayData.get("summary");

            byte[] excelData = userWorktimeExcelExporter.exportToExcel(currentUser, entryDTOs, summaryDTO, selectedYear, selectedMonth);

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"worktime_%s_%d_%02d.xlsx\"",
                            currentUser.getUsername(), selectedYear, selectedMonth))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    //  CLEAN HELPER METHODS
    // ========================================================================

    // Execute field update using appropriate command (business logic only)
    private OperationResult executeFieldUpdate(User currentUser, LocalDate workDate, String field, String value) {
        String username = currentUser.getUsername();
        Integer userId = currentUser.getUserId();

        LoggerUtil.info(this.getClass(),
                " DEBUG executeFieldUpdate:)" +
                         "  - Original field: '" + field + "'" +
                         "  - Field length: " + field.length() +
                         "  - Lowercase field: '" + field.toLowerCase() + "'" +
                         "  - Equals 'tempstop': " + "tempstop".equalsIgnoreCase(field));

        return switch (field.toLowerCase()) {
            case "starttime" -> worktimeOperationService.updateUserStartTime(username, userId, workDate, value);
            case "endtime" -> worktimeOperationService.updateUserEndTime(username, userId, workDate, value);
            case "tempstop" -> {
                if (value == null || value.trim().isEmpty() || "0".equals(value.trim())) {
                    // Remove temporary stop
                    yield worktimeOperationService.removeUserTemporaryStop(username, userId, workDate);
                } else {
                    // Add/update temporary stop
                    try {
                        Integer tempStopMinutes = Integer.parseInt(value.trim());
                        yield worktimeOperationService.updateUserTemporaryStop(username, userId, workDate, tempStopMinutes);
                    } catch (NumberFormatException e) {
                        yield OperationResult.failure("Invalid temporary stop value: " + value, "FIELD_UPDATE");
                    }
                }
            }
            case "timeoff" -> {
                if (value == null || value.trim().isEmpty()) {
                    // REMOVAL: Use existing service method
                    yield worktimeOperationService.removeUserTimeOff(username, userId, workDate);
                } else {
                    // ADDITION: Block direct addition
                    yield OperationResult.failure("Use the Time Off Request form to add time off", "FIELD_UPDATE");
                }
            }
            default -> OperationResult.failure("Unknown field type: " + field, "FIELD_UPDATE");
        };
    }

    // Process update result and create appropriate response
    private ResponseEntity<Map<String, Object>> processUpdateResult(OperationResult result, String field, String date, String value) {
        Map<String, Object> response = new HashMap<>();

        if (result.isSuccess()) {
            response.put("success", true);
            response.put("message", result.getMessage());

            // Add side effects info if present
            if (result.getSideEffects() != null) {
                if (result.getSideEffects().getOldHolidayBalance() != null) {
                    response.put("holidayBalanceChange", String.format("%d → %d",
                            result.getSideEffects().getOldHolidayBalance(),
                            result.getSideEffects().getNewHolidayBalance()));
                }
            }

            LoggerUtil.info(this.getClass(), String.format(
                    "Field update successful: %s=%s on %s", field, value, date));
        } else {
            response.put("success", false);
            response.put("message", result.getMessage());
            LoggerUtil.warn(this.getClass(), String.format(
                    "Field update failed: %s=%s on %s - %s", field, value, date, result.getMessage()));
        }

        return ResponseEntity.ok(response);
    }

    // Get redirect URL with month preservation
    private String getRedirectUrl(String startDate) {
        try {
            LocalDate date = LocalDate.parse(startDate);
            return String.format("redirect:/user/time-management?year=%d&month=%d",
                    date.getYear(), date.getMonthValue());
        } catch (Exception e) {
            // IMPROVED: Try to preserve current URL parameters instead of losing them
            LoggerUtil.warn(this.getClass(), "Failed to parse startDate: " + startDate + ", using current month");
            LocalDate now = getStandardCurrentDate();
            return String.format("redirect:/user/time-management?year=%d&month=%d",
                    now.getYear(), now.getMonthValue());
        }
    }

    // Create error response helper
    private ResponseEntity<Map<String, Object>> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);  // Frontend reads this field
        response.put("error", message);    // Fallback field name
        return ResponseEntity.status(status).body(response);
    }

    // Sanitize user data for display
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

    // Add this method to UserTimeManagementController
    private String getExistingTimeOffTypeForDate(LocalDate date, User user) {
        try {
            // Load worktime data for the month containing this date
            List<WorkTimeTable> entries = worktimeOperationService.loadUserWorktime(user.getUsername(), date.getYear(), date.getMonthValue());

            // Find entry for the specific date and user
            return entries.stream()
                    .filter(entry -> entry.getUserId().equals(user.getUserId()) &&
                            entry.getWorkDate().equals(date))
                    .map(WorkTimeTable::getTimeOffType)
                    .findFirst()
                    .orElse(null); // null means no time off exists for this date
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), String.format(
                    "Error getting existing time off type for %s on %s: %s",
                    user.getUsername(), date, e.getMessage()));
            return null; // Safe fallback - assume no existing time off
        }
    }
}