package com.ctgraphdep.controller;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.ContinuationPoint;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeCalculationResult;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.EndDayCommand;
import com.ctgraphdep.session.commands.UpdateSessionCalculationsCommand;
import com.ctgraphdep.session.query.GetCurrentSessionQuery;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Controller
@PreAuthorize("isAuthenticated()")
@RequestMapping("/user/session/resolve")
public class SessionResolutionController extends BaseController {

    private final SessionCommandService commandService;
    private final SessionCommandFactory commandFactory;
    private final UserService userService;

    public SessionResolutionController(SessionCommandService commandService, SessionCommandFactory commandFactory, UserService userService) {
        super(commandService.getContext().getUserService(), commandService.getContext().getFolderStatusService());
        this.commandService = commandService;
        this.commandFactory = commandFactory;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String showResolutionPage(
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            // Ensure authentication is not null
            if (authentication == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Get username from authentication
            String username = authentication.getName();

            // Get user from UserService
            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            // Get the current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Check if session has start time
            if (session == null || session.getDayStartTime() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "No valid session found");
                return "redirect:/user/session";
            }

            // Check if session needs resolution
            // A session needs resolution if:
            // 1. It has no end time (this captures both midnight handler sessions and forgotten sessions)
            // 2. OR it's marked as not completed (for safety)
            boolean needsResolution = session.getDayEndTime() == null || !session.getWorkdayCompleted();

            if (!needsResolution) {
                redirectAttributes.addFlashAttribute("infoMessage", "This session is already resolved");
                return "redirect:/user/session";
            }

            // Get the date from the session
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();

            // Calculate default end time using continuation points or default
            LocalTime defaultEndTime = calculateDefaultEndTime(username, sessionDate);

            // Format date for display - KEEP THE ORIGINAL DATE TOO
            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("EEEE dd - MMMM - yyyy");
            String formattedDate = sessionDate.format(displayFormatter);

            // Add data to model
            model.addAttribute("user", username);
            model.addAttribute("date", sessionDate);
            model.addAttribute("formattedDate", formattedDate); // Add formatted version
            model.addAttribute("startTimeFormatted", session.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            model.addAttribute("session", session);
            model.addAttribute("isTemporaryStop", WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
            model.addAttribute("defaultHour", defaultEndTime.getHour());
            model.addAttribute("defaultMinute", defaultEndTime.getMinute());

            // Add hours and minutes options for dropdown
            model.addAttribute("hours", getHoursOptions());
            model.addAttribute("minutes", getMinutesOptions());

            return "user/session-resolution";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error preparing session resolution page: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error loading session resolution data: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    private LocalTime calculateDefaultEndTime(String username, LocalDate sessionDate) {
        try {
            // First try to get from continuation points
            List<ContinuationPoint> continuationPoints = commandService.getContext()
                    .getContinuationTrackingService()
                    .getActiveContinuationPoints(username, sessionDate);

            if (!continuationPoints.isEmpty()) {
                Optional<ContinuationPoint> latestPoint = continuationPoints.stream()
                        .max(Comparator.comparing(ContinuationPoint::getTimestamp));

                if (latestPoint.isPresent() && latestPoint.get().getTimestamp() != null) {
                    LocalTime pointTime = latestPoint.get().getTimestamp().toLocalTime();
                    LoggerUtil.info(this.getClass(),
                            String.format("Using continuation point time for default: %s", pointTime));
                    return pointTime;
                }
            }

            // If no continuation point, use schedule-based default
            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            int scheduleHours = user.getSchedule();
            // For 8-hour schedule add lunch break
            int endHour = sessionDate.getDayOfWeek().getValue() >= 6 ? 13 : (scheduleHours == 8 ? 17 : scheduleHours + 9);
            int endMinute = 0;

            return LocalTime.of(endHour, endMinute);
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error calculating default end time: " + e.getMessage());
            return LocalTime.of(17, 0); // Fallback to 5:00 PM
        }
    }

    @PostMapping
    public String resolveSession(
            Authentication authentication,
            @RequestParam int endHour,
            @RequestParam int endMinute,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.debug(this.getClass(),
                    String.format("Resolving session - Received Hour: %d, Minute: %d", endHour, endMinute));

            // Ensure authentication is not null
            if (authentication == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Get username from authentication
            String username = authentication.getName();

            // Get user from UserService
            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            // Get the current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            if (session == null || session.getDayStartTime() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "No valid session to resolve");
                return "redirect:/user/session";
            }

            // Get session date
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();

            // Create end time from user input
            LocalDateTime endTime = LocalDateTime.of(sessionDate, LocalTime.of(endHour, endMinute));
            LoggerUtil.debug(this.getClass(), String.format("Resolving session - User selected time: %s from session date %s", endTime, sessionDate));

            // Update session end time
            session.setDayEndTime(endTime);

            // If session was in temporary stop, resume first
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // Simulate resume by updating the last temporary stop
                commandService.getContext().updateLastTemporaryStop(session, endTime);
            }

            // Ensure session is in a state that can be processed
            // For Offline sessions from midnight handler, set to Online temporarily for calculation
            boolean wasOffline = false;
            if (WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                wasOffline = true;
                session.setSessionStatus(WorkCode.WORK_ONLINE);
                LoggerUtil.info(this.getClass(), "Temporarily setting Offline session to Online for calculation");
            }

            // Update calculations based on the new end time
            UpdateSessionCalculationsCommand updateCommand = commandFactory.createUpdateSessionCalculationsCommand(session,endTime);
            session = commandService.executeCommand(updateCommand);

            // Calculate worked minutes based on session
            int workedMinutes = commandService.getContext().calculateRawWorkMinutes(session, endTime);
            session.setTotalWorkedMinutes(workedMinutes);

            // Get final worked minutes from user's schedule
            int userSchedule = user.getSchedule();
            WorkTimeCalculationResult result = commandService.getContext().calculateWorkTime(
                    workedMinutes, userSchedule);

            // If we temporarily changed to Online, set back to Offline for consistency
            if (wasOffline) {
                session.setSessionStatus(WorkCode.WORK_OFFLINE);
            }

            // End the session using command with explicit end time and calculated results
            EndDayCommand endCommand = commandFactory.createEndDayCommand(
                    username,
                    user.getUserId(),
                    result.getProcessedMinutes(),
                    endTime);  // Pass the explicit end time

            commandService.executeCommand(endCommand);

            // Mark continuation points as resolved
            commandService.getContext().getContinuationTrackingService()
                    .resolveContinuationPoints(
                            username,
                            sessionDate,
                            username,
                            result.getOvertimeMinutes());

            redirectAttributes.addFlashAttribute("successMessage", "Previous session resolved successfully");
            LoggerUtil.debug(this.getClass(), "Session resolved successfully. Redirecting to /user/session");
            return "redirect:/user/session";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error resolving session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to resolve session: " + e.getMessage());
            return "redirect:/user/session/resolve";
        }
    }

    @PostMapping("/skip")
    public String skipResolution(
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            // Ensure authentication is not null
            if (authentication == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Get username from authentication
            String username = authentication.getName();

            // Get user from UserService
            User user = userService.getUserByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            // Get the current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Get session date
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();

            // Use default end time (17:00)
            LocalDateTime endTime = LocalDateTime.of(sessionDate, LocalTime.of(17, 0));

            // Update session end time
            session.setDayEndTime(endTime);

            // If session was in temporary stop, resume first
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // Simulate resume by updating the last temporary stop with standard end time
                commandService.getContext().updateLastTemporaryStop(session, endTime);
            }

            // Handle offline sessions from midnight handler
            boolean wasOffline = false;
            if (WorkCode.WORK_OFFLINE.equals(session.getSessionStatus())) {
                wasOffline = true;
                session.setSessionStatus(WorkCode.WORK_ONLINE);
                LoggerUtil.info(this.getClass(),
                        "Temporarily setting Offline session to Online for skip resolution");
            }

            // End the session using command with standard hours and explicit end time
            int standardMinutes = user.getSchedule() * 60; // Standard schedule in minutes

            // If we temporarily changed to Online, set back to Offline for consistency
            if (wasOffline) {
                session.setSessionStatus(WorkCode.WORK_OFFLINE);
            }

            EndDayCommand endCommand = commandFactory.createEndDayCommand(
                    username,
                    user.getUserId(),
                    standardMinutes,
                    endTime);  // Pass the explicit end time

            commandService.executeCommand(endCommand);

            // Mark continuation points as resolved (no overtime)
            commandService.getContext().getContinuationTrackingService()
                    .resolveContinuationPoints(
                            username,
                            sessionDate,
                            username,
                            0);

            redirectAttributes.addFlashAttribute("infoMessage", "Session resolution skipped. Standard schedule recorded.");

            return "redirect:/user/session";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error skipping session resolution: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to skip session resolution: " + e.getMessage());
            return "redirect:/user/session/resolve";
        }
    }

    private int[] getHoursOptions() {
        int[] hours = new int[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = i;
        }
        return hours;
    }

    private int[] getMinutesOptions() {
        return new int[]{0, 15, 30, 45};
    }
}