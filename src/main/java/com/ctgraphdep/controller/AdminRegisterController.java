package com.ctgraphdep.controller;

import com.ctgraphdep.model.*;
import com.ctgraphdep.service.AdminRegisterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepType;
import com.ctgraphdep.service.WorkTimeManagementService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/register")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRegisterController {

    private final AdminRegisterService adminRegisterService;
    private final UserService userService;
    private final WorkTimeManagementService workTimeManagementService;

    @Autowired
    public AdminRegisterController(AdminRegisterService adminRegisterService,
                                   UserService userService, WorkTimeManagementService workTimeManagementService) {
        this.adminRegisterService = adminRegisterService;
        this.userService = userService;
        this.workTimeManagementService = workTimeManagementService;
        LoggerUtil.initialize(this.getClass(), "Initializing Admin Register Controller");
    }

    @GetMapping
    public String getRegisterPage(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        try {
            // Set default year and month if not provided
            LocalDate now = LocalDate.now();
            year = year != null ? year : now.getYear();
            month = month != null ? month : now.getMonthValue();

            // Add users list for dropdown
            List<User> allUsers = userService.getAllUsers();
            List<User> users = userService.getNonAdminUsers(allUsers);
            model.addAttribute("users", users);

            // Add action types and print prep types for filters
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepType.getValues());

            // If userId is provided, look up user and load entries
            if (userId != null) {
                User selectedUser = userService.getUserById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));

                List<RegisterEntry> entries = adminRegisterService.loadUserRegisterEntries(
                        selectedUser.getUsername(), userId, year, month);

                // Get worked days from workTimeManagementService
                int workedDays = workTimeManagementService.getWorkedDays(userId, year, month);

                // Add to model
                model.addAttribute("entries", entries);
                model.addAttribute("selectedUser", selectedUser);
                model.addAttribute("workedDays", workedDays);  // Add worked days

                LoggerUtil.info(this.getClass(),
                        String.format("Loaded %d entries and %d worked days for user %s",
                                entries.size(), workedDays, selectedUser.getUsername()));
            } else {
                LoggerUtil.info(this.getClass(), "No user selected, adding empty entries list");
                model.addAttribute("entries", new ArrayList<>());
            }

            // Add period info
            model.addAttribute("currentYear", year);
            model.addAttribute("currentMonth", month);

            // Add bonus configuration defaults
            model.addAttribute("bonusConfig", BonusConfiguration.getDefaultConfig());

            return "admin/register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error loading register page: " + e.getMessage(), e);
            model.addAttribute("error", "Error loading register data: " + e.getMessage());
            return "admin/register";
        }
    }

    @GetMapping("/worked-days")
    public ResponseEntity<Integer> getWorkedDays(
            @RequestParam Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month) {
        int workedDays = workTimeManagementService.getWorkedDays(userId, year, month);
        return ResponseEntity.ok(workedDays);
    }

    @PostMapping("/filter")
    public ResponseEntity<List<RegisterEntry>> filterEntries(
            @RequestBody List<RegisterEntry> entries,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String printPrepType) {

        ActionType selectedActionType = actionType != null ?
                ActionType.valueOf(actionType) : null;
        PrintPrepType selectedPrintType = printPrepType != null ?
                PrintPrepType.valueOf(printPrepType) : null;

        List<RegisterEntry> filteredEntries = adminRegisterService.filterEntries(
                entries, selectedActionType, selectedPrintType);

        return ResponseEntity.ok(filteredEntries);
    }

    @PostMapping("/search")
    public ResponseEntity<List<RegisterEntry>> searchEntries(
            @RequestBody List<RegisterEntry> entries,
            @RequestParam String searchTerm) {

        List<RegisterEntry> searchResults = adminRegisterService.searchEntries(
                entries, searchTerm);

        return ResponseEntity.ok(searchResults);
    }

    @PostMapping("/update")
    public ResponseEntity<?> bulkUpdateEntries(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");
            @SuppressWarnings("unchecked")
            List<Integer> selectedIds = (List<Integer>) request.get("selectedIds");
            Double newValue = Double.parseDouble(request.get("newValue").toString());

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;

            List<RegisterEntry> entries = entriesData.stream()
                    .map(data -> RegisterEntry.builder()
                            .entryId(Integer.parseInt(data.get("entryId").toString()))
                            .userId(Integer.parseInt(data.get("userId").toString()))
                            .date(LocalDate.parse(data.get("date").toString(), formatter))  // Parse ISO format date
                            .orderId(data.get("orderId").toString())
                            .productionId(data.get("productionId").toString())
                            .omsId(data.get("omsId").toString())
                            .clientName(data.get("clientName").toString())
                            .actionType(data.get("actionType").toString())
                            .printPrepType(data.get("printPrepType").toString())
                            .colorsProfile(data.get("colorsProfile").toString())
                            .articleNumbers(Integer.parseInt(data.get("articleNumbers").toString()))
                            .graphicComplexity(Double.parseDouble(data.get("graphicComplexity").toString()))
                            .observations(data.get("observations") != null ? data.get("observations").toString() : "")
                            .adminSync("ADMIN_EDITED")
                            .build())
                    .collect(Collectors.toList());

            adminRegisterService.bulkUpdateEntries(entries, selectedIds, "graphicComplexity", newValue.toString());
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating entries: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error updating entries: " + e.getMessage());
        }
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveEntries(@RequestBody Map<String, Object> request) {
        try {
            String username = (String) request.get("username");
            Integer userId = Integer.parseInt(request.get("userId").toString());
            Integer year = Integer.parseInt(request.get("year").toString());
            Integer month = Integer.parseInt(request.get("month").toString());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) request.get("entries");

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;

            List<RegisterEntry> entries = entriesData.stream()
                    .map(data -> RegisterEntry.builder()
                            .entryId(Integer.parseInt(data.get("entryId").toString()))
                            .userId(Integer.parseInt(data.get("userId").toString()))
                            .date(LocalDate.parse(data.get("date").toString(), formatter))
                            .orderId(data.get("orderId").toString())
                            .productionId(data.get("productionId").toString())
                            .omsId(data.get("omsId").toString())
                            .clientName(data.get("clientName").toString())
                            .actionType(data.get("actionType").toString())
                            .printPrepType(data.get("printPrepType").toString())
                            .colorsProfile(data.get("colorsProfile").toString())
                            .articleNumbers(Integer.parseInt(data.get("articleNumbers").toString()))
                            .graphicComplexity(Double.parseDouble(data.get("graphicComplexity").toString()))
                            .observations(data.get("observations") != null ? data.get("observations").toString() : "")
                            .adminSync("ADMIN_EDITED")  // Always set to ADMIN_EDITED when saving
                            .build())
                    .collect(Collectors.toList());

            adminRegisterService.saveAdminRegisterEntries(username, userId, year, month, entries);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving entries: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error saving entries: " + e.getMessage());
        }
    }

    @PostMapping("/calculate-bonus")
    public ResponseEntity<BonusCalculationResult> calculateBonus(@RequestBody Map<String, Object> request) {
        try {
            BonusCalculationResult result = adminRegisterService.calculateBonusFromRequest(request);

            // Get required parameters
            Integer userId = (Integer) request.get("userId");
            Integer year = (Integer) request.get("year");
            Integer month = (Integer) request.get("month");

            // Get username from UserService
            String username = userService.getUserById(userId)
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
    public ResponseEntity<RegisterSummary> getRegisterSummary(
            @RequestParam String username,
            @RequestParam Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month) {

        List<RegisterEntry> entries = adminRegisterService.loadUserRegisterEntries(
                username, userId, year, month);
        RegisterSummary summary = adminRegisterService.calculateRegisterSummary(entries);
        return ResponseEntity.ok(summary);
    }
}