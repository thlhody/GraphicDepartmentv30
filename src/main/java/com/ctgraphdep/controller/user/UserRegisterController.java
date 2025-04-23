package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepTypes;
import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.dto.RegisterSearchResultDTO;
import com.ctgraphdep.service.UserRegisterService;
import com.ctgraphdep.service.UserService;
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
    }

    @GetMapping
    public String showRegister(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {

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
                ValidatePeriodCommand validateCommand = validationFactory.createValidatePeriodCommand(
                        selectedYear, selectedMonth, 24); // 24 months ahead max
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                // Handle validation failure gracefully
                String userMessage = "The selected period is not valid. You can only view periods up to 24 months in the future.";
                redirectAttributes.addFlashAttribute("periodError", userMessage);

                // Reset to current period
                LocalDate currentDate = getStandardCurrentDate();
                return "redirect:/user/register?year=" + currentDate.getYear() +
                        "&month=" + currentDate.getMonthValue();
            }

            // Always set these basic attributes regardless of potential errors
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepTypes.getValues());
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);

            // Set user information (always using current user)
            model.addAttribute("user", currentUser);
            model.addAttribute("userName", currentUser.getName());
            model.addAttribute("userDisplayName",
                    currentUser.getName() != null ? currentUser.getName() : currentUser.getUsername());

            // Load entries for the current user only
            List<RegisterEntry> entries = userRegisterService.loadMonthEntries(
                    currentUser.getUsername(),
                    currentUser.getUserId(),
                    selectedYear,
                    selectedMonth
            );
            model.addAttribute("entries", entries != null ? entries : new ArrayList<>());

            return "user/register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading register page: " + e.getMessage(), e);

            // Set error attributes while preserving basic functionality
            model.addAttribute("error", "Error loading register data: " + e.getMessage());
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

            // Get the user - don't need to add model attributes for action methods
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Initial validation for required fields
            if (date == null) {
                return String.format("redirect:/user/register?error=missing_date&year=%d&month=%d", year, month);
            }
            if (orderId == null || orderId.trim().isEmpty()) {
                return String.format("redirect:/user/register?error=missing_order_id&year=%d&month=%d", year, month);
            }
            if (omsId == null || omsId.trim().isEmpty()) {
                return String.format("redirect:/user/register?error=missing_oms_id&year=%d&month=%d", year, month);
            }
            if (clientName == null || clientName.trim().isEmpty()) {
                return String.format("redirect:/user/register?error=missing_client&year=%d&month=%d", year, month);
            }
            if (actionType == null || actionType.trim().isEmpty()) {
                return String.format("redirect:/user/register?error=missing_action_type&year=%d&month=%d", year, month);
            }
            if (printPrepTypes == null || printPrepTypes.isEmpty()) {
                return String.format("redirect:/user/register?error=missing_print_type&year=%d&month=%d", year, month);
            }
            if (articleNumbers == null) {
                return String.format("redirect:/user/register?error=missing_articles&year=%d&month=%d", year, month);
            }

            List<String> uniquePrintPrepTypes = new ArrayList<>(new LinkedHashSet<>(printPrepTypes));

            RegisterEntry entry = RegisterEntry.builder()
                    .userId(currentUser.getUserId())
                    .date(date)
                    .orderId(orderId.trim())
                    .productionId(productionId != null ? productionId.trim() : null)
                    .omsId(omsId.trim())
                    .clientName(clientName.trim())
                    .actionType(actionType)
                    .printPrepTypes(uniquePrintPrepTypes)  // Use the deduplicated list
                    .colorsProfile(colorsProfile != null ? colorsProfile.trim().toUpperCase() : null)
                    .articleNumbers(articleNumbers)
                    .graphicComplexity(graphicComplexity)
                    .observations(observations != null ? observations.trim() : null)
                    .adminSync("USER_INPUT")
                    .build();

            userRegisterService.saveEntry(currentUser.getUsername(), currentUser.getUserId(), entry);
            redirectAttributes.addFlashAttribute("successMessage", "Entry added successfully");

        } catch (RegisterValidationException e) {
            return String.format("redirect:/user/register?error=%s&year=%d&month=%d", e.getErrorCode(), year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving register entry: " + e.getMessage());
            return String.format("redirect:/user/register?error=save_failed&year=%d&month=%d", year, month);
        }

        return "redirect:/user/register?year=" + year + "&month=" + month;
    }

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
            // Get the user - don't need to add model attributes for action methods
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            RegisterEntry entry = RegisterEntry.builder()
                    .entryId(entryId)
                    .userId(currentUser.getUserId())
                    .date(date)
                    .orderId(orderId)
                    .productionId(productionId)
                    .omsId(omsId)
                    .clientName(clientName)
                    .actionType(actionType)
                    .printPrepTypes(printPrepTypes)  // Set the list directly
                    .colorsProfile(colorsProfile)
                    .articleNumbers(articleNumbers)
                    .graphicComplexity(graphicComplexity)
                    .observations(observations)
                    .adminSync("USER_INPUT")
                    .build();

            userRegisterService.saveEntry(currentUser.getUsername(), currentUser.getUserId(), entry);
            redirectAttributes.addFlashAttribute("successMessage", "Entry updated successfully");

        } catch (RegisterValidationException e) {
            LoggerUtil.warn(this.getClass(), "Validation error while updating entry: " + e.getMessage());
            return String.format("redirect:/user/register?error=%s&year=%d&month=%d", e.getErrorCode(), year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating register entry: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update entry: " + e.getMessage());
        }

        return "redirect:/user/register?year=" + year + "&month=" + month;
    }

    @PostMapping("/delete")
    public String deleteEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer entryId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Deleting register entry " + entryId + " at " + getStandardCurrentDateTime());

            // Get the user - don't need to add model attributes for action methods
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            userRegisterService.deleteEntry(currentUser.getUsername(), currentUser.getUserId(), entryId, year, month);
            redirectAttributes.addFlashAttribute("successMessage", "Entry deleted successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error deleting entry: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting entry");
        }

        return "redirect:/user/register?year=" + year + "&month=" + month;
    }

    @GetMapping("/full-search")
    public ResponseEntity<List<RegisterSearchResultDTO>> performFullRegisterSearch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam() String query,
            @RequestParam(required = false) Integer userId
    ) {
        try {
            LoggerUtil.info(this.getClass(), "Performing register search at " + getStandardCurrentDateTime());

            // Get the user - don't need to add model attributes for API endpoints
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // If no userId provided, use current user's ID
            if (userId == null) {
                userId = currentUser.getUserId();
            }

            // Perform search
            List<RegisterEntry> searchResults = userRegisterService.performFullRegisterSearch(currentUser.getUsername(), userId, query);

            // Convert to DTO
            List<RegisterSearchResultDTO> dtoResults = searchResults.stream().map(RegisterSearchResultDTO::new).collect(Collectors.toList());

            return ResponseEntity.ok(dtoResults);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error performing full register search: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {
        try {
            // Get the user - don't need to add model attributes for API endpoints
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            List<RegisterEntry> entries = userRegisterService.loadMonthEntries(currentUser.getUsername(), currentUser.getUserId(), year, month);

            byte[] excelData = userRegisterExcelExporter.exportToExcel(currentUser, entries, year, month);

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"register_%d_%02d.xlsx\"", year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).body(excelData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}