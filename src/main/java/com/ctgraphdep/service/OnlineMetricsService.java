package com.ctgraphdep.service;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusDTO;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OnlineMetricsService {
    private final UserService userService;
    private final DataAccessService dataAccess;
    private static final TypeReference<WorkUsersSessionsStates> SESSION_TYPE = new TypeReference<>() {};

    public OnlineMetricsService(
            UserService userService,
            DataAccessService dataAccess) {
        this.userService = userService;
        this.dataAccess = dataAccess;
        LoggerUtil.initialize(this.getClass(), null);
    }

    public int getOnlineUserCount() {
        return (int) getUserStatuses().stream().filter(status -> WorkCode.WORK_ONLINE.equals(status.getStatus())).count();
    }

    public int getActiveUserCount() {
        return (int) getUserStatuses().stream().filter(status -> !WorkCode.WORK_OFFLINE.equals(status.getStatus())).count();
    }

    public List<UserStatusDTO> getUserStatuses() {
        // Filter out administrators using multiple criteria
        List<User> regularUsers = userService.getAllUsers().stream()
                .filter(user -> !user.isAdmin() &&
                        !user.getRole().equals("ADMIN") &&
                        !user.getRole().equals("ADMINISTRATOR") &&
                        !user.getUsername().equalsIgnoreCase("admin"))
                .toList();

        return regularUsers.stream()
                .map(this::getUserStatus)
                .collect(Collectors.toList());
    }

    private UserStatusDTO getUserStatus(User user) {
        try {
            if (dataAccess.networkSessionExists(user.getUsername(), user.getUserId())) {
                WorkUsersSessionsStates session = dataAccess.readNetworkSessionFile(
                        user.getUsername(),
                        user.getUserId()
                );

                if (session != null) {
                    return buildUserStatusDTO(user, session);
                }
            }
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error reading network session for user %s: %s",
                            user.getUsername(), e.getMessage()));
        }

        return createOfflineStatus(user);
    }

    private UserStatusDTO buildUserStatusDTO(User user, WorkUsersSessionsStates session) {
        return UserStatusDTO.builder()
                .username(user.getUsername())
                .userId(user.getUserId()) // Make sure to include the userId
                .name(user.getName())
                .status(determineStatus(session.getSessionStatus()))
                .lastActive(formatDateTime(session.getLastActivity()))
                .build();
    }

    private UserStatusDTO createOfflineStatus(User user) {
        return UserStatusDTO.builder()
                .username(user.getUsername())
                .userId(user.getUserId()) // Make sure to include the userId
                .name(user.getName())
                .status(WorkCode.WORK_OFFLINE)
                .lastActive(WorkCode.LAST_ACTIVE_NEVER)
                .build();
    }

    private String determineStatus(String workCode) {
        if (workCode == null) {
            return WorkCode.WORK_OFFLINE;
        }

        return switch (workCode) {
            case WorkCode.WORK_ONLINE -> WorkCode.WORK_ONLINE;
            case WorkCode.WORK_TEMPORARY_STOP -> WorkCode.WORK_TEMPORARY_STOP;
            case WorkCode.WORK_OFFLINE -> WorkCode.WORK_OFFLINE;
            default -> WorkCode.STATUS_UNKNOWN;
        };
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return WorkCode.LAST_ACTIVE_NEVER;
        }
        return dateTime.format(WorkCode.INPUT_FORMATTER);
    }
}