package com.ctgraphdep.config;

import com.ctgraphdep.service.PermissionFilterService;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.HashSet;
import java.util.Set;

@Configuration
public class PermissionConfiguration {

    @Bean
    public PermissionFilterService permissionFilterService() {
        PermissionFilterService service = new PermissionFilterService();

        // Define base permissions sets - common to all users
        Set<String> baseUserPermissions = Set.of(
                PermissionFilterService.PERMISSION_ACCESS_OMS,
                PermissionFilterService.PERMISSION_MANAGE_ACCOUNT,
                PermissionFilterService.PERMISSION_MANAGE_SESSION,
                PermissionFilterService.PERMISSION_VIEW_WORKTIME_USER,
                PermissionFilterService.PERMISSION_REQUEST_TIMEOFF
        );

        // Regular USER permissions
        Set<String> userPermissions = new HashSet<>(baseUserPermissions);
        userPermissions.add(PermissionFilterService.PERMISSION_VIEW_STATUS_USER);
        userPermissions.add(PermissionFilterService.PERMISSION_MANAGE_USER_REGISTER);
        service.addRolePermissions(SecurityConstants.ROLE_USER, userPermissions);

        // CHECKING permissions
        Set<String> checkingPermissions = new HashSet<>(baseUserPermissions);
        checkingPermissions.add(PermissionFilterService.PERMISSION_VIEW_STATUS_USER);
        checkingPermissions.add(PermissionFilterService.PERMISSION_MANAGE_CHECK_REGISTER);
        service.addRolePermissions(SecurityConstants.ROLE_CHECKING, checkingPermissions);

        // USER_CHECKING permissions (combines User + Checking)
        Set<String> userCheckingPermissions = new HashSet<>(userPermissions);
        userCheckingPermissions.add(PermissionFilterService.PERMISSION_MANAGE_CHECK_REGISTER);
        service.addRolePermissions(SecurityConstants.ROLE_USER_CHECKING, userCheckingPermissions);

        // TEAM_LEADER permissions
        Set<String> teamLeaderPermissions = new HashSet<>(userPermissions);
        teamLeaderPermissions.remove(PermissionFilterService.PERMISSION_VIEW_STATUS_USER);
        teamLeaderPermissions.add(PermissionFilterService.PERMISSION_VIEW_STATUS_ADMIN);
        teamLeaderPermissions.add(PermissionFilterService.PERMISSION_VIEW_TEAM_STATS);
        service.addRolePermissions(SecurityConstants.ROLE_TEAM_LEADER, teamLeaderPermissions);

        // TL_CHECKING permissions (Team Leader + Checking)
        Set<String> tlCheckingPermissions = new HashSet<>(teamLeaderPermissions);
        tlCheckingPermissions.add(PermissionFilterService.PERMISSION_MANAGE_CHECK_REGISTER);
        tlCheckingPermissions.add(PermissionFilterService.PERMISSION_VIEW_STATS_CHECKING);
        tlCheckingPermissions.add(PermissionFilterService.PERMISSION_MANAGE_TEAM_CHECKING);
        tlCheckingPermissions.add(PermissionFilterService.PERMISSION_MANAGE_CHECK_VALUES);
        service.addRolePermissions(SecurityConstants.ROLE_TL_CHECKING, tlCheckingPermissions);

        // ADMIN permissions (includes everything)
        Set<String> adminPermissions = getAdminPermissions(tlCheckingPermissions);
        service.addRolePermissions(SecurityConstants.ROLE_ADMIN, adminPermissions);

        return service;
    }

    private static @NotNull Set<String> getAdminPermissions(Set<String> tlCheckingPermissions) {
        Set<String> adminPermissions = new HashSet<>(tlCheckingPermissions); // Include all TL_CHECKING permissions

        // Add admin-specific permissions
        adminPermissions.add(PermissionFilterService.PERMISSION_MANAGE_SETTINGS);
        adminPermissions.add(PermissionFilterService.PERMISSION_MANAGE_HOLIDAYS);
        adminPermissions.add(PermissionFilterService.PERMISSION_VIEW_WORKTIME_ADMIN);
        adminPermissions.add(PermissionFilterService.PERMISSION_MANAGE_ADMIN_REGISTER);
        adminPermissions.add(PermissionFilterService.PERMISSION_MANAGE_BONUS);
        adminPermissions.add(PermissionFilterService.PERMISSION_MANAGE_STATISTICS);
        adminPermissions.add(PermissionFilterService.PERMISSION_MANAGE_ADMIN_CHECKING);
        adminPermissions.add(PermissionFilterService.PERMISSION_MANAGE_CHECKING_BONUS);
        return adminPermissions;
    }
}