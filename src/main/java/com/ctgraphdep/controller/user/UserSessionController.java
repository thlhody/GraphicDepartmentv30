package com.ctgraphdep.controller.user;

import com.ctgraphdep.calculations.CalculationCommandFactory;
import com.ctgraphdep.calculations.CalculationCommandService;
import com.ctgraphdep.calculations.commands.UpdateLastTemporaryStopCommand;
import com.ctgraphdep.calculations.queries.CalculateRecommendedEndTimeQuery;
import com.ctgraphdep.calculations.queries.CalculateRawWorkMinutesQuery;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.*;
import com.ctgraphdep.session.NavigationContext;
import com.ctgraphdep.session.query.*;
import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.IsActiveSessionCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling user session operations through the command pattern.
 * All session operations go through the SessionCommandService to ensure consistent handling.
 */
@Controller
@RequestMapping({"/user/session"})
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserSessionController extends BaseController {

    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final CalculationCommandService calculationService;
    private final CalculationCommandFactory calculationFactory;

    @Autowired
    public UserSessionController(
            SessionCommandService commandService,
            SessionCommandFactory commandFactory,
            CalculationCommandService calculationService,
            CalculationCommandFactory calculationFactory,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService) {
        super(commandService.getContext().getUserService(), folderStatus, timeValidationService);
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.calculationService = calculationService;
        this.calculationFactory = calculationFactory;
    }

    @GetMapping
    public String getSessionPage(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model,
            RedirectAttributes redirectAttributes,
            @RequestParam(name = "skipResolutionCheck", required = false, defaultValue = "false") boolean skipResolutionCheck) {

        try {
            LoggerUtil.info(this.getClass(), "Loading session page at " + getStandardCurrentDateTime());

            // Use the new helper method instead of checkUserAccess
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Configure navigation context
            NavigationContextQuery navQuery = commandFactory.createNavigationContextQuery(currentUser);
            NavigationContext navContext = commandService.executeQuery(navQuery);

            // Override the dashboard URL from prepareUserAndCommonModelAttributes with the one from NavigationContext
            // as it might contain additional context-specific logic
            model.addAttribute("dashboardUrl", navContext.getDashboardUrl());
            model.addAttribute("completedSessionToday", navContext.isCompletedSessionToday());
            model.addAttribute("isTeamLeaderView", navContext.isTeamLeaderView());

            // Initialize variables at the start:
            List<WorkTimeTable> unresolvedEntries = List.of();
            boolean hasUnresolvedEntries = false;
            Map<LocalDate, LocalDateTime> recommendedEndTimes = new HashMap<>();

            // Check for unresolved work time entries
            if (!skipResolutionCheck) {
                // Use the UnresolvedWorkTimeQuery to check for entries that need resolution
                UnresolvedWorkTimeQuery unresolvedQuery = new UnresolvedWorkTimeQuery(currentUser.getUsername());
                unresolvedEntries = commandService.executeQuery(unresolvedQuery);

                if (!unresolvedEntries.isEmpty()) {
                    LoggerUtil.info(this.getClass(), String.format("Found %d unresolved work time entries for user %s",
                            unresolvedEntries.size(), currentUser.getUsername()));

                    hasUnresolvedEntries = true;

                    // Calculate recommended end times for each entry using the calculation command
                    for (WorkTimeTable entry : unresolvedEntries) {
                        // Use CalculateRecommendedEndTimeQuery
                        CalculateRecommendedEndTimeQuery endTimeQuery = calculationFactory.createCalculateRecommendedEndTimeQuery(entry, currentUser.getSchedule());
                        LocalDateTime recommendedTime = calculationService.executeQuery(endTimeQuery);
                        recommendedEndTimes.put(entry.getWorkDate(), recommendedTime);
                    }
                }
            }
            model.addAttribute("hasUnresolvedEntries", hasUnresolvedEntries);
            model.addAttribute("unresolvedEntries", unresolvedEntries);
            model.addAttribute("recommendedEndTimes", recommendedEndTimes);

            // Get and process current session (the one we show on the main page)
            ResolveSessionQuery resolveQuery = commandFactory.createResolveSessionQuery(currentUser.getUsername(), currentUser.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(resolveQuery);

            // Update calculations if session is active
            if (isActiveSession(session)) {
                // Get standardized time using our base controller method
                LocalDateTime currentTime = getStandardCurrentDateTime();

                // Update session calculations using the standardized current time
                UpdateSessionCalculationsCommand updateCommand = commandFactory.createUpdateSessionCalculationsCommand(session, currentTime);
                session = commandService.executeCommand(updateCommand);
            }

            // Prepare view model through a dedicated command
            PrepareSessionViewModelCommand viewModelCommand = commandFactory.createPrepareSessionViewModelCommand(model, session, currentUser);
            commandService.executeCommand(viewModelCommand);

            // Add current time to model for UI display - already done by prepareUserAndCommonModelAttributes
            // but using a different format, so keep this explicit formatting
            model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

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
            LoggerUtil.info(this.getClass(), "Starting session at " + getStandardCurrentDateTime());

            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            String username = currentUser.getUsername();
            Integer userId = currentUser.getUserId();

            // Check for unresolved work time entries
            UnresolvedWorkTimeQuery unresolvedQuery = new UnresolvedWorkTimeQuery(username);
            List<WorkTimeTable> unresolvedEntries = commandService.executeQuery(unresolvedQuery);

            if (!unresolvedEntries.isEmpty()) {
                // Note: We don't block the user, just show a warning
                redirectAttributes.addFlashAttribute("warningMessage",
                        "You have unresolved work sessions. Please consider resolving them.");
            }

            // Get current session to check if it needs to be reset
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, userId);
            WorkUsersSessionsStates currentSession = commandService.executeQuery(sessionQuery);

            // If there's a session from today in OFFLINE state, clear it to start a fresh one
            if (currentSession != null && currentSession.getDayStartTime() != null) {

                // Use standardized time from the base controller
                LocalDate today = getStandardCurrentDate();
                LocalDate sessionDate = currentSession.getDayStartTime().toLocalDate();

                // If session is from today and in OFFLINE state but not completed, we don't need resolution
                // Just start a new session instead
                if (sessionDate.equals(today) &&
                        WorkCode.WORK_OFFLINE.equals(currentSession.getSessionStatus()) && !currentSession.getWorkdayCompleted()) {
                    LoggerUtil.info(this.getClass(), String.format("Clearing incomplete session from today for user %s before starting new one", username));
                }
            }

            // Execute start day command
            StartDayCommand command = commandFactory.createStartDayCommand(username, userId);
            commandService.executeCommand(command);

            return "redirect:/user/session?action=start";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error starting session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error starting session: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/resume-previous")
    public String resumePreviousSession(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        try {
            LoggerUtil.info(this.getClass(), "Initiating resume previous session at " + getStandardCurrentDateTime());

            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Just redirect to a page that shows a confirmation dialog
            return "redirect:/user/session?showResumeConfirmation=true";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error initiating resume workflow: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/confirm-resume")
    public String confirmResumeSession(
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        try {
            LoggerUtil.info(this.getClass(), "Confirming resume session at " + getStandardCurrentDateTime());

            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Execute resume command
            ResumePreviousSessionCommand command = commandFactory.createResumePreviousSessionCommand(currentUser.getUsername(), currentUser.getUserId());
            commandService.executeCommand(command);

            return "redirect:/user/session?action=resume";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resuming session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error resuming session: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/temp-stop")
    public String toggleTemporaryStop(
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        try {
            LoggerUtil.info(this.getClass(), "Toggling temporary stop at " + getStandardCurrentDateTime());

            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Get current session state
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(currentUser.getUsername(), currentUser.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            if (session != null) {
                if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus())) {
                    // Start temporary stop
                    StartTemporaryStopCommand command = commandFactory.createStartTemporaryStopCommand(currentUser.getUsername(), currentUser.getUserId());
                    commandService.executeCommand(command);
                    return "redirect:/user/session?action=pause";
                } else if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                    // Resume from temporary stop
                    ResumeFromTemporaryStopCommand command = commandFactory.createResumeFromTemporaryStopCommand(currentUser.getUsername(), currentUser.getUserId());
                    commandService.executeCommand(command);
                    return "redirect:/user/session?action=resume";
                }
            }

            return "redirect:/user/session";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error toggling temporary stop: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error toggling temporary stop: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/end")
    public String endSession(
            @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        try {
            LoggerUtil.info(this.getClass(), "Ending session at " + getStandardCurrentDateTime());

            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Get current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(currentUser.getUsername(), currentUser.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Check if session is already offline
            if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                return "redirect:/user/session";
            }

            // Get standardized time using our base controller method
            LocalDateTime currentTime = getStandardCurrentDateTime();

            // Handle active sessions
            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) || WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // Calculate final worked minutes using calculation command
                CalculateRawWorkMinutesQuery workMinutesQuery = calculationFactory.createCalculateRawWorkMinutesQuery(session, currentTime);
                int rawWorkMinutes = calculationService.executeQuery(workMinutesQuery);

                // Handle temporary stop case
                if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                    // Use the calculation command for updating last temporary stop
                    UpdateLastTemporaryStopCommand tempStopCommand = calculationFactory.createUpdateLastTemporaryStopCommand(session, currentTime);
                    session = calculationService.executeCommand(tempStopCommand);
                    SaveSessionCommand saveCommand = commandFactory.createSaveSessionCommand(session);
                    commandService.executeCommand(saveCommand);
                }

                // End day using command with current time
                EndDayCommand command = commandFactory.createEndDayCommand(
                        currentUser.getUsername(),
                        currentUser.getUserId(),
                        rawWorkMinutes, // Use the calculated raw work minutes
                        currentTime);  // Pass the standardized current time

                commandService.executeCommand(command);
                return "redirect:/user/session?action=end";
            }

            return "redirect:/user/session";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error ending session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error ending session: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/resolve-worktime")
    public String resolveWorkTimeEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("entryDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate,
            @RequestParam("endHour") int endHour,
            @RequestParam("endMinute") int endMinute,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(),
                    String.format("Resolving worktime entry for date %s at %s",
                            entryDate, getStandardCurrentDateTime()));

            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Validate hour and minute range
            if (endHour < 0 || endHour > 23 || endMinute < 0 || endMinute > 59) {
                LoggerUtil.warn(this.getClass(), "Invalid time values provided: " + endHour + ":" + endMinute);
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid time values");
                return "redirect:/user/session";
            }

            // Create end time from user input
            LocalDateTime endTime = LocalDateTime.of(entryDate, LocalTime.of(endHour, endMinute));

            // Execute the resolution command
            ResolveWorkTimeEntryCommand command = new ResolveWorkTimeEntryCommand(currentUser.getUsername(), currentUser.getUserId(), entryDate, endTime);
            boolean success = commandService.executeCommand(command);

            if (success) {
                LoggerUtil.info(this.getClass(), "Successfully resolved worktime entry for " + entryDate);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Work session from " + entryDate.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")) + " resolved successfully");
            } else {
                LoggerUtil.warn(this.getClass(), "Failed to resolve worktime entry for " + entryDate);
                redirectAttributes.addFlashAttribute("errorMessage", "Failed to resolve work session");
            }

            return "redirect:/user/session";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resolving work time entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    private boolean isActiveSession(WorkUsersSessionsStates session) {
        try {
            // Use the getTimeValidationService to access validation service
            IsActiveSessionCommand command = getTimeValidationService().getValidationFactory().createIsActiveSessionCommand(session);
            return getTimeValidationService().execute(command);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error checking session activity: " + e.getMessage(), e);
            return false; // Default to false for safety
        }
    }
}