package com.ctgraphdep.controller;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.ApprovalStatusType;
import com.ctgraphdep.enums.CheckType;
import com.ctgraphdep.enums.PrintPrepTypes;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.*;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.ValidatePeriodCommand;
import com.ctgraphdep.worktime.commands.status.*;
import com.ctgraphdep.worktime.context.WorktimeOperationContext;
import com.ctgraphdep.worktime.display.WorktimeDisplayService;
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
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REFACTORED StatusController - No longer uses StatusService.
 * Now uses commands directly through WorktimeOperationContext.
 * Implements local → network → empty fallback strategy for all data access.
 * Complete replacement of StatusService functionality.
 */
@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class StatusController extends BaseController {

    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final WorktimeOperationContext worktimeContext;
    private final ReadFileNameStatusService readFileNameStatusService;
    private final ThymeleafService thymeleafService;
    private final WorktimeDisplayService worktimeDisplayService;
    private final UserRegisterExcelExporter excelExporter;
    private final UserWorktimeExcelExporter userWorktimeExcelExporter;
    private final CheckRegisterStatusExcelExporter checkRegisterExcelExporter;

    public StatusController(UserService userService,
                            FolderStatus folderStatus,
                            WorktimeOperationContext worktimeContext,
                            ReadFileNameStatusService readFileNameStatusService,
                            ThymeleafService thymeleafService,
                            TimeValidationService timeValidationService,
                            WorktimeDisplayService worktimeDisplayService,
                            UserRegisterExcelExporter excelExporter,
                            UserWorktimeExcelExporter userWorktimeExcelExporter,
                            CheckRegisterStatusExcelExporter checkRegisterExcelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.worktimeContext = worktimeContext;
        this.readFileNameStatusService = readFileNameStatusService;
        this.thymeleafService = thymeleafService;
        this.worktimeDisplayService = worktimeDisplayService;
        this.excelExporter = excelExporter;
        this.userWorktimeExcelExporter = userWorktimeExcelExporter;
        this.checkRegisterExcelExporter = checkRegisterExcelExporter;
    }

    @GetMapping
    public String getStatus(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing unified status page at " + getStandardCurrentDateTime());

        // Use prepareUserAndCommonModelAttributes to set up common attributes and get current user
        User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Get statuses - roles are already included from the cache
        List<UserStatusDTO> userStatuses = readFileNameStatusService.getAllUserStatuses();
        long onlineCount = readFileNameStatusService.getOnlineUserCount();

        // Add model attributes
        model.addAttribute("userStatuses", userStatuses);
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("onlineCount", onlineCount);
        model.addAttribute("isAdminView", currentUser.isAdmin());
        model.addAttribute("hasAdminTeamLeaderRole",
                currentUser.hasRole(SecurityConstants.ROLE_ADMIN) ||
                        currentUser.hasRole(SecurityConstants.ROLE_TEAM_LEADER) ||
                        currentUser.hasRole(SecurityConstants.ROLE_TL_CHECKING));

        return "status/status";
    }

    @GetMapping("/refresh")
    public String refreshStatus() {
        // Invalidate the cache to ensure fresh data
        readFileNameStatusService.invalidateCache();
        LoggerUtil.info(this.getClass(), "Status cache invalidated via manual refresh at " + getStandardCurrentDateTime());
        return "redirect:/status";
    }

    @GetMapping("/ajax-refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> ajaxRefreshStatus(@AuthenticationPrincipal UserDetails userDetails) {
        LocalDateTime currentTime = getStandardCurrentDateTime();
        LoggerUtil.info(this.getClass(), "Processing AJAX status refresh request at " + currentTime);

        try {
            // Force invalidation of the status cache
            readFileNameStatusService.invalidateCache();

            User currentUser = getUser(userDetails);

            // Get fresh status list after invalidating cache
            var userStatuses = readFileNameStatusService.getAllUserStatuses();
            long onlineCount = readFileNameStatusService.getOnlineUserCount();

            // Create a model to generate the HTML for the table body
            Model tableModel = new ConcurrentModel();
            tableModel.addAttribute("userStatuses", userStatuses);
            tableModel.addAttribute("currentUsername", currentUser.getUsername());
            tableModel.addAttribute("isAdminView", currentUser.isAdmin());

            // Add the flag for admin/team leader role check
            boolean hasAdminTeamLeaderRole = currentUser.hasRole(SecurityConstants.ROLE_ADMIN) ||
                    currentUser.hasRole(SecurityConstants.ROLE_TEAM_LEADER) ||
                    currentUser.hasRole(SecurityConstants.ROLE_TL_CHECKING);
            tableModel.addAttribute("hasAdminTeamLeaderRole", hasAdminTeamLeaderRole);

            // Render the table body fragment using Thymeleaf
            String tableHtml = "";
            try {
                tableHtml = thymeleafService.processTemplate("status/fragments/status-table-body", tableModel);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error rendering status table fragment: " + e.getMessage(), e);
            }

            // Prepare response data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("onlineCount", onlineCount);
            responseData.put("tableHtml", tableHtml);
            responseData.put("timestamp", currentTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing AJAX status refresh: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("error", "Failed to refresh status data")
            );
        }
    }

    /**
     * REFACTORED: Register search now uses LoadUserRegisterStatusCommand
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
     * REFACTORED: Register export now uses LoadUserRegisterStatusCommand
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

    /**
     * REFACTORED: Time off history now uses LoadUserTimeOffStatusCommand
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

    /**
     * REFACTORED: Worktime status now uses LoadUserWorktimeStatusCommand
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
                ValidatePeriodCommand validateCommand = getTimeValidationService().getValidationFactory()
                        .createValidatePeriodCommand(currentYear, currentMonth, 24);
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
                    !currentUser.hasRole(SecurityConstants.ROLE_TEAM_LEADER);

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
     * REFACTORED: Worktime export now uses LoadUserWorktimeStatusCommand
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

    /**
     * FIXED: Check register now uses LoadUserCheckRegisterStatusCommand
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
     * REFACTORED: Check register export now uses LoadUserCheckRegisterStatusCommand
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
    // HELPER METHODS (UNCHANGED)
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