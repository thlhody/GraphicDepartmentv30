package com.ctgraphdep.controller.team;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.TeamMemberDTO;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.service.TeamStatisticsService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/user/stats")
@PreAuthorize("hasRole('ROLE_TEAM_LEADER')")
public class TeamStatisticsController extends BaseController {
    private final TeamStatisticsService teamStatisticsService;

    public TeamStatisticsController(
            UserService userService,
            FolderStatus folderStatus,
            TeamStatisticsService teamStatisticsService,
            TimeValidationService timeValidationService) {
        super(userService, folderStatus, timeValidationService);
        this.teamStatisticsService = teamStatisticsService;
    }

    @GetMapping
    public String showTeamStats(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {
        try {
            // Check if user has team leader role
            String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_TEAM_LEADER);
            if (accessCheck != null) {
                return accessCheck;
            }

            User teamLead = getUser(userDetails);

            // Use determineYear and determineMonth methods from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Get all non-admin users for selection
            List<User> availableUsers = getUserService().getAllUsers().stream()
                    .filter(user -> !user.isAdmin())
                    .collect(Collectors.toList());

            // Load existing team members if any
            List<TeamMemberDTO> teamMemberDTOS = teamStatisticsService.getTeamMembers(
                    teamLead.getUsername(), selectedYear, selectedMonth);

            // Add data to model
            model.addAttribute("teamLead", teamLead);
            model.addAttribute("availableUsers", availableUsers);
            model.addAttribute("teamMemberDTOS", teamMemberDTOS);
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);
            model.addAttribute("dashboardUrl", "/team-lead");

            return "user/team-stats";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error showing team stats page: " + e.getMessage());
            model.addAttribute("error", "Error loading team statistics");
            return "user/team-stats";
        }
    }

    @PostMapping("/initialize")
    public String initializeTeamMembers(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam List<Integer> selectedUsers,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            // Check if user has team leader role
            String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_TEAM_LEADER);
            if (accessCheck != null) {
                return accessCheck;
            }

            User teamLead = getUser(userDetails);

            teamStatisticsService.initializeTeamMembers(selectedUsers, teamLead.getUsername(), year, month);
            redirectAttributes.addFlashAttribute("successMessage", "Team members initialized successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initializing team members: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to initialize team members");
        }

        return "redirect:/user/stats?year=" + year + "&month=" + month;
    }

    @PostMapping("/update")
    public String updateTeamStats(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            // Check if user has team leader role
            String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_TEAM_LEADER);
            if (accessCheck != null) {
                return accessCheck;
            }

            User teamLead = getUser(userDetails);

            teamStatisticsService.updateTeamStatistics(teamLead.getUsername(), year, month);
            redirectAttributes.addFlashAttribute("successMessage", "Team statistics updated successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating team statistics: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update team statistics");
        }

        return "redirect:/user/stats?year=" + year + "&month=" + month;
    }
}