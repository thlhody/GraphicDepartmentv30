package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepTypes;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.TimeOffSummaryDTO;
import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserRegisterExcelExporter;
import com.ctgraphdep.utils.UserWorktimeExcelExporter;
import com.ctgraphdep.validation.TimeValidationService;
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

@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class StatusController extends BaseController {

    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final StatusService statusService;
    private final UserStatusDbService userStatusDbService;
    private final ThymeleafService thymeleafService;
    private final UserRegisterExcelExporter excelExporter;
    private final UserWorktimeExcelExporter userWorktimeExcelExporter;

    public StatusController(UserService userService,
                            FolderStatus folderStatus,
                            StatusService statusService,
                            UserStatusDbService userStatusDbService,
                            ThymeleafService thymeleafService,
                            UserRegisterExcelExporter excelExporter,
                            UserWorktimeExcelExporter userWorktimeExcelExporter,
                            TimeValidationService timeValidationService) {
        super(userService, folderStatus, timeValidationService);
        this.statusService = statusService;
        this.userStatusDbService = userStatusDbService;
        this.thymeleafService = thymeleafService;
        this.excelExporter = excelExporter;
        this.userWorktimeExcelExporter = userWorktimeExcelExporter;
    }

    @GetMapping
    public String getStatus(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing unified status page at " + getStandardCurrentDateTime());

        User currentUser = getUser(userDetails);

        // Determine dashboard URL based on user role
        String dashboardUrl = getDashboardUrl(currentUser);

        // Get filtered status list using the StatusService
        List<UserStatusDTO> userStatuses = statusService.getUserStatuses();
        long onlineCount = statusService.getUserStatusCount("online");

        // Add the flag for admin/team leader role check
        boolean hasAdminTeamLeaderRole = currentUser.hasRole("ADMIN") || currentUser.hasRole("TEAM_LEADER");

        // Add model attributes
        model.addAttribute("userStatuses", userStatuses);
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("onlineCount", onlineCount);
        model.addAttribute("isAdminView", currentUser.isAdmin());
        model.addAttribute("hasAdminTeamLeaderRole", hasAdminTeamLeaderRole);
        model.addAttribute("dashboardUrl", dashboardUrl);
        model.addAttribute("currentSystemTime", formatCurrentDateTime());

        return "status/status";
    }


    @GetMapping("/refresh")
    public String refreshStatus() {
        // Invalidate the cache to ensure fresh data
        userStatusDbService.invalidateCache();
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
            userStatusDbService.invalidateCache();

            User currentUser = getUser(userDetails);

            // Get fresh status list after invalidating cache
            List<UserStatusDTO> userStatuses = statusService.getUserStatuses();
            long onlineCount = statusService.getUserStatusCount("online");

            // Create a model to generate the HTML for the table body
            Model tableModel = new ConcurrentModel();
            tableModel.addAttribute("userStatuses", userStatuses);
            tableModel.addAttribute("currentUsername", currentUser.getUsername());
            tableModel.addAttribute("isAdminView", currentUser.isAdmin());

            // Add the flag for admin/team leader role check
            boolean hasAdminTeamLeaderRole = currentUser.hasRole("ADMIN") || currentUser.hasRole("TEAM_LEADER");
            tableModel.addAttribute("hasAdminTeamLeaderRole", hasAdminTeamLeaderRole);

            // Render the table body fragment using Thymeleaf
            String tableHtml = "";
            try {
                tableHtml = thymeleafService.processTemplate("status/fragments/status-table-body", tableModel);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error rendering status table fragment: " + e.getMessage(), e);
                // If rendering fails, we'll return an empty string for tableHtml
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
            Model model) {

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

            // Set current period for the UI
            model.addAttribute("currentYear", displayYear);
            model.addAttribute("currentMonth", displayMonth);
            model.addAttribute("currentDate", getStandardCurrentDate());

            // Load appropriate entries based on search parameters
            List<RegisterEntry> entries;
            boolean isSearching = hasSearchCriteria(searchTerm, startDate, endDate, actionType, printPrepTypes, clientName);

            if (isSearching) {
                // Load entries based on search criteria using StatusService
                entries = statusService.loadRegisterEntriesForSearch(
                        targetUser, searchTerm, startDate, endDate,
                        actionType, printPrepTypes, clientName,
                        year, month, displayYear, displayMonth);

                LoggerUtil.info(this.getClass(),
                        String.format("Register search completed for user %s: found %d entries",
                                targetUser.getUsername(), entries.size()));
            } else {
                // No search criteria - just load the current display period
                entries = statusService.loadRegisterEntriesForPeriod(targetUser, displayYear, displayMonth);

                LoggerUtil.info(this.getClass(),
                        String.format("Displaying register entries for user %s (%d/%d): %d entries",
                                targetUser.getUsername(), displayYear, displayMonth, entries.size()));
            }

            // Add entries to model
            model.addAttribute("entries", entries);

            // Extract unique clients for dropdown (from current period if not searching)
            List<RegisterEntry> clientSourceEntries = isSearching ? entries :
                    statusService.loadRegisterEntriesForPeriod(targetUser, displayYear, displayMonth);
            Set<String> clients = statusService.extractUniqueClients(clientSourceEntries);
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

            // Determine which user's register to export using the helper method
            User targetUser = username != null && !username.isEmpty() ? getUserService().getUserByUsername(username).orElseThrow(() -> new RuntimeException("User not found")) : currentUser;

            // Use determineYear and determineMonth methods from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Load all relevant entries
            List<RegisterEntry> allEntries = statusService.loadAllRelevantEntries(
                    targetUser,
                    selectedYear,
                    selectedMonth,
                    startDate,
                    endDate);

            // Apply filters using StatusService
            List<RegisterEntry> filteredEntries = statusService.filterRegisterEntries(
                    allEntries, searchTerm, startDate, endDate, actionType, printPrepTypes, clientName);

            // Generate Excel file
            byte[] excelData = excelExporter.exportToExcel(
                    targetUser,
                    filteredEntries,
                    selectedYear,
                    selectedMonth);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=\"register_search_results_%s.xlsx\"",
                                    targetUser.getUsername()))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting search results: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/timeoff-history")
    public String getTimeOffHistory(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            Model model,
            RedirectAttributes redirectAttributes) {

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

            // Get APPROVED time off entries from tracker converted to WorkTimeTable format
            List<WorkTimeTable> timeOffs = statusService.getApprovedTimeOffFromTracker(
                    user.getUsername(), user.getUserId(), selectedYear);

            // Also get the raw tracker using the StatusService method
            TimeOffTracker tracker = statusService.getTimeOffTrackerReadOnly(
                    user.getUsername(), user.getUserId(), selectedYear);

            // Calculate time off summary directly from tracker
            TimeOffSummaryDTO summary = statusService.getTimeOffSummaryFromTracker(
                    user.getUsername(), user.getUserId(), selectedYear);

            // Add data to model
            model.addAttribute("user", user);
            model.addAttribute("timeOffs", timeOffs);
            model.addAttribute("tracker", tracker); // Optional, for debugging
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

    @GetMapping("/worktime-status")
    public String getWorktimeStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing worktime status at " + getStandardCurrentDateTime());

            // Get current authenticated user
            User currentUser = getUser(userDetails);

            // Determine target user (user being viewed)
            User targetUser = username != null && !username.isEmpty() ?
                    getUserService().getUserByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found")) :
                    currentUser;

            // Use determineYear and determineMonth from BaseController
            int currentYear = determineYear(year);
            int currentMonth = determineMonth(month);

            // For other users' data, only admins and team leaders can view
            boolean canViewOtherUser = !targetUser.getUsername().equals(currentUser.getUsername()) &&
                    !currentUser.hasRole("ADMIN") && !currentUser.hasRole("TEAM_LEADER");

            if (canViewOtherUser) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "You don't have permission to view other users' worktime data");
                return "redirect:/status";
            }

            // Load work time data using StatusService (read-only operation)
            List<WorkTimeTable> worktimeData = statusService.loadViewOnlyWorktime(
                    targetUser.getUsername(), targetUser.getUserId(), currentYear, currentMonth);

            // Prepare display data using StatusService (now returns DTOs)
            Map<String, Object> displayData = statusService.prepareWorktimeDisplayData(
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

            // Determine target user using simplified logic
            User targetUser = username != null && !username.isEmpty() ?
                    getUserService().getUserByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found")) :
                    currentUser;

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Load worktime data using StatusService
            List<WorkTimeTable> worktimeData = statusService.loadViewOnlyWorktime(
                    targetUser.getUsername(), targetUser.getUserId(), selectedYear, selectedMonth);

            // Get display data which includes the summary
            Map<String, Object> displayData = statusService.prepareWorktimeDisplayData(
                    targetUser, worktimeData, selectedYear, selectedMonth);

            // Extract the DTOs from display data
            @SuppressWarnings("unchecked")
            List<WorkTimeEntryDTO> entryDTOs = (List<WorkTimeEntryDTO>) displayData.get("worktimeData");
            WorkTimeSummaryDTO summaryDTO = (WorkTimeSummaryDTO) displayData.get("summary");

            // Use the updated exporter to generate Excel data with DTOs
            byte[] excelData = userWorktimeExcelExporter.exportToExcel(
                    targetUser, entryDTOs, summaryDTO, selectedYear, selectedMonth);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=\"worktime_%s_%d_%02d.xlsx\"",
                                    targetUser.getUsername(), selectedYear, selectedMonth))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // Formats the current datetime using the standard pattern
    private String formatCurrentDateTime() {
        return getStandardCurrentDateTime().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT_PATTERN));
    }

    // Determines the appropriate dashboard URL based on user role
    private String getDashboardUrl(User user) {
        if (user.hasRole("TEAM_LEADER")) {
            return "/team-lead";
        } else if (user.hasRole("ADMIN")) {
            return "/admin";
        } else {
            return "/user";
        }
    }

    // Helper method to determine target user based on permissions
    private User determineTargetUser(User currentUser, String requestedUsername) {
        if (requestedUsername == null || requestedUsername.isEmpty()) {
            return currentUser;
        }

        // Anyone can view register search for any user
        return getUserService().getUserByUsername(requestedUsername).orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Helper to check if any search criteria are present
    private boolean hasSearchCriteria(String searchTerm, LocalDate startDate, LocalDate endDate,
                                      String actionType, String printPrepTypes, String clientName) {
        return (searchTerm != null && !searchTerm.trim().isEmpty()) ||
                startDate != null || endDate != null ||
                (actionType != null && !actionType.trim().isEmpty()) ||
                (printPrepTypes != null && !printPrepTypes.trim().isEmpty()) ||
                (clientName != null && !clientName.trim().isEmpty());
    }
}