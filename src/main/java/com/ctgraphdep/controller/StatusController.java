package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusDTO;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.OnlineMetricsService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class StatusController extends BaseController {
    private final OnlineMetricsService onlineMetricsService;

    public StatusController(
            UserService userService,
            FolderStatusService folderStatusService,
            OnlineMetricsService onlineMetricsService) {
        super(userService, folderStatusService);
        this.onlineMetricsService = onlineMetricsService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String getStatus(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing unified status page");

        User currentUser = getUser(userDetails);

        // Determine dashboard URL based on user role
        String dashboardUrl = currentUser.hasRole("TEAM_LEADER") ? "/team-lead" :
                currentUser.hasRole("ADMIN") ? "/admin" : "/user";

        LoggerUtil.debug(this.getClass(),
                String.format("Determined Dashboard URL for %s: %s",
                        currentUser.getUsername(), dashboardUrl));

        // Get filtered status list for non-admin users
        List<UserStatusDTO> userStatuses = onlineMetricsService.getUserStatuses();
        long onlineCount = userStatuses.stream()
                .filter(status -> "Online".equals(status.getStatus()))
                .count();

        // Add model attributes
        model.addAttribute("userStatuses", userStatuses);
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("onlineCount", onlineCount);
        model.addAttribute("isAdminView", currentUser.isAdmin());
        model.addAttribute("dashboardUrl", dashboardUrl);  // Add this line

        return "status/status";
    }

    @GetMapping("/refresh")
    public String refreshStatus() {
        return "redirect:/status";
    }
}