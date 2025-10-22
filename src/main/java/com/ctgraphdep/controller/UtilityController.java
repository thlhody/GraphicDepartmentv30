package com.ctgraphdep.controller;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Main utility controller for user self-service diagnostics and recovery page.
 * Renders the utility dashboard page. All REST API endpoints have been moved to specialized controllers:
 * - BackupUtilityController: /utility/backups/**
 * - CacheUtilityController: /utility/cache/**
 * - SessionUtilityController: /utility/session/**
 * - HealthUtilityController: /utility/health/**
 * - MergeUtilityController: /utility/merge/**
 * - DiagnosticsUtilityController: /utility/diagnostics/**
 */
@Controller
@RequestMapping("/utility")
public class UtilityController extends BaseController {

    public UtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService) {

        super(userService, folderStatus, timeValidationService);
    }

    /**
     * Main utility page with card-based layout.
     * All REST API endpoints have been moved to specialized controllers in controller.utility package.
     */
    @GetMapping
    public String utilityPage(Authentication authentication, Model model) {
        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = prepareUserAndCommonModelAttributes(userDetails, model);
            if (currentUser == null) {
                return "redirect:/login";
            }

            model.addAttribute("currentTime", getStandardCurrentDateTime());
            model.addAttribute("userId", currentUser.getUserId());
            LoggerUtil.info(this.getClass(), "User " + currentUser.getUsername() + " accessed utilities page");

            return "utility";
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error loading utility page: " + e.getMessage(), e);
            model.addAttribute("error", "Failed to load utilities page");
            return "error";
        }
    }
}