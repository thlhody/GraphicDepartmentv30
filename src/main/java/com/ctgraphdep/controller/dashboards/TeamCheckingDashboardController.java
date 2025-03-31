package com.ctgraphdep.controller.dashboards;

import com.ctgraphdep.controller.base.BaseDashboardController;
import com.ctgraphdep.config.DashboardConfig;
import com.ctgraphdep.service.DashboardService;
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
@RequestMapping("/team-checking")
@PreAuthorize("hasRole('TL_CHECKING')")
public class TeamCheckingDashboardController extends BaseDashboardController {

    @Autowired
    public TeamCheckingDashboardController(UserService userService, DashboardService dashboardService,
                                       @Qualifier("teamCheckingDashboardConfig") DashboardConfig userDashboardConfig,
                                       PermissionFilterService permissionFilterService, TimeValidationService timeValidationService) {
        super(userService, dashboardService, userDashboardConfig, permissionFilterService, timeValidationService);
    }

    @GetMapping
    public String dashboard(Model model) {
        return renderDashboard(model);
    }

    @Override
    protected String getTemplateType() {
        return "team-checking";
    }

    @Override
    protected String getDashboardViewName() {
        return getCurrentUser().getName();
    }
}
