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
                        createWorktimeCard(),
                        createOMSSystemCard(),
                        createHolidaysCard(),
                        createSettingsCard()
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
                        createUserWorktimeCard(),
                        createTimeOffCard(),
                        createOMSSystemCard(),
                        createUserSettingsCard(),
                        createUserRegisterCard()
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

    // User Cards
    private DashboardCard createSessionCard() {
        return DashboardCard.builder()
                .title("Work Session")
                .subtitle("Manage your work time")
                .color("success")
                .icon("play-circle-fill")
                .badge("Active")
                .badgeColor("success")
                .actionText("Start Work")
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