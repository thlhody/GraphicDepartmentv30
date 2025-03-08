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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@PreAuthorize("isAuthenticated()")
@RequestMapping({"/user/session"})
public class UserSessionController extends BaseController {

    private final UserSessionService userSessionService;
    private final SessionPersistenceService persistenceService;
    private final UserService userService;
    private final UserSessionCalcService calculatorService;
    private final ContinuationTrackingService continuationTrackingService;

    @Autowired
    public UserSessionController(
            UserSessionService userSessionService,
            UserService userService,
            FolderStatusService folderStatusService,
            SessionPersistenceService persistenceService,
            UserSessionCalcService calculatorService,
            ContinuationTrackingService continuationTrackingService) {
        super(userService, folderStatusService);
        this.userSessionService = userSessionService;
        this.userService = userService;
        this.calculatorService = calculatorService;
        this.persistenceService = persistenceService;
        this.continuationTrackingService = continuationTrackingService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String getSessionPage(@AuthenticationPrincipal UserDetails userDetails, Model model, RedirectAttributes redirectAttributes) {
        try {
            // Step 1: Validate authentication and get user
            User user = validateAndGetUser(userDetails);

            // Step 3: Check for unresolved sessions from previous days
            if (hasUnresolvedSessions(user, redirectAttributes)) {
                return "redirect:/user/session/resolve";
            }

            // Step 4: Set up navigation context
            configureNavigationContext(user, model);

            // Step 5: Process current session
            WorkUsersSessionsStates session = processCurrentSession(user);

            // Step 6: Prepare view model
            prepareViewModel(model, session, user);

            // Step 7: Return view
            return "user/session";
        } catch (Exception e) {
            // Centralized error handling
            LoggerUtil.error(this.getClass(), "Error loading session page: " + e.getMessage(), e);
            model.addAttribute("error", "Error loading session data: " + e.getMessage());
            return "user/session";
        }
    }

    /**
     * Checks if user has any unresolved sessions from previous days
     */
    private boolean hasUnresolvedSessions(User user, RedirectAttributes redirectAttributes) {
        // Check for unresolved continuation points
        boolean hasUnresolvedContinuations = continuationTrackingService.hasUnresolvedMidnightEnd(user.getUsername());

        // Check for incomplete sessions from previous days
        WorkUsersSessionsStates session = userSessionService.getCurrentSession(user.getUsername(), user.getUserId());
        boolean hasPreviousDaySession = false;

        if (session != null && session.getDayStartTime() != null) {
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();
            LocalDate today = LocalDate.now();

            hasPreviousDaySession = sessionDate.isBefore(today) &&
                    (session.getDayEndTime() == null || !session.getWorkdayCompleted());
        }

        // If any unresolved sessions are found, add a warning message
        if (hasUnresolvedContinuations || hasPreviousDaySession) {
            LoggerUtil.info(this.getClass(),
                    String.format("User %s has unresolved session - redirecting to resolution page", user.getUsername()));

            redirectAttributes.addFlashAttribute("warningMessage",
                    "Your previous work session needs to be resolved before starting a new day.");

            return true;
        }

        return false;
    }

    /**
     * Sets up navigation context based on user role
     */
    private void configureNavigationContext(User user, Model model) {
        // Check if there's a completed session for today
        boolean completedSessionToday = userSessionService.hasCompletedSessionForToday(
                user.getUsername(), user.getUserId());
        model.addAttribute("completedSessionToday", completedSessionToday);

        // Determine dashboard URL based on user role
        if (user.hasRole("TEAM_LEADER")) {
            model.addAttribute("isTeamLeaderView", true);
            model.addAttribute("dashboardUrl", "/team-lead");
        } else {
            model.addAttribute("dashboardUrl", "/user");
        }
    }

    /**
     * Processes the current session and updates if active
     */
    private WorkUsersSessionsStates processCurrentSession(User user) {
        // Retrieve session
        WorkUsersSessionsStates session = retrieveAndValidateSession(user);

        // Update session if active
        if (isActiveSession(session)) {
            updateActiveSession(session);
        }

        return session;
    }

    /**
     * Prepares the view model with session information
     */
    private void prepareViewModel(Model model, WorkUsersSessionsStates session, User user) {
        // Add session information to model
        prepareSessionModel(model, session);

        // Add user information
        model.addAttribute("username", user.getUsername());
        model.addAttribute("userFullName", user.getName());

        // Add current date/time
        model.addAttribute("currentDate", LocalDate.now());
    }

    private User validateAndGetUser(UserDetails userDetails) {
        if (userDetails == null) {
            LoggerUtil.error(this.getClass(), "Null UserDetails during session page access");
            throw new IllegalStateException("User authentication is required");
        }

        User user = getUser(userDetails);
        if (user == null) {
            LoggerUtil.error(this.getClass(),
                    String.format("No user found for username: %s", userDetails.getUsername()));
            throw new IllegalStateException("User not found");
        }

        return user;
    }

    private WorkUsersSessionsStates retrieveAndValidateSession(User user) {
        WorkUsersSessionsStates session = userSessionService.getCurrentSession(
                user.getUsername(),
                user.getUserId()
        );

        LoggerUtil.info(this.getClass(), "Session data: " + session);

        // Verify session ownership
        if (session != null && !session.getUsername().equals(user.getUsername())) {
            LoggerUtil.warn(this.getClass(),
                    "Session ownership mismatch for user: " + user.getUsername());
            throw new SecurityException("Session ownership mismatch");
        }

        return session;
    }

    private void prepareSessionModel(Model model, WorkUsersSessionsStates session) {
        if (session == null) {
            model.addAttribute("sessionStatus", "Offline");
            return;
        }

        String formattedStatus = getFormattedStatus(session.getSessionStatus());

        LoggerUtil.debug(this.getClass(),
                String.format("Session status: %s, Formatted status: %s",
                        session.getSessionStatus(), formattedStatus));

        model.addAttribute("sessionStatus", formattedStatus);
        populateSessionModel(model, session);
    }

    private String determineSessionView(String basePath) {
        // Ensure consistent view name based on base path
        return "user/session";
    }

    private String handleSessionPageError(Model model, Exception e, String basePath) {
        LoggerUtil.error(this.getClass(),
                "Error loading session page: " + e.getMessage(), e);

        model.addAttribute("error", "Error loading session data: " + e.getMessage());

        // Return a view that matches the base path
        return determineSessionView(basePath);
    }

    private boolean verifySessionOwnership(String username, WorkUsersSessionsStates session) {
        return session != null && session.getUsername().equals(username);
    }

    @PostMapping("/start")
    public String startSession(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        User user = getUserOrThrow(userDetails);

        // Check for unresolved continuation points before allowing a start
        boolean hasUnresolvedContinuations = continuationTrackingService.hasUnresolvedMidnightEnd(user.getUsername());

        if (hasUnresolvedContinuations) {
            redirectAttributes.addFlashAttribute("warningMessage",
                    "Your previous work session needs to be resolved before starting a new day.");
            return "redirect:/user/session/resolve";
        }

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

    @PostMapping("/resume-previous")
    public String resumePreviousSession(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserOrThrow(userDetails);
        // Just redirect to a page that shows a confirmation dialog
        return "redirect:/user/session?showResumeConfirmation=true";
    }

    @PostMapping("/confirm-resume")
    public String confirmResumeSession(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserOrThrow(userDetails);

        try {
            userSessionService.resumePreviousSession(user.getUsername(), user.getUserId());
            return "redirect:/user/session?action=resume";
        } catch (Exception e) {
            return "redirect:/user/session?error=" + e.getMessage();
        }
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

            return "redirect:/user/session";
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

    private void updateActiveSession(WorkUsersSessionsStates session) {
        try {
            // Calculate and update session values
            calculatorService.updateSessionCalculations(session);

            // Persist session using the SessionPersistenceService
            persistenceService.persistSession(session);

            LoggerUtil.debug(this.getClass(), String.format("Updated active session for user %s", session.getUsername()));
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error updating active session: %s", e.getMessage()));
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
        LoggerUtil.debug(this.getClass(), String.format("Converting status from '%s' to formatted status", status));

        return switch (status) {
            case WorkCode.WORK_ONLINE -> "Online";
            case WorkCode.WORK_TEMPORARY_STOP -> "Temporary Stop";
            default -> WorkCode.WORK_OFFLINE;
        };
    }

    private User getUserOrThrow(UserDetails userDetails) {
        return userService.getUserByUsername(userDetails.getUsername()).orElseThrow(() -> new RuntimeException("User not found"));
    }
}