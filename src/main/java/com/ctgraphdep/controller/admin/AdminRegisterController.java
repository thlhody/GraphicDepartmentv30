package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.SyncStatusWorktime;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.bonus.BonusCalculationResultDTO;
import com.ctgraphdep.model.dto.RegisterSummaryDTO;
import com.ctgraphdep.service.*;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepTypes;
import com.ctgraphdep.utils.AdminRegisterExcelExporter;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/register")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminRegisterController extends BaseController {

    private final AdminRegisterService adminRegisterService;
    private final WorktimeManagementService worktimeManagementService;
    private final AdminRegisterExcelExporter adminRegisterExcelExporter;

    @Autowired
    public AdminRegisterController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            AdminRegisterService adminRegisterService,
            WorktimeManagementService worktimeManagementService,
            AdminRegisterExcelExporter adminRegisterExcelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.adminRegisterService = adminRegisterService;
        this.worktimeManagementService = worktimeManagementService;
        this.adminRegisterExcelExporter = adminRegisterExcelExporter;
    }

    @GetMapping
    public String getRegisterPage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        try {
            // Use checkUserAccess from BaseController for consistent access control
            String accessCheck = checkUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
            if (accessCheck != null) {
                return accessCheck;
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Add users list for dropdown
            List<User> allUsers = getUserService().getAllUsers();
            List<User> users = getUserService().getNonAdminUsers(allUsers);
            model.addAttribute("users", users);

            // Add action types and print prep types for filters
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepTypes.getValues());

            // If userId is provided, look up user and load entries
            if (userId != null) {
                User selectedUser = getUserService().getUserById(userId).orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

                List<RegisterEntry> entries = adminRegisterService.loadUserRegisterEntries(selectedUser.getUsername(), userId, selectedYear, selectedMonth);

                // Get worked days from workTimeManagementService
                int workedDays = worktimeManagementService.getWorkedDays(userId, selectedYear, selectedMonth);

                // Add to model
                model.addAttribute("entries", entries);
                model.addAttribute("selectedUser", selectedUser);
                model.addAttribute("workedDays", workedDays);

                LoggerUtil.info(this.getClass(), String.format("Loaded %d entries and %d worked days for user %s", entries.size(), workedDays, selectedUser.getUsername()));
            } else {
                LoggerUtil.info(this.getClass(), "No user selected, adding empty entries list");
                model.addAttribute("entries", new ArrayList<>());
            }

            // Add period info - use the selected values here
            model.addAttribute("currentYear", selectedYear);
            model.addAttribute("currentMonth", selectedMonth);

            // Add bonus configuration defaults
            model.addAttribute("bonusConfig", BonusConfiguration.getDefaultConfig());

            return "admin/register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading register page: " + e.getMessage(), e);
            model.addAttribute("error", "Error loading register data: " + e.getMessage());
            return "admin/register";
        }
    }

    @GetMapping("/worked-days")
    public ResponseEntity<Integer> getWorkedDays(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month) {

        // Use validateUserAccess for REST controllers
        User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        int workedDays = worktimeManagementService.getWorkedDays(userId, year, month);
        return ResponseEntity.ok(workedDays);
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveEntries(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {

        // Use validateUserAccess for REST controllers
        User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Admin access required");
        }

        try {
            String username = (String) request.get("username");
            Integer userId = Integer.parseInt(request.get("userId").toString());
            Integer year = Integer.parseInt(request.get("year").toString());
            Integer month = Integer.parseInt(request.get("month").toString());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;

            List<RegisterEntry> entries = entriesData.stream()
                    .map(data -> {
                        LoggerUtil.debug(this.getClass(), "Processing entry ID: " + data.get("entryId") +
                                ", printPrepTypes: " + data.get("printPrepTypes") +
                                ", adminSync: " + data.get("adminSync"));

                        // Handle printPrepTypes conversion
                        List<String> printPrepTypes = new ArrayList<>();
                        Object printPrepTypesObj = data.get("printPrepTypes");

                        if (printPrepTypesObj instanceof List<?> typesList) {
                            // Handle list case
                            typesList.forEach(type -> {
                                if (type != null && !type.toString().equalsIgnoreCase("null") && !type.toString().isEmpty()) {
                                    printPrepTypes.add(type.toString().trim());
                                }
                            });
                        } else if (printPrepTypesObj instanceof String typesStr) {
                            // Handle string case
                            if (!typesStr.isEmpty()) {
                                Arrays.stream(typesStr.split("\\s*,\\s*"))
                                        .filter(type -> !type.equalsIgnoreCase("null") && !type.isEmpty())
                                        .forEach(type -> printPrepTypes.add(type.trim()));
                            }
                        }

                        // If no valid types were found, add default
                        if (printPrepTypes.isEmpty()) {
                            printPrepTypes.add("DIGITAL");
                        }

                        // IMPORTANT: Preserve the original adminSync status from the client
                        String adminSync = data.get("adminSync") != null ?
                                data.get("adminSync").toString() : SyncStatusWorktime.USER_DONE.name();

                        return RegisterEntry.builder()
                                .entryId(Integer.parseInt(data.get("entryId").toString()))
                                .userId(Integer.parseInt(data.get("userId").toString()))
                                .date(LocalDate.parse(data.get("date").toString(), formatter))
                                .orderId(data.get("orderId").toString())
                                .productionId(data.get("productionId").toString())
                                .omsId(data.get("omsId").toString())
                                .clientName(data.get("clientName").toString())
                                .actionType(data.get("actionType").toString())
                                .printPrepTypes(printPrepTypes)
                                .colorsProfile(data.get("colorsProfile").toString())
                                .articleNumbers(Integer.parseInt(data.get("articleNumbers").toString()))
                                .graphicComplexity(Double.parseDouble(data.get("graphicComplexity").toString()))
                                .observations(data.get("observations") != null ? data.get("observations").toString() : "")
                                .adminSync(adminSync) // Use the status from the client
                                .build();
                    })
                    .collect(Collectors.toList());

            LoggerUtil.info(this.getClass(),
                    String.format("Saving %d entries for user %s (year: %d, month: %d)",
                            entries.size(), username, year, month));

            adminRegisterService.saveAdminRegisterEntries(username, userId, year, month, entries);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving entries: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error saving entries: " + e.getMessage());
        }
    }

    @PostMapping("/calculate-bonus")
    public ResponseEntity<BonusCalculationResultDTO> calculateBonus(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {

        // Use validateUserAccess for REST controllers
        User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            BonusCalculationResultDTO result = adminRegisterService.calculateBonusFromRequest(request);

            // Get required parameters
            Integer userId = (Integer) request.get("userId");
            Integer year = (Integer) request.get("year");
            Integer month = (Integer) request.get("month");

            // Get username from UserService
            String username = getUserService().getUserById(userId)
                    .map(User::getUsername)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Save bonus result with username
            adminRegisterService.saveBonusResult(userId, year, month, result, username);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating bonus: " + e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<RegisterSummaryDTO> getRegisterSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String username,
            @RequestParam Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month) {

        // Use validateUserAccess for REST controllers
        User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<RegisterEntry> entries = adminRegisterService.loadUserRegisterEntries(
                username, userId, year, month);
        RegisterSummaryDTO summary = adminRegisterService.calculateRegisterSummary(entries);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month) {

        // Use validateUserAccess for REST controllers
        User currentUser = validateUserAccess(userDetails, SecurityConstants.ROLE_ADMIN);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            // Get user details
            User user = getUserService().getUserById(userId).orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

            // Load register entries
            List<RegisterEntry> entries = adminRegisterService.loadUserRegisterEntries(user.getUsername(), userId, year, month);

            // Get bonus configuration and calculation result if exists
            BonusConfiguration bonusConfig = BonusConfiguration.getDefaultConfig();
            BonusCalculationResultDTO bonusResult = null;

            try {
                // Try to load saved bonus result
                bonusResult = adminRegisterService.loadSavedBonusResult(userId, year, month);
            } catch (Exception e) {
                LoggerUtil.info(this.getClass(), "No saved bonus result found for user " + userId);
            }

            // Generate Excel file
            byte[] excelBytes = adminRegisterExcelExporter.exportToExcel(user, entries, bonusConfig, bonusResult, year, month);

            // Set up response headers
            String filename = String.format("register_report_%s_%d_%02d.xlsx", user.getUsername(), year, month);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename(filename, StandardCharsets.UTF_8)
                    .build());

            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting Excel: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}