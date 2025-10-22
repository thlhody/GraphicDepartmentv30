package com.ctgraphdep.controller.utility;

import com.ctgraphdep.controller.base.BaseController;
import com.ctgraphdep.model.FolderStatus;
import com.ctgraphdep.model.User;
import com.ctgraphdep.service.UserService;
import com.ctgraphdep.service.cache.MainDefaultUserContextService;
import com.ctgraphdep.session.service.SessionMidnightHandler;
import com.ctgraphdep.utils.LoggerUtil;
import com.ctgraphdep.validation.TimeValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for session management utilities.
 * Handles manual session resets, midnight reset status, and user context information.
 */
@RestController
@RequestMapping("/utility/session")
public class SessionUtilityController extends BaseController {

    private final SessionMidnightHandler sessionMidnightHandler;
    private final MainDefaultUserContextService mainDefaultUserContextService;

    public SessionUtilityController(
            UserService userService,
            FolderStatus folderStatus,
            TimeValidationService timeValidationService,
            SessionMidnightHandler sessionMidnightHandler,
            MainDefaultUserContextService mainDefaultUserContextService) {

        super(userService, folderStatus, timeValidationService);
        this.sessionMidnightHandler = sessionMidnightHandler;
        this.mainDefaultUserContextService = mainDefaultUserContextService;
    }

    // ========================================================================
    // SESSION MANAGEMENT ENDPOINTS
    // ========================================================================

    /**
     * Perform manual session reset
     */
    @PostMapping("/manual-reset")
    public ResponseEntity<Map<String, Object>> performManualReset(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = getUser(userDetails);
            if (currentUser == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Perform manual reset using SessionMidnightHandler
            sessionMidnightHandler.performManualReset(currentUser.getUsername());

            response.put("success", true);
            response.put("message", "Manual session reset completed successfully");
            response.put("username", currentUser.getUsername());
            response.put("timestamp", getStandardCurrentDateTime());

            LoggerUtil.info(this.getClass(), "Manual session reset performed for user: " + currentUser.getUsername());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error performing manual reset: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error performing manual reset: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get midnight reset status
     */
    @GetMapping("/reset-status")
    public ResponseEntity<Map<String, Object>> getResetStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            String resetStatus = sessionMidnightHandler.getMidnightResetStatus();

            response.put("success", true);
            response.put("resetStatus", resetStatus);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting reset status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting reset status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get user context status
     */
    @GetMapping("/context-status")
    public ResponseEntity<Map<String, Object>> getUserContextStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            User currentUser = mainDefaultUserContextService.getOriginalUser();
            String currentUsername = mainDefaultUserContextService.getCurrentUsername();
            boolean isHealthy = mainDefaultUserContextService.isCacheHealthy();
            boolean hasRealUser = mainDefaultUserContextService.hasRealUser();
            boolean isInitialized = mainDefaultUserContextService.isCacheInitialized();

            response.put("success", true);
            response.put("currentUsername", currentUsername);
            response.put("currentUserId", currentUser != null ? currentUser.getUserId() : null);
            response.put("isHealthy", isHealthy);
            response.put("hasRealUser", hasRealUser);
            response.put("isInitialized", isInitialized);
            response.put("timestamp", getStandardCurrentDateTime());

        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Error getting user context status: " + e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error getting user context status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }

        return ResponseEntity.ok(response);
    }
}
