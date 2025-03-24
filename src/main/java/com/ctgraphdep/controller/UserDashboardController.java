package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseDashboardController;
import com.ctgraphdep.model.dashboard.DashboardConfiguration;
import com.ctgraphdep.service.DashboardService;
import com.ctgraphdep.service.PermissionFilterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.validation.TimeValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
@PreAuthorize("hasRole('ROLE_USER')")
public class UserDashboardController extends BaseDashboardController {

    @Autowired
    public UserDashboardController(
            UserService userService,
            DashboardService dashboardService,
            @Qualifier("userDashboardConfig") DashboardConfiguration userDashboardConfig,
            PermissionFilterService permissionFilterService, TimeValidationService timeValidationService) {
        super(userService, dashboardService, userDashboardConfig, permissionFilterService, timeValidationService);
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