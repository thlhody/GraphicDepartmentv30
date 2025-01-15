package com.ctgraphdep.controller;

import com.ctgraphdep.config.PathConfig;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.SessionCalculationService;
import com.ctgraphdep.service.SessionPersistenceService;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.UserSessionService;
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
@RequestMapping("/user/session")
public class UserSessionController extends BaseController {

    private final SessionCalculationService calculationService;
    private final UserSessionService userSessionService;
    private final SessionPersistenceService persistenceService;
    private final PathConfig pathConfig;
    private final UserService userService;

    @Autowired
    public UserSessionController(
            UserSessionService userSessionService,
            UserService userService,
            FolderStatusService folderStatusService,
            SessionCalculationService calculationService,
            SessionPersistenceService persistenceService,
            PathConfig pathConfig) {
        super(userService, folderStatusService);
        this.userSessionService = userSessionService;
        this.calculationService = calculationService;
        this.userService = userService;
        this.persistenceService = persistenceService;
        this.pathConfig = pathConfig;

        LoggerUtil.initialize(this.getClass(), "Initializing User Session Controller");
    }

    @GetMapping
    public String getSessionPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = getUser(userDetails);

        if (user.isAdmin()) {
            return "redirect:/admin/dashboard";
        }

        try {
            // Get or create session through service
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(
                    user.getUsername(),
                    user.getUserId()
            );

            // Verify session ownership
            if (session != null && !session.getUsername().equals(user.getUsername())) {
                LoggerUtil.warn(this.getClass(),
                        "Session ownership mismatch for user: " + user.getUsername());
                session = null;
            }

            // Calculate work time if active
            if (isActiveSession(session)) {
                updateActiveSession(session, user.getSchedule());
            }

            // Populate model
            assert session != null;
            populateSessionModel(model, session);

            return "user/session";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading session page: " + e.getMessage());
            model.addAttribute("error", "Error loading session data");
            return "user/session";
        }
    }
    private boolean verifySessionOwnership(String username, WorkUsersSessionsStates session) {
        return session != null && session.getUsername().equals(username);
    }

    @PostMapping("/start")
    public String startSession(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserOrThrow(userDetails);

        // Clear any existing session first
        WorkUsersSessionsStates existingSession = userSessionService.getCurrentSession(
                user.getUsername(),
                user.getUserId()
        );

        if (existingSession != null && !verifySessionOwnership(user.getUsername(), existingSession)) {
            return "redirect:/user/session";
        }

        userSessionService.startDay(
                user.getUsername(),
                user.getUserId()
        );

        return "redirect:/user/session?action=start";
    }

    @PostMapping("/temp-stop")
    public String toggleTemporaryStop(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserOrThrow(userDetails);
        WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(
                user.getUsername(),
                user.getUserId()
        );

        if (currentSession != null) {
            if (WorkCode.WORK_ONLINE.equals(currentSession.getSessionStatus())) {
                userSessionService.startTemporaryStop(user.getUsername(), user.getUserId());
                return "redirect:/user/session?action=pause";
            } else if (WorkCode.WORK_TEMPORARY_STOP.equals(currentSession.getSessionStatus())) {
                userSessionService.resumeFromTemporaryStop(user.getUsername(), user.getUserId());
                return "redirect:/user/session?action=resume";
            }
        }

        return "redirect:/user/session";
    }

    @PostMapping("/end")
    public String endSession(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserOrThrow(userDetails);
        WorkUsersSessionsStates currentSession = userSessionService.getCurrentSession(
                user.getUsername(),
                user.getUserId()
        );

        // First check if session is already offline
        if (currentSession == null || WorkCode.WORK_OFFLINE.equals(currentSession.getSessionStatus())) {
            return "redirect:/user/session";
        }

        if (WorkCode.WORK_ONLINE.equals(currentSession.getSessionStatus())) {
            calculationService.calculateCurrentWork(currentSession, user.getSchedule());
            userSessionService.endDay(
                    user.getUsername(),
                    user.getUserId(),
                    currentSession.getFinalWorkedMinutes()
            );
            return "redirect:/user/session?action=end";
        }

        return "redirect:/user/session";
    }

    private boolean isActiveSession(WorkUsersSessionsStates session) {
        return session != null && (
                WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())
        );
    }

    private void updateActiveSession(WorkUsersSessionsStates session, int schedule) {
        calculationService.calculateCurrentWork(session, schedule);
        String sessionPath = pathConfig.getSessionFilePath(
                session.getUsername(),
                session.getUserId()
        ).toString();
        persistenceService.persistSession(session, sessionPath);
    }

    private void populateSessionModel(Model model, WorkUsersSessionsStates session) {
        model.addAttribute("sessionStatus", getFormattedStatus(session.getSessionStatus()));
        model.addAttribute("currentDateTime", CalculateWorkHoursUtil.formatDateTime(LocalDateTime.now()));
        model.addAttribute("dayStartTime", CalculateWorkHoursUtil.formatDateTime(session.getDayStartTime()));
        model.addAttribute("totalWorkRaw", formatWorkTime(session.getTotalWorkedMinutes()));
        model.addAttribute("temporaryStopCount", session.getTemporaryStopCount() != null ? session.getTemporaryStopCount() : 0);
        model.addAttribute("totalTemporaryStopTime", formatWorkTime(session.getTotalTemporaryStopMinutes()));
        model.addAttribute("overtime", formatWorkTime(session.getTotalOvertimeMinutes()));
        model.addAttribute("lunchBreakStatus", session.getLunchBreakDeducted());
        model.addAttribute("actualWorkTime", formatWorkTime(session.getFinalWorkedMinutes()));
        LoggerUtil.debug(this.getClass(), "Model attributes: " + model.asMap());

    }

    private String formatWorkTime(Integer minutes) {
        return minutes != null ? CalculateWorkHoursUtil.minutesToHHmm(minutes) : "00:00";
    }

    private String getFormattedStatus(String status) {
        if (status == null) return "Offline";
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