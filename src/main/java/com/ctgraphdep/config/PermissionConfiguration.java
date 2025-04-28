package com.ctgraphdep.config;

import com.ctgraphdep.service.PermissionFilterService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration
public class PermissionConfiguration {

    @Bean
    public PermissionFilterService permissionFilterService() {
        PermissionFilterService service = new PermissionFilterService();

        // For Role_User permissions (optional - can customize default user permissions)
        service.addRolePermissions("USER", Set.of(
                PermissionFilterService.PERMISSION_VIEW_STATUS_USER,
                PermissionFilterService.PERMISSION_ACCESS_OMS,
                PermissionFilterService.PERMISSION_MANAGE_SESSION,
                PermissionFilterService.PERMISSION_VIEW_WORKTIME_USER,
                PermissionFilterService.PERMISSION_REQUEST_TIMEOFF,
                PermissionFilterService.PERMISSION_MANAGE_ACCOUNT,
                PermissionFilterService.PERMISSION_MANAGE_USER_REGISTER
        ));

        // For Role_Admin permissions (optional - admins already have all permissions by default)
        service.addRolePermissions("ADMIN", Set.of(
                PermissionFilterService.PERMISSION_VIEW_STATUS_ADMIN,
                PermissionFilterService.PERMISSION_ACCESS_OMS,
                PermissionFilterService.PERMISSION_MANAGE_SETTINGS,
                PermissionFilterService.PERMISSION_MANAGE_HOLIDAYS,
                PermissionFilterService.PERMISSION_MANAGE_SESSION,
                PermissionFilterService.PERMISSION_VIEW_WORKTIME_ADMIN,
                PermissionFilterService.PERMISSION_REQUEST_TIMEOFF,
                PermissionFilterService.PERMISSION_MANAGE_ACCOUNT,
                PermissionFilterService.PERMISSION_MANAGE_USER_REGISTER,
                PermissionFilterService.PERMISSION_MANAGE_ADMIN_REGISTER,
                PermissionFilterService.PERMISSION_MANAGE_BONUS,
                PermissionFilterService.PERMISSION_MANAGE_STATISTICS,
                PermissionFilterService.PERMISSION_MANAGE_ADMIN_CHECKING,
                PermissionFilterService.PERMISSION_VIEW_STATS_CHECKING
        ));

        // For Role_Team_Leader permissions
        service.addRolePermissions("TEAM_LEADER", Set.of(
                PermissionFilterService.PERMISSION_VIEW_STATUS_ADMIN,
                PermissionFilterService.PERMISSION_VIEW_WORKTIME_USER,
                PermissionFilterService.PERMISSION_MANAGE_SESSION,
                PermissionFilterService.PERMISSION_MANAGE_USER_REGISTER,
                PermissionFilterService.PERMISSION_REQUEST_TIMEOFF,
                PermissionFilterService.PERMISSION_VIEW_TEAM_STATS
        ));

        // 1. CHECKING role - has only checking register permissions, like a user focused on checking
        service.addRolePermissions("CHECKING", Set.of(
                PermissionFilterService.PERMISSION_VIEW_STATUS_USER,
                PermissionFilterService.PERMISSION_ACCESS_OMS,
                PermissionFilterService.PERMISSION_MANAGE_SESSION,
                PermissionFilterService.PERMISSION_VIEW_WORKTIME_USER,
                PermissionFilterService.PERMISSION_REQUEST_TIMEOFF,
                PermissionFilterService.PERMISSION_MANAGE_ACCOUNT,
                PermissionFilterService.PERMISSION_MANAGE_USER_CHECKING
        ));

        // 2. USER_CHECKING role - same as user but with checking permissions
        service.addRolePermissions("USER_CHECKING", Set.of(
                PermissionFilterService.PERMISSION_VIEW_STATUS_USER,
                PermissionFilterService.PERMISSION_ACCESS_OMS,
                PermissionFilterService.PERMISSION_MANAGE_SESSION,
                PermissionFilterService.PERMISSION_VIEW_WORKTIME_USER,
                PermissionFilterService.PERMISSION_REQUEST_TIMEOFF,
                PermissionFilterService.PERMISSION_MANAGE_ACCOUNT,
                PermissionFilterService.PERMISSION_MANAGE_USER_REGISTER,
                PermissionFilterService.PERMISSION_MANAGE_USER_CHECKING

        ));

        // 3. TL_CHECKING role - has all permissions of team leader + checking
        service.addRolePermissions("TL_CHECKING", Set.of(
                PermissionFilterService.PERMISSION_VIEW_STATUS_TEAM_LEADER,
                PermissionFilterService.PERMISSION_ACCESS_OMS,
                PermissionFilterService.PERMISSION_MANAGE_SESSION,
                PermissionFilterService.PERMISSION_MANAGE_ACCOUNT,
                PermissionFilterService.PERMISSION_MANAGE_USER_REGISTER,
                PermissionFilterService.PERMISSION_REQUEST_TIMEOFF,
                PermissionFilterService.PERMISSION_VIEW_WORKTIME_USER,
                PermissionFilterService.PERMISSION_MANAGE_TEAM,
                PermissionFilterService.PERMISSION_VIEW_TEAM_STATS,
                PermissionFilterService.PERMISSION_MANAGE_TEAM_CHECKING,
                PermissionFilterService.PERMISSION_VIEW_STATS_CHECKING,
                PermissionFilterService.PERMISSION_MANAGE_CHECK_VALUES
        ));

        return service;
    }
}