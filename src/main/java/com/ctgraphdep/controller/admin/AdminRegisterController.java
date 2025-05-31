package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.bonus.BonusCalculationResultDTO;
import com.ctgraphdep.model.dto.RegisterSummaryDTO;
import com.ctgraphdep.service.*;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepTypes;
import com.ctgraphdep.utils.AdminRegisterExcelExporter;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * REFACTORED AdminRegisterController with ServiceResult pattern.
 * Key Changes:
 * - All service calls now use ServiceResult<T> instead of direct exceptions
 * - Consistent error handling with user-friendly messages
 * - Proper validation and warning support
 * - Enhanced logging and error categorization
 * - Follows same pattern as CheckRegisterController
 */
@Controller
@RequestMapping("/admin/register")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminRegisterController extends BaseController {

    private final AdminRegisterService adminRegisterService;
    private final WorktimeManagementService worktimeManagementService;
    private final AdminRegisterExcelExporter adminRegisterExcelExporter;

    @Autowired
    public AdminRegisterController(UserService userService, FolderStatus folderStatus, TimeValidationService timeValidationService,
            AdminRegisterService adminRegisterService, WorktimeManagementService worktimeManagementService, AdminRegisterExcelExporter adminRegisterExcelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.adminRegisterService = adminRegisterService;
        this.worktimeManagementService = worktimeManagementService;
        this.adminRegisterExcelExporter = adminRegisterExcelExporter;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Display admin register page - REFACTORED with ServiceResult handling
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

            // If userId is provided, look up user and load entries
            if (userId != null) {
                ServiceResult<User> userResult = getUserById(userId);
                if (userResult.isFailure()) {
                    String errorMessage = handleServiceError(userResult, "loading user details");
                    model.addAttribute("errorMessage", errorMessage);
                    model.addAttribute("entries", new ArrayList<>());
                    return "admin/register";
                }

                User selectedUser = userResult.getData();

                // Load register entries using ServiceResult
                ServiceResult<List<RegisterEntry>> entriesResult = adminRegisterService.loadUserRegisterEntries(
                        selectedUser.getUsername(), userId, selectedYear, selectedMonth);

                if (entriesResult.isSuccess()) {
                    List<RegisterEntry> entries = entriesResult.getData();

                    // Get worked days from workTimeManagementService
                    int workedDays = worktimeManagementService.getWorkedDays(userId, selectedYear, selectedMonth);

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
                    String errorMessage = handleServiceError(entriesResult, "loading register entries");
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
     * Get worked days - REFACTORED with ServiceResult handling
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

            int workedDays = worktimeManagementService.getWorkedDays(userId, year, month);
            LoggerUtil.info(this.getClass(), String.format("Retrieved %d worked days for user %d", workedDays, userId));
            return ResponseEntity.ok(workedDays);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting worked days for user %d: %s", userId, e.getMessage()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Save entries - REFACTORED with ServiceResult handling
     */
    @PostMapping("/save")
    public ResponseEntity<?> saveEntries(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
            }

            LoggerUtil.info(this.getClass(), "Processing admin register save request");

            // Validate request structure
            if (request == null) {
                return ResponseEntity.badRequest().body("Request body is required");
            }

            // Extract and validate required parameters
            ServiceResult<SaveRequestData> requestParseResult = parseAndValidateSaveRequest(request);
            if (requestParseResult.isFailure()) {
                String errorMessage = handleServiceError(requestParseResult, "parsing save request");
                return ResponseEntity.badRequest().body(errorMessage);
            }

            SaveRequestData requestData = requestParseResult.getData();

            // Convert entries data to RegisterEntry objects
            ServiceResult<List<RegisterEntry>> entriesConversionResult = convertRequestEntriesToRegisterEntries(requestData.entriesData);
            if (entriesConversionResult.isFailure()) {
                String errorMessage = handleServiceError(entriesConversionResult, "converting entries data");
                return ResponseEntity.badRequest().body(errorMessage);
            }

            List<RegisterEntry> entries = entriesConversionResult.getData();

            LoggerUtil.info(this.getClass(), String.format(
                    "Saving %d entries for user %s (year: %d, month: %d)",
                    entries.size(), requestData.username, requestData.year, requestData.month));

            // Save entries through service using ServiceResult pattern
            ServiceResult<Void> saveResult = adminRegisterService.saveAdminRegisterEntries(
                    requestData.username, requestData.userId, requestData.year, requestData.month, entries);

            if (saveResult.isSuccess()) {
                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully saved %d entries for user %s", entries.size(), requestData.username));

                // Handle warnings if any
                if (saveResult.hasWarnings()) {
                    LoggerUtil.info(this.getClass(), "Save completed with warnings: " + String.join(", ", saveResult.getWarnings()));
                }

                return ResponseEntity.ok().build();
            } else {
                // Handle different error types appropriately
                String errorMessage = handleServiceError(saveResult, "saving entries");
                LoggerUtil.error(this.getClass(), String.format(
                        "Failed to save entries for user %s: %s", requestData.username, saveResult.getErrorMessage()));

                if (saveResult.isValidationError()) {
                    return ResponseEntity.badRequest().body(errorMessage);
                } else if (saveResult.isSystemError()) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
                } else {
                    return ResponseEntity.badRequest().body(errorMessage);
                }
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error saving entries: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error occurred while saving entries");
        }
    }

    /**
     * Calculate bonus - REFACTORED with ServiceResult handling
     */
    @PostMapping("/calculate-bonus")
    public ResponseEntity<BonusCalculationResultDTO> calculateBonus(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            LoggerUtil.info(this.getClass(), "Processing bonus calculation request");

            // Calculate bonus using service with ServiceResult pattern
            ServiceResult<BonusCalculationResultDTO> calculationResult = adminRegisterService.calculateBonusFromRequest(request);

            if (calculationResult.isSuccess()) {
                BonusCalculationResultDTO result = calculationResult.getData();

                // Get required parameters for saving
                Integer userId = (Integer) request.get("userId");
                Integer year = (Integer) request.get("year");
                Integer month = (Integer) request.get("month");

                // Get username from UserService
                ServiceResult<User> userResult = getUserById(userId);
                if (userResult.isFailure()) {
                    LoggerUtil.error(this.getClass(), "Failed to get username for saving bonus: " + userResult.getErrorMessage());
                    return ResponseEntity.badRequest().build();
                }

                String username = userResult.getData().getUsername();

                // Save bonus result using ServiceResult pattern
                ServiceResult<Void> saveResult = adminRegisterService.saveBonusResult(userId, year, month, result, username);
                if (saveResult.isFailure()) {
                    LoggerUtil.warn(this.getClass(), "Bonus calculated successfully but failed to save: " + saveResult.getErrorMessage());
                    // Still return the calculation result even if save failed
                }

                LoggerUtil.info(this.getClass(), String.format(
                        "Successfully calculated bonus for user %s: amount=%.2f", username, result.getBonusAmount()));

                return ResponseEntity.ok(result);
            } else {
                LoggerUtil.error(this.getClass(), "Failed to calculate bonus: " + calculationResult.getErrorMessage());

                if (calculationResult.isValidationError()) {
                    return ResponseEntity.badRequest().build();
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error calculating bonus: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get register summary - REFACTORED with ServiceResult handling
     */
    @GetMapping("/summary")
    public ResponseEntity<RegisterSummaryDTO> getRegisterSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String username,
            @RequestParam Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            LoggerUtil.info(this.getClass(), String.format("Getting register summary for %s - %d/%d", username, year, month));

            // Load entries using ServiceResult pattern
            ServiceResult<List<RegisterEntry>> entriesResult = adminRegisterService.readMergedAdminEntries(username, userId, year, month);
            if (entriesResult.isFailure()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load entries for summary for %s: %s", username, entriesResult.getErrorMessage()));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            List<RegisterEntry> entries = entriesResult.getData();

            // Calculate summary using ServiceResult pattern
            ServiceResult<RegisterSummaryDTO> summaryResult = adminRegisterService.calculateRegisterSummary(entries);
            if (summaryResult.isSuccess()) {
                LoggerUtil.info(this.getClass(), String.format("Successfully calculated summary for %s", username));
                return ResponseEntity.ok(summaryResult.getData());
            } else {
                LoggerUtil.error(this.getClass(), String.format("Failed to calculate summary for %s: %s", username, summaryResult.getErrorMessage()));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Unexpected error getting summary for %s: %s", username, e.getMessage()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export to Excel - ENHANCED with ServiceResult handling
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(
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

            LoggerUtil.info(this.getClass(), String.format("Exporting register to Excel for user %d - %d/%d", userId, year, month));

            // Get user details using ServiceResult
            ServiceResult<User> userResult = getUserById(userId);
            if (userResult.isFailure()) {
                LoggerUtil.error(this.getClass(), "Failed to get user details for export: " + userResult.getErrorMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            User user = userResult.getData();

            // Load register entries using ServiceResult
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
                // Try to load saved bonus result using ServiceResult
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
     * Confirm all changes - REFACTORED with ServiceResult handling
     */
    @PostMapping("/confirm-all-changes")
    public ResponseEntity<?> confirmAllChanges(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month) {

        try {
            // Use validateUserAccess for REST controllers
            User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
            }

            LoggerUtil.info(this.getClass(), String.format("Confirming all changes for user %d - %d/%d", userId, year, month));

            // Get username using ServiceResult
            ServiceResult<User> userResult = getUserById(userId);
            if (userResult.isFailure()) {
                String errorMessage = handleServiceError(userResult, "loading user for confirmation");
                return ResponseEntity.badRequest().body(errorMessage);
            }

            String username = userResult.getData().getUsername();

            // Confirm changes using ServiceResult pattern
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
                // Handle different error types appropriately
                String errorMessage = handleServiceError(confirmResult, "confirming changes");
                LoggerUtil.error(this.getClass(), String.format("Failed to confirm changes for user %s: %s", username, confirmResult.getErrorMessage()));

                if (confirmResult.isValidationError()) {
                    return ResponseEntity.badRequest().body(errorMessage);
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
                }
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error confirming admin changes: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error occurred while confirming changes");
        }
    }

    // ========================================================================
    // HELPER METHODS FOR ERROR HANDLING AND PARSING
    // ========================================================================

    /**
     * Handles ServiceResult errors with appropriate user-friendly messages
     */
    private String handleServiceError(ServiceResult<?> result, String operation) {
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
     * Get user by ID with ServiceResult wrapper
     */
    private ServiceResult<User> getUserById(Integer userId) {
        try {
            return getUserService().getUserById(userId)
                    .map(ServiceResult::success)
                    .orElse(ServiceResult.notFound("User not found with ID: " + userId, "user_not_found"));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error getting user by ID %d: %s", userId, e.getMessage()), e);
            return ServiceResult.systemError("Failed to retrieve user information", "user_retrieval_failed");
        }
    }

    /**
     * Parse and validate save request data
     */
    private ServiceResult<SaveRequestData> parseAndValidateSaveRequest(Map<String, Object> request) {
        try {
            if (request.get("username") == null || request.get("username").toString().trim().isEmpty()) {
                return ServiceResult.validationError("Username is required", "missing_username");
            }
            if (request.get("userId") == null) {
                return ServiceResult.validationError("User ID is required", "missing_user_id");
            }
            if (request.get("year") == null) {
                return ServiceResult.validationError("Year is required", "missing_year");
            }
            if (request.get("month") == null) {
                return ServiceResult.validationError("Month is required", "missing_month");
            }
            if (request.get("entries") == null) {
                return ServiceResult.validationError("Entries data is required", "missing_entries");
            }

            String username = request.get("username").toString();
            Integer userId = Integer.parseInt(request.get("userId").toString());
            Integer year = Integer.parseInt(request.get("year").toString());
            Integer month = Integer.parseInt(request.get("month").toString());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");

            SaveRequestData data = new SaveRequestData(username, userId, year, month, entriesData);
            return ServiceResult.success(data);

        } catch (NumberFormatException e) {
            return ServiceResult.validationError("Invalid number format in request", "invalid_number_format");
        } catch (ClassCastException e) {
            return ServiceResult.validationError("Invalid data format in request", "invalid_data_format");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error parsing save request: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to parse request data", "request_parse_failed");
        }
    }

    /**
     * Convert request entries data to RegisterEntry objects
     */
    private ServiceResult<List<RegisterEntry>> convertRequestEntriesToRegisterEntries(List<Map<String, Object>> entriesData) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
            List<RegisterEntry> entries = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (int i = 0; i < entriesData.size(); i++) {
                try {
                    Map<String, Object> data = entriesData.get(i);

                    LoggerUtil.debug(this.getClass(), "Processing entry ID: " + data.get("entryId") +
                            ", printPrepTypes: " + data.get("printPrepTypes") +
                            ", adminSync: " + data.get("adminSync"));

                    // Handle printPrepTypes conversion
                    List<String> printPrepTypes = new ArrayList<>();
                    Object printPrepTypesObj = data.get("printPrepTypes");

                    if (printPrepTypesObj instanceof List<?> typesList) {
                        // Handle list case
                        typesList.forEach(type -> {
                            if (type != null && !type.toString().equalsIgnoreCase("null") && !type.toString().isEmpty()) {
                                printPrepTypes.add(type.toString().trim());
                            }
                        });
                    } else if (printPrepTypesObj instanceof String typesStr) {
                        // Handle string case
                        if (!typesStr.isEmpty()) {
                            Arrays.stream(typesStr.split("\\s*,\\s*"))
                                    .filter(type -> !type.equalsIgnoreCase("null") && !type.isEmpty())
                                    .forEach(type -> printPrepTypes.add(type.trim()));
                        }
                    }

                    // If no valid types were found, add default
                    if (printPrepTypes.isEmpty()) {
                        printPrepTypes.add("DIGITAL");
                    }

                    // IMPORTANT: Preserve the original adminSync status from the client
                    String adminSync = data.get("adminSync") != null ?
                            data.get("adminSync").toString() : SyncStatusMerge.USER_DONE.name();

                    RegisterEntry entry = RegisterEntry.builder()
                            .entryId(Integer.parseInt(data.get("entryId").toString()))
                            .userId(Integer.parseInt(data.get("userId").toString()))
                            .date(LocalDate.parse(data.get("date").toString(), formatter))
                            .orderId(data.get("orderId").toString())
                            .productionId(data.get("productionId").toString())
                            .omsId(data.get("omsId").toString())
                            .clientName(data.get("clientName").toString())
                            .actionType(data.get("actionType").toString())
                            .printPrepTypes(printPrepTypes)
                            .colorsProfile(data.get("colorsProfile").toString())
                            .articleNumbers(Integer.parseInt(data.get("articleNumbers").toString()))
                            .graphicComplexity(Double.parseDouble(data.get("graphicComplexity").toString()))
                            .observations(data.get("observations") != null ? data.get("observations").toString() : "")
                            .adminSync(adminSync) // Use the status from the client
                            .build();

                    entries.add(entry);

                } catch (Exception e) {
                    warnings.add("Failed to convert entry at index " + i + ": " + e.getMessage());
                    LoggerUtil.warn(this.getClass(), String.format("Error converting entry at index %d: %s", i, e.getMessage()));
                }
            }

            if (entries.isEmpty() && !entriesData.isEmpty()) {
                return ServiceResult.validationError("No valid entries could be converted", "no_valid_entries");
            }

            if (!warnings.isEmpty()) {
                return ServiceResult.successWithWarnings(entries, warnings);
            }

            return ServiceResult.success(entries);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error converting entries data: " + e.getMessage(), e);
            return ServiceResult.systemError("Failed to convert entries data", "entries_conversion_failed");
        }
    }

    /**
     * Data class for save request parsing
     */
    private record SaveRequestData(String username, Integer userId, Integer year, Integer month,
                                   List<Map<String, Object>> entriesData) {
    }
}