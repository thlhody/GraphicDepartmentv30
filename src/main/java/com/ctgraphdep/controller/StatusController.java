package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.TimeOffSummary;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusDTO;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.OnlineMetricsService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.UserTimeOffService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Year;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class StatusController extends BaseController {
    private final OnlineMetricsService onlineMetricsService;
    private final UserTimeOffService userTimeOffService;

    public StatusController(
            UserService userService,
            FolderStatusService folderStatusService,
            OnlineMetricsService onlineMetricsService,
            UserTimeOffService userTimeOffService) {
        super(userService, folderStatusService);
        this.onlineMetricsService = onlineMetricsService;
        this.userTimeOffService = userTimeOffService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String getStatus(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing unified status page");

        User currentUser = getUser(userDetails);

        // Determine dashboard URL based on user role
        String dashboardUrl = currentUser.hasRole("TEAM_LEADER") ? "/team-lead" :
                currentUser.hasRole("ADMIN") ? "/admin" : "/user";

        LoggerUtil.debug(this.getClass(), String.format("Determined Dashboard URL for %s: %s", currentUser.getUsername(), dashboardUrl));

        // Get filtered status list for non-admin users
        List<UserStatusDTO> userStatuses = onlineMetricsService.getUserStatuses();
        long onlineCount = userStatuses.stream().filter(status -> "Online".equals(status.getStatus())).count();

        // Add model attributes
        model.addAttribute("userStatuses", userStatuses);
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("onlineCount", onlineCount);
        model.addAttribute("isAdminView", currentUser.isAdmin());
        model.addAttribute("dashboardUrl", dashboardUrl);

        return "status/status";
    }

    @GetMapping("/refresh")
    public String refreshStatus() {
        return "redirect:/status";
    }

    @GetMapping("/timeoff-history")
    public String getTimeOffHistory(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            // We need either userId or username
            if (userId == null && (username == null || username.isEmpty())) {
                redirectAttributes.addFlashAttribute("errorMessage", "User information is required");
                return "redirect:/status";
            }

            // Set default year if not provided
            int currentYear = Year.now().getValue();
            year = year != null ? year : currentYear;

            // Get user details either by ID or username
            Optional<User> userOpt;
            if (userId != null) {
                LoggerUtil.info(this.getClass(), "Accessing time off history for user ID: " + userId);
                userOpt = getUserService().getUserById(userId);
            } else {
                LoggerUtil.info(this.getClass(), "Accessing time off history for username: " + username);
                userOpt = getUserService().getUserByUsername(username);
            }

            if (userOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "User not found");
                redirectAttributes.addFlashAttribute("errorMessage", "User not found");
                return "redirect:/status";
            }

            User user = userOpt.get();

            // Get time off records
            List<WorkTimeTable> timeOffs = userTimeOffService.getUserTimeOffHistory(user.getUsername(), year);

            // Calculate time off summary
            TimeOffSummary summary = userTimeOffService.calculateTimeOffSummary(user.getUsername(), year);

            // Add data to model
            model.addAttribute("user", user);
            model.addAttribute("timeOffs", timeOffs);
            model.addAttribute("summary", summary);
            model.addAttribute("year", year);

            return "status/timeoff-history";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error viewing time off history: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading time off history");
            return "redirect:/status";
        }
    }
}