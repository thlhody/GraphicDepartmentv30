package com.ctgraphdep.controller.user;

import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.session.EndTimeCalculationDTO;
import com.ctgraphdep.model.dto.session.ResolutionCalculationDTO;
import com.ctgraphdep.model.dto.session.WorkSessionDTO;
import com.ctgraphdep.service.SessionMonitorService;
import com.ctgraphdep.service.SessionService;
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
import com.ctgraphdep.validation.GetStandardTimeValuesCommand;
import com.ctgraphdep.validation.TimeValidationService;
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

@Controller
@RequestMapping({"/user/session"})
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserSessionController extends BaseController {

    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final SessionService sessionService;

    @Autowired
    public UserSessionController(SessionCommandService commandService, SessionCommandFactory commandFactory,
            FolderStatus folderStatus, TimeValidationService timeValidationService, SessionService sessionService) {
        super(commandService.getContext().getUserService(), folderStatus, timeValidationService);
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.sessionService = sessionService;
    }

    @GetMapping
    public String getSessionPage(@AuthenticationPrincipal UserDetails userDetails, Model model, RedirectAttributes redirectAttributes,
                                 @RequestParam(name = "skipResolutionCheck", required = false, defaultValue = "false") boolean skipResolutionCheck) {

        try {
            LoggerUtil.info(this.getClass(), "Loading session page at " + getStandardCurrentDateTime());

            // Use the helper method to prepare user and common model attributes
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Configure navigation context
            NavigationContextQuery navQuery = commandFactory.createNavigationContextQuery(currentUser);
            NavigationContext navContext = commandService.executeQuery(navQuery);

            model.addAttribute("dashboardUrl", navContext.dashboardUrl());
            model.addAttribute("completedSessionToday", navContext.completedSessionToday());
            model.addAttribute("isTeamLeaderView", navContext.isTeamLeaderView());

            // Check for unresolved work time entries using a query
            if (!skipResolutionCheck) {
                GetUnresolvedEntriesQuery unresolvedQuery = commandFactory.createGetUnresolvedEntriesQuery(currentUser.getUsername());
                List<ResolutionCalculationDTO> unresolvedEntries = commandService.executeQuery(unresolvedQuery);

                model.addAttribute("hasUnresolvedEntries", !unresolvedEntries.isEmpty());
                model.addAttribute("unresolvedEntries", unresolvedEntries);
                // After getting unresolvedEntries
                if (!unresolvedEntries.isEmpty()) {
                    LoggerUtil.info(this.getClass(), "=== DEBUGGING UNRESOLVED ENTRIES ===");
                    for (ResolutionCalculationDTO entry : unresolvedEntries) {
                        LoggerUtil.info(this.getClass(), String.format(
                                "Entry: workDate=%s, startTime=%s, recommendedEndTime=%s, formattedRecommendedEndTime=%s",
                                entry.getWorkDate(),
                                entry.getFormattedStartTime(),
                                entry.getRecommendedEndTime(),
                                entry.getFormattedRecommendedEndTime()
                        ));
                    }
                    LoggerUtil.info(this.getClass(), "=== END DEBUGGING ===");
                }
            } else {
                model.addAttribute("hasUnresolvedEntries", false);
            }


            // Prepare view model through dedicated command
            PrepareSessionViewModelCommand viewModelCommand = commandFactory.createPrepareSessionViewModelCommand(model, currentUser);
            commandService.executeCommand(viewModelCommand);

            // Add current time for UI display in specific format
            model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

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
                redirectAttributes.addFlashAttribute("warningMessage", "You have unresolved work sessions. Please consider resolving them.");
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
    public String confirmResumeSession(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
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
    public String toggleTemporaryStop(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
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
    public String endSession(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        try {
            LoggerUtil.info(this.getClass(), "Ending session at " + getStandardCurrentDateTime());

            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Get current session just to check if it exists and is not already offline
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(currentUser.getUsername(), currentUser.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Check if session is already offline
            if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                return "redirect:/user/session";
            }

            // Get standardized time using our base controller method
            LocalDateTime currentTime = getStandardCurrentDateTime();

            // Let the enhanced EndDayCommand handle all logic - temporary stops, calculations, etc.
            EndDayCommand command = commandFactory.createEndDayCommand(
                    currentUser.getUsername(),
                    currentUser.getUserId(),
                    null,  // Let the command calculate the minutes
                    currentTime);  // Pass the standardized current time

            commandService.executeCommand(command);
            return "redirect:/user/session?action=end";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error ending session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error ending session: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/resolve-worktime")
    public String resolveWorkTimeEntry(@AuthenticationPrincipal UserDetails userDetails, @RequestParam("entryDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate entryDate,
            @RequestParam("endHour") int endHour, @RequestParam("endMinute") int endMinute, RedirectAttributes redirectAttributes) {

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

            // Use SessionService to calculate resolution values
            ResolutionCalculationDTO result = sessionService.calculateResolutionValues(currentUser.getUsername(), entryDate, endHour, endMinute);

            // Still need to execute the command to actually save the resolution
            if (result.getSuccess()) {
                // Create end time from user input
                LocalDateTime endTime = LocalDateTime.of(entryDate, LocalTime.of(endHour, endMinute));

                // Execute the resolution command - this actually saves the changes
                ResolveWorkTimeEntryCommand command = new ResolveWorkTimeEntryCommand(currentUser.getUsername(), currentUser.getUserId(), entryDate, endTime);
                boolean success = commandService.executeCommand(command);

                if (success) {
                    LoggerUtil.info(this.getClass(), "Successfully resolved worktime entry for " + entryDate);
                    redirectAttributes.addFlashAttribute("successMessage", "Work session from " + entryDate.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")) +
                            " resolved successfully");
                } else {
                    LoggerUtil.warn(this.getClass(), "Failed to resolve worktime entry for " + entryDate);
                    redirectAttributes.addFlashAttribute("errorMessage", "Failed to resolve work session");
                }
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", result.getErrorMessage());
            }

            return "redirect:/user/session";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resolving work time entry: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/calculate-resolution")
    @ResponseBody
    public ResolutionCalculationDTO calculateResolution(@AuthenticationPrincipal UserDetails userDetails, @RequestBody Map<String, Object> requestData) {

        // Get current user
        User currentUser = getUser(userDetails);
        if (currentUser == null) {
            ResolutionCalculationDTO errorDto = new ResolutionCalculationDTO();
            errorDto.setSuccess(false);
            errorDto.setErrorMessage("Authentication required");
            return errorDto;
        }

        // Extract values from request
        LocalDate entryDate = LocalDate.parse((String)requestData.get("entryDate"));
        int endHour = (Integer)requestData.get("endHour");
        int endMinute = (Integer)requestData.get("endMinute");

        // Use SessionService to calculate resolution values
        return sessionService.calculateResolutionValues(
                currentUser.getUsername(),
                entryDate,
                endHour,
                endMinute);
    }

    @PostMapping("/schedule-end")
    public String scheduleEndSession(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(value = "endHour") int endHour,
            @RequestParam(value = "endMinute") int endMinute, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), String.format("Scheduling end session at %02d:%02d", endHour, endMinute));

            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Get current session to check if it's active
            WorkUsersSessionsStates session = commandService.getContext().getCurrentSession(currentUser.getUsername(), currentUser.getUserId());

            if (session == null || WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                redirectAttributes.addFlashAttribute("warningMessage", "No active session to schedule end time for");
                return "redirect:/user/session";
            }

            // Get standardized time values
            GetStandardTimeValuesCommand timeCommand = getTimeValidationService().getValidationFactory().createGetStandardTimeValuesCommand();
            GetStandardTimeValuesCommand.StandardTimeValues timeValues = getTimeValidationService().execute(timeCommand);
            LocalDate today = timeValues.getCurrentDate();

            // Create end time from user input
            LocalDateTime endTime = LocalDateTime.of(today, LocalTime.of(endHour, endMinute));

            // Get the session monitor service and schedule the end time
            SessionMonitorService monitorService = commandService.getContext().getSessionMonitorService();
            boolean success = monitorService.scheduleAutomaticEnd(currentUser.getUsername(), currentUser.getUserId(), endTime);

            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", String.format("Session scheduled to end at %02d:%02d", endHour, endMinute));
            } else {
                redirectAttributes.addFlashAttribute("warningMessage", "Could not schedule end time. Ensure it's in the future.");
            }

            return "redirect:/user/session";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error scheduling end session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error scheduling end session: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @PostMapping("/cancel-scheduled-end")
    public String cancelScheduledEnd(@AuthenticationPrincipal UserDetails userDetails, RedirectAttributes redirectAttributes) {
        try {
            LoggerUtil.info(this.getClass(), "Cancelling scheduled end session");

            User currentUser = getUser(userDetails);
            SessionMonitorService monitorService = commandService.getContext().getSessionMonitorService();

            // DEBUG: Check scheduled time BEFORE cancel
            LocalDateTime beforeCancel = monitorService.getScheduledEndTime(currentUser.getUsername());
            LoggerUtil.info(this.getClass(), String.format("Scheduled time BEFORE cancel: %s", beforeCancel));

            boolean success = monitorService.cancelScheduledEnd(currentUser.getUsername());

            // DEBUG: Check scheduled time AFTER cancel
            LocalDateTime afterCancel = monitorService.getScheduledEndTime(currentUser.getUsername());
            LoggerUtil.info(this.getClass(), String.format("Scheduled time AFTER cancel: %s", afterCancel));
            LoggerUtil.info(this.getClass(), String.format("Cancel operation result: %s", success));

            if (success) {
                redirectAttributes.addFlashAttribute("successMessage", "Scheduled end time cancelled");
            } else {
                redirectAttributes.addFlashAttribute("warningMessage", "Failed to cancel scheduled end time");
            }

            return "redirect:/user/session";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error cancelling scheduled end: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error cancelling scheduled end: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    @GetMapping("/recommended-end-time")
    @ResponseBody
    public Map<String, Object> getRecommendedEndTime(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return Map.of("success", false, "message", "Authentication required");
            }

            // Use WorkSessionDTO from the SessionService
            WorkSessionDTO sessionDTO = sessionService.getCurrentSession(currentUser.getUsername(), currentUser.getUserId());

            if (sessionDTO == null || sessionDTO.getDayStartTime() == null) {
                return Map.of("success", false, "message", "No active session");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("recommendedEndTime", sessionDTO.getEstimatedEndTime() != null ? sessionDTO.getEstimatedEndTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null);
            result.put("expectedEndTime", "17:00");
            result.put("scheduledEndTime", sessionDTO.getScheduledEndTime() != null ? sessionDTO.getScheduledEndTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null);
            return result;

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting recommended end time: " + e.getMessage(), e);
            return Map.of("success", false, "message", "Error: " + e.getMessage());
        }
    }

    @PostMapping("/calculate-end-time")
    @ResponseBody
    public EndTimeCalculationDTO calculateEndTime(@AuthenticationPrincipal UserDetails userDetails, @RequestBody Map<String, Integer> endTimeData) {

        try {
            // Get the current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                EndTimeCalculationDTO errorDTO = new EndTimeCalculationDTO();
                errorDTO.setSuccess(false);
                errorDTO.setMessage("Authentication required");
                return errorDTO;
            }

            Integer endHour = endTimeData.get("endHour");
            Integer endMinute = endTimeData.get("endMinute");

            // Use SessionService to calculate end time
            return sessionService.calculateEndTimeWork(
                    currentUser.getUsername(),
                    currentUser.getUserId(),
                    endHour,
                    endMinute);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating end time work: " + e.getMessage(), e);
            EndTimeCalculationDTO errorDTO = new EndTimeCalculationDTO();
            errorDTO.setSuccess(false);
            errorDTO.setMessage("Error calculating work time: " + e.getMessage());
            return errorDTO;
        }
    }
}