package com.ctgraphdep.controller;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.model.ContinuationPoint;
import com.ctgraphdep.model.TemporaryStop;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkUsersSessionsStates;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.session.SessionCommandFactory;
import com.ctgraphdep.session.SessionCommandService;
import com.ctgraphdep.session.commands.*;
import com.ctgraphdep.session.query.*;
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
import java.util.Objects;
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
            User user = userService.getUserByUsername(username).orElseThrow(() -> new IllegalStateException("User not found"));

            // Get the current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            // Check if session has start time
            if (session == null || session.getDayStartTime() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "No valid session found");
                return "redirect:/user/session";
            }

            // Check if session needs resolution using the query
            NeedsResolutionQuery needsResolutionQuery = commandFactory.createNeedsResolutionQuery(session);
            boolean needsResolution = commandService.executeQuery(needsResolutionQuery);

            if (!needsResolution) {
                redirectAttributes.addFlashAttribute("infoMessage", "This session is already resolved");
                return "redirect:/user/session";
            }

            // Get the date from the session
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();
            // Calculate default end time using continuation points or default
            LocalTime defaultEndTime = calculateDefaultEndTime(username, sessionDate);
            // Create a simulated end time for initial display
            LocalDateTime simulatedEndTime = LocalDateTime.of(sessionDate, defaultEndTime);

            // Format date for display
            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("EEEE dd - MMMM - yyyy");
            String formattedDate = sessionDate.format(displayFormatter);

            // Use the commands to ensure temporary stops are correctly processed
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // This ensures any temporary stops are properly finalized for display
                UpdateLastTemporaryStopCommand updateCommand = commandFactory.createUpdateLastTemporaryStopCommand(session, simulatedEndTime);
                commandService.executeCommand(updateCommand);
            }

            // Update session calculations with the simulated end time
            UpdateSessionCalculationsCommand updateCommand = commandFactory.createUpdateSessionCalculationsCommand(session, simulatedEndTime);
            session = commandService.executeCommand(updateCommand);

            // Make sure each temporary stop is properly formatted for display
            if (session.getTemporaryStops() != null && !session.getTemporaryStops().isEmpty()) {
                for (TemporaryStop stop : session.getTemporaryStops()) {
                    // Ensure end time is set
                    if (stop.getEndTime() == null && session.getLastTemporaryStopTime() != null) {
                        stop.setEndTime(session.getLastTemporaryStopTime());
                    }

                    // Ensure duration is calculated
                    if (stop.getDuration() == null && stop.getStartTime() != null && stop.getEndTime() != null) {
                        long minutes = java.time.Duration.between(stop.getStartTime(), stop.getEndTime()).toMinutes();
                        stop.setDuration((int) minutes);
                    }
                }
            }

            // Debug code to check temporary stops
            if (session.getTemporaryStops() != null) {
                LoggerUtil.info(this.getClass(), "Number of temporary stops: " + session.getTemporaryStops().size());
                for (TemporaryStop stop : session.getTemporaryStops()) {
                    LoggerUtil.info(this.getClass(), String.format("Stop: Start=%s, End=%s, Duration=%d", stop.getStartTime(), stop.getEndTime(), stop.getDuration()));
                }
            } else {
                LoggerUtil.info(this.getClass(), "Temporary stops list is null");
            }

            // IMPORTANT: Format break time explicitly for display
            int totalTempStopMinutes = session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0;

            int tempStopHours = totalTempStopMinutes / 60;
            int tempStopMinutes = totalTempStopMinutes % 60;
            String formattedBreakTime = String.format("%02d:%02d", tempStopHours, tempStopMinutes);

            LoggerUtil.info(this.getClass(), "Total temporary stop minutes from session: " + totalTempStopMinutes);
            LoggerUtil.info(this.getClass(), "Formatted break time: " + formattedBreakTime);

            // Explicitly add to model for JavaScript and display
            model.addAttribute("totalTempStopMinutes", totalTempStopMinutes);
            model.addAttribute("formattedBreakTime", formattedBreakTime);

            // Use the PrepareSessionViewModelCommand to ensure consistent formatting
            PrepareSessionViewModelCommand viewModelCommand = commandFactory.createPrepareSessionViewModelCommand(model, session, user);
            commandService.executeCommand(viewModelCommand);

            // Add additional data specific to the resolution page
            model.addAttribute("user", username);
            model.addAttribute("date", sessionDate);
            model.addAttribute("formattedDate", formattedDate);
            model.addAttribute("startTimeFormatted", session.getDayStartTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            model.addAttribute("isTemporaryStop", WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus()));
            model.addAttribute("defaultHour", defaultEndTime.getHour());
            model.addAttribute("defaultMinute", defaultEndTime.getMinute());
            model.addAttribute("hours", getHoursOptions());
            model.addAttribute("minutes", getMinutesOptions());
            model.addAttribute("temporaryStops", session.getTemporaryStops());
            // Log temporary stops for debugging
            logTemporaryStopsInfo(session);

            return "user/session-resolution";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error preparing session resolution page: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading session resolution data: " + e.getMessage());
            return "redirect:/user/session";
        }
    }

    /**
     * Log temporary stops information for debugging
     */
    private void logTemporaryStopsInfo(WorkUsersSessionsStates session) {
        if (session.getTemporaryStops() != null && !session.getTemporaryStops().isEmpty()) {
            LoggerUtil.info(this.getClass(), String.format("Session has %d temporary stops totaling %d minutes", session.getTemporaryStopCount(), session.getTotalTemporaryStopMinutes()));

            session.getTemporaryStops().forEach(stop -> {
                LoggerUtil.info(this.getClass(), String.format("Temporary stop: %s to %s (duration: %d minutes)", stop.getStartTime(), stop.getEndTime(), stop.getDuration()));
            });
        } else {
            LoggerUtil.info(this.getClass(), "Session has no temporary stops");
        }
    }

    /**
     * Calculate default end time based on continuation points or schedule
     */
    private LocalTime calculateDefaultEndTime(String username, LocalDate sessionDate) {
        try {
            // Use the query to get continuation points
            GetActiveContinuationPointsQuery pointsQuery = commandFactory.createGetActiveContinuationPointsQuery(username, sessionDate);
            List<ContinuationPoint> continuationPoints = commandService.executeQuery(pointsQuery);

            if (!continuationPoints.isEmpty()) {
                // Find the continuation point with the latest timestamp
                Optional<ContinuationPoint> latestPoint = continuationPoints.stream().max(Comparator.comparing(ContinuationPoint::getTimestamp));
                // Since we checked the list isn't empty, we know latestPoint will have a value
                if (latestPoint.get().getTimestamp() != null) {
                    LocalTime pointTime = latestPoint.get().getTimestamp().toLocalTime();
                    LoggerUtil.info(this.getClass(), String.format("Using continuation point time for default: %s", pointTime));
                    return pointTime;
                }
            }

            // If no continuation point, use WorkScheduleQuery to get expected end time
            User user = userService.getUserByUsername(username).orElseThrow(() -> new IllegalStateException("User not found"));

            WorkScheduleQuery query = commandFactory.createWorkScheduleQuery(sessionDate, user.getSchedule());
            WorkScheduleQuery.ScheduleInfo scheduleInfo = commandService.executeQuery(query);

            return scheduleInfo.getExpectedEndTime();
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(), "Error calculating default end time: " + e.getMessage());
            return LocalTime.of(15, 30); // Fallback to 3:30 PM
        }
    }

    @PostMapping
    public String resolveSession(
            Authentication authentication,
            @RequestParam int endHour,
            @RequestParam int endMinute,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.debug(this.getClass(), String.format("Resolving session - Received Hour: %d, Minute: %d", endHour, endMinute));

            // Ensure authentication is not null
            if (authentication == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Get username from authentication
            String username = authentication.getName();

            // Get user from UserService
            User user = userService.getUserByUsername(username).orElseThrow(() -> new IllegalStateException("User not found"));

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

            // Use the ResolveSessionCommand to handle the resolution logic
            ResolveSessionCommand resolveCommand = commandFactory.createResolveSessionCommand(username, user.getUserId(), endTime);
            commandService.executeCommand(resolveCommand);

            redirectAttributes.addFlashAttribute("successMessage", "Previous session resolved successfully");
            LoggerUtil.debug(this.getClass(), "Session resolved successfully. Redirecting to /user/session");
            return "redirect:/user/session";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error resolving session: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to resolve session: " + e.getMessage());
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
            User user = userService.getUserByUsername(username).orElseThrow(() -> new IllegalStateException("User not found"));

            // Get the current session
            GetCurrentSessionQuery sessionQuery = commandFactory.createGetCurrentSessionQuery(username, user.getUserId());
            WorkUsersSessionsStates session = commandService.executeQuery(sessionQuery);

            if (session == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "No valid session to skip");
                return "redirect:/user/session";
            }

            // Get session date
            LocalDate sessionDate = session.getDayStartTime().toLocalDate();
            // Use default end time (15:30)
            LocalDateTime endTime = LocalDateTime.of(sessionDate, LocalTime.of(15, 30));

            // If session was in temporary stop, resume first
            if (WorkCode.WORK_TEMPORARY_STOP.equals(session.getSessionStatus())) {
                // Use the command to update temporary stop
                UpdateLastTemporaryStopCommand tempStopCommand = commandFactory.createUpdateLastTemporaryStopCommand(session, endTime);
                commandService.executeCommand(tempStopCommand);
            }

            // End the session using standard schedule hours
            int standardMinutes = user.getSchedule() * 60; // Standard schedule in minutes

            // End the session
            EndDayCommand endCommand = commandFactory.createEndDayCommand(username, user.getUserId(), standardMinutes, endTime);
            commandService.executeCommand(endCommand);

            // Mark continuation points as resolved (no overtime)
            ResolveContinuationPointsCommand resolvePointsCommand = commandFactory.createResolveContinuationPointsCommand(username, sessionDate, username, 0);
            commandService.executeCommand(resolvePointsCommand);

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