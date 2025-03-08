package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseDashboardController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dashboard.DashboardConfiguration;
import com.ctgraphdep.service.ContinuationTrackingService;
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
@RequestMapping("/team-lead")
@PreAuthorize("hasRole('ROLE_TEAM_LEADER')")
public class TeamLeadDashboardController extends BaseDashboardController {

    private final ContinuationTrackingService continuationTrackingService;

    @Autowired
    public TeamLeadDashboardController(
            UserService userService,
            DashboardService dashboardService,
            @Qualifier("teamLeadDashboardConfig") DashboardConfiguration teamLeadDashboardConfig,
            PermissionFilterService permissionFilterService, ContinuationTrackingService continuationTrackingService) {
        super(userService, dashboardService, teamLeadDashboardConfig, permissionFilterService);
        this.continuationTrackingService = continuationTrackingService;
    }

    @GetMapping
    public String dashboard(Model model) {
        User currentUser = getCurrentUser();
        boolean hasUnresolvedContinuations = continuationTrackingService.hasUnresolvedMidnightEnd(currentUser.getUsername());

        if (hasUnresolvedContinuations) {
            return "redirect:/user/session/resolve";
        }
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