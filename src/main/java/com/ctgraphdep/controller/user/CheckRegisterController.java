package com.ctgraphdep.controller.user;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ApprovalStatusType;
import com.ctgraphdep.enums.CheckType;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.merge.constants.MergingStatusConstants;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UsersCheckValueEntry;
import com.ctgraphdep.register.service.CheckRegisterService;
import com.ctgraphdep.register.service.CheckValuesService;
import com.ctgraphdep.service.*;
import com.ctgraphdep.service.cache.CheckValuesCacheManager;
import com.ctgraphdep.service.result.ServiceResult;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for check register operations - FULLY REFACTORED
 * Handles regular user operations on check register entries using ServiceResult pattern
 * All service calls now use ServiceResult for consistent error handling
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

    public CheckRegisterController(UserService userService, FolderStatus folderStatus, CheckRegisterService checkRegisterService, TimeValidationService timeValidationService,
                                   CheckRegisterExcelExporter checkRegisterExcelExporter, WorkScheduleService workScheduleService, CheckValuesCacheManager checkValuesCacheManager,
                                   CheckValuesService checkValuesService) {
        super(userService, folderStatus, timeValidationService);
        this.checkRegisterService = checkRegisterService;
        this.checkRegisterExcelExporter = checkRegisterExcelExporter;
        this.workScheduleService = workScheduleService;
        this.checkValuesCacheManager = checkValuesCacheManager;
        this.checkValuesService = checkValuesService;
    }

    /**
     * Display check register page - REFACTORED with ServiceResult handling
     */
    @GetMapping
    public String showCheckRegister(@AuthenticationPrincipal UserDetails userDetails,
                                    @RequestParam(required = false) String username,
                                    @RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) Integer month, Model model) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing check register page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes in one call
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }
            LoggerUtil.info(this.getClass(), "User role in showCheckRegister: '" + currentUser.getRole() + "'");

            // Initialize check values cache if needed
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
                LoggerUtil.info(this.getClass(), String.format("USING CACHED VALUE: For user %s, cached targetWorkUnitsPerHour=%f", currentUser.getUsername(), targetWorkUnitsPerHour));
            } else {
                targetWorkUnitsPerHour = workScheduleService.getTargetWorkUnitsPerHour();
                LoggerUtil.warn(this.getClass(), String.format("USING DEFAULT VALUE: For user %s, default targetWorkUnitsPerHour=%f", currentUser.getUsername(), targetWorkUnitsPerHour));
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

            // Load entries using ServiceResult pattern
            ServiceResult<List<RegisterCheckEntry>> entriesResult = checkRegisterService.loadMonthEntries(
                    currentUser.getUsername(), currentUser.getUserId(), selectedYear, selectedMonth);

            if (entriesResult.isSuccess()) {
                List<RegisterCheckEntry> entries = entriesResult.getData();
                model.addAttribute("entries", entries != null ? entries : new ArrayList<>());

                // Add flag for entries that can be edited (only USER_INPUT entries)
                if (entries != null && !entries.isEmpty()) {
                    List<Integer> editableEntryIds = entries.stream()
                            .filter(entry -> MergingStatusConstants.USER_INPUT.equals(entry.getAdminSync()))
                            .map(RegisterCheckEntry::getEntryId)
                            .collect(Collectors.toList());
                    model.addAttribute("editableEntryIds", editableEntryIds);
                }

                // Handle warnings if any
                if (entriesResult.hasWarnings()) {
                    model.addAttribute("warningMessage", "Data loaded with warnings: " + String.join(", ", entriesResult.getWarnings()));
                }

                LoggerUtil.info(this.getClass(), String.format("Successfully loaded %d entries for %s",
                        entries != null ? entries.size() : 0, currentUser.getUsername()));

            } else {
                // Handle error but provide fallback data
                String errorMessage = handleServiceError(entriesResult, "loading check register entries");
                model.addAttribute("errorMessage", errorMessage);
                model.addAttribute("entries", new ArrayList<>());
                model.addAttribute("editableEntryIds", new ArrayList<>());

                LoggerUtil.error(this.getClass(), String.format("Failed to load entries for %s: %s",
                        currentUser.getUsername(), entriesResult.getErrorMessage()));
            }

            return "user/check-register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error loading check register page: " + e.getMessage(), e);

            // Set error attributes while preserving basic functionality
            model.addAttribute("errorMessage", "An unexpected error occurred while loading check register data");
            model.addAttribute("entries", new ArrayList<>());
            model.addAttribute("editableEntryIds", new ArrayList<>());
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
     * Save new check entry - REFACTORED with ServiceResult handling
     */
    @PostMapping("/entry")
    public String saveEntry(@AuthenticationPrincipal UserDetails userDetails,
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
                            @RequestParam Integer year, @RequestParam Integer month,
                            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Creating new check entry at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Create entry through service (false = not team lead)
            RegisterCheckEntry entry = checkRegisterService.createEntry(
                    false, null, date, omsId, productionId, designerName,
                    checkType, articleNumbers, filesNumbers, errorDescription,
                    approvalStatus, orderValue);

            // Save through service using ServiceResult pattern
            ServiceResult<RegisterCheckEntry> result = checkRegisterService.saveUserEntry(
                    currentUser.getUsername(), currentUser.getUserId(), entry);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", "Check entry added successfully");

                // Handle warnings if any
                if (result.hasWarnings()) {
                    redirectAttributes.addFlashAttribute("infoMessage",
                            "Entry saved with notes: " + String.join(", ", result.getWarnings()));
                }

                LoggerUtil.info(this.getClass(), String.format("Successfully created entry for %s", currentUser.getUsername()));
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "creating entry");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);

                LoggerUtil.warn(this.getClass(), String.format("Failed to create entry for %s: %s",
                        currentUser.getUsername(), result.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error saving check entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while saving entry");
        }

        return "redirect:/user/check-register?year=" + year + "&month=" + month;
    }

    /**
     * Update existing check entry - REFACTORED with ServiceResult handling
     */
    @PostMapping("/entry/{entryId}")
    public String updateEntry(@AuthenticationPrincipal UserDetails userDetails,
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
                              @RequestParam Integer year, @RequestParam Integer month,
                              RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Updating check entry " + entryId + " at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Create entry with existing ID
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
                    .build();

            // Save through service using ServiceResult pattern
            ServiceResult<RegisterCheckEntry> result = checkRegisterService.saveUserEntry(
                    currentUser.getUsername(), currentUser.getUserId(), entry);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", "Check entry updated successfully");

                // Handle warnings if any
                if (result.hasWarnings()) {
                    redirectAttributes.addFlashAttribute("infoMessage",
                            "Entry updated with notes: " + String.join(", ", result.getWarnings()));
                }

                LoggerUtil.info(this.getClass(), String.format("Successfully updated entry %d for %s", entryId, currentUser.getUsername()));
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "updating entry");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);

                LoggerUtil.warn(this.getClass(), String.format("Failed to update entry %d for %s: %s",
                        entryId, currentUser.getUsername(), result.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error updating check entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while updating entry");
        }

        return "redirect:/user/check-register?year=" + year + "&month=" + month;
    }

    /**
     * Delete check entry - REFACTORED with ServiceResult handling
     */
    @PostMapping("/delete")
    public String deleteEntry(@AuthenticationPrincipal UserDetails userDetails, @RequestParam Integer entryId,
                              @RequestParam Integer year, @RequestParam Integer month, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Deleting check entry " + entryId + " at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Delete through service using ServiceResult pattern
            ServiceResult<Void> result = checkRegisterService.deleteUserEntry(
                    currentUser.getUsername(), currentUser.getUserId(), entryId, year, month);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", "Check entry deleted successfully");
                LoggerUtil.info(this.getClass(), String.format("Successfully deleted entry %d for %s", entryId, currentUser.getUsername()));
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "deleting entry");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);

                LoggerUtil.warn(this.getClass(), String.format("Failed to delete entry %d for %s: %s",
                        entryId, currentUser.getUsername(), result.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error deleting check entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while deleting entry");
        }

        return "redirect:/user/check-register?year=" + year + "&month=" + month;
    }

    /**
     * Update from Team Lead - Force cache invalidation and perform merge with team lead register
     * This endpoint clears the cache for the specified month and triggers a fresh merge with team lead updates
     */
    @PostMapping("/update-from-team-lead")
    public String updateFromTeamLead(@AuthenticationPrincipal UserDetails userDetails,
                                     @RequestParam Integer year,
                                     @RequestParam Integer month,
                                     RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Update from Team Lead requested at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return "redirect:/login";
            }

            LoggerUtil.info(this.getClass(), String.format("Invalidating check register cache and performing merge for %s - %d/%d",
                    currentUser.getUsername(), year, month));

            // Step 1: Clear the cache for this month to force reload
            try {
                checkRegisterService.getRegisterCheckCacheService().clearMonth(currentUser.getUsername(), year, month);
                LoggerUtil.info(this.getClass(), String.format("Successfully cleared check register cache for %s - %d/%d",
                        currentUser.getUsername(), year, month));
            } catch (Exception cacheException) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to clear cache: %s", cacheException.getMessage()));
                // Continue anyway - the merge will still work
            }

            // Step 2: Perform the merge with team lead register
            ServiceResult<List<RegisterCheckEntry>> mergeResult =
                    checkRegisterService.loadAndMergeUserLoginEntries(currentUser.getUsername(), currentUser.getUserId(), year, month);

            if (mergeResult.isSuccess()) {
                List<RegisterCheckEntry> mergedEntries = mergeResult.getData();
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("Successfully updated with team lead changes. %d entries processed.", mergedEntries.size()));

                LoggerUtil.info(this.getClass(), String.format("Successfully merged %d entries for %s - %d/%d",
                        mergedEntries.size(), currentUser.getUsername(), year, month));

                // Handle warnings if any
                if (mergeResult.hasWarnings()) {
                    redirectAttributes.addFlashAttribute("infoMessage",
                            "Update completed with notes: " + String.join(", ", mergeResult.getWarnings()));
                }
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(mergeResult, "updating from team lead");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);

                LoggerUtil.error(this.getClass(), String.format("Failed to merge for %s - %d/%d: %s",
                        currentUser.getUsername(), year, month, mergeResult.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error updating from team lead: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while updating from team lead");
        }

        return "redirect:/user/check-register?year=" + year + "&month=" + month;
    }

    /**
     * Search across all check register entries - REFACTORED with ServiceResult handling
     */
    @GetMapping("/search")
    public ResponseEntity<List<RegisterCheckEntry>> performSearch(@AuthenticationPrincipal UserDetails userDetails, @RequestParam() String query) {

        try {
            LoggerUtil.info(this.getClass(), "Performing check register search at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Perform search using ServiceResult pattern
            ServiceResult<List<RegisterCheckEntry>> searchResult = checkRegisterService.performFullRegisterSearch(
                    currentUser.getUsername(), currentUser.getUserId(), query);

            if (searchResult.isSuccess()) {
                List<RegisterCheckEntry> results = searchResult.getData();
                LoggerUtil.info(this.getClass(), String.format("Search completed successfully, found %d results", results.size()));
                return ResponseEntity.ok(results);
            } else {
                LoggerUtil.warn(this.getClass(), String.format("Search failed for %s: %s",
                        currentUser.getUsername(), searchResult.getErrorMessage()));

                if (searchResult.isValidationError()) {
                    return ResponseEntity.badRequest().build();
                } else if (searchResult.isUnauthorized()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error performing check register search: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export to Excel - ENHANCED with ServiceResult handling
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(@AuthenticationPrincipal UserDetails userDetails, @RequestParam int year, @RequestParam int month) {

        try {
            LoggerUtil.info(this.getClass(), "Exporting check register to Excel at " + getStandardCurrentDateTime());

            // Get the user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Load entries using ServiceResult pattern
            ServiceResult<List<RegisterCheckEntry>> entriesResult = checkRegisterService.loadMonthEntries(
                    currentUser.getUsername(), currentUser.getUserId(), year, month);

            if (entriesResult.isFailure()) {
                LoggerUtil.error(this.getClass(), String.format("Failed to load entries for export for %s: %s",
                        currentUser.getUsername(), entriesResult.getErrorMessage()));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            List<RegisterCheckEntry> entries = entriesResult.getData();

            // Generate Excel using our exporter
            byte[] excelData = checkRegisterExcelExporter.exportToExcel(currentUser, entries, year, month);

            LoggerUtil.info(this.getClass(), String.format("Successfully exported %d entries to Excel for %s",
                    entries.size(), currentUser.getUsername()));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"check_register_%d_%02d.xlsx\"", year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error exporting check register to Excel: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
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

    /**
     * Helper method to load check values for a user
     */
    private void loadCheckValuesForUser(User user) {
        try {
            // Get the user's check values
            UsersCheckValueEntry userCheckValues = checkValuesService.getUserCheckValues(user.getUsername(), user.getUserId());

            if (userCheckValues != null && userCheckValues.getCheckValuesEntry() != null) {
                // Log the actual values being loaded
                LoggerUtil.info(this.getClass(), String.format("Loading check values for %s: workUnitsPerHour=%f",
                        user.getUsername(), userCheckValues.getCheckValuesEntry().getWorkUnitsPerHour()));

                // Cache the check values
                checkValuesCacheManager.cacheCheckValues(user.getUsername(), userCheckValues.getCheckValuesEntry());
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
        boolean hasRole = role.contains(SecurityConstants.ROLE_TL_CHECKING) ||
                role.contains(SecurityConstants.ROLE_USER_CHECKING) ||
                role.equals(SecurityConstants.ROLE_CHECKING);

        LoggerUtil.info(this.getClass(), String.format("ROLE CHECK: User %s has role '%s', checking role result: %b",
                user.getUsername(), role, hasRole));

        return hasRole;
    }
}