package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.bonus.BonusCalculationResultDTO;
import com.ctgraphdep.model.dto.RegisterSummaryDTO;
import com.ctgraphdep.register.service.AdminRegisterService;
import com.ctgraphdep.service.*;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepTypes;
import com.ctgraphdep.utils.AdminRegisterExcelExporter;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REFACTORED AdminRegisterController - Thin HTTP layer.
 * Key Changes:
 * - Removed all data conversion logic (moved to service)
 * - Removed duplicate user operations (consolidated in service)
 * - Simplified all endpoints to just call service and handle response
 * - Added consistent error handling helper method
 * - Controller now focuses purely on HTTP concerns
 * - Service handles all business logic, validation, and data conversion
 */
@Controller
@RequestMapping("/admin/register")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminRegisterController extends BaseController {

    private final AdminRegisterService adminRegisterService;
    private final WorktimeOperationService worktimeOperationService;
    private final AdminRegisterExcelExporter adminRegisterExcelExporter;

    @Autowired
    public AdminRegisterController(UserService userService, FolderStatus folderStatus, TimeValidationService timeValidationService,
                                   AdminRegisterService adminRegisterService, WorktimeOperationService worktimeOperationService, AdminRegisterExcelExporter adminRegisterExcelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.adminRegisterService = adminRegisterService;
        this.worktimeOperationService = worktimeOperationService;
        this.adminRegisterExcelExporter = adminRegisterExcelExporter;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Display admin register page - SIMPLIFIED to use service layer
     */
    @GetMapping
    public String getRegisterPage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing admin register page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Use checkUserAccess from BaseController for consistent access control
            String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (accessCheck != null) {
                return accessCheck;
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Add users list for dropdown
            List<User> allUsers = getUserService().getAllUsers();
            List<User> users = getUserService().getNonAdminUsers(allUsers);
            model.addAttribute("users", users);

            // Add action types and print prep types for filters
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepTypes.getValues());

            // Add period info
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);

            // Add bonus configuration defaults
            model.addAttribute("bonusConfig", BonusConfiguration.getDefaultConfig());

            // If userId is provided, load entries using service
            if (userId != null) {
                ServiceResult<User> userResult = adminRegisterService.getUserById(userId);
                if (userResult.isFailure()) {
                    String errorMessage = formatServiceError(userResult, "loading user details");
                    model.addAttribute("errorMessage", errorMessage);
                    model.addAttribute("entries", new ArrayList<>());
                    return "admin/register";
                }

                User selectedUser = userResult.getData();

                // Load register entries using service
                ServiceResult<List<RegisterEntry>> entriesResult = adminRegisterService.loadUserRegisterEntries(
                        selectedUser.getUsername(), userId, selectedYear, selectedMonth);

                if (entriesResult.isSuccess()) {
                    List<RegisterEntry> entries = entriesResult.getData();

                    // Get worked days from worktime service
                    int workedDays = worktimeOperationService.getWorkedDays(userId, selectedYear, selectedMonth);

                    // Add to model
                    model.addAttribute("entries", entries);
                    model.addAttribute("selectedUser", selectedUser);
                    model.addAttribute("workedDays", workedDays);

                    // Handle warnings if any
                    if (entriesResult.hasWarnings()) {
                        model.addAttribute("warningMessage",
                                "Data loaded with warnings: " + String.join(", ", entriesResult.getWarnings()));
                    }

                    LoggerUtil.info(this.getClass(), String.format(
                            "Loaded %d entries and %d worked days for user %s",
                            entries.size(), workedDays, selectedUser.getUsername()));
                } else {
                    // Handle error but provide fallback data
                    String errorMessage = formatServiceError(entriesResult, "loading register entries");
                    model.addAttribute("errorMessage", errorMessage);
                    model.addAttribute("entries", new ArrayList<>());

                    LoggerUtil.error(this.getClass(), String.format(
                            "Failed to load entries for user %d: %s", userId, entriesResult.getErrorMessage()));
                }
            } else {
                LoggerUtil.info(this.getClass(), "No user selected, adding empty entries list");
                model.addAttribute("entries", new ArrayList<>());
            }

            return "admin/register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error loading admin register page: " + e.getMessage(), e);

            // Set error attributes while preserving basic functionality
            model.addAttribute("errorMessage", "An unexpected error occurred while loading register data");
            model.addAttribute("entries", new ArrayList<>());
            model.addAttribute("users", new ArrayList<>());
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepTypes.getValues());
            model.addAttribute("currentYear", year != null ? year : getStandardCurrentDate().getYear());
            model.addAttribute("currentMonth", month != null ? month : getStandardCurrentDate().getMonthValue());
            model.addAttribute("bonusConfig", BonusConfiguration.getDefaultConfig());

            return "admin/register";
        }
    }

    /**
     * Get worked days - SIMPLIFIED
     */
    @GetMapping("/worked-days")
    public ResponseEntity<Integer> getWorkedDays(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            int workedDays = worktimeOperationService.getWorkedDays(userId, year, month);
            LoggerUtil.info(this.getClass(), String.format("Retrieved %d worked days for user %d", workedDays, userId));
            return ResponseEntity.ok(workedDays);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting worked days for user %d: %s", userId, e.getMessage()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Save entries - SIMPLIFIED to delegate to service
     */
    @PostMapping("/save")
    public ResponseEntity<?> saveEntries(@AuthenticationPrincipal UserDetails userDetails, @RequestBody Map<String, Object> request) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
            }

            LoggerUtil.info(this.getClass(), "Processing admin register save request");

            // Delegate everything to service
            ServiceResult<Void> saveResult = adminRegisterService.saveEntriesFromRequest(request);

            return handleServiceResultResponse(saveResult, "saving entries");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error saving entries: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error occurred while saving entries");
        }
    }

    /**
     * Calculate bonus - SIMPLIFIED to delegate to service
     */
    @PostMapping("/calculate-bonus")
    public ResponseEntity<BonusCalculationResultDTO> calculateBonus(@AuthenticationPrincipal UserDetails userDetails, @RequestBody Map<String, Object> request) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            LoggerUtil.info(this.getClass(), "Processing bonus calculation request");

            // Delegate everything to service (service handles parsing, calculation, and saving)
            ServiceResult<BonusCalculationResultDTO> calculationResult = adminRegisterService.calculateBonusFromRequest(request);

            if (calculationResult.isSuccess()) {
                BonusCalculationResultDTO result = calculationResult.getData();

                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully calculated bonus: amount=%.2f", result.getBonusAmount()));

                // Handle warnings if any (e.g., calculation succeeded but save failed)
                if (calculationResult.hasWarnings()) {
                    LoggerUtil.warn(this.getClass(), "Bonus calculation warnings: " + String.join(", ", calculationResult.getWarnings()));
                }

                return ResponseEntity.ok(result);
            } else {
                LoggerUtil.error(this.getClass(), "Failed to calculate bonus: " + calculationResult.getErrorMessage());
                return handleServiceResultErrorResponse(calculationResult);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error calculating bonus: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get register summary - SIMPLIFIED to delegate to service
     */
    @GetMapping("/summary")
    public ResponseEntity<RegisterSummaryDTO> getRegisterSummary(@AuthenticationPrincipal UserDetails userDetails, @RequestParam String username,
                                                                 @RequestParam Integer userId, @RequestParam Integer year, @RequestParam Integer month) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            LoggerUtil.info(this.getClass(), String.format("Getting register summary for %s - %d/%d", username, year, month));

            // Delegate to service
            ServiceResult<RegisterSummaryDTO> summaryResult = adminRegisterService.getRegisterSummaryForUser(username, userId, year, month);

            if (summaryResult.isSuccess()) {
                LoggerUtil.info(this.getClass(), String.format("Successfully calculated summary for %s", username));
                return ResponseEntity.ok(summaryResult.getData());
            } else {
                LoggerUtil.error(this.getClass(), String.format("Failed to calculate summary for %s: %s", username, summaryResult.getErrorMessage()));
                return handleServiceResultErrorResponse(summaryResult);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error getting summary for %s: %s", username, e.getMessage()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export to Excel - SIMPLIFIED with service delegation
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(@AuthenticationPrincipal UserDetails userDetails, @RequestParam Integer userId, @RequestParam Integer year, @RequestParam Integer month) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            LoggerUtil.info(this.getClass(), String.format("Exporting register to Excel for user %d - %d/%d", userId, year, month));

            // Get user details using service
            ServiceResult<User> userResult = adminRegisterService.getUserById(userId);
            if (userResult.isFailure()) {
                LoggerUtil.error(this.getClass(), "Failed to get user details for export: " + userResult.getErrorMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            User user = userResult.getData();

            // Load register entries using service
            ServiceResult<List<RegisterEntry>> entriesResult = adminRegisterService.loadUserRegisterEntries(user.getUsername(), userId, year, month);
            if (entriesResult.isFailure()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load entries for export for %s: %s", user.getUsername(), entriesResult.getErrorMessage()));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            List<RegisterEntry> entries = entriesResult.getData();

            // Get bonus configuration and calculation result if exists
            BonusConfiguration bonusConfig = BonusConfiguration.getDefaultConfig();
            BonusCalculationResultDTO bonusResult = null;

            try {
                // Try to load saved bonus result using service
                ServiceResult<BonusCalculationResultDTO> bonusResultService = adminRegisterService.loadSavedBonusResult(userId, year, month);
                if (bonusResultService.isSuccess()) {
                    bonusResult = bonusResultService.getData();
                    LoggerUtil.info(this.getClass(), String.format("Loaded saved bonus result for user %d", userId));
                } else {
                    LoggerUtil.info(this.getClass(), String.format("No saved bonus result found for user %d", userId));
                }
            } catch (Exception e) {
                LoggerUtil.info(this.getClass(), String.format("No saved bonus result found for user %d: %s", userId, e.getMessage()));
            }

            // Generate Excel file
            byte[] excelBytes = adminRegisterExcelExporter.exportToExcel(user, entries, bonusConfig, bonusResult, year, month);

            // Set up response headers
            String filename = String.format("register_report_%s_%d_%02d.xlsx", user.getUsername(), year, month);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename(filename, StandardCharsets.UTF_8)
                    .build());

            LoggerUtil.info(this.getClass(), String.format("Successfully exported Excel for user %s with %d entries", user.getUsername(), entries.size()));

            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error exporting Excel: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Confirm all changes - SIMPLIFIED to delegate to service
     */
    @PostMapping("/confirm-all-changes")
    public ResponseEntity<?> confirmAllChanges(@AuthenticationPrincipal UserDetails userDetails, @RequestParam Integer userId, @RequestParam Integer year, @RequestParam Integer month) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
            }

            LoggerUtil.info(this.getClass(), String.format("Confirming all changes for user %d - %d/%d", userId, year, month));

            // Get username using service
            ServiceResult<User> userResult = adminRegisterService.getUserById(userId);
            if (userResult.isFailure()) {
                String errorMessage = formatServiceError(userResult, "loading user for confirmation");
                return ResponseEntity.badRequest().body(errorMessage);
            }

            String username = userResult.getData().getUsername();

            // Confirm changes using service
            ServiceResult<Integer> confirmResult = adminRegisterService.confirmAllAdminChanges(username, userId, year, month);

            if (confirmResult.isSuccess()) {
                Integer confirmedCount = confirmResult.getData();

                LoggerUtil.info(this.getClass(), String.format(
                        "Admin confirmed %d changes for user %s - %d/%d", confirmedCount, username, year, month));

                String responseMessage = String.format("Confirmed %d changes", confirmedCount);

                // Handle warnings if any
                if (confirmResult.hasWarnings()) {
                    LoggerUtil.info(this.getClass(), "Confirmation completed with warnings: " + String.join(", ", confirmResult.getWarnings()));
                }

                return ResponseEntity.ok().body(responseMessage);
            } else {
                return handleServiceResultResponse(confirmResult, "confirming changes");
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error confirming admin changes: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error occurred while confirming changes");
        }
    }

    // ========================================================================
    // SIMPLIFIED ERROR HANDLING METHODS
    // ========================================================================

    /**
     * Format ServiceResult errors with appropriate user-friendly messages
     */
    private String formatServiceError(ServiceResult<?> result, String operation) {
        if (result.isValidationError()) {
            return "Validation error: " + result.getErrorMessage();
        } else if (result.isSystemError()) {
            return "System error occurred while " + operation + ". Please try again.";
        } else if (result.isBusinessError()) {
            return "Error " + operation + ": " + result.getErrorMessage();
        } else if (result.isNotFound()) {
            return "Required data not found for " + operation;
        } else if (result.isUnauthorized()) {
            return "You are not authorized to perform this " + operation;
        } else {
            return "Error occurred while " + operation + ": " + result.getErrorMessage();
        }
    }

    /**
     * Handle ServiceResult for ResponseEntity (for endpoints that return data)
     */
    private <T> ResponseEntity<T> handleServiceResultErrorResponse(ServiceResult<T> result) {
        if (result.isValidationError()) {
            return ResponseEntity.badRequest().build();
        } else if (result.isSystemError()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } else if (result.isNotFound()) {
            return ResponseEntity.notFound().build();
        } else if (result.isUnauthorized()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Handle ServiceResult for ResponseEntity (for endpoints that return success/error messages)
     */
    private ResponseEntity<?> handleServiceResultResponse(ServiceResult<?> result, String operation) {
        if (result.isSuccess()) {
            return ResponseEntity.ok().build();
        } else {
            String errorMessage = formatServiceError(result, operation);

            if (result.isValidationError()) {
                return ResponseEntity.badRequest().body(errorMessage);
            } else if (result.isSystemError()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
            } else if (result.isNotFound()) {
                return ResponseEntity.notFound().build();
            } else if (result.isUnauthorized()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorMessage);
            } else {
                return ResponseEntity.badRequest().body(errorMessage);
            }
        }
    }
}