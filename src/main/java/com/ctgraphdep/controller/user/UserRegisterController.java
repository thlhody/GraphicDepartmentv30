package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepTypes;
import com.ctgraphdep.enums.SyncStatusMerge;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.dto.RegisterSearchResultDTO;
import com.ctgraphdep.service.UserRegisterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserRegisterExcelExporter;
import com.ctgraphdep.validation.TimeValidationFactory;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.ValidatePeriodCommand;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REFACTORED UserRegisterController with ServiceResult pattern.
 * Key Changes:
 * - All service calls now use ServiceResult<T> instead of direct exceptions
 * - Consistent error handling with user-friendly messages
 * - Proper validation and warning support
 * - Enhanced logging and error categorization
 * - Follows same pattern as CheckRegisterController
 * - Removed manual validation - now handled by service layer
 */
@Controller
@RequestMapping("/user/register")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_TL_CHECKING')")
public class UserRegisterController extends BaseController {

    private final UserRegisterService userRegisterService;
    private final UserRegisterExcelExporter userRegisterExcelExporter;

    public UserRegisterController(UserService userService,
                                  FolderStatus folderStatus,
                                  UserRegisterService userRegisterService,
                                  UserRegisterExcelExporter userRegisterExcelExporter,
                                  TimeValidationService timeValidationService) {
        super(userService, folderStatus, timeValidationService);
        this.userRegisterService = userRegisterService;
        this.userRegisterExcelExporter = userRegisterExcelExporter;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Display register page - REFACTORED with ServiceResult handling
     */
    @GetMapping
    public String showRegister(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing user register page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes in one call
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            try {
                // Create and execute validation command directly
                TimeValidationFactory validationFactory = getTimeValidationService().getValidationFactory();
                ValidatePeriodCommand validateCommand = validationFactory.createValidatePeriodCommand(selectedYear, selectedMonth, 24); // 24 months ahead max
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                // Handle validation failure gracefully
                String userMessage = "The selected period is not valid. You can only view periods up to 24 months in the future.";
                redirectAttributes.addFlashAttribute("periodError", userMessage);

                // Reset to current period
                LocalDate currentDate = getStandardCurrentDate();
                return "redirect:/user/register?year=" + currentDate.getYear() + "&month=" + currentDate.getMonthValue();
            }

            // Always set these basic attributes regardless of potential errors
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepTypes.getValues());
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);

            // Set user information (always using current user)
            model.addAttribute("user", currentUser);
            model.addAttribute("userName", currentUser.getName());
            model.addAttribute("userDisplayName", currentUser.getName() != null ? currentUser.getName() : currentUser.getUsername());

            // Load entries for the current user using ServiceResult pattern
            ServiceResult<List<RegisterEntry>> entriesResult = userRegisterService.loadMonthEntries(
                    currentUser.getUsername(), currentUser.getUserId(), selectedYear, selectedMonth);

            if (entriesResult.isSuccess()) {
                List<RegisterEntry> entries = entriesResult.getData();
                model.addAttribute("entries", entries != null ? entries : new ArrayList<>());

                // Handle warnings if any
                if (entriesResult.hasWarnings()) {
                    model.addAttribute("warningMessage", "Data loaded with warnings: " + String.join(", ", entriesResult.getWarnings()));
                }

                LoggerUtil.info(this.getClass(), String.format("Successfully loaded %d entries for %s", entries != null ? entries.size() : 0, currentUser.getUsername()));

            } else {
                // Handle error but provide fallback data
                String errorMessage = handleServiceError(entriesResult, "loading register entries");
                model.addAttribute("errorMessage", errorMessage);
                model.addAttribute("entries", new ArrayList<>());

                LoggerUtil.error(this.getClass(), String.format("Failed to load entries for %s: %s", currentUser.getUsername(), entriesResult.getErrorMessage()));
            }

            return "user/register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error loading register page: " + e.getMessage(), e);

            // Set error attributes while preserving basic functionality
            model.addAttribute("errorMessage", "An unexpected error occurred while loading register data");
            model.addAttribute("entries", new ArrayList<>());
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepTypes.getValues());
            model.addAttribute("currentYear", year != null ? year : getStandardCurrentDate().getYear());
            model.addAttribute("currentMonth", month != null ? month : getStandardCurrentDate().getMonthValue());

            // Try to set user info from current user if available
            if (userDetails != null) {
                User currentUser = getUser(userDetails);
                if (currentUser != null) {
                    model.addAttribute("user", currentUser);
                    model.addAttribute("userName", currentUser.getName());
                    model.addAttribute("userDisplayName", currentUser.getName());
                }
            }

            return "user/register";
        }
    }

    /**
     * Save new register entry - REFACTORED with ServiceResult handling
     */
    @PostMapping("/entry")
    public String saveEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String productionId,
            @RequestParam(required = false) String omsId,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) List<String> printPrepTypes,
            @RequestParam(required = false) String colorsProfile,
            @RequestParam(required = false) Integer articleNumbers,
            @RequestParam(required = false) Double graphicComplexity,
            @RequestParam(required = false) String observations,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Creating new register entry at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Process print prep types to remove duplicates
            List<String> uniquePrintPrepTypes = printPrepTypes != null ?
                    new ArrayList<>(new LinkedHashSet<>(printPrepTypes)) : new ArrayList<>();

            // Create entry object
            RegisterEntry entry = RegisterEntry.builder()
                    .userId(currentUser.getUserId())
                    .date(date)
                    .orderId(orderId != null ? orderId.trim() : null)
                    .productionId(productionId != null ? productionId.trim() : null)
                    .omsId(omsId != null ? omsId.trim() : null)
                    .clientName(clientName != null ? clientName.trim() : null)
                    .actionType(actionType)
                    .printPrepTypes(uniquePrintPrepTypes)
                    .colorsProfile(colorsProfile != null ? colorsProfile.trim().toUpperCase() : null)
                    .articleNumbers(articleNumbers)
                    .graphicComplexity(graphicComplexity)
                    .observations(observations != null ? observations.trim() : null)
                    .adminSync(SyncStatusMerge.USER_INPUT.name())
                    .build();

            // Save entry through service using ServiceResult pattern
            ServiceResult<RegisterEntry> result = userRegisterService.saveEntry(currentUser.getUsername(), currentUser.getUserId(), entry);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", "Entry added successfully");

                // Handle warnings if any
                if (result.hasWarnings()) {
                    redirectAttributes.addFlashAttribute("infoMessage", "Entry saved with notes: " + String.join(", ", result.getWarnings()));
                }

                LoggerUtil.info(this.getClass(), String.format("Successfully created entry for %s", currentUser.getUsername()));
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "creating entry");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);

                LoggerUtil.warn(this.getClass(), String.format("Failed to create entry for %s: %s", currentUser.getUsername(), result.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error saving register entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while saving entry");
        }

        return "redirect:/user/register?year=" + year + "&month=" + month;
    }

    /**
     * Update existing register entry - REFACTORED with ServiceResult handling
     */
    @PostMapping("/entry/{entryId}")
    public String updateEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer entryId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam String orderId,
            @RequestParam(required = false) String productionId,
            @RequestParam String omsId,
            @RequestParam String clientName,
            @RequestParam String actionType,
            @RequestParam List<String> printPrepTypes,
            @RequestParam(required = false) String colorsProfile,
            @RequestParam Integer articleNumbers,
            @RequestParam(required = false) Double graphicComplexity,
            @RequestParam(required = false) String observations,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Updating register entry " + entryId + " at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Create entry with existing ID
            RegisterEntry entry = RegisterEntry.builder()
                    .entryId(entryId)
                    .userId(currentUser.getUserId())
                    .date(date)
                    .orderId(orderId)
                    .productionId(productionId)
                    .omsId(omsId)
                    .clientName(clientName)
                    .actionType(actionType)
                    .printPrepTypes(printPrepTypes)
                    .colorsProfile(colorsProfile)
                    .articleNumbers(articleNumbers)
                    .graphicComplexity(graphicComplexity)
                    .observations(observations)
                    .adminSync("USER_INPUT")
                    .build();

            // Save entry through service using ServiceResult pattern
            ServiceResult<RegisterEntry> result = userRegisterService.saveEntry(currentUser.getUsername(), currentUser.getUserId(), entry);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", "Entry updated successfully");

                // Handle warnings if any
                if (result.hasWarnings()) {
                    redirectAttributes.addFlashAttribute("infoMessage", "Entry updated with notes: " + String.join(", ", result.getWarnings()));
                }

                LoggerUtil.info(this.getClass(), String.format("Successfully updated entry %d for %s", entryId, currentUser.getUsername()));
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "updating entry");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);

                LoggerUtil.warn(this.getClass(), String.format("Failed to update entry %d for %s: %s", entryId, currentUser.getUsername(), result.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error updating register entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while updating entry");
        }

        return "redirect:/user/register?year=" + year + "&month=" + month;
    }

    /**
     * Delete register entry - REFACTORED with ServiceResult handling
     */
    @PostMapping("/delete")
    public String deleteEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer entryId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Deleting register entry " + entryId + " at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Delete through service using ServiceResult pattern
            ServiceResult<Void> result = userRegisterService.deleteEntry(currentUser.getUsername(), currentUser.getUserId(), entryId, year, month);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", "Entry deleted successfully");
                LoggerUtil.info(this.getClass(), String.format("Successfully deleted entry %d for %s", entryId, currentUser.getUsername()));
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "deleting entry");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);

                LoggerUtil.warn(this.getClass(), String.format("Failed to delete entry %d for %s: %s", entryId, currentUser.getUsername(), result.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error deleting register entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while deleting entry");
        }

        return "redirect:/user/register?year=" + year + "&month=" + month;
    }

    /**
     * Perform full register search - REFACTORED with ServiceResult handling
     */
    @GetMapping("/full-search")
    public ResponseEntity<List<RegisterSearchResultDTO>> performFullRegisterSearch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam() String query,
            @RequestParam(required = false) Integer userId) {

        try {
            LoggerUtil.info(this.getClass(), "Performing register search at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // If no userId provided, use current user's ID
            if (userId == null) {
                userId = currentUser.getUserId();
            }

            // Perform search using ServiceResult pattern
            ServiceResult<List<RegisterEntry>> searchResult = userRegisterService.performFullRegisterSearch(currentUser.getUsername(), userId, query);

            if (searchResult.isSuccess()) {
                List<RegisterEntry> searchResults = searchResult.getData();

                // Convert to DTO
                List<RegisterSearchResultDTO> dtoResults = searchResults.stream().map(RegisterSearchResultDTO::new).collect(Collectors.toList());

                LoggerUtil.info(this.getClass(), String.format("Search completed successfully, found %d results", dtoResults.size()));

                return ResponseEntity.ok(dtoResults);
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Search failed for %s: %s", currentUser.getUsername(), searchResult.getErrorMessage()));

                if (searchResult.isValidationError()) {
                    return ResponseEntity.badRequest().build();
                } else if (searchResult.isUnauthorized()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error performing register search: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export to Excel - ENHANCED with ServiceResult handling
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {

        try {
            LoggerUtil.info(this.getClass(), "Exporting register to Excel at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Load entries using ServiceResult pattern
            ServiceResult<List<RegisterEntry>> entriesResult = userRegisterService.loadMonthEntries(currentUser.getUsername(), currentUser.getUserId(), year, month);

            if (entriesResult.isFailure()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load entries for export for %s: %s", currentUser.getUsername(), entriesResult.getErrorMessage()));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            List<RegisterEntry> entries = entriesResult.getData();

            // Generate Excel using our exporter
            byte[] excelData = userRegisterExcelExporter.exportToExcel(currentUser, entries, year, month);

            LoggerUtil.info(this.getClass(), String.format("Successfully exported %d entries to Excel for %s", entries.size(), currentUser.getUsername()));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"register_%d_%02d.xlsx\"", year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error exporting register to Excel: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * API endpoint to fetch entries for AJAX requests - ENHANCED with ServiceResult handling
     */
    @GetMapping("/api/entries")
    @ResponseBody
    public ResponseEntity<List<RegisterEntry>> getEntriesJson(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer year,
            @RequestParam Integer month) {

        try {
            LoggerUtil.info(this.getClass(), "Fetching entries via API at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Load entries for the current user using ServiceResult pattern
            ServiceResult<List<RegisterEntry>> entriesResult = userRegisterService.loadMonthEntries(currentUser.getUsername(), currentUser.getUserId(), year, month);

            if (entriesResult.isSuccess()) {
                List<RegisterEntry> entries = entriesResult.getData();
                LoggerUtil.info(this.getClass(), String.format("Successfully fetched %d entries via API for %s", entries != null ? entries.size() : 0, currentUser.getUsername()));
                return ResponseEntity.ok(entries != null ? entries : new ArrayList<>());
            } else {
                LoggerUtil.error(this.getClass(), String.format("Failed to fetch entries via API for %s: %s", currentUser.getUsername(), entriesResult.getErrorMessage()));

                if (entriesResult.isValidationError()) {
                    return ResponseEntity.badRequest().build();
                } else if (entriesResult.isUnauthorized()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error fetching entries via API: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ========================================================================
    // HELPER METHODS FOR ERROR HANDLING
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
}