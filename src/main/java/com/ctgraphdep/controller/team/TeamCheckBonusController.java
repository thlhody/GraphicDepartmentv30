package com.ctgraphdep.controller.team;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.CheckBonusEntry;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.register.service.CheckBonusService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.result.ServiceResult;
import com.ctgraphdep.utils.ExportCheckBonusExcel;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller for Team Lead Check Bonus operations.
 * Handles calculation, saving, and viewing of check register bonuses.
 */
@Controller
@RequestMapping("/team/check-register")
@PreAuthorize("hasAnyRole('ROLE_TL_CHECKING', 'ROLE_ADMIN')")
public class TeamCheckBonusController extends BaseController {

    @Autowired
    private CheckBonusService checkBonusService;

    @Autowired
    private ExportCheckBonusExcel exportCheckBonusExcel;

    public TeamCheckBonusController(UserService userService, FolderStatus folderStatus,
                                     TimeValidationService timeValidationService,
                                     CheckBonusService checkBonusService,
                                     ExportCheckBonusExcel exportCheckBonusExcel) {
        super(userService, folderStatus, timeValidationService);
        this.checkBonusService = checkBonusService;
        this.exportCheckBonusExcel = exportCheckBonusExcel;
        LoggerUtil.initialize(this.getClass(), null);
    }

    /**
     * Calculate bonus for a single user
     * POST /team/check-register/calculate-bonus
     */
    @PostMapping("/calculate-bonus")
    @ResponseBody
    public ResponseEntity<?> calculateBonus(@RequestBody Map<String, Object> request) {
        try {
            // Extract parameters
            String username = (String) request.get("username");
            Integer userId = (Integer) request.get("userId");
            Integer year = (Integer) request.get("year");
            Integer month = (Integer) request.get("month");
            Double bonusSum = ((Number) request.get("bonusSum")).doubleValue();
            Double standardHours = ((Number) request.get("standardHours")).doubleValue();

            // Validate parameters
            if (username == null || userId == null || year == null || month == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "Missing required parameters"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                "Calculate bonus request for user: %s, year: %d, month: %d", username, year, month));

            // Calculate bonus
            ServiceResult<CheckBonusEntry> result = checkBonusService.calculateUserBonus(
                username, userId, year, month, bonusSum, standardHours);

            if (!result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", result.getErrorMessage()));
            }

            // Return bonus data
            return ResponseEntity.ok(result.getData());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error calculating bonus: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Failed to calculate bonus: " + e.getMessage()));
        }
    }

    /**
     * Save bonus data
     * POST /team/check-register/save-bonus
     */
    @PostMapping("/save-bonus")
    @ResponseBody
    public ResponseEntity<?> saveBonus(@RequestBody CheckBonusEntry bonusEntry) {
        try {
            if (bonusEntry == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "Bonus data is required"));
            }

            LoggerUtil.info(this.getClass(), String.format(
                "Save bonus request for user: %s, year: %d, month: %d",
                bonusEntry.getUsername(), bonusEntry.getYear(), bonusEntry.getMonth()));

            // Save bonus
            ServiceResult<Void> result = checkBonusService.saveBonusData(bonusEntry);

            if (!result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", result.getErrorMessage()));
            }

            return ResponseEntity.ok(Map.of(
                "message", "Bonus saved successfully",
                "filename", String.format("lead_check_bonus_%d_%02d.json",
                    bonusEntry.getYear(), bonusEntry.getMonth())
            ));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error saving bonus: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Failed to save bonus: " + e.getMessage()));
        }
    }

    /**
     * Load bonus data for a period
     * GET /team/check-register/load-bonus
     */
    @GetMapping("/load-bonus")
    @ResponseBody
    public ResponseEntity<?> loadBonus(@RequestParam("year") int year,
                                        @RequestParam("month") int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                "Load bonus request for year: %d, month: %d", year, month));

            // Load bonus data
            ServiceResult<List<CheckBonusEntry>> result = checkBonusService.loadBonusData(year, month);

            if (!result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No bonus data found for this period"));
            }

            List<CheckBonusEntry> bonusList = result.getData();

            // Return empty list if no data (graceful handling)
            if (bonusList == null || bonusList.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            return ResponseEntity.ok(bonusList);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading bonus: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Failed to load bonus: " + e.getMessage()));
        }
    }

    /**
     * Show check bonus dashboard page
     * GET /team/check-register/bonus
     */
    @GetMapping("/bonus")
    public String showCheckBonusDashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        try {
            // Get current user using BaseController method
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            model.addAttribute("currentUser", currentUser);
            model.addAttribute("userName", currentUser.getName());

            // Add current date info
            LocalDate now = getStandardCurrentDate();
            model.addAttribute("currentYear", now.getYear());
            model.addAttribute("currentMonth", now.getMonthValue());

            // Add dashboard URL based on user role
            String dashboardUrl = getDashboardUrlForUser(currentUser);
            model.addAttribute("dashboardUrl", dashboardUrl);

            return "admin/check-bonus";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error showing check bonus dashboard: " + e.getMessage(), e);
            return "error/500";
        }
    }

    /**
     * Export bonus data to Excel
     * GET /team/check-register/export-bonus
     */
    @GetMapping("/export-bonus")
    public ResponseEntity<?> exportBonus(@RequestParam("year") int year,
                                          @RequestParam("month") int month) {
        try {
            LoggerUtil.info(this.getClass(), String.format(
                "Export bonus request for year: %d, month: %d", year, month));

            // Load bonus data
            ServiceResult<List<CheckBonusEntry>> result = checkBonusService.loadBonusData(year, month);

            if (!result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No bonus data to export"));
            }

            // Export to Excel
            byte[] excelData = exportCheckBonusExcel.exportToExcel(result.getData(), year, month);

            // Prepare response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    String.format("check_bonus_%d_%02d.xlsx", year, month));

            LoggerUtil.info(this.getClass(), String.format(
                "Successfully exported %d bonus entries to Excel", result.getData().size()));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting bonus: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to export bonus: " + e.getMessage()));
        }
    }
}