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
import com.ctgraphdep.model.dto.CheckRegisterSearchResultDTO;
import com.ctgraphdep.register.service.CheckRegisterService;
import com.ctgraphdep.register.service.CheckValuesService;
import com.ctgraphdep.register.service.ExcelCheckRegisterProcessingService;
import com.ctgraphdep.service.*;
import com.ctgraphdep.service.cache.CheckValuesCacheManager;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.model.CheckValuesEntry;
import com.ctgraphdep.utils.CheckRegisterWithCalculationExporter;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final CheckRegisterWithCalculationExporter checkRegisterWithCalculationExporter;
    private final WorkScheduleService workScheduleService;
    private final CheckValuesCacheManager checkValuesCacheManager;
    private final CheckValuesService checkValuesService;
    private final ExcelCheckRegisterProcessingService excelProcessingService;

    public CheckRegisterController(UserService userService, FolderStatus folderStatus, CheckRegisterService checkRegisterService, TimeValidationService timeValidationService,
                                   CheckRegisterWithCalculationExporter checkRegisterWithCalculationExporter,
                                   WorkScheduleService workScheduleService, CheckValuesCacheManager checkValuesCacheManager,
                                   CheckValuesService checkValuesService,
                                   ExcelCheckRegisterProcessingService excelProcessingService) {
        super(userService, folderStatus, timeValidationService);
        this.checkRegisterService = checkRegisterService;
        this.checkRegisterWithCalculationExporter = checkRegisterWithCalculationExporter;
        this.workScheduleService = workScheduleService;
        this.checkValuesCacheManager = checkValuesCacheManager;
        this.checkValuesService = checkValuesService;
        this.excelProcessingService = excelProcessingService;
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

            // Calculate standard work hours using cache-aware method
            double standardWorkHours = workScheduleService.calculateStandardWorkHoursWithCache(
                    currentUser.getUsername(), currentUser.getUserId(), selectedYear, selectedMonth);

            // Calculate live work hours using cache-aware method
            double liveWorkHours = workScheduleService.calculateLiveWorkHours(
                    currentUser.getUsername(), currentUser.getUserId(), selectedYear, selectedMonth);

            // Get target work units per hour from cache (CheckValuesCacheManager handles defaults internally)
            double targetWorkUnitsPerHour = checkValuesCacheManager.getTargetWorkUnitsPerHour(currentUser.getUsername());
            LoggerUtil.info(this.getClass(), String.format("Target work units/hour for %s: %.2f", currentUser.getUsername(), targetWorkUnitsPerHour));

            model.addAttribute("standardWorkHours", standardWorkHours);
            model.addAttribute("liveWorkHours", liveWorkHours);
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
            model.addAttribute("liveWorkHours", 0);
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
     * Also invalidates check values cache to ensure latest values are loaded
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

            LoggerUtil.info(this.getClass(), String.format("Invalidating caches and performing merge for %s - %d/%d",
                    currentUser.getUsername(), year, month));

            // Step 1: Clear the check register cache for this month to force reload
            try {
                checkRegisterService.getRegisterCheckCacheService().clearMonth(currentUser.getUsername(), year, month);
                LoggerUtil.info(this.getClass(), String.format("Successfully cleared check register cache for %s - %d/%d",
                        currentUser.getUsername(), year, month));
            } catch (Exception cacheException) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to clear check register cache: %s", cacheException.getMessage()));
                // Continue anyway - the merge will still work
            }

            // Step 2: Invalidate check values cache to force reload of latest values
            try {
                checkValuesCacheManager.invalidateCache(currentUser.getUsername());
                LoggerUtil.info(this.getClass(), String.format("Successfully invalidated check values cache for %s",
                        currentUser.getUsername()));

                // Reload check values immediately
                loadCheckValuesForUser(currentUser);
                LoggerUtil.info(this.getClass(), String.format("Successfully reloaded check values for %s",
                        currentUser.getUsername()));
            } catch (Exception valuesCacheException) {
                LoggerUtil.warn(this.getClass(), String.format("Failed to invalidate/reload check values cache: %s",
                        valuesCacheException.getMessage()));
                // Continue anyway - the merge will still work
            }

            // Step 3: Perform the merge with team lead register
            ServiceResult<List<RegisterCheckEntry>> mergeResult =
                    checkRegisterService.loadAndMergeUserLoginEntries(currentUser.getUsername(), currentUser.getUserId(), year, month);

            if (mergeResult.isSuccess()) {
                List<RegisterCheckEntry> mergedEntries = mergeResult.getData();
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("Successfully updated with team lead changes. %d entries processed. Check values refreshed.", mergedEntries.size()));

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
     * Search across all check register entries - REFACTORED with ServiceResult handling and DTO
     */
    @GetMapping("/search")
    public ResponseEntity<List<CheckRegisterSearchResultDTO>> performSearch(@AuthenticationPrincipal UserDetails userDetails, @RequestParam() String query) {

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

                // Convert to DTO (hides internal fields like entryId and adminSync)
                List<CheckRegisterSearchResultDTO> dtoResults = results.stream()
                        .map(CheckRegisterSearchResultDTO::new)
                        .collect(Collectors.toList());

                LoggerUtil.info(this.getClass(), String.format("Search completed successfully, found %d results", dtoResults.size()));
                return ResponseEntity.ok(dtoResults);
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
     * Export to Excel with two sheets (Registry + Calculation) - ENHANCED with ServiceResult handling
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

            // Get check values for this user
            UsersCheckValueEntry userCheckValues = checkValuesService.getUserCheckValues(currentUser.getUsername(), currentUser.getUserId());
            CheckValuesEntry checkValues;

            if (userCheckValues != null && userCheckValues.getCheckValuesEntry() != null) {
                checkValues = userCheckValues.getCheckValuesEntry();
                LoggerUtil.info(this.getClass(), "Using check values for user " + currentUser.getUsername());
            } else {
                // Use default values
                checkValues = CheckValuesEntry.createDefault();
                LoggerUtil.warn(this.getClass(), "Using default check values for user " + currentUser.getUsername());
            }

            // Generate Excel with two sheets using the new exporter
            byte[] excelData = checkRegisterWithCalculationExporter.exportToExcel(currentUser, entries, checkValues, year, month);

            if (excelData.length == 0) {
                LoggerUtil.error(this.getClass(), "Excel export returned empty byte array");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            LoggerUtil.info(this.getClass(), String.format("Successfully exported %d entries to Excel for %s",
                    entries.size(), currentUser.getUsername()));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"check_register_%s_%d_%02d.xlsx\"",
                            currentUser.getUsername(), year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error exporting check register to Excel: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Upload Excel file with check register entries
     * POST /user/check-register/upload-excel
     */
    @PostMapping("/upload-excel")
    public String uploadExcelFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("excelFile") org.springframework.web.multipart.MultipartFile file,
            @RequestParam Integer year,
            @RequestParam Integer month,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        try {
            // Get current user
            User currentUser = getUserService().getUserByUsername(userDetails.getUsername()).orElse(null);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "User not found");
                return String.format("redirect:/user/check-register?year=%d&month=%d", year, month);
            }

            String username = currentUser.getUsername();
            Integer userId = currentUser.getUserId();

            LoggerUtil.info(this.getClass(), String.format(
                    "Uploading Excel file for user %s, year: %d, month: %d",
                    username, year, month));

            // Validate file
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Please select an Excel file to upload");
                return String.format("redirect:/user/check-register?year=%d&month=%d", year, month);
            }

            // Process Excel file using the injected service
            // User uploads always get USER_INPUT status
            ServiceResult<List<RegisterCheckEntry>> parseResult = this.excelProcessingService.processExcelFile(
                    file, username, userId, username, MergingStatusConstants.USER_INPUT);

            // If validation failed, show error message with link to download error details
            if (parseResult.isFailure()) {
                String errorMsg = parseResult.getErrorMessage();

                // For validation errors, provide detailed error display
                if ("validation_failed".equals(parseResult.getErrorCode())) {
                    // Store errors in session for download endpoint
                    request.getSession().setAttribute("excel_validation_errors", errorMsg);
                    request.getSession().setAttribute("excel_validation_username", username);

                    // Extract first few errors for inline display
                    String[] errorLines = errorMsg.split("\n");
                    String summary = errorLines.length > 2 ? errorLines[0] + "\n" + errorLines[1] : errorMsg;

                    redirectAttributes.addFlashAttribute("errorMessage", summary);
                    redirectAttributes.addFlashAttribute("validationErrors", errorMsg);
                    redirectAttributes.addFlashAttribute("showDownloadLink", true);

                    LoggerUtil.info(this.getClass(), String.format(
                            "Validation failed for %s - errors stored in session, showDownloadLink=true", username));
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Failed to process Excel file: " + errorMsg);
                }
                return String.format("redirect:/user/check-register?year=%d&month=%d", year, month);
            }

            List<RegisterCheckEntry> entries = parseResult.getData();

            if (entries.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage", "No valid entries found in Excel file");
                return String.format("redirect:/user/check-register?year=%d&month=%d", year, month);
            }

            // Batch save all entries at once (avoids file locking issues)
            ServiceResult<Integer> batchResult = checkRegisterService.batchSaveUserEntries(username, userId, entries);

            if (batchResult.isSuccess()) {
                int savedCount = batchResult.getData();
                if (batchResult.hasWarnings()) {
                    // Has warnings - some entries may have failed
                    String warningMsg = String.join("; ", batchResult.getWarnings());
                    redirectAttributes.addFlashAttribute("warningMessage", warningMsg);
                } else {
                    // All entries succeeded
                    redirectAttributes.addFlashAttribute("successMessage",
                            String.format("Successfully imported %d entries from Excel file", savedCount));
                }
                LoggerUtil.info(this.getClass(), String.format(
                        "Excel upload completed for %s: %d entries saved", username, savedCount));
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Failed to import entries: " + batchResult.getErrorMessage());
                LoggerUtil.error(this.getClass(), String.format(
                        "Excel upload failed for %s: %s", username, batchResult.getErrorMessage()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error uploading Excel file: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An unexpected error occurred while uploading Excel file: " + e.getMessage());
        }

        return String.format("redirect:/user/check-register?year=%d&month=%d", year, month);
    }

    /**
     * Download Excel validation errors as text file
     * GET /user/check-register/download-errors
     */
    @GetMapping("/download-errors")
    public ResponseEntity<byte[]> downloadValidationErrors(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }

            String errors = (String) session.getAttribute("excel_validation_errors");
            String username = (String) session.getAttribute("excel_validation_username");

            if (errors == null || username == null) {
                return ResponseEntity.notFound().build();
            }

            // Clear session attributes after retrieving
            session.removeAttribute("excel_validation_errors");
            session.removeAttribute("excel_validation_username");

            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("Excel_Validation_Errors_%s_%s.txt", username, timestamp);

            // Build error file content
            StringBuilder content = new StringBuilder();
            content.append("Excel Check Register Validation Errors\n");
            content.append("=====================================\n");
            content.append("User: ").append(username).append("\n");
            content.append("Date: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
            content.append("\n");
            content.append(errors);
            content.append("\n\n");
            content.append("Please correct these errors in your Excel file and upload again.\n");

            byte[] fileContent = content.toString().getBytes(StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(fileContent.length);

            LoggerUtil.info(this.getClass(), String.format(
                    "Validation errors file downloaded by %s: %s", username, filename));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error generating validation errors file: " + e.getMessage(), e);
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