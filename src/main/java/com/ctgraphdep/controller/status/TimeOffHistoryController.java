package com.ctgraphdep.controller.status;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.TimeOffTracker;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.worktime.commands.status.LoadUserTimeOffStatusCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TimeOffHistoryController - Handles time off history viewing functionality.
 * Displays approved time off entries and summary for a specific user and year.
 *
 * Part of StatusController refactoring - separated from monolithic StatusController.
 */
@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class TimeOffHistoryController extends BaseController {

    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final WorktimeOperationContext worktimeContext;

    public TimeOffHistoryController(UserService userService,
                                   FolderStatus folderStatus,
                                   TimeValidationService timeValidationService,
                                   WorktimeOperationContext worktimeContext) {
        super(userService, folderStatus, timeValidationService);
        this.worktimeContext = worktimeContext;
    }

    /**
     * Time off history page
     * REFACTORED: Now uses LoadUserTimeOffStatusCommand
     */
    @GetMapping("/timeoff-history")
    public String getTimeOffHistory(@RequestParam(required = false) Integer userId,
                                    @RequestParam(required = false) String username,
                                    @RequestParam(required = false) Integer year,
                                    Model model, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing time off history at " + getStandardCurrentDateTime());

            // We need either userId or username
            if (userId == null && (username == null || username.isEmpty())) {
                redirectAttributes.addFlashAttribute("errorMessage", "User information is required");
                return "redirect:/status";
            }

            // Use determineYear from BaseController
            int selectedYear = determineYear(year);

            // Get user details either by ID or username
            Optional<User> userOpt;
            if (userId != null) {
                LoggerUtil.info(this.getClass(), "Accessing time off history for user ID: " + userId);
                userOpt = getUserService().getUserById(userId);
            } else {
                LoggerUtil.info(this.getClass(), "Accessing time off history for username: " + username);
                userOpt = getUserService().getUserByUsername(username);
            }

            if (userOpt.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "User not found");
                redirectAttributes.addFlashAttribute("errorMessage", "User not found");
                return "redirect:/status";
            }

            User user = userOpt.get();

            // REFACTORED: Use LoadUserTimeOffStatusCommand instead of StatusService
            LoadUserTimeOffStatusCommand command = new LoadUserTimeOffStatusCommand(
                    worktimeContext, user.getUsername(), user.getUserId(), selectedYear);

            OperationResult result = command.execute();

            TimeOffTracker tracker = null;
            TimeOffSummaryDTO summary = null;
            List<WorkTimeTable> timeOffs = new ArrayList<>();

            if (result.isSuccess() && result.getData() instanceof LoadUserTimeOffStatusCommand.TimeOffStatusData statusData) {
                tracker = statusData.getTracker();
                summary = statusData.getSummary();
                timeOffs = statusData.getApprovedEntries();

                LoggerUtil.info(this.getClass(), String.format(
                        "Time off history loaded for user %s: %d approved entries",
                        user.getUsername(), timeOffs.size()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Time off history failed for user %s: %s", user.getUsername(), result.getMessage()));
            }

            // Add data to model
            model.addAttribute("user", user);
            model.addAttribute("timeOffs", timeOffs);
            model.addAttribute("tracker", tracker);
            model.addAttribute("summary", summary);
            model.addAttribute("year", selectedYear);
            model.addAttribute("currentSystemTime", formatCurrentDateTime());

            return "status/timeoff-history";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error viewing time off history: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading time off history");
            return "redirect:/status";
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Formats the current datetime using the standard pattern
     */
    private String formatCurrentDateTime() {
        return getStandardCurrentDateTime().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN));
    }
}
