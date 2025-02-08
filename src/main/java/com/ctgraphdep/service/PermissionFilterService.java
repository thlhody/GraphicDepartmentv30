package com.ctgraphdep.service;

import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dashboard.DashboardCard;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionFilterService {

    // Admin Permissions
    public static final String PERMISSION_VIEW_STATUS_ADMIN = "VIEW_STATUS_ADMIN";
    public static final String PERMISSION_MANAGE_SETTINGS = "MANAGE_SETTINGS";
    public static final String PERMISSION_MANAGE_HOLIDAYS = "MANAGE_HOLIDAYS";
    public static final String PERMISSION_VIEW_WORKTIME_ADMIN = "VIEW_WORKTIME_ADMIN";
    public static final String PERMISSION_MANAGE_ADMIN_REGISTER = "MANAGE_ADMIN_REGISTER";
    public static final String PERMISSION_MANAGE_BONUS = "MANAGE_BONUS";

    // User Permissions
    public static final String PERMISSION_VIEW_STATUS_USER = "VIEW_STATUS_USER";
    public static final String PERMISSION_MANAGE_SESSION = "MANAGE_SESSION";
    public static final String PERMISSION_MANAGE_ACCOUNT = "MANAGE_ACCOUNT";
    public static final String PERMISSION_REQUEST_TIMEOFF = "REQUEST_TIMEOFF";
    public static final String PERMISSION_VIEW_WORKTIME_USER = "VIEW_WORKTIME_USER";
    public static final String PERMISSION_MANAGE_USER_REGISTER = "MANAGE_USER_REGISTER";
    public static final String PERMISSION_MANAGE_STATISTICS = "MANAGE_STATISTICS";


    // Team Leader Specific Permissions
    public static final String PERMISSION_VIEW_STATUS_TEAM_LEADER = "VIEW_STATUS_TEAM_LEADER";
    public static final String PERMISSION_MANAGE_TEAM = "MANAGE_TEAM";
    public static final String PERMISSION_VIEW_TEAM_WORKTIME = "VIEW_TEAM_WORKTIME";
    public static final String PERMISSION_APPROVE_TIMEOFF = "APPROVE_TIMEOFF";
    public static final String PERMISSION_VIEW_TEAM_STATS = "VIEW_TEAM_STATS";

    // Common Permissions
    public static final String PERMISSION_ACCESS_OMS = "ACCESS_OMS";

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = new HashMap<>();

    static {
        // Admin permissions
        ROLE_PERMISSIONS.put("ADMIN", Set.of(
                PERMISSION_VIEW_STATUS_ADMIN,
                PERMISSION_ACCESS_OMS,
                PERMISSION_MANAGE_SETTINGS,
                PERMISSION_MANAGE_HOLIDAYS,
                PERMISSION_VIEW_WORKTIME_ADMIN,
                PERMISSION_MANAGE_ADMIN_REGISTER,
                PERMISSION_MANAGE_BONUS,
                PERMISSION_MANAGE_STATISTICS
        ));

        // Team Leader permissions
        ROLE_PERMISSIONS.put("TEAM_LEADER", Set.of(
                PERMISSION_VIEW_STATUS_TEAM_LEADER,
                PERMISSION_ACCESS_OMS,
                PERMISSION_MANAGE_SESSION,
                PERMISSION_MANAGE_ACCOUNT,
                PERMISSION_MANAGE_USER_REGISTER,
                PERMISSION_REQUEST_TIMEOFF,
                PERMISSION_VIEW_WORKTIME_USER,
                PERMISSION_MANAGE_TEAM,
                PERMISSION_VIEW_TEAM_WORKTIME,
                PERMISSION_APPROVE_TIMEOFF,
                PERMISSION_VIEW_TEAM_STATS
        ));

        // User permissions
        ROLE_PERMISSIONS.put("USER", Set.of(
                PERMISSION_VIEW_STATUS_USER,
                PERMISSION_ACCESS_OMS,
                PERMISSION_MANAGE_SESSION,
                PERMISSION_MANAGE_ACCOUNT,
                PERMISSION_REQUEST_TIMEOFF,
                PERMISSION_VIEW_WORKTIME_USER,
                PERMISSION_MANAGE_USER_REGISTER
        ));
    }

    public List<DashboardCard> filterCardsByPermission(List<DashboardCard> cards, User user) {
        if (cards == null || user == null) {
            return Collections.emptyList();
        }

        Set<String> userPermissions = getUserPermissions(user);

        return cards.stream()
                .filter(card -> hasPermissionForCard(card, userPermissions, user))
                .collect(Collectors.toList());
    }

    private boolean hasPermissionForCard(DashboardCard card, Set<String> userPermissions, User user) {
        String requiredPermission = card.getPermission();

        // Always allow OMS access and Status view
        if (requiredPermission.equals("ACCESS_OMS") ||
                requiredPermission.equals("VIEW_STATUS_USER")) {
            return true;
        }

        // For all other permissions, check if user has them
        return userPermissions.contains(requiredPermission);
    }

    private Set<String> getUserPermissions(User user) {
        if (user.isAdmin()) {
            return ROLE_PERMISSIONS.get("ADMIN");
        } else if (user.hasRole("TEAM_LEADER")) {
            return ROLE_PERMISSIONS.get("TEAM_LEADER");
        }
        return ROLE_PERMISSIONS.get("USER");
    }

    public void addRolePermissions(String role, Set<String> permissions) {
        if (role != null && permissions != null) {
            ROLE_PERMISSIONS.merge(role, permissions, (existing, newPerms) -> {
                Set<String> merged = new HashSet<>(existing);
                merged.addAll(newPerms);
                return merged;
            });
            LoggerUtil.info(this.getClass(),
                    String.format("Added permissions for role %s: %s",
                            role, String.join(", ", permissions)));
        }
    }
}