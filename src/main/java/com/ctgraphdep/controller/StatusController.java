package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepType;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.TimeOffSummary;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.UserStatusDTO;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.OnlineMetricsService;
import com.ctgraphdep.service.UserRegisterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.UserTimeOffService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserRegisterExcelExporter;
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
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class StatusController extends BaseController {
    private final OnlineMetricsService onlineMetricsService;
    private final UserTimeOffService userTimeOffService;
    private final UserRegisterService userRegisterService;
    private final UserRegisterExcelExporter excelExporter;

    public StatusController(
            UserService userService,
            FolderStatusService folderStatusService,
            OnlineMetricsService onlineMetricsService,
            UserTimeOffService userTimeOffService,
            UserRegisterService userRegisterService,
            UserRegisterExcelExporter excelExporter) {
        super(userService, folderStatusService);
        this.onlineMetricsService = onlineMetricsService;
        this.userTimeOffService = userTimeOffService;
        this.userRegisterService = userRegisterService;
        this.excelExporter = excelExporter;
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String getStatus(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing unified status page");

        User currentUser = getUser(userDetails);

        // Determine dashboard URL based on user role
        String dashboardUrl = currentUser.hasRole("TEAM_LEADER") ? "/team-lead" :
                currentUser.hasRole("ADMIN") ? "/admin" : "/user";

        LoggerUtil.debug(this.getClass(), String.format("Determined Dashboard URL for %s: %s", currentUser.getUsername(), dashboardUrl));

        // Get filtered status list for non-admin users
        List<UserStatusDTO> userStatuses = onlineMetricsService.getUserStatuses();
        long onlineCount = userStatuses.stream().filter(status -> "Online".equals(status.getStatus())).count();

        // Add model attributes
        model.addAttribute("userStatuses", userStatuses);
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("onlineCount", onlineCount);
        model.addAttribute("isAdminView", currentUser.isAdmin());
        model.addAttribute("dashboardUrl", dashboardUrl);

        return "status/status";
    }

    @GetMapping("/refresh")
    public String refreshStatus() {
        return "redirect:/status";
    }

    @GetMapping("/register-search")
    public String registerSearch(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String printPrepType,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String username,
            Model model) {

        try {
            User currentUser = getUser(userDetails);
            User targetUser;

            // Determine which user's register to search
            if (username != null && !username.isEmpty()) {
                // For admin/team leader - can search any user's register
                if (currentUser.hasRole("ADMIN") || currentUser.hasRole("TEAM_LEADER")) {
                    targetUser = getUserService().getUserByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                } else {
                    // Regular users can only search their own register
                    if (!username.equals(currentUser.getUsername())) {
                        return "redirect:/status/register-search";
                    }
                    targetUser = currentUser;
                }
            } else {
                // Default to current user
                targetUser = currentUser;
            }

            // Add user info to model
            model.addAttribute("user", targetUser);

            // Add available action types and print prep types to model
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepType.getValues());

            // Don't search if no search parameters provided
            if (isSearchRequest(searchTerm, startDate, endDate, actionType, printPrepType, clientName, year, month)) {
                // Load all relevant entries first
                List<RegisterEntry> allEntries = loadAllRelevantEntries(targetUser, year, month, startDate, endDate);

                // Apply filters
                List<RegisterEntry> filteredEntries = filterEntries(allEntries, searchTerm, startDate, endDate,
                        actionType, printPrepType, clientName);

                // Add results to model
                model.addAttribute("entries", filteredEntries);

                // Add unique client names for dropdown
                Set<String> clients = extractUniqueClients(allEntries);
                model.addAttribute("clients", clients);

                LoggerUtil.info(this.getClass(),
                        String.format("Register search completed for user %s: found %d entries",
                                targetUser.getUsername(), filteredEntries.size()));
            } else {
                // Load a small set of entries to extract client names for dropdown
                List<RegisterEntry> recentEntries = loadRecentEntries(targetUser);
                Set<String> clients = extractUniqueClients(recentEntries);
                model.addAttribute("clients", clients);

                LoggerUtil.info(this.getClass(),
                        String.format("Register search page accessed by user %s", targetUser.getUsername()));
            }

            return "status/register-search";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in register search: " + e.getMessage(), e);
            model.addAttribute("errorMessage", "Error processing register search: " + e.getMessage());
            return "status/register-search";
        }
    }

    @GetMapping("/register-search/export")
    public ResponseEntity<byte[]> exportSearchResults(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String searchTerm,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String printPrepType,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String username) {

        try {
            User currentUser = getUser(userDetails);
            User targetUser;

            // Determine which user's register to export
            if (username != null && !username.isEmpty()) {
                if (currentUser.hasRole("ADMIN") || currentUser.hasRole("TEAM_LEADER")) {
                    targetUser = getUserService().getUserByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                } else {
                    if (!username.equals(currentUser.getUsername())) {
                        return ResponseEntity.badRequest().build();
                    }
                    targetUser = currentUser;
                }
            } else {
                targetUser = currentUser;
            }

            // Load entries
            List<RegisterEntry> allEntries = loadAllRelevantEntries(targetUser, year, month, startDate, endDate);

            // Apply filters
            List<RegisterEntry> filteredEntries = filterEntries(allEntries, searchTerm, startDate, endDate,
                    actionType, printPrepType, clientName);

            // Generate Excel file
            byte[] excelData = excelExporter.exportToExcel(targetUser, filteredEntries,
                    year != null ? year : LocalDate.now().getYear(),
                    month != null ? month : LocalDate.now().getMonthValue());

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
            // We need either userId or username
            if (userId == null && (username == null || username.isEmpty())) {
                redirectAttributes.addFlashAttribute("errorMessage", "User information is required");
                return "redirect:/status";
            }

            // Set default year if not provided
            int currentYear = Year.now().getValue();
            year = year != null ? year : currentYear;

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

            // Get time off records
            List<WorkTimeTable> timeOffs = userTimeOffService.getUserTimeOffHistory(user.getUsername(), year);

            // Calculate time off summary
            TimeOffSummary summary = userTimeOffService.calculateTimeOffSummary(user.getUsername(), year);

            // Add data to model
            model.addAttribute("user", user);
            model.addAttribute("timeOffs", timeOffs);
            model.addAttribute("summary", summary);
            model.addAttribute("year", year);

            return "status/timeoff-history";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error viewing time off history: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading time off history");
            return "redirect:/status";
        }
    }

    // Helper methods

    private boolean isSearchRequest(String searchTerm, LocalDate startDate, LocalDate endDate,
                                    String actionType, String printPrepType, String clientName,
                                    Integer year, Integer month) {
        return searchTerm != null || startDate != null || endDate != null ||
                actionType != null || printPrepType != null || clientName != null ||
                year != null || month != null;
    }

    private List<RegisterEntry> loadAllRelevantEntries(User user, Integer year, Integer month,
                                                       LocalDate startDate, LocalDate endDate) {
        List<RegisterEntry> allEntries = new ArrayList<>();

        // If specific year and month provided
        if (year != null && month != null) {
            List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(
                    user.getUsername(), user.getUserId(), year, month);
            if (monthEntries != null) {
                allEntries.addAll(monthEntries);
            }
            return allEntries;
        }

        // If date range provided, load all months in range
        if (startDate != null && endDate != null) {
            YearMonth start = YearMonth.from(startDate);
            YearMonth end = YearMonth.from(endDate);

            YearMonth current = start;
            while (!current.isAfter(end)) {
                try {
                    List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(
                            user.getUsername(), user.getUserId(), current.getYear(), current.getMonthValue());
                    if (monthEntries != null) {
                        allEntries.addAll(monthEntries);
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Error loading entries for %s - %d/%d: %s",
                                    user.getUsername(), current.getYear(), current.getMonthValue(), e.getMessage()));
                }
                current = current.plusMonths(1);
            }
            return allEntries;
        }

        // If only start date provided
        if (startDate != null) {
            YearMonth start = YearMonth.from(startDate);
            YearMonth now = YearMonth.now();

            YearMonth current = start;
            while (!current.isAfter(now)) {
                try {
                    List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(
                            user.getUsername(), user.getUserId(), current.getYear(), current.getMonthValue());
                    if (monthEntries != null) {
                        allEntries.addAll(monthEntries);
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Error loading entries for %s - %d/%d: %s",
                                    user.getUsername(), current.getYear(), current.getMonthValue(), e.getMessage()));
                }
                current = current.plusMonths(1);
            }
            return allEntries;
        }

        // If only year provided, load all months for that year
        if (year != null) {
            for (int m = 1; m <= 12; m++) {
                try {
                    List<RegisterEntry> monthEntries = userRegisterService.loadMonthEntries(
                            user.getUsername(), user.getUserId(), year, m);
                    if (monthEntries != null) {
                        allEntries.addAll(monthEntries);
                    }
                } catch (Exception e) {
                    LoggerUtil.warn(this.getClass(),
                            String.format("Error loading entries for %s - %d/%d: %s",
                                    user.getUsername(), year, m, e.getMessage()));
                }
            }
            return allEntries;
        }

        // Default: load current month and previous month
        LocalDate now = LocalDate.now();
        try {
            List<RegisterEntry> currentMonthEntries = userRegisterService.loadMonthEntries(
                    user.getUsername(), user.getUserId(), now.getYear(), now.getMonthValue());
            if (currentMonthEntries != null) {
                allEntries.addAll(currentMonthEntries);
            }

            // Previous month
            LocalDate prevMonth = now.minusMonths(1);
            List<RegisterEntry> prevMonthEntries = userRegisterService.loadMonthEntries(
                    user.getUsername(), user.getUserId(), prevMonth.getYear(), prevMonth.getMonthValue());
            if (prevMonthEntries != null) {
                allEntries.addAll(prevMonthEntries);
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    String.format("Error loading recent entries for %s: %s",
                            user.getUsername(), e.getMessage()));
        }

        return allEntries;
    }

    private List<RegisterEntry> loadRecentEntries(User user) {
        List<RegisterEntry> recentEntries = new ArrayList<>();
        LocalDate now = LocalDate.now();

        try {
            // Current month
            List<RegisterEntry> currentMonthEntries = userRegisterService.loadMonthEntries(
                    user.getUsername(), user.getUserId(), now.getYear(), now.getMonthValue());
            if (currentMonthEntries != null) {
                recentEntries.addAll(currentMonthEntries);
            }

            // Previous month
            LocalDate prevMonth = now.minusMonths(1);
            List<RegisterEntry> prevMonthEntries = userRegisterService.loadMonthEntries(
                    user.getUsername(), user.getUserId(), prevMonth.getYear(), prevMonth.getMonthValue());
            if (prevMonthEntries != null) {
                recentEntries.addAll(prevMonthEntries);
            }
        } catch (Exception e) {
            LoggerUtil.warn(this.getClass(),
                    String.format("Error loading recent entries for %s: %s",
                            user.getUsername(), e.getMessage()));
        }

        return recentEntries;
    }

    private List<RegisterEntry> filterEntries(List<RegisterEntry> entries,
                                              String searchTerm,
                                              LocalDate startDate,
                                              LocalDate endDate,
                                              String actionType,
                                              String printPrepType,
                                              String clientName) {
        List<RegisterEntry> filteredEntries = new ArrayList<>(entries);

        // Filter by search term (search across multiple fields)
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String term = searchTerm.toLowerCase();
            filteredEntries = filteredEntries.stream()
                    .filter(entry ->
                            (entry.getOrderId() != null && entry.getOrderId().toLowerCase().contains(term)) ||
                                    (entry.getProductionId() != null && entry.getProductionId().toLowerCase().contains(term)) ||
                                    (entry.getOmsId() != null && entry.getOmsId().toLowerCase().contains(term)) ||
                                    (entry.getClientName() != null && entry.getClientName().toLowerCase().contains(term)) ||
                                    (entry.getActionType() != null && entry.getActionType().toLowerCase().contains(term)) ||
                                    (entry.getObservations() != null && entry.getObservations().toLowerCase().contains(term))
                    )
                    .collect(Collectors.toList());
        }

        // Filter by date range
        if (startDate != null) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> !entry.getDate().isBefore(startDate))
                    .collect(Collectors.toList());
        }

        if (endDate != null) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> !entry.getDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Filter by action type
        if (actionType != null && !actionType.isEmpty()) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> actionType.equals(entry.getActionType()))
                    .collect(Collectors.toList());
        }

        // Filter by print prep type
        if (printPrepType != null && !printPrepType.isEmpty()) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> entry.getPrintPrepTypes() != null &&
                            entry.getPrintPrepTypes().contains(printPrepType))
                    .collect(Collectors.toList());
        }

        // Filter by client name
        if (clientName != null && !clientName.isEmpty()) {
            filteredEntries = filteredEntries.stream()
                    .filter(entry -> clientName.equals(entry.getClientName()))
                    .collect(Collectors.toList());
        }

        return filteredEntries;
    }

    private Set<String> extractUniqueClients(List<RegisterEntry> entries) {
        return entries.stream()
                .map(RegisterEntry::getClientName)
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }
}