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
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class StatusController extends BaseController {
    private final OnlineMetricsService onlineMetricsService;

    public StatusController(
            UserService userService,
            FolderStatusService folderStatusService,
            OnlineMetricsService onlineMetricsService) {
        super(userService, folderStatusService);
        this.onlineMetricsService = onlineMetricsService;
        LoggerUtil.initialize(this.getClass(), "Initializing Status Controller");
    }

    @GetMapping
    public String getStatus(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing unified status page");

        User currentUser = getUser(userDetails);

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

        return "status/status";
    }

    @GetMapping("/refresh")
    public String refreshStatus() {
        return "redirect:/status";
    }
}