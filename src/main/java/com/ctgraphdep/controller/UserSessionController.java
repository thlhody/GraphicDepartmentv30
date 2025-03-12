package com.ctgraphdep.controller;

import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.*;
import com.ctgraphdep.session.NavigationContext;
import com.ctgraphdep.session.query.*;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.User;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * Controller for handling user session operations through the command pattern.
 * All session operations go through the SessionCommandService to ensure consistent handling.
 */
@Controller
@PreAuthorize("isAuthenticated()")
@RequestMapping({"/user/session"})
public class UserSessionController extends BaseController {

    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;

    @Autowired
    public UserSessionController(
            SessionCommandService commandService,
            SessionCommandFactory commandFactory) {
        super(commandService.getContext().getUserService(), commandService.getContext().getFolderStatusService());
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String getSessionPage(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            // Execute authentication query
            AuthenticatedUserQuery authQuery = commandFactory.createAuthenticatedUserQuery(userDetails);
            User user = commandService.executeQuery(authQuery);

            // Check for unresolved sessions
            UnresolvedSessionQuery unresolvedQuery = commandFactory.createUnresolvedSessionQuery(user.getUsername(), user.getUserId());
            boolean hasUnresolved = commandService.executeQuery(unresolvedQuery);

            if (hasUnresolved) {
                redirectAttributes.addFlashAttribute("warningMessage", "Your previous work session needs to be resolved before starting a new day.");
                return "redirect:/user/session/resolve";
            }

            // Configure navigation context
            NavigationContextQuery navQuery = commandFactory.createNavigationContextQuery(user);
            NavigationContext navContext = commandService.executeQuery(navQuery);
            model.addAttribute("completedSessionToday", navContext.isCompletedSessionToday());
            model.addAttribute("isTeamLeaderView", navContext.isTeamLeaderView());
            model.addAttribute("dashboardUrl", navContext.getDashboardUrl());

            // Get and process session
            ResolveSessionQuery resolveQuery = commandFactory.createResolveSessionQuery(user.getUsername(), user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(resolveQuery);

            // Check for previous day session
            IsPreviousDaySessionQuery isPreviousDayQuery = commandFactory.createIsPreviousDaySessionQuery(session);
            if (commandService.executeQuery(isPreviousDayQuery)) {
                HandlePreviousDaySessionCommand handlePreviousDayCommand = commandFactory.createHandlePreviousDaySessionCommand(session);
                session = commandService.executeCommand(handlePreviousDayCommand);
            }

            // Update calculations if session is active
            if (isActiveSession(session)) {
                UpdateSessionCalculationsCommand updateCommand = commandFactory.createUpdateSessionCalculationsCommand(session,session.getDayEndTime());
                session = commandService.executeCommand(updateCommand);
            }

            // Prepare view model through a dedicated command
            PrepareSessionViewModelCommand viewModelCommand = commandFactory.createPrepareSessionViewModelCommand(model, session, user);
            commandService.executeCommand(viewModelCommand);

            return "user/session";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading session page: " + e.getMessage(), e);
            model.addAttribute("error", "Error loading session data: " + e.getMessage());
            return "user/session";
        }
    }

    @PostMapping("/start")
    public String startSession(
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        try {
            // Get authenticated user
            AuthenticatedUserQuery authQuery = commandFactory.createAuthenticatedUserQuery(userDetails);
            User user = commandService.executeQuery(authQuery);

            // Check for unresolved sessions
            UnresolvedSessionQuery unresolvedQuery = commandFactory.createUnresolvedSessionQuery(user.getUsername(), user.getUserId());
            boolean hasUnresolved = commandService.executeQuery(unresolvedQuery);

            if (hasUnresolved) {
                redirectAttributes.addFlashAttribute("warningMessage", "Your previous work session needs to be resolved before starting a new day.");
                return "redirect:/user/session/resolve";
            }

            // Execute start day command
            StartDayCommand command = commandFactory.createStartDayCommand(user.getUsername(), user.getUserId());
            commandService.executeCommand(command);

            return "redirect:/user/session?action=start";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error starting session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error starting session: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/resume-previous")
    public String resumePreviousSession(@AuthenticationPrincipal UserDetails userDetails) {
        // Just redirect to a page that shows a confirmation dialog
        return "redirect:/user/session?showResumeConfirmation=true";
    }

    @PostMapping("/confirm-resume")
    public String confirmResumeSession(
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        try {
            // Get authenticated user
            AuthenticatedUserQuery authQuery = commandFactory.createAuthenticatedUserQuery(userDetails);
            User user = commandService.executeQuery(authQuery);

            // Execute resume command
            ResumePreviousSessionCommand command = commandFactory.createResumePreviousSessionCommand(user.getUsername(), user.getUserId());
            commandService.executeCommand(command);

            return "redirect:/user/session?action=resume";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resuming session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error resuming session: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/temp-stop")
    public String toggleTemporaryStop(
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        try {
            // Get authenticated user
            AuthenticatedUserQuery authQuery = commandFactory.createAuthenticatedUserQuery(userDetails);
            User user = commandService.executeQuery(authQuery);

            // Get current session state
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(user.getUsername(), user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            if (session != null) {
                if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                    // Start temporary stop
                    StartTemporaryStopCommand command = commandFactory.createStartTemporaryStopCommand(user.getUsername(), user.getUserId());
                    commandService.executeCommand(command);
                    return "redirect:/user/session?action=pause";
                } else if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                    // Resume from temporary stop
                    ResumeFromTemporaryStopCommand command = commandFactory.createResumeFromTemporaryStopCommand(user.getUsername(), user.getUserId());
                    commandService.executeCommand(command);
                    return "redirect:/user/session?action=resume";
                }
            }

            return "redirect:/user/session";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error toggling temporary stop: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error toggling temporary stop: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/end")
    public String endSession(
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        try {
            // Get authenticated user
            AuthenticatedUserQuery authQuery = commandFactory.createAuthenticatedUserQuery(userDetails);
            User user = commandService.executeQuery(authQuery);

            // Get current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(user.getUsername(), user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Check if session is already offline
            if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                return "redirect:/user/session";
            }

            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                GetSessionTimeValuesQuery timeQuery = commandFactory.getSessionTimeValuesQuery();
                GetSessionTimeValuesQuery.SessionTimeValues timeValues = commandService.executeQuery(timeQuery);
                // Use current time for normal end of day
                LocalDateTime currentTime = timeValues.getCurrentTime();

                // End day using command with current time
                EndDayCommand command = commandFactory.createEndDayCommand(
                        user.getUsername(),
                        user.getUserId(),
                        session.getFinalWorkedMinutes(),
                        currentTime);  // Pass the current time explicitly

                commandService.executeCommand(command);
                return "redirect:/user/session?action=end";
            }

            return "redirect:/user/session";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error ending session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error ending session: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    /**
     * Checks if the session is currently active (online or in temporary stop)
     */
    private boolean isActiveSession(WorkUsersSessionsStates session) {
        return session != null && (
                WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) ||
                        WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())
        );
    }
}