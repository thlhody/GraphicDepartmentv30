package com.ctgraphdep.controller;

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
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationFactory;
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
@PreAuthorize("isAuthenticated()")
@RequestMapping({"/user/session"})
public class UserSessionController extends BaseController {

    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final CalculationCommandService calculationService;
    private final CalculationCommandFactory calculationFactory;
    private final TimeValidationService validationService;
    private final TimeValidationFactory validationFactory;

    @Autowired
    public UserSessionController(
            SessionCommandService commandService,
            SessionCommandFactory commandFactory,
            CalculationCommandService calculationService,
            CalculationCommandFactory calculationFactory,
            TimeValidationService validationService,
            TimeValidationFactory validationFactory) {
        super(commandService.getContext().getUserService(), commandService.getContext().getFolderStatusService());
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.calculationService = calculationService;
        this.calculationFactory = calculationFactory;
        this.validationService = validationService;
        this.validationFactory = validationFactory;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String getSessionPage(@AuthenticationPrincipal UserDetails userDetails, Model model, RedirectAttributes redirectAttributes,
                                 @RequestParam(name = "skipResolutionCheck", required = false, defaultValue = "false") boolean skipResolutionCheck) {

        try {
            // Execute authentication query
            AuthenticatedUserQuery authQuery = commandFactory.createAuthenticatedUserQuery(userDetails);
            User user = commandService.executeQuery(authQuery);

            // Configure navigation context
            NavigationContextQuery navQuery = commandFactory.createNavigationContextQuery(user);
            NavigationContext navContext = commandService.executeQuery(navQuery);
            model.addAttribute("completedSessionToday", navContext.isCompletedSessionToday());
            model.addAttribute("isTeamLeaderView", navContext.isTeamLeaderView());
            model.addAttribute("dashboardUrl", navContext.getDashboardUrl());

            // Initialize variables at the start:
            List<WorkTimeTable> unresolvedEntries = List.of();
            boolean hasUnresolvedEntries = false;
            Map<LocalDate, LocalDateTime> recommendedEndTimes = new HashMap<>();

            // Check for unresolved work time entries
            if (!skipResolutionCheck) {
                // Use the new UnresolvedWorkTimeQuery to check for entries that need resolution
                UnresolvedWorkTimeQuery unresolvedQuery = new UnresolvedWorkTimeQuery(user.getUsername(), user.getUserId());
                unresolvedEntries = commandService.executeQuery(unresolvedQuery);

                if (!unresolvedEntries.isEmpty()) {
                    LoggerUtil.info(this.getClass(),
                            String.format("Found %d unresolved work time entries for user %s",
                                    unresolvedEntries.size(), user.getUsername()));

                    hasUnresolvedEntries = true;

                    // Calculate recommended end times for each entry using the calculation command
                    for (WorkTimeTable entry : unresolvedEntries) {
                        // Use CalculateRecommendedEndTimeQuery instead of direct utility call
                        CalculateRecommendedEndTimeQuery endTimeQuery =
                                calculationFactory.createCalculateRecommendedEndTimeQuery(entry, user.getSchedule());
                        LocalDateTime recommendedTime = calculationService.executeQuery(endTimeQuery);
                        recommendedEndTimes.put(entry.getWorkDate(), recommendedTime);
                    }
                }
            }
            model.addAttribute("hasUnresolvedEntries", hasUnresolvedEntries);
            model.addAttribute("unresolvedEntries", unresolvedEntries);
            model.addAttribute("recommendedEndTimes", recommendedEndTimes);

            // Get and process current session (the one we show on the main page)
            ResolveSessionQuery resolveQuery = commandFactory.createResolveSessionQuery(user.getUsername(), user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(resolveQuery);

            // Update calculations if session is active
            if (isActiveSession(session)) {
                // Get standardized time values using the new validation system
                GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
                GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);

                // Update session calculations using the current time from standardized time values
                UpdateSessionCalculationsCommand updateCommand = commandFactory.createUpdateSessionCalculationsCommand(session, timeValues.getCurrentTime());
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
    public String startSession(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        try {
            // Get authenticated user
            AuthenticatedUserQuery authQuery = commandFactory.createAuthenticatedUserQuery(userDetails);
            User user = commandService.executeQuery(authQuery);

            String username = user.getUsername();
            Integer userId = user.getUserId();

            // Check for unresolved work time entries
            UnresolvedWorkTimeQuery unresolvedQuery = new UnresolvedWorkTimeQuery(username, userId);
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
                // Get standardized time values for consistent date comparison using the new validation system
                GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
                GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);

                LocalDate sessionDate = currentSession.getDayStartTime().toLocalDate();
                LocalDate today = timeValues.getCurrentDate();

                // If session is from today and in OFFLINE state but not completed, we don't need resolution
                // Just start a new session instead
                if (sessionDate.equals(today) &&
                        WorkCode.WORK_OFFLINE.equals(currentSession.getSessionStatus()) &&
                        !currentSession.getWorkdayCompleted()) {
                    LoggerUtil.info(this.getClass(),
                            String.format("Clearing incomplete session from today for user %s before starting new one", username));
                }
            }

            // Execute start day command
            StartDayCommand command = commandFactory.createStartDayCommand(user.getUsername(), user.getUserId());
            commandService.executeCommand(command);

            return "redirect:/user/session?action=start";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error starting session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error starting session: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/resume-previous")
    public String resumePreviousSession(@AuthenticationPrincipal UserDetails userDetails) {
        // Just redirect to a page that shows a confirmation dialog
        return "redirect:/user/session?showResumeConfirmation=true";
    }

    @PostMapping("/confirm-resume")
    public String confirmResumeSession(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
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
    public String toggleTemporaryStop(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
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
    public String endSession(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
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

            // Get standardized time values using the new validation system
            GetStandardTimeValuesCommand timeCommand = validationFactory.createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = validationService.execute(timeCommand);

            // Handle active sessions
            if (WorkCode.WORK_ONLINE.equals(session.getSessionStatus()) || WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // Use current time for normal end of day
                LocalDateTime currentTime = timeValues.getCurrentTime();

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
                        user.getUsername(),
                        user.getUserId(),
                        rawWorkMinutes, // Use the calculated raw work minutes
                        currentTime);  // Pass the current time explicitly

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
            // Get authenticated user
            AuthenticatedUserQuery authQuery = commandFactory.createAuthenticatedUserQuery(userDetails);
            User user = commandService.executeQuery(authQuery);

            // Create end time from user input
            LocalDateTime endTime = LocalDateTime.of(entryDate, LocalTime.of(endHour, endMinute));

            // Execute the resolution command using a new ResolveWorkTimeEntryCommand
            ResolveWorkTimeEntryCommand command = new ResolveWorkTimeEntryCommand(user.getUsername(), user.getUserId(), entryDate, endTime);

            boolean success = commandService.executeCommand(command);

            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", "Work session from " + entryDate.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")) + " resolved successfully");
            } else {
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
        // Use the IsActiveSessionCommand from the validation system
        IsActiveSessionCommand command = validationFactory.createIsActiveSessionCommand(session);
        return validationService.execute(command);
    }
}