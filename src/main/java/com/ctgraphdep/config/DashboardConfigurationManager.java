package com.ctgraphdep.config;

import com.ctgraphdep.model.dashboard.DashboardCard;
import com.ctgraphdep.model.dashboard.DashboardConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class DashboardConfigurationManager {

    @Bean
    @Qualifier("adminDashboardConfig")
    public DashboardConfiguration adminDashboardConfig() {
        return DashboardConfiguration.builder()
                .title("Admin Dashboard")
                .description("System Administration Dashboard")
                .role("ADMIN")
                .refreshEnabled(true)
                .refreshInterval(30000)
                .cards(Arrays.asList(
                        createStatusCard("ADMIN"),
                        createAdminRegisterCard(),
                        createWorktimeCard(),
                        createAdminBonusCard(),
                        createOMSSystemCard(),
                        createHolidaysCard(),
                        createSettingsCard(),
                        createAdminStatistics()
                ))
                .build();
    }

    @Bean
    @Qualifier("teamLeadDashboardConfig")
    public DashboardConfiguration teamLeadDashboardConfig() {
        return DashboardConfiguration.builder()
                .title("Team Lead Dashboard")
                .description("Team Management Dashboard")
                .role("TEAM_LEADER")
                .refreshEnabled(true)
                .refreshInterval(45000)
                .cards(Arrays.asList(
                        createStatusCard("ADMIN"),
                        createTeamLeadSessionCard(),
                        createTeamLeadRegisterCard(),
                        createTeamLeadWorktimeCard(),
                        createTeamLeadTimeOffCard(),
                        createOMSSystemCard(),
                        createUserSettingsCard(),
                        createTeamStatisticsCard()
                ))
                .build();
    }

    @Bean
    @Qualifier("teamCheckingDashboardConfig")
    public DashboardConfiguration teamCheckingDashboardConfig() {
        return DashboardConfiguration.builder()
                .title("Team Checking Dashboard")
                .description("Team Management Dashboard")
                .role("TL_CHECKING")
                .refreshEnabled(true)
                .refreshInterval(45000)
                .cards(Arrays.asList(
                        createStatusCard("ADMIN"),
                        createTeamLeadSessionCard(),
                        createTeamLeadRegisterCard(),
                        createTeamLeadWorktimeCard(),
                        createTeamLeadTimeOffCard(),
                        createOMSSystemCard(),
                        createUserSettingsCard(),
                        createTeamStatisticsCard()
                        ///need to implement a management checking register card
                ))
                .build();
    }

    @Bean
    @Qualifier("checkingDashboardConfig")
    public DashboardConfiguration checkingDashboardConfig() {
        return DashboardConfiguration.builder()
                .title("Checking Dashboard")
                .description("User Checking Control Panel")
                .role("CHECKING")
                .refreshEnabled(true)
                .refreshInterval(60000)
                .cards(Arrays.asList(
                        createStatusCard("USER"),
                        createSessionCard(),
                        createUserWorktimeCard(),
                        createTimeOffCard(),
                        createOMSSystemCard(),
                        createUserSettingsCard()
                        /// needs to implement the checking register
                ))
                .build();
    }

    @Bean
    @Qualifier("userCheckingDashboardConfig")
    public DashboardConfiguration userCheckingDashboardConfig() {
        return DashboardConfiguration.builder()
                .title("Checking Dashboard")
                .description("User Checking Control Panel")
                .role("USER_CHECKING")
                .refreshEnabled(true)
                .refreshInterval(60000)
                .cards(Arrays.asList(
                        createStatusCard("USER"),
                        createSessionCard(),
                        createUserRegisterCard(),
                        createUserWorktimeCard(),
                        createTimeOffCard(),
                        createOMSSystemCard(),
                        createUserSettingsCard()
                        /// needs to implement the checking register
                ))
                .build();
    }



    @Bean
    @Qualifier("userDashboardConfig")
    public DashboardConfiguration userDashboardConfig() {
        return DashboardConfiguration.builder()
                .title("User Dashboard")
                .description("User Control Panel")
                .role("USER")
                .refreshEnabled(true)
                .refreshInterval(60000)
                .cards(Arrays.asList(
                        createStatusCard("USER"),
                        createSessionCard(),
                        createUserRegisterCard(),
                        createUserWorktimeCard(),
                        createTimeOffCard(),
                        createOMSSystemCard(),
                        createUserSettingsCard()
                ))
                .build();
    }

    //General Cards
    private DashboardCard createStatusCard(String role) {
        return DashboardCard.builder()
                .title("User Status")
                .subtitle("Monitor active users")
                .color("primary")
                .icon("people-fill")
                .badge("Active")
                .badgeColor("primary")
                .actionText("View Status")
                .actionUrl("/status")
                .external(false)
                .permission(role.equals("ADMIN") ? "VIEW_STATUS_ADMIN" : "VIEW_STATUS_USER")
                .build();
    }

    private DashboardCard createOMSSystemCard() {
        return DashboardCard.builder()
                .title("OMS System")
                .subtitle("Access OMS Portal")
                .color("info")
                .icon("link-45deg")
                .badge("External")
                .badgeColor("info")
                .actionText("Go to OMS")
                .actionUrl("https://oms.cottontex.ro")
                .external(true)
                .permission("ACCESS_OMS")
                .build();
    }
    // Admin Cards
    private DashboardCard createSettingsCard() {
        return DashboardCard.builder()
                .title("Settings")
                .subtitle("System configuration")
                .color("secondary")
                .icon("gear-fill")
                .badge("Admin")
                .badgeColor("secondary")
                .actionText("Manage Settings")
                .actionUrl("/admin/settings")
                .external(false)
                .permission("MANAGE_SETTINGS")
                .build();
    }

    private DashboardCard createWorktimeCard() {
        return DashboardCard.builder()
                .title("Work Time")
                .subtitle("Monitor employee hours")
                .color("warning")
                .icon("clock-fill")
                .badge("Manage")
                .badgeColor("warning")
                .actionText("View Hours")
                .actionUrl("/admin/worktime")
                .external(false)
                .permission("VIEW_WORKTIME_ADMIN")
                .build();
    }

    private DashboardCard createAdminRegisterCard() {
        return DashboardCard.builder()
                .title("Work Register Manager")
                .subtitle("Manage work activities")
                .color("primary")
                .icon("journal-text")
                .badge("View")
                .badgeColor("primary")
                .actionText("Manage Register")
                .actionUrl("/admin/register")
                .external(false)
                .permission("MANAGE_USER_REGISTER")
                .build();
    }

    private DashboardCard createAdminBonusCard() {
        return DashboardCard.builder()
                .title("Admin Bonus Management")
                .subtitle("Manage Bonus")
                .color("primary")
                .icon("journal-text")
                .badge("View")
                .badgeColor("primary")
                .actionText("Manage Register")
                .actionUrl("/admin/bonus")
                .external(false)
                .permission("MANAGE_BONUS")
                .build();
    }

    private DashboardCard createAdminStatistics() {
        return DashboardCard.builder()
                .title("Admin Statistics Management")
                .subtitle("CT3 Statistics")
                .color("primary")
                .icon("journal-text")
                .badge("View")
                .badgeColor("primary")
                .actionText("Manage Statistics")
                .actionUrl("/admin/statistics")
                .external(false)
                .permission("MANAGE_STATISTICS")
                .build();
    }

    private DashboardCard createHolidaysCard() {
        return DashboardCard.builder()
                .title("Holidays")
                .subtitle("Manage paid leave")
                .color("info")
                .icon("calendar-fill")
                .badge("Manage")
                .badgeColor("info")
                .actionText("Holiday List")
                .actionUrl("/admin/holidays")
                .external(false)
                .permission("MANAGE_HOLIDAYS")
                .build();
    }

    private DashboardCard createTeamStatisticsCard() {
        return DashboardCard.builder()
                .title("Team Statistics")
                .subtitle("View team performance")
                .color("primary")
                .icon("graph-up")
                .badge("Stats")
                .badgeColor("primary")
                .actionText("View Stats")
                .actionUrl("/user/stats")
                .external(false)
                .permission("VIEW_TEAM_STATS")
                .build();
    }

    private DashboardCard createTeamLeadSessionCard() {
        return DashboardCard.builder()
                .title("Work Session")
                .subtitle("Manage your work time")
                .color("success")
                .icon("play-circle-fill")
                .badge("Active")
                .badgeColor("success")
                .actionText("Work Session")
                .actionUrl("/user/session")
                .external(false)
                .permission("MANAGE_SESSION")
                .build();
    }

    private DashboardCard createTeamLeadRegisterCard() {
        return DashboardCard.builder()
                .title("Work Register")
                .subtitle("Log your work activities")
                .color("primary")
                .icon("journal-text")
                .badge("View")
                .badgeColor("primary")
                .actionText("Open Register")
                .actionUrl("/user/register")
                .external(false)
                .permission("MANAGE_USER_REGISTER")
                .build();
    }

    private DashboardCard createTeamLeadWorktimeCard() {
        return DashboardCard.builder()
                .title("Work Hours")
                .subtitle("View your hours")
                .color("warning")
                .icon("clock-fill")
                .badge("View")
                .badgeColor("warning")
                .actionText("View Hours")
                .actionUrl("/user/worktime")
                .external(false)
                .permission("VIEW_WORKTIME_USER")
                .build();
    }

    private DashboardCard createTeamLeadTimeOffCard() {
        return DashboardCard.builder()
                .title("Time Off")
                .subtitle("Request leave")
                .color("info")
                .icon("calendar-fill")
                .badge("Request")
                .badgeColor("info")
                .actionText("Request Time Off")
                .actionUrl("/user/timeoff")
                .external(false)
                .permission("REQUEST_TIMEOFF")
                .build();
    }

    // User Cards
    private DashboardCard createSessionCard() {
        return DashboardCard.builder()
                .title("Work Session")
                .subtitle("Manage your work time")
                .color("success")
                .icon("play-circle-fill")
                .badge("Active")
                .badgeColor("success")
                .actionText("Work Session")
                .actionUrl("/user/session")
                .external(false)
                .permission("MANAGE_SESSION")
                .build();
    }

    private DashboardCard createUserSettingsCard() {
        return DashboardCard.builder()
                .title("Settings")
                .subtitle("Account settings")
                .color("secondary")
                .icon("gear-fill")
                .badge("User")
                .badgeColor("secondary")
                .actionText("Manage Account")
                .actionUrl("/user/settings")
                .external(false)
                .permission("MANAGE_ACCOUNT")
                .build();
    }

    private DashboardCard createTimeOffCard() {
        return DashboardCard.builder()
                .title("Time Off")
                .subtitle("Request leave")
                .color("info")
                .icon("calendar-fill")
                .badge("Request")
                .badgeColor("info")
                .actionText("Request Time Off")
                .actionUrl("/user/timeoff")
                .external(false)
                .permission("REQUEST_TIMEOFF")
                .build();
    }

    private DashboardCard createUserWorktimeCard() {
        return DashboardCard.builder()
                .title("Work Hours")
                .subtitle("View your hours")
                .color("warning")
                .icon("clock-fill")
                .badge("View")
                .badgeColor("warning")
                .actionText("View Hours")
                .actionUrl("/user/worktime")
                .external(false)
                .permission("VIEW_WORKTIME_USER")
                .build();
    }

    private DashboardCard createUserRegisterCard() {
        return DashboardCard.builder()
                .title("Work Register")
                .subtitle("Log your work activities")
                .color("primary")
                .icon("journal-text")
                .badge("View")
                .badgeColor("primary")
                .actionText("Open Register")
                .actionUrl("/user/register")
                .external(false)
                .permission("MANAGE_USER_REGISTER")
                .build();
    }
}