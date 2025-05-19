package com.ctgraphdep.controller.team;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ApprovalStatusType;
import com.ctgraphdep.enums.CheckType;
import com.ctgraphdep.enums.CheckingStatus;
import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.model.*;
import com.ctgraphdep.service.CheckRegisterService;
import com.ctgraphdep.service.CheckValuesService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.WorkScheduleService;
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
 * Controller for team lead operations on check registers
 * Provides functionality for team leads to view and manage check registers of users
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
     * Display the team check register page with user tabs
     * Shows a list of all checking users and allows selecting one to view/edit their register
     */
    @GetMapping
    public String showTeamCheckRegister(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
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

            // Get all users with checking roles
            List<User> checkUsers = checkRegisterService.getAllCheckUsers();
            model.addAttribute("userName", teamLeader.getName());
            model.addAttribute("checkUsers", checkUsers);

            // Set year and month
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);

            // Add check type and approval status values
            model.addAttribute("checkTypes", CheckType.getValues());
            model.addAttribute("approvalStatusTypes", ApprovalStatusType.getValues());

            // Initialize needsInitialization to false by default
            model.addAttribute("needsInitialization", false);

            // Load selected user's entries if specified
            if (selectedUser != null && selectedUserId != null) {
                LoggerUtil.info(this.getClass(), String.format("Loading check register for selected user %s (ID: %d) for %d/%d", selectedUser, selectedUserId, selectedYear, selectedMonth));

                loadSelectedUserData(selectedUser, selectedUserId, selectedYear, selectedMonth, checkUsers, model);

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
            model.addAttribute("error", "Error loading team check register: " + e.getMessage());
            return "user/team-check-register";
        }
    }

    /**
     * Helper method to load the selected user's data
     * Enhanced to handle case where team register isn't initialized but user has entries
     */
    private void loadSelectedUserData(String username, Integer userId, int selectedYear, int selectedMonth, List<User> checkUsers, Model model) {
        // Set default values to prevent null booleans
        model.addAttribute("needsInitialization", false);
        model.addAttribute("showRegisterContent", false);
        model.addAttribute("showMarkAllCheckedButton", false);
        model.addAttribute("entries", new ArrayList<>());

        User selectedUserObj = checkUsers.stream().filter(u -> u.getUsername().equals(username) && u.getUserId().equals(userId)).findFirst().orElse(null);

        if (selectedUserObj != null) {
            model.addAttribute("selectedUser", selectedUserObj);

            // First, check if team check register exists
            List<RegisterCheckEntry> teamEntries = checkRegisterService.loadTeamCheckRegister(username, userId, selectedYear, selectedMonth);
            boolean isInitialized = teamEntries != null && !teamEntries.isEmpty();

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

                // Check if user has entries
                try {
                    List<RegisterCheckEntry> userEntries = checkRegisterService.loadUserEntriesDirectly(username, userId, selectedYear, selectedMonth);
                    if (userEntries == null || userEntries.isEmpty()) {
                        LoggerUtil.info(this.getClass(), "No user entries found for " + username);
                    }
                } catch (Exception e) {
                    LoggerUtil.error(this.getClass(), "Error checking user entries: " + e.getMessage());
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
     * Initialize team check register from user register
     * Creates a copy of the user's register entries with CHECKING_INPUT status
     * If user register is empty, creates an empty team register
     */
    @PostMapping("/initialize")
    public String initializeTeamCheckRegister(@AuthenticationPrincipal UserDetails userDetails,
                                              @RequestParam String username, @RequestParam Integer userId,
                                              @RequestParam Integer year, @RequestParam Integer month, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Initializing team check register for " + username);

            // Initialize team check register
            List<RegisterCheckEntry> entries = checkRegisterService.initializeTeamCheckRegister(
                    username, userId, year, month);

            if (entries.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage", "No entries found in user register. An empty team register has been created.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", String.format("Initialized team check register with %d entries", entries.size()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing team check register: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error initializing team check register: " + e.getMessage());
        }

        return getRedirectUrl(username, userId, year, month);
    }

    /**
     * Mark all entries in the team check register as TL_CHECK_DONE
     * This indicates that the team lead has reviewed and approved the entries
     */
    @PostMapping("/mark-all-checked")
    public String markAllEntriesAsChecked(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String username, @RequestParam Integer userId, @RequestParam Integer year,
            @RequestParam Integer month, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Marking all entries as checked for " + username);

            // Call service method to update status of all entries
            List<RegisterCheckEntry> updatedEntries = checkRegisterService.markAllEntriesAsChecked(username, userId, year, month);

            if (updatedEntries.isEmpty()) {
                redirectAttributes.addFlashAttribute("warningMessage", "No entries found to mark as checked.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", String.format("Marked %d entries as checked", updatedEntries.size()));
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error marking entries as checked: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error marking entries as checked: " + e.getMessage());
        }

        return getRedirectUrl(username, userId, year, month);
    }

    /**
     * Create new entry in team check register
     */
    @PostMapping("/entry")
    public String createEntry(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String username,
            @RequestParam(required = false) Integer userId,  // Make userId optional
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
            LoggerUtil.info(this.getClass(), "Creating new entry for " + username);

            // Get the user to get userId if not provided in form
            if (userId == null) {
                User user = getUserService().getUserByUsername(username)
                        .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                userId = user.getUserId();
                LoggerUtil.info(this.getClass(), "Retrieved userId " + userId + " for username " + username);
            }

            // Create entry through service (true = team lead)
            RegisterCheckEntry entry = checkRegisterService.createEntry(
                    true, null, date, omsId, productionId, designerName,
                    checkType, articleNumbers, filesNumbers, errorDescription,
                    approvalStatus, orderValue);

            // Save through service
            checkRegisterService.saveEntry(true, username, userId, entry);
            redirectAttributes.addFlashAttribute("successMessage", "Entry created successfully");

        } catch (RegisterValidationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Validation error: " + e.getMessage());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error creating entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error creating entry: " + e.getMessage());
        }

        return getRedirectUrl(username, userId, year, month);
    }

    /**
     * Update entry in team check register
     * Sets status to TL_EDITED
     */
    @PostMapping("/entry/{entryId}")
    public String updateEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer entryId,
            @RequestParam String username,
            @RequestParam(required = false) Integer userId,  // Make userId optional
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

            // Get the user to get userId if not provided in form
            if (userId == null) {
                User user = getUserService().getUserByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                userId = user.getUserId();
                LoggerUtil.info(this.getClass(), "Retrieved userId " + userId + " for username " + username);
            }

            RegisterCheckEntry entry = createEntryWithTeamLeadStatus(entryId, date, omsId, productionId, designerName,
                    checkType, articleNumbers, filesNumbers, errorDescription, approvalStatus, orderValue);

            checkRegisterService.updateTeamEntry(username, userId, entry, year, month);
            redirectAttributes.addFlashAttribute("successMessage", "Entry updated successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating entry: " + e.getMessage());
        }

        return getRedirectUrl(username, userId, year, month);
    }

    /**
     * Helper method to create an entry with team lead status
     */
    private RegisterCheckEntry createEntryWithTeamLeadStatus(Integer entryId, LocalDate date, String omsId,
                                                             String productionId, String designerName,
                                                             String checkType, Integer articleNumbers,
                                                             Integer filesNumbers, String errorDescription,
                                                             String approvalStatus, Double orderValue) {
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
     * Mark entry for deletion
     * Sets status to TL_BLANK which will trigger removal during merge
     */
    @PostMapping("/delete")
    public String markEntryForDeletion(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer entryId,
            @RequestParam String username,
            @RequestParam(required = false) Integer userId,  // Make userId optional
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Marking entry " + entryId + " for deletion for " + username);

            // Get the user to get userId if not provided in form
            if (userId == null) {
                User user = getUserService().getUserByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
                userId = user.getUserId();
                LoggerUtil.info(this.getClass(), "Retrieved userId " + userId + " for username " + username);
            }

            checkRegisterService.markEntryForDeletion(username, userId, entryId, year, month);
            redirectAttributes.addFlashAttribute("successMessage", "Entry marked for deletion");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error marking entry for deletion: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error marking entry for deletion: " + e.getMessage());
        }

        return getRedirectUrl(username, userId, year, month);
    }

    /**
     * Helper method to generate the redirect URL
     */
    private String getRedirectUrl(String username, Integer userId, Integer year, Integer month) {
        // If userId is null, handle it safely by getting it from the username
        if (userId == null) {
            try {
                User user = getUserService().getUserByUsername(username).orElse(null);
                if (user != null) {
                    userId = user.getUserId();
                } else {
                    // Default to -1 as a fallback if user not found
                    userId = -1;
                    LoggerUtil.warn(this.getClass(), "Could not find userId for username: " + username + ", using default value");
                }
            } catch (Exception e) {
                // Default to -1 as a fallback if an error occurs
                userId = -1;
                LoggerUtil.error(this.getClass(), "Error finding userId for username: " + username + ", using default value", e);
            }
        }

        return String.format("redirect:/team/check-register?year=%d&month=%d&selectedUser=%s&selectedUserId=%d", year, month, username, userId);
    }
}