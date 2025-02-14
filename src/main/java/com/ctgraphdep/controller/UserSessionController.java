package com.ctgraphdep.controller;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.CalculateWorkHoursUtil;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@PreAuthorize("isAuthenticated()")
@RequestMapping({"/user/session", "/team-lead/session"})
public class UserSessionController extends BaseController {

    private final UserSessionService userSessionService;
    private final SessionPersistenceService persistenceService;
    private final UserService userService;

    @Autowired
    public UserSessionController(
            UserSessionService userSessionService,
            UserService userService,
            FolderStatusService folderStatusService,
            SessionPersistenceService persistenceService) {
        super(userService, folderStatusService);
        this.userSessionService = userSessionService;
        this.userService = userService;
        this.persistenceService = persistenceService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String getSessionPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = getUser(userDetails);
        String basePath = user.getRole().equals("TEAM_LEADER") ? "/team-lead" : "/user";
        if (user.isAdmin()) {
            return "redirect:/admin/dashboard";
        }

        try {
            // Get or create session through service
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(
                    user.getUsername(),
                    user.getUserId()
            );

            LoggerUtil.debug(this.getClass(),
                    String.format("Session status before formatting: %s", session.getSessionStatus()));

            String formattedStatus = getFormattedStatus(session.getSessionStatus());

            LoggerUtil.debug(this.getClass(),
                    String.format("Formatted session status: %s", formattedStatus));

            model.addAttribute("sessionStatus", formattedStatus);

            // Verify session ownership
            if (!session.getUsername().equals(user.getUsername())) {
                LoggerUtil.warn(this.getClass(),
                        "Session ownership mismatch for user: " + user.getUsername());
                session = null;
            }

            // Calculate work time if active
            if (isActiveSession(session)) {
                updateActiveSession(session);  // Remove the schedule parameter
            }

            // Populate model
            assert session != null;
            populateSessionModel(model, session);

            return basePath.substring(1) + "/session";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading session page: " + e.getMessage());
            model.addAttribute("error", "Error loading session data");
            return basePath.substring(1) + "/session";
        }
    }
    private boolean verifySessionOwnership(String username, WorkUsersSessionsStates session) {
        return session != null && session.getUsername().equals(username);
    }

    @PostMapping("/start")
    public String startSession(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserOrThrow(userDetails);
        String basePath = user.getRole().equals("TEAM_LEADER") ? "/team-lead" : "/user";

        // Clear any existing session first
        WorkUsersSessionsStates existingSession = userSessionService.getCurrentSession(
                user.getUsername(),
                user.getUserId()
        );

        if (existingSession != null && !verifySessionOwnership(user.getUsername(), existingSession)) {
            return "redirect:" + basePath + "/session";
        }

        userSessionService.startDay(
                user.getUsername(),
                user.getUserId()
        );

        return "redirect:" + basePath + "/session?action=start";
    }

    @PostMapping("/temp-stop")
    public String toggleTemporaryStop(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserOrThrow(userDetails);
        String basePath = user.getRole().equals("TEAM_LEADER") ? "/team-lead" : "/user";
        WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(
                user.getUsername(),
                user.getUserId()
        );

        if (currentSession != null) {
            if (WorkCode.WORK_ONLINE.equals(currentSession.getSessionStatus())) {
                userSessionService.startTemporaryStop(user.getUsername(), user.getUserId());
                return "redirect:" + basePath + "/session?action=pause";
            } else if (WorkCode.WORK_TEMPORARY_STOP.equals(currentSession.getSessionStatus())) {
                userSessionService.resumeFromTemporaryStop(user.getUsername(), user.getUserId());
                return "redirect:" + basePath + "/session?action=resume";
            }

            return "redirect:" + basePath + "/session";
        }
        return "redirect:" + basePath + "/session";
    }

    @PostMapping("/end")
    public String endSession(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserOrThrow(userDetails);
        String basePath = user.getRole().equals("TEAM_LEADER") ? "/team-lead" : "/user";
        WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(
                user.getUsername(),
                user.getUserId()
        );

        // First check if session is already offline
        if (currentSession == null || WorkCode.WORK_OFFLINE.equals(currentSession.getSessionStatus())) {
            return "redirect:" + basePath + "/session";
        }

        if (WorkCode.WORK_ONLINE.equals(currentSession.getSessionStatus())) {
            userSessionService.endDay(
                    user.getUsername(),
                    user.getUserId(),
                    currentSession.getFinalWorkedMinutes()
            );
            return "redirect:" + basePath + "/session?action=end";
        }

        return "redirect:" + basePath + "/session";
    }

    private boolean isActiveSession(WorkUsersSessionsStates session) {
        return session != null && (
                WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())
        );
    }

    private void updateActiveSession(WorkUsersSessionsStates session) {
        try {
            // Get user schedule
            User user = userService.getUserById(session.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Persist session using the SessionPersistenceService
            persistenceService.persistSession(session);

            LoggerUtil.debug(this.getClass(),
                    String.format("Updated active session for user %s", session.getUsername()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error updating active session: %s", e.getMessage()));
        }
    }

    private void populateSessionModel(Model model, WorkUsersSessionsStates session) {
        //initial values
        model.addAttribute("sessionStatus", getFormattedStatus(session.getSessionStatus()));
        model.addAttribute("currentDateTime", CalculateWorkHoursUtil.formatDateTime(LocalDateTime.now()));
        model.addAttribute("dayStartTime", CalculateWorkHoursUtil.formatDateTime(session.getDayStartTime()));
        //work values
        model.addAttribute("totalWorkRaw", formatWorkTime(session.getTotalWorkedMinutes()));
        model.addAttribute("actualWorkTime", formatWorkTime(session.getFinalWorkedMinutes()));
        model.addAttribute("lunchBreakStatus", session.getLunchBreakDeducted());
        model.addAttribute("overtime", formatWorkTime(session.getTotalOvertimeMinutes()));
        //temporary stop values
        model.addAttribute("lastTemporaryStopTime", formatLastTempStopTime(session));
        model.addAttribute("temporaryStopCount", getTemporaryStopCount(session));
        model.addAttribute("totalTemporaryStopTime", formatWorkTime(calculateTotalBreakMinutes(session)));

        LoggerUtil.debug(this.getClass(), "Model attributes: " + model.asMap());
    }

    //helper methods
    private String formatWorkTime(Integer minutes) {
        return minutes != null ? CalculateWorkHoursUtil.minutesToHHmm(minutes) : "--:--";
    }
    private int getTemporaryStopCount(WorkUsersSessionsStates session) {
        return session != null && session.getTemporaryStopCount() != null ? session.getTemporaryStopCount() : 0;
    }
    private String formatLastTempStopTime(WorkUsersSessionsStates session) {
        return session != null && session.getLastTemporaryStopTime() != null ? CalculateWorkHoursUtil.formatDateTime(session.getLastTemporaryStopTime()) : "--:--";
    }
    private int calculateTotalBreakMinutes(WorkUsersSessionsStates session) {
        // Calculate current break duration if in temporary stop
        int currentBreakMinutes = 0;
        if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()) && session.getLastTemporaryStopTime() != null) {
            currentBreakMinutes = CalculateWorkHoursUtil.calculateMinutesBetween(session.getLastTemporaryStopTime(), LocalDateTime.now());
        }
        // Add current break to total temporary stop minutes
        return (session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0) + currentBreakMinutes;
    }
    private String getFormattedStatus(String status) {
        if (status == null) return "Offline";
        LoggerUtil.debug(this.getClass(),
                String.format("Converting status from '%s' to formatted status", status));

        return switch (status) {
            case WorkCode.WORK_ONLINE -> "Online";
            case WorkCode.WORK_TEMPORARY_STOP -> "Temporary Stop";
            default -> WorkCode.WORK_OFFLINE;
        };
    }

    private User getUserOrThrow(UserDetails userDetails) {
        return userService.getUserByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}