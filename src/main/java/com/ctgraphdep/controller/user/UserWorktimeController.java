package com.ctgraphdep.controller.user;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.*;
import com.ctgraphdep.model.dto.worktime.WorkTimeEntryDTO;
import com.ctgraphdep.model.dto.worktime.WorkTimeSummaryDTO;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserWorktimeExcelExporter;
import com.ctgraphdep.validation.TimeValidationService;
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

import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@PreAuthorize("isAuthenticated()")
@RequestMapping("/user/worktime")
public class UserWorktimeController extends BaseController {

    private final UserWorkTimeDisplayService displayService;
    private final WorkTimeEntrySyncService entrySyncService;
    private final UserWorktimeExcelExporter excelExporter;
    private final DataAccessService dataAccessService;

    public UserWorktimeController(
            UserService userService,
            FolderStatus folderStatus,
            UserWorkTimeDisplayService displayService,
            WorkTimeEntrySyncService entrySyncService,
            UserWorktimeExcelExporter excelExporter,
            TimeValidationService validationService,
            DataAccessService dataAccessService) {
        super(userService, folderStatus, validationService);
        this.displayService = displayService;
        this.entrySyncService = entrySyncService;
        this.excelExporter = excelExporter;
        this.dataAccessService = dataAccessService;
    }

    @GetMapping
    public String getWorktimePage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        try {
            LoggerUtil.info(this.getClass(), "Accessing worktime page at " + getStandardCurrentDateTime());

            // Use checkUserAccess for authentication verification
            String accessCheck = checkUserAccess(userDetails, "USER", "ADMIN", "TEAM_LEADER");
            if (accessCheck != null) {
                return accessCheck;
            }

            User currentUser = getUser(userDetails);

            // Add role-specific view attributes
            if (currentUser.hasRole("ADMIN")) {
                model.addAttribute("isAdminView", true);
                model.addAttribute("dashboardUrl", "/admin");
            } else if (currentUser.hasRole("TEAM_LEADER")) {
                model.addAttribute("isTeamLeaderView", true);
                model.addAttribute("dashboardUrl", "/team-lead");
            } else {
                model.addAttribute("dashboardUrl", "/user");
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Step 1: Synchronize entries between admin and user files
            entrySyncService.synchronizeEntries(
                    currentUser.getUsername(), currentUser.getUserId(), selectedYear, selectedMonth);

            // Step 2: Read the data from disk after sync is complete
            List<WorkTimeTable> worktimeData = dataAccessService.readUserWorktime(
                    currentUser.getUsername(), selectedYear, selectedMonth);

            // Make sure we have data (never null)
            if (worktimeData == null) {
                worktimeData = new ArrayList<>();
                LoggerUtil.warn(this.getClass(),
                        String.format("No worktime data found for user %s (%d/%d) after sync",
                                currentUser.getUsername(), selectedMonth, selectedYear));
            }

            // Step 3: Process data with display service to get DTOs
            Map<String, Object> displayData = displayService.prepareDisplayData(
                    currentUser, worktimeData, selectedYear, selectedMonth);

            // Step 4: Add display data to the model
            model.addAllAttributes(displayData);

            // Add current time to model
            model.addAttribute("currentSystemTime", getStandardCurrentDateTime()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

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

            // Use validateUserAccess for better authorization checking
            User user = validateUserAccess(userDetails, "USER", "ADMIN", "TEAM_LEADER");
            if (user == null) {
                LoggerUtil.error(this.getClass(), "Unauthorized access attempt to export worktime data");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Use determineYear and determineMonth from BaseController
            int selectedYear = determineYear(year);
            int selectedMonth = determineMonth(month);

            // Get worktime data
            List<WorkTimeTable> worktimeData = entrySyncService.synchronizeEntries(
                    user.getUsername(), user.getUserId(), selectedYear, selectedMonth);

            // Log the data details
            LoggerUtil.info(this.getClass(),
                    String.format("Exporting worktime data for %s (%d/%d). Total entries: %d",
                            user.getUsername(), selectedMonth, selectedYear, worktimeData.size()));

            // Get display data which includes the summary with DTOs
            Map<String, Object> displayData = displayService.prepareDisplayData(
                    user, worktimeData, selectedYear, selectedMonth);

            // Extract DTO's for Excel export
            @SuppressWarnings("unchecked")
            List<WorkTimeEntryDTO> entryDTOs = (List<WorkTimeEntryDTO>) displayData.get("worktimeData");
            WorkTimeSummaryDTO summaryDTO = (WorkTimeSummaryDTO) displayData.get("summary");

            // Pass DTOs to the updated Excel exporter
            byte[] excelData = excelExporter.exportToExcel(user, entryDTOs, summaryDTO, selectedYear, selectedMonth);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=\"worktime_%s_%d_%02d.xlsx\"",
                                    user.getUsername(), selectedYear, selectedMonth))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}