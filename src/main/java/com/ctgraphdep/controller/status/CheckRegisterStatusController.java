package com.ctgraphdep.controller.status;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ApprovalStatusType;
import com.ctgraphdep.enums.CheckType;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.RegisterCheckEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.CheckRegisterStatusExcelExporter;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.ValidatePeriodCommand;
import com.ctgraphdep.worktime.commands.status.LoadUserCheckRegisterStatusCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CheckRegisterStatusController - Handles check register viewing and export functionality.
 * Displays check register entries with search filters and allows Excel export.
 *
 * Part of StatusController refactoring - separated from monolithic StatusController.
 */
@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class CheckRegisterStatusController extends BaseController {

    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final WorktimeOperationContext worktimeContext;
    private final CheckRegisterStatusExcelExporter checkRegisterExcelExporter;

    public CheckRegisterStatusController(UserService userService,
                                        FolderStatus folderStatus,
                                        TimeValidationService timeValidationService,
                                        WorktimeOperationContext worktimeContext,
                                        CheckRegisterStatusExcelExporter checkRegisterExcelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.worktimeContext = worktimeContext;
        this.checkRegisterExcelExporter = checkRegisterExcelExporter;
    }

    /**
     * Check register status page with search functionality
     * FIXED: Now uses LoadUserCheckRegisterStatusCommand
     */
    @GetMapping("/check-register-status")
    public String getCheckRegister(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) String designerName,
            @RequestParam(required = false) String approvalStatus,
            Model model, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing check register search at " + getStandardCurrentDateTime());

            // Get the current user and determine the target user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Authentication required");
                return "redirect:/login";
            }

            // Determine target user
            User targetUser = determineTargetUser(currentUser, username);

            // Add user info to model
            model.addAttribute("user", targetUser);

            // Use determineYear and determineMonth from BaseController
            int displayYear = determineYear(year);
            int displayMonth = determineMonth(month);

            // Validate period
            try {
                ValidatePeriodCommand validateCommand = getTimeValidationService().getValidationFactory()
                        .createValidatePeriodCommand(displayYear, displayMonth, 12);
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                String userMessage = "The selected period is not valid. You can only view periods up to 4 months in the future.";
                redirectAttributes.addFlashAttribute("periodError", userMessage);
                LocalDate currentDate = getStandardCurrentDate();
                return "redirect:/status/check-register-status?username=" + (username != null ? username : "") +
                        "&year=" + currentDate.getYear() + "&month=" + currentDate.getMonthValue();
            }

            // Set current period for the UI
            model.addAttribute("currentYear", displayYear);
            model.addAttribute("currentMonth", displayMonth);
            model.addAttribute("currentDate", getStandardCurrentDate());

            // Add check types and approval status types for dropdowns
            model.addAttribute("checkTypes", CheckType.getValues());
            model.addAttribute("approvalStatusTypes", ApprovalStatusType.getValues());

            // FIXED: Use LoadUserCheckRegisterStatusCommand
            LoadUserCheckRegisterStatusCommand command = new LoadUserCheckRegisterStatusCommand(
                    worktimeContext, targetUser.getUsername(), targetUser.getUserId(), displayYear, displayMonth,
                    searchTerm, startDate, endDate, checkType, designerName, approvalStatus);

            OperationResult result = command.execute();

            // Initialize with defaults
            List<RegisterCheckEntry> entries = new ArrayList<>();
            LoadUserCheckRegisterStatusCommand.CheckRegisterSummary summaryObj =
                    new LoadUserCheckRegisterStatusCommand.CheckRegisterSummary(); // Empty summary
            Set<String> designers = new HashSet<>();

            if (result.isSuccess() && result.getData() instanceof LoadUserCheckRegisterStatusCommand.CheckRegisterStatusData statusData) {
                entries = statusData.getEntries();
                summaryObj = statusData.getSummary();
                designers = new HashSet<>(statusData.getUniqueDesigners());

                LoggerUtil.info(this.getClass(), String.format(
                        "Check register loaded for user %s: %d entries", targetUser.getUsername(), entries.size()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Check register failed for user %s: %s", targetUser.getUsername(), result.getMessage()));
            }

            // FIXED: Set model attributes only once, correctly
            model.addAttribute("entries", entries);
            model.addAttribute("summary", summaryObj);  // ✅ Use the CheckRegisterSummary object
            model.addAttribute("designers", designers);

            // Add system time
            model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            return "status/check-register-status";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in check register: " + e.getMessage(), e);
            model.addAttribute("errorMessage", "Error processing check register: " + e.getMessage());
            model.addAttribute("entries", new ArrayList<>());
            model.addAttribute("currentDate", getStandardCurrentDate());
            return "redirect:/status";
        }
    }

    /**
     * Export check register to Excel
     * REFACTORED: Now uses LoadUserCheckRegisterStatusCommand
     */
    @GetMapping("/check-register-status/export")
    public ResponseEntity<byte[]> exportCheckRegister(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) String designerName,
            @RequestParam(required = false) String approvalStatus) {

        try {
            LoggerUtil.info(this.getClass(), "Exporting check register at " + getStandardCurrentDateTime());

            // Get current authenticated user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Determine target user
            User targetUser = determineTargetUser(currentUser, username);

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // REFACTORED: Use LoadUserCheckRegisterStatusCommand instead of StatusService
            LoadUserCheckRegisterStatusCommand command = new LoadUserCheckRegisterStatusCommand(
                    worktimeContext, targetUser.getUsername(), targetUser.getUserId(), selectedYear, selectedMonth,
                    searchTerm, startDate, endDate, checkType, designerName, approvalStatus);

            OperationResult result = command.execute();

            List<RegisterCheckEntry> entries = new ArrayList<>();
            if (result.isSuccess() && result.getData() instanceof LoadUserCheckRegisterStatusCommand.CheckRegisterStatusData statusData) {
                entries = statusData.getEntries();
            }

            // Use our exporter
            byte[] excelData = checkRegisterExcelExporter.exportToExcel(targetUser, entries, selectedYear, selectedMonth);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"check_register_%s_%d_%02d.xlsx\"",
                            targetUser.getUsername(), selectedYear, selectedMonth))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting check register: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

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
