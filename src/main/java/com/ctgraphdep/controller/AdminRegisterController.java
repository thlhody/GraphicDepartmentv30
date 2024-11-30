package com.ctgraphdep.controller;

import com.ctgraphdep.model.*;
import com.ctgraphdep.service.AdminRegisterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepType;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/register")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRegisterController {

    private final AdminRegisterService adminRegisterService;
    private final UserService userService;

    @Autowired
    public AdminRegisterController(AdminRegisterService adminRegisterService,
                                   UserService userService) {
        this.adminRegisterService = adminRegisterService;
        this.userService = userService;
        LoggerUtil.initialize(this.getClass(), "Initializing Admin Register Controller");
    }

    @GetMapping
    public String getRegisterPage(
            @RequestParam(required = false) String username,
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
            // In AdminRegisterController.java
            List<User> allUsers = userService.getAllUsers();
            List<User> users = userService.getNonAdminUsers(allUsers);
            model.addAttribute("users", users);

            // Add action types and print prep types for filters
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepType.getValues());

            // If username and userId are provided, load entries
            if (username != null && userId != null) {
                List<RegisterEntry> entries = adminRegisterService.loadUserRegisterEntries(
                        username, userId, year, month);
                model.addAttribute("entries", entries);
                model.addAttribute("selectedUser",
                        userService.getUserById(userId).orElse(null));
            }

            // Add period info
            model.addAttribute("currentYear", year);
            model.addAttribute("currentMonth", month);

            // Add bonus configuration defaults
            model.addAttribute("bonusConfig", BonusConfiguration.getDefaultConfig());

            return "admin/register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error loading register page: " + e.getMessage());
            model.addAttribute("error", "Error loading register data");
            return "admin/register";
        }
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
    public ResponseEntity<?> bulkUpdateEntries(
            @RequestBody Map<String, Object> request) {

        try {
            @SuppressWarnings("unchecked")
            List<RegisterEntry> entries = (List<RegisterEntry>) request.get("entries");
            @SuppressWarnings("unchecked")
            List<Integer> selectedIds = (List<Integer>) request.get("selectedIds");
            String fieldName = (String) request.get("fieldName");
            String newValue = (String) request.get("newValue");

            adminRegisterService.bulkUpdateEntries(entries, selectedIds,
                    fieldName, newValue);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error updating entries: " + e.getMessage());
            return ResponseEntity.badRequest()
                    .body("Error updating entries: " + e.getMessage());
        }
    }

    @PostMapping("/save")
    public String saveEntries(
            @RequestParam String username,
            @RequestParam Integer userId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestBody List<RegisterEntry> entries,
            RedirectAttributes redirectAttributes) {

        try {
            adminRegisterService.saveAdminRegisterEntries(username, userId,
                    year, month, entries);
            redirectAttributes.addFlashAttribute("success",
                    "Successfully saved register entries");

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error saving entries: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Error saving entries: " + e.getMessage());
        }

        return "redirect:/admin/register?username=" + username +
                "&userId=" + userId + "&year=" + year + "&month=" + month;
    }

    @PostMapping("/calculate-bonus")
    public ResponseEntity<BonusCalculationResult> calculateBonus(
            @RequestBody Map<String, Object> request) {

        try {
            @SuppressWarnings("unchecked")
            List<RegisterEntry> entries = (List<RegisterEntry>) request.get("entries");
            Integer userId = (Integer) request.get("userId");
            Integer year = (Integer) request.get("year");
            Integer month = (Integer) request.get("month");

            // Get bonus configuration from request
            @SuppressWarnings("unchecked")
            Map<String, Double> configValues = (Map<String, Double>) request.get("bonusConfig");

            BonusConfiguration config = BonusConfiguration.builder()
                    .entriesPercentage(configValues.get("entriesPercentage"))
                    .articlesPercentage(configValues.get("articlesPercentage"))
                    .complexityPercentage(configValues.get("complexityPercentage"))
                    .miscPercentage(configValues.get("miscPercentage"))
                    .normValue(configValues.get("normValue"))
                    .sumValue(configValues.get("sumValue"))
                    .miscValue(configValues.get("miscValue"))
                    .build();

            if (!config.isValid()) {
                return ResponseEntity.badRequest()
                        .body("Invalid bonus configuration: percentages must sum to 1.0");
            }

            BonusCalculationResult result = adminRegisterService.calculateBonus(
                    entries, userId, year, month, config);

            // Save bonus result
            adminRegisterService.saveBonusResult(userId, year, month, result);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error calculating bonus: " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}