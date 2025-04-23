package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserWorktimeExcelExporter;
import com.ctgraphdep.validation.TimeValidationService;
import com.ctgraphdep.validation.commands.ValidatePeriodCommand;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/user/worktime")
@PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_TEAM_LEADER', 'ROLE_USER_CHECKING', 'ROLE_CHECKING', 'ROLE_TL_CHECKING')")
public class UserWorktimeController extends BaseController {

    private final WorktimeDisplayService worktimeDisplayService;
    private final WorktimeManagementService worktimeManagementService;
    private final UserWorktimeExcelExporter excelExporter;

    public UserWorktimeController(
            UserService userService,
            FolderStatus folderStatus,
            WorktimeDisplayService worktimeDisplayService,
            WorktimeManagementService worktimeManagementService,
            UserWorktimeExcelExporter excelExporter,
            TimeValidationService validationService) {
        super(userService, folderStatus, validationService);
        this.worktimeDisplayService = worktimeDisplayService;
        this.worktimeManagementService = worktimeManagementService;
        this.excelExporter = excelExporter;
    }

    @GetMapping
    public String getWorktimePage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model, RedirectAttributes redirectAttributes) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing worktime page at " + getStandardCurrentDateTime());

            // Get user and add common model attributes in one call
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Validate period directly here before making service call
            try {
                // Create and execute validation command directly
                ValidatePeriodCommand validateCommand = getTimeValidationService().getValidationFactory().createValidatePeriodCommand(selectedYear, selectedMonth, 24); // 24 months ahead max
                getTimeValidationService().execute(validateCommand);
            } catch (IllegalArgumentException e) {
                // Handle validation failure gracefully
                String userMessage = "The selected period is not valid. You can only view periods up to 24 months in the future.";
                redirectAttributes.addFlashAttribute("periodError", userMessage);

                // Reset to current period
                LocalDate currentDate = getStandardCurrentDate();
                return "redirect:/user/worktime?year=" + currentDate.getYear() + "&month=" + currentDate.getMonthValue();
            }

            // Use worktimeManagementService to load worktime data which handles synchronization internally
            List<WorkTimeTable> worktimeData = worktimeManagementService.loadMonthWorktime(currentUser.getUsername(), selectedYear, selectedMonth);

            // Make sure we have data (never null)
            if (worktimeData == null) {
                worktimeData = new ArrayList<>();
                LoggerUtil.warn(this.getClass(), String.format("No worktime data found for user %s (%d/%d)", currentUser.getUsername(), selectedMonth, selectedYear));
            }

            // Process data with display service to get DTOs
            Map<String, Object> displayData = worktimeDisplayService.prepareUserDisplayData(currentUser, worktimeData, selectedYear, selectedMonth);

            // Add display data to the model
            model.addAllAttributes(displayData);

            // Add current time to model
            model.addAttribute("currentSystemTime", getStandardCurrentDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            return "user/worktime";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing worktime: " + e.getMessage(), e);
            model.addAttribute("error", "Error loading worktime data");
            return "user/worktime";
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportToExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        try {
            LoggerUtil.info(this.getClass(), "Exporting worktime data at " + getStandardCurrentDateTime());

            // Get the user - don't need to add model attributes for API endpoints
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                LoggerUtil.error(this.getClass(), "Unauthorized access attempt to export worktime data");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Get worktime data using WorktimeManagementService for consistency
            List<WorkTimeTable> worktimeData = worktimeManagementService.loadMonthWorktime(
                    currentUser.getUsername(), selectedYear, selectedMonth);

            // Log the data details
            LoggerUtil.info(this.getClass(), String.format("Exporting worktime data for %s (%d/%d). Total entries: %d",
                    currentUser.getUsername(), selectedMonth, selectedYear, worktimeData.size()));

            // Get display data which includes the summary with DTOs
            Map<String, Object> displayData = worktimeDisplayService.prepareUserDisplayData(
                    currentUser, worktimeData, selectedYear, selectedMonth);

            // Extract DTO's for export in Excel
            @SuppressWarnings("unchecked")
            List<WorkTimeEntryDTO> entryDTOs = (List<WorkTimeEntryDTO>) displayData.get("worktimeData");
            WorkTimeSummaryDTO summaryDTO = (WorkTimeSummaryDTO) displayData.get("summary");

            // Pass DTOs to the updated Excel exporter
            byte[] excelData = excelExporter.exportToExcel(currentUser, entryDTOs, summaryDTO, selectedYear, selectedMonth);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=\"worktime_%s_%d_%02d.xlsx\"",
                            currentUser.getUsername(), selectedYear, selectedMonth))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}