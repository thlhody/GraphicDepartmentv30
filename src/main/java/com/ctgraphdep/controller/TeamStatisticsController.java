package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.team.TeamMember;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.TeamStatisticsService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/user/stats")
@PreAuthorize("hasRole('ROLE_TEAM_LEADER')")
public class TeamStatisticsController extends BaseController {
    private final TeamStatisticsService teamStatisticsService;

    public TeamStatisticsController(
            UserService userService,
            FolderStatusService folderStatusService,
            TeamStatisticsService teamStatisticsService) {
        super(userService, folderStatusService);
        this.teamStatisticsService = teamStatisticsService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String showTeamStats(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {
        try {
            User teamLead = getUser(userDetails);
            if (teamLead == null) {
                return "redirect:/login";
            }

            // Set default year and month if not provided
            LocalDate now = LocalDate.now();
            year = year != null ? year : now.getYear();
            month = month != null ? month : now.getMonthValue();

            // Get all non-admin users for selection
            List<User> availableUsers = getUserService().getAllUsers().stream()
                    .filter(user -> !user.isAdmin()) // Only filter out admins
                    .collect(Collectors.toList());

            // Load existing team members if any
            List<TeamMember> teamMembers = teamStatisticsService.getTeamMembers(teamLead.getUsername(), year, month);

            // Add data to model
            model.addAttribute("teamLead", teamLead);
            model.addAttribute("availableUsers", availableUsers);
            model.addAttribute("teamMembers", teamMembers);
            model.addAttribute("currentYear", year);
            model.addAttribute("currentMonth", month);
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
            User teamLead = getUser(userDetails);
            if (teamLead == null) {
                return "redirect:/login";
            }

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
            User teamLead = getUser(userDetails);
            if (teamLead == null) {
                return "redirect:/login";
            }

            teamStatisticsService.updateTeamStatistics(teamLead.getUsername(), year, month);
            redirectAttributes.addFlashAttribute("successMessage", "Team statistics updated successfully");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating team statistics: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update team statistics");
        }

        return "redirect:/user/stats?year=" + year + "&month=" + month;
    }
}