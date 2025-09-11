package com.ctgraphdep.dashboard;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.dashboard.config.DashboardConfig;
import com.ctgraphdep.model.dto.dashboard.DashboardCardDTO;
import com.ctgraphdep.service.PermissionFilterService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class DashboardManager {

    @Bean
    @Qualifier("adminDashboardConfig")
    public DashboardConfig adminDashboardConfig() {
        return DashboardConfig.builder()
                .title("Admin Dashboard")
                .description("System Administration Dashboard")
                .role(SecurityConstants.ROLE_ADMIN)
                .refreshEnabled(true)
                .refreshInterval(30000)
                .cards(Arrays.asList(
                        createStatusCard(SecurityConstants.ROLE_ADMIN),
                        createAdminWorktimeCard(),
                        createAdminRegisterCard(),
                        createAdminCheckRegisterCard(),
                        createAdminBonusCard(),
                        createTeamStatisticsCard(),
                        createAdminStatistics(),
                        createAdminHolidaysCard(),
                        createSettingsCard(SecurityConstants.ROLE_ADMIN),
                        createOMSSystemCard()
                ))
                .build();
    }

    @Bean
    @Qualifier("teamLeadDashboardConfig")
    public DashboardConfig teamLeadDashboardConfig() {
        return DashboardConfig.builder()
                .title("Team Lead Dashboard")
                .description("Team Management Dashboard")
                .role(SecurityConstants.ROLE_TEAM_LEADER)
                .refreshEnabled(true)
                .refreshInterval(45000)
                .cards(Arrays.asList(
                        createStatusCard(SecurityConstants.ROLE_ADMIN),  // Using admin view for status
                        createSessionCard(),
                        createRegisterCard(),
                        createWorkTimeManagementCard(),
                        createTeamStatisticsCard(),
                        createOMSSystemCard(),
                        createSettingsCard(SecurityConstants.ROLE_USER)
                ))
                .build();
    }

    @Bean
    @Qualifier("teamCheckingDashboardConfig")
    public DashboardConfig teamCheckingDashboardConfig() {
        return DashboardConfig.builder()
                .title("Team Checking Dashboard")
                .description("Team Checking Management Dashboard")
                .role(SecurityConstants.ROLE_TL_CHECKING)
                .refreshEnabled(true)
                .refreshInterval(45000)
                .cards(Arrays.asList(
                        createStatusCard(SecurityConstants.ROLE_ADMIN),  // Using admin view for status
                        createSessionCard(),
                        createRegisterCard(),
                        createUserCheckRegisterCard(),
                        createTeamCheckRegisterCard(),
                        createWorkTimeManagementCard(),
                        createCheckValuesCard(),
                        createTeamStatisticsCard(),
                        createOMSSystemCard(),
                        createSettingsCard(SecurityConstants.ROLE_USER)
                ))
                .build();
    }

    @Bean
    @Qualifier("userCheckingDashboardConfig")
    public DashboardConfig userCheckingDashboardConfig() {
        return DashboardConfig.builder()
                .title("Checking Dashboard")
                .description("User Checking Control Panel")
                .role(SecurityConstants.ROLE_USER_CHECKING)
                .refreshEnabled(true)
                .refreshInterval(60000)
                .cards(Arrays.asList(
                        createStatusCard(SecurityConstants.ROLE_USER),
                        createSessionCard(),
                        createRegisterCard(),
                        createWorkTimeManagementCard(),
                        createUserCheckRegisterCard(),
                        createSettingsCard(SecurityConstants.ROLE_USER),
                        createOMSSystemCard()
                ))
                .build();
    }

    @Bean
    @Qualifier("checkingDashboardConfig")
    public DashboardConfig checkingDashboardConfig() {
        return DashboardConfig.builder()
                .title("Checking Dashboard")
                .description("User Checking Control Panel")
                .role(SecurityConstants.ROLE_CHECKING)
                .refreshEnabled(true)
                .refreshInterval(60000)
                .cards(Arrays.asList(
                        createStatusCard(SecurityConstants.ROLE_USER),
                        createSessionCard(),
                        createUserCheckRegisterCard(),
                        createWorkTimeManagementCard(),
                        createSettingsCard(SecurityConstants.ROLE_USER),
                        createOMSSystemCard()
                ))
                .build();
    }

    @Bean
    @Qualifier("userDashboardConfig")
    public DashboardConfig userDashboardConfig() {
        return DashboardConfig.builder()
                .title("User Dashboard")
                .description("User Control Panel")
                .role(SecurityConstants.ROLE_USER)
                .refreshEnabled(true)
                .refreshInterval(60000)
                .cards(Arrays.asList(
                        createStatusCard(SecurityConstants.ROLE_USER),
                        createSessionCard(),
                        createRegisterCard(),
                        createWorkTimeManagementCard(),
                        createOMSSystemCard(),
                        createSettingsCard(SecurityConstants.ROLE_USER)
                ))
                .build();
    }

    //Checking Cards
    private DashboardCardDTO createUserCheckRegisterCard() {
        return DashboardCardDTO.builder()
                .title("Check Register")
                .subtitle("Log your work activities(check)")
                .color("success")  // You can choose a different color
                .icon("check-square")  // Using the check-square icon to match the page
                .badge("View")
                .badgeColor("success")
                .actionText("Open Check Register")
                .actionUrl("/user/check-register")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_CHECK_REGISTER)
                .build();
    }
    private DashboardCardDTO createCheckValuesCard() {
        return DashboardCardDTO.builder()
                .title("Check Values Configuration")
                .subtitle("Manage checker productivity values")
                .color("warning")
                .icon("sliders")
                .badge("Config")
                .badgeColor("warning")
                .actionText("Configure Values")
                .actionUrl("/user/check-values")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_CHECK_VALUES)
                .build();
    }

    //General Cards
    private DashboardCardDTO createStatusCard(String role) {
        return DashboardCardDTO.builder()
                .title("User Status")
                .subtitle("User online statuses")
                .color("primary")
                .icon("people-fill")
                .badge("Active")
                .badgeColor("primary")
                .actionText("View Status")
                .actionUrl("/status")
                .external(false)
                .permission(role.equals(SecurityConstants.ROLE_ADMIN) ?
                        PermissionFilterService.PERMISSION_VIEW_STATUS_ADMIN : PermissionFilterService.PERMISSION_VIEW_STATUS_USER)
                .build();
    }
    private DashboardCardDTO createOMSSystemCard() {
        return DashboardCardDTO.builder()
                .title("OMS System")
                .subtitle("Access OMS Portal")
                .color("info")
                .icon("link-45deg")
                .badge("External")
                .badgeColor("info")
                .actionText("Go to OMS")
                .actionUrl("https://oms.cottontex.ro")
                .external(true)
                .permission(PermissionFilterService.PERMISSION_ACCESS_OMS)
                .build();
    }
    private DashboardCardDTO createSessionCard() {
        return DashboardCardDTO.builder()
                .title("Work Session")
                .subtitle("Manage your work time")
                .color("success")
                .icon("play-circle-fill")
                .badge("Active")
                .badgeColor("success")
                .actionText("Work Session")
                .actionUrl("/user/session")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_SESSION)
                .build();
    }
    private DashboardCardDTO createRegisterCard() {
        return DashboardCardDTO.builder()
                .title("Work Register")
                .subtitle("Log your work activities")
                .color("primary")
                .icon("journal-text")
                .badge("View")
                .badgeColor("primary")
                .actionText("Open Register")
                .actionUrl("/user/register")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_USER_REGISTER)
                .build();
    }
    private DashboardCardDTO createWorkTimeManagementCard() {
        return DashboardCardDTO.builder()
                .title("Work Time Management")
                .subtitle("Manage Time")
                .color("info")
                .icon("calendar-fill")
                .badge("Request/View")
                .badgeColor("info")
                .actionText("Edit Work Time")
                .actionUrl("/user/time-management")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_REQUEST_TIMEOFF)
                .build();
    }
    private DashboardCardDTO createSettingsCard(String role) {
        return DashboardCardDTO.builder()
                .title("Settings")
                .subtitle(role.equals(SecurityConstants.ROLE_ADMIN) ? "Manage CTTT Users" : "User account settings")
                .color("secondary")
                .icon("gear-fill")
                .badge(role.equals(SecurityConstants.ROLE_ADMIN) ? "Admin" : "User")
                .badgeColor("secondary")
                .actionText(role.equals(SecurityConstants.ROLE_ADMIN) ? "Manage Settings" : "Manage Account")
                .actionUrl(role.equals(SecurityConstants.ROLE_ADMIN) ? "/admin/settings" : "/user/settings")
                .external(false)
                .permission(role.equals(SecurityConstants.ROLE_ADMIN) ?
                        PermissionFilterService.PERMISSION_MANAGE_SETTINGS : PermissionFilterService.PERMISSION_MANAGE_ACCOUNT)
                .build();
    }

    // Admin Cards
    private DashboardCardDTO createAdminWorktimeCard() {
        return DashboardCardDTO.builder()
                .title("Work Time")
                .subtitle("Manage employees hours")
                .color("warning")
                .icon("clock-fill")
                .badge("Manage")
                .badgeColor("warning")
                .actionText("View Hours")
                .actionUrl("/admin/worktime")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_VIEW_WORKTIME_ADMIN)
                .build();
    }
    private DashboardCardDTO createAdminRegisterCard() {
        return DashboardCardDTO.builder()
                .title("Work Register Manager")
                .subtitle("Manage work activities")
                .color("primary")
                .icon("journal-text")
                .badge("View")
                .badgeColor("primary")
                .actionText("Manage Register")
                .actionUrl("/admin/register")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_ADMIN_REGISTER)
                .build();
    }
    private DashboardCardDTO createAdminCheckRegisterCard() {
        return DashboardCardDTO.builder()
                .title("Check Register Manager")
                .subtitle("Manage work checking activities")
                .color("warning")
                .icon("check-square")
                .badge("Admin")
                .badgeColor("warning")
                .actionText("Manage Check Register")
                .actionUrl("/user/check-register")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_ADMIN_CHECKING)
                .build();
    }
    private DashboardCardDTO createAdminBonusCard() {
        return DashboardCardDTO.builder()
                .title("Admin Bonus Management")
                .subtitle("Manage Bonus")
                .color("primary")
                .icon("journal-text")
                .badge("View")
                .badgeColor("primary")
                .actionText("Manage Register")
                .actionUrl("/admin/bonus")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_BONUS)
                .build();
    }
    private DashboardCardDTO createAdminStatistics() {
        return DashboardCardDTO.builder()
                .title("Admin Statistics Management")
                .subtitle("CT3 Statistics")
                .color("primary")
                .icon("journal-text")
                .badge("View")
                .badgeColor("primary")
                .actionText("Manage Statistics")
                .actionUrl("/admin/statistics")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_STATISTICS)
                .build();
    }
    private DashboardCardDTO createAdminHolidaysCard() {
        return DashboardCardDTO.builder()
                .title("Holidays")
                .subtitle("Update paid holidays")
                .color("info")
                .icon("calendar-fill")
                .badge("Manage")
                .badgeColor("info")
                .actionText("Holiday List")
                .actionUrl("/admin/holidays")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_HOLIDAYS)
                .build();
    }

    //Team Lead Cards
    private DashboardCardDTO createTeamStatisticsCard() {
        return DashboardCardDTO.builder()
                .title("Team Statistics")
                .subtitle("View team statistics")
                .color("primary")
                .icon("graph-up")
                .badge("Stats")
                .badgeColor("primary")
                .actionText("View Stats")
                .actionUrl("/user/stats")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_VIEW_TEAM_STATS)
                .build();
    }
    private DashboardCardDTO createTeamCheckRegisterCard() {
        return DashboardCardDTO.builder()
                .title("Team Check Register")
                .subtitle("Manage team checking activities")
                .color("info")
                .icon("check-square")
                .badge("Team")
                .badgeColor("info")
                .actionText("Team Check Register")
                .actionUrl("/team/check-register")
                .external(false)
                .permission(PermissionFilterService.PERMISSION_MANAGE_TEAM_CHECKING)
                .build();
    }

}