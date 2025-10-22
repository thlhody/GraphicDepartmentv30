package com.ctgraphdep.controller.status;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepTypes;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserRegisterExcelExporter;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.ValidatePeriodCommand;
import com.ctgraphdep.worktime.commands.status.LoadUserRegisterStatusCommand;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.model.OperationResult;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RegisterSearchController - Handles register search and export functionality.
 * Allows users to search and export their register entries with various filters.
 *
 * Part of StatusController refactoring - separated from monolithic StatusController.
 */
@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class RegisterSearchController extends BaseController {

    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final WorktimeOperationContext worktimeContext;
    private final UserRegisterExcelExporter excelExporter;

    public RegisterSearchController(UserService userService,
                                   FolderStatus folderStatus,
                                   TimeValidationService timeValidationService,
                                   WorktimeOperationContext worktimeContext,
                                   UserRegisterExcelExporter excelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.worktimeContext = worktimeContext;
        this.excelExporter = excelExporter;
    }

    /**
     * Register search page with filtering capabilities
     * REFACTORED: Now uses LoadUserRegisterStatusCommand
     */
    @GetMapping("/register-search")
    public String registerSearch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String printPrepTypes,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String username,
            Model model, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing register search at " + getStandardCurrentDateTime());

            // Get the current user and determine the target user
            User currentUser = getUser(userDetails);
            User targetUser = determineTargetUser(currentUser, username);

            // Add user info to model
            model.addAttribute("user", targetUser);

            // Add reference data for dropdowns
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepTypes.getValues());

            // Use determineYear and determineMonth from BaseController
            int displayYear = determineYear(year);
            int displayMonth = determineMonth(month);

            // Validate period
            try {
                ValidatePeriodCommand validateCommand = getTimeValidationService().getValidationFactory()
                        .createValidatePeriodCommand(displayYear, displayMonth, 24);
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                String userMessage = "The selected period is not valid. You can only view periods up to 24 months in the future.";
                redirectAttributes.addFlashAttribute("periodError", userMessage);
                LocalDate currentDate = getStandardCurrentDate();
                return "redirect:/status/register-search?username=" + (username != null ? username : "") +
                        "&year=" + currentDate.getYear() + "&month=" + currentDate.getMonthValue();
            }

            // Set current period for the UI
            model.addAttribute("currentYear", displayYear);
            model.addAttribute("currentMonth", displayMonth);
            model.addAttribute("currentDate", getStandardCurrentDate());

            // REFACTORED: Use LoadUserRegisterStatusCommand instead of StatusService
            LoadUserRegisterStatusCommand command = new LoadUserRegisterStatusCommand(
                    worktimeContext, targetUser.getUsername(), targetUser.getUserId(),
                    displayYear, displayMonth, startDate, endDate, searchTerm, actionType, printPrepTypes, clientName);

            OperationResult result = command.execute();

            List<RegisterEntry> entries = new ArrayList<>();
            Set<String> clients = new HashSet<>();

            if (result.isSuccess() && result.getData() instanceof LoadUserRegisterStatusCommand.RegisterStatusData statusData) {
                entries = statusData.getEntries();
                clients = statusData.getUniqueClients();

                LoggerUtil.info(this.getClass(), String.format(
                        "Register search completed for user %s: found %d entries",
                        targetUser.getUsername(), entries.size()));
            } else {
                LoggerUtil.warn(this.getClass(), String.format(
                        "Register search failed for user %s: %s", targetUser.getUsername(), result.getMessage()));
            }

            // Add entries to model
            model.addAttribute("entries", entries);
            model.addAttribute("clients", clients);

            // Add system time
            model.addAttribute("currentSystemTime", formatCurrentDateTime());

            return "status/register-search";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in register search: " + e.getMessage(), e);
            model.addAttribute("errorMessage", "Error processing register search: " + e.getMessage());
            model.addAttribute("entries", new ArrayList<>());
            model.addAttribute("currentDate", getStandardCurrentDate());
            return "status/register-search";
        }
    }

    /**
     * Export register search results to Excel
     * REFACTORED: Now uses LoadUserRegisterStatusCommand
     */
    @GetMapping("/register-search/export")
    public ResponseEntity<byte[]> exportSearchResults(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String printPrepTypes,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String username) {

        try {
            LoggerUtil.info(this.getClass(), "Exporting register search results at " + getStandardCurrentDateTime());

            User currentUser = getUser(userDetails);
            User targetUser = determineTargetUser(currentUser, username);

            // Use determineYear and determineMonth methods from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // REFACTORED: Use LoadUserRegisterStatusCommand instead of StatusService
            LoadUserRegisterStatusCommand command = new LoadUserRegisterStatusCommand(
                    worktimeContext, targetUser.getUsername(), targetUser.getUserId(),
                    selectedYear, selectedMonth, startDate, endDate, searchTerm, actionType, printPrepTypes, clientName);

            OperationResult result = command.execute();

            List<RegisterEntry> filteredEntries = new ArrayList<>();
            if (result.isSuccess() && result.getData() instanceof LoadUserRegisterStatusCommand.RegisterStatusData statusData) {
                filteredEntries = statusData.getEntries();
            }

            // Generate Excel file
            byte[] excelData = excelExporter.exportToExcel(targetUser, filteredEntries, selectedYear, selectedMonth);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"register_search_results_%s.xlsx\"",
                            targetUser.getUsername()))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting search results: " + e.getMessage(), e);
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
