package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ApprovalStatusType;
import com.ctgraphdep.enums.CheckType;
import com.ctgraphdep.enums.CheckingStatus;
import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UsersCheckValueEntry;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.CheckRegisterExcelExporter;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for check register operations
 * Similar to UserRegisterController but for check entries
 */
@Controller
@RequestMapping("/user/check-register")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class CheckRegisterController extends BaseController {

    private final CheckRegisterService checkRegisterService;
    private final CheckRegisterExcelExporter checkRegisterExcelExporter;
    private final WorkScheduleService workScheduleService;
    private final CheckValuesCacheManager checkValuesCacheManager;
    private final CheckValuesService checkValuesService;

    public CheckRegisterController(UserService userService, FolderStatus folderStatus, CheckRegisterService checkRegisterService,
                                   TimeValidationService timeValidationService, CheckRegisterExcelExporter checkRegisterExcelExporter, WorkScheduleService workScheduleService, CheckValuesCacheManager checkValuesCacheManager, CheckValuesService checkValuesService) {
        super(userService, folderStatus, timeValidationService);
        this.checkRegisterService = checkRegisterService;
        this.checkRegisterExcelExporter = checkRegisterExcelExporter;
        this.workScheduleService = workScheduleService;
        this.checkValuesCacheManager = checkValuesCacheManager;
        this.checkValuesService = checkValuesService;
    }

    /**
     * Display check register page
     */
    @GetMapping
    public String showCheckRegister(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing check register page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes in one call
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }
            LoggerUtil.info(this.getClass(), "User role in showCheckRegister: '" + currentUser.getRole() + "'");

            if (hasCheckingRole(currentUser) && !checkValuesCacheManager.hasCachedCheckValues(currentUser.getUsername())) {
                LoggerUtil.info(this.getClass(), "Cache not initialized for " + currentUser.getUsername() + ", loading values now");
                loadCheckValuesForUser(currentUser);
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Calculate standard work hours
            int standardWorkHours = workScheduleService.calculateStandardWorkHours(currentUser.getUsername(), selectedYear, selectedMonth);

            // Get target work units per hour from cache if available, otherwise use the service
            double targetWorkUnitsPerHour;
            if (checkValuesCacheManager.hasCachedCheckValues(currentUser.getUsername())) {
                targetWorkUnitsPerHour = checkValuesCacheManager.getTargetWorkUnitsPerHour(currentUser.getUsername());
                LoggerUtil.info(this.getClass(), String.format(
                        "USING CACHED VALUE: For user %s, cached targetWorkUnitsPerHour=%f",
                        currentUser.getUsername(), targetWorkUnitsPerHour));
            } else {
                targetWorkUnitsPerHour = workScheduleService.getTargetWorkUnitsPerHour();
                LoggerUtil.warn(this.getClass(), String.format(
                        "USING DEFAULT VALUE: For user %s, default targetWorkUnitsPerHour=%f",
                        currentUser.getUsername(), targetWorkUnitsPerHour));
            }

            model.addAttribute("standardWorkHours", standardWorkHours);
            model.addAttribute("targetWorkUnitsPerHour", targetWorkUnitsPerHour);

            // Add check type values from cache to be used by JavaScript
            if (checkValuesCacheManager.hasCachedCheckValues(currentUser.getUsername())) {
                Map<String, Double> checkTypeValues = new HashMap<>();
                for (String checkType : CheckType.getValues()) {
                    checkTypeValues.put(checkType, checkValuesCacheManager.getCheckTypeValue(currentUser.getUsername(), checkType));
                }
                model.addAttribute("checkTypeValues", checkTypeValues);
                LoggerUtil.info(this.getClass(), "Added cached check type values to model");
            }

            // Always set these basic attributes regardless of potential errors
            model.addAttribute("checkTypes", CheckType.getValues());
            model.addAttribute("approvalStatusTypes", ApprovalStatusType.getValues());
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);

            // Set user information
            model.addAttribute("user", currentUser);
            model.addAttribute("userName", currentUser.getName());
            model.addAttribute("userDisplayName", currentUser.getName() != null ? currentUser.getName() : currentUser.getUsername());

            // Load entries
            List<RegisterCheckEntry> entries = checkRegisterService.loadMonthEntries(currentUser.getUsername(), currentUser.getUserId(), selectedYear, selectedMonth);
            model.addAttribute("entries", entries != null ? entries : new ArrayList<>());

            return "user/check-register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading check register page: " + e.getMessage(), e);

            // Set error attributes while preserving basic functionality
            model.addAttribute("error", "Error loading check register data: " + e.getMessage());
            model.addAttribute("entries", new ArrayList<>());
            model.addAttribute("checkTypes", CheckType.getValues());
            model.addAttribute("approvalStatusTypes", ApprovalStatusType.getValues());
            model.addAttribute("currentYear", year != null ? year : getStandardCurrentDate().getYear());
            model.addAttribute("currentMonth", month != null ? month : getStandardCurrentDate().getMonthValue());

            // Provide default values for metrics in case of error
            model.addAttribute("standardWorkHours", 160);
            model.addAttribute("targetWorkUnitsPerHour", 4.5);

            return "user/check-register";
        }
    }

    /**
     * Save new check entry
     */
    @PostMapping("/entry")
    public String saveEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) String omsId,
            @RequestParam(required = false) String productionId,
            @RequestParam(required = false) String designerName,
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) Integer articleNumbers,
            @RequestParam(required = false) Integer filesNumbers,
            @RequestParam(required = false) String errorDescription,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) Double orderValue,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Creating new check entry at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Initial validation for required fields
            if (date == null) {
                return String.format("redirect:/user/check-register?error=missing_date&year=%d&month=%d", year, month);
            }
            if (omsId == null || omsId.trim().isEmpty()) {
                return String.format("redirect:/user/check-register?error=missing_oms_id&year=%d&month=%d", year, month);
            }
            if (designerName == null || designerName.trim().isEmpty()) {
                return String.format("redirect:/user/check-register?error=missing_designer_name&year=%d&month=%d", year, month);
            }
            if (checkType == null || checkType.trim().isEmpty()) {
                return String.format("redirect:/user/check-register?error=missing_check_type&year=%d&month=%d", year, month);
            }
            if (articleNumbers == null) {
                return String.format("redirect:/user/check-register?error=missing_article_numbers&year=%d&month=%d", year, month);
            }
            if (filesNumbers == null) {
                return String.format("redirect:/user/check-register?error=missing_file_numbers&year=%d&month=%d", year, month);
            }
            if (approvalStatus == null || approvalStatus.trim().isEmpty()) {
                return String.format("redirect:/user/check-register?error=missing_approval_status&year=%d&month=%d", year, month);
            }

            RegisterCheckEntry entry = RegisterCheckEntry.builder()
                    .date(date)
                    .omsId(omsId.trim())
                    .productionId(productionId != null ? productionId.trim() : null)
                    .designerName(designerName.trim())
                    .checkType(checkType)
                    .articleNumbers(articleNumbers)
                    .filesNumbers(filesNumbers)
                    .errorDescription(errorDescription != null ? errorDescription.trim() : null)
                    .approvalStatus(approvalStatus)
                    .orderValue(orderValue)
                    .adminSync(CheckingStatus.CHECKING_INPUT.name())
                    .build();

            checkRegisterService.saveEntry(currentUser.getUsername(), currentUser.getUserId(), entry);
            redirectAttributes.addFlashAttribute("successMessage", "Check entry added successfully");

        } catch (RegisterValidationException e) {
            return String.format("redirect:/user/check-register?error=%s&year=%d&month=%d", e.getErrorCode(), year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving check entry: " + e.getMessage());
            return String.format("redirect:/user/check-register?error=save_failed&year=%d&month=%d", year, month);
        }

        return "redirect:/user/check-register?year=" + year + "&month=" + month;
    }

    /**
     * Update existing check entry
     */
    @PostMapping("/entry/{entryId}")
    public String updateEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer entryId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam String omsId,
            @RequestParam(required = false) String productionId,
            @RequestParam String designerName,
            @RequestParam String checkType,
            @RequestParam Integer articleNumbers,
            @RequestParam Integer filesNumbers,
            @RequestParam(required = false) String errorDescription,
            @RequestParam String approvalStatus,
            @RequestParam(required = false) Double orderValue,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Updating check entry " + entryId + " at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            RegisterCheckEntry entry = RegisterCheckEntry.builder()
                    .entryId(entryId)
                    .date(date)
                    .omsId(omsId)
                    .productionId(productionId)
                    .designerName(designerName)
                    .checkType(checkType)
                    .articleNumbers(articleNumbers)
                    .filesNumbers(filesNumbers)
                    .errorDescription(errorDescription)
                    .approvalStatus(approvalStatus)
                    .orderValue(orderValue)
                    .adminSync(CheckingStatus.CHECKING_INPUT.name())
                    .build();

            checkRegisterService.saveEntry(currentUser.getUsername(), currentUser.getUserId(), entry);
            redirectAttributes.addFlashAttribute("successMessage", "Check entry updated successfully");

        } catch (RegisterValidationException e) {
            LoggerUtil.warn(this.getClass(), "Validation error while updating check entry: " + e.getMessage());
            return String.format("redirect:/user/check-register?error=%s&year=%d&month=%d", e.getErrorCode(), year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating check entry: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update check entry: " + e.getMessage());
        }

        return "redirect:/user/check-register?year=" + year + "&month=" + month;
    }

    /**
     * Delete check entry
     */
    @PostMapping("/delete")
    public String deleteEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer entryId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Deleting check entry " + entryId + " at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            checkRegisterService.deleteEntry(currentUser.getUsername(), currentUser.getUserId(), entryId, year, month);
            redirectAttributes.addFlashAttribute("successMessage", "Check entry deleted successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error deleting check entry: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting check entry");
        }

        return "redirect:/user/check-register?year=" + year + "&month=" + month;
    }

    /**
     * Search across all check register entries
     * This is a placeholder for future implementation
     */
    @GetMapping("/search")
    public ResponseEntity<List<RegisterCheckEntry>> performSearch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam() String query
    ) {
        try {
            LoggerUtil.info(this.getClass(), "Performing check register search at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Perform search
            List<RegisterCheckEntry> searchResults = checkRegisterService.performFullRegisterSearch(
                    currentUser.getUsername(),
                    currentUser.getUserId(),
                    query
            );

            return ResponseEntity.ok(searchResults);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error performing check register search: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {
        try {
            LoggerUtil.info(this.getClass(), "Exporting check register to Excel at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Load entries
            List<RegisterCheckEntry> entries = checkRegisterService.loadMonthEntries(
                    currentUser.getUsername(),
                    currentUser.getUserId(),
                    year,
                    month);

            // Generate Excel using our exporter
            byte[] excelData = checkRegisterExcelExporter.exportToExcel(
                    currentUser,
                    entries,
                    year,
                    month);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"check_register_%d_%02d.xlsx\"", year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting check register to Excel: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Helper method to load check values for a user
     */
    private void loadCheckValuesForUser(User user) {
        try {
            // Get the user's check values
            UsersCheckValueEntry userCheckValues = checkValuesService.getUserCheckValues(
                    user.getUsername(), user.getUserId());

            if (userCheckValues != null && userCheckValues.getCheckValuesEntry() != null) {
                // Log the actual values being loaded
                LoggerUtil.info(this.getClass(), String.format(
                        "Loading check values for %s: workUnitsPerHour=%f",
                        user.getUsername(),
                        userCheckValues.getCheckValuesEntry().getWorkUnitsPerHour()));

                // Cache the check values
                checkValuesCacheManager.cacheCheckValues(
                        user.getUsername(), userCheckValues.getCheckValuesEntry());
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading check values: " + e.getMessage());
        }
    }

    /**
     * Helper method to check if a user has checking roles
     */
    private boolean hasCheckingRole(User user) {
        if (user == null || user.getRole() == null) {
            LoggerUtil.warn(this.getClass(), "Cannot check role: user or role is null");
            return false;
        }

        String role = user.getRole();

        // Check for roles without the ROLE_ prefix
        boolean hasRole = role.contains("TL_CHECKING") ||
                role.contains("USER_CHECKING") ||
                role.equals("CHECKING");

        LoggerUtil.info(this.getClass(), String.format(
                "ROLE CHECK: User %s has role '%s', checking role result: %b",
                user.getUsername(), role, hasRole));

        return hasRole;
    }
}