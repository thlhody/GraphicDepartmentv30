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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Controller
@RequestMapping("/user/register")
@PreAuthorize("isAuthenticated()")
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
        LoggerUtil.initialize(this.getClass(), null);
    }

    @GetMapping
    public String showRegister(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        try {
            // Get current user
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                LoggerUtil.error(this.getClass(), "Current user is null after getUser()");
                return "redirect:/login";
            }

            // Initialize year and month if not provided
            LocalDate now = LocalDate.now();
            year = year != null ? year : now.getYear();
            month = month != null ? month : now.getMonthValue();

            // Always set these basic attributes regardless of potential errors
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepType.getValues());
            model.addAttribute("currentYear", year);
            model.addAttribute("currentMonth", month);

            // Determine target user
            User targetUser = currentUser;
            if (username != null) {
                if (currentUser.hasRole("ADMIN") || currentUser.hasRole("TEAM_LEADER")) {
                    targetUser = getUserService().getUserByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                } else if (!username.equals(currentUser.getUsername())) {
                    return "redirect:/user/register";
                }
            }

            // Set user information
            model.addAttribute("user", targetUser);
            model.addAttribute("userName", targetUser.getName());
            model.addAttribute("userDisplayName",
                    targetUser.getName() != null ? targetUser.getName() : targetUser.getUsername());

            // Load entries
            List<RegisterEntry> entries = userRegisterService.loadMonthEntries(
                    targetUser.getUsername(),
                    targetUser.getUserId(),
                    year,
                    month
            );
            model.addAttribute("entries", entries != null ? entries : new ArrayList<>());

            // Add role-specific attributes
            if (currentUser.hasRole("ADMIN")) {
                model.addAttribute("isAdminView", true);
                model.addAttribute("dashboardUrl", "/admin");
            } else if (currentUser.hasRole("TEAM_LEADER")) {
                model.addAttribute("isTeamLeaderView", true);
                model.addAttribute("dashboardUrl", "/team-lead");
            } else {
                model.addAttribute("dashboardUrl", "/user");
            }

            // Add view control attributes for admin/team leader
            if (currentUser.hasRole("ADMIN") || currentUser.hasRole("TEAM_LEADER")) {
                model.addAttribute("isAdminView", true);
                model.addAttribute("targetUser", targetUser);
                if (!targetUser.equals(currentUser)) {
                    model.addAttribute("viewingOtherUser", true);
                    model.addAttribute("canEdit", true);
                }
            }

            return "user/register";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading register page: " + e.getMessage(), e);

            // Set error attributes while preserving basic functionality
            model.addAttribute("error", "Error loading register data: " + e.getMessage());
            model.addAttribute("entries", new ArrayList<>());
            model.addAttribute("actionTypes", ActionType.getValues());
            model.addAttribute("printPrepTypes", PrintPrepType.getValues());
            model.addAttribute("currentYear", year);
            model.addAttribute("currentMonth", month);

            // Try to set user info from current user if available
            if (userDetails != null) {
                User currentUser = getUser(userDetails);
                if (currentUser != null) {
                    model.addAttribute("user", currentUser);
                    model.addAttribute("userName", currentUser.getName());
                    model.addAttribute("userDisplayName", currentUser.getName());
                }
            }

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
            @RequestParam(required = false) List<String> printPrepTypes,  // Changed to List<String>
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
            if (printPrepTypes == null || printPrepTypes.isEmpty()) {
                return String.format("redirect:/user/register?error=missing_print_type&year=%d&month=%d", year, month);
            }
            if (articleNumbers == null) {
                return String.format("redirect:/user/register?error=missing_articles&year=%d&month=%d", year, month);
            }

            User user = getUser(userDetails);
            List<String> uniquePrintPrepTypes = new ArrayList<>(new LinkedHashSet<>(printPrepTypes));

            RegisterEntry entry = RegisterEntry.builder()
                    .userId(user.getUserId())
                    .date(date)
                    .orderId(orderId.trim())
                    .productionId(productionId != null ? productionId.trim() : null)
                    .omsId(omsId.trim())
                    .clientName(clientName.trim())
                    .actionType(actionType)
                    .printPrepTypes(uniquePrintPrepTypes)  // Use the deduplicated list
                    .colorsProfile(colorsProfile != null ? colorsProfile.trim().toUpperCase() : null)
                    .articleNumbers(articleNumbers)
                    .graphicComplexity(graphicComplexity)
                    .observations(observations != null ? observations.trim() : null)
                    .adminSync("USER_INPUT")
                    .build();

            userRegisterService.saveEntry(user.getUsername(), user.getUserId(), entry);
            redirectAttributes.addFlashAttribute("successMessage", "Entry added successfully");

        } catch (RegisterValidationException e) {
            return String.format("redirect:/user/register?error=%s&year=%d&month=%d", e.getErrorCode(), year, month);
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
            @RequestParam List<String> printPrepTypes,  // Changed to List<String>
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
                    .userId(user.getUserId())
                    .date(date)
                    .orderId(orderId)
                    .productionId(productionId)
                    .omsId(omsId)
                    .clientName(clientName)
                    .actionType(actionType)
                    .printPrepTypes(printPrepTypes)  // Set the list directly
                    .colorsProfile(colorsProfile)
                    .articleNumbers(articleNumbers)
                    .graphicComplexity(graphicComplexity)
                    .observations(observations)
                    .adminSync("USER_INPUT")
                    .build();

            userRegisterService.saveEntry(user.getUsername(), user.getUserId(), entry);
            redirectAttributes.addFlashAttribute("successMessage", "Entry updated successfully");

        } catch (RegisterValidationException e) {
            LoggerUtil.warn(this.getClass(), "Validation error while updating entry: " + e.getMessage());
            return String.format("redirect:/user/register?error=%s&year=%d&month=%d", e.getErrorCode(), year, month);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error updating register entry: " + e.getMessage());
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
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"register_%d_%02d.xlsx\"", year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).body(excelData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}