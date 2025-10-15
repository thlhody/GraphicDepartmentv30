package com.ctgraphdep.controller.team;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.model.OperationResult;
import com.ctgraphdep.worktime.service.TeamOperationService;
import lombok.Getter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REFACTORED TeamStatisticsController using command pattern.
 * Fully migrated from deprecated TeamStatisticsService to command-based operations.
 * Key Features:
 * - Uses TeamOperationService for all team operations
 * - Handles OperationResult responses with proper error handling
 * - Consistent with command pattern architecture
 * - Enhanced logging and user feedback
 * - Proper security validation for team leader operations
 */
@Controller
@RequestMapping("/user/stats")
public class TeamStatisticsController extends BaseController {

    private final TeamOperationService teamOperationService;

    public TeamStatisticsController(UserService userService,
                                    FolderStatus folderStatus,
                                    TeamOperationService teamOperationService,
                                    TimeValidationService timeValidationService) {
        super(userService, folderStatus, timeValidationService);
        this.teamOperationService = teamOperationService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Display team statistics page with existing team members
     */
    @GetMapping
    public String showTeamStats(@AuthenticationPrincipal UserDetails userDetails,
                                @RequestParam(required = false) Integer year,
                                @RequestParam(required = false) Integer month,
                                Model model) {
        try {
            // Validate team leader access
            String accessCheck = validateTeamLeaderAccess(userDetails);
            if (accessCheck != null) {
                return accessCheck;
            }

            User teamLead = getUser(userDetails);
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            LoggerUtil.info(this.getClass(), String.format(
                    "Loading team stats page for team leader %s - %d/%d",
                    teamLead.getUsername(), selectedYear, selectedMonth));

            // Load available users for team selection
            List<User> availableUsers = loadAvailableUsersForTeam();

            // Load existing team members using command pattern
            TeamPageData pageData = loadTeamPageData(teamLead.getUsername(), selectedYear, selectedMonth);

            // Populate model with all required data
            populateModelForTeamStatsPage(model, teamLead, availableUsers, pageData, selectedYear, selectedMonth);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded team stats page for %s with %d team members",
                    teamLead.getUsername(), pageData.getTeamMembers().size()));

            return "user/team-stats";

        } catch (Exception e) {
            return handlePageLoadError(e, userDetails, model);
        }
    }

    /**
     * Initialize team members for selected users (command operation)
     */
    @PostMapping("/initialize")
    public String initializeTeamMembers(@AuthenticationPrincipal UserDetails userDetails,
                                        @RequestParam List<Integer> selectedUsers,
                                        @RequestParam Integer year,
                                        @RequestParam Integer month,
                                        RedirectAttributes redirectAttributes) {
        try {
            // Validate team leader access
            String accessCheck = validateTeamLeaderAccess(userDetails);
            if (accessCheck != null) {
                return accessCheck;
            }

            User teamLead = getUser(userDetails);

            LoggerUtil.info(this.getClass(), String.format(
                    "Executing team initialization command for %s: %d users for %d/%d",
                    teamLead.getUsername(), selectedUsers.size(), year, month));

            // Execute initialize team members command
            OperationResult result = teamOperationService.initializeTeamMembers(
                    selectedUsers, teamLead.getUsername(), year, month);

            // Handle operation result
            handleOperationResult(result, redirectAttributes, "initialization");

            LoggerUtil.info(this.getClass(), String.format(
                    "Team initialization command completed for %s: %s",
                    teamLead.getUsername(), result.isSuccess() ? "SUCCESS" : "FAILURE"));

        } catch (Exception e) {
            handleOperationError(e, redirectAttributes, "initialize team members", userDetails.getUsername());
        }

        return createRedirectUrl(year, month);
    }

    /**
     * Update team statistics for all members (command operation)
     */
    @PostMapping("/update")
    public String updateTeamStats(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestParam Integer year,
                                  @RequestParam Integer month,
                                  RedirectAttributes redirectAttributes) {
        try {
            // Validate team leader access
            String accessCheck = validateTeamLeaderAccess(userDetails);
            if (accessCheck != null) {
                return accessCheck;
            }

            User teamLead = getUser(userDetails);

            LoggerUtil.info(this.getClass(), String.format(
                    "Executing team statistics update command for %s - %d/%d",
                    teamLead.getUsername(), year, month));

            // Execute update team statistics command
            OperationResult result = teamOperationService.updateTeamStatistics(
                    teamLead.getUsername(), year, month);

            // Handle operation result
            handleOperationResult(result, redirectAttributes, "statistics update");

            LoggerUtil.info(this.getClass(), String.format(
                    "Team statistics update command completed for %s: %s",
                    teamLead.getUsername(), result.isSuccess() ? "SUCCESS" : "FAILURE"));

        } catch (Exception e) {
            handleOperationError(e, redirectAttributes, "update team statistics", userDetails.getUsername());
        }

        return createRedirectUrl(year, month);
    }

    /**
     * AJAX endpoint to get team status information
     */
    @GetMapping("/status")
    @ResponseBody
    public TeamStatusResponse getTeamStatus(@AuthenticationPrincipal UserDetails userDetails,
                                            @RequestParam Integer year,
                                            @RequestParam Integer month) {
        try {
            String accessCheck = validateTeamLeaderAccess(userDetails);
            if (accessCheck != null) {
                return TeamStatusResponse.error("Access denied");
            }

            User teamLead = getUser(userDetails);

            LoggerUtil.debug(this.getClass(), String.format(
                    "Getting team status for %s - %d/%d", teamLead.getUsername(), year, month));

            // Get team status using command pattern
            boolean hasTeam = teamOperationService.hasTeamMembers(teamLead.getUsername(), year, month);
            int memberCount = teamOperationService.getTeamMemberCount(teamLead.getUsername(), year, month);

            String statusMessage = String.format("Team %s has %d members for %d/%d",
                    hasTeam ? "exists" : "not initialized", memberCount, year, month);

            return TeamStatusResponse.success(statusMessage, hasTeam, memberCount);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error getting team status for user %s: %s",
                    userDetails.getUsername(), e.getMessage()));
            return TeamStatusResponse.error("Error getting team status: " + e.getMessage());
        }
    }

    /**
     * AJAX endpoint to validate team initialization before processing
     */
    @PostMapping("/validate-initialization")
    @ResponseBody
    public ValidationResponse validateInitialization(@AuthenticationPrincipal UserDetails userDetails,
                                                     @RequestParam List<Integer> selectedUsers,
                                                     @RequestParam Integer year,
                                                     @RequestParam Integer month) {
        try {
            String accessCheck = validateTeamLeaderAccess(userDetails);
            if (accessCheck != null) {
                return ValidationResponse.error("Access denied");
            }

            // Validate input parameters
            if (selectedUsers == null || selectedUsers.isEmpty()) {
                return ValidationResponse.error("No users selected for team initialization");
            }

            if (selectedUsers.size() > 20) {
                return ValidationResponse.error("Too many users selected. Maximum 20 users allowed per team");
            }

            User teamLead = getUser(userDetails);

            // Check if team already exists
            boolean hasExistingTeam = teamOperationService.hasTeamMembers(teamLead.getUsername(), year, month);
            if (hasExistingTeam) {
                int existingCount = teamOperationService.getTeamMemberCount(teamLead.getUsername(), year, month);
                return ValidationResponse.warning(String.format(
                        "Team already exists with %d members. Initialization will replace existing team.",
                        existingCount));
            }

            return ValidationResponse.success(String.format(
                    "Ready to initialize team with %d members for %d/%d",
                    selectedUsers.size(), year, month));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error validating team initialization: %s", e.getMessage()));
            return ValidationResponse.error("Validation error: " + e.getMessage());
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Validate team leader access with consistent error handling
     * Allows access for: TEAM_LEADER, TL_CHECKING, and ADMIN roles
     */
    private String validateTeamLeaderAccess(UserDetails userDetails) {
        return checkUserAccess(userDetails,
                SecurityConstants.ROLE_TEAM_LEADER,
                SecurityConstants.ROLE_TL_CHECKING,
                SecurityConstants.ROLE_ADMIN);
    }

    /**
     * Load available users for team selection (non-admin users only)
     */
    private List<User> loadAvailableUsersForTeam() {
        try {
            return getUserService().getAllUsers().stream()
                    .filter(user -> !user.isAdmin())
                    .sorted((u1, u2) -> u1.getName().compareToIgnoreCase(u2.getName()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading available users for team: %s", e.getMessage()));
            return new ArrayList<>();
        }
    }

    /**
     * Load team page data using command pattern
     */
    private TeamPageData loadTeamPageData(String teamLeadUsername, int year, int month) {
        try {
            OperationResult result = teamOperationService.loadTeamMembers(teamLeadUsername, year, month);

            if (result.isSuccess()) {
                List<TeamMemberDTO> teamMembers = result.getTeamMembersData();
                if (teamMembers == null) {
                    teamMembers = new ArrayList<>();
                }

                LoggerUtil.debug(this.getClass(), String.format(
                        "Loaded %d team members for %s", teamMembers.size(), teamLeadUsername));

                return new TeamPageData(teamMembers, true, result.getMessage());
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Failed to load team members for %s - %d/%d: %s",
                        teamLeadUsername, year, month, result.getMessage()));

                return new TeamPageData(new ArrayList<>(), false, result.getMessage());
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format(
                    "Error loading team page data for %s - %d/%d: %s",
                    teamLeadUsername, year, month, e.getMessage()));

            return new TeamPageData(new ArrayList<>(), false, "Error loading team data: " + e.getMessage());
        }
    }

    /**
     * Populate model with all required data for team stats page
     */
    private void populateModelForTeamStatsPage(Model model, User teamLead, List<User> availableUsers,
                                               TeamPageData pageData, int year, int month) {
        // Core data
        model.addAttribute("teamLead", teamLead);
        model.addAttribute("availableUsers", availableUsers);
        model.addAttribute("teamMemberDTOS", pageData.getTeamMembers());
        model.addAttribute("currentYear", year);
        model.addAttribute("currentMonth", month);
        model.addAttribute("dashboardUrl", getDashboardUrlForUser(teamLead));

        // Team statistics
        model.addAttribute("teamMemberCount", pageData.getTeamMembers().size());
        model.addAttribute("hasTeamMembers", !pageData.getTeamMembers().isEmpty());
        model.addAttribute("teamLoadSuccess", pageData.isLoadSuccess());
        model.addAttribute("teamLoadMessage", pageData.getLoadMessage());

        // Available users count for UI
        model.addAttribute("availableUserCount", availableUsers.size());

        // Page metadata
        model.addAttribute("pageTitle", String.format("Team Statistics - %d/%d", year, month));
        model.addAttribute("operationMode", "command-pattern");
    }

    /**
     * Handle operation results with consistent feedback
     */
    private void handleOperationResult(OperationResult result, RedirectAttributes redirectAttributes, String operationName) {
        if (result.isSuccess()) {
            redirectAttributes.addFlashAttribute("successMessage", result.getMessage());

            // Add additional success information if available
            if (result.hasSideEffects()) {
                LoggerUtil.debug(this.getClass(), String.format(
                        "Operation %s completed with side effects", operationName));
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());

            LoggerUtil.warn(this.getClass(), String.format(
                    "Operation %s failed: %s", operationName, result.getMessage()));
        }
    }

    /**
     * Handle operation errors with consistent error messaging
     */
    private void handleOperationError(Exception e, RedirectAttributes redirectAttributes,
                                      String operationName, String username) {
        String errorMessage = String.format("Failed to %s: %s", operationName, e.getMessage());

        LoggerUtil.error(this.getClass(), String.format(
                "Error during %s for user %s: %s", operationName, username, e.getMessage()), e);

        redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
    }

    /**
     * Handle page load errors
     */
    private String handlePageLoadError(Exception e, UserDetails userDetails, Model model) {
        String errorMessage = "Error loading team statistics: " + e.getMessage();

        LoggerUtil.error(this.getClass(), String.format(
                "Error showing team stats page for user %s: %s",
                userDetails.getUsername(), e.getMessage()), e);

        model.addAttribute("error", errorMessage);
        model.addAttribute("teamMemberDTOS", new ArrayList<>());
        model.addAttribute("availableUsers", new ArrayList<>());

        return "user/team-stats";
    }

    /**
     * Create redirect URL with parameters
     */
    private String createRedirectUrl(Integer year, Integer month) {
        return String.format("redirect:/user/stats?year=%d&month=%d", year, month);
    }

    // ========================================================================
    // RESPONSE CLASSES
    // ========================================================================

    /**
     * Response class for team status AJAX endpoint
     */
    @Getter
    public static class TeamStatusResponse {
        // Getters
        private final boolean success;
        private final String message;
        private final boolean hasTeam;
        private final int memberCount;

        private TeamStatusResponse(boolean success, String message, boolean hasTeam, int memberCount) {
            this.success = success;
            this.message = message;
            this.hasTeam = hasTeam;
            this.memberCount = memberCount;
        }

        public static TeamStatusResponse success(String message, boolean hasTeam, int memberCount) {
            return new TeamStatusResponse(true, message, hasTeam, memberCount);
        }

        public static TeamStatusResponse error(String message) {
            return new TeamStatusResponse(false, message, false, 0);
        }

    }

    /**
     * Response class for validation AJAX endpoint
     */
    @Getter
    public static class ValidationResponse {
        // Getters
        private final boolean success;
        private final String message;
        private final String type; // "success", "warning", "error"

        private ValidationResponse(boolean success, String message, String type) {
            this.success = success;
            this.message = message;
            this.type = type;
        }

        public static ValidationResponse success(String message) {
            return new ValidationResponse(true, message, "success");
        }

        public static ValidationResponse warning(String message) {
            return new ValidationResponse(true, message, "warning");
        }

        public static ValidationResponse error(String message) {
            return new ValidationResponse(false, message, "error");
        }

    }

    /**
     * Data container for team page information
     */
    @Getter
    private static class TeamPageData {
        private final List<TeamMemberDTO> teamMembers;
        private final boolean loadSuccess;
        private final String loadMessage;

        public TeamPageData(List<TeamMemberDTO> teamMembers, boolean loadSuccess, String loadMessage) {
            this.teamMembers = teamMembers;
            this.loadSuccess = loadSuccess;
            this.loadMessage = loadMessage;
        }

    }
}