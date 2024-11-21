package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseDashboardController;
import com.ctgraphdep.model.dashboard.DashboardConfiguration;
import com.ctgraphdep.service.DashboardService;
import com.ctgraphdep.service.PermissionFilterService;
import com.ctgraphdep.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
@PreAuthorize("hasRole('USER')")
public class UserDashboardController extends BaseDashboardController {

    @Autowired
    public UserDashboardController(
            UserService userService,
            DashboardService dashboardService,
            @Qualifier("userDashboardConfig") DashboardConfiguration userDashboardConfig,
            PermissionFilterService permissionFilterService) {
        super(userService, dashboardService, userDashboardConfig, permissionFilterService);
    }

    @GetMapping
    public String dashboard(Model model) {
        return renderDashboard(model);
    }

    @Override
    protected String getTemplateType() {
        return "user";
    }

    @Override
    protected String getDashboardViewName() {
        return getCurrentUser().getName();
    }
}