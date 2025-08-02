package com.ctgraphdep.controller.admin;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.WorkTimeTable;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.WorkTimeExcelExporter;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.ValidationResult;
import com.ctgraphdep.worktime.display.WorktimeDisplayService;
import com.ctgraphdep.worktime.service.WorktimeOperationService;
import com.ctgraphdep.worktime.model.OperationResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/worktime")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminWorkTimeController extends BaseController {

    private final WorktimeOperationService worktimeOperationService;
    private final UserManagementService userManagementService;
    private final WorktimeDisplayService worktimeDisplayService;
    private final WorkTimeExcelExporter excelExporter;

    protected AdminWorkTimeController(UserService userService, FolderStatus folderStatus, TimeValidationService timeValidationService, WorktimeOperationService worktimeOperationService,
                                      UserManagementService userManagementService, WorktimeDisplayService worktimeDisplayService, WorkTimeExcelExporter excelExporter) {
        super(userService, folderStatus, timeValidationService);
        this.worktimeOperationService = worktimeOperationService;
        this.userManagementService = userManagementService;
        this.worktimeDisplayService = worktimeDisplayService;
        this.excelExporter = excelExporter;
    }

    // ========================================================================
    // MAIN PAGE ENDPOINTS
    // ========================================================================

    /**
     * Display admin worktime management page (using DTO-based display)
     */
    @GetMapping
    public String getWorktimePage(@RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month, @RequestParam(required = false) Integer selectedUserId,
                                  Model model, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), String.format(
                    "Loading admin worktime page: year=%s, month=%s", year, month));

            // Handle blank table case (no period specified)
            if (year == null || month == null) {
                LoggerUtil.info(this.getClass(), "No period specified - showing blank table");
                model.addAttribute("showBlankTable", true);
                model.addAttribute("currentYear", LocalDate.now().getYear());
                model.addAttribute("currentMonth", LocalDate.now().getMonthValue());
                return "admin/worktime";
            }

            // Period specified - validate and display existing data
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            try {
                // Validate period
                var validateCommand = getTimeValidationService().getValidationFactory()
                        .createValidatePeriodCommand(selectedYear, selectedMonth, 24);
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                LoggerUtil.warn(this.getClass(), String.format("Period validation failed: %s", e.getMessage()));
                String userMessage = "The selected period is not valid. You can only view periods up to 24 months in the future.";
                redirectAttributes.addFlashAttribute("periodError", userMessage);

                LocalDate currentDate = getStandardCurrentDate();
                return "redirect:/admin/worktime?year=" + currentDate.getYear() + "&month=" + currentDate.getMonthValue();
            }

            // Get users
            List<User> nonAdminUsers = userManagementService.getNonAdminUsers();
            if (nonAdminUsers.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No users found");
                return "redirect:/admin";
            }

            // Load existing data only - no consolidation
            LoggerUtil.info(this.getClass(), String.format(
                    "Loading existing admin worktime data for %d/%d (no consolidation)", selectedMonth, selectedYear));

            List<WorkTimeTable> viewableEntries = worktimeOperationService.getViewableEntries(selectedYear, selectedMonth);
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = convertToUserEntriesMap(viewableEntries);

            // NEW: Prepare model data using DTO-based approach for consistent display
            worktimeDisplayService.prepareWorkTimeModelWithDTOs(
                    model, selectedYear, selectedMonth, selectedUserId, nonAdminUsers, userEntriesMap);

            LoggerUtil.info(this.getClass(), String.format(
                    "Successfully loaded admin worktime page for %d/%d with %d entries",
                    selectedMonth, selectedYear, viewableEntries.size()));

            return "admin/worktime";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading admin worktime page: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Error loading worktime data");
            return "redirect:/admin/worktime";
        }
    }


    // ========================================================================
    // UPDATE ENDPOINTS
    // ========================================================================

    @PostMapping("/update")
    public String updateValue(@RequestParam Integer userId, @RequestParam int year, @RequestParam int month, @RequestParam int day, @RequestParam String value, RedirectAttributes redirectAttributes) {

        try {
            LocalDate date = LocalDate.of(year, month, day);

            LoggerUtil.info(this.getClass(), String.format("Admin updating worktime: userId=%d, date=%s, value=%s", userId, date, value));

            ValidationResult validationResult = getTimeValidationService().validateAdminWorktimeUpdate(date, value);
            if (validationResult.isInvalid()) {
                redirectAttributes.addFlashAttribute("errorMessage", validationResult.getErrorMessage());
                return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);
            }

            // ✅ SIMPLIFIED: Use only AdminUpdateCommand for ALL admin updates
            // AdminUpdateCommand now handles:
            // - Regular work hours: "8"
            // - Time off types: "CO", "CM", "SN"
            // - Special day work: "SN:5", "CO:6", "CM:4", "W:8"
            // - Removal: "BLANK", "REMOVE", empty string
            OperationResult result = worktimeOperationService.processAdminUpdate(userId, date, value);

            // Process result
            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", result.getMessage());
                LoggerUtil.info(this.getClass(), "Admin update successful: " + result.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
                LoggerUtil.warn(this.getClass(), "Admin update failed: " + result.getMessage());
            }

            return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in admin worktime update: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Update failed: " + e.getMessage());
            return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);
        }
    }

    @PostMapping("/holiday/add")
    public String addNationalHoliday(@RequestParam int year, @RequestParam int month, @RequestParam int day, RedirectAttributes redirectAttributes) {

        try {
            LocalDate holidayDate = LocalDate.of(year, month, day);

            ValidationResult validationResult = getTimeValidationService().validateHolidayAddition(holidayDate);
            if (validationResult.isInvalid()) {
                redirectAttributes.addFlashAttribute("errorMessage", validationResult.getErrorMessage());
                return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);
            }

            // Execute addition (service receives clean data)
            OperationResult result = worktimeOperationService.addNationalHoliday(holidayDate);

            // Process result
            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", String.format("National holiday added for %s", holidayDate));
                LoggerUtil.info(this.getClass(), "Holiday added successfully: " + result.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
                LoggerUtil.warn(this.getClass(), "Holiday addition failed: " + result.getMessage());
            }

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error adding holiday: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to add holiday");
        }

        return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);
    }

    /**
     * Finalize worktime entries for a period
     */
    @PostMapping("/finalize")
    public String finalizeWorktime(@RequestParam int year, @RequestParam int month, @RequestParam(required = false) Integer userId,
            RedirectAttributes redirectAttributes) {

        try {


            LoggerUtil.info(this.getClass(), String.format("Admin requesting finalization: year=%d, month=%d, userId=%s",
                    year, month, userId));

            // Validate period
            try {
                var validateCommand = getTimeValidationService().getValidationFactory().createValidatePeriodCommand(year, month, 24);
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid period: " + e.getMessage());
                return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);
            }

            // Execute finalization
            OperationResult result = worktimeOperationService.finalizeWorktimePeriod(year, month, userId);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", result.getMessage());
                LoggerUtil.info(this.getClass(), "Finalization successful: " + result.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", result.getMessage());
                LoggerUtil.warn(this.getClass(), "Finalization failed: " + result.getMessage());
            }

            return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in finalization endpoint: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Finalization failed: " + e.getMessage());
            return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);
        }
    }

    // ========================================================================
    // PERIOD CONSOLIDATION ENDPOINTS (UNCHANGED)
    // ========================================================================

    /**
     * View specific period with consolidation
     */
    @PostMapping("/view-period")
    public String viewPeriod(@RequestParam int year, @RequestParam int month, RedirectAttributes redirectAttributes) {

        try {
            // Validate period first
            try {
                var validateCommand = getTimeValidationService().getValidationFactory().createValidatePeriodCommand(year, month, 60);
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                redirectAttributes.addFlashAttribute("error", "Invalid period: " + e.getMessage());
                return "redirect:/admin/worktime";
            }

            LoggerUtil.info(this.getClass(), String.format("Admin consolidating and viewing period: %d/%d", year, month));

            // This is the ONLY place where consolidation happens
            OperationResult result = worktimeOperationService.consolidateWorktime(year, month);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("successMessage", result.getMessage());
                LoggerUtil.info(this.getClass(), "Period consolidation successful: " + result.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("error", result.getMessage());
                LoggerUtil.warn(this.getClass(), "Period consolidation failed: " + result.getMessage());
            }

            return String.format("redirect:/admin/worktime?year=%d&month=%d", year, month);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error in period consolidation: " + e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Consolidation failed: " + e.getMessage());
            return "redirect:/admin/worktime";
        }
    }

    // ========================================================================
    // EXPORT ENDPOINTS (CORRECTED TO USE EXISTING METHODS)
    // ========================================================================

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(@RequestParam int year, @RequestParam int month) {

        try {
            LoggerUtil.info(this.getClass(), String.format("Exporting admin worktime for %d/%d", year, month));

            // Get users and load existing data
            List<User> nonAdminUsers = userManagementService.getNonAdminUsers();
            List<WorkTimeTable> viewableEntries = worktimeOperationService.getViewableEntries(year, month);
            Map<Integer, Map<LocalDate, WorkTimeTable>> userEntriesMap = convertToUserEntriesMap(viewableEntries);

            if (userEntriesMap.isEmpty()) {
                LoggerUtil.warn(this.getClass(), "No data available for export");
                return ResponseEntity.noContent().build();
            }

            // Use existing WorkTimeExcelExporter.exportToExcel method
            byte[] excelData = excelExporter.exportToExcel(nonAdminUsers, userEntriesMap, year, month);

            return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"admin_worktime_%d_%02d.xlsx\"", year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting admin worktime: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // AJAX ENDPOINTS (USING EXISTING METHODS)
    // ========================================================================

    @GetMapping("/entry-details")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEntryDetails(@RequestParam Integer userId, @RequestParam Integer year, @RequestParam Integer month, @RequestParam Integer day) {

        try {
            // Delegate to existing method in WorktimeDisplayService
            Map<String, Object> entryDetails = worktimeDisplayService.getEntryDetails(userId, year, month, day);
            return ResponseEntity.ok(entryDetails);

        } catch (IllegalArgumentException e) {
            LoggerUtil.warn(this.getClass(), String.format("Invalid request for entry details: %s", e.getMessage()));
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), String.format("Error retrieving entry details for user %d on %d-%d-%d: %s", userId, year, month, day, e.getMessage()), e);

            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
        }
    }

    /**
     * Get available users for dropdown
     */
    @GetMapping("/users")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAvailableUsers() {
        try {
            List<User> users = userManagementService.getAllUsers();

            List<Map<String, Object>> userList = users.stream()
                    .filter(user -> !user.getRole().contains(SecurityConstants.ROLE_ADMIN))
                    .map(user -> {
                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("userId", user.getUserId());
                        userMap.put("username", user.getUsername());
                        userMap.put("fullName", user.getName());
                        return userMap;
                    })
                    .sorted((a, b) -> ((String) a.get("username")).compareToIgnoreCase((String) b.get("username")))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(userList);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting available users: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Convert list of entries to user entries map (existing implementation)
     */
    private Map<Integer, Map<LocalDate, WorkTimeTable>> convertToUserEntriesMap(List<WorkTimeTable> entries) {
        return entries.stream().collect(Collectors.groupingBy(
                WorkTimeTable::getUserId,
                Collectors.toMap(WorkTimeTable::getWorkDate, entry -> entry,
                        (existing, replacement) -> replacement, TreeMap::new)
        ));
    }

}