package com.ctgraphdep.service;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.dashboard.DashboardCardDTO;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionFilterService {
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = new HashMap<>();

    // Admin Permissions
    public static final String PERMISSION_VIEW_STATUS_ADMIN = "VIEW_STATUS_ADMIN";
    public static final String PERMISSION_MANAGE_SETTINGS = "MANAGE_SETTINGS";
    public static final String PERMISSION_MANAGE_HOLIDAYS = "MANAGE_HOLIDAYS";
    public static final String PERMISSION_VIEW_WORKTIME_ADMIN = "VIEW_WORKTIME_ADMIN";
    public static final String PERMISSION_MANAGE_ADMIN_REGISTER = "MANAGE_ADMIN_REGISTER";
    public static final String PERMISSION_MANAGE_BONUS = "MANAGE_BONUS";
    public static final String PERMISSION_MANAGE_STATISTICS = "MANAGE_STATISTICS";
    public static final String PERMISSION_MANAGE_ADMIN_CHECKING = "MANAGE_ADMIN_CHECKING";
    public static final String PERMISSION_MANAGE_CHECKING_BONUS = "MANAGE_CHECKING_BONUS";
    public static final String PERMISSION_VIEW_STATS_CHECKING = "VIEW_STATS_CHECKING";

    // Team Leader Specific Permissions
    public static final String PERMISSION_VIEW_TEAM_STATS = "VIEW_TEAM_STATS";
    public static final String PERMISSION_MANAGE_TEAM_CHECKING = "MANAGE_TEAM_CHECKING";
    public static final String PERMISSION_MANAGE_CHECK_VALUES = "MANAGE_CHECK_VALUES";

    // User Permissions
    public static final String PERMISSION_VIEW_STATUS_USER = "VIEW_STATUS_USER";
    public static final String PERMISSION_MANAGE_ACCOUNT = "MANAGE_ACCOUNT";
    public static final String PERMISSION_MANAGE_SESSION = "MANAGE_SESSION";
    public static final String PERMISSION_VIEW_WORKTIME_USER = "VIEW_WORKTIME_USER";
    public static final String PERMISSION_REQUEST_TIMEOFF = "REQUEST_TIMEOFF";
    public static final String PERMISSION_MANAGE_USER_REGISTER = "MANAGE_USER_REGISTER";

    // Check user Permissions
    public static final String PERMISSION_MANAGE_CHECK_REGISTER = "MANAGE_CHECK_REGISTER";
    // Common Permissions
    public static final String PERMISSION_ACCESS_OMS = "ACCESS_OMS";

    public List<DashboardCardDTO> filterCardsByPermission(List<DashboardCardDTO> cards, User user) {
        if (cards == null || user == null) {
            return Collections.emptyList();
        }

        Set<String> userPermissions = getUserPermissions(user);

        return cards.stream()
                .filter(card -> hasPermissionForCard(card, userPermissions))
                .collect(Collectors.toList());
    }

    private boolean hasPermissionForCard(DashboardCardDTO card, Set<String> userPermissions) {
        String requiredPermission = card.getPermission();

        // Remove the hardcoded permission overrides - rely on proper permission assignments
        return userPermissions.contains(requiredPermission);
    }

    private Set<String> getUserPermissions(User user) {
        if (user.isAdmin()) {
            return ROLE_PERMISSIONS.get(SecurityConstants.ROLE_ADMIN);
        } else if (user.hasRole(SecurityConstants.ROLE_TL_CHECKING)) {
            return ROLE_PERMISSIONS.get(SecurityConstants.ROLE_TL_CHECKING);
        } else if (user.hasRole(SecurityConstants.ROLE_TEAM_LEADER)) {
            return ROLE_PERMISSIONS.get(SecurityConstants.ROLE_TEAM_LEADER);
        } else if (user.hasRole(SecurityConstants.ROLE_USER_CHECKING)) {
            return ROLE_PERMISSIONS.get(SecurityConstants.ROLE_USER_CHECKING);
        } else if (user.hasRole(SecurityConstants.ROLE_CHECKING)) {
            return ROLE_PERMISSIONS.get(SecurityConstants.ROLE_CHECKING);
        }
        return ROLE_PERMISSIONS.getOrDefault(SecurityConstants.ROLE_USER, Collections.emptySet());
    }

    public void addRolePermissions(String role, Set<String> permissions) {
        if (role != null && permissions != null) {
            // Use put instead of merge since we're only adding permissions once now
            ROLE_PERMISSIONS.put(role, new HashSet<>(permissions));
            LoggerUtil.info(this.getClass(), String.format("Added permissions for role %s: %s", role, String.join(", ", permissions)));
        }
    }
}
