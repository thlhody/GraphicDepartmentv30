package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.*;
import com.ctgraphdep.service.*;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.utils.UserWorktimeExcelExporter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/user/worktime")
public class UserWorktimeController extends BaseController {

    private final UserWorkTimeDisplayService displayService;
    private final WorkTimeEntrySyncService entrySyncService;
    private final UserWorktimeExcelExporter excelExporter;

    public UserWorktimeController(
            UserService userService,
            FolderStatusService folderStatusService,
            UserWorkTimeDisplayService displayService,
            WorkTimeEntrySyncService entrySyncService, UserWorktimeExcelExporter excelExporter) {
        super(userService, folderStatusService);
        this.displayService = displayService;
        this.entrySyncService = entrySyncService;
        this.excelExporter = excelExporter;
        LoggerUtil.initialize(this.getClass(), "Initializing User Worktime Controller");
    }

    @GetMapping
    public String getWorktimePage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {

        try {
            User currentUser = getUser(userDetails);
            User targetUser;

            // Determine which user's worktime to display
            if (username != null) {
                // Check roles for access control
                if (currentUser.hasRole("ADMIN") || currentUser.hasRole("TEAM_LEADER")) {
                    targetUser = getUserService().getUserByUsername(username)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                } else if (username.equals(currentUser.getUsername())) {
                    // Regular users can only view their own worktime
                    targetUser = currentUser;
                } else {
                    // If regular user tries to access another user's worktime, redirect to their own
                    return "redirect:/user/worktime";
                }
            } else {
                // No username specified, show current user's worktime
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
            year = Optional.ofNullable(year).orElse(now.getYear());
            month = Optional.ofNullable(month).orElse(now.getMonthValue());

            // Synchronize and get worktime entries
            List<WorkTimeTable> worktimeData = entrySyncService.synchronizeEntries(
                    targetUser.getUsername(),
                    targetUser.getUserId(),
                    year,
                    month
            );

            // Prepare display data
            Map<String, Object> displayData = displayService.prepareDisplayData(
                    targetUser,
                    worktimeData,
                    year,
                    month
            );

            // Add role-based view data
            if (currentUser.getRole().equals("ROLE_ADMIN") ||
                    currentUser.getRole().equals("ROLE_TEAM_LEADER")) {
                model.addAttribute("isAdminView", true);
                model.addAttribute("targetUser", targetUser);

                if (!targetUser.equals(currentUser)) {
                    model.addAttribute("viewingOtherUser", true);
                    model.addAttribute("canEdit", true);
                }
            }

            // Add all data to model
            model.addAllAttributes(displayData);

            return "user/worktime";

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(),
                    String.format("Error processing worktime: %s", e.getMessage()));
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
            User user = getUser(userDetails);

            // Set default year and month if not provided
            LocalDate now = LocalDate.now();
            year = Optional.ofNullable(year).orElse(now.getYear());
            month = Optional.ofNullable(month).orElse(now.getMonthValue());

            // Get worktime data
            List<WorkTimeTable> worktimeData = entrySyncService.synchronizeEntries(
                    user.getUsername(),
                    user.getUserId(),
                    year,
                    month
            );

            // Log the dates to verify data
            LoggerUtil.info(this.getClass(),
                    String.format("Exporting worktime data for %d/%d. Total entries: %d",
                            month, year, worktimeData.size()));
            worktimeData.forEach(entry ->
                    LoggerUtil.debug(this.getClass(),
                            "Entry date: " + entry.getWorkDate()));

            // Get display data which includes the summary
            Map<String, Object> displayData = displayService.prepareDisplayData(
                    user,
                    worktimeData,
                    year,
                    month
            );
            WorkTimeSummary summary = (WorkTimeSummary) displayData.get("summary");

            byte[] excelData = excelExporter.exportToExcel(user, worktimeData, summary, year, month);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=\"worktime_%s_%d_%02d.xlsx\"",
                                    user.getUsername(), year, month))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error exporting to Excel: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}