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
@RequestMapping("/team-lead")
@PreAuthorize("hasRole('ROLE_TEAM_LEADER')")
public class TeamLeadDashboardController extends BaseDashboardController {


    @Autowired
    public TeamLeadDashboardController(
            UserService userService,
            DashboardService dashboardService,
            @Qualifier("teamLeadDashboardConfig") DashboardConfig teamLeadDashboardConfig,
            PermissionFilterService permissionFilterService, TimeValidationService timeValidationService) {
        super(userService, dashboardService, teamLeadDashboardConfig, permissionFilterService,timeValidationService);
    }

    @GetMapping
    public String dashboard(Model model) {
        return renderDashboard(model);
    }

    @Override
    protected String getTemplateType() {
        return "team-lead";
    }

    @Override
    protected String getDashboardViewName() {
        return getCurrentUser().getName() + " (Team Leader)";
    }
}