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
@RequestMapping("/checking")
@PreAuthorize("hasRole('CHECKING')")
public class CheckingDashboardController extends BaseDashboardController {

    @Autowired
    public CheckingDashboardController(UserService userService, DashboardService dashboardService,
            @Qualifier("checkingDashboardConfig") DashboardConfig userDashboardConfig,
            PermissionFilterService permissionFilterService, TimeValidationService timeValidationService) {
        super(userService, dashboardService, userDashboardConfig, permissionFilterService, timeValidationService);
    }

    @GetMapping
    public String dashboard(Model model) {
        return renderDashboard(model);
    }

    @Override
    protected String getTemplateType() {
        return "checking";
    }

    @Override
    protected String getDashboardViewName() {
        return getCurrentUser().getName();
    }
}
