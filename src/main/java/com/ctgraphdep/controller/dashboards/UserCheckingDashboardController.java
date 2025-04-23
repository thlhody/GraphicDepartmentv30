package com.ctgraphdep.controller.dashboards;

import com.ctgraphdep.controller.base.BaseDashboardController;
import com.ctgraphdep.dashboard.config.DashboardConfig;
import com.ctgraphdep.dashboard.service.DashboardService;
import com.ctgraphdep.service.PermissionFilterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user-checking")
@PreAuthorize("hasRole('ROLE_USER_CHECKING')")
public class UserCheckingDashboardController extends BaseDashboardController {

    @Autowired
    public UserCheckingDashboardController(
            UserService userService,
            DashboardService dashboardService,
            @Qualifier("userCheckingDashboardConfig") DashboardConfig userDashboardConfig,
            PermissionFilterService permissionFilterService, TimeValidationService timeValidationService) {
        super(userService, dashboardService, userDashboardConfig, permissionFilterService, timeValidationService);
        LoggerUtil.debug(this.getClass(), "UserCheckingDashboardController initialized with required role: " + userDashboardConfig.getRole());
    }

    @GetMapping
    public String dashboard(Model model) {
        LoggerUtil.debug(this.getClass(), "UserCheckingDashboardController.dashboard() called");
        return renderDashboard(model);
    }

    @Override
    protected String getTemplateType() {
        return "user-checking";
    }

    @Override
    protected String getDashboardViewName() {
        return getCurrentUser().getName();
    }
}