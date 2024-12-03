package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.enums.ActionType;
import com.ctgraphdep.enums.PrintPrepType;
import com.ctgraphdep.exception.RegisterValidationException;
import com.ctgraphdep.model.RegisterEntry;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.FolderStatusService;
import com.ctgraphdep.service.UserRegisterService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserRegisterExcelExporter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/user/register")
public class UserRegisterController extends BaseController {
    private final UserRegisterService userRegisterService;
    private final UserRegisterExcelExporter userRegisterExcelExporter;

    public UserRegisterController(
            UserService userService,
            FolderStatusService folderStatusService,
            UserRegisterService userRegisterService, UserRegisterExcelExporter userRegisterExcelExporter) {
        super(userService, folderStatusService);
        this.userRegisterService = userRegisterService;
        this.userRegisterExcelExporter = userRegisterExcelExporter;
        LoggerUtil.initialize(this.getClass(), "Initializing Register Controller");
    }

    @GetMapping
    public String showRegister(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,  // Add username parameter
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        try {
            User currentUser = getUser(userDetails);
            User targetUser;

            // Determine which user's register to display
            if (username != null) {
                // If username is provided and user has admin role, show that user's register
                if (currentUser.hasRole("ADMIN") || currentUser.hasRole("TEAM_LEADER")) {
                    targetUser = getUserService().getUserByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                } else if (username.equals(currentUser.getUsername())) {
                    // Regular users can only view their own register
                    targetUser = currentUser;
                } else {
                    // If regular user tries to access another user's register, redirect to their own
                    return "redirect:/user/register";
                }
            } else {
                // No username specified, show current user's register
                targetUser = currentUser;
            }

            // Add role-specific view attributes
            if (currentUser.hasRole("ADMIN")) {
                model.addAttribute("isAdminView", true);
                model.addAttribute("dashboardUrl", "/admin");
            } else if (currentUser.hasRole("TEAM_LEADER")) {
                model.addAttribute("isTeamLeaderView", true);
                model.addAttribute("dashboardUrl", "/team-leader");
            } else {
                model.addAttribute("dashboardUrl", "/user");
            }

            // Set default year and month if not provided
            LocalDate now = LocalDate.now();
            year = year != null ? year : now.getYear();
            month = month != null ? month : now.getMonthValue();

            // Load entries for the target user
            List<RegisterEntry> entries = userRegisterService.loadMonthEntries(
                    targetUser.getUsername(),
                    targetUser.getUserId(),
                    year,
                    month
            );

            // Add all necessary data to model
            model.addAttribute("user", targetUser);
            model.addAttribute("entries", entries);
            model.addAttribute("currentYear", year);
            model.addAttribute("currentMonth", month);
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepType.getValues());


            // Add view control attributes
            if (currentUser.getRole().equals("ROLE_ADMIN") ||
                    currentUser.getRole().equals("ROLE_TEAM_LEADER")) {
                model.addAttribute("isAdminView", true);
                model.addAttribute("targetUser", targetUser);

                if (!targetUser.equals(currentUser)) {
                    model.addAttribute("viewingOtherUser", true);
                    model.addAttribute("canEdit", true);
                }
            }

            return "user/register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading register page: " + e.getMessage());
            model.addAttribute("error", "Error loading register data");
            return "user/register";
        }
    }

    @PostMapping("/entry")
    public String saveEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String productionId,
            @RequestParam(required = false) String omsId,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String printPrepType,
            @RequestParam(required = false) String colorsProfile,
            @RequestParam(required = false) Integer articleNumbers,
            @RequestParam(required = false) Double graphicComplexity,
            @RequestParam(required = false) String observations,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            // Initial validation for required fields
            if (date == null) {
                return String.format("redirect:/user/register?error=missing_date&year=%d&month=%d", year, month);
            }
            if (orderId == null || orderId.trim().isEmpty()) {
                return String.format("redirect:/user/register?error=missing_order_id&year=%d&month=%d", year, month);
            }
            if (omsId == null || omsId.trim().isEmpty()) {
                return String.format("redirect:/user/register?error=missing_oms_id&year=%d&month=%d", year, month);
            }
            if (clientName == null || clientName.trim().isEmpty()) {
                return String.format("redirect:/user/register?error=missing_client&year=%d&month=%d", year, month);
            }
            if (actionType == null || actionType.trim().isEmpty()) {
                return String.format("redirect:/user/register?error=missing_action_type&year=%d&month=%d", year, month);
            }
            if (printPrepType == null || printPrepType.trim().isEmpty()) {
                return String.format("redirect:/user/register?error=missing_print_type&year=%d&month=%d", year, month);
            }
            if (articleNumbers == null) {
                return String.format("redirect:/user/register?error=missing_articles&year=%d&month=%d", year, month);
            }

            User user = getUser(userDetails);

            RegisterEntry entry = RegisterEntry.builder()
                    .userId(user.getUserId())
                    .date(date)
                    .orderId(orderId.trim())
                    .productionId(productionId != null ? productionId.trim() : null)
                    .omsId(omsId.trim())
                    .clientName(clientName.trim())
                    .actionType(actionType)
                    .printPrepType(printPrepType)
                    .colorsProfile(colorsProfile != null ? colorsProfile.trim().toUpperCase() : null)
                    .articleNumbers(articleNumbers)
                    .graphicComplexity(graphicComplexity)
                    .observations(observations != null ? observations.trim() : null)
                    .adminSync("USER_INPUT")
                    .build();

            userRegisterService.saveEntry(user.getUsername(), user.getUserId(), entry);
            redirectAttributes.addFlashAttribute("successMessage", "Entry added successfully");

        } catch (RegisterValidationException e) {
            return String.format("redirect:/user/register?error=%s&year=%d&month=%d",
                    e.getErrorCode(), year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving register entry: " + e.getMessage());
            return String.format("redirect:/user/register?error=save_failed&year=%d&month=%d", year, month);
        }

        return "redirect:/user/register?year=" + year + "&month=" + month;
    }

    @PostMapping("/entry/{entryId}")
    public String updateEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer entryId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam String orderId,
            @RequestParam(required = false) String productionId,
            @RequestParam String omsId,
            @RequestParam String clientName,
            @RequestParam String actionType,
            @RequestParam String printPrepType,
            @RequestParam(required = false) String colorsProfile,
            @RequestParam Integer articleNumbers,
            @RequestParam(required = false) Double graphicComplexity,
            @RequestParam(required = false) String observations,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            User user = getUser(userDetails);

            RegisterEntry entry = RegisterEntry.builder()
                    .entryId(entryId)
                    .userId(user.getUserId())  // Set the userId
                    .date(date)
                    .orderId(orderId)
                    .productionId(productionId)
                    .omsId(omsId)
                    .clientName(clientName)
                    .actionType(actionType)
                    .printPrepType(printPrepType)
                    .colorsProfile(colorsProfile)
                    .articleNumbers(articleNumbers)
                    .graphicComplexity(graphicComplexity)
                    .observations(observations)
                    .adminSync("USER_INPUT")
                    .build();

            userRegisterService.saveEntry(user.getUsername(), user.getUserId(), entry);
            redirectAttributes.addFlashAttribute("successMessage", "Entry updated successfully");

        } catch (RegisterValidationException e) {
            LoggerUtil.warn(this.getClass(),
                    "Validation error while updating entry: " + e.getMessage());
            return String.format("redirect:/user/register?error=%s&year=%d&month=%d",
                    e.getErrorCode(), year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    "Error updating register entry: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update entry: " + e.getMessage());
        }

        return "redirect:/user/register?year=" + year + "&month=" + month;
    }

    @PostMapping("/delete")
    public String deleteEntry(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Integer entryId,
            @RequestParam Integer year,
            @RequestParam Integer month,
            RedirectAttributes redirectAttributes) {

        try {
            User user = getUser(userDetails);
            userRegisterService.deleteEntry(user.getUsername(), user.getUserId(), entryId, year, month);
            redirectAttributes.addFlashAttribute("successMessage", "Entry deleted successfully");
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error deleting entry: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting entry");
        }

        return "redirect:/user/register?year=" + year + "&month=" + month;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {
        try {
            User user = getUser(userDetails);
            List<RegisterEntry> entries = userRegisterService.loadMonthEntries(
                    user.getUsername(), user.getUserId(), year, month);

            byte[] excelData = userRegisterExcelExporter.exportToExcel(user, entries, year, month);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=\"register_%d_%02d.xlsx\"", year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}