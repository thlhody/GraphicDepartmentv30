package com.ctgraphdep.controller.dashboards;

import com.ctgraphdep.controller.base.BaseDashboardController;
import com.ctgraphdep.dashboard.config.DashboardConfig;
import com.ctgraphdep.dashboard.service.DashboardService;
import com.ctgraphdep.service.PermissionFilterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminDashboardController extends BaseDashboardController {

    @Autowired
    public AdminDashboardController(UserService userService,
                                    DashboardService dashboardService,
                                    @Qualifier("adminDashboardConfig") DashboardConfig adminDashboardConfig,
                                    PermissionFilterService permissionFilterService,
                                    TimeValidationService timeValidationService) {
        super(userService, dashboardService, adminDashboardConfig, permissionFilterService, timeValidationService);
    }
    @GetMapping
    public String dashboard(Model model) {
        return renderDashboard(model);
    }

    @Override
    protected String getTemplateType() {
        return "admin";
    }

    @Override
    protected String getDashboardViewName() {
        return "Admin Dashboard";
    }
}
