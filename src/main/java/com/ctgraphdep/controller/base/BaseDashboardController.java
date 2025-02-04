package com.ctgraphdep.controller.base;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dashboard.*;
import com.ctgraphdep.service.DashboardService;
import com.ctgraphdep.service.PermissionFilterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

public abstract class BaseDashboardController {
    protected final UserService userService;
    protected final DashboardService dashboardService;
    protected final DashboardConfiguration dashboardConfig;
    protected final PermissionFilterService permissionFilterService;

    protected BaseDashboardController(
            UserService userService,
            DashboardService dashboardService,
            DashboardConfiguration dashboardConfig,
            PermissionFilterService permissionFilterService) {
        this.userService = userService;
        this.dashboardService = dashboardService;
        this.dashboardConfig = dashboardConfig;
        this.permissionFilterService = permissionFilterService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    protected DashboardViewModel prepareDashboardViewModel() {
        User currentUser = getCurrentUser();
        validateUserRole(currentUser);
        // Filter cards based on permissions
        List<DashboardCard> filteredCards = permissionFilterService.filterCardsByPermission(
                dashboardConfig.getCards(),
                currentUser
        );

        // Create a new configuration with filtered cards
        DashboardConfiguration filteredConfig = DashboardConfiguration.builder()
                .title(dashboardConfig.getTitle())
                .description(dashboardConfig.getDescription())
                .role(dashboardConfig.getRole())
                .refreshEnabled(dashboardConfig.isRefreshEnabled())
                .refreshInterval(dashboardConfig.getRefreshInterval())
                .cards(filteredCards)
                .build();

        // Build view model with filtered configuration
        return dashboardService.buildDashboardViewModel(currentUser, filteredConfig);
    }

    protected String renderDashboard(Model model) {
        try {
            DashboardViewModel viewModel = prepareDashboardViewModel();
            populateModel(model, viewModel);
            return "dashboard/" + getTemplateType() + "/dashboard";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing dashboard: " + e.getMessage());
            throw new RuntimeException("Failed to prepare dashboard", e);
        }
    }

    private void populateModel(Model model, DashboardViewModel viewModel) {
        model.addAttribute("dashboard", viewModel);
        model.addAttribute("dashboardCards", viewModel.getCards());
        model.addAttribute("dashboardMetrics", viewModel.getMetrics());
        model.addAttribute("refreshEnabled", dashboardConfig.isRefreshEnabled());
        model.addAttribute("refreshInterval", dashboardConfig.getRefreshInterval());
        model.addAttribute("templateType", getTemplateType());
    }

    protected User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("No authenticated user found");
        }
        User user = userService.getUserByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        LoggerUtil.debug(this.getClass(),
                String.format("Current user: %s, Role: %s, Required role: %s",
                        user.getUsername(),
                        user.getRole(),
                        dashboardConfig.getRole()));

        return user;
    }

    private void validateUserRole(User user) {
        if (!user.getRole().equals(dashboardConfig.getRole())) {
            throw new AccessDeniedException("User does not have required role: " + dashboardConfig.getRole());
        }
    }

    protected abstract String getTemplateType();
    protected abstract String getDashboardViewName();
}