package com.ctgraphdep.controller.base;

import com.ctgraphdep.dashboard.config.DashboardConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.DashboardViewModelDTO;
import com.ctgraphdep.model.dto.dashboard.DashboardCardDTO;
import com.ctgraphdep.model.dto.dashboard.DashboardMetricsDTO;
import com.ctgraphdep.dashboard.service.DashboardService;
import com.ctgraphdep.service.PermissionFilterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;

import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public abstract class BaseDashboardController {
    protected final UserService userService;
    protected final DashboardService dashboardService;
    protected final DashboardConfig dashboardConfig;
    protected final PermissionFilterService permissionFilterService;
    private final TimeValidationService timeValidationService;

    protected BaseDashboardController(UserService userService, DashboardService dashboardService, DashboardConfig dashboardConfig,
                                      PermissionFilterService permissionFilterService, TimeValidationService timeValidationService) {
        this.userService = userService;
        this.dashboardService = dashboardService;
        this.dashboardConfig = dashboardConfig;
        this.permissionFilterService = permissionFilterService;
        this.timeValidationService = timeValidationService;
    }

    protected DashboardViewModelDTO prepareDashboardViewModel() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            LoggerUtil.error(this.getClass(), "Failed to prepare dashboard: current user is null");
            // Return empty dashboard model
            return createEmptyDashboardModel();
        }

        if (!isUserRoleValid(currentUser)) {
            LoggerUtil.error(this.getClass(), String.format("User %s with role %s does not have required role: %s", currentUser.getUsername(), currentUser.getRole(), dashboardConfig.getRole()));
            // Return empty dashboard model
            return createEmptyDashboardModel();
        }

        try {
            // Filter cards based on permissions
            List<DashboardCardDTO> filteredCards = permissionFilterService.filterCardsByPermission(dashboardConfig.getCards(), currentUser);

            // Create a new configuration with filtered cards
            DashboardConfig filteredConfig = DashboardConfig.builder()
                    .title(dashboardConfig.getTitle())
                    .description(dashboardConfig.getDescription())
                    .role(dashboardConfig.getRole())
                    .refreshEnabled(dashboardConfig.isRefreshEnabled())
                    .refreshInterval(dashboardConfig.getRefreshInterval())
                    .cards(filteredCards)
                    .build();

            // Build view model with filtered configuration
            return dashboardService.buildDashboardViewModel(currentUser, filteredConfig);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error filtering dashboard cards: " + e.getMessage(), e);
            return createEmptyDashboardModel();
        }
    }

    protected String renderDashboard(Model model) {
        try {
            DashboardViewModelDTO viewModel = prepareDashboardViewModel();
            populateModel(model, viewModel);
            return "dashboard/" + getTemplateType() + "/dashboard";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing dashboard: " + e.getMessage(), e);
            model.addAttribute("error", "Failed to load dashboard. See logs for details.");
            return "error/dashboard-error"; // Fallback error template
        }
    }

    private void populateModel(Model model, DashboardViewModelDTO viewModel) {
        model.addAttribute("dashboard", viewModel);
        model.addAttribute("dashboardCards", viewModel.getCards());
        model.addAttribute("dashboardMetrics", viewModel.getMetrics());
        model.addAttribute("refreshEnabled", dashboardConfig.isRefreshEnabled());
        model.addAttribute("refreshInterval", dashboardConfig.getRefreshInterval());
        model.addAttribute("templateType", getTemplateType());
        model.addAttribute("dashboardViewName", getDashboardViewName());
    }

    protected User getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                LoggerUtil.error(this.getClass(), "No authenticated user found in security context");
                return null;
            }

            Optional<User> userOptional = userService.getUserByUsername(auth.getName());
            if (userOptional.isEmpty()) {
                LoggerUtil.error(this.getClass(), "User not found for username: " + auth.getName());
                return null;
            }

            User user = userOptional.get();
            LoggerUtil.debug(this.getClass(), String.format("Current user: %s, Role: %s, Required role: %s", user.getUsername(), user.getRole(), dashboardConfig.getRole()));

            return user;
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error retrieving current user: " + e.getMessage(), e);
            return null;
        }
    }

    private boolean isUserRoleValid(User user) {
        if (user == null || user.getRole() == null) {
            LoggerUtil.warn(this.getClass(), "User or user role is null");
            return false;
        }

        // Normalize roles by removing ROLE_ prefix
        String normalizedUserRole = user.getRole().replace("ROLE_", "");
        String normalizedRequiredRole = dashboardConfig.getRole().replace("ROLE_", "");

        // Only allow exact matches - no more special compatibility
        if (normalizedUserRole.equals(normalizedRequiredRole)) {
            LoggerUtil.debug(this.getClass(),
                    String.format("Role match for user %s: %s matches required role %s",
                            user.getUsername(), normalizedUserRole, normalizedRequiredRole));
            return true;
        }

        LoggerUtil.debug(this.getClass(),
                String.format("Role mismatch for user %s: %s does not match required role %s",
                        user.getUsername(), normalizedUserRole, normalizedRequiredRole));

        return false;
    }

    private DashboardViewModelDTO createEmptyDashboardModel() {
        GetStandardTimeValuesCommand timeCommand = timeValidationService.getValidationFactory().createGetStandardTimeValuesCommand();
        GetStandardTimeValuesCommand.StandardTimeValues timeValues = timeValidationService.execute(timeCommand);

        return DashboardViewModelDTO.builder()
                .pageTitle("Dashboard Unavailable")
                .username("Unknown")
                .userFullName("Unknown User")
                .userRole("NONE")
                .currentDateTime(timeValues.getCurrentTime().format(WorkCode.DATE_TIME_FORMATTER))
                .cards(Collections.emptyList())
                .metrics(buildEmptyMetrics())
                .build();
    }

    private DashboardMetricsDTO buildEmptyMetrics() {
        return DashboardMetricsDTO.builder()
                .onlineUsers(0)
                .activeUsers(0)
                .systemStatus("UNAVAILABLE")
                .lastUpdate(LocalDateTime.now().format(WorkCode.DATE_TIME_FORMATTER))
                .build();
    }

    protected abstract String getTemplateType();
    protected abstract String getDashboardViewName();
}