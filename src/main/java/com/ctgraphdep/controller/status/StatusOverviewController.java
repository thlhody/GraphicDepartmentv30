package com.ctgraphdep.controller.status;

import com.ctgraphdep.config.SecurityConstants;
import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.model.dto.UserStatusDTO;
import com.ctgraphdep.service.ReadFileNameStatusService;
import com.ctgraphdep.service.ThymeleafService;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StatusOverviewController - Handles main status page and AJAX refresh functionality.
 * Displays online/offline status for all users in the system.
 * Part of StatusController refactoring - separated from monolithic StatusController.
 */
@Controller
@RequestMapping("/status")
@PreAuthorize("isAuthenticated()")
public class StatusOverviewController extends BaseController {

    private final ReadFileNameStatusService readFileNameStatusService;
    private final ThymeleafService thymeleafService;

    public StatusOverviewController(UserService userService,
                                   FolderStatus folderStatus,
                                   TimeValidationService timeValidationService,
                                   ReadFileNameStatusService readFileNameStatusService,
                                   ThymeleafService thymeleafService) {
        super(userService, folderStatus, timeValidationService);
        this.readFileNameStatusService = readFileNameStatusService;
        this.thymeleafService = thymeleafService;
    }

    /**
     * Main status overview page - displays all user statuses
     */
    @GetMapping
    public String getStatus(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        LoggerUtil.info(this.getClass(), "Accessing unified status page at " + getStandardCurrentDateTime());

        // Use prepareUserAndCommonModelAttributes to set up common attributes and get current user
        User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
        if (currentUser == null) {
            return "redirect:/login";
        }

        // ENHANCED: Always sync from network flags to get fresh data from "source of truth"
        readFileNameStatusService.invalidateCache();
        LoggerUtil.debug(this.getClass(), "Synced status cache from network flags on page load");

        // Get statuses - now guaranteed to be fresh from network flags
        List<UserStatusDTO> userStatuses = readFileNameStatusService.getAllUserStatuses();
        long onlineCount = readFileNameStatusService.getOnlineUserCount();

        // Add model attributes
        model.addAttribute("userStatuses", userStatuses);
        model.addAttribute("currentUsername", currentUser.getUsername());
        model.addAttribute("onlineCount", onlineCount);
        model.addAttribute("isAdminView", currentUser.isAdmin());
        model.addAttribute("hasAdminTeamLeaderRole",
                currentUser.hasRole(SecurityConstants.ROLE_ADMIN) ||
                        currentUser.hasRole(SecurityConstants.ROLE_TEAM_LEADER) ||
                        currentUser.hasRole(SecurityConstants.ROLE_TL_CHECKING));

        return "status/status";
    }

    /**
     * Manual refresh endpoint - invalidates cache and redirects to main page
     */
    @GetMapping("/refresh")
    public String refreshStatus() {
        // Invalidate the cache to ensure fresh data
        readFileNameStatusService.invalidateCache();
        LoggerUtil.info(this.getClass(), "Status cache invalidated via manual refresh at " + getStandardCurrentDateTime());
        return "redirect:/status";
    }

    /**
     * AJAX refresh endpoint - returns JSON with updated status data and rendered HTML
     */
    @GetMapping("/ajax-refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> ajaxRefreshStatus(@AuthenticationPrincipal UserDetails userDetails) {
        LocalDateTime currentTime = getStandardCurrentDateTime();
        LoggerUtil.info(this.getClass(), "Processing AJAX status refresh request at " + currentTime);

        try {
            // Force invalidation of the status cache
            readFileNameStatusService.invalidateCache();

            User currentUser = getUser(userDetails);

            // Get fresh status list after invalidating cache
            var userStatuses = readFileNameStatusService.getAllUserStatuses();
            long onlineCount = readFileNameStatusService.getOnlineUserCount();

            // Create a model to generate the HTML for the table body
            Model tableModel = new ConcurrentModel();
            tableModel.addAttribute("userStatuses", userStatuses);
            tableModel.addAttribute("currentUsername", currentUser.getUsername());
            tableModel.addAttribute("isAdminView", currentUser.isAdmin());

            // Add the flag for admin/team leader role check
            boolean hasAdminTeamLeaderRole = currentUser.hasRole(SecurityConstants.ROLE_ADMIN) ||
                    currentUser.hasRole(SecurityConstants.ROLE_TEAM_LEADER) ||
                    currentUser.hasRole(SecurityConstants.ROLE_TL_CHECKING);
            tableModel.addAttribute("hasAdminTeamLeaderRole", hasAdminTeamLeaderRole);

            // Render the table body fragment using Thymeleaf
            String tableHtml = "";
            try {
                tableHtml = thymeleafService.processTemplate("status/fragments/status-table-body", tableModel);
            } catch (Exception e) {
                LoggerUtil.error(this.getClass(), "Error rendering status table fragment: " + e.getMessage(), e);
            }

            // Prepare response data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("onlineCount", onlineCount);
            responseData.put("tableHtml", tableHtml);
            responseData.put("timestamp", currentTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error processing AJAX status refresh: " + e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of("error", "Failed to refresh status data")
            );
        }
    }
}
