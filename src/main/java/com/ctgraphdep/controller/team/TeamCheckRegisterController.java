package com.ctgraphdep.controller.team;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ApprovalStatusType;
import com.ctgraphdep.enums.CheckType;
import com.ctgraphdep.enums.CheckingStatus;
import com.ctgraphdep.model.*;
import com.ctgraphdep.register.service.CheckRegisterService;
import com.ctgraphdep.service.CheckValuesService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.WorkScheduleService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.jetbrains.annotations.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;

/**
 * Controller for team lead operations on check registers - UPDATED FOR REFACTORED SERVICE
 * Provides functionality for team leads to view and manage check registers of users
 * Updated to handle new ServiceResult return types from refactored service layer
 */
@Controller
@RequestMapping("/team/check-register")
@PreAuthorize("hasAnyRole('ROLE_TL_CHECKING', 'ROLE_ADMIN')")
public class TeamCheckRegisterController extends BaseController {

    private final CheckRegisterService checkRegisterService;
    private final CheckValuesService checkValuesService;
    private final WorkScheduleService workScheduleService;

    public TeamCheckRegisterController(UserService userService, FolderStatus folderStatus, TimeValidationService timeValidationService, CheckRegisterService checkRegisterService,
                                       CheckValuesService checkValuesService, WorkScheduleService workScheduleService) {
        super(userService, folderStatus, timeValidationService);
        this.checkRegisterService = checkRegisterService;
        this.checkValuesService = checkValuesService;
        this.workScheduleService = workScheduleService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Display the team check register page with user tabs - UPDATED for ServiceResult
     * Shows a list of all checking users and allows selecting one to view/edit their register
     */
    @GetMapping
    public String showTeamCheckRegister(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
                                        @RequestParam(required = false) String selectedUser, @RequestParam(required = false) Integer selectedUserId, Model model) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing team check register page at " + getStandardCurrentDateTime());

            // Get current team lead user
            User teamLeader = prepareUserAndCommonModelAttributes(userDetails, model);
            if (teamLeader == null) {
                return "redirect:/login";
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Get all users with checking roles using ServiceResult
            ServiceResult<List<User>> checkUsersResult = checkRegisterService.getAllCheckUsers();
            if (checkUsersResult.isFailure()) {
                model.addAttribute("errorMessage", "Failed to load checking users: " + checkUsersResult.getErrorMessage());
                model.addAttribute("checkUsers", new ArrayList<>());
            } else {
                model.addAttribute("checkUsers", checkUsersResult.getData());
            }

            model.addAttribute("userName", teamLeader.getName());

            // Set year and month
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);

            // Add check type and approval status values
            model.addAttribute("checkTypes", CheckType.getValues());
            model.addAttribute("approvalStatusTypes", ApprovalStatusType.getValues());

            // Initialize needsInitialization to false by default
            model.addAttribute("needsInitialization", false);

            // Load selected user's entries if specified
            if (selectedUser != null && selectedUserId != null && checkUsersResult.isSuccess()) {
                LoggerUtil.info(this.getClass(), String.format("Loading check register for selected user %s (ID: %d) for %d/%d", selectedUser, selectedUserId, selectedYear, selectedMonth));

                loadSelectedUserData(selectedUser, selectedUserId, selectedYear, selectedMonth, checkUsersResult.getData(), model);

                // Check if entries were loaded
                Object entries = model.getAttribute("entries");
                if (entries instanceof List) {
                    LoggerUtil.info(this.getClass(), String.format("Loaded %d entries for user %s", ((List<?>) entries).size(), selectedUser));
                } else {
                    LoggerUtil.warn(this.getClass(), "No entries loaded for user " + selectedUser);
                }
            }

            return "user/team-check-register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading team check register page: " + e.getMessage(), e);
            model.addAttribute("errorMessage", "Error loading team check register: " + e.getMessage());
            // Provide fallback data
            model.addAttribute("checkUsers", new ArrayList<>());
            model.addAttribute("entries", new ArrayList<>());
            model.addAttribute("needsInitialization", false);
            return "user/team-check-register";
        }
    }

    /**
     * Helper method to load the selected user's data - UPDATED for ServiceResult
     * Enhanced to handle case where team register isn't initialized but user has entries
     */
    private void loadSelectedUserData(String username, Integer userId, int selectedYear, int selectedMonth, List<User> checkUsers, Model model) {
        // Set default values to prevent null booleans
        model.addAttribute("needsInitialization", false);
        model.addAttribute("showRegisterContent", false);
        model.addAttribute("showMarkAllCheckedButton", false);
        model.addAttribute("entries", new ArrayList<>());

        User selectedUserObj = checkUsers.stream()
                .filter(u -> u.getUsername().equals(username) && u.getUserId().equals(userId))
                .findFirst()
                .orElse(null);

        if (selectedUserObj != null) {
            model.addAttribute("selectedUser", selectedUserObj);

            // First, check if team check register exists using ServiceResult
            ServiceResult<List<RegisterCheckEntry>> teamEntriesResult = checkRegisterService.loadTeamCheckRegister(username, userId, selectedYear, selectedMonth);

            if (teamEntriesResult.isFailure()) {
                // Handle error case
                model.addAttribute("errorMessage", "Failed to load team register: " + teamEntriesResult.getErrorMessage());
                model.addAttribute("needsInitialization", true);
                model.addAttribute("showRegisterContent", false);
                return;
            }

            List<RegisterCheckEntry> teamEntries = teamEntriesResult.getData();
            boolean isInitialized = teamEntries != null && !teamEntries.isEmpty();

            // Handle warnings from service
            if (teamEntriesResult.hasWarnings()) {
                // Check if warning indicates initialization needed
                String warningsText = String.join(", ", teamEntriesResult.getWarnings());
                if (warningsText.contains("Initialization required")) {
                    model.addAttribute("needsInitialization", true);
                    model.addAttribute("showRegisterContent", false);
                    model.addAttribute("infoMessage", warningsText);
                    return;
                } else {
                    model.addAttribute("infoMessage", warningsText);
                }
            }

            if (isInitialized) {
                // Register is initialized, show all content
                model.addAttribute("entries", teamEntries);
                model.addAttribute("needsInitialization", false);
                model.addAttribute("showRegisterContent", true);
                model.addAttribute("showMarkAllCheckedButton", true);

                // Load check values directly for the selected user
                try {
                    // Get check values for the selected user directly from the service
                    UsersCheckValueEntry userCheckValues = checkValuesService.getUserCheckValues(username, userId);

                    if (userCheckValues != null && userCheckValues.getCheckValuesEntry() != null) {
                        CheckValuesEntry checkValues = userCheckValues.getCheckValuesEntry();

                        // Create a map of check type values for JavaScript
                        Map<String, Double> checkTypeValues = getStringDoubleMap(checkValues);

                        model.addAttribute("checkTypeValues", checkTypeValues);

                        // Also add the target work units per hour for metrics
                        model.addAttribute("targetWorkUnitsPerHour", checkValues.getWorkUnitsPerHour());

                        LoggerUtil.info(this.getClass(), "Added check values for user " + username + " with workUnitsPerHour=" + checkValues.getWorkUnitsPerHour());
                    } else {
                        LoggerUtil.warn(this.getClass(), "No check values found for user " + username + ", using default values");

                        // Use default values if user values not found
                        model.addAttribute("targetWorkUnitsPerHour", 4.5);
                    }

                    // Calculate standard work hours for the selected user
                    try {
                        int standardWorkHours = workScheduleService.calculateStandardWorkHours(username, selectedYear, selectedMonth);
                        model.addAttribute("standardWorkHours", standardWorkHours);
                        LoggerUtil.info(this.getClass(), "Calculated standard work hours for " + username + ": " + standardWorkHours);
                    } catch (Exception e) {
                        LoggerUtil.warn(this.getClass(), "Error calculating standard work hours for " + username + ": " + e.getMessage());
                        // Use a default value as fallback (160 hours is common for a full month)
                        model.addAttribute("standardWorkHours", 160);
                    }

                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(), "Error loading check values for user " + username + ": " + e.getMessage());

                    // Use default value as fallback
                    model.addAttribute("targetWorkUnitsPerHour", 4.5);
                    model.addAttribute("standardWorkHours", 160);
                }
            } else {
                // Register needs initialization, only show init button
                model.addAttribute("needsInitialization", true);
                model.addAttribute("showRegisterContent", false);

                // Check if user has entries using ServiceResult
                try {
                    ServiceResult<List<RegisterCheckEntry>> userEntriesResult = checkRegisterService.loadUserEntriesDirectly(username, userId, selectedYear, selectedMonth);
                    if (userEntriesResult.isSuccess()) {
                        List<RegisterCheckEntry> userEntries = userEntriesResult.getData();
                        if (userEntries == null || userEntries.isEmpty()) {
                            LoggerUtil.info(this.getClass(), "No user entries found for " + username);
                        } else {
                            LoggerUtil.info(this.getClass(), String.format("Found %d user entries for %s, team register can be initialized", userEntries.size(), username));
                        }
                    } else {
                        LoggerUtil.error(this.getClass(), "Error checking user entries: " + userEntriesResult.getErrorMessage());
                        model.addAttribute("warningMessage", "Could not check user entries: " + userEntriesResult.getErrorMessage());
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error checking user entries: " + e.getMessage());
                    model.addAttribute("warningMessage", "Could not check user entries");
                }
            }
        }
    }

    private static @NotNull Map<String, Double> getStringDoubleMap(CheckValuesEntry checkValues) {
        Map<String, Double> checkTypeValues = new HashMap<>();

        // Manual mapping to ensure we get all values
        checkTypeValues.put("LAYOUT", checkValues.getLayoutValue());
        checkTypeValues.put("KIPSTA LAYOUT", checkValues.getKipstaLayoutValue());
        checkTypeValues.put("LAYOUT CHANGES", checkValues.getLayoutChangesValue());
        checkTypeValues.put("GPT", checkValues.getGptArticlesValue());
        checkTypeValues.put("PRODUCTION", checkValues.getProductionValue());
        checkTypeValues.put("REORDER", checkValues.getReorderValue());
        checkTypeValues.put("SAMPLE", checkValues.getSampleValue());
        checkTypeValues.put("OMS PRODUCTION", checkValues.getOmsProductionValue());
        checkTypeValues.put("KIPSTA PRODUCTION", checkValues.getKipstaProductionValue());
        return checkTypeValues;
    }

    /**
     * Initialize team check register from user register - UNCHANGED (already uses ServiceResult)
     * Enhanced with ServiceResult handling
     */
    @PostMapping("/initialize")
    public String initializeTeamCheckRegister(@AuthenticationPrincipal UserDetails userDetails,
                                              @RequestParam String username,
                                              @RequestParam Integer userId,
                                              @RequestParam Integer year,
                                              @RequestParam Integer month,
                                              RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Initializing team check register for " + username);

            // Initialize team check register using ServiceResult pattern
            ServiceResult<List<RegisterCheckEntry>> result = checkRegisterService.initializeTeamCheckRegister(
                    username, userId, year, month);

            if (result.isSuccess()) {
                List<RegisterCheckEntry> entries = result.getData();

                if (entries.isEmpty()) {
                    redirectAttributes.addFlashAttribute("warningMessage",
                            "No entries found in user register. An empty team register has been created.");
                } else {
                    redirectAttributes.addFlashAttribute("successMessage",
                            String.format("Initialized team check register with %d entries", entries.size()));
                }

                // Handle warnings if any
                if (result.hasWarnings()) {
                    redirectAttributes.addFlashAttribute("infoMessage",
                            "Initialization completed with notes: " + String.join(", ", result.getWarnings()));
                }

            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "initialization");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error initializing team check register: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred during initialization");
        }

        return getRedirectUrl(username, userId, year, month);
    }

    /**
     * Mark all entries in the team check register as TL_CHECK_DONE - UNCHANGED (already uses ServiceResult)
     * Enhanced with ServiceResult handling
     */
    @PostMapping("/mark-all-checked")
    public String markAllEntriesAsChecked(@AuthenticationPrincipal UserDetails userDetails,
                                          @RequestParam String username,
                                          @RequestParam Integer userId,
                                          @RequestParam Integer year,
                                          @RequestParam Integer month,
                                          RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Marking all entries as checked for " + username);

            // Call service method with ServiceResult pattern
            ServiceResult<List<RegisterCheckEntry>> result = checkRegisterService.markAllEntriesAsChecked(
                    username, userId, year, month);

            if (result.isSuccess()) {
                List<RegisterCheckEntry> updatedEntries = result.getData();

                if (updatedEntries.isEmpty()) {
                    redirectAttributes.addFlashAttribute("warningMessage", "No entries found to mark as checked.");
                } else {
                    redirectAttributes.addFlashAttribute("successMessage",
                            String.format("Marked %d entries as checked", updatedEntries.size()));
                }

                // Handle warnings (e.g., some entries were skipped)
                if (result.hasWarnings()) {
                    redirectAttributes.addFlashAttribute("infoMessage",
                            String.join(", ", result.getWarnings()));
                }

            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "marking entries as checked");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error marking entries as checked: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while marking entries");
        }

        return getRedirectUrl(username, userId, year, month);
    }

    /**
     * Create new entry in team check register - UNCHANGED (already uses ServiceResult)
     * Enhanced with ServiceResult handling and early validation
     */
    @PostMapping("/entry")
    public String createEntry(@AuthenticationPrincipal UserDetails userDetails,
                              @RequestParam String username,
                              @RequestParam(required = false) Integer userId,
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
            LoggerUtil.info(this.getClass(), "Creating new entry for " + username);

            // Early validation at controller level
            ServiceResult<Integer> userIdResult = resolveUserId(username, userId);
            if (userIdResult.isFailure()) {
                redirectAttributes.addFlashAttribute("errorMessage", userIdResult.getErrorMessage());
                return getRedirectUrl(username, userId, year, month);
            }
            userId = userIdResult.getData();

            // Additional controller-level validation
            List<String> validationErrors = validateEntryParameters(date, omsId, designerName, checkType,
                    articleNumbers, filesNumbers, approvalStatus);

            if (!validationErrors.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Validation errors: " + String.join(", ", validationErrors));
                return getRedirectUrl(username, userId, year, month);
            }

            // Create entry through service (true = team lead)
            RegisterCheckEntry entry = checkRegisterService.createEntry(
                    true, null, date, omsId, productionId, designerName,
                    checkType, articleNumbers, filesNumbers, errorDescription,
                    approvalStatus, orderValue);

            // Save through service using ServiceResult pattern
            ServiceResult<RegisterCheckEntry> result = checkRegisterService.saveEntry(true, username, userId, entry);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", "Entry created successfully");

                // Handle warnings (e.g., sorting issues)
                if (result.hasWarnings()) {
                    redirectAttributes.addFlashAttribute("infoMessage",
                            "Entry created with notes: " + String.join(", ", result.getWarnings()));
                }
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "creating entry");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error creating entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while creating entry");
        }

        return getRedirectUrl(username, userId, year, month);
    }

    /**
     * Update entry in team check register - UNCHANGED (already uses ServiceResult)
     * Enhanced with ServiceResult handling
     */
    @PostMapping("/entry/{entryId}")
    public String updateEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer entryId,
            @RequestParam String username,
            @RequestParam(required = false) Integer userId,
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
            LoggerUtil.info(this.getClass(), "Updating entry " + entryId + " for " + username);

            // Resolve userId if not provided
            ServiceResult<Integer> userIdResult = resolveUserId(username, userId);
            if (userIdResult.isFailure()) {
                redirectAttributes.addFlashAttribute("errorMessage", userIdResult.getErrorMessage());
                return getRedirectUrl(username, userId, year, month);
            }
            userId = userIdResult.getData();

            // Validate entry parameters
            List<String> validationErrors = validateEntryParameters(date, omsId, designerName, checkType,
                    articleNumbers, filesNumbers, approvalStatus);

            if (!validationErrors.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Validation errors: " + String.join(", ", validationErrors));
                return getRedirectUrl(username, userId, year, month);
            }

            // Create entry with team lead status
            RegisterCheckEntry entry = createEntryWithTeamLeadStatus(entryId, date, omsId, productionId,
                    designerName, checkType, articleNumbers, filesNumbers, errorDescription, approvalStatus, orderValue);

            // Update through service using ServiceResult pattern
            ServiceResult<RegisterCheckEntry> result = checkRegisterService.updateTeamEntry(username, userId, entry, year, month);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", "Entry updated successfully");

                // Handle warnings
                if (result.hasWarnings()) {
                    redirectAttributes.addFlashAttribute("infoMessage",
                            "Entry updated with notes: " + String.join(", ", result.getWarnings()));
                }
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "updating entry");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error updating entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while updating entry");
        }

        return getRedirectUrl(username, userId, year, month);
    }

    /**
     * Helper method to create an entry with team lead status
     */
    private RegisterCheckEntry createEntryWithTeamLeadStatus(
            Integer entryId, LocalDate date, String omsId, String productionId, String designerName, String checkType,
            Integer articleNumbers, Integer filesNumbers, String errorDescription, String approvalStatus, Double orderValue) {

        return RegisterCheckEntry.builder()
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
                .adminSync(CheckingStatus.TL_EDITED.name())
                .build();
    }

    /**
     * Mark entry for deletion - UNCHANGED (already uses ServiceResult)
     * Enhanced with ServiceResult handling
     */
    @PostMapping("/delete")
    public String markEntryForDeletion(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer entryId,
            @RequestParam String username,
            @RequestParam(required = false) Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Marking entry " + entryId + " for deletion for " + username);

            // Resolve userId if not provided
            ServiceResult<Integer> userIdResult = resolveUserId(username, userId);
            if (userIdResult.isFailure()) {
                redirectAttributes.addFlashAttribute("errorMessage", userIdResult.getErrorMessage());
                return getRedirectUrl(username, userId, year, month);
            }
            userId = userIdResult.getData();

            // Mark entry for deletion using ServiceResult pattern
            ServiceResult<Void> result = checkRegisterService.markEntryForDeletion(username, userId, entryId, year, month);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", "Entry marked for deletion");
            } else {
                // Handle different error types gracefully
                String errorMessage = handleServiceError(result, "marking entry for deletion");
                redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Unexpected error marking entry for deletion: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred while marking entry for deletion");
        }

        return getRedirectUrl(username, userId, year, month);
    }

// ========================================================================
// HELPER METHODS FOR ERROR HANDLING AND VALIDATION (UNCHANGED)
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
     * Resolves userId from username if not provided
     */
    private ServiceResult<Integer> resolveUserId(String username, Integer userId) {
        if (userId != null) {
            return ServiceResult.success(userId);
        }

        try {
            User user = getUserService().getUserByUsername(username).orElse(null);
            if (user != null) {
                LoggerUtil.info(this.getClass(), "Retrieved userId " + user.getUserId() + " for username " + username);
                return ServiceResult.success(user.getUserId());
            } else {
                return ServiceResult.notFound("User not found: " + username, "user_not_found");
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resolving userId for username " + username + ": " + e.getMessage(), e);
            return ServiceResult.systemError("Error resolving user information", "user_resolution_failed");
        }
    }

    /**
     * Controller-level validation for entry parameters
     */
    private List<String> validateEntryParameters(LocalDate date, String omsId, String designerName,
                                                 String checkType, Integer articleNumbers, Integer filesNumbers,
                                                 String approvalStatus) {
        List<String> errors = new ArrayList<>();

        if (date == null) {
            errors.add("Date is required");
        }
        if (omsId == null || omsId.trim().isEmpty()) {
            errors.add("OMS ID is required");
        }
        if (designerName == null || designerName.trim().isEmpty()) {
            errors.add("Designer name is required");
        }
        if (checkType == null || checkType.trim().isEmpty()) {
            errors.add("Check type is required");
        }
        if (articleNumbers == null || articleNumbers <= 0) {
            errors.add("Article numbers must be positive");
        }
        if (filesNumbers == null || filesNumbers <= 0) {
            errors.add("File numbers must be positive");
        }
        if (approvalStatus == null || approvalStatus.trim().isEmpty()) {
            errors.add("Approval status is required");
        }

        return errors;
    }

    /**
     * Enhanced helper method to generate the redirect URL with better error handling
     */
    private String getRedirectUrl(String username, Integer userId, Integer year, Integer month) {
        // If userId is null, handle it safely by getting it from the username
        if (userId == null) {
            try {
                ServiceResult<Integer> userIdResult = resolveUserId(username, null);
                if (userIdResult.isSuccess()) {
                    userId = userIdResult.getData();
                } else {
                    // Default to -1 as a fallback if user not found
                    userId = -1;
                    LoggerUtil.warn(this.getClass(), "Could not resolve userId for username: " + username + ", using default value");
                }
            } catch (Exception e) {
                // Default to -1 as a fallback if an error occurs
                userId = -1;
                LoggerUtil.error(this.getClass(), "Error resolving userId for redirect for username: " + username + ", using default value", e);
            }
        }

        return String.format("redirect:/team/check-register?year=%d&month=%d&selectedUser=%s&selectedUserId=%d",
                year, month, username, userId);
    }
}