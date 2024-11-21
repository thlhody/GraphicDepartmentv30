package com.ctgraphdep.controller;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusDTO;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.OnlineMetricsService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/status")
public class StatusController extends BaseController {
    private final OnlineMetricsService onlineMetricsService;
    private final ObjectMapper objectMapper;
    private final PathConfig pathConfig;

    public StatusController(
            UserService userService,
            FolderStatusService folderStatusService,
            OnlineMetricsService onlineMetricsService, ObjectMapper objectMapper, PathConfig pathConfig) {
        super(userService, folderStatusService);
        this.onlineMetricsService = onlineMetricsService;
        this.objectMapper = objectMapper;
        this.pathConfig = pathConfig;
        LoggerUtil.initialize(this.getClass(), "Initializing Status Controller");
    }

    @GetMapping
    public String getStatus(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing unified status page");

        User currentUser = getUser(userDetails);
        List<UserStatusDTO> userStatuses = onlineMetricsService.getUserStatuses();

        model.addAttribute("userStatuses", userStatuses);
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("onlineCount",
                userStatuses.stream()
                        .filter(status -> "Online".equals(status.getStatus()))
                        .count());

        return "status/status";
    }

    @GetMapping("/refresh")
    public String refreshStatus() {
        return "redirect:/status";
    }

    private List<UserStatusDTO> getAllUserStatuses() {
        return getUserService().getAllUsers().stream()
                .filter(user -> !user.getRole().equals("ROLE_ADMIN"))
                .map(this::createUserStatus)
                .toList();
    }

    private UserStatusDTO createUserStatus(User user) {
        Path sessionPath = pathConfig.getSessionFilePath(user.getUsername(), user.getUserId());
        return UserStatusDTO.builder()
                .username(user.getUsername())
                .name(user.getName())
                .status(getStatus(sessionPath))
                .lastActive(getLastActive(sessionPath))
                .build();
    }

    private String getStatus(Path sessionPath) {
        try {
            if (Files.exists(sessionPath)) {
                WorkUsersSessionsStates session = objectMapper.readValue(
                        sessionPath.toFile(), WorkUsersSessionsStates.class);
                return getStatusDisplay(session.getSessionStatus());
            }
            return "Offline";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error reading session status: " + sessionPath, e);
            return "Error";
        }
    }

    private String getLastActive(Path sessionPath) {
        try {
            if (Files.exists(sessionPath)) {
                WorkUsersSessionsStates session = objectMapper.readValue(
                        sessionPath.toFile(), WorkUsersSessionsStates.class);
                return formatDateTime(session.getDayStartTime());
            }
            return "Never";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error reading last active time: " + sessionPath, e);
            return "Unknown";
        }
    }

    private String getStatusDisplay(String workCode) {
        if (workCode == null) return "Offline";
        return switch (workCode) {
            case WorkCode.WORK_ONLINE -> "Online";
            case WorkCode.WORK_TEMPORARY_STOP -> "Temporary Stop";
            case WorkCode.WORK_OFFLINE -> "Offline";
            default -> "Unknown";
        };
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "Never";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}