package com.ctgraphdep.dashboard;

import com.ctgraphdep.dashboard.config.DashboardConfig;
import com.ctgraphdep.model.dto.dashboard.DashboardCardDTO;
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
                .role("ADMIN")
                .refreshEnabled(true)
                .refreshInterval(30000)
                .cards(Arrays.asList(
                        createStatusCard("ADMIN"),
                        createWorktimeCard(),
                        createAdminRegisterCard(),
                        createAdminCheckRegisterCard(),
                        createAdminBonusCard(),
                        createAdminStatistics(),
                        createHolidaysCard(),
                        createSettingsCard(),
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
                .role("TEAM_LEADER")
                .refreshEnabled(true)
                .refreshInterval(45000)
                .cards(Arrays.asList(
                        createStatusCard("ADMIN"),  // Using admin view for status
                        createTeamLeadSessionCard(),
                        createTeamLeadRegisterCard(),
                        createTeamLeadWorktimeCard(),
                        createTeamLeadTimeOffCard(),
                        createTeamStatisticsCard(),
                        createOMSSystemCard(),
                        createUserSettingsCard(),
                        createTeamCheckRegisterCard()
                ))
                .build();
    }

    @Bean
    @Qualifier("teamCheckingDashboardConfig")
    public DashboardConfig teamCheckingDashboardConfig() {
        return DashboardConfig.builder()
                .title("Team Checking Dashboard")
                .description("Team Checking Management Dashboard")
                .role("TL_CHECKING")
                .refreshEnabled(true)
                .refreshInterval(45000)
                .cards(Arrays.asList(
                        createStatusCard("ADMIN"),  // Using admin view for status
                        createTeamLeadSessionCard(),
                        createTeamLeadRegisterCard(),
                        createTeamLeadWorktimeCard(),
                        createTeamLeadTimeOffCard(),
                        createTeamCheckRegisterCard(),
                        createTeamStatisticsCard(),
                        createOMSSystemCard(),
                        createUserSettingsCard()


                ))
                .build();
    }

    @Bean
    @Qualifier("userCheckingDashboardConfig")
    public DashboardConfig userCheckingDashboardConfig() {
        return DashboardConfig.builder()
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
                        createUserCheckRegisterCard(),
                        createUserSettingsCard(),
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
                .role("CHECKING")
                .refreshEnabled(true)
                .refreshInterval(60000)
                .cards(Arrays.asList(
                        createStatusCard("USER"),
                        createSessionCard(),
                        createUserCheckRegisterCard(),
                        createUserWorktimeCard(),
                        createTimeOffCard(),
                        createUserSettingsCard(),
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

    //Checking Cards
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
                .permission("MANAGE_ADMIN_CHECKING")
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
                .actionUrl("/user/check-register")
                .external(false)
                .permission("MANAGE_TEAM_CHECKING")
                .build();
    }
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
                .permission("MANAGE_USER_CHECKING")
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
                .permission(role.equals("ADMIN") ? "VIEW_STATUS_ADMIN" : "VIEW_STATUS_USER")
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
                .permission("ACCESS_OMS")
                .build();
    }

    // Admin Cards
    private DashboardCardDTO createSettingsCard() {
        return DashboardCardDTO.builder()
                .title("Settings")
                .subtitle("Mange CTTT Users")
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
    private DashboardCardDTO createWorktimeCard() {
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
                .permission("VIEW_WORKTIME_ADMIN")
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
                .permission("MANAGE_USER_REGISTER")
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
                .permission("MANAGE_BONUS")
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
                .permission("MANAGE_STATISTICS")
                .build();
    }
    private DashboardCardDTO createHolidaysCard() {
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
                .permission("MANAGE_HOLIDAYS")
                .build();
    }
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
                .permission("VIEW_TEAM_STATS")
                .build();
    }
    private DashboardCardDTO createTeamLeadSessionCard() {
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
                .permission("MANAGE_SESSION")
                .build();
    }
    private DashboardCardDTO createTeamLeadRegisterCard() {
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
                .permission("MANAGE_USER_REGISTER")
                .build();
    }
    private DashboardCardDTO createTeamLeadWorktimeCard() {
        return DashboardCardDTO.builder()
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
    private DashboardCardDTO createTeamLeadTimeOffCard() {
        return DashboardCardDTO.builder()
                .title("Time Off")
                .subtitle("Request leave")
                .color("info")
                .icon("calendar-fill")
                .badge("Request")
                .badgeColor("info")
                .actionText("Add Time Off")
                .actionUrl("/user/timeoff")
                .external(false)
                .permission("REQUEST_TIMEOFF")
                .build();
    }

    // User Cards
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
                .permission("MANAGE_SESSION")
                .build();
    }
    private DashboardCardDTO createUserSettingsCard() {
        return DashboardCardDTO.builder()
                .title("Settings")
                .subtitle("User account settings")
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
    private DashboardCardDTO createTimeOffCard() {
        return DashboardCardDTO.builder()
                .title("Time Off")
                .subtitle("Request leave")
                .color("info")
                .icon("calendar-fill")
                .badge("Request")
                .badgeColor("info")
                .actionText("Add Time Off")
                .actionUrl("/user/timeoff")
                .external(false)
                .permission("REQUEST_TIMEOFF")
                .build();
    }
    private DashboardCardDTO createUserWorktimeCard() {
        return DashboardCardDTO.builder()
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
    private DashboardCardDTO createUserRegisterCard() {
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
                .permission("MANAGE_USER_REGISTER")
                .build();
    }
}