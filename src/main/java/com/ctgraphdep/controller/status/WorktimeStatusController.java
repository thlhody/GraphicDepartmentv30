package com.ctgraphdep.controller.status;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserWorktimeExcelExporter;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.ValidatePeriodCommand;
import com.ctgraphdep.worktime.commands.status.LoadUserWorktimeStatusCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.display.WorktimeDisplayService;
import com.ctgraphdep.worktime.model.OperationResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WorktimeStatusController - Handles worktime status viewing and export functionality.
 * Displays worktime entries and summary for a specific user, month, and year.
 * Part of StatusController refactoring - separated from monolithic StatusController.
 */
@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class WorktimeStatusController extends BaseController {

    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final WorktimeOperationContext worktimeContext;
    private final WorktimeDisplayService worktimeDisplayService;
    private final UserWorktimeExcelExporter userWorktimeExcelExporter;

    public WorktimeStatusController(UserService userService,
                                   FolderStatus folderStatus,
                                   TimeValidationService timeValidationService,
                                   WorktimeOperationContext worktimeContext,
                                   WorktimeDisplayService worktimeDisplayService,
                                   UserWorktimeExcelExporter userWorktimeExcelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.worktimeContext = worktimeContext;
        this.worktimeDisplayService = worktimeDisplayService;
        this.userWorktimeExcelExporter = userWorktimeExcelExporter;
    }

    /**
     * Worktime status page
     * REFACTORED: Now uses LoadUserWorktimeStatusCommand
     */
    @GetMapping("/worktime-status")
    public String getWorktimeStatus(@AuthenticationPrincipal UserDetails userDetails,
                                    @RequestParam(required = false) String username,
                                    @RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) Integer month,
                                    Model model, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing worktime status at " + getStandardCurrentDateTime());

            // Get current authenticated user
            User currentUser = getUser(userDetails);

            // Determine target user (user being viewed)
            User targetUser = determineTargetUser(currentUser, username);

            // Use determineYear and determineMonth from BaseController
            int currentYear = determineYear(year);
            int currentMonth = determineMonth(month);

            // Validate period
            try {
                ValidatePeriodCommand validateCommand = getTimeValidationService().getValidationFactory().createValidatePeriodCommand(currentYear, currentMonth, 24);
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                String userMessage = "The selected period is not valid. You can only view periods up to 24 months in the future.";
                redirectAttributes.addFlashAttribute("periodError", userMessage);
                LocalDate currentDate = getStandardCurrentDate();
                return "redirect:/status/worktime-status?username=" + (username != null ? username : "") +
                        "&year=" + currentDate.getYear() + "&month=" + currentDate.getMonthValue();
            }

            // Check permissions
            boolean canViewOtherUser = !targetUser.getUsername().equals(currentUser.getUsername()) &&
                    !currentUser.hasRole(SecurityConstants.ROLE_ADMIN) &&
                    !currentUser.hasRole(SecurityConstants.ROLE_TEAM_LEADER) &&
                    !currentUser.hasRole(SecurityConstants.ROLE_TL_CHECKING);

            if (canViewOtherUser) {
                redirectAttributes.addFlashAttribute("errorMessage", "You don't have permission to view other users' worktime data");
                return "redirect:/status";
            }

            // REFACTORED: Use LoadUserWorktimeStatusCommand instead of StatusService
            LoadUserWorktimeStatusCommand command = new LoadUserWorktimeStatusCommand(
                    worktimeContext, targetUser.getUsername(), currentYear, currentMonth);

            OperationResult result = command.execute();

            List<WorkTimeTable> worktimeData = new ArrayList<>();
            if (result.isSuccess() && result.getEntriesData() != null) {
                worktimeData = result.getEntriesData();
                LoggerUtil.info(this.getClass(), String.format(
                        "Worktime status loaded for user %s: %d entries", targetUser.getUsername(), worktimeData.size()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Worktime status failed for user %s: %s", targetUser.getUsername(), result.getMessage()));
            }

            // Prepare display data using WorktimeDisplayService (keep this as it's separate concern)
            Map<String, Object> displayData = worktimeDisplayService.prepareWorktimeDisplayData(
                    targetUser, worktimeData, currentYear, currentMonth);

            // Add all data to model
            model.addAllAttributes(displayData);

            // Add current system time
            model.addAttribute("currentSystemTime", formatCurrentDateTime());
            model.addAttribute("standardDate", getStandardCurrentDate());

            return "status/worktime-status";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting worktime status: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading worktime data: " + e.getMessage());
            return "redirect:/status";
        }
    }

    /**
     * Export worktime data to Excel
     * REFACTORED: Now uses LoadUserWorktimeStatusCommand
     */
    @GetMapping("/worktime-status/export")
    public ResponseEntity<byte[]> exportWorktimeData(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        try {
            LoggerUtil.info(this.getClass(), "Exporting worktime data at " + getStandardCurrentDateTime());

            // Get current authenticated user
            User currentUser = getUser(userDetails);

            // Determine target user
            User targetUser = determineTargetUser(currentUser, username);

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // REFACTORED: Use LoadUserWorktimeStatusCommand instead of StatusService
            LoadUserWorktimeStatusCommand command = new LoadUserWorktimeStatusCommand(
                    worktimeContext, targetUser.getUsername(), selectedYear, selectedMonth);

            OperationResult result = command.execute();

            List<WorkTimeTable> worktimeData = new ArrayList<>();
            if (result.isSuccess() && result.getEntriesData() != null) {
                worktimeData = result.getEntriesData();
            }

            // Get display data which includes the summary
            Map<String, Object> displayData = worktimeDisplayService.prepareWorktimeDisplayData(
                    targetUser, worktimeData, selectedYear, selectedMonth);

            // Extract the DTOs from display data
            @SuppressWarnings("unchecked")
            List<WorkTimeEntryDTO> entryDTOs = (List<WorkTimeEntryDTO>) displayData.get("worktimeData");
            WorkTimeSummaryDTO summaryDTO = (WorkTimeSummaryDTO) displayData.get("summary");

            // Use the updated exporter to generate Excel data with DTOs
            byte[] excelData = userWorktimeExcelExporter.exportToExcel(targetUser, entryDTOs, summaryDTO, selectedYear, selectedMonth);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"worktime_%s_%d_%02d.xlsx\"",
                            targetUser.getUsername(), selectedYear, selectedMonth))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
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

    /**
     * Helper method to determine target user based on permissions
     */
    private User determineTargetUser(User currentUser, String requestedUsername) {
        if (requestedUsername == null || requestedUsername.isEmpty()) {
            return currentUser;
        }

        // Anyone can view status data for any user (read-only)
        return getUserService().getUserByUsername(requestedUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
