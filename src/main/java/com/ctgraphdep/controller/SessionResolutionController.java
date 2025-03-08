package com.ctgraphdep.controller;

import com.ctgraphdep.config.WorkCode;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.SyncStatus;
import com.ctgraphdep.model.*;
import com.ctgraphdep.service.ContinuationTrackingService;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.UserSessionService;
import com.ctgraphdep.service.UserWorkTimeService;
import com.ctgraphdep.utils.LoggerUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for handling session resolution.
 * This allows users to resolve previous sessions that ended at midnight
 * or continued beyond schedule without proper ending.
 */
@Controller
@PreAuthorize("isAuthenticated()")
@RequestMapping("/user/session/resolve")
public class SessionResolutionController extends BaseController {

    private final UserSessionService userSessionService;
    private final ContinuationTrackingService continuationTrackingService;
    private final UserWorkTimeService userWorkTimeService;

    public SessionResolutionController(
            UserService userService,
            FolderStatusService folderStatusService,
            UserSessionService userSessionService,
            ContinuationTrackingService continuationTrackingService,
            UserWorkTimeService userWorkTimeService) {
        super(userService, folderStatusService);
        this.userSessionService = userSessionService;
        this.continuationTrackingService = continuationTrackingService;
        this.userWorkTimeService = userWorkTimeService;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Show the session resolution page for the previous day's session
     */
    @GetMapping
    public String showResolutionPage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) LocalDate date,
            Model model) {

        // Default to yesterday if no date provided
        if (date == null) {
            date = LocalDate.now().minusDays(1);
        }

        User user = getUser(userDetails);
        int scheduleHours = user.getSchedule();

        // Get continuation points for the date
        List<ContinuationPoint> continuationPoints =
                continuationTrackingService.getActiveContinuationPoints(user.getUsername(), date);

        // Check if there's a midnight end that needs resolution
        boolean hasMidnightEnd = continuationTrackingService.hasUnresolvedMidnightEnd(user.getUsername());

        // Calculate actual minutes worked based on continuation points
        int actualMinutesWorked = calculateActualMinutesFromContinuationPoints(user.getUsername(), date);

        // Determine appropriate overtime based on actual minutes
        int recommendedOvertime = 0;
        if (actualMinutesWorked > 0) {
            recommendedOvertime = determineOvertimeFromActualMinutes(actualMinutesWorked, scheduleHours);
        } else {
            // Fall back to existing recommendation if no actual minutes calculated
            recommendedOvertime = continuationTrackingService.getRecommendedOvertime(user.getUsername(), date);
        }

        // Make sure recommended overtime is in multiples of 60 (full hours)
        if (recommendedOvertime > 0) {
            // Round up to the nearest hour
            recommendedOvertime = ((recommendedOvertime + 59) / 60) * 60;
            // Cap at 8 hours (480 minutes) maximum overtime
            recommendedOvertime = Math.min(recommendedOvertime, 480);
        }

        // Calculate standard duration based on schedule
        int standardMinutes = calculateStandardDuration(scheduleHours);

        // Generate overtime options
        List<OvertimeOption> overtimeOptions = generateOvertimeOptions(scheduleHours, recommendedOvertime);

        // Add actual minutes worked to model for display
        model.addAttribute("actualMinutesWorked", actualMinutesWorked);
        model.addAttribute("actualHoursWorked", actualMinutesWorked / 60.0);

        // Add data to model
        model.addAttribute("user", user);
        model.addAttribute("userScheduleHours", scheduleHours);
        model.addAttribute("date", date);
        model.addAttribute("continuationPoints", continuationPoints);
        model.addAttribute("hasMidnightEnd", hasMidnightEnd);
        model.addAttribute("recommendedOvertime", recommendedOvertime);
        model.addAttribute("standardMinutes", standardMinutes);
        model.addAttribute("needsLunchBreak", scheduleHours == WorkCode.INTERVAL_HOURS_C);
        model.addAttribute("overtimeOptions", overtimeOptions);

        return "user/session-resolution";
    }

    /**
     * Calculates standard duration in minutes based on schedule hours
     */
    private int calculateStandardDuration(int scheduleHours) {
        // For 8-hour schedule: 8.5 hours (510 minutes)
        // For others: schedule hours only
        if (scheduleHours == WorkCode.INTERVAL_HOURS_C) {
            return (scheduleHours * WorkCode.HOUR_DURATION) + WorkCode.HALF_HOUR_DURATION;
        } else {
            return scheduleHours * WorkCode.HOUR_DURATION;
        }
    }

    /**
     * Generate overtime options based on schedule
     */
    private List<OvertimeOption> generateOvertimeOptions(int scheduleHours, int recommendedOvertime) {
        List<OvertimeOption> options = new ArrayList<>();
        boolean needsLunchBreak = scheduleHours == WorkCode.INTERVAL_HOURS_C;
        int standardMinutes = calculateStandardDuration(scheduleHours);

        // Always include standard option (0 overtime)
        options.add(new OvertimeOption(0, "Standard Schedule Only", standardMinutes, needsLunchBreak));

        // Add overtime options for 1-8 hours
        int[] overtimeValues = {60, 120, 180, 240, 300, 360, 420, 480};
        for (int i = 0; i < overtimeValues.length; i++) {
            int overtime = overtimeValues[i];
            // Skip if this is the recommended value (added separately)
            if (overtime == recommendedOvertime) continue;

            options.add(new OvertimeOption(
                    overtime,
                    (i+1) + " Hour" + (i > 0 ? "s" : "") + " Overtime",
                    standardMinutes + overtime,
                    needsLunchBreak
            ));

            // Limit to 4 options to avoid cluttering the UI
            if (i >= 3 && overtime > recommendedOvertime) break;
        }

        // If recommended overtime is different from standard options, add it
        if (recommendedOvertime > 0) {
            OvertimeOption recommended = new OvertimeOption(
                    recommendedOvertime,
                    "Recommended (" + (recommendedOvertime / 60) + " hours overtime)",
                    standardMinutes + recommendedOvertime,
                    needsLunchBreak
            );

            // Insert the recommended option at the top of the list
            options.add(0, recommended);
        }

        return options;
    }


    /**
     * Handle the resolution of a session
     */
    @PostMapping
    public String resolveSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam LocalDate date,
            @RequestParam int overtime,
            RedirectAttributes redirectAttributes) {

        User user = getUser(userDetails);

        try {
            // Calculate total minutes based on schedule and overtime
            int scheduleHours = user.getSchedule();
            int standardMinutes = calculateStandardDuration(scheduleHours);
            int totalMinutes = standardMinutes + overtime;

            // Create resolved session with appropriate values
            createResolvedSession(user, date, overtime, totalMinutes);

            // Then resolve continuation points
            continuationTrackingService.resolveContinuationPoints(
                    user.getUsername(), date, user.getUsername(), overtime);

            // Add confirmation message
            String overtimeMsg = overtime > 0 ? " and " + (overtime / 60) + " hour(s) of overtime" : "";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Previous session resolved successfully with standard schedule" + overtimeMsg);

            LoggerUtil.info(this.getClass(),
                    String.format("User %s resolved session for %s with %d minutes overtime",
                            user.getUsername(), date, overtime));

            // Redirect to session page to start new day
            return "redirect:/user/session";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error resolving session for user %s: %s",
                            user.getUsername(), e.getMessage()), e);

            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to resolve session: " + e.getMessage());

            return "redirect:/user/session/resolve?date=" + date;
        }
    }

    private void createResolvedSession(User user, LocalDate date, int overtime, int totalMinutes) {
        try {
            // First retrieve the session for the specified date (don't create new if it exists)
            WorkUsersSessionsStates session = userSessionService.getCurrentSession(user.getUsername(), user.getUserId());

            // If session doesn't exist or is for a different date, then create a new one
            if (session == null || session.getDayStartTime() == null ||
                    !session.getDayStartTime().toLocalDate().equals(date)) {

                // Create a new session with default 5 AM start time
                LocalDateTime startTime = LocalDateTime.of(date, LocalTime.of(5, 0));
                session = new WorkUsersSessionsStates();
                session.setUserId(user.getUserId());
                session.setUsername(user.getUsername());
                session.setDayStartTime(startTime);
                session.setCurrentStartTime(startTime);
                session.setTemporaryStopCount(0);
                session.setTotalTemporaryStopMinutes(0);
                session.setTemporaryStops(new ArrayList<>());
            }

            // Calculate times and values based on user's schedule
            int scheduleHours = user.getSchedule();
            boolean is8HourSchedule = (scheduleHours == WorkCode.INTERVAL_HOURS_C);

            // Determine appropriate total minutes based on schedule and overtime
            int totalWorkMinutes;
            if (is8HourSchedule) {
                // Use the special constants for 8-hour schedule
                if (overtime == 0) {
                    totalWorkMinutes = WorkCode.NORMAL_WORK_TIME;
                } else if (overtime == 60) {
                    totalWorkMinutes = WorkCode.OVERTIME_ONE;
                } else if (overtime == 120) {
                    totalWorkMinutes = WorkCode.OVERTIME_TWO;
                } else if (overtime == 180) {
                    totalWorkMinutes = WorkCode.OVERTIME_THREE;
                } else if (overtime == 240) {
                    totalWorkMinutes = WorkCode.OVERTIME_FOUR;
                } else if (overtime == 300) {
                    totalWorkMinutes = WorkCode.OVERTIME_FIVE;
                } else if (overtime == 360) {
                    totalWorkMinutes = WorkCode.OVERTIME_SIX;
                } else if (overtime == 420) {
                    totalWorkMinutes = WorkCode.OVERTIME_SEVEN;
                } else if (overtime == 480) {
                    totalWorkMinutes = WorkCode.OVERTIME_EIGHT;
                } else {
                    totalWorkMinutes = WorkCode.NORMAL_WORK_TIME + overtime;
                }
            } else {
                // For non-8-hour schedules
                totalWorkMinutes = scheduleHours * 60 + overtime;
            }

            // Calculate end time based on start time and the total minutes
            LocalDateTime endTime = session.getDayStartTime().plusMinutes(totalWorkMinutes);

            // Update session values
            session.setSessionStatus(WorkCode.WORK_OFFLINE);
            session.setDayEndTime(endTime);
            session.setTotalWorkedMinutes(totalWorkMinutes);
            session.setFinalWorkedMinutes(scheduleHours * 60);  // Standard hours without lunch
            session.setTotalOvertimeMinutes(overtime);
            session.setLunchBreakDeducted(is8HourSchedule);
            session.setWorkdayCompleted(true);
            session.setLastActivity(LocalDateTime.now());

            // Save the updated session
            userSessionService.saveSession(user.getUsername(), session);

            // Update worktime entry
            updateWorkTimeEntry(user, date, session, overtime, totalWorkMinutes);

            LoggerUtil.info(this.getClass(),
                    String.format("Created resolved session for user %s on %s with %d minutes overtime",
                            user.getUsername(), date, overtime));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error creating resolved session for user %s: %s",
                            user.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to create resolved session: " + e.getMessage(), e);
        }
    }

    /**
     * Skip session resolution and just start a new day
     */
    @PostMapping("/skip")
    public String skipResolution(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam LocalDate date,
            RedirectAttributes redirectAttributes) {

        User user = getUser(userDetails);

        try {
            // Use standard schedule with zero overtime when skipping
            createResolvedSession(user, date, 0);

            // Mark continuation points as resolved with 0 overtime
            continuationTrackingService.resolveContinuationPoints(
                    user.getUsername(), date, user.getUsername(), 0);

            // Add confirmation message
            redirectAttributes.addFlashAttribute("infoMessage",
                    "Session resolution skipped. Standard schedule recorded with no overtime.");

            LoggerUtil.info(this.getClass(),
                    String.format("User %s skipped session resolution for %s",
                            user.getUsername(), date));

            // Redirect to session page to start new day
            return "redirect:/user/session";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error skipping session resolution for user %s: %s",
                            user.getUsername(), e.getMessage()), e);

            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to skip session resolution: " + e.getMessage());

            return "redirect:/user/session/resolve?date=" + date;
        }
    }

    /**
     * Creates a properly resolved session for the given date and updates worktime
     * Uses the existing UserSessionService methods where possible
     */
    private void createResolvedSession(User user, LocalDate date, int overtime) {
        try {
            // First retrieve or create a session for the specified date
            WorkUsersSessionsStates session = retrieveOrCreateSessionForDay(user, date);

            // Set session status and mark as completed
            session.setSessionStatus(WorkCode.WORK_OFFLINE);
            session.setWorkdayCompleted(true);

            // Calculate times and values based on user's schedule
            int scheduleHours = user.getSchedule();
            boolean is8HourSchedule = (scheduleHours == WorkCode.INTERVAL_HOURS_C);

            // Start time at 9 AM on the specified date (or keep existing start time)
            LocalDateTime startTime = session.getDayStartTime();
            if (startTime == null || !startTime.toLocalDate().equals(date)) {
                startTime = LocalDateTime.of(date, LocalTime.of(9, 0));
                session.setDayStartTime(startTime);
            }

            // Determine end time based on schedule and overtime
            int scheduleMinutes = scheduleHours * WorkCode.HOUR_DURATION;
            int totalWorkMinutes = scheduleMinutes;

            // For 8-hour schedule, add lunch break to total work minutes
            if (is8HourSchedule) {
                totalWorkMinutes += WorkCode.HALF_HOUR_DURATION;
            }

            // Calculate end time
            LocalDateTime endTime = startTime.plusMinutes(totalWorkMinutes + overtime);
            session.setDayEndTime(endTime);

            // Set lunch break flag based on schedule
            session.setLunchBreakDeducted(is8HourSchedule);

            // Set work minutes
            session.setTotalWorkedMinutes(totalWorkMinutes);
            session.setFinalWorkedMinutes(scheduleMinutes);
            session.setTotalOvertimeMinutes(overtime);

            // Update last activity time
            session.setLastActivity(LocalDateTime.now());

            // Save the updated session
            userSessionService.saveSession(user.getUsername(), session);

            // Instead of using endDay (which might not update the worktime correctly),
            // Directly create/update the worktime entry
            updateWorkTimeEntry(user, date, session, overtime, totalWorkMinutes+overtime);

            LoggerUtil.info(this.getClass(),
                    String.format("Created resolved session for user %s on %s with %d minutes overtime",
                            user.getUsername(), date, overtime));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error creating resolved session for user %s: %s",
                            user.getUsername(), e.getMessage()), e);
            throw new RuntimeException("Failed to create resolved session: " + e.getMessage(), e);
        }
    }

    /**
     * Directly updates the worktime entry for the resolved session
     */
    private void updateWorkTimeEntry(User user, LocalDate date, WorkUsersSessionsStates session, int overtime, int totalWorkedMinutes) {
        try {
            // Create a worktime entry that matches the session values
            WorkTimeTable entry = new WorkTimeTable();
            entry.setUserId(user.getUserId());
            entry.setWorkDate(date);
            entry.setDayStartTime(session.getDayStartTime());
            entry.setDayEndTime(session.getDayEndTime());
            entry.setTotalWorkedMinutes(totalWorkedMinutes);
            entry.setTotalOvertimeMinutes(overtime);

            // Preserve any existing temporary stop information
            entry.setTemporaryStopCount(session.getTemporaryStopCount() != null ? session.getTemporaryStopCount() : 0);
            entry.setTotalTemporaryStopMinutes(session.getTotalTemporaryStopMinutes() != null ? session.getTotalTemporaryStopMinutes() : 0);

            // Set lunch break status
            entry.setLunchBreakDeducted(session.getLunchBreakDeducted());

            // Set as completed
            entry.setAdminSync(SyncStatus.USER_INPUT);
            entry.setTimeOffType(null);

            // Save the worktime entry
            userWorkTimeService.saveWorkTimeEntry(
                    user.getUsername(),
                    entry,
                    date.getYear(),
                    date.getMonthValue(),
                    user.getUsername()
            );

            LoggerUtil.info(this.getClass(),
                    String.format("Updated worktime entry for user %s on %s", user.getUsername(), date));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error updating worktime entry: %s", e.getMessage()));
            throw new RuntimeException("Failed to update worktime entry: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves existing session for the day or creates a new one if none exists
     */
    private WorkUsersSessionsStates retrieveOrCreateSessionForDay(User user, LocalDate date) {
        // Try to get existing session
        WorkUsersSessionsStates session = userSessionService.getCurrentSession(user.getUsername(), user.getUserId());

        // If no session or if session is for a different day, create a new one
        if (session == null || session.getDayStartTime() == null ||
                !session.getDayStartTime().toLocalDate().equals(date)) {

            // Create a new session for the specified date
            LocalDateTime startTime = LocalDateTime.of(date, LocalTime.of(7, 0));
            session = new WorkUsersSessionsStates();
            session.setUserId(user.getUserId());
            session.setUsername(user.getUsername());
            session.setSessionStatus(WorkCode.WORK_OFFLINE);
            session.setDayStartTime(startTime);
            session.setCurrentStartTime(startTime);
            session.setTemporaryStopCount(0);
            session.setTotalTemporaryStopMinutes(0);
            session.setTemporaryStops(new java.util.ArrayList<>());
            session.setLastActivity(LocalDateTime.now());
        }

        return session;
    }


    /**
     * Determines the appropriate overtime in minutes based on the actual worked minutes
     */
    private int determineOvertimeFromActualMinutes(int actualMinutes, int scheduleHours) {
        // Convert schedule hours to minutes (including lunch break for 8-hour schedule)
        int scheduleMinutes;
        if (scheduleHours == WorkCode.INTERVAL_HOURS_C) { // 8 hours
            scheduleMinutes = WorkCode.NORMAL_WORK_TIME; // 8.5 hours including break
        } else {
            scheduleMinutes = scheduleHours * 60; // Other schedules
        }

        // If worked less than schedule, no overtime
        if (actualMinutes <= scheduleMinutes) {
            return 0;
        }

        // Calculate how many full hours of overtime based on thresholds
        if (actualMinutes <= WorkCode.OVERTIME_ONE) {
            return 60; // 1 hour overtime
        } else if (actualMinutes <= WorkCode.OVERTIME_TWO) {
            return 120; // 2 hours overtime
        } else if (actualMinutes <= WorkCode.OVERTIME_THREE) {
            return 180; // 3 hours overtime
        } else if (actualMinutes <= WorkCode.OVERTIME_FOUR) {
            return 240; // 4 hours overtime
        } else if (actualMinutes <= WorkCode.OVERTIME_FIVE) {
            return 300; // 5 hours overtime
        } else if (actualMinutes <= WorkCode.OVERTIME_SIX) {
            return 360; // 6 hours overtime
        } else if (actualMinutes <= WorkCode.OVERTIME_SEVEN) {
            return 420; // 7 hours overtime
        } else {
            return 480; // 8 hours overtime (maximum)
        }
    }

    /**
     * Calculate actual worked minutes from continuation points
     */
    private int calculateActualMinutesFromContinuationPoints(String username, LocalDate date) {
        List<ContinuationPoint> points = continuationTrackingService.getActiveContinuationPoints(username, date);

        // If no continuation points, use 0 (will default to standard schedule)
        if (points.isEmpty()) {
            return 0;
        }

        // Find the latest timestamp among all continuation points
        LocalDateTime latestPoint = points.stream()
                .map(ContinuationPoint::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        if (latestPoint == null) {
            return 0;
        }

        // Assume work started at 9 AM
        LocalDateTime startTime = LocalDateTime.of(date, LocalTime.of(9, 0));

        // Calculate minutes between start and latest continuation point
        return (int) ChronoUnit.MINUTES.between(startTime, latestPoint);
    }


    /**
     * Inner class to represent overtime options
     */
    @Data
    @AllArgsConstructor
    public static class OvertimeOption {
        private int overtimeMinutes;
        private String label;
        private int totalMinutes; // Standard + overtime
        private boolean needsLunchBreak;

        public String getFormattedDuration() {
            int hours = totalMinutes / 60;
            int minutes = totalMinutes % 60;
            return minutes > 0 ?
                    String.format("%d hours %d minutes", hours, minutes) :
                    String.format("%d hours", hours);
        }
    }
}