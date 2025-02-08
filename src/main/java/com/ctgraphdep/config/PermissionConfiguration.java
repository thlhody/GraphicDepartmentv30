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

        // Initialize role permissions - these are in addition to the default ones in PermissionFilterService

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
                PermissionFilterService.PERMISSION_MANAGE_ADMIN_REGISTER
        ));

        // Could add other custom roles here
        // For example, if you wanted to add a Team Leader role:
        service.addRolePermissions("TEAM_LEADER", Set.of(
                PermissionFilterService.PERMISSION_VIEW_STATUS_ADMIN,
                PermissionFilterService.PERMISSION_VIEW_WORKTIME_USER,
                PermissionFilterService.PERMISSION_MANAGE_SESSION,
                PermissionFilterService.PERMISSION_MANAGE_USER_REGISTER,
                PermissionFilterService.PERMISSION_REQUEST_TIMEOFF

        ));

        return service;
    }
}